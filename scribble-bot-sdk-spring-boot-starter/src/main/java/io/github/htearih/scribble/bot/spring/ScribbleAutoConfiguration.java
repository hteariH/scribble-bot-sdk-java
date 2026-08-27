package io.github.htearih.scribble.bot.spring;

import io.github.htearih.scribble.bot.AddressedHandler;
import io.github.htearih.scribble.bot.ChatAddressedHandler;
import io.github.htearih.scribble.bot.HookHandler;
import io.github.htearih.scribble.bot.ScribblePubBot;
import io.github.htearih.scribble.bot.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires a {@link ScribblePubBot} and the webhook endpoint from {@code scribble.*}.
 *
 * <p>Everything is conditional on {@code scribble.enabled=true}, so the starter can sit on the
 * classpath of an application that only sometimes talks to scribble.pub.
 *
 * <p>Supply a handler bean — one of:
 *
 * <ul>
 *   <li>{@link AddressedHandler} — the usual case: a message addressed to the bot in, one line of
 *       text back.
 *   <li>{@link ChatAddressedHandler} — the same trigger with everything on it, for answering in
 *       thread, quoting, or posting more than one message. Wins over an {@code AddressedHandler}
 *       when both are defined.
 *   <li>{@link HookHandler} — the catch-all, for triggers no specific handler claimed. It composes
 *       with the two above rather than replacing them, so a bot can answer chat messages and still
 *       see whatever else arrives.
 * </ul>
 *
 * <p>With none of them defined, deliveries are answered with HTTP 501.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "scribble", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ScribbleProperties.class)
public class ScribbleAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ScribbleAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ScribbleTokenProvider scribbleTokenProvider(ScribbleProperties properties) {
        return new ScribbleTokenProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ScribblePubBot scribblePubBot(
            ScribbleProperties properties,
            ScribbleTokenProvider tokenProvider,
            ObjectProvider<ObjectMapper> objectMappers,
            ObjectProvider<HookHandler> hookHandlers,
            ObjectProvider<ChatAddressedHandler> chatAddressedHandlers,
            ObjectProvider<AddressedHandler> addressedHandlers) {

        var builder = ScribblePubBot.builder()
                .token(tokenProvider.getToken())
                .baseUrl(properties.getBaseUrl())
                .handle(properties.getHandle())
                .maxMessageLength(properties.getMaxMessageLength())
                .replyInThread(properties.isReplyInThread());
        // Reuse the application's Jackson configuration when there is one, so a customised mapper
        // does not quietly diverge from the one reading the webhook body.
        var mapper = objectMappers.getIfAvailable();
        if (mapper != null) {
            builder.json(new Json(mapper));
        }
        var bot = builder.build();

        var hookHandler = hookHandlers.getIfAvailable();
        var chatAddressedHandler = chatAddressedHandlers.getIfAvailable();
        var addressedHandler = addressedHandlers.getIfAvailable();
        if (hookHandler != null) {
            bot.onHook(hookHandler);
        }
        if (chatAddressedHandler != null) {
            bot.onChatAddressed(chatAddressedHandler);
        } else if (addressedHandler != null) {
            bot.onAddressed(addressedHandler);
        }
        if (hookHandler == null && chatAddressedHandler == null && addressedHandler == null) {
            log.warn("scribble.enabled=true but no AddressedHandler, ChatAddressedHandler or HookHandler bean"
                    + " is defined; deliveries will be answered with HTTP 501");
        }
        return bot;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "scribble", name = "public-url")
    public ScribbleWebhookRegistrar scribbleWebhookRegistrar(ScribblePubBot bot, ScribbleProperties properties) {
        return new ScribbleWebhookRegistrar(bot, properties.getPublicUrl());
    }

    /**
     * The endpoint itself. Split out so the SDK still auto-configures in an application without a
     * reactive web stack — those can call {@link ScribblePubBot#handleHook} from their own layer.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.reactive.DispatcherHandler")
    public static class WebFluxConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ScribbleWebhookController scribbleWebhookController(
                ScribblePubBot bot, ScribbleProperties properties) {
            return new ScribbleWebhookController(bot, properties);
        }
    }
}
