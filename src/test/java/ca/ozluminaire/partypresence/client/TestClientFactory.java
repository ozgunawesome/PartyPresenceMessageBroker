package ca.ozluminaire.partypresence.client;

import ca.ozluminaire.partypresence.ClientMessage;
import ca.ozluminaire.partypresence.ClientMessagingEndpointGrpc;
import com.sun.jdi.LongValue;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ManagedChannelProvider;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class TestClientFactory {

    private final Channel channel;
    private final Map<Long, TestClient> testClientMap = new ConcurrentHashMap<>();

    public TestClientFactory(String url) {
        this.channel = ManagedChannelBuilder.forTarget(url).usePlaintext().build();
    }

    public TestClient create(Long id) {
        return testClientMap.computeIfAbsent(id, key -> new TestClient(key, channel));
    }

    public TestClient get(Long id) {
        return testClientMap.get(id);
    }

    public Collection<TestClient> getAll() {
        return testClientMap.values();
    }

    public void clearAllMessages() {
        forAll(TestClient::clearMessages);
    }

    public void forAll(Consumer<TestClient> consumer) {
        testClientMap.values().forEach(consumer::accept);
    }

    public Set<Long> getIds() {
        return testClientMap.keySet();
    }

    public Set<Long> getRandomIdsWithSize(int size) {
        List<Long> list = new ArrayList<>(testClientMap.keySet());
        Collections.shuffle(list);
        return list.stream().limit(size).collect(Collectors.toSet());
    }

    public int getTotalSize() {
        return testClientMap.size();
    }

    public void remove(Long id) {
        this.testClientMap.remove(id);
    }
}
