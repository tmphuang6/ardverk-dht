/*
 * Copyright 2009-2010 Roger Kapsi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ardverk.dht.config;

import org.ardverk.dht.message.MessageType;

/**
 * The {@link PutConfig} provides configuration data for the {@link MessageType#FIND_NODE}
 * and {@link MessageType#STORE} operations.
 */
public interface PutConfig extends Config {

    /**
     * Returns the {@link LookupConfig}.
     */
    public LookupConfig getLookupConfig();
    
    /**
     * Sets the {@link LookupConfig}.
     */
    public void setLookupConfig(LookupConfig lookupConfig);
    
    /**
     * Returns the {@link StoreConfig}.
     */
    public StoreConfig getStoreConfig();
    
    /**
     * Sets the {@link StoreConfig}.
     */
    public void setStoreConfig(StoreConfig storeConfig);
}