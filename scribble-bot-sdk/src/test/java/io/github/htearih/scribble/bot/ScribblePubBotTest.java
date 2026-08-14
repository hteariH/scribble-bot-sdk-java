package io.github.htearih.scribble.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.htearih.scribble.bot.model.Action;
import io.github.htearih.scribble.bot.model.AddMessage;
import io.github.htearih.scribble.bot.model.HookResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ScribblePubBotTest {

    private static final String TOKEN = "test-secret-token";
    private static final String MENTION = """
            {"trigger":{"trigger":"chat.mention","text":"@mary hello","room":"Main",\
            "timestamp":1779999999999,"username":"TheBestArtist",\
            "directUrl":"https://eu.scribble.pub"}}""";

    private final ScribblePubBot bot = ScribblePubBot.builder()
            .token(TOKEN)
            .handle("mary")
            .maxMessageLength(40)
            .build();

    @Test
    void answersAMentionWithASingleAddMessageAction() {
        bot.onMention(mention -> "hi there");

        var result = deliver(MENTION);

        assertThat(result.status()).isEqualTo(200);
        assertThat(actions(result)).containsExactly(new AddMessage("hi there"));
        assertThat(new String(bot.toJson(result.body()), StandardCharsets.UTF_8))
                .isEqualTo("{\"actions\":[{\"type\":\"addMessage\",\"text\":\"hi there\"}]}");
    }

    @Test
    void handsTheHandlerTheMessageWithoutTheHandle() {
        var seen = new AtomicReference<Mention>();
        bot.onMention(mention -> {
            seen.set(mention);
            return "ok";
        });

        deliver(MENTION);

        assertThat(seen.get().text()).isEqualTo("hello");
        assertThat(seen.get().rawText()).isEqualTo("@mary hello");
        assertThat(seen.get().room()).isEqualTo("Main");
        assertThat(seen.get().username()).isEqualTo("TheBestArtist");
        assertThat(seen.get().timestamp()).isEqualTo(1779999999999L);
        assertThat(seen.get().isBare()).isFalse();
    }

    @Test
    void reportsABareMentionAsSuch() {
        var seen = new AtomicReference<Mention>();
        bot.onMention(mention -> {
            seen.set(mention);
            return "?";
        });

        deliver(MENTION.replace("@mary hello", "@mary"));

        assertThat(seen.get().isBare()).isTrue();
    }

    @Test
    void flattensMarkupAndTruncatesTheAnswerForTheRoom() {
        bot.onMention(mention -> "<b>Yes</b> — see <a href=\"https://x.dev\">docs</a>");
        assertThat(text(deliver(MENTION))).isEqualTo("Yes — see docs (https://x.dev)");

        bot.onMention(mention -> "word ".repeat(50));
        assertThat(text(deliver(MENTION))).hasSizeLessThanOrEqualTo(40);
    }

    @Test
    void postsNothingWhenTheHandlerHasNothingToSay() {
        bot.onMention(mention -> "   ");

        var result = deliver(MENTION);

        assertThat(result.status()).isEqualTo(200);
        assertThat(actions(result)).isEmpty();
    }

    @Test
    void rejectsAnInvalidSignatureWithoutRunningTheHandler() {
        var called = new AtomicReference<Boolean>(false);
        bot.onMention(mention -> {
            called.set(true);
            return "hi";
        });

        var result = bot.handleHook(bytes(MENTION), "sha256=invalid-signature-here");

        assertThat(result.status()).isEqualTo(401);
        assertThat(result.body()).isEqualTo(new HookResult.Failure("invalid signature"));
        assertThat(called.get()).isFalse();
    }

    @Test
    void rejectsMalformedJson() {
        bot.onMention(mention -> "hi");

        var result = deliver("{not valid json");

        assertThat(result.status()).isEqualTo(400);
        assertThat(result.body()).isEqualTo(new HookResult.Failure("invalid JSON"));
    }

    @Test
    void rejectsAPayloadThatIsNotACompleteMention() {
        bot.onMention(mention -> "hi");

        assertThat(deliver("{\"event\":\"message\"}").status()).isEqualTo(400);
        assertThat(deliver(MENTION.replace("chat.mention", "chat.something")).status()).isEqualTo(400);
        assertThat(deliver(MENTION.replace("\"username\":\"TheBestArtist\"", "\"username\":null")).status())
                .isEqualTo(400);
        assertThat(deliver(MENTION.replace(",\"directUrl\":\"https://eu.scribble.pub\"", "")).status())
                .isEqualTo(400);
    }

    @Test
    void saysSoWhenNoHandlerIsRegistered() {
        var result = deliver(MENTION);

        assertThat(result.status()).isEqualTo(501);
        assertThat(result.body()).isEqualTo(new HookResult.Failure("no handler registered"));
    }

    @Test
    void turnsAHandlerFailureIntoA500RatherThanThrowing() {
        bot.onMention(mention -> {
            throw new IllegalStateException("all models failed");
        });

        var result = deliver(MENTION);

        assertThat(result.status()).isEqualTo(500);
        assertThat(result.isOk()).isFalse();
    }

    @Test
    void rejectsALowLevelHandlerThatReturnsANullAction() {
        bot.onHook(request -> {
            var actions = new java.util.ArrayList<Action>();
            actions.add(null);
            return actions;
        });

        assertThat(deliver(MENTION).status()).isEqualTo(500);
    }

    @Test
    void theLowLevelHandlerSeesTheWholePayload() {
        bot.on(ScribblePubBot.HOOK_EVENT,
                request -> List.of(Action.addMessage("room=" + request.trigger().room())));

        assertThat(text(deliver(MENTION))).isEqualTo("room=Main");
    }

    @Test
    void refusesUnknownEvents() {
        assertThatThrownBy(() -> bot.on("message", request -> List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hook");
    }

    @Test
    void normalisesTheBaseUrl() {
        assertThat(ScribblePubBot.builder().token(TOKEN).build().baseUrl())
                .isEqualTo(ScribblePubBot.DEFAULT_BASE_URL);
        assertThat(ScribblePubBot.builder().token(TOKEN).baseUrl("http://localhost:3000//").build().baseUrl())
                .isEqualTo("http://localhost:3000");
    }

    private HookResult deliver(String body) {
        return bot.handleHook(bytes(body), bot.signature().sign(bytes(body)));
    }

    private static List<Action> actions(HookResult result) {
        return ((HookResponse) result.body()).actions();
    }

    private static String text(HookResult result) {
        return ((AddMessage) actions(result).get(0)).text();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
