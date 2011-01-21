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

import java.io.IOException;

import org.ardverk.dht.KUID;
import org.ardverk.dht.message.MessageFactory;
import org.ardverk.dht.message.MessageType;
import org.ardverk.dht.message.RequestMessage;
import org.ardverk.dht.message.ResponseMessage;
import org.ardverk.dht.message.ValueRequest;
import org.ardverk.dht.routing.Contact;
import org.ardverk.dht.routing.RouteTable;
import org.ardverk.dht.storage.Database;
import org.ardverk.dht.storage.ValueTuple;
import org.ardverk.lang.Arguments;


/**
 * The {@link ValueRequestHandler} handles {@link ValueRequest} 
 * ({@link MessageType#FIND_VALUE}) messages. 
 */
public class ValueRequestHandler extends AbstractRequestHandler {

    private final RouteTable routeTable;
    
    private final Database database;
    
    public ValueRequestHandler(
            MessageDispatcher messageDispatcher, 
            RouteTable routeTable, 
            Database database) {
        super(messageDispatcher);
        
        this.routeTable = Arguments.notNull(routeTable, "routeTable");
        this.database = Arguments.notNull(database, "database");
    }

    @Override
    public void handleRequest(RequestMessage message) throws IOException {
        ValueRequest request = (ValueRequest)message;
        
        KUID valueId = request.getId();
        ValueTuple value = database.get(valueId);
        
        MessageFactory factory = messageDispatcher.getMessageFactory();
        ResponseMessage response = null;
        
        if (value != null) {
            response = factory.createValueResponse(request, value);
        } else {
            Contact[] contacts = routeTable.select(valueId);
            response = factory.createNodeResponse(request, contacts);
        }
        
        send(request, response);
    }
}