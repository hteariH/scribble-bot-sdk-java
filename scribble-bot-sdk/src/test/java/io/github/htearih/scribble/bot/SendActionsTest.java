package io.github.htearih.scribble.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.htearih.scribble.bot.model.Action;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** {@link ScribblePubBot#sendActions(String, List)} against real HTTP servers on loopback. */
class SendActionsTest {

    private static final String TOKEN = "test-secret-token";
    private static final String MENTION = """
            {"trigger":{"trigger":"chat.mention","text":"@mary hi","room":"Main",\
            "timestamp":1779999999999,"username":"you","directUrl":"%s"}}""";

    private HttpServer serverA;
    private HttpServer serverB;

    @AfterEach
    void stopServers() {
        if (serverA != null) {
            serverA.stop(0);
        }
        if (serverB != null) {
            serverB.stop(0);
        }
    }

    @Test
    void postsActionsToTheRoomsActionsEndpointAsABearerRequest() throws IOException {
        var lastExchange = new AtomicReference<HttpExchange>();
        var lastBody = new AtomicReference<String>();
        serverA = jsonServer(200, "{}", lastExchange, lastBody, new AtomicInteger());

        bot(url(serverA)).sendActions("main", List.of(Action.addMessage("hi there")));

        var exchange = lastExchange.get();
        assertThat(exchange.getRequestMethod()).isEqualTo("POST");
        assertThat(exchange.getRequestURI().getPath()).isEqualTo("/api/v0/room/main/actions");
        assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer " + TOKEN);
        assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).isEqualTo("application/json");
        assertThat(lastBody.get()).isEqualTo("{\"actions\":[{\"type\":\"addMessage\",\"text\":\"hi there\"}]}");
    }

    @Test
    void encodesTheRoomNameInThePath() throws IOException {
        var lastExchange = new AtomicReference<HttpExchange>();
        serverA = jsonServer(200, "{}", lastExchange, new AtomicReference<>(), new AtomicInteger());

        bot(url(serverA)).sendActions("room with spaces", List.of(Action.addMessage("hi")));

        assertThat(lastExchange.get().getRequestURI().getRawPath())
                .isEqualTo("/api/v0/room/room%20with%20spaces/actions");
    }

    @Test
    void raisesTheApiErrorWithStatusAndBody() throws IOException {
        serverA = jsonServer(
                403, "{\"error\":\"unknown room\"}", new AtomicReference<>(), new AtomicReference<>(), new AtomicInteger());

        assertThatThrownBy(() -> bot(url(serverA)).sendActions("main", List.of(Action.addMessage("hi"))))
                .isInstanceOf(ScribblePubApiError.class)
                .hasMessageContaining("403")
                .satisfies(thrown -> {
                    var error = (ScribblePubApiError) thrown;
                    assertThat(error.getStatus()).isEqualTo(403);
                    assertThat(error.getBody()).isEqualTo("{\"error\":\"unknown room\"}");
                });
    }

    @Test
    void refusesABlankRoomOrInvalidActions() {
        var bot = bot("http://127.0.0.1:1");

        assertThatThrownBy(() -> bot.sendActions(" ", List.of(Action.addMessage("hi"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bot.sendActions("main", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bot.sendActions("main", Collections.singletonList(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usesTheDirectUrlLearnedFromAPriorHookDelivery() throws IOException {
        var hits = new AtomicInteger();
        var lastExchange = new AtomicReference<HttpExchange>();
        serverB = jsonServer(200, "{}", lastExchange, new AtomicReference<>(), hits);

        // An unreachable base URL proves the call never falls back to it: everything must be routed
        // to the instance learned from the hook delivery below.
        var bot = bot("http://127.0.0.1:1");
        var mentionBody = MENTION.formatted(url(serverB)).getBytes(StandardCharsets.UTF_8);
        bot.handleHook(mentionBody, bot.signature().sign(mentionBody));

        bot.sendActions("main", List.of(Action.addMessage("hi")));

        assertThat(hits.get()).isEqualTo(1);
        assertThat(lastExchange.get().getRequestURI().getPath()).isEqualTo("/api/v0/room/main/actions");
    }

    @Test
    void learnsTheServingInstanceFromA307Redirect() throws IOException {
        var hitsA = new AtomicInteger();
        var hitsB = new AtomicInteger();
        var lastExchangeB = new AtomicReference<HttpExchange>();
        serverB = jsonServer(200, "{}", lastExchangeB, new AtomicReference<>(), hitsB);
        var redirectTarget = url(serverB) + "/api/v0/room/chaos/actions";
        serverA = startServer(exchange -> {
            exchange.getRequestBody().readAllBytes();
            hitsA.incrementAndGet();
            exchange.getResponseHeaders().add("Location", redirectTarget);
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });

        var bot = bot(url(serverA));
        bot.sendActions("chaos", List.of(Action.addMessage("hi")));
        bot.sendActions("chaos", List.of(Action.addMessage("hi again")));

        assertThat(hitsA.get()).isEqualTo(1);
        assertThat(hitsB.get()).isEqualTo(2);
        assertThat(lastExchangeB.get().getRequestURI().getPath()).isEqualTo("/api/v0/room/chaos/actions");
    }

    private ScribblePubBot bot(String baseUrl) {
        return ScribblePubBot.builder().token(TOKEN).baseUrl(baseUrl).build();
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static HttpServer startServer(HttpHandler handler) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return server;
    }

    private static HttpServer jsonServer(
            int status, String body, AtomicReference<HttpExchange> lastExchange,
            AtomicReference<String> lastBody, AtomicInteger hits) throws IOException {
        return startServer(exchange -> {
            lastExchange.set(exchange);
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            hits.incrementAndGet();
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }
}
