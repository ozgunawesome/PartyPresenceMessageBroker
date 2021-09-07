package ca.ozluminaire.partypresence.messaging;

import ca.ozluminaire.partypresence.ClientMessage;
import ca.ozluminaire.partypresence.ClientMessagingEndpointGrpc;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ClientMessagingEndpoint extends ClientMessagingEndpointGrpc.ClientMessagingEndpointImplBase {

    private final ClientMessageProcessor clientMessageProcessor;

    public ClientMessagingEndpoint(ClientMessageProcessor clientMessageProcessor) {
        this.clientMessageProcessor = clientMessageProcessor;
    }

    @Override
    public StreamObserver<ClientMessage> beginStream(StreamObserver<ClientMessage> responseObserver) {

        return new StreamObserver<>() {

            private final SessionReference sessionRef = new SessionReference();

            @Override
            public void onNext(ClientMessage value) {
                log.debug("Message received from {}, content: {}", responseObserver.hashCode(), value.toString());
                clientMessageProcessor.processMessage(sessionRef, responseObserver, value);
            }

            @Override
            public void onError(Throwable t) {
                log.warn("An error occurred in {}, cause: {}", responseObserver.hashCode(), t.getMessage());
                clientMessageProcessor.processError(sessionRef);
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                /* Stream commit events without a LEAVE message are still treated as an ungraceful leave.
                   The client may re-open the transport with the same session ID and resume the session
                   if session TTL has not expired.
                   Implicit LEAVE messages will delete the session and then close the transport. */
                log.warn("The stream closed for {}", responseObserver.hashCode());
                clientMessageProcessor.processCompleted(sessionRef);
                responseObserver.onCompleted();
            }
        };
    }

}
