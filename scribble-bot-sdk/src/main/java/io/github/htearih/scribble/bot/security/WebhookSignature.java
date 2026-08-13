package io.github.htearih.scribble.bot.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The {@code X-Scribble-Pub-Signature} header: HMAC-SHA256 over the request body, keyed with the
 * bot token, hex-encoded behind a {@code sha256=} prefix.
 *
 * <p><strong>Sign and verify the raw bytes.</strong> Re-serialising a parsed body changes its
 * whitespace and every signature then fails — read the body as {@code byte[]}, not as an object.
 *
 * <p>Comparison is constant-time ({@link MessageDigest#isEqual}).
 */
public final class WebhookSignature {

    /** Header scribble.pub sends the signature in. HTTP header names are case-insensitive. */
    public static final String HEADER = "X-Scribble-Pub-Signature";

    private static final String PREFIX = "sha256=";
    private static final String ALGORITHM = "HmacSHA256";
    private static final byte[] EMPTY = new byte[0];

    private final byte[] key;

    private WebhookSignature(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("A scribble.pub bot token is required to verify webhooks");
        }
        this.key = token.getBytes(StandardCharsets.UTF_8);
    }

    public static WebhookSignature of(String token) {
        return new WebhookSignature(token);
    }

    /** Whether {@code signatureHeader} is a valid signature of {@code body}. Never throws. */
    public boolean verify(byte[] body, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return false;
        }
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(signatureHeader.substring(PREFIX.length()).trim());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return MessageDigest.isEqual(provided, hmac(body));
    }

    /**
     * The header value scribble.pub would send for {@code body}. Not needed to run a bot — it is
     * here so tests and local tooling can forge a delivery without shelling out to {@code openssl}.
     */
    public String sign(byte[] body) {
        return PREFIX + HexFormat.of().formatHex(hmac(body));
    }

    private byte[] hmac(byte[] body) {
        try {
            var mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(body == null ? EMPTY : body);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot compute HMAC-SHA256", exception);
        }
    }
}
