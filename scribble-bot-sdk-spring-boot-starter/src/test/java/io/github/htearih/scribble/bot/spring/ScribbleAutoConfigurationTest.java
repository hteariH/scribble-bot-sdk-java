package io.github.htearih.scribble.bot.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.htearih.scribble.bot.HookHandler;
import io.github.htearih.scribble.bot.AddressedHandler;
import io.github.htearih.scribble.bot.ChatAddressedHandler;
import io.github.htearih.scribble.bot.ScribblePubBot;
import io.github.htearih.scribble.bot.model.Action;
import io.github.htearih.scribble.bot.model.AddMessage;
import io.github.htearih.scribble.bot.model.OutboundReplyTarget;
import io.github.htearih.scribble.bot.model.HookResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ScribbleAutoConfigurationTest {

    private static final String ADDRESSED = """
            {"trigger":{"type":"chat.addressed","text":"@mary hello","room":"Main",\
            "timestamp":1779999999,"username":"TheBestArtist","userId":"u1a2b3c4d5",\
            "messageId":42,"directUrl":"https://eu.scribble.pub"}}""";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ScribbleAutoConfiguration.class));

    @Test
    void nothingIsRegisteredWhileScribbleIsDisabled() {
        runner.withPropertyValues("scribble.enabled=false")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(ScribblePubBot.class)
                        .doesNotHaveBean(ScribbleWebhookController.class)
                        .doesNotHaveBean(ScribbleTokenProvider.class));
    }

    @Test
    void theBotAndTheEndpointAppearWhenEnabledWithAToken() {
        enabled().run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(ScribblePubBot.class)
                .hasSingleBean(ScribbleWebhookController.class)
                .hasSingleBean(ScribbleTokenProvider.class)
                // only registered when a public URL is configured
                .doesNotHaveBean(ScribbleWebhookRegistrar.class));
    }

    @Test
    void theTokenCanComeFromAFile(@TempDir Path dir) throws IOException {
        var file = dir.resolve("scribble_token.txt");
        Files.writeString(file, "  file-token\n");

        runner.withPropertyValues("scribble.enabled=true", "scribble.token-file=" + file)
                .run(context -> assertThat(context.getBean(ScribbleTokenProvider.class).getToken())
                        .isEqualTo("file-token"));
    }

    @Test
    void startupFailsLoudlyWhenEnabledWithoutAToken() {
        runner.withPropertyValues("scribble.enabled=true", "scribble.token-file=does-not-exist.txt")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("scribble.token"));
    }

    @Test
    void anAddressedHandlerBeanIsWiredIntoTheBot() {
        enabled().withUserConfiguration(Addressed.class).run(context -> {
            var result = deliver(context.getBean(ScribblePubBot.class));

            assertThat(result.status()).isEqualTo(200);
            assertThat(((HookResponse) result.body()).actions())
                    // the handle was stripped before the handler saw the text
                    .containsExactly(new AddMessage("you said: hello"));
        });
    }

    @Test
    void aChatAddressedHandlerBeanWinsOverAnAddressedHandler() {
        enabled().withUserConfiguration(Addressed.class, ChatAddressed.class).run(context -> {
            var result = deliver(context.getBean(ScribblePubBot.class));

            assertThat(((HookResponse) result.body()).actions()).containsExactly(new AddMessage("low level"));
        });
    }

    @Test
    void aCatchAllHookHandlerComposesWithTheChatHandlerRatherThanReplacingIt() {
        enabled().withUserConfiguration(Addressed.class, Hooks.class).run(context -> {
            var result = deliver(context.getBean(ScribblePubBot.class));

            // chat.addressed still goes to the specific handler; "hook" only catches what is left.
            assertThat(((HookResponse) result.body()).actions())
                    .containsExactly(new AddMessage("you said: hello"));
        });
    }

    @Test
    void replyInThreadTurnsTheAnswerIntoAReply() {
        enabled().withUserConfiguration(Addressed.class)
                .withPropertyValues("scribble.reply-in-thread=true")
                .run(context -> {
                    var bot = context.getBean(ScribblePubBot.class);
                    assertThat(bot.replyInThread()).isTrue();

                    var actions = ((HookResponse) deliver(bot).body()).actions();
                    assertThat(((AddMessage) actions.get(0)).replyTo())
                            .isEqualTo(OutboundReplyTarget.to(42));
                });
    }

    @Test
    void withoutAHandlerDeliveriesAreAnsweredWithNotImplemented() {
        enabled().run(context -> assertThat(deliver(context.getBean(ScribblePubBot.class)).status())
                .isEqualTo(501));
    }

    @Test
    void thePublicUrlTurnsOnWebhookRegistration() {
        enabled().withPropertyValues("scribble.public-url=https://bots.example.com/webhook")
                // the registrar is an ApplicationRunner, so it does not call out during this test
                .run(context -> assertThat(context).hasSingleBean(ScribbleWebhookRegistrar.class));
    }

    @Test
    void propertiesReachTheBot() {
        enabled().withPropertyValues(
                        "scribble.base-url=http://localhost:3000/",
                        "scribble.max-message-length=17")
                .run(context -> {
                    var bot = context.getBean(ScribblePubBot.class);
                    assertThat(bot.baseUrl()).isEqualTo("http://localhost:3000");
                    assertThat(bot.handle()).isEqualTo("mary");
                    assertThat(bot.maxMessageLength()).isEqualTo(17);
                    assertThat(bot.replyInThread()).isFalse();
                });
    }

    private ApplicationContextRunner enabled() {
        return runner.withPropertyValues(
                "scribble.enabled=true", "scribble.token=a-token", "scribble.handle=mary");
    }

    private static io.github.htearih.scribble.bot.HookResult deliver(ScribblePubBot bot) {
        var body = ADDRESSED.getBytes(StandardCharsets.UTF_8);
        return bot.handleHook(body, bot.signature().sign(body));
    }

    @Configuration(proxyBeanMethods = false)
    static class Addressed {

        @Bean
        AddressedHandler addressedHandler() {
            return addressed -> "you said: " + addressed.text();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ChatAddressed {

        @Bean
        ChatAddressedHandler chatAddressedHandler() {
            return trigger -> List.<Action>of(Action.addMessage("low level"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Hooks {

        @Bean
        HookHandler hookHandler() {
            return trigger -> List.<Action>of(Action.addMessage("catch-all"));
        }
    }
}
