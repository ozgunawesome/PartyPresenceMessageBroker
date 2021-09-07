package ca.ozluminaire.partypresence.messaging;

import ca.ozluminaire.partypresence.ClientMessage;
import ca.ozluminaire.partypresence.ClientMessageType;
import ca.ozluminaire.partypresence.StatusCode;
import ca.ozluminaire.partypresence.model.Client;
import ca.ozluminaire.partypresence.model.Party;
import ca.ozluminaire.partypresence.model.Session;
import ca.ozluminaire.partypresence.service.ClientService;
import ca.ozluminaire.partypresence.service.PartyService;
import ca.ozluminaire.partypresence.service.SessionService;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import static ca.ozluminaire.partypresence.util.ClientMessageUtil.buildAckMessage;
import static ca.ozluminaire.partypresence.util.ClientMessageUtil.getBuilderFor;

@Singleton
@Slf4j
public class ClientMessageProcessor {

    private final SessionService sessionService;
    private final ClientService clientService;
    private final PartyService partyService;
    private final KeepaliveService keepaliveService;

    public ClientMessageProcessor(SessionService sessionService, ClientService clientService, PartyService partyService, KeepaliveService keepaliveService) {
        this.sessionService = sessionService;
        this.clientService = clientService;
        this.partyService = partyService;
        this.keepaliveService = keepaliveService;
    }

    void processMessage(SessionReference sessionRef, StreamObserver<ClientMessage> responseObserver,
                        ClientMessage clientMessage) {

        switch (clientMessage.getMessageType()) {
            case JOIN:
                processJoinMessage(sessionRef, clientMessage, responseObserver);
                break;
            case LEAVE:
                processLeaveMessage(sessionRef, clientMessage);
                break;
            case PING:
                processPingMessage(sessionRef, clientMessage);
                break;
            case ACK:
                processAckMessage(sessionRef, clientMessage);
                break;
            default:
                return;
        }

        if (sessionRef.getSession() != null) {
            keepaliveService.setKeepaliveTimer(sessionRef);
        }
    }

    // Suspend the session and close the transport. The client may reconnect with the same session ID to resume if it wants
    void processError(SessionReference sessionRef) {
        sessionService.unbindSession(sessionRef.getSession());
    }

    // process client disconnect
    void processCompleted(SessionReference sessionRef) {
        sessionService.unbindSession(sessionRef.getSession());
    }

    void processLeaveMessage(SessionReference sessionRef, ClientMessage clientMessage) {
        if (sessionRef.getSession() != null) {
            Session session = sessionRef.getSession();
            session.getParty().removeSession(session);

            session.queueMessage(buildAckMessage(clientMessage.getMessageId()));
            session.sendQueuedMessages();

            sessionService.deleteSession(session);

            sessionRef.setSession(null);
        }
    }

    void processJoinMessage(SessionReference sessionRef, ClientMessage clientMessage, StreamObserver<ClientMessage> responseObserver) {
        if (sessionRef.getSession() != null) {
            return;
        }

        Party party = partyService.getParty(clientMessage.getPartyId());
        Client client = clientService.getClient(clientMessage.getClientId());
        // TODO - null checks for party and client. (irrelevant here because there's no special party/client creation routine)

        if (sessionService.hasSession(clientMessage.getSessionId())) {
            Session existingSession = sessionService.getSession(clientMessage.getSessionId());
            if (!existingSession.getClient().equals(client)) {
                log.warn("Cannot create a session for {} on responseObserver {} because {} already exists", client, responseObserver.hashCode(), existingSession);
                responseObserver.onNext(getBuilderFor(ClientMessageType.ERROR)
                        .setSessionId(clientMessage.getSessionId())
                        .setStatusCode(StatusCode.SESSION_ID_IN_USE).build());
                responseObserver.onCompleted();
                return;
            }
        }

        Session session = sessionService.createOrResumeSession(clientMessage.getSessionId(), client, party, responseObserver);
        session.queueMessage(buildAckMessage(clientMessage.getMessageId()));
        session.sendQueuedMessages();

        party.addSession(session);

        sessionRef.setSession(session);
    }

    void processAckMessage(SessionReference sessionRef, ClientMessage clientMessage) {
        if (sessionRef.getSession() != null) {
            clientMessage.getAckMessageIdsList().forEach(id -> sessionRef.getSession().ackMessage(id));
        }
    }

    void processPingMessage(SessionReference sessionRef, ClientMessage clientMessage) {
        if (sessionRef.getSession() != null) {
            sessionRef.getSession().queueMessage(buildAckMessage(clientMessage.getMessageId()));
            sessionRef.getSession().sendQueuedMessages();
        }
    }
}
