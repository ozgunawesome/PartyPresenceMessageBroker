package ca.ozluminaire.partypresence.util;

import ca.ozluminaire.partypresence.ClientMessage;
import ca.ozluminaire.partypresence.ClientMessageType;

import java.util.Random;

public class ClientMessageUtil {

    private static final Random random = new Random();
    public static ClientMessage buildAckMessage(long messageId) {
        return ClientMessage.newBuilder()
                .setMessageType(ClientMessageType.ACK)
                .setTimestamp(System.currentTimeMillis())
                .addAckMessageIds(messageId)
                .build();
    }

    public static ClientMessage.Builder getBuilderFor(ClientMessageType type) {
        return ClientMessage.newBuilder()
                .setMessageType(type)
                .setMessageId(random.nextLong())
                .setTimestamp(System.currentTimeMillis());
    }

}
