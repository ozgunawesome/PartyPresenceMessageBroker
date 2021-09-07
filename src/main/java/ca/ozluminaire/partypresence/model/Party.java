package ca.ozluminaire.partypresence.model;

import ca.ozluminaire.partypresence.ClientMessage;
import ca.ozluminaire.partypresence.ClientMessageType;
import ca.ozluminaire.partypresence.ClientSessionIdEntry;
import ca.ozluminaire.partypresence.StatusCode;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import static ca.ozluminaire.partypresence.util.ClientMessageUtil.getBuilderFor;

@Slf4j
@RequiredArgsConstructor
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Party {
    @EqualsAndHashCode.Include
    @ToString.Include
    private final Long id;

    private final Set<Session> sessions = new HashSet<>();

    public void addSession(Session newSession) {
        synchronized (sessions) {
            sessions.remove(newSession);
            sessions.forEach(session -> session.queueMessage(getBuilderFor(ClientMessageType.JOIN)
                    .setClientId(newSession.getClient().getId())
                    .setSessionId(newSession.getId())
                    .build()));
            sessions.add(newSession);

            Map<Long, Set<Long>> clientSessionIdMap = new HashMap<>();
            sessions.forEach(session ->
                    clientSessionIdMap.computeIfAbsent(session.getClient().getId(), key -> new HashSet<>())
                            .add(session.getId()));

            newSession.queueMessage(getBuilderFor(ClientMessageType.LIST)
                    .addAllParticipantClientIds(clientSessionIdMap.entrySet().stream().map(entry ->
                            ClientSessionIdEntry.newBuilder()
                                    .setClientId(entry.getKey())
                                    .addAllSessionId(entry.getValue()).build()).collect(Collectors.toList()))
                    .build());

            sessions.forEach(Session::sendQueuedMessages);
        }
    }

    public void removeSession(Session session) {
        removeSession(session, StatusCode.OK);
    }

    public void removeSession(Session removedSession, StatusCode statusCode) {
        synchronized (sessions) {
            sessions.remove(removedSession);
            sessions.forEach(session -> session.queueMessage(
                    getBuilderFor(ClientMessageType.LEAVE)
                            .setClientId(removedSession.getClient().getId())
                            .setSessionId(removedSession.getId())
                            .setStatusCode(statusCode).build()));
            sessions.forEach(Session::sendQueuedMessages);
        }
    }
}
