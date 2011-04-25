package org.ardverk.dht.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class DefaultResource extends AbstractResource {
    
    private final byte[] content;
    
    public DefaultResource(ResourceId resourceId, InputStream in) throws IOException {
        super(resourceId);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        byte[] buffer = new byte[4 * 1024];
        int len = -1;
        while ((len = in.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        
        this.content = baos.toByteArray();
    }
    
    public DefaultResource(ResourceId resourceId, byte[] content) {
        super(resourceId);
        this.content = content;
    }
    
    @Override
    public long getContentLength() {
        return content.length;
    }

    @Override
    public InputStream getContent() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public byte[] getContentAsBytes() {
        return content;
    }
}
