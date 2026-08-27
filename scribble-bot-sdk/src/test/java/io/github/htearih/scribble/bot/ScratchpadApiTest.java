package io.github.htearih.scribble.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.htearih.scribble.bot.model.ScratchpadLayerMessage;
import io.github.htearih.scribble.bot.model.ScratchpadObjectMessage;
import io.github.htearih.scribble.bot.model.ScratchpadSessionMetaMessage;
import io.github.htearih.scribble.bot.model.UnknownRoomMessage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The per-app read endpoints upstream introduced in 0.4.0, against a real HTTP server on loopback.
 *
 * <p>The paths are the point of these tests: 0.4.0 split the room-wide {@code /state} and
 * {@code /preview} into {@code /scratchpad/state} and {@code /scratchpad/preview}, and a bot that
 * calls the old ones reads nothing.
 */
class ScratchpadApiTest {

    private static final String TOKEN = "test-secret-token";

    private static final String STATE_BODY =
            """
            {"messages":[\
            {"type":"sp.sessionMeta","currentSession":3,"eventId":10,"seqId":167,"canvasWidth":1000,"canvasHeight":700},\
            {"type":"sp.layer","layerId":1,"frames":[100],"lastEventId":11},\
            {"type":"sp.layerOrder","order":[1],"lastEventId":-1},\
            {"type":"sp.object","objectId":5,"frameId":100,"eventId":12,"objectType":"line.floats",\
            "rgba":16711935,"lineWidth":2.5,"points":[1.0,2.0,3.0,4.0]},\
            {"type":"sp.brandNewThing","whatever":true},\
            {"type":"sp.lastEventId","lastEventId":42}]}""";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void readsTheScratchpadStateFromThePerAppEndpoint() throws IOException {
        var seen = new AtomicReference<HttpExchange>();
        server = respondWith(seen, 200, STATE_BODY.getBytes(StandardCharsets.UTF_8), "application/json");

        var response = bot().getScratchpadStateMessages("Main");

        assertThat(seen.get().getRequestMethod()).isEqualTo("GET");
        assertThat(seen.get().getRequestURI().getPath()).isEqualTo("/api/v0/room/Main/scratchpad/state");
        assertThat(seen.get().getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer " + TOKEN);

        assertThat(response.messages()).hasSize(6);
        assertThat(response.messages().get(0)).isInstanceOf(ScratchpadSessionMetaMessage.class);
        assertThat(response.messages().get(1)).isInstanceOf(ScratchpadLayerMessage.class);
        // A type this SDK version has never heard of must not fail the fetch.
        assertThat(response.messages().get(4))
                .isInstanceOf(UnknownRoomMessage.class)
                .extracting(m -> m.type())
                .isEqualTo("sp.brandNewThing");
    }

    @Test
    void reducesTheStateIntoSomethingDrawable() throws IOException {
        server = respondWith(
                new AtomicReference<>(), 200, STATE_BODY.getBytes(StandardCharsets.UTF_8), "application/json");

        var state = bot().getScratchpadState("main");

        assertThat(state.sessionMeta().seqId()).isEqualTo(167);
        assertThat(state.layerOrder()).containsExactly(1L);
        assertThat(state.lastEventId()).isEqualTo(42);

        var object = state.objects().get(5L);
        assertThat(object.isLineFloats()).isTrue();
        assertThat(object.lineWidth()).isEqualTo(2.5);
        assertThat(object.points()).containsExactly(1.0, 2.0, 3.0, 4.0);
        assertThat(state.frames().get(100L).objects()).containsExactly(object);
    }

    @Test
    void encodesTheRoomNameInTheScratchpadPath() throws IOException {
        var seen = new AtomicReference<HttpExchange>();
        server = respondWith(seen, 200, "{\"messages\":[]}".getBytes(StandardCharsets.UTF_8), "application/json");

        bot().getScratchpadStateMessages("room with spaces");

        assertThat(seen.get().getRequestURI().getRawPath())
                .isEqualTo("/api/v0/room/room%20with%20spaces/scratchpad/state");
    }

    @Test
    void fetchesThePreviewPngAndItsValidator() throws IOException {
        var seen = new AtomicReference<HttpExchange>();
        server = startServer(exchange -> {
            seen.set(exchange);
            exchange.getResponseHeaders().add("Last-Modified", "Mon, 17 Aug 2026 13:43:50 GMT");
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, 4);
            try (var out = exchange.getResponseBody()) {
                out.write(new byte[] {1, 2, 3, 4});
            }
        });

        var preview = bot().getScratchpadPreviewImage("main", "Sun, 16 Aug 2026 00:00:00 GMT");

        assertThat(seen.get().getRequestURI().getPath()).isEqualTo("/api/v0/room/main/scratchpad/preview");
        assertThat(seen.get().getRequestHeaders().getFirst("If-Modified-Since"))
                .isEqualTo("Sun, 16 Aug 2026 00:00:00 GMT");
        assertThat(preview.image()).containsExactly(1, 2, 3, 4);
        assertThat(preview.lastModified()).isEqualTo("Mon, 17 Aug 2026 13:43:50 GMT");
    }

