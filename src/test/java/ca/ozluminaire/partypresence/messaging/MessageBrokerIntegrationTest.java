package ca.ozluminaire.partypresence.messaging;

import ca.ozluminaire.partypresence.ClientMessage;
import ca.ozluminaire.partypresence.ClientMessageType;
import ca.ozluminaire.partypresence.StatusCode;
import ca.ozluminaire.partypresence.client.TestClient;
import ca.ozluminaire.partypresence.client.TestClientFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static java.util.stream.Collectors.toSet;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertAll;

@Slf4j
class MessageBrokerIntegrationTest {

    private static final Random random = new Random();
    private static TestClientFactory testClientFactory;

    public static final long PARTY_ID = 999L;

    @BeforeAll
    static void setup() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        testClientFactory = new TestClientFactory(embeddedServer.getHost() + ":" + embeddedServer.getPort());
    }

    @Test
    void test() {
        // Create test clients and join all clients to the same party
        final int clientCount = 200;

        LongStream.range(1, clientCount + 1).boxed()
                .forEach(id -> testClientFactory.create(id).sendMessage(joinMessage(id, PARTY_ID, random.nextLong())));

        // wait for all clients to know all the other clients
        waitALittleUntil(() -> testClientFactory.getAll().stream()
                .map(TestClient::getClientSetSize).allMatch(size -> size == testClientFactory.getTotalSize()));

        // all clients should receive ack, list and join notifications
        for (TestClient client : testClientFactory.getAll()) {
            assertAll("client with id=" + client.getId(),
                    () -> assertThat("got ack in first message",
                            client.getReceivedMessages().get(0).getMessageType(), is(ClientMessageType.ACK)),
                    () -> assertThat("got list in second message",
                            client.getReceivedMessages().get(1).getMessageType(), is(ClientMessageType.LIST)),
                    () -> assertThat("received all IDs",
                            client.getClientSet(), is(equalTo(testClientFactory.getIds())))
            );
        }

        testClientFactory.clearAllMessages();

        // gracefully disconnect half the clients
        final Set<Long> gracefulDisconnectIds = testClientFactory.getRandomIdsWithSize(testClientFactory.getTotalSize() / 2);
        gracefulDisconnectIds.forEach(client -> testClientFactory.get(client).sendMessage(leaveMessage()));
        waitALittleUntil(() -> gracefulDisconnectIds.stream().map(testClientFactory::get).allMatch(TestClient::isCompleted));

        // remove the disconnected clients
        gracefulDisconnectIds.forEach(testClientFactory::remove);

        // wait for message propagation
        waitALittleUntil(() -> testClientFactory.getAll().stream().map(TestClient::getClientSetSize).allMatch(size -> size == testClientFactory.getIds().size()));

        // the other clients should receive N leave messages, N = gracefulDisconnectIds.size()
        for (TestClient client : testClientFactory.getAll()) {
            assertAll("client with id " + client.getId(),
                    () -> assertThat("should receive messages equal to disconnect set size",
                            client.getReceivedMessages(), hasSize(gracefulDisconnectIds.size())),
                    () -> assertThat("all should be LEAVE messages",
                            client.streamMessages().map(ClientMessage::getMessageType).collect(toSet()), everyItem(is(ClientMessageType.LEAVE))),
                    () -> assertThat("messages should have IDs in the disconnect set",
                            client.streamMessages().map(ClientMessage::getClientId).collect(toSet()), is(equalTo(gracefulDisconnectIds)))
            );
        }

        testClientFactory.clearAllMessages();

        // a client can't use an existing session ID
        final long duplicateSessionId = random.nextLong();
        Set<Long> sessionCollisionClientIds = LongStream.range(500000, 500000 + clientCount / 2).boxed().collect(toSet());
        sessionCollisionClientIds.forEach(id -> testClientFactory.create(id).sendMessage(joinMessage(id, 999L, duplicateSessionId)));

        // due to the deterministic nature of async calls we're making, we can't tell which client will get the session id
        // one of them will be fine, the others will be kicked off when the dupe session ID comes in
        Set<TestClient> sessionCollisionClients = sessionCollisionClientIds.stream().map(testClientFactory::get).collect(Collectors.toSet());
        waitALittleUntil(() -> sessionCollisionClients.stream().filter(TestClient::isCompleted).count() == (long) sessionCollisionClientIds.size() - 1);

        TestClient sessionCollisionFirstClient = sessionCollisionClients.stream().filter(client -> !client.isCompleted()).findAny().orElseThrow();
        sessionCollisionClients.remove(sessionCollisionFirstClient);

        for (TestClient client : sessionCollisionClients) {
            assertAll("all the clients using a duplicate session ID",
                    () -> assertThat("received 1 message",
                            client.getReceivedMessages(), hasSize(1)),
                    () -> assertThat("got an error message",
                            client.getReceivedMessages().get(0).getMessageType(), is(ClientMessageType.ERROR)),
                    () -> assertThat("with status code set",
                            client.getReceivedMessages().get(0).getStatusCode(), is(StatusCode.SESSION_ID_IN_USE)),
                    () -> assertThat("was disconnected",
                            client.isCompleted(), is(true)));
            testClientFactory.remove(client.getId());
        }
        assertAll("the client who got there first",
                () -> assertThat("is still connected",
                        sessionCollisionFirstClient.isCompleted(), is(false)),
                () -> assertThat("got an ack message",
                        sessionCollisionFirstClient.getReceivedMessages().get(0).getMessageType(), is(ClientMessageType.ACK)),
                () -> assertThat("got a LIST message",
                        sessionCollisionFirstClient.getReceivedMessages().get(1).getMessageType(), is(ClientMessageType.LIST)),
                () -> assertThat("knows about all other clients",
                        sessionCollisionFirstClient.getClientSet(), is(equalTo(testClientFactory.getIds()))));

        // boot the last client
        sessionCollisionFirstClient.sendMessage(leaveMessage());
        waitALittleUntil(sessionCollisionFirstClient::isCompleted);
        testClientFactory.remove(sessionCollisionFirstClient.getId());
        testClientFactory.clearAllMessages();

        // Test session resume functionality - add some more clients
        Map<Long, Long> streamCloseIds = new HashMap<>();
        LongStream.range(10000, 10000 + clientCount).boxed().forEach(id -> {
            streamCloseIds.put(id, random.nextLong());
            testClientFactory.create(id).sendMessage(joinMessage(id, PARTY_ID, streamCloseIds.get(id)));
        });
        // wait till they get all the messages
        waitALittleUntil(() -> streamCloseIds.keySet().stream()
                .map(testClientFactory::get)
                .map(TestClient::getClientSetSize)
                .allMatch(size -> size == testClientFactory.getTotalSize()));

        // close the new clients streams without LEAVE, then resume with the same session ID
        streamCloseIds.keySet().forEach(id -> testClientFactory.get(id).closeStream());
        waitALittleUntil(() -> streamCloseIds.keySet().stream().map(testClientFactory::get).allMatch(TestClient::isCompleted));
        streamCloseIds.keySet().forEach(id -> {
            testClientFactory.remove(id);
            testClientFactory.create(id).sendMessage(joinMessage(id, PARTY_ID, streamCloseIds.get(id)));
        });

        // wait until all clients have all the entries
        waitALittleUntil(() -> testClientFactory.getAll().stream()
                .allMatch(client -> client.getClientSetSize() == testClientFactory.getTotalSize()));

        // no client should have received a leave message
        for (TestClient client : testClientFactory.getAll()) {
            assertThat("client " + client.getId() + " did not receive a leave message",
                    client.getMatchingMessages(message -> message.getMessageType() == ClientMessageType.LEAVE), is(empty()));
        }
        // the closed and rejoined clients should have received a full list and join messages
        for (Long id : streamCloseIds.keySet()) {
            TestClient client = testClientFactory.get(id);
            assertAll("Client with id = " + id,
                    () -> assertThat("got one LIST message",
                            client.getMatchingMessages(message -> message.getMessageType() == ClientMessageType.LIST), hasSize(1)),
                    () -> assertThat("has been informed of all clients",
                            client.getClientSet(), is(equalTo(testClientFactory.getIds())))
            );
        }

        testClientFactory.clearAllMessages();

        // send ping from half the clients, let the others time out and close
        Set<Long> keepaliveTimeoutIds = testClientFactory.getRandomIdsWithSize(testClientFactory.getTotalSize() / 2);
        Set<Long> keepalivePingIds = new HashSet<>(testClientFactory.getIds());
        keepalivePingIds.removeAll(keepaliveTimeoutIds);

        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(clientCount / 2);
        keepalivePingIds.forEach(id -> executor.schedule(() -> testClientFactory.get(id).sendMessage(pingMessage()),
                random.nextInt(5000) + 1000, TimeUnit.MILLISECONDS));

        //wait for clients to disconnect (keepalive timeout in test is 10 seconds)
        waitALittleMoreUntil(() -> keepaliveTimeoutIds.stream().map(testClientFactory::get).allMatch(TestClient::isCompleted));
        keepaliveTimeoutIds.forEach(testClientFactory::remove);

        // check remaining clients
        for (TestClient client : testClientFactory.getAll()) {
            assertAll("client id " + client.getId(),
                    () -> assertThat("is one of the kept alive IDs",
                            client.getId(), is(in(keepalivePingIds))),
                    () -> assertThat("is not one of the timed-out IDs",
                            client.getId(), is(not(in(keepaliveTimeoutIds)))),
                    () -> assertThat("received the right count of messages",
                            client.getReceivedMessages(), hasSize(keepalivePingIds.size() + 1)),
                    () -> assertThat("first being an ACK message",
                            client.getReceivedMessages().get(0).getMessageType(), is(ClientMessageType.ACK)),
                    () -> assertThat("rest being LEAVE messages",
                            client.getMatchingMessages(message -> message.getMessageType() == ClientMessageType.LEAVE), hasSize(keepaliveTimeoutIds.size())),
                    () -> assertThat("with IDs in the left for dead set",
                            client.getMatchingMessages(message -> message.getMessageType() == ClientMessageType.LEAVE).stream()
                                    .map(ClientMessage::getClientId).collect(toSet()), is(equalTo(keepaliveTimeoutIds))),
                    () -> assertThat("with timeout status code",
                            client.getMatchingMessages(message -> message.getMessageType() == ClientMessageType.LEAVE).stream()
                                    .map(ClientMessage::getStatusCode).collect(toSet()), everyItem(is(StatusCode.CLIENT_TIMEOUT)))
            );
        }
    }

    @AfterAll
    static void teardown() {
        testClientFactory.forAll(client -> client.sendMessage(leaveMessage()));
    }

    private static ClientMessage joinMessage(long clientId, long partyId, long sessionId) {
        return ClientMessage.newBuilder()
                .setClientId(clientId).setPartyId(partyId).setSessionId(sessionId)
                .setMessageId(random.nextLong()).setMessageType(ClientMessageType.JOIN).build();
    }

    private static ClientMessage leaveMessage() {
        return ClientMessage.newBuilder()
                .setMessageId(random.nextLong()).setMessageType(ClientMessageType.LEAVE).build();
    }

    private static ClientMessage pingMessage() {
        return ClientMessage.newBuilder().setMessageType(ClientMessageType.PING).setMessageId(random.nextLong()).build();
    }

    private static void waitALittleUntil(Callable<Boolean> condition) {
        await().atMost(20, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(condition);
    }

    private static void waitALittleMoreUntil(Callable<Boolean> condition) {
        await().atMost(60, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(condition);
    }
}