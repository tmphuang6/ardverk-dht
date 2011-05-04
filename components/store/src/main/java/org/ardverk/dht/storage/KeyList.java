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

public class KeyList extends SimpleValue {

    private final Key[] keys;
    
    private byte[] payload = null;
    
    public KeyList(Collection<? extends Key> c) {
        this(CollectionUtils.toArray(c, Key.class));
    }
    
    public KeyList(Key[] keys) {
        super(ValueType.KEY_LIST);
        this.keys = keys;
    }

    public Key[] getKeys() {
        return keys;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        int index = 0;
        for (Key key : keys) {
            sb.append(index++).append(") ").append(key).append("\n");
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
                
                writeHeader(out);
                
                out.writeShort(keys.length);
                for (Key key: keys) {
                    out.writeKey(key);
                }
                out.close();
                
                payload = baos.toByteArray();
            } catch (IOException err) {
                throw new IllegalStateException("IOException", err);
            }
        }
        return payload;
    }
    
    public static KeyList valueOf(MessageInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        Key[] keys = new Key[count];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = in.readKey();
        }
        
        return new KeyList(keys);
    }
}