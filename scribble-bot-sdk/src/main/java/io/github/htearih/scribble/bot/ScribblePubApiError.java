package io.github.htearih.scribble.bot;

/** A non-2xx answer from the scribble.pub HTTP API. */
public class ScribblePubApiError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String body;

    public ScribblePubApiError(int status, String body) {
        super("scribble.pub API returned " + status + ": " + body);
        this.status = status;
        this.body = body;
    }

    public int getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }
}
