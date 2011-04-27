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

package org.ardverk.dht.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.ardverk.collection.CollectionUtils;
import org.ardverk.dht.codec.bencode.MessageInputStream;
import org.ardverk.dht.codec.bencode.MessageOutputStream;
import org.ardverk.dht.message.AbstractContent;
import org.ardverk.dht.message.Content;
import org.ardverk.io.IoUtils;

public class ValueList extends AbstractContent {

    private final ResourceId[] resourceIds;
    
    private byte[] payload = null;
    
    public ValueList(Collection<? extends ResourceId> c) {
        this(CollectionUtils.toArray(c, ResourceId.class));
    }
    
    public ValueList(ResourceId[] resourceIds) {
        this.resourceIds = resourceIds;
    }
    
    public ResourceId[] getResourceIds() {
        return resourceIds;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        int index = 0;
        for (ResourceId resourceId : resourceIds) {
            sb.append(index++).append(") ").append(resourceId).append("\n");
        }
        
        return sb.toString();
    }
    
    @Override
    public long getContentLength() {
        return payload().length;
    }

    @Override
    public InputStream getContent() throws IOException {
        return new ByteArrayInputStream(payload());
    }
    
    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public boolean isStreaming() {
        return false;
    }
    
    private synchronized byte[] payload() {
        if (payload == null) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                MessageOutputStream out = new MessageOutputStream(baos);
                
                out.writeShort(resourceIds.length);
                for (ResourceId resourceId: resourceIds) {
                    out.writeResourceId(resourceId);
                }
                out.close();
                
                payload = baos.toByteArray();
            } catch (IOException err) {
                throw new IllegalStateException("IOException", err);
            }
        }
        return payload;
    }
    
    public static ValueList valueOf(Content content) {
        MessageInputStream in = null;
        try {
            in = new MessageInputStream(content.getContent());
            
            int count = in.readUnsignedShort();
            ResourceId[] resourceIds = new ResourceId[count];
            for (int i = 0; i < resourceIds.length; i++) {
                resourceIds[i] = in.readResourceId();
            }
            
            return new ValueList(resourceIds);
        } catch (IOException err) {
            throw new IllegalStateException("IOException", err);
        } finally {
            IoUtils.close(in);
        }
    }
}