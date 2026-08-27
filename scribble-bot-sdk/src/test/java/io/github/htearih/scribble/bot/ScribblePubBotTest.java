package io.github.htearih.scribble.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.htearih.scribble.bot.model.Action;
import io.github.htearih.scribble.bot.model.AddMessage;
import io.github.htearih.scribble.bot.model.ChatAddressedTrigger;
import io.github.htearih.scribble.bot.model.HookResponse;
import io.github.htearih.scribble.bot.model.OutboundReplyTarget;
import io.github.htearih.scribble.bot.model.Trigger;
import io.github.htearih.scribble.bot.model.UnsupportedTrigger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ScribblePubBotTest {

    private static final String TOKEN = "test-secret-token";

    /** A message that addresses the bot by opening with its tag. */
    private static final String TAGGED = """
            {"trigger":{"type":"chat.addressed","text":"@mary hello","room":"Main",\
            "timestamp":1779999999,"username":"TheBestArtist","userId":"u1a2b3c4d5",\
            "messageId":42,"directUrl":"https://eu.scribble.pub"}}""";

    /** A reply to one of the bot's own messages, quoting a fragment of it. */
    private static final String REPLY = """
            {"trigger":{"type":"chat.addressed","text":"what did you mean?","room":"Main",\
            "timestamp":1779999999,"username":"TheBestArtist","userId":"u1a2b3c4d5",\
            "messageId":43,"directUrl":"https://eu.scribble.pub","replyToMessageId":42,\
            "replyTo":{"messageId":42,"localId":7,"username":"mary","userId":"b9z8y7x6w5",\
            "text":"the cat sat on the mat","quoteStart":4,"quoteText":"cat"}}}""";

    private final ScribblePubBot bot = ScribblePubBot.builder()
            .token(TOKEN)
            .handle("mary")
            .maxMessageLength(40)
            .build();

    @Test
    void answersAnAddressedMessageWithASingleChatAddMessageAction() {
        bot.onAddressed(addressed -> "hi there");

        var result = deliver(TAGGED);

        assertThat(result.status()).isEqualTo(200);
        assertThat(actions(result)).containsExactly(new AddMessage("hi there"));
        assertThat(new String(bot.toJson(result.body()), StandardCharsets.UTF_8))
                .isEqualTo("{\"actions\":[{\"type\":\"chat.addMessage\",\"text\":\"hi there\"}]}");
    }

    @Test
    void handsTheHandlerTheMessageWithoutTheHandle() {
        var seen = new AtomicReference<Addressed>();
        bot.onAddressed(addressed -> {
            seen.set(addressed);
            return "ok";
        });

        deliver(TAGGED);

        assertThat(seen.get().text()).isEqualTo("hello");
        assertThat(seen.get().rawText()).isEqualTo("@mary hello");
        assertThat(seen.get().room()).isEqualTo("Main");
        assertThat(seen.get().username()).isEqualTo("TheBestArtist");
        assertThat(seen.get().userId()).isEqualTo("u1a2b3c4d5");
        assertThat(seen.get().messageId()).isEqualTo(42);
        assertThat(seen.get().timestamp()).isEqualTo(1779999999L);
        assertThat(seen.get().isBare()).isFalse();
        assertThat(seen.get().isReply()).isFalse();
    }

    @Test
    void deliversTheRepliedToMessageWhenTheTargetIsStillLive() {
        var seen = new AtomicReference<Addressed>();
        bot.onAddressed(addressed -> {
            seen.set(addressed);
            return "ok";
        });

        deliver(REPLY);

        var addressed = seen.get();
        assertThat(addressed.isReply()).isTrue();
        assertThat(addressed.replyToMessageId()).isEqualTo(42L);
        assertThat(addressed.replyTo().text()).isEqualTo("the cat sat on the mat");
        assertThat(addressed.replyTo().userId()).isEqualTo("b9z8y7x6w5");
        assertThat(addressed.quote()).isEqualTo("cat");
        // localId came back, so this is a reply to something the bot itself posted.
        assertThat(addressed.isReplyToSelf()).isTrue();
        assertThat(addressed.replyTo().localId()).isEqualTo(7L);
    }

    @Test
    void aReplyWhoseTargetIsGoneKeepsOnlyTheId() {
        var seen = new AtomicReference<Addressed>();
        bot.onAddressed(addressed -> {
            seen.set(addressed);
            return "ok";
        });

        // The platform drops `replyTo` entirely when the parent was deleted, hidden or expired,
        // leaving `replyToMessageId` on its own.
        deliver("""
                {"trigger":{"type":"chat.addressed","text":"@mary hm","room":"Main",\
                "timestamp":1779999999,"username":"A","userId":"u1","messageId":44,\
                "directUrl":"https://eu.scribble.pub","replyToMessageId":9}}""");

        assertThat(seen.get().isReply()).isTrue();
        assertThat(seen.get().replyToMessageId()).isEqualTo(9L);
        assertThat(seen.get().replyTo()).isNull();
        assertThat(seen.get().isReplyToSelf()).isFalse();
        assertThat(seen.get().quote()).isNull();
    }

    @Test
    void answersInThreadOnlyWhenAskedTo() {
        var threaded = ScribblePubBot.builder()
                .token(TOKEN)
                .handle("mary")
                .replyInThread(true)
                .build()
                .onAddressed(addressed -> "hi there");

        var actions = ((HookResponse) threaded.handleHook(bytes(TAGGED), threaded.signature().sign(bytes(TAGGED)))
                        .body())
                .actions();

        assertThat(((AddMessage) actions.get(0)).replyTo()).isEqualTo(OutboundReplyTarget.to(42));

        // The default stays a standalone message, as rooms behaved before replies existed.
        bot.onAddressed(addressed -> "hi there");
        assertThat(((AddMessage) actions(deliver(TAGGED)).get(0)).replyTo()).isNull();
    }

    @Test
    void reportsABareMessageAsSuch() {
        var seen = new AtomicReference<Addressed>();
        bot.onAddressed(addressed -> {
            seen.set(addressed);
            return "?";
        });

        deliver(TAGGED.replace("@mary hello", "@mary"));

        assertThat(seen.get().isBare()).isTrue();
    }

    @Test
    void flattensMarkupAndTruncatesTheAnswerForTheRoom() {
        bot.onAddressed(addressed -> "<b>Yes</b> — see <a href=\"https://x.dev\">docs</a>");
        assertThat(text(deliver(TAGGED))).isEqualTo("Yes — see docs (https://x.dev)");

        bot.onAddressed(addressed -> "word ".repeat(50));
        assertThat(text(deliver(TAGGED))).hasSizeLessThanOrEqualTo(40);
    }

    @Test
    void postsNothingWhenTheHandlerHasNothingToSay() {
        bot.onAddressed(addressed -> "   ");

        var result = deliver(TAGGED);

        assertThat(result.status()).isEqualTo(200);
        assertThat(actions(result)).isEmpty();
    }

    @Test
    void rejectsAnInvalidSignatureWithoutRunningTheHandler() {
        var called = new AtomicReference<>(false);
        bot.onAddressed(addressed -> {
            called.set(true);
            return "hi";
        });

        var result = bot.handleHook(bytes(TAGGED), "sha256=invalid-signature-here");

        assertThat(result.status()).isEqualTo(401);
        assertThat(result.body()).isEqualTo(new HookResult.Failure("invalid signature"));
        assertThat(called.get()).isFalse();
    }

    @Test
    void rejectsMalformedJson() {
        bot.onAddressed(addressed -> "hi");

        var result = deliver("{not valid json");

        assertThat(result.status()).isEqualTo(400);
        assertThat(result.body()).isEqualTo(new HookResult.Failure("invalid JSON"));
    }

    @Test
    void rejectsAStructurallyBrokenPayload() {
        bot.onAddressed(addressed -> "hi");

        assertThat(deliver("{\"event\":\"message\"}").status()).isEqualTo(400);
        assertThat(deliver(TAGGED.replace("\"username\":\"TheBestArtist\"", "\"username\":null")).status())
                .isEqualTo(400);
        assertThat(deliver(TAGGED.replace(",\"directUrl\":\"https://eu.scribble.pub\"", "")).status())
                .isEqualTo(400);
    }

    @Test
    void acknowledgesATriggerTypeItDoesNotKnowInsteadOfFailing() {
        bot.onAddressed(addressed -> "hi");

        // A 400 here would make the bot look broken every time the platform grows an event.
        var result = deliver(TAGGED.replace("chat.addressed", "chat.somethingNew"));

        assertThat(result.status()).isEqualTo(200);
        assertThat(actions(result)).isEmpty();
    }

    @Test
    void handsUnknownTriggersToAnUnsupportedHandlerWhenOneIsRegistered() {
        var seen = new AtomicReference<Trigger>();
        bot.onAddressed(addressed -> "hi");
        bot.onUnsupported(trigger -> {
            seen.set(trigger);
            return List.of();
        });

        deliver(TAGGED.replace("chat.addressed", "chat.somethingNew"));

        assertThat(seen.get()).isInstanceOf(UnsupportedTrigger.class);
        assertThat(seen.get().type()).isEqualTo("chat.somethingNew");
        assertThat(seen.get().room()).isEqualTo("Main");
        assertThat(seen.get().isSupported()).isFalse();
    }

    @Test
    void neverRoutesAnUnknownTriggerToTheCatchAllHandler() {
        // "hook" is typed as something this SDK understands, so an unmodelled trigger must not
        // arrive there pretending to be one.
        var seen = new AtomicReference<Trigger>();
        bot.onHook(trigger -> {
            seen.set(trigger);
            return List.of();
        });

        assertThat(deliver(TAGGED.replace("chat.addressed", "chat.somethingNew")).status())
                .isEqualTo(200);
        assertThat(seen.get()).isNull();

        deliver(TAGGED);
        assertThat(seen.get()).isInstanceOf(ChatAddressedTrigger.class);
    }

    @Test
    void saysSoWhenNoHandlerIsRegistered() {
        var result = deliver(TAGGED);

        assertThat(result.status()).isEqualTo(501);
        assertThat(result.body()).isEqualTo(new HookResult.Failure("no handler registered"));
    }

    @Test
    void turnsAHandlerFailureIntoA500RatherThanThrowing() {
        bot.onAddressed(addressed -> {
            throw new IllegalStateException("all models failed");
        });

        var result = deliver(TAGGED);

        assertThat(result.status()).isEqualTo(500);
        assertThat(result.isOk()).isFalse();
    }

    @Test
    void rejectsAHandlerThatReturnsANullAction() {
        bot.onChatAddressed(trigger -> {
            var actions = new java.util.ArrayList<Action>();
            actions.add(null);
            return actions;
        });

        assertThat(deliver(TAGGED).status()).isEqualTo(500);
    }

    @Test
    void rejectsAHandlerThatReturnsAnUnsendableAction() {
        // Both a messageId and a localId: the platform would reject it, so the SDK does first.
        bot.onChatAddressed(trigger ->
                List.of(new AddMessage("hi", null, new OutboundReplyTarget(1L, 2L, null, null))));

        assertThat(deliver(TAGGED).status()).isEqualTo(500);
    }

    @Test
    void theTypedHandlerWinsOverTheCatchAll() {
        bot.onHook(trigger -> List.of(Action.addMessage("catch-all")));
        bot.onChatAddressed(trigger -> List.of(Action.addMessage("room=" + trigger.room())));

        assertThat(text(deliver(TAGGED))).isEqualTo("room=Main");
    }

    @Test
    void theCatchAllSeesTheTriggerDirectly() {
        bot.on(ScribblePubBot.HOOK_EVENT, trigger -> List.of(Action.addMessage("room=" + trigger.room())));

        assertThat(text(deliver(TAGGED))).isEqualTo("room=Main");
    }

    @Test
    void refusesUnknownEvents() {
        assertThatThrownBy(() -> bot.on("message", trigger -> List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");

        // chat.addressed carries a typed trigger, so it has its own registration method.
        assertThatThrownBy(() -> bot.on(ChatAddressedTrigger.TYPE, trigger -> List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("onChatAddressed");
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
