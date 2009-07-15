package com.ardverk.dht.routing;

import java.net.SocketAddress;
import java.util.Map;

import com.ardverk.dht.KUID;
import com.ardverk.dht.KeyFactory;

public interface ContactFactory {

    public KeyFactory getKeyFactory();
    
    public Contact createUncharted(KUID contactId, 
            int instanceId, SocketAddress address);
    
    public Contact createUncharted(KUID contactId, 
            int instanceId, SocketAddress address, Map<?, ?> attributes);
    
    public Contact createCharted(KUID contactId, 
            int instanceId, SocketAddress address);
    
    public Contact createCharted(KUID contactId, 
            int instanceId, SocketAddress address, Map<?, ?> attributes);
    
    public Contact merge(Contact existing, Contact contact);
}
