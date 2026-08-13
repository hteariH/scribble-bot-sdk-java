package io.github.htearih.scribble.bot.spring;

import io.github.htearih.scribble.bot.ScribblePubBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Tells scribble.pub where to deliver hooks, once the application is up. Only active when
 * {@code scribble.public-url} is set.
 *
 * <p>A failure here is logged, not thrown: a bot whose registration call failed still serves the
 * webhook it already had registered, and killing the application would take the rest of it down too.
 */
public class ScribbleWebhookRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScribbleWebhookRegistrar.class);

    private final ScribblePubBot bot;
    private final String publicUrl;

    public ScribbleWebhookRegistrar(ScribblePubBot bot, String publicUrl) {
        this.bot = bot;
        this.publicUrl = publicUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            bot.registerWebhook(publicUrl);
        } catch (RuntimeException exception) {
            log.warn("Could not register the scribble.pub webhook URL {}: {}", publicUrl, exception.getMessage());
        }
    }
}
