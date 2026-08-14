package io.github.htearih.scribble.bot.spring;

import io.github.htearih.scribble.bot.ScribblePubBot;
import io.github.htearih.scribble.bot.security.WebhookSignature;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class ScribbleWebhookControllerTest {

    private static final String TOKEN = "test-secret-token";
    private static final String PATH = "/webhook";
    private static final String MENTION = """
            {"trigger":{"trigger":"chat.mention","text":"@mary hello","room":"main",\
            "timestamp":1779999999999,"username":"TheBestArtist",\
            "directUrl":"https://eu.scribble.pub"}}""";

    private ScribblePubBot bot;
    private ScribbleProperties properties;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        properties = new ScribbleProperties();
        properties.setHandle("mary");
        properties.setReplyTimeout(Duration.ofSeconds(2));
        bot = ScribblePubBot.builder().token(TOKEN).handle("mary").build();
        client = WebTestClient.bindToController(new ScribbleWebhookController(bot, properties))
                .configureClient()
                .baseUrl("http://localhost")
                .build();
    }

    @Test
    void answersAMentionWithASingleAddMessageAction() {
        bot.onMention(mention -> "hi there");

        post(MENTION)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.actions.length()").isEqualTo(1)
                .jsonPath("$.actions[0].type").isEqualTo("addMessage")
                .jsonPath("$.actions[0].text").isEqualTo("hi there");
    }

    @Test
    void rejectsAnInvalidSignature() {
        bot.onMention(mention -> "hi there");

        client.post().uri(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(WebhookSignature.HEADER, "sha256=invalid-signature-here")
                .bodyValue(MENTION)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.error").isEqualTo("invalid signature");
    }

    @Test
    void rejectsAMissingSignature() {
        client.post().uri(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(MENTION)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectsMalformedJson() {
        bot.onMention(mention -> "hi there");

        post("{not valid json")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.error").isEqualTo("invalid JSON");
    }

    @Test
    void rejectsAPayloadThatIsNotAMention() {
        bot.onMention(mention -> "hi there");

        post("{\"event\":\"message\"}")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.error").isEqualTo("invalid payload");
    }

    @Test
    void stillAnswersWhenTheHandlerOverrunsTheTimeout() {
        bot.onMention(mention -> {
            sleep(Duration.ofSeconds(5));
            return "too late";
        });

        post(MENTION)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.actions[0].type").isEqualTo("addMessage")
                .jsonPath("$.actions[0].text").isEqualTo(properties.getMessages().getTimeout());
    }

    @Test
    void answersAFailedHandlerWithAnApologyRatherThanA500() {
        bot.onMention(mention -> {
            throw new IllegalStateException("all models failed");
        });

        post(MENTION)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.actions[0].text").isEqualTo(properties.getMessages().getError());
    }

    @Test
    void surfacesTheFailureWhenAlwaysAnswerIsOff() {
        properties.setAlwaysAnswer(false);
        bot.onMention(mention -> {
            throw new IllegalStateException("all models failed");
        });

        post(MENTION).expectStatus().is5xxServerError();
    }

    private WebTestClient.ResponseSpec post(String body) {
        return client.post().uri(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(WebhookSignature.HEADER, bot.signature().sign(body.getBytes(StandardCharsets.UTF_8)))
                .bodyValue(body)
                .exchange();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
