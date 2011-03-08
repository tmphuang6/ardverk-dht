/*
 * Copyright 2009-2011 Roger Kapsi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ardverk.dht.io;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ardverk.collection.FixedSizeHashSet;
import org.ardverk.concurrent.ExecutorUtils;
import org.ardverk.dht.KUID;
import org.ardverk.dht.event.EventUtils;
import org.ardverk.dht.io.transport.Endpoint;
import org.ardverk.dht.io.transport.Transport;
import org.ardverk.dht.io.transport.TransportCallback;
import org.ardverk.dht.message.Message;
import org.ardverk.dht.message.MessageFactory;
import org.ardverk.dht.message.MessageId;
import org.ardverk.dht.message.RequestMessage;
import org.ardverk.dht.message.ResponseMessage;
import org.ardverk.dht.routing.Contact;
import org.ardverk.io.Bindable;
import org.ardverk.io.IoUtils;
import org.ardverk.lang.Arguments;
import org.ardverk.lang.NullArgumentException;
import org.ardverk.lang.TimeStamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The {@link MessageDispatcher} is responsible for sending messages over
 * a given {@link Transport} and keeping track of the messages.
 */
public abstract class MessageDispatcher 
        implements Bindable<Transport>, Closeable {
    
    private static final Logger LOG 
        = LoggerFactory.getLogger(MessageDispatcher.class);
    
    private static final ScheduledExecutorService EXECUTOR 
        = ExecutorUtils.newSingleThreadScheduledExecutor(
            "MessageDispatcherThread");
    
    private final TransportCallback callback = new TransportCallback() {
        @Override
        public void messageReceived(Endpoint endpoint, Message message) throws IOException {
            MessageDispatcher.this.handleMessage(endpoint, message);
        }
        
        @Override
        public void messageSent(Endpoint endpoint, Message message) {
            MessageDispatcher.this.messageSent(message);
        }
        
        @Override
        public void handleException(Endpoint endpoint, Message message, Throwable t) {
            MessageDispatcher.this.handleException(message, t);
        }
    };
    
    private final List<MessageListener> listeners 
        = new CopyOnWriteArrayList<MessageListener>();
    
    private final MessageEntityManager entityManager 
        = new MessageEntityManager();
        
    private final ScheduledExecutorService executor;
    
    private final MessageFactory factory;
    
    private final ResponseChecker checker;
    
    private Transport transport = null;
    
    /**
     * Creates a {@link MessageDispatcher}.
     */
    public MessageDispatcher(MessageFactory factory) {
        this(EXECUTOR, factory);
    }
    
    /**
     * Creates a {@link MessageDispatcher} with a custom 
     * {@link ScheduledExecutorService} that is used to
     * keep for timing out requests.
     */
    public MessageDispatcher(ScheduledExecutorService executor, 
            MessageFactory factory) {
        this.executor = executor;
        this.factory = factory;
        
        // TODO: Is memorizing the 512 most recently received MessageIds
        // too much or too little? 
        this.checker = new ResponseChecker(factory, 512);
    }
    
    /**
     * Returns the {@link Transport}
     */
    public synchronized Transport getTransport() {
        return transport;
    }
    
    @Override
    public synchronized boolean isBound() {
        return transport != null;
    }
    
    @Override
    public synchronized void bind(Transport transport) throws IOException {
        if (transport == null) {
            throw new NullArgumentException("transport");
        }
        
        if (isBound()) {
            throw new IOException();
        }
        
        transport.bind(callback);
        this.transport = transport;
    }
    
    @Override
    public synchronized void unbind() {
        unbind(false);
    }
    
    /**
     * Unbinds the {@link Transport} and closes it optionally.
     */
    private synchronized void unbind(boolean close) {
        if (transport != null) {
            IoUtils.unbind(transport);
            
            if (close) {
                IoUtils.close(transport);
            }
            
            transport = null;
        }
    }
    
    @Override
    public void close() {
        unbind(true);
        entityManager.close();
    }
    
    /**
     * Returns the {@link MessageFactory}
     */
    public MessageFactory getMessageFactory() {
        return factory;
    }
    
    /**
     * Sends the given {@link Message}.
     */
    protected void send(Endpoint endpoint, Message message, 
            long timeout, TimeUnit unit) throws IOException {
        
        if (endpoint == null) {
            throw new IOException();
        }
        
        endpoint.send(message, timeout, unit);
    }
    
    /**
     * Sends a {@link ResponseMessage} to the given {@link Contact}.
     */
    public void send(Endpoint endpoint, Contact dst, 
            ResponseMessage message) throws IOException {
        send(endpoint, message, -1L, TimeUnit.MILLISECONDS);
        fireMessageSent(dst, message);
    }
    
    /**
     * Sends a {@link RequestMessage} to the given {@link Contact}.
     */
    public void send(MessageCallback callback, 
            Contact dst, RequestMessage message, 
            long timeout, TimeUnit unit) throws IOException {
        
        KUID contactId = dst.getId();
        send(callback, contactId, message, timeout, unit);
    }
    
    /**
     * Sends a {@link RequestMessage} to the a {@link Contact} with the 
     * given {@link KUID}.
     * 
     * <p>NOTE: The destination {@link SocketAddress} is encoded in the 
     * {@link RequestMessage}. The {@link KUID} is used to validate
     * {@link ResponseMessage}s.
     */
    public void send(MessageCallback callback, 
            KUID contactId, RequestMessage request, 
            long timeout, TimeUnit unit) throws IOException {
        
        if (callback != null) {
            RequestEntity entity = new RequestEntity(
                    contactId, request);
            entityManager.add(callback, entity, timeout, unit);
        }
        
        Transport transport = null;
        synchronized (this) {
            transport = this.transport;
        }
        
        send(transport, request, timeout, unit);
        fireMessageSent(contactId, request);
    }
    
    /**
     * Callback method for outgoing {@link Message} that failed to be sent.
     * Returns {@code true} if the {@link Throwable} was handled or not.
     */
    public boolean handleException(Message message, Throwable t) {
        MessageId messageId = message.getMessageId();
        MessageEntity entity = entityManager.get(messageId);
        
        if (entity != null) {
            entity.handleException(t);
            return true;
        }
        return false;
    }
    
    /**
     * Callback method for all outgoing {@link Message}s that have been sent.
     */
    public void messageSent(Message message) {
    }
    
    /**
     * Callback method for incoming {@link Message}s.
     */
    public void handleMessage(Endpoint endpoint, Message message) throws IOException {
        if (message instanceof RequestMessage) {
            handleRequest(endpoint, (RequestMessage)message);
        } else {
            handleResponse((ResponseMessage)message);
        }
        
        fireMessageReceived(message);
    }
    
    /**
     * Callback method for incoming {@link ResponseMessage}s.
     */
    private void handleResponse(ResponseMessage response) throws IOException {
        
        if (!checker.check(response)) {
            return;
        }
        
        MessageEntity entity = entityManager.get(response);
        if (entity != null) {
            entity.handleResponse(response);
        } else {
            lateResponse(response);
        }
    }
    
    /**
     * Callback method for incoming {@link RequestMessage}s.
     */
    protected abstract void handleRequest(Endpoint endpoint, 
            RequestMessage request) throws IOException;
    
    /**
     * Callback method for late incoming {@link ResponseMessage}s.
     */
    protected abstract void lateResponse(ResponseMessage response) throws IOException;
    
    /**
     * Callback method for late incoming {@link ResponseMessage}s.
     */
    protected void handleResponse(MessageCallback callback, 
            RequestEntity entity, ResponseMessage response, 
            long time, TimeUnit unit) throws IOException {
        callback.handleResponse(entity, response, time, unit);
    }
    
    /**
     * Callback method for timeouts.
     */
    protected void handleTimeout(MessageCallback callback, 
            RequestEntity entity, long time, TimeUnit unit) 
                throws IOException {
        callback.handleTimeout(entity, time, unit);
    }
    
    /**
     * Callback method for illegal {@link ResponseMessage}s.
     */
    protected void handleIllegalResponse(MessageCallback callback, 
            RequestEntity entity, ResponseMessage response, 
            long time, TimeUnit unit) throws IOException {
        
        if (LOG.isErrorEnabled()) {
            LOG.error("Illegal Response: " + entity + " -> " + response);
        }
    }
    
    /**
     * Callback for {@link Exception}s.
     */
    protected void handleException(MessageCallback callback, 
            RequestEntity entity, Throwable t) {
        callback.handleException(entity, t);
    }
    
    /**
     * Adds the given {@link MessageListener}.
     */
    public void addMessageListener(MessageListener l) {
        listeners.add(Arguments.notNull(l, "l"));
    }
    
    /**
     * Removes the given {@link MessageListener}.
     */
    public void removeMessageListener(MessageListener l) {
        listeners.remove(l);
    }
    
    /**
     * Returns all {@link MessageListener}s.
     */
    public MessageListener[] getMessageListeners() {
        return listeners.toArray(new MessageListener[0]);
    }
    
    /**
     * Fires a message sent event.
     */
    protected void fireMessageSent(Contact dst, Message message) {
        fireMessageSent(dst.getId(), message);
    }
    
    /**
     * Fires a message sent event.
     */
    protected void fireMessageSent(final KUID contactId, final Message message) {
        
        if (!listeners.isEmpty()) {
            Runnable event = new Runnable() {
                @Override
                public void run() {
                    for (MessageListener l : listeners) {
                        l.handleMessageSent(contactId, message);
                    }
                }
            };
            EventUtils.fireEvent(event);
        }
    }
    
    /**
     * Fires a message received event.
     */
    protected void fireMessageReceived(final Message message) {
        if (!listeners.isEmpty()) {
            Runnable event = new Runnable() {
                @Override
                public void run() {
                    for (MessageListener l : listeners) {
                        l.handleMessageReceived(message);
                    }
                }
            };
            EventUtils.fireEvent(event);
        }
    }
    
    /**
     * The {@link MessageEntityManager} keeps track of {@link RequestEntity}s
     * and their {@link MessageCallback}s. It's also responsible for timing
     * out {@link RequestMessage} for which we haven't received any responses
     * within a defined time frame.
     */
    private class MessageEntityManager implements Closeable {
        
        private final Map<MessageId, MessageEntity> callbacks 
            = Collections.synchronizedMap(new HashMap<MessageId, MessageEntity>());
        
        private boolean open = true;
        
        @Override
        public void close() {
            synchronized (callbacks) {
                if (!open) {
                    return;
                }
                
                open = false;
                
                for (MessageEntity entity : callbacks.values()) {
                    entity.cancel();
                }
                
                callbacks.clear();
            }
        }
        
        /**
         * Adds a {@link RequestEntity} and its {@link MessageCallback}.
         */
        public void add(MessageCallback callback, RequestEntity entity, 
                long timeout, TimeUnit unit) {
            
            final MessageId messageId = entity.getMessageId();
            
            synchronized (callbacks) {
                
                if (!open) {
                    throw new IllegalStateException();
                }
                
                if (callbacks.containsKey(messageId)) {
                    throw new IllegalArgumentException("messageId=" + messageId);
                }
                
                Runnable task = new Runnable() {
                    @Override
                    public void run() {
                        MessageEntity entity 
                            = callbacks.remove(messageId);
                        
                        if (entity != null) {
                            try {
                                entity.handleTimeout();
                            } catch (IOException err) {
                                LOG.error("IOException", err);
                            }
                        }
                    }
                };
                
                ScheduledFuture<?> future 
                    = executor.schedule(task, timeout, unit);
                
                MessageEntity messageEntity = new MessageEntity(
                        future, callback, entity);
                callbacks.put(messageId, messageEntity);
            }
        }
        
        /**
         * Returns a {@link MessageEntity} for the given {@link ResponseMessage}.
         */
        public MessageEntity get(ResponseMessage message) {
            return get(message.getMessageId());
        }
        
        /**
         * Returns a {@link MessageEntity} for the given {@link MessageId}.
         */
        public MessageEntity get(MessageId messageId) {
            return callbacks.remove(messageId);
        }
    }

    /**
     * A {@link MessageEntity} represents a {@link RequestMessage} we've sent 
     * and provides the infrastructure to process the {@link ResponseMessage}.
     */
    private class MessageEntity {
        
        private final TimeStamp creationTime = TimeStamp.now();
        
        private final ScheduledFuture<?> future;

        private final MessageCallback callback;
        
        private final RequestEntity entity;
        
        private final AtomicBoolean open = new AtomicBoolean(true);
        
        private MessageEntity(ScheduledFuture<?> future, 
                MessageCallback callback, 
                RequestEntity entity) {
            
            this.future = Arguments.notNull(future, "future");
            this.callback = Arguments.notNull(callback, "callback");
            this.entity = Arguments.notNull(entity, "entity");
        }

        /**
         * Cancels the {@link MessageEntity}.
         */
        public boolean cancel() {
            future.cancel(true);
            return open.getAndSet(false);
        }
        
        /**
         * Called if a {@link ResponseMessage} was received.
         */
        public void handleResponse(ResponseMessage response) throws IOException {
            if (cancel()) {
                long time = creationTime.getAgeInMillis();
                
                if (entity.check(response)) {
                    MessageDispatcher.this.handleResponse(callback, entity, 
                            response, time, TimeUnit.MILLISECONDS);
                } else {
                    MessageDispatcher.this.handleIllegalResponse(callback, 
                            entity, response, time, TimeUnit.MILLISECONDS);
                }
            }
        }

        /**
         * Called if a timeout occurred (i.e. we didn't receive a 
         * {@link ResponseMessage} within in the predefined time).
         */
        public void handleTimeout() throws IOException {
            if (cancel()) {
                
                long time = creationTime.getAgeInMillis();
                MessageDispatcher.this.handleTimeout(callback, entity, 
                        time, TimeUnit.MILLISECONDS);
            }
        }
        
        /**
         * Called if an exception occured.
         */
        public void handleException(Throwable t) {
            if (cancel()) {
                MessageDispatcher.this.handleException(callback, entity, t);
            }
        }
    }
    
    /**
     * The {@link ResponseChecker} makes sure {@link ResponseMessage}s fulfill
     * certain requirements before they're considered for further processing.
     */
    private static class ResponseChecker {
        
        private static final Logger LOG 
            = LoggerFactory.getLogger(ResponseChecker.class);
        
        private final MessageFactory factory;
        
        private final Set<MessageId> history;
        
        public ResponseChecker(MessageFactory factory, int historySize) {
            this.factory = Arguments.notNull(factory, "factory");
            this.history = Collections.synchronizedSet(
                    new FixedSizeHashSet<MessageId>(historySize));
        }
        
        /**
         * Returns {@code true} if the {@link ResponseMessage} is OK.
         */
        public boolean check(ResponseMessage response) {
            MessageId messageId = response.getMessageId();
            if (!history.add(messageId)) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Multiple respones: " + response);
                }
                return false;
            }
            
            Contact contact = response.getContact();
            if (!factory.isFor(messageId, contact.getRemoteAddress())) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Wrong MessageId signature: " + response);
                }
                return false;
            }
            
            return true;
        }
    }
}