package io.github.htearih.scribble.bot.spring;

import io.github.htearih.scribble.bot.HookHandler;
import io.github.htearih.scribble.bot.MentionHandler;
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
 * <p>Supply exactly one handler bean — a {@link MentionHandler} (the usual case) or a
 * {@link HookHandler} when you need to return something other than a single message. A
 * {@code HookHandler} wins if both are present.
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
            ObjectProvider<MentionHandler> mentionHandlers) {

        var builder = ScribblePubBot.builder()
                .token(tokenProvider.getToken())
                .baseUrl(properties.getBaseUrl())
                .handle(properties.getHandle())
                .maxMessageLength(properties.getMaxMessageLength());
        // Reuse the application's Jackson configuration when there is one, so a customised mapper
        // does not quietly diverge from the one reading the webhook body.
        var mapper = objectMappers.getIfAvailable();
        if (mapper != null) {
            builder.json(new Json(mapper));
        }
        var bot = builder.build();

        var hookHandler = hookHandlers.getIfAvailable();
        if (hookHandler != null) {
            bot.onHook(hookHandler);
        } else {
            var mentionHandler = mentionHandlers.getIfAvailable();
            if (mentionHandler != null) {
                bot.onMention(mentionHandler);
            } else {
                log.warn("scribble.enabled=true but no MentionHandler or HookHandler bean is defined;"
                        + " deliveries will be answered with HTTP 501");
            }
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
