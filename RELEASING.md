# Releasing

Both artifacts are released together, from `gradle.properties`' `version`.

## One-time setup

**Maven Central** is the target that lets anyone consume the SDK with a bare dependency
declaration. GitHub Packages is published too, but reading from it requires a token, so it is a
convenience for this org, not a public distribution channel.

1. Register the `io.github.htearih` namespace on the
   [Central Portal](https://central.sonatype.com/). Verification is a one-off: the Portal names a
   repository like `hteariH/abc123xyz`, you create it public and empty, and it checks that it
   exists. The namespace then covers every group under it, `io.github.htearih.scribble` included.
2. Generate a user token in the Portal, and a GPG key for signing:

   ```bash
   gpg --quick-generate-key "Your Name <you@example.com>"
   gpg --armor --export-secret-keys <KEY_ID>      # the value for SIGNING_KEY
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   ```

3. Add four repository secrets: `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` (the user token),
   `SIGNING_KEY` (the armoured private key, newlines included), `SIGNING_KEY_PASSWORD`.

## Cutting a release

1. Set the release version in `gradle.properties`, commit, tag.
2. Publish a GitHub release for the tag — `.github/workflows/publish.yml` builds, signs, pushes to
   GitHub Packages and uploads the Central bundle.
3. The upload lands as `USER_MANAGED`, so it waits in the Portal until you press *Publish*. Change
   `publishingType` to `AUTOMATIC` in the workflow once you trust the pipeline.
4. Bump `gradle.properties` to the next version.

## Locally

```bash
./gradlew publishToMavenLocal    # ~/.m2, for trying it in a consumer before releasing
./gradlew centralBundle -PsigningInMemoryKey="$(cat key.asc)" -PsigningInMemoryKeyPassword=…
```

Signing is skipped entirely when no key is supplied, so the local publish needs no GPG setup.
