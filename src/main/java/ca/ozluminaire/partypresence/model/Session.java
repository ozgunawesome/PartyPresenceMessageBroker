package ca.ozluminaire.partypresence.model;

import ca.ozluminaire.partypresence.ClientMessage;
import io.grpc.stub.StreamObserver;
import io.netty.util.Timeout;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Session {

    @EqualsAndHashCode.Include
    @ToString.Include
    private final Long id;

    @EqualsAndHashCode.Include
    @ToString.Include
    private final Client client;

    @EqualsAndHashCode.Include
    @ToString.Include
    private final Party party;

    @Setter
    @ToString.Include
    private SessionState sessionState = SessionState.NEW;

    @Setter
    private StreamObserver<ClientMessage> responseObserver; // reference to gRPC client stream

    // outgoing message queue for this session
    private final Deque<ClientMessage> outgoingMessages = new ConcurrentLinkedDeque<>();

    // inflight messages for this session
    private final Map<Long, ClientMessage> inflightMessages = new ConcurrentHashMap<>();

    // keepalive timer
    @Setter
    private Timeout timeout;

    public synchronized void close() {
        if (timeout != null && !timeout.isCancelled()) {
            timeout.cancel();
        }
        if (responseObserver != null) {
            responseObserver.onCompleted();
        }

        clearQueues();

        timeout = null;
        responseObserver = null;
        sessionState = SessionState.EXPIRED;
    }

    public void queueMessage(ClientMessage message) {
        if (sessionState == SessionState.ACTIVE) {
            outgoingMessages.offer(message);
        }
    }

    public void clearQueues() {
        if (sessionState != SessionState.ACTIVE) {
            inflightMessages.clear();
            outgoingMessages.clear();
        }
    }

    public synchronized void sendQueuedMessages() {
        while (sessionState == SessionState.ACTIVE && responseObserver != null && !outgoingMessages.isEmpty()) {
            ClientMessage message = outgoingMessages.poll();
            responseObserver.onNext(message);
            inflightMessages.put(message.getMessageId(), message);
        }
    }

    public void ackMessage(long messageId) {
        inflightMessages.remove(messageId);
    }
    // TODO add retries for inflight ack deadline exceeded messages
    // TODO add retry count and a dead letter queue
}
