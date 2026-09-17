package Timeout.travel_tackle.notification.sse;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 사용자별 SSE 연결 보관소. 탭을 여러 개 열 수 있으니 사용자 하나에 emitter 여러 개를 허용한다.
 * 서버 한 대 기준의 메모리 구현이며, 다중 인스턴스가 되면 Redis pub/sub 등으로 전파를 추가해야 한다.
 */
@Slf4j
@Component
public class NotificationSseRegistry {

    private static final long TIMEOUT_MILLIS = 30L * 60 * 1000;
    private static final long HEARTBEAT_SECONDS = 25; // 프록시(ALB/CloudFront) 유휴 타임아웃보다 짧게

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public NotificationSseRegistry() {
        heartbeat.scheduleAtFixedRate(this::sendHeartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    public SseEmitter connect(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        // 등록과 제거를 같은 compute 안에서 처리해, 제거 중인 빈 리스트에 새 연결이 붙어 유실되는 경합을 막는다
        emitters.compute(userId, (id, list) -> {
            List<SseEmitter> target = list == null ? new CopyOnWriteArrayList<>() : list;
            target.add(emitter);
            return target;
        });
        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    public void send(UUID userId, String eventName, Object data) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                remove(userId, emitter);
            }
        }
    }

    public int connectionCount(UUID userId) {
        List<SseEmitter> list = emitters.get(userId);
        return list == null ? 0 : list.size();
    }

    private void sendHeartbeat() {
        emitters.forEach((userId, list) -> send(userId, "heartbeat", "ping"));
    }

    private void remove(UUID userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }

    @PreDestroy
    void shutdown() {
        heartbeat.shutdownNow();
        emitters.values().forEach(list -> list.forEach(SseEmitter::complete));
    }
}
