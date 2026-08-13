package io.github.htearih.scribble.bot.spring;

import io.github.htearih.scribble.bot.HookResult;
import io.github.htearih.scribble.bot.ScribblePubBot;
import io.github.htearih.scribble.bot.model.HookResponse;
import io.github.htearih.scribble.bot.security.WebhookSignature;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Serves the scribble.pub webhook on {@code scribble.webhook-path}.
 *
 * <p>The Bot API has no outbound endpoint, so the bot's answer <em>is</em> this HTTP response —
 * which makes the reply latency-bound. Handlers run on a bounded-elastic thread under
 * {@code scribble.reply-timeout}, and a slower answer is lost rather than delayed.
 *
 * <p>Status codes come from {@link ScribblePubBot#handleHook} (401 bad signature, 400 bad
 * JSON/payload). A handler that failed still comes back as a 200 with an apology by default
 * ({@code scribble.always-answer}), because a 5xx leaves the room with nothing at all.
 *
 * <p>The body is bound as {@code byte[]} deliberately: the signature covers the exact bytes that
 * were sent, and re-serialising a parsed object changes the whitespace enough to break every HMAC.
 */
@RestController
public class ScribbleWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ScribbleWebhookController.class);

    private final ScribblePubBot bot;
    private final ScribbleProperties properties;

    public ScribbleWebhookController(ScribblePubBot bot, ScribbleProperties properties) {
        this.bot = bot;
        this.properties = properties;
    }

    @PostMapping(path = "${scribble.webhook-path:/webhook}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> hook(
            @RequestHeader(name = WebhookSignature.HEADER, required = false) String signature,
            @RequestBody(required = false) byte[] body) {

        return Mono.fromCallable(() -> bot.handleHook(body, signature))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(properties.getReplyTimeout())
                .onErrorResume(exception -> {
                    if (exception instanceof TimeoutException) {
                        log.warn("No answer within {} for a scribble.pub delivery", properties.getReplyTimeout());
                        return Mono.just(say(properties.getMessages().getTimeout()));
                    }
                    log.error("The scribble.pub webhook failed", exception);
                    return Mono.just(say(properties.getMessages().getError()));
                })
                .map(this::toResponse);
    }

    private ResponseEntity<Object> toResponse(HookResult result) {
        if (properties.isAlwaysAnswer() && result.status() == 500) {
            return ResponseEntity.ok(say(properties.getMessages().getError()).body());
        }
        return ResponseEntity.status(result.status()).body(result.body());
    }

    private HookResult say(String message) {
        return HookResult.ok(HookResponse.of(bot.toRoomText(message)));
    }
}
