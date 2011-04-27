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

package org.ardverk.dht.message;

import java.io.IOException;
import java.io.InputStream;

import org.ardverk.dht.concurrent.DHTFuture;
import org.ardverk.dht.concurrent.NopFuture;

/**
 * 
 */
public interface Content {
    
    /**
     * 
     */
    public static final NopFuture<Void> DEFAULT_FUTURE 
        = NopFuture.withValue(null);
    
    /**
     * 
     */
    public DHTFuture<Void> getContentFuture();
    
    /**
     * 
     */
    public long getContentLength();
    
    /**
     * 
     */
    public InputStream getContent() throws IOException;
    
    /**
     * 
     */
    public byte[] getContentAsBytes() throws IOException;
    
    /**
     * Tells if the entity is capable of producing its data more than once.
     */
    public boolean isRepeatable();
    
    /**
     * Tells that this entity is streaming.
     */
    public boolean isStreaming();
}