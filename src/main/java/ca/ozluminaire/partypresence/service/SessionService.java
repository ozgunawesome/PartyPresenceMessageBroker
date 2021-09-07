package ca.ozluminaire.partypresence.service;

import ca.ozluminaire.partypresence.ClientMessage;
import ca.ozluminaire.partypresence.model.Client;
import ca.ozluminaire.partypresence.model.Party;
import ca.ozluminaire.partypresence.model.Session;
import ca.ozluminaire.partypresence.model.SessionState;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Singleton
public class SessionService {

    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    public boolean hasSession(Long id) {
        return sessions.containsKey(id);
    }

    public Session getSession(Long id) {
        return sessions.get(id);
    }

    public Session createOrResumeSession(Long id, Client client, Party party, StreamObserver<ClientMessage> responseObserver) {
        Session session = sessions.computeIfAbsent(id, key -> new Session(key, client, party));
        log.info("Creating {}", session);
        if (session.getSessionState() == SessionState.ACTIVE) {
            unbindSession(session);
        }
        bindSession(session, responseObserver);
        return session;
    }

    public void bindSession(Session session, StreamObserver<ClientMessage> responseObserver) {
        if (session.getSessionState() == SessionState.NEW || session.getSessionState() == SessionState.INACTIVE) {
            log.info("Bind {} to responseObserver {}", session, responseObserver.hashCode());
            session.setSessionState(SessionState.ACTIVE);
            session.setResponseObserver(responseObserver);
        }
    }

    public void unbindSession(Session session) {
        if (session.getSessionState() == SessionState.ACTIVE) {
            log.info("Unbind {} from its response observer {}", session,
                    Optional.ofNullable(session.getResponseObserver()).map(Objects::hashCode).orElse(0));
            session.setSessionState(SessionState.INACTIVE);
            session.setResponseObserver(null);
        }
    }

    public void deleteSession(Session session) {
        if (session.getSessionState() != SessionState.EXPIRED) {
            log.info("Delete {}", session);
            session.close();
            sessions.remove(session.getId());
        }
    }

}
