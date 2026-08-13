package io.github.htearih.scribble.bot.spring;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the bot token: {@code scribble.token} if set, otherwise the contents of
 * {@code scribble.token-file} (the token arrives from support@scribble.pub as a file, and that is
 * often how it ends up on a host).
 *
 * <p>Fails fast when the starter is enabled without a token, because the alternative is a webhook
 * that silently rejects every delivery with a 401.
 */
public class ScribbleTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ScribbleTokenProvider.class);

    private final String token;

    public ScribbleTokenProvider(ScribbleProperties properties) {
        var configured = properties.getToken();
        if (configured != null && !configured.isBlank()) {
            this.token = configured.trim();
            log.info("scribble.pub token loaded from configuration");
            return;
        }
        var fromFile = readFile(properties.getTokenFile());
        if (fromFile == null) {
            throw new IllegalStateException(
                    "scribble.enabled=true but no token: set scribble.token or point scribble.token-file"
                            + " at a readable file (tried: '" + properties.getTokenFile() + "')");
        }
        this.token = fromFile;
        log.info("scribble.pub token loaded from {}", properties.getTokenFile());
    }

    public String getToken() {
        return token;
    }

    private static String readFile(String tokenFile) {
        if (tokenFile == null || tokenFile.isBlank()) {
            return null;
        }
        try {
            var content = Files.readString(Path.of(tokenFile), StandardCharsets.UTF_8).trim();
            return content.isEmpty() ? null : content;
        } catch (Exception exception) {
            log.warn("Could not read scribble token file {}: {}", tokenFile, exception.getMessage());
            return null;
        }
    }
}
