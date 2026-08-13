# Keeping up with the TypeScript SDK

The official [`@scribble-pub/bot-sdk`](https://github.com/scribble-pub/bot-sdk) is the normative
spec for the wire format; this repository is a port of it. When it releases, this port has to
follow, and a scheduled Claude Code routine does that first pass automatically.

The routine is not push-driven and cannot be: the upstream repository belongs to another org, so
nobody here can install a GitHub webhook on it, and npm does not notify anyone either. Every
possible design is polling — this one polls from the routine itself, so there is no service to
host, no secret to rotate, and nothing that can die silently on a VPS.

The whole state is one line, `upstreamVersion` in [gradle.properties](../gradle.properties): the
upstream release this port is known to match.

## What the routine does

1. Reads `dist-tags.latest` from `https://registry.npmjs.org/@scribble-pub/bot-sdk`.
2. Compares it with `upstreamVersion`. **Equal → stop.** No branch, no PR, no commit; a run that
   finds nothing must leave no trace.
3. Otherwise checks whether `upstream/<new>` already exists on the remote (a previous run may have
   opened it, or a human may be working on it). If it does → stop.
4. Reads what actually changed. Clone the upstream repo and diff the two release tags over `src/`:

   ```bash
   git clone --quiet https://github.com/scribble-pub/bot-sdk.git
   git -C bot-sdk diff v<old>..v<new> -- src/
   ```

   If the tags are not there (unpublished, or named differently), fall back to the published
   tarballs — `npm pack @scribble-pub/bot-sdk@<old>` and `@<new>`, then diff the unpacked
   `dist/index.d.mts`, which is the type surface the port mirrors.
5. Ports the changes, on a branch off `main` named `upstream/<new>`.
6. Sets `version=<new>-SNAPSHOT` and `upstreamVersion=<new>` in `gradle.properties`. The Java
   artifacts track upstream's version number, so the two stay readable side by side.
7. Runs `./gradlew build` and makes it pass, with tests covering whatever the diff introduced.
8. Pushes the branch — [`.github/workflows/snapshot.yml`](../.github/workflows/snapshot.yml)
   publishes `<new>-SNAPSHOT` to Central's snapshot repository — and opens a PR against `main`.

The routine never merges, never pushes to `main`, and never cuts a release. A human reviews the PR
and follows [RELEASING.md](../RELEASING.md) when it is right.

## What counts as a change worth porting

Only the wire contract and the behaviour that depends on it — the TypeScript ergonomics are not
the spec:

| Upstream change | Lands in |
| --- | --- |
| Event payload fields, names, optionality | `bot/model/HookRequest.java`, `bot/model/Mention.java` |
| New or changed actions | `bot/model/Action.java`, `bot/model/AddMessage.java`, `HookResponse` |
| Signature header, algorithm, signed bytes | `bot/security/WebhookSignature.java` |
| Registration payload or endpoint | `bot/model/RegisterWebhookPayload.java`, `ScribblePubBot` |
| Reply deadline, retry or error semantics | `ScribblePubBot`, starter's `ScribbleProperties` |
| Mention syntax, plain-text flattening | `bot/text/Mentions.java`, `bot/text/PlainText.java` |
| New config knob a bot author would set | `spring/ScribbleProperties.java` + `ScribbleAutoConfiguration` |

Two rules that outrank convenience:

- **Signatures cover the raw bytes.** Anything that reparses and re-serialises the body before
  verification breaks every HMAC, no matter how clean it reads.
- **The answer is on a deadline** (~10s, upstream hangs up). A change that adds work on the reply
  path has to fit inside the starter's `reply-timeout` budget, not just compile.

If a change is not mechanical — an upstream redesign that needs a real API decision here — the
routine still opens the PR, marked draft, describing the options rather than guessing. A draft PR
that asks the right question beats a merged one that answered it wrong.

## Changing the schedule or the procedure

The procedure lives here, in the repository, and the routine's prompt only points at this file.
Edit this document to change what the sync does; touch the routine itself only to change *when* it
runs.
