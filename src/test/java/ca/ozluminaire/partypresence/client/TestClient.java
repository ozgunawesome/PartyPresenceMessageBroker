package ca.ozluminaire.partypresence.client;

import ca.ozluminaire.partypresence.ClientMessage;
import ca.ozluminaire.partypresence.ClientMessageType;
import ca.ozluminaire.partypresence.ClientMessagingEndpointGrpc;
import ca.ozluminaire.partypresence.ClientSessionIdEntry;
import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@Slf4j
public class TestClient {

    private final long id;
    private final StreamObserver<ClientMessage> streamObserver;
    private final List<ClientMessage> receivedMessages = new ArrayList<>();
    private final List<Throwable> receivedErrors = new ArrayList<>();
    private final Set<Long> clientSet = new HashSet<>();
    private boolean isCompleted = false;

    TestClient(long id, Channel channel) {
        this.id = id;
        this.streamObserver = ClientMessagingEndpointGrpc.newStub(channel).beginStream(new StreamObserver<>() {
            @Override
            public void onNext(ClientMessage value) {
                log.debug("Client id {} received:\n{}", id, value);
                receivedMessages.add(value);
                if (value.getMessageType() == ClientMessageType.LIST) {
                    clientSet.clear();
                    clientSet.addAll(value.getParticipantClientIdsList().stream()
                            .map(ClientSessionIdEntry::getClientId).collect(Collectors.toSet()));
                } else if (value.getMessageType() == ClientMessageType.JOIN) {
                    clientSet.add(value.getClientId());
                } else if (value.getMessageType() == ClientMessageType.LEAVE) {
                    clientSet.remove(value.getClientId());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("test client id = {} received an error: {}", id, Status.fromThrowable(t));
                receivedErrors.add(t);
            }

            @Override
            public void onCompleted() {
                isCompleted = true;
            }
        });
    }

    public void sendMessage(ClientMessage message) {
        this.streamObserver.onNext(message);
    }

    public void closeStream() {
        this.streamObserver.onCompleted();
        isCompleted = true;
    }

    public int getClientSetSize() {
        return clientSet.size();
    }

    public void clearMessages() {
        receivedMessages.clear();
    }

    public Stream<ClientMessage> streamMessages() {
        return this.receivedMessages.stream();
    }

    public Collection<ClientMessage> getMatchingMessages(Predicate<ClientMessage> predicate) {
        return this.getReceivedMessages().stream().filter(predicate).collect(Collectors.toList());
    }

}
