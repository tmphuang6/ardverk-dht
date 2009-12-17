package com.ardverk.dht.entity;

import org.ardverk.concurrent.AsyncFuture;

import com.ardverk.dht.KUID;
import com.ardverk.dht.routing.Contact;

public interface NodeEntity extends LookupEntity {
    
    public AsyncFuture<StoreEntity> store(KUID key, byte[] value);
    
    public Contact[] getContacts();
    
    public int getHops();
}
