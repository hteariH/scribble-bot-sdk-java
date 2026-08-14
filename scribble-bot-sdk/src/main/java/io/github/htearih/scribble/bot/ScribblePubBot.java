package io.github.htearih.scribble.bot;

import io.github.htearih.scribble.bot.json.Json;
import io.github.htearih.scribble.bot.model.Action;
import io.github.htearih.scribble.bot.model.HookRequest;
import io.github.htearih.scribble.bot.model.HookResponse;
import io.github.htearih.scribble.bot.model.RegisterWebhookPayload;
import io.github.htearih.scribble.bot.security.WebhookSignature;
import io.github.htearih.scribble.bot.text.Mentions;
import io.github.htearih.scribble.bot.text.PlainText;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A scribble.pub bot: verifies deliveries, dispatches them to your handler, and registers the
 * webhook URL with the platform. The Java counterpart of {@code ScribblePubBot} in
 * <a href="https://github.com/scribble-pub/bot-sdk">scribble-pub/bot-sdk</a>, which is the
 * normative spec for this API.
 *
 * <pre>{@code
 * var bot = ScribblePubBot.builder()
 *         .token(System.getenv("SCRIBBLE_BOT_TOKEN"))
 *         .handle("mary")
 *         .build()
 *         .onMention(mention -> "Hi " + mention.username() + "! You said: " + mention.text());
 *
 * // in your HTTP layer, with the *raw* body bytes:
 * var result = bot.handleHook(rawBody, request.getHeader(WebhookSignature.HEADER));
 * respond(result.status(), bot.toJson(result.body()));
 * }</pre>
 *
 * <p><strong>The reply is the HTTP response.</strong> scribble.pub has no outbound endpoint: there
 * is no way to post into a room later, and the platform hangs up around ten seconds after the
 * delivery. A handler that answers late does not answer at all — budget for it.
 *
 * <p>Thread-safe. Handlers may be swapped at runtime; {@link #handleHook} reads the current one.
 */
public final class ScribblePubBot {

    /** The platform's public origin. */
    public static final String DEFAULT_BASE_URL = "https://scribble.pub";

    /** Where {@link #registerWebhook(String)} POSTs. */
    public static final String REGISTER_WEBHOOK_PATH = "/api/v0/bot/webhook/register";

    /** The only event {@link #on(String, HookHandler)} accepts. */
    public static final String HOOK_EVENT = "hook";

    /**
     * Pre-set production instances serving the most common public rooms, to avoid losing time on a
     * 307 redirect to the instance that actually hosts them. Only used when {@link #baseUrl} is
     * {@value #DEFAULT_BASE_URL}.
     */
    private static final Map<String, String> DEFAULT_ROOM_INSTANCES = Map.of(
            "main", "https://eu.scribble.pub",
            "sandbox", "https://eu.scribble.pub",
            "chaos", "https://eu.scribble.pub",
            "prosto_kot", "https://ap.scribble.pub");

    private static final Logger log = LoggerFactory.getLogger(ScribblePubBot.class);

    private final String token;
    private final String baseUrl;
    private final String handle;
    private final Pattern handlePattern;
    private final int maxMessageLength;
    private final WebhookSignature signature;
    private final Json json;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    /**
     * Root instance lookup for rooms this bot has seen, keyed by lowercased room name. Avoids
     * losing time on a 307 redirect when a replica instance is hit. Updated on every
     * {@link #handleHook} delivery and every {@link #sendActions} redirect.
     */
    private final Map<String, String> roomInstanceMap;

    private volatile HookHandler hookHandler;

    private ScribblePubBot(Builder builder) {
        this.token = Objects.requireNonNull(builder.token, "token is required");
        this.baseUrl = stripTrailingSlashes(builder.baseUrl);
        this.handle = builder.handle;
        this.handlePattern = builder.handle == null || builder.handle.isBlank()
                ? null
                : Mentions.pattern(builder.handle);
        this.maxMessageLength = builder.maxMessageLength;
        this.signature = WebhookSignature.of(builder.token);
        this.json = builder.json == null ? new Json() : builder.json;
        this.requestTimeout = builder.requestTimeout;
        this.httpClient = builder.httpClient == null
                ? HttpClient.newBuilder()
                        .connectTimeout(builder.requestTimeout)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build()
                : builder.httpClient;
        this.roomInstanceMap = new ConcurrentHashMap<>(
                this.baseUrl.equals(DEFAULT_BASE_URL) ? DEFAULT_ROOM_INSTANCES : Map.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A bot with every default; equivalent to {@code builder().token(token).build()}. */
    public static ScribblePubBot withToken(String token) {
        return builder().token(token).build();
    }

    // ---------------------------------------------------------------- handlers

    /** Parity with the TypeScript SDK's {@code bot.on("hook", …)}. */
    public ScribblePubBot on(String event, HookHandler handler) {
        if (!HOOK_EVENT.equals(event)) {
            throw new IllegalArgumentException("Unknown event '" + event + "'; the only event is '" + HOOK_EVENT + "'");
        }
        return onHook(handler);
    }

    public ScribblePubBot onHook(HookHandler handler) {
        this.hookHandler = Objects.requireNonNull(handler, "handler is required");
        return this;
    }

    /**
     * Registers a handler that answers a mention with one line of text: the {@code @handle} is
     * stripped from the incoming text, and the answer is flattened to plain text (rooms render no
     * markup) and truncated to {@code maxMessageLength}.
     */
    public ScribblePubBot onMention(MentionHandler handler) {
        Objects.requireNonNull(handler, "handler is required");
        return onHook(request -> {
            var trigger = request.trigger();
            var text = handlePattern == null
                    ? trigger.text().trim()
                    : Mentions.strip(trigger.text(), handlePattern);
            var mention = new Mention(trigger.room(), trigger.username(), text, trigger.text(), trigger.timestamp());
            return HookResponse.of(toRoomText(handler.reply(mention))).actions();
        });
    }

    /** Applies the room's constraints to a reply: no markup, no more than {@code maxMessageLength}. */
    public String toRoomText(String reply) {
        var plain = PlainText.flatten(reply);
        return maxMessageLength > 0 ? PlainText.truncate(plain, maxMessageLength) : plain;
    }

    // ---------------------------------------------------------------- inbound

    /**
     * Verifies, parses and dispatches one webhook delivery. Never throws: everything, including a
     * handler that blew up, comes back as a {@link HookResult} to write out.
     *
     * @param rawBody         the request body <em>as received</em> — the signature covers those
     *                        exact bytes, so a re-serialised object will never verify
     * @param signatureHeader the {@code X-Scribble-Pub-Signature} header, or {@code null}
     */
    public HookResult handleHook(byte[] rawBody, String signatureHeader) {
        if (!signature.verify(rawBody, signatureHeader)) {
            log.warn("Rejected a scribble.pub delivery with an invalid signature");
            return HookResult.failure(401, "invalid signature");
        }

        HookRequest request;
        try {
            request = json.read(rawBody, HookRequest.class);
        } catch (RuntimeException exception) {
            log.warn("Rejected a scribble.pub delivery with unreadable JSON: {}", exception.getMessage());
            return HookResult.failure(400, "invalid JSON");
        }
        if (request == null || !request.isValid()) {
            log.warn("Rejected a scribble.pub delivery with an unexpected payload");
            return HookResult.failure(400, "invalid payload");
        }

        roomInstanceMap.put(request.trigger().room().toLowerCase(Locale.ROOT), request.trigger().directUrl());

        var handler = this.hookHandler;
        if (handler == null) {
            log.error("A scribble.pub delivery arrived but no handler is registered");
            return HookResult.failure(501, "no handler registered");
        }

        List<Action> actions;
        try {
            actions = handler.handle(request);
        } catch (RuntimeException exception) {
            log.error("Handler failed for scribble.pub room {}", request.trigger().room(), exception);
            return HookResult.failure(500, "handler failed");
        }
        // Not `actions.contains(null)`: List.of() throws NPE rather than answering the question.
        if (actions != null && actions.stream().anyMatch(Objects::isNull)) {
            log.error("Handler returned a null action for scribble.pub room {}", request.trigger().room());
            return HookResult.failure(500, "invalid actions");
        }
        return HookResult.ok(new HookResponse(actions == null ? List.of() : actions));
    }

    /** Serialises a {@link HookResult#body()} for writing to the response. */
    public byte[] toJson(Object body) {
        return json.write(body);
    }

    // ---------------------------------------------------------------- outbound

    /**
     * Tells scribble.pub where to deliver this bot's hooks. Call it once at startup, or whenever
     * your public URL changes.
     *
     * @throws ScribblePubApiError      when the platform answers non-2xx
     * @throws IllegalArgumentException when {@code url} is not an http(s) URL
     */
    public void registerWebhook(String url) {
        var payload = new RegisterWebhookPayload(requireHttpUrl(url));
        post("register the webhook", baseUrl + REGISTER_WEBHOOK_PATH, payload);
        log.info("Registered the scribble.pub webhook URL {}", url);
    }

    /**
     * Sends actions, such as new chat messages, into {@code room} outside of a hook reply — the way
     * to answer once the ~10s reply deadline in {@link #handleHook} has already passed.
     *
     * <p>Uses {@link #roomInstanceMap} to reach the instance that actually hosts the room, avoiding
     * an extra redirect when one is already known — from a prior {@link #handleHook} delivery, from
     * the platform's default instances, or from a redirect this method itself already followed. That
     * map is updated with whichever instance served the request.
     *
     * @throws IllegalArgumentException when {@code room} is blank or {@code actions} is invalid
     * @throws ScribblePubApiError      when the platform answers non-2xx
     */
    public void sendActions(String room, List<Action> actions) {
        if (room == null || room.isBlank()) {
            throw new IllegalArgumentException("A room is required");
        }
        if (actions == null || actions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("actions must be a list with no null entries");
        }

        var key = room.trim().toLowerCase(Locale.ROOT);
        var origin = roomInstanceMap.getOrDefault(key, baseUrl);
        var path = "/api/v0/room/" + encodePathSegment(room) + "/actions";

        var response = post("send actions", origin + path, new HookResponse(actions));

        var servedBy = originOf(response.uri());
        if (!servedBy.equalsIgnoreCase(origin)) {
            roomInstanceMap.put(key, servedBy);
        }
    }

    /** Issues an authenticated POST, turning any non-2xx answer into a {@link ScribblePubApiError}. */
    private HttpResponse<String> post(String operation, String url, Object body) {
        var request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.write(body)))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not reach " + url + " to " + operation, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while trying to " + operation, exception);
        }
        if (response.statusCode() / 100 != 2) {
            throw new ScribblePubApiError(response.statusCode(), response.body());
        }
        return response;
    }

    private static String originOf(URI uri) {
        return uri.getScheme() + "://" + uri.getAuthority();
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // ---------------------------------------------------------------- accessors

    /** The signature helper for this bot's token, for tooling that needs to forge a delivery. */
    public WebhookSignature signature() {
        return signature;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String handle() {
        return handle;
    }

    public int maxMessageLength() {
        return maxMessageLength;
    }

    private static String requireHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("A webhook URL is required");
        }
        var scheme = URI.create(url).getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("The webhook URL must be http or https, got: " + url);
        }
        return url;
    }

    private static String stripTrailingSlashes(String baseUrl) {
        var trimmed = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** @see ScribblePubBot#builder() */
    public static final class Builder {

        private String token;
        private String baseUrl = DEFAULT_BASE_URL;
        private String handle;
        private int maxMessageLength = 2000;
        private Json json;
        private HttpClient httpClient;
        private Duration requestTimeout = Duration.ofSeconds(10);

        private Builder() {
        }

        /** The bot token from {@code support@scribble.pub}; also the HMAC key for signatures. */
        public Builder token(String token) {
            this.token = token;
            return this;
        }

        /** Defaults to {@value ScribblePubBot#DEFAULT_BASE_URL}. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** The bot's handle without {@code @}; stripped from mention text by {@link #onMention}. */
        public Builder handle(String handle) {
            this.handle = handle;
            return this;
        }

        /** Longest reply {@link #onMention} posts; {@code 0} or less disables truncation. */
        public Builder maxMessageLength(int maxMessageLength) {
            this.maxMessageLength = maxMessageLength;
            return this;
        }

        /** Reuse an application's Jackson configuration instead of a vanilla mapper. */
        public Builder json(Json json) {
            this.json = json;
            return this;
        }

        /** Only used by {@link #registerWebhook(String)} and {@link #sendActions}; inbound handling makes no HTTP calls. */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public ScribblePubBot build() {
            return new ScribblePubBot(this);
        }
    }
}
