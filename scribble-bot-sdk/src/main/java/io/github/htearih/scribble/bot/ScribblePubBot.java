package io.github.htearih.scribble.bot;

import io.github.htearih.scribble.bot.json.Json;
import io.github.htearih.scribble.bot.model.Action;
import io.github.htearih.scribble.bot.model.AddMessage;
import io.github.htearih.scribble.bot.model.ChatAddressedTrigger;
import io.github.htearih.scribble.bot.model.HookRequest;
import io.github.htearih.scribble.bot.model.HookResponse;
import io.github.htearih.scribble.bot.model.RegisterWebhookPayload;
import io.github.htearih.scribble.bot.model.ScratchpadStateResponse;
import io.github.htearih.scribble.bot.model.Trigger;
import io.github.htearih.scribble.bot.security.WebhookSignature;
import io.github.htearih.scribble.bot.state.ScratchpadState;
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
 * A scribble.pub bot: verifies deliveries, dispatches them to your handler, registers the webhook
 * URL and reads a room's scratchpad. The Java counterpart of {@code ScribblePubBot} in
 * <a href="https://github.com/scribble-pub/bot-sdk">scribble-pub/bot-sdk</a>, which is the normative
 * spec for this API.
 *
 * <pre>{@code
 * var bot = ScribblePubBot.builder()
 *         .token(System.getenv("SCRIBBLE_BOT_TOKEN"))
 *         .handle("mary")
 *         .build()
 *         .onAddressed(addressed -> "Hi " + addressed.username() + "! You said: " + addressed.text());
 *
 * // in your HTTP layer, with the *raw* body bytes:
 * var result = bot.handleHook(rawBody, request.getHeader(WebhookSignature.HEADER));
 * respond(result.status(), bot.toJson(result.body()));
 * }</pre>
 *
 * <p><strong>The reply is the HTTP response.</strong> scribble.pub has no outbound endpoint for a
 * hook: the platform hangs up around ten seconds after the delivery, and a handler that answers late
 * does not answer at all. {@link #sendActions} exists for what comes after that deadline.
 *
 * <p>Thread-safe. Handlers may be swapped at runtime; {@link #handleHook} reads the current one.
 */
public final class ScribblePubBot {

    /** The platform's public origin. */
    public static final String DEFAULT_BASE_URL = "https://scribble.pub";

    /** Where {@link #registerWebhook(String)} POSTs. */
    public static final String REGISTER_WEBHOOK_PATH = "/api/v0/bot/webhook/register";

    /** Where {@link #getLogoImage()} GETs. Not room-scoped, so it needs no room permissions. */
    public static final String LOGO_PATH = "/api/v0/logo";

    /** The catch-all event name accepted by {@link #on(String, HookHandler)}. */
    public static final String HOOK_EVENT = "hook";

    /** The unknown-trigger event name accepted by {@link #on(String, HookHandler)}. */
    public static final String UNSUPPORTED_EVENT = "unsupported";

    /** The trigger types this SDK version models; everything else is an {@code UnsupportedTrigger}. */
    public static final List<String> SUPPORTED_TRIGGER_TYPES = List.of(ChatAddressedTrigger.TYPE);

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

    /** Which theme the site logo's letter borders are drawn in. */
    public enum LogoTheme {
        /** Black borders, for drawing over a light background. The platform's default. */
        LIGHT,
        /** Light borders, for drawing over a dark background. */
        DARK
    }

    private final String token;
    private final String baseUrl;
    private final String handle;
    private final Pattern handlePattern;
    private final int maxMessageLength;
    private final boolean replyInThread;
    private final WebhookSignature signature;
    private final Json json;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    /**
     * Root instance lookup for rooms this bot has seen, keyed by lowercased room name. Avoids losing
     * time on a 307 redirect when a replica instance is hit. Updated on every {@link #handleHook}
     * delivery and every {@link #sendActions} redirect.
     */
    private final Map<String, String> roomInstanceMap;

    private volatile HookHandler hookHandler;
    private volatile HookHandler unsupportedHandler;
    private volatile ChatAddressedHandler chatAddressedHandler;

    private ScribblePubBot(Builder builder) {
        this.token = Objects.requireNonNull(builder.token, "token is required");
        this.baseUrl = stripTrailingSlashes(builder.baseUrl);
        this.handle = builder.handle;
        this.handlePattern = builder.handle == null || builder.handle.isBlank()
                ? null
                : Mentions.pattern(builder.handle);
        this.maxMessageLength = builder.maxMessageLength;
        this.replyInThread = builder.replyInThread;
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

    /**
     * Parity with the TypeScript SDK's {@code bot.on(event, …)} for the events that take a whole
     * trigger: {@value #HOOK_EVENT} and {@value #UNSUPPORTED_EVENT}.
     *
     * @see #onChatAddressed(ChatAddressedHandler) for {@code "chat.addressed"}, which is typed
     */
    public ScribblePubBot on(String event, HookHandler handler) {
        return switch (event) {
            case HOOK_EVENT -> onHook(handler);
            case UNSUPPORTED_EVENT -> onUnsupported(handler);
            case ChatAddressedTrigger.TYPE -> throw new IllegalArgumentException(
                    "Use onChatAddressed(…) for '" + ChatAddressedTrigger.TYPE + "'; it receives a typed trigger");
            default -> throw new IllegalArgumentException("Unknown event '" + event + "'");
        };
    }

    /**
     * The catch-all: handles any trigger no specific handler claimed. At most one handler runs per
     * delivery — the specific one when there is one, this otherwise.
     */
    public ScribblePubBot onHook(HookHandler handler) {
        this.hookHandler = Objects.requireNonNull(handler, "handler is required");
        return this;
    }

    /**
     * Handles a chat message addressed to this bot, tag or reply alike.
     *
     * <p>This is the trigger a bot exists for; {@link #onAddressed(AddressedHandler)} is the shorter
     * road when the answer is one line of text.
     */
    public ScribblePubBot onChatAddressed(ChatAddressedHandler handler) {
        this.chatAddressedHandler = Objects.requireNonNull(handler, "handler is required");
        return this;
    }

    /**
     * Handles triggers this SDK version does not model.
     *
     * <p>Entirely optional: unknown triggers are acknowledged with HTTP 200 and no actions whether or
     * not anything is registered here. Register one to log what is arriving, so an SDK upgrade
     * becomes a decision rather than a surprise.
     */
    public ScribblePubBot onUnsupported(HookHandler handler) {
        this.unsupportedHandler = Objects.requireNonNull(handler, "handler is required");
        return this;
    }

    /**
     * Registers a handler that answers with one line of text: the {@code @handle} is stripped from
     * the incoming text, and the answer is flattened to plain text (rooms render no markup) and
     * truncated to {@code maxMessageLength}.
     *
     * <p>Whether the answer lands in the thread or as a standalone message follows
     * {@link Builder#replyInThread(boolean)}.
     */
    public ScribblePubBot onAddressed(AddressedHandler handler) {
        Objects.requireNonNull(handler, "handler is required");
        return onChatAddressed(trigger -> {
            var text = handlePattern == null ? safeTrim(trigger.text()) : Mentions.strip(trigger.text(), handlePattern);
            var reply = toRoomText(handler.reply(Addressed.of(trigger, text)));
            if (reply == null || reply.isBlank()) {
                return List.of();
            }
            return List.of(replyInThread ? new AddMessage(reply).replyingTo(trigger.messageId()) : new AddMessage(reply));
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
     * <p>A trigger type this SDK does not model is <em>not</em> an error — it is acknowledged with
     * HTTP 200 and no actions, so a platform that grew a new event does not make this bot look
     * broken. See {@link #onUnsupported(HookHandler)}.
     *
     * @param rawBody         the request body <em>as received</em> — the signature covers those exact
     *                        bytes, so a re-serialised object will never verify
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

        var trigger = request.trigger();
        roomInstanceMap.put(trigger.room().toLowerCase(Locale.ROOT), trigger.directUrl());

        var chat = this.chatAddressedHandler;
        var hook = this.hookHandler;
        var unsupported = this.unsupportedHandler;
        if (chat == null && hook == null && unsupported == null) {
            log.error("A scribble.pub delivery arrived but no handler is registered");
            return HookResult.failure(501, "no handler registered");
        }

        List<Action> actions;
        try {
            actions = dispatch(trigger, chat, hook, unsupported);
        } catch (RuntimeException exception) {
            log.error("Handler failed for scribble.pub room {}", trigger.room(), exception);
            return HookResult.failure(500, "handler failed");
        }
        if (actions == null || actions.isEmpty()) {
            return HookResult.ok(new HookResponse(List.of()));
        }

        var errors = Validation.actions(actions);
        if (!errors.isEmpty()) {
            log.error("Handler returned invalid actions for scribble.pub room {}: {}", trigger.room(), errors);
            return HookResult.failure(500, "invalid actions");
        }
        return HookResult.ok(new HookResponse(actions));
    }

    /**
     * Picks the one handler that runs for this trigger. An unclaimed trigger is acknowledged with no
     * actions rather than treated as an error.
     */
    private static List<Action> dispatch(
            Trigger trigger, ChatAddressedHandler chat, HookHandler hook, HookHandler unsupported) {

        if (trigger instanceof ChatAddressedTrigger addressed) {
            if (chat != null) {
                return chat.handle(addressed);
            }
            return hook == null ? List.of() : hook.handle(addressed);
        }
        // Unknown triggers are dropped unless somebody asked to see them; they never reach "hook",
        // which is typed as something this SDK understands.
        return unsupported == null ? List.of() : unsupported.handle(trigger);
    }

    /** Serialises a {@link HookResult#body()} for writing to the response. */
    public byte[] toJson(Object body) {
        return json.write(body);
    }

    // ---------------------------------------------------------------- outbound

    /**
     * Tells scribble.pub where to deliver this bot's hooks. Call it once at startup, or whenever your
     * public URL changes.
     *
     * @throws ScribblePubApiError          when the platform answers non-2xx
     * @throws ScribblePubValidationError   when {@code url} is not an absolute http(s) URL
     */
    public void registerWebhook(String url) {
        Validation.require("webhook URL", Validation.webhookUrl(url));
        post("register the webhook", baseUrl + REGISTER_WEBHOOK_PATH, new RegisterWebhookPayload(url));
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
     * @throws ScribblePubValidationError when {@code room} is blank or {@code actions} is invalid
     * @throws ScribblePubApiError        when the platform answers non-2xx
     */
    public void sendActions(String room, List<Action> actions) {
        var key = requireRoom(room);
        Validation.require("actions", Validation.actions(actions));

        var origin = roomInstanceMap.getOrDefault(key, baseUrl);
        var path = "/api/v0/room/" + encodePathSegment(room) + "/actions";

        var response = post("send actions", origin + path, new HookResponse(actions));

        var servedBy = originOf(response.uri());
        if (!servedBy.equalsIgnoreCase(origin)) {
            roomInstanceMap.put(key, servedBy);
        }
    }

    /** Posts a single message into {@code room}; shorthand for the common {@link #sendActions} call. */
    public void sendMessage(String room, String text) {
        sendActions(room, List.of(new AddMessage(toRoomText(text))));
    }

    // ---------------------------------------------------------------- scratchpad

    /**
     * Reads a room's scratchpad as the list of messages that rebuild it.
     *
     * <p>Only the currently visible static snapshot, not full animation timelines: for most layers
     * that is the first frame, and for layers in "Roll" mode the frame currently rolled. Prefer
     * {@link #getScratchpadState(String)} unless you want to reduce the messages yourself.
     *
     * <p>Calls {@code GET /api/v0/room/{room}/scratchpad/state} — the per-app endpoint introduced in
     * upstream 0.4.0. (The room-wide {@code /state} it replaced kept working until 0.5.0 upstream;
     * this port never shipped it, so nothing here has to be unlearned.)
     *
     * @throws ScribblePubValidationError when {@code room} is blank
     * @throws ScribblePubApiError        when the platform answers non-2xx
     */
    public ScratchpadStateResponse getScratchpadStateMessages(String room) {
        requireRoom(room);
        // Reads are served by whichever replica answers, so no instance lookup is needed.
        var response = get("get scratchpad state", scratchpadUrl(room, "state"), Map.of());
        requireOk("get scratchpad state", response);
        return json.read(response.body(), ScratchpadStateResponse.class);
    }

    /**
     * Reads a room's scratchpad and reduces it into a {@link ScratchpadState} ready to draw.
     *
     * @throws ScribblePubValidationError when {@code room} is blank
     * @throws ScribblePubApiError        when the platform answers non-2xx
     */
    public ScratchpadState getScratchpadState(String room) {
        return ScratchpadState.fromMessages(getScratchpadStateMessages(room).messages());
    }

    /** Fetches the scratchpad preview unconditionally. */
    public ScratchpadPreviewImage getScratchpadPreviewImage(String room) {
        return getScratchpadPreviewImage(room, null);
    }

    /**
     * Fetches a low-res (600x420) raster preview of the room's scratchpad, a 0.6 scale of the
     * 1000x700 canvas.
     *
     * <p>Keep the returned {@link ScratchpadPreviewImage#lastModified()} and pass it back as
     * {@code ifModifiedSince} next time: an unchanged canvas then costs a 304 and no body at all.
     *
     * <p>Calls {@code GET /api/v0/room/{room}/scratchpad/preview} — the per-app endpoint introduced
     * in upstream 0.4.0.
     *
     * @param ifModifiedSince an HTTP date, e.g. {@code "Mon, 17 Aug 2026 13:43:50 GMT"}, or {@code null}
     * @return the PNG and its validator, or {@code null} when it has not been modified (304)
     * @throws ScribblePubValidationError when {@code room} is blank
     * @throws ScribblePubApiError        when the platform answers non-2xx
     */
    public ScratchpadPreviewImage getScratchpadPreviewImage(String room, String ifModifiedSince) {
        requireRoom(room);
        var headers = ifModifiedSince == null || ifModifiedSince.isBlank()
                ? Map.<String, String>of()
                : Map.of("If-Modified-Since", ifModifiedSince);

        var response = get("get scratchpad preview", scratchpadUrl(room, "preview"), headers);
        if (response.statusCode() == 304) {
            return null;
        }
        requireOk("get scratchpad preview", response);
        return new ScratchpadPreviewImage(response.body(), header(response, "last-modified"));
    }

    /** Fetches the site logo in the light theme, unconditionally. */
    public LogoImage getLogoImage() {
        return getLogoImage(LogoTheme.LIGHT, null);
    }

    /**
     * Fetches the site logo, drawn by the community pixel by pixel.
     *
     * <p>It arrives masked to the letter shapes, so everything around them is transparent, ready to
     * be drawn over whatever the bot is drawing. Take its dimensions from the image itself rather
     * than hardcoding them — the logo can be resized.
     *
     * <p>Keep the returned {@link LogoImage#etag()} and pass it back as {@code ifNoneMatch} next
     * time: a logo nobody has drawn on then costs a 304 and no body at all.
     *
     * <p>Calls {@code GET /api/v0/logo}. Not room-scoped, so it needs no room permissions.
     *
     * @return the PNG and its validator, or {@code null} when it has not been modified (304)
     * @throws ScribblePubApiError when the platform answers non-2xx
     */
    public LogoImage getLogoImage(LogoTheme theme, String ifNoneMatch) {
        var headers = ifNoneMatch == null || ifNoneMatch.isBlank()
                ? Map.<String, String>of()
                : Map.of("If-None-Match", ifNoneMatch);
        var url = baseUrl + LOGO_PATH + (theme == LogoTheme.DARK ? "?theme=dark" : "");

        var response = get("get logo", url, headers);
        if (response.statusCode() == 304) {
            return null;
        }
        requireOk("get logo", response);
        return new LogoImage(response.body(), header(response, "etag"));
    }

    private String scratchpadUrl(String room, String leaf) {
        return baseUrl + "/api/v0/room/" + encodePathSegment(room) + "/scratchpad/" + leaf;
    }

    // ---------------------------------------------------------------- transport

    /** Issues an authenticated POST, turning any non-2xx answer into a {@link ScribblePubApiError}. */
    private HttpResponse<String> post(String operation, String url, Object body) {
        var request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.write(body)))
                .build();

        var response = send(operation, url, request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new ScribblePubApiError(response.statusCode(), response.body());
        }
        return response;
    }

    /**
     * Issues an authenticated GET, returning the raw bytes. The status is left to the caller, because
     * a 304 is a perfectly good answer for the image endpoints.
     */
    private HttpResponse<byte[]> get(String operation, String url, Map<String, String> headers) {
        var builder = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .timeout(requestTimeout);
        headers.forEach(builder::header);

        return send(operation, url, builder.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private <T> HttpResponse<T> send(
            String operation, String url, HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) {
        try {
            return httpClient.send(request, bodyHandler);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not reach " + url + " to " + operation, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while trying to " + operation, exception);
        }
    }

    private void requireOk(String operation, HttpResponse<byte[]> response) {
        if (response.statusCode() / 100 != 2) {
            var body = response.body() == null ? "" : new String(response.body(), StandardCharsets.UTF_8);
            throw new ScribblePubApiError(response.statusCode(), errorMessage(body));
        }
    }

    /**
     * Digs the human-readable line out of an error body. The API answers non-2xx with
     * {@code {"error": "…"}}; anything else is passed through as it arrived.
     */
    private String errorMessage(String body) {
        var trimmed = body == null ? "" : body.trim();
        if (!trimmed.startsWith("{")) {
            return trimmed;
        }
        try {
            var error = json.mapper().readTree(trimmed).get("error");
            return error != null && error.isString() ? error.stringValue() : trimmed;
        } catch (RuntimeException exception) {
            return trimmed;
        }
    }

    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }

    private static String requireRoom(String room) {
        if (room == null || room.isBlank()) {
            throw new ScribblePubValidationError(
                    "invalid room reference: room is required", List.of(new ValidationError("room", "is required")));
        }
        return room.trim().toLowerCase(Locale.ROOT);
    }

    private static String safeTrim(String text) {
        return text == null ? "" : text.trim();
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

    /** Whether {@link #onAddressed} answers in the thread; see {@link Builder#replyInThread(boolean)}. */
    public boolean replyInThread() {
        return replyInThread;
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
        private boolean replyInThread = false;
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

        /** The bot's handle without {@code @}; stripped from the text before {@link #onAddressed} sees it. */
        public Builder handle(String handle) {
            this.handle = handle;
            return this;
        }

        /** Longest reply {@link #onAddressed} posts; {@code 0} or less disables truncation. */
        public Builder maxMessageLength(int maxMessageLength) {
            this.maxMessageLength = maxMessageLength;
            return this;
        }

        /**
         * Whether {@link ScribblePubBot#onAddressed} answers as a reply to the message that addressed
         * the bot, keeping the exchange in one thread, rather than posting a standalone message.
         *
         * <p>Off by default, which is how the room behaved before the platform had replies at all.
         * Handlers built through {@link ScribblePubBot#onChatAddressed} decide per message and ignore
         * this entirely.
         */
        public Builder replyInThread(boolean replyInThread) {
            this.replyInThread = replyInThread;
            return this;
        }

        /** Reuse an application's Jackson configuration instead of a vanilla mapper. */
        public Builder json(Json json) {
            this.json = json;
            return this;
        }

        /** Only used by the outbound calls; inbound handling makes no HTTP requests. */
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
