package io.github.htearih.scribble.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link ScribblePubBot#registerWebhook(String)} against a real HTTP server on loopback. */
class RegisterWebhookTest {

    private static final String TOKEN = "test-secret-token";

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<HttpExchange> lastExchange = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> response = new AtomicReference<>("{\"ok\":true}");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastExchange.set(exchange);
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var body = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postsTheUrlToTheRegisterEndpointAsABearerRequest() {
        bot().registerWebhook("https://bots.example.com/webhook");

        var exchange = lastExchange.get();
        assertThat(exchange.getRequestMethod()).isEqualTo("POST");
        assertThat(exchange.getRequestURI().getPath()).isEqualTo(ScribblePubBot.REGISTER_WEBHOOK_PATH);
        assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer " + TOKEN);
        assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).isEqualTo("application/json");
        assertThat(lastBody.get()).isEqualTo("{\"url\":\"https://bots.example.com/webhook\"}");
    }

    @Test
    void trailingSlashesInTheBaseUrlDoNotDoubleUp() {
        ScribblePubBot.builder().token(TOKEN).baseUrl(baseUrl + "/").build()
                .registerWebhook("https://bots.example.com/webhook");

        assertThat(lastExchange.get().getRequestURI().getPath())
                .isEqualTo(ScribblePubBot.REGISTER_WEBHOOK_PATH);
    }

    @Test
    void raisesTheApiErrorWithStatusAndBody() {
        status.set(403);
        response.set("{\"error\":\"unknown bot\"}");

        assertThatThrownBy(() -> bot().registerWebhook("https://bots.example.com/webhook"))
                .isInstanceOf(ScribblePubApiError.class)
                .hasMessageContaining("403")
                .satisfies(thrown -> {
                    var error = (ScribblePubApiError) thrown;
                    assertThat(error.getStatus()).isEqualTo(403);
                    assertThat(error.getBody()).isEqualTo("{\"error\":\"unknown bot\"}");
                });
    }

    @Test
    void refusesUrlsThePlatformCouldNotCall() {
        assertThatThrownBy(() -> bot().registerWebhook("ftp://example.com/hook"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bot().registerWebhook(" "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(lastExchange.get()).isNull();
    }

    private ScribblePubBot bot() {
        return ScribblePubBot.builder().token(TOKEN).baseUrl(baseUrl).build();
    }
}
