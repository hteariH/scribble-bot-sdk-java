package io.github.htearih.scribble.bot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Uses the fixture from the reference SDK's own test suite (scribble-pub/bot-sdk,
 * {@code test/bot.test.ts}) so this verifier stays bit-compatible with the platform's signer.
 */
class WebhookSignatureTest {

    private static final String TOKEN = "test-secret-token";
    private static final String PAYLOAD = """
            {"trigger":{"trigger":"chat.mention","text":"@TestBot hello","room":"main",\
            "timestamp":1779999999999,"username":"TheBestArtist"}}""";

    private final WebhookSignature signature = WebhookSignature.of(TOKEN);

    @Test
    void acceptsASignatureProducedWithTheToken() {
        assertThat(signature.verify(bytes(PAYLOAD), hmac(TOKEN, PAYLOAD))).isTrue();
    }

    @Test
    void signsWhatItVerifies() {
        assertThat(signature.sign(bytes(PAYLOAD))).isEqualTo(hmac(TOKEN, PAYLOAD));
        assertThat(signature.verify(bytes(PAYLOAD), signature.sign(bytes(PAYLOAD)))).isTrue();
    }

    @Test
    void rejectsASignatureFromAnotherToken() {
        assertThat(signature.verify(bytes(PAYLOAD), hmac("someone-elses-token", PAYLOAD))).isFalse();
    }

    @Test
    void rejectsATamperedBody() {
        var header = hmac(TOKEN, PAYLOAD);
        var tampered = PAYLOAD.replace("hello", "hell0");

        assertThat(signature.verify(bytes(tampered), header)).isFalse();
    }

    @Test
    void rejectsMalformedOrMissingHeaders() {
        assertThat(signature.verify(bytes(PAYLOAD), null)).isFalse();
        assertThat(signature.verify(bytes(PAYLOAD), "sha256=invalid-signature-here")).isFalse();
        assertThat(signature.verify(bytes(PAYLOAD), "sha256=")).isFalse();
        // no algorithm prefix
        assertThat(signature.verify(bytes(PAYLOAD), hmac(TOKEN, PAYLOAD).substring(7))).isFalse();
    }

    @Test
    void acceptsThePrefixInAnyCase() {
        assertThat(signature.verify(bytes(PAYLOAD), hmac(TOKEN, PAYLOAD).replace("sha256=", "SHA256=")))
                .isTrue();
    }

    @Test
    void refusesToBeBuiltWithoutAToken() {
        assertThatThrownBy(() -> WebhookSignature.of("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /** The signing side, mirroring the SDK's {@code crypto.subtle.sign} + hex encoding. */
    private static String hmac(String token, String payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(bytes(token), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(bytes(payload)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
