package ca.ozluminaire.partypresence.service;

import ca.ozluminaire.partypresence.model.Client;
import ca.ozluminaire.partypresence.model.Party;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class ClientService {

    private final Map<Long, Client> clientMap = new ConcurrentHashMap<>();

    public Client getClient(Long clientId) {
        return clientMap.computeIfAbsent(clientId, Client::new);
    }

}
