# scribble-bot-sdk-java

A Java SDK for the [scribble.pub](https://scribble.pub) Bot API — the counterpart of the official
TypeScript [`scribble-pub/bot-sdk`](https://github.com/scribble-pub/bot-sdk), which stays the
normative spec for the wire format.

```java
var bot = ScribblePubBot.builder()
        .token(System.getenv("SCRIBBLE_BOT_TOKEN"))
        .handle("mary")
        .build()
        .onAddressed(msg -> "Hi " + msg.username() + "! You said: " + msg.text());
```

> **0.4.0 is a breaking release, and deliberately carries no compatibility layer.** `chat.mention`
> became `chat.addressed`, the action is `chat.addMessage`, and `Mention`/`MentionHandler` are now
> `Addressed`/`AddressedHandler`. See [Upgrading to 0.4.0](#upgrading-to-040).

Two artifacts:

| Artifact | What it is | Depends on |
| --- | --- | --- |
| `scribble-bot-sdk` | The SDK: payload types, HMAC signature verification, hook dispatch, webhook registration, scratchpad reads, rune-safe quoting, plain-text flattening. No framework. | Jackson 3, SLF4J |
| `scribble-bot-sdk-spring-boot-starter` | Auto-configuration: a `scribble.*`-configured bot, a WebFlux endpoint, token resolution, startup webhook registration. | the above + Spring Boot 4 |

Java 17+.

## Install

Gradle (Kotlin DSL):

```kotlin
implementation("io.github.htearih.scribble:scribble-bot-sdk-spring-boot-starter:0.2.0")
// or, without Spring:
implementation("io.github.htearih.scribble:scribble-bot-sdk:0.2.0")
```

Maven:

```xml
<dependency>
    <groupId>io.github.htearih.scribble</groupId>
    <artifactId>scribble-bot-sdk-spring-boot-starter</artifactId>
    <version>0.2.0</version>
</dependency>
```

`0.2.0` is the current release. The `0.4.0` described below tracks upstream's *unreleased*
`0.4.0-SNAPSHOT` and is published only as a snapshot — the wire contract can still move before
upstream tags it.

## What the Bot API is

One inbound webhook, plus a few reads. scribble.pub POSTs a `chat.addressed` event when someone
addresses your bot in a room — by opening a message with its tag, or by replying to one of its
messages — signed with HMAC-SHA256 in `X-Scribble-Pub-Signature`, and **your reply is the HTTP
response**: a list of actions, today only `chat.addMessage`.

Who counts as addressing your bot is the platform's decision, and it is worth knowing:

- **Only an opening tag addresses a bot.** Naming one mid-sentence is ordinary text.
- **Tags win over replies.** A reply reaches your bot only when the message opens with no bot tag,
  so replying to you while opening with `@OtherBot` reaches that bot alone.
- **Bots never trigger bots**, and never themselves.

Four consequences the SDK is shaped around:

- **The hook reply is on a deadline.** The platform hangs up roughly ten seconds after the delivery
  (measured: `status:0` at 10.000s in an edge access log). A slower answer in the *hook response* is
  *lost*, not delayed — which is why the starter defaults `scribble.reply-timeout` to 9s and posts a
  fallback line rather than nothing.
- **A late or follow-up answer goes through `sendActions`.** There is still no way to start a
  conversation cold in a room the bot has never heard from, but once a mention has arrived —
  or for the platform's own default rooms — `bot.sendActions(room, actions)` can post into it after
  the fact, for work that ran past the hook deadline or was never triggered by one.
- **Signatures cover the raw bytes.** Read the body as `byte[]`. Re-serialising a parsed object
  changes its whitespace, and every HMAC then fails.
- **Unknown triggers are acknowledged, not rejected.** The platform will grow events this SDK has
  never heard of; those answer `200` with no actions rather than `400`, so an un-upgraded bot keeps
  looking healthy. `onUnsupported` lets you see them.

## Spring Boot

Add the starter, declare one handler bean, and set the token:

```java
@Component
class MyBot implements AddressedHandler {

    @Override
    public String reply(Addressed msg) {
        return "Hi " + msg.username() + "! You said: " + msg.text();
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
text, truncates it and returns the `chat.addMessage` action.

Return `null` or a blank string to post nothing at all.

### Properties

| Property | Default | |
| --- | --- | --- |
| `scribble.enabled` | `false` | Serve the webhook. Everything in the starter is conditional on it. |
| `scribble.token` | | Bot token from `support@scribble.pub`; also the HMAC key. |
| `scribble.token-file` | | Read the token from this file when `scribble.token` is empty. |
| `scribble.handle` | | The bot's handle without `@`; stripped from the text before your handler sees it. |
| `scribble.webhook-path` | `/webhook` | Must match the URL registered with the platform. |
| `scribble.base-url` | `https://scribble.pub` | API origin. |
| `scribble.public-url` | | When set, registered with the platform at startup. |
| `scribble.reply-timeout` | `9s` | Keep it under the platform's ~10s hangup. |
| `scribble.max-message-length` | `2000` | Longer answers are truncated on a word boundary. |
| `scribble.reply-in-thread` | `false` | Answer as a reply to the message that addressed the bot, keeping the exchange in one thread. |
| `scribble.always-answer` | `true` | Answer a failed handler with HTTP 200 + an apology instead of a 500. |
| `scribble.messages.timeout` | *"Sorry, that took too long…"* | Posted when the handler overruns. |
| `scribble.messages.error` | *"Sorry, I couldn't answer that right now."* | Posted when the handler failed. |

Need more than one message, or want to quote what was said? Declare a `ChatAddressedHandler` bean
instead — same trigger, everything on it, actions returned by you. It wins over an
`AddressedHandler`. A `HookHandler` bean is the catch-all for triggers neither of them claimed; it
composes with them rather than replacing them.

## Without Spring

`ScribblePubBot.handleHook` takes the raw body and the signature header, and hands back a status and
a body to serialise. Wire it into whatever server you run:

```java
var bot = ScribblePubBot.withToken(token)
        .onChatAddressed(t -> List.of(Action.addMessage("You wrote: " + t.text())));

var result = bot.handleHook(rawBody, request.getHeader(WebhookSignature.HEADER));
response.setStatus(result.status());
response.getOutputStream().write(bot.toJson(result.body()));
```

It never throws. Statuses match the reference SDK:

| | |
| --- | --- |
| `200` | the actions your handler returned |
| `401` | invalid or missing signature |
| `400` | unreadable JSON, or a structurally broken payload |
| `501` | no handler registered |
| `500` | the handler threw, or returned an action the platform would reject |

A trigger type this SDK does not model is **not** an error: it answers `200` with no actions.

## Registering your webhook URL

```java
bot.registerWebhook("https://bots.example.com/webhook");
```

POSTs `{"url": …}` to `/api/v0/bot/webhook/register` as `Authorization: Bearer <token>`, and throws
`ScribblePubApiError` (carrying `status` and `body`) on a non-2xx answer. Under Spring, setting
`scribble.public-url` does this once at startup and only logs a failure — a bot whose registration
call failed still serves the URL it already had.

## Sending actions after the deadline

```java
bot.sendActions("main", List.of(Action.addMessage("(sorry, that took a while) " + answer)));
```

POSTs `{"actions": […]}` to `/api/v0/room/{room}/actions` as `Authorization: Bearer <token>` — the
way to answer once `handleHook`'s ~10s reply window has already closed, or to post into a room from
work that was never triggered by a hook at all. Routed to the instance that actually hosts the room —
learned from a prior `handleHook` delivery, the platform's own default instances, or a redirect this
call itself just followed, so the next call to the same room skips the extra hop. Throws
`ScribblePubValidationError` (an `IllegalArgumentException`, carrying the offending field paths) when
`room` is blank or the actions are ones the platform would reject, and `ScribblePubApiError` on a
non-2xx answer.

## Replying and quoting

A bot is addressed by a reply as well as by a tag, and it can reply back — into the same thread, and
quoting a fragment if it wants to:

```java
bot.onChatAddressed(t -> {
    if (t.replyTo() != null) {
        // Someone replied to a message — yours, or a third party's they tagged you about.
        var quoted = t.replyTo().quoteText();   // null when they quoted nothing
    }
    return List.of(new AddMessage("Answering that").replyingTo(t.messageId()));
});
```

Set `scribble.reply-in-thread: true` (or `Builder.replyInThread(true)`) to have the one-line
`AddressedHandler` do the same automatically.

Three things about replies are easy to get wrong, so the SDK handles them:

- **`replyTo` is absent when the parent is gone.** Deleted, hidden or expired leaves
  `replyToMessageId` on its own. Its *presence* therefore tells you the parent is still readable —
  so there is never a half-populated `replyTo` to defend against.
- **Quote offsets count runes, not `char`s.** They are Unicode code points, as in Go. Java indexes
  UTF-16, so `indexOf` drifts one position right per emoji earlier in the message, and a quote built
  from it lands on the wrong text. Use `Runes.quoteRange` / `Runes.slice`, or
  `OutboundReplyTarget.quoting(messageId, sourceText, "the bit to quote")`, which does it for you.
- **`localId` is how you recognise your own.** Post with one and it comes back as
  `replyTo.localId()` when somebody replies, so you can look the message up without ever storing a
  room-global ID. It doubles as an idempotency key: re-sending the same `localId` into the same room
  is dropped rather than posted twice.

```java
bot.sendActions("main", List.of(new AddMessage("Take one").withLocalId(counter.incrementAndGet())));
```

## Reading the scratchpad

A room's drawing surface can be read back, as the events that rebuild it or as a reduced state:

```java
var state = bot.getScratchpadState("main");
for (var layerId : state.layerOrder()) {                       // bottom to top
    for (var frameId : state.layers().get(layerId).frames()) {
        for (var object : state.frames().get(frameId).objects()) {
            draw(object);                                      // already in render order
        }
    }
}
```

`getScratchpadStateMessages` returns the raw `sp.*` messages instead, if you would rather reduce them
yourself. Colours arrive packed as `R << 24 | G << 16 | B << 8 | A` — **alpha is the lowest byte**,
which is not what Java imaging expects, so `Rgba.toHex`, `Rgba.toComponents` and `Rgba.toArgb` exist.
A `lineWidth` of `0` means a filled polygon, not a hairline, and fewer than four coordinates means a
dot.

There are two images too, both conditional so an unchanged one costs no download:

```java
var preview = bot.getScratchpadPreviewImage("main", lastModified);  // null when unchanged (304)
var logo = bot.getLogoImage(LogoTheme.DARK, etag);                  // null when unchanged (304)
```

These are the per-app endpoints upstream introduced in 0.4.0 —
`/api/v0/room/{room}/scratchpad/state` and `/scratchpad/preview`. The room-wide `/state` and
`/preview` they replaced are deprecated upstream and were never shipped by this port.

## Plain text

Rooms render no markup. `PlainText.flatten` is the safety net for when a model hands you
`**bold**` anyway: it strips HTML and unambiguous Markdown, keeps link targets (`<a href="u">t</a>`
becomes `t (u)`), and deliberately leaves single `*`/`_` alone, because mangling `some_var_name` or
`3 * 4` would be worse than a stray asterisk. `onAddressed` applies it for you.

## Testing your bot

`bot.signature().sign(body)` produces the header the platform would send, so a test can forge a
delivery without shelling out to `openssl`:

```java
var body = triggerJson.getBytes(UTF_8);
var result = bot.handleHook(body, bot.signature().sign(body));
```

Against a running service:

```bash
TOKEN=$(cat scribble_token.txt); BODY='{"trigger":{"type":"chat.addressed","room":"main","timestamp":1779999999,"text":"@mary hi","username":"you","userId":"u1a2b3c4d5","messageId":42,"directUrl":"https://eu.scribble.pub"}}'; SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$TOKEN" -hex | sed 's/^.*= //'); curl -sS -X POST localhost:8087/webhook -H 'Content-Type: application/json' -H "X-Scribble-Pub-Signature: sha256=$SIG" -d "$BODY"
```

## Building

```bash
./gradlew build                 # compile + test both modules
./gradlew publishToMavenLocal   # into ~/.m2, for trying it in another project
```

## Upgrading to 0.4.0

Upstream calls it "a massive breaking update": once the server is on 0.4.0, bots that were not
upgraded stop responding, because they drop the trigger they no longer recognise. This port follows
it without a compatibility layer — including skipping the 0.3.0 spellings that 0.4.0 removed, which
never shipped here at all.

| Before | Now |
| --- | --- |
| trigger `chat.mention`, discriminated by `trigger` | trigger `chat.addressed`, discriminated by `type` |
| action `addMessage` | action `chat.addMessage` |
| `Mention`, `MentionHandler`, `onMention` | `Addressed`, `AddressedHandler`, `onAddressed` |
| `HookHandler.handle(HookRequest)` | `HookHandler.handle(Trigger)`, and `ChatAddressedHandler` for the typed one |
| — | `userId`, `messageId`, `replyToMessageId`, `replyTo` on every chat trigger |
| — | `localId` and `replyTo` on outbound messages |
| — | `getScratchpadState`, `getScratchpadPreviewImage`, `getLogoImage` |

The rename is the whole migration for most bots: `Mention` → `Addressed` and `onMention` →
`onAddressed`, with the same method names on it. What is genuinely new is that `text()` may now
arrive with no tag in it at all, because a reply addressed you instead — if your bot assumed a tag
was always present, that assumption is now wrong.

**Key storage on `userId`, not `username`.** Usernames change; the ID does not. Its first letter is
the author type (`u` registered, `g` guest, `b` bot), and more letters are expected later, so treat
it as one opaque case-sensitive string rather than parsing it.

## Getting a token

Bot access is still private: ask `support@scribble.pub`. The token arrives as a file, which is why
`scribble.token-file` exists.

## Licence

MIT.
