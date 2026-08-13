package io.github.htearih.scribble.bot.spring;

import io.github.htearih.scribble.bot.ScribblePubBot;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under {@code scribble.*}. */
@ConfigurationProperties("scribble")
public class ScribbleProperties {

    /** Serve the scribble.pub webhook. Off by default; the whole starter is conditional on it. */
    private boolean enabled = false;

    /** Bot token (from support@scribble.pub); also the HMAC key for webhook signatures. */
    private String token = "";

    /** File to read the token from when {@code scribble.token} is empty. */
    private String tokenFile = "";

    /** The bot's handle without {@code @}; stripped from mention text before your handler sees it. */
    private String handle = "";

    /** Path the webhook is served on. Must match the URL registered with the platform. */
    private String webhookPath = "/webhook";

    /** Origin of the scribble.pub API. */
    private String baseUrl = ScribblePubBot.DEFAULT_BASE_URL;

    /** Public URL of this webhook. When set, it is registered with the platform at startup. */
    private String publicUrl = "";

    /**
     * How long a handler may take before the fallback line is returned instead. scribble.pub hangs
     * up around 10s after the delivery and the room then gets nothing at all, so keep this under it.
     */
    private Duration replyTimeout = Duration.ofSeconds(9);

    /** Longest reply posted into a room; longer answers are truncated. 0 disables truncation. */
    private int maxMessageLength = 2000;

    /**
     * Answer a failed handler with {@link Messages#getError()} and HTTP 200 instead of a 500. There
     * is no outbound API to apologise on later, so by default the room still gets a line.
     */
    private boolean alwaysAnswer = true;

    private final Messages messages = new Messages();

    /** Lines the starter posts when the handler could not. */
    public static class Messages {

        /** Posted when the handler overruns {@code scribble.reply-timeout}. */
        private String timeout = "Sorry, that took too long to think about — ask me again?";

        /** Posted when the handler failed and {@code scribble.always-answer} is on. */
        private String error = "Sorry, I couldn't answer that right now.";

        public String getTimeout() {
            return timeout;
        }

        public void setTimeout(String timeout) {
            this.timeout = timeout;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenFile() {
        return tokenFile;
    }

    public void setTokenFile(String tokenFile) {
        this.tokenFile = tokenFile;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public String getWebhookPath() {
        return webhookPath;
    }

    public void setWebhookPath(String webhookPath) {
        this.webhookPath = webhookPath;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public Duration getReplyTimeout() {
        return replyTimeout;
    }

    public void setReplyTimeout(Duration replyTimeout) {
        this.replyTimeout = replyTimeout;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public void setMaxMessageLength(int maxMessageLength) {
        this.maxMessageLength = maxMessageLength;
    }

    public boolean isAlwaysAnswer() {
        return alwaysAnswer;
    }

    public void setAlwaysAnswer(boolean alwaysAnswer) {
        this.alwaysAnswer = alwaysAnswer;
    }

    public Messages getMessages() {
        return messages;
    }
}
