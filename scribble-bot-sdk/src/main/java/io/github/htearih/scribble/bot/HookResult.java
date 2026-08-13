package io.github.htearih.scribble.bot;

import io.github.htearih.scribble.bot.model.HookResponse;

/**
 * What to answer a webhook delivery with: an HTTP status and a body to serialise. Framework-free on
 * purpose — map it onto whatever server you are running (see the Spring Boot starter for one).
 *
 * @param status HTTP status: 200 with actions, 401 bad signature, 400 bad JSON or payload,
 *               501 no handler registered, 500 the handler blew up
 * @param body   {@link HookResponse} on success, {@link Failure} otherwise
 */
public record HookResult(int status, Object body) {

    /** The error body the SDK returns for every non-2xx status. */
    public record Failure(String error) {
    }

    public static HookResult ok(HookResponse response) {
        return new HookResult(200, response);
    }

    public static HookResult failure(int status, String error) {
        return new HookResult(status, new Failure(error));
    }

    public boolean isOk() {
        return status >= 200 && status < 300;
    }
}
