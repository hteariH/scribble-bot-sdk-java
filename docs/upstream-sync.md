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
2. Compares it with `upstreamVersion`, **as versions, not as strings**. **`upstreamVersion` at or
   ahead of `latest` → stop.** No branch, no PR, no commit; a run that finds nothing must leave no
   trace.

   Not merely "equal", because this port is allowed to run ahead of a release — see
   [Porting an unreleased upstream](#porting-an-unreleased-upstream). An equality check would treat
   "ahead" as "diverged" and re-port a version that was deliberately skipped.
3. Otherwise checks whether `upstream/<new>` already exists on the remote (a previous run may have
   opened it, or a human may be working on it). If it does → stop.

   The branch is the lock, and it is the only one. A branch deleted along with a rejected PR
   releases it, so the next run will port that version again from `main` — which is the intended
   behaviour when the port was wrong, and the reason to move `upstreamVersion` forward when the
   version was deliberately skipped instead.
4. Reads what actually changed. Clone the upstream repo and diff the two release tags over the
   sources:

   ```bash
   git clone --quiet https://github.com/scribble-pub/bot-sdk.git
   git -C bot-sdk diff <old>..<new> -- packages/
   ```

   Upstream tags are bare versions — `0.3.0`, not `v0.3.0` — and since 0.4.0 the sources live in
   `packages/api/src` and `packages/bot-sdk/src` rather than a top-level `src/`. Check `git tag` and
   the tree rather than assuming either.

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

## Porting an unreleased upstream

Upstream develops on `main` at `<next>-SNAPSHOT` and tags on release, so the wire contract is
readable — and portable — before npm has anything to fetch. Doing that is a deliberate act, not
something the routine decides: it polls `dist-tags.latest` and will never see an unreleased version.

When a human asks for it anyway, everything above holds except where the version comes from. Take
`<new>` from the upstream `packages/*/package.json` rather than npm, diff `<last tag>..main`, and
set `upstreamVersion=<new>` as usual — being ahead of `latest` is exactly what step 2 reads as
"nothing to do", which is what keeps the routine from re-porting the releases now skipped.

Two things follow from the snapshot being unreleased:

- **The contract can still move.** Upstream's own changelog says so. Re-diff `<last tag>..main`
  before the release lands rather than trusting the port as filed.
- **Do not cut a release from it.** `version=<new>-SNAPSHOT` may be published as a snapshot; a real
  `<new>` waits for upstream to tag `<new>`, or the two numbers stop meaning the same thing.

## What counts as a change worth porting

Only the wire contract and the behaviour that depends on it — the TypeScript ergonomics are not
the spec:

| Upstream change | Lands in |
| --- | --- |
| Trigger payload fields, names, optionality | `bot/model/Trigger.java` and its subtypes, `bot/Addressed.java` |
| A new trigger type | a `Trigger` subtype + `@JsonSubTypes` on `Trigger`, `ScribblePubBot.dispatch` |
| New or changed actions | `bot/model/Action.java`, `bot/model/AddMessage.java`, `HookResponse` |
| Outbound payload rules the platform enforces | `bot/Validation.java` |
| Signature header, algorithm, signed bytes | `bot/security/WebhookSignature.java` |
| Registration payload or endpoint | `bot/model/RegisterWebhookPayload.java`, `ScribblePubBot` |
| A new or moved read endpoint | `ScribblePubBot`, and a response record under `bot/model/` |
| Room/scratchpad message types or state rules | `bot/model/RoomMessage.java` and its subtypes, `bot/state/ScratchpadState.java` |
| Reply deadline, retry or error semantics | `ScribblePubBot`, starter's `ScribbleProperties` |
| Tag syntax, plain-text flattening, rune offsets | `bot/text/Mentions.java`, `PlainText.java`, `Runes.java` |
| New config knob a bot author would set | `spring/ScribbleProperties.java` + `ScribbleAutoConfiguration` |

**One module, not two.** Upstream split into `@scribble-pub/api` and `@scribble-pub/bot-sdk` in
0.4.0. This port does not follow that split: `scribble-bot-sdk` carries both halves, because the
split buys a TypeScript consumer tree-shaking and buys a Java consumer a second coordinate to
depend on. Port what the packages *say*, not how they are packaged.

Three rules that outrank convenience:

- **Signatures cover the raw bytes.** Anything that reparses and re-serialises the body before
  verification breaks every HMAC, no matter how clean it reads.
- **The answer is on a deadline** (~10s, upstream hangs up). A change that adds work on the reply
  path has to fit inside the starter's `reply-timeout` budget, not just compile.
- **Do not port a deprecation this port never shipped.** When several upstream releases are being
  caught up at once, port the *destination*, not the path: a name deprecated in one release and
  removed in the next never existed here, and adding it in order to delete it spends a breaking
  change on nothing. Read the whole `CHANGELOG.md` range, not just the last entry.

If a change is not mechanical — an upstream redesign that needs a real API decision here — the
routine still opens the PR, marked draft, describing the options rather than guessing. A draft PR
that asks the right question beats a merged one that answered it wrong.

## Changing the schedule or the procedure

The procedure lives here, in the repository, and the routine's prompt only points at this file.
Edit this document to change what the sync does; touch the routine itself only to change *when* it
runs.
