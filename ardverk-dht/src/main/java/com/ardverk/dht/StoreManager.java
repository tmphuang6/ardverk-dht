package com.ardverk.dht;

import org.ardverk.concurrent.AsyncFuture;
import org.ardverk.concurrent.AsyncFutureListener;
import org.ardverk.concurrent.AsyncProcess;
import org.ardverk.concurrent.FutureUtils;
import org.ardverk.concurrent.NopAsyncProcess;
import org.ardverk.concurrent.ValueReference;

import com.ardverk.dht.concurrent.ArdverkFuture;
import com.ardverk.dht.config.PutConfig;
import com.ardverk.dht.config.StoreConfig;
import com.ardverk.dht.entity.DefaultNodeStoreEntity;
import com.ardverk.dht.entity.NodeEntity;
import com.ardverk.dht.entity.StoreEntity;
import com.ardverk.dht.io.MessageDispatcher;
import com.ardverk.dht.io.StoreResponseHandler;
import com.ardverk.dht.routing.Contact;
import com.ardverk.dht.routing.RouteTable;
import com.ardverk.dht.storage.DefaultValueTuple;
import com.ardverk.dht.storage.Value;
import com.ardverk.dht.storage.ValueTuple;

class StoreManager {

    private final DHT dht;
    
    private final RouteTable routeTable;
    
    private final MessageDispatcher messageDispatcher;
    
    public StoreManager(DHT dht, RouteTable routeTable, 
            MessageDispatcher messageDispatcher) {
        this.dht = dht;
        this.routeTable = routeTable;
        this.messageDispatcher = messageDispatcher;
    }
    
    public ArdverkFuture<StoreEntity> put(
            final KUID key, final Value value, final PutConfig config) {
        
        final Object lock = new Object();
        synchronized (lock) {
            
            // This is the ArdverkFuture we're going to return to the caller
            // of this method (in most cases the user).
            AsyncProcess<StoreEntity> process = NopAsyncProcess.create();
            final ArdverkFuture<StoreEntity> userFuture 
                = dht.submit(process, config);
            
            // This will get initialized once we've found the k-closest
            // Contacts to the given KUID
            final ValueReference<ArdverkFuture<StoreEntity>> storeFutureRef 
                = new ValueReference<ArdverkFuture<StoreEntity>>();
            
            // Start the lookup for the given KUID
            final ArdverkFuture<NodeEntity> lookupFuture 
                = dht.lookup(key, config.getLookupConfig());
            
            // Let's wait for the result of the FIND_NODE operation. On success we're 
            // going to initialize the storeFutureRef and do the actual STOREing.
            lookupFuture.addAsyncFutureListener(new AsyncFutureListener<NodeEntity>() {
                @Override
                public void operationComplete(AsyncFuture<NodeEntity> future) {
                    synchronized (lock) {
                        try {
                            if (!future.isCancelled()) {
                                handleNodeEntity(future.get());
                            } else {
                                handleCancelled();
                            }
                        } catch (Throwable t) {
                            handleException(t);
                        }
                    }
                }
                
                private void handleNodeEntity(final NodeEntity nodeEntity) {
                    Contact[] contacts = nodeEntity.getContacts();
                    ArdverkFuture<StoreEntity> storeFuture 
                        = storeFutureRef.make(store(contacts, 
                                key, value, config.getStoreConfig()));
                    
                    storeFuture.addAsyncFutureListener(new AsyncFutureListener<StoreEntity>() {
                        @Override
                        public void operationComplete(AsyncFuture<StoreEntity> future) {
                            synchronized (lock) {
                                try {
                                    if (!future.isCancelled()) {
                                        handleStoreEntity(future.get());
                                    } else {
                                        handleCancelled();
                                    }
                                } catch (Throwable t) {
                                    handleException(t);
                                }
                            }
                        }
                        
                        private void handleStoreEntity(StoreEntity storeEntity) {
                            userFuture.setValue(new DefaultNodeStoreEntity(
                                    nodeEntity, storeEntity));
                        }
                    });
                }
                
                private void handleCancelled() {
                    userFuture.cancel(true);
                }
                
                private void handleException(Throwable t) {
                    userFuture.setException(t);
                }
            });
            
            userFuture.addAsyncFutureListener(new AsyncFutureListener<StoreEntity>() {
                @Override
                public void operationComplete(AsyncFuture<StoreEntity> future) {
                    synchronized (lock) {
                        FutureUtils.cancel(lookupFuture, true);
                        FutureUtils.cancel(storeFutureRef, true);
                    }
                }
            });
            
            return userFuture;
        }
    }
    
    public ArdverkFuture<StoreEntity> put(Contact[] dst, 
            KUID key, Value value, StoreConfig config) {
        return store(dst, key, value, config);
    }
    
    private ArdverkFuture<StoreEntity> store(Contact[] dst, 
            KUID key, Value value, StoreConfig config) {
        
        int k = routeTable.getK();
        
        Contact localhost = dht.getLocalhost();
        ValueTuple valueTuple = new DefaultValueTuple(localhost, key, value);
        
        AsyncProcess<StoreEntity> process 
            = new StoreResponseHandler(messageDispatcher, 
                dst, k, valueTuple, config);
        
        return dht.submit(process, config);
    }
}
