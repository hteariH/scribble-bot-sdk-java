package io.github.htearih.scribble.bot;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.htearih.scribble.bot.model.Action;
import io.github.htearih.scribble.bot.model.AddMessage;
import io.github.htearih.scribble.bot.model.OutboundReplyTarget;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationTest {

    @Test
    void acceptsAPlainMessage() {
        assertThat(Validation.actions(List.of(Action.addMessage("hi")))).isEmpty();
    }

    @Test
    void acceptsAReplyByEitherIdButNeverBoth() {
        assertThat(Validation.actions(List.of(new AddMessage("hi", null, OutboundReplyTarget.to(1)))))
                .isEmpty();
        assertThat(Validation.actions(List.of(new AddMessage("hi", null, OutboundReplyTarget.toLocal(1)))))
                .isEmpty();

        assertThat(Validation.actions(List.of(new AddMessage("hi", null, new OutboundReplyTarget(1L, 2L, null, null)))))
                .extracting(ValidationError::message)
                .containsExactly("give either messageId or localId, not both");
    }

    @Test
    void rejectsAReplyThatTargetsNothing() {
        assertThat(Validation.actions(List.of(new AddMessage("hi", null, new OutboundReplyTarget(null, null, 0, 3)))))
                .extracting(ValidationError::message)
                .contains("one of messageId or localId is required");
    }

    @Test
    void rejectsHalfAQuote() {
        var startOnly = new OutboundReplyTarget(1L, null, 4, null);
        var lengthOnly = new OutboundReplyTarget(1L, null, null, 3);

        assertThat(Validation.actions(List.of(new AddMessage("hi", null, startOnly))))
                .extracting(ValidationError::path)
                .containsExactly("actions.0.replyTo.quoteLength");
        assertThat(Validation.actions(List.of(new AddMessage("hi", null, lengthOnly))))
                .extracting(ValidationError::path)
                .containsExactly("actions.0.replyTo.quoteStart");
    }

    @Test
    void rejectsNonsensicalQuoteOffsets() {
        assertThat(Validation.actions(List.of(new AddMessage("hi", null, new OutboundReplyTarget(1L, null, -1, 3)))))
                .extracting(ValidationError::message)
                .contains("must not be negative");
        assertThat(Validation.actions(List.of(new AddMessage("hi", null, new OutboundReplyTarget(1L, null, 0, 0)))))
                .extracting(ValidationError::message)
                .contains("must be positive");
    }

    @Test
    void capsLocalIdAtTheSafeIntegerLimit() {
        assertThat(Validation.actions(List.of(new AddMessage("hi", AddMessage.MAX_LOCAL_ID, null))))
                .isEmpty();
        assertThat(Validation.actions(List.of(new AddMessage("hi", AddMessage.MAX_LOCAL_ID + 1, null))))
                .extracting(ValidationError::message)
                .containsExactly("must not exceed " + AddMessage.MAX_LOCAL_ID);
    }

    @Test
    void treatsZeroLocalIdAsAbsentRatherThanInvalid() {
        // 0 is the wire's "no local ID" and is never deduplicated, so it never reaches the payload.
        var message = new AddMessage("hi", 0L, null);

        assertThat(message.localId()).isNull();
        assertThat(Validation.actions(List.of(message))).isEmpty();
    }

    @Test
    void rejectsNullsAndMissingText() {
        assertThat(Validation.actions(null)).extracting(ValidationError::path).containsExactly("actions");
        assertThat(Validation.actions(java.util.Collections.singletonList(null)))
                .extracting(ValidationError::message)
                .containsExactly("is null");
        assertThat(Validation.actions(List.<Action>of(new AddMessage(null, null, null))))
                .extracting(ValidationError::path)
                .containsExactly("actions.0.text");
    }

    @Test
    void reportsTheOffendingActionsIndex() {
        var actions = List.<Action>of(
                Action.addMessage("fine"), new AddMessage("bad", null, new OutboundReplyTarget(1L, 2L, null, null)));

        assertThat(Validation.actions(actions))
                .extracting(ValidationError::path)
                .containsExactly("actions.1.replyTo.messageId");
    }

    @Test
    void checksWebhookUrlsAreAbsoluteHttpUrls() {
        assertThat(Validation.webhookUrl("https://bot.example/webhook")).isEmpty();
        assertThat(Validation.webhookUrl("http://localhost:8080/hook")).isEmpty();

        assertThat(Validation.webhookUrl(null)).isNotEmpty();
        assertThat(Validation.webhookUrl("  ")).isNotEmpty();
        assertThat(Validation.webhookUrl("/webhook")).isNotEmpty();
        assertThat(Validation.webhookUrl("ftp://bot.example/webhook")).isNotEmpty();
    }
}
