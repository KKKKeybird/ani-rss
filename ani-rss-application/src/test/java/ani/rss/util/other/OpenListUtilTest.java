package ani.rss.util.other;

import ani.rss.commons.MavenUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OpenListUtilTest {
    private HttpServer server;

    @BeforeAll
    static void versionWithoutSpringContext() {
        ReflectionTestUtils.setField(MavenUtils.class, "version", "test");
    }

    private OpenListUtil api() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        return OpenListUtil.getInstance("http://127.0.0.1:" + server.getAddress().getPort(), "test-token");
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void retryUsesTidQueryParameter() throws IOException {
        OpenListUtil api = api();
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/api/task/offline_download/retry", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            reply(exchange, "{\"code\":200,\"message\":\"success\"}");
        });
        assertTrue(api.taskRetry("task-1"));
        assertEquals("tid=task-1", query.get());
    }

    @Test
    void cancelUsesTidQueryParameter() throws IOException {
        OpenListUtil api = api();
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/api/task/offline_download/cancel", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            reply(exchange, "{\"code\":200,\"message\":\"success\"}");
        });
        assertTrue(api.taskCancel("task-1"));
        assertEquals("tid=task-1", query.get());
    }

    @Test
    void batchDeleteReportsPerTaskFailure() throws IOException {
        OpenListUtil api = api();
        server.createContext("/api/task/offline_download/delete_some", exchange ->
                reply(exchange, "{\"code\":200,\"data\":{\"task-1\":\"task not found\"}}"));
        assertFalse(api.taskDelete("task-1"));
    }

    @Test
    void moveWaitsForReturnedBackgroundTask() throws IOException {
        OpenListUtil api = api();
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/api/fs/move", exchange ->
                reply(exchange, "{\"code\":200,\"data\":{\"tasks\":[{\"id\":\"move-1\"}]}}"));
        server.createContext("/api/task/move/info", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            reply(exchange, "{\"code\":200,\"data\":{\"id\":\"move-1\",\"state\":2}}");
        });
        api.fsMoveAndWait("/from", "/to", List.of("episode.mkv"),
                System.currentTimeMillis() + 5000);
        assertEquals("tid=move-1", query.get());
    }

    private static void reply(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
