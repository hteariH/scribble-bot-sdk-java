package io.github.htearih.scribble.bot.model;

/** Body of {@code POST /api/v0/bot/webhook/register}. */
public record RegisterWebhookPayload(String url) {
}
