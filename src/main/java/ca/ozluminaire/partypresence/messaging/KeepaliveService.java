package ca.ozluminaire.partypresence.messaging;

import ca.ozluminaire.partypresence.StatusCode;
import ca.ozluminaire.partypresence.model.Session;
import ca.ozluminaire.partypresence.model.SessionState;
import ca.ozluminaire.partypresence.service.SessionService;
import io.micronaut.context.annotation.Value;
import io.netty.util.HashedWheelTimer;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class KeepaliveService {

    @Value("${party-presence-message-broker.keepalive-timeout:120}")
    private Long keepaliveTimeout;

    // A fast, approximate timer implementation for a large number of requests. Common in I/O timeout scheduling.
    // The default constructor creates a timer with 100 ms tick duration, which is more than enough
    // for more info see https://github.com/wangjia184/HashedWheelTimer
    private final HashedWheelTimer timer = new HashedWheelTimer();

    private final SessionService sessionService;
    public KeepaliveService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public void setKeepaliveTimer(SessionReference sessionReference) {
        final Session session = sessionReference.getSession();
        if (session != null) {
            if (session.getTimeout() != null && !session.getTimeout().isCancelled()) {
                session.getTimeout().cancel();
            }
            session.setTimeout(timer.newTimeout(timeout -> {
                log.warn("Closing {} due to timeout", session);
                session.setSessionState(SessionState.INACTIVE);
                session.getParty().removeSession(session, StatusCode.CLIENT_TIMEOUT);
                sessionService.deleteSession(session);

            }, keepaliveTimeout, TimeUnit.SECONDS));
        }
    }

}
