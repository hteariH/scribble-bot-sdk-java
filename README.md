# scribble-bot-sdk-java

A Java SDK for the [scribble.pub](https://scribble.pub) Bot API — the counterpart of the official
TypeScript [`scribble-pub/bot-sdk`](https://github.com/scribble-pub/bot-sdk), which stays the
normative spec for the wire format.

```java
var bot = ScribblePubBot.builder()
        .token(System.getenv("SCRIBBLE_BOT_TOKEN"))
        .handle("mary")
        .build()
        .onMention(mention -> "Hi " + mention.username() + "! You said: " + mention.text());
```

Two artifacts:

| Artifact | What it is | Depends on |
| --- | --- | --- |
| `scribble-bot-sdk` | The SDK: payload types, HMAC signature verification, hook dispatch, webhook registration, plain-text flattening. No framework. | Jackson 3, SLF4J |
| `scribble-bot-sdk-spring-boot-starter` | Auto-configuration: a `scribble.*`-configured bot, a WebFlux endpoint, token resolution, startup webhook registration. | the above + Spring Boot 4 |

Java 17+.

## Install

Gradle (Kotlin DSL):

```kotlin
implementation("io.github.htearih.scribble:scribble-bot-sdk-spring-boot-starter:0.1.0")
// or, without Spring:
implementation("io.github.htearih.scribble:scribble-bot-sdk:0.1.0")
```

Maven:

```xml
<dependency>
    <groupId>io.github.htearih.scribble</groupId>
    <artifactId>scribble-bot-sdk-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

## What the Bot API is

One inbound webhook, and that is all. scribble.pub POSTs a `chat.mention` event when someone tags
your bot in a room, signed with HMAC-SHA256 in `X-Scribble-Pub-Signature`, and **your reply is the
HTTP response** — a list of actions, today only `addMessage`.

Three consequences the SDK is shaped around:

- **Nothing proactive is possible.** There is no send endpoint: a bot cannot start a conversation,
  post a reminder, or follow up on its own.
- **The answer is on a deadline.** The platform hangs up roughly ten seconds after the delivery
  (measured: `status:0` at 10.000s in an edge access log). A slower answer is *lost*, not delayed —
  which is why the starter defaults `scribble.reply-timeout` to 9s and posts a fallback line rather
  than nothing.
- **Signatures cover the raw bytes.** Read the body as `byte[]`. Re-serialising a parsed object
  changes its whitespace, and every HMAC then fails.

## Spring Boot

Add the starter, declare one handler bean, and set the token:

```java
@Component
class MyBot implements MentionHandler {

    @Override
    public String reply(Mention mention) {
        return "Hi " + mention.username() + "! You said: " + mention.text();
    }
}
```

```yaml
scribble:
  enabled: true
  token: ${SCRIBBLE_BOT_TOKEN}
  handle: mary
  webhook-path: /webhook
```

That is the whole integration. The starter verifies the signature, strips `@mary` from the text,
runs your handler off the event loop under `scribble.reply-timeout`, flattens the answer to plain
text, truncates it and returns the `addMessage` action.

Return `null` or a blank string to post nothing at all.

### Properties

| Property | Default | |
| --- | --- | --- |
| `scribble.enabled` | `false` | Serve the webhook. Everything in the starter is conditional on it. |
| `scribble.token` | | Bot token from `support@scribble.pub`; also the HMAC key. |
| `scribble.token-file` | | Read the token from this file when `scribble.token` is empty. |
| `scribble.handle` | | The bot's handle without `@`; stripped from mention text. |
| `scribble.webhook-path` | `/webhook` | Must match the URL registered with the platform. |
| `scribble.base-url` | `https://scribble.pub` | API origin. |
| `scribble.public-url` | | When set, registered with the platform at startup. |
| `scribble.reply-timeout` | `9s` | Keep it under the platform's ~10s hangup. |
| `scribble.max-message-length` | `2000` | Longer answers are truncated on a word boundary. |
| `scribble.always-answer` | `true` | Answer a failed handler with HTTP 200 + an apology instead of a 500. |
| `scribble.messages.timeout` | *"Sorry, that took too long…"* | Posted when the handler overruns. |
| `scribble.messages.error` | *"Sorry, I couldn't answer that right now."* | Posted when the handler failed. |

Need more than one message per mention? Declare a `HookHandler` bean instead of a `MentionHandler`
and return the actions yourself; it takes precedence.

## Without Spring

`ScribblePubBot.handleHook` takes the raw body and the signature header, and hands back a status and
a body to serialise. Wire it into whatever server you run:

```java
var bot = ScribblePubBot.withToken(token)
        .onHook(request -> List.of(Action.addMessage("You wrote: " + request.trigger().text())));

var result = bot.handleHook(rawBody, request.getHeader(WebhookSignature.HEADER));
response.setStatus(result.status());
response.getOutputStream().write(bot.toJson(result.body()));
```

It never throws. Statuses match the reference SDK:

| | |
| --- | --- |
| `200` | the actions your handler returned |
| `401` | invalid or missing signature |
| `400` | unreadable JSON, or a payload that is not a complete mention |
| `501` | no handler registered |
| `500` | the handler threw, or returned a null action |

## Registering your webhook URL

```java
bot.registerWebhook("https://bots.example.com/webhook");
```

POSTs `{"url": …}` to `/api/v0/bot/webhook/register` as `Authorization: Bearer <token>`, and throws
`ScribblePubApiError` (carrying `status` and `body`) on a non-2xx answer. Under Spring, setting
`scribble.public-url` does this once at startup and only logs a failure — a bot whose registration
call failed still serves the URL it already had.

## Plain text

Rooms render no markup. `PlainText.flatten` is the safety net for when a model hands you
`**bold**` anyway: it strips HTML and unambiguous Markdown, keeps link targets (`<a href="u">t</a>`
becomes `t (u)`), and deliberately leaves single `*`/`_` alone, because mangling `some_var_name` or
`3 * 4` would be worse than a stray asterisk. `onMention` applies it for you.

## Testing your bot

`bot.signature().sign(body)` produces the header the platform would send, so a test can forge a
delivery without shelling out to `openssl`:

```java
var body = mentionJson.getBytes(UTF_8);
var result = bot.handleHook(body, bot.signature().sign(body));
```

Against a running service:

```bash
TOKEN=$(cat scribble_token.txt); BODY='{"trigger":{"trigger":"chat.mention","room":"main","timestamp":1779999999999,"text":"@mary hi","username":"you"}}'; SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$TOKEN" -hex | sed 's/^.*= //'); curl -sS -X POST localhost:8087/webhook -H 'Content-Type: application/json' -H "X-Scribble-Pub-Signature: sha256=$SIG" -d "$BODY"
```

## Building

```bash
./gradlew build                 # compile + test both modules
./gradlew publishToMavenLocal   # 0.1.0 into ~/.m2, for trying it in another project
```

## Getting a token

Bot access is still private: ask `support@scribble.pub`. The token arrives as a file, which is why
`scribble.token-file` exists.

## Licence

MIT.
