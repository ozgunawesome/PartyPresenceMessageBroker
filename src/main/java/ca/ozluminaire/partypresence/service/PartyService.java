package ca.ozluminaire.partypresence.service;

import ca.ozluminaire.partypresence.model.Party;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class PartyService {

    private final Map<Long, Party> partyMap = new ConcurrentHashMap<>();

    public Party getParty(Long partyId) {
        return partyMap.computeIfAbsent(partyId, Party::new);
    }

}