    @Test
    void returnsNothingWhenThePreviewHasNotChanged() throws IOException {
        server = startServer(exchange -> exchange.sendResponseHeaders(304, -1));

        assertThat(bot().getScratchpadPreviewImage("main", "Sun, 16 Aug 2026 00:00:00 GMT"))
                .isNull();
    }

    @Test
    void sendsNoValidatorHeaderWhenThereIsNothingToValidate() throws IOException {
        var seen = new AtomicReference<HttpExchange>();
        server = respondWith(seen, 200, new byte[] {1}, "image/png");

        bot().getScratchpadPreviewImage("main");

        assertThat(seen.get().getRequestHeaders().getFirst("If-Modified-Since")).isNull();
    }

    @Test
    void fetchesTheLogoWithItsEtag() throws IOException {
        var seen = new AtomicReference<HttpExchange>();
        server = startServer(exchange -> {
            seen.set(exchange);
            exchange.getResponseHeaders().add("ETag", "\"abc123\"");
            exchange.sendResponseHeaders(200, 2);
            try (var out = exchange.getResponseBody()) {
                out.write(new byte[] {9, 9});
            }
        });

        var logo = bot().getLogoImage(ScribblePubBot.LogoTheme.DARK, "\"stale\"");

        assertThat(seen.get().getRequestURI().getPath()).isEqualTo("/api/v0/logo");
        assertThat(seen.get().getRequestURI().getQuery()).isEqualTo("theme=dark");
        assertThat(seen.get().getRequestHeaders().getFirst("If-None-Match")).isEqualTo("\"stale\"");
        assertThat(logo.image()).containsExactly(9, 9);
        assertThat(logo.etag()).isEqualTo("\"abc123\"");
    }

    @Test
    void asksForTheLightLogoWithoutAThemeParameter() throws IOException {
        var seen = new AtomicReference<HttpExchange>();
        server = respondWith(seen, 200, new byte[] {1}, "image/png");

        bot().getLogoImage();

        assertThat(seen.get().getRequestURI().getQuery()).isNull();
        assertThat(seen.get().getRequestHeaders().getFirst("If-None-Match")).isNull();
    }

    @Test
    void returnsNothingWhenTheLogoHasNotChanged() throws IOException {
        server = startServer(exchange -> exchange.sendResponseHeaders(304, -1));

        assertThat(bot().getLogoImage(ScribblePubBot.LogoTheme.LIGHT, "\"same\"")).isNull();
    }

    @Test
    void unwrapsTheApiErrorMessage() throws IOException {
        server = respondWith(
                new AtomicReference<>(),
                404,
                "{\"error\":\"Room is not found\"}".getBytes(StandardCharsets.UTF_8),
                "application/json");

        assertThatThrownBy(() -> bot().getScratchpadStateMessages("nope"))
                .isInstanceOf(ScribblePubApiError.class)
                .hasMessageContaining("Room is not found")
                .satisfies(thrown -> assertThat(((ScribblePubApiError) thrown).getStatus())
                        .isEqualTo(404));
    }

    @Test
    void refusesABlankRoomBeforeMakingARequest() {
        var bot = ScribblePubBot.builder().token(TOKEN).baseUrl("http://127.0.0.1:1").build();

        assertThatThrownBy(() -> bot.getScratchpadStateMessages(" "))
                .isInstanceOf(ScribblePubValidationError.class);
        assertThatThrownBy(() -> bot.getScratchpadPreviewImage(null))
                .isInstanceOf(ScribblePubValidationError.class);
    }

    private ScribblePubBot bot() {
        return ScribblePubBot.builder()
                .token(TOKEN)
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .build();
    }

    private static HttpServer startServer(HttpHandler handler) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                handler.handle(exchange);
            }
        });
        server.start();
        return server;
    }

    private static HttpServer respondWith(
            AtomicReference<HttpExchange> seen, int status, byte[] body, String contentType) throws IOException {
        return startServer(exchange -> {
            seen.set(exchange);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }
}
