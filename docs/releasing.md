# Android releases

This fork publishes one signed APK per normal GitHub release. Obtainium follows this repository without prerelease settings or an APK filter. The first public version, `1.0.4-samsung.3`, was built locally; keep that existing APK and tag unchanged.

## Make the next release

1. Review and commit the source on `main`.
2. Set `samsungVersionName` and `samsungVersionCode` in `gradle.properties`. Increase the Android code above the previous APK **for every release**, even if only the `samsung.N` suffix changes. For example, `1.0.4-samsung.4` / `1000007` follows the published `1.0.4-samsung.3` / `1000006`.
3. Add that version’s notes under `## <version>` in `CHANGELOG.md`. Commit and push those changes.
4. Push the matching `vX.Y.Z-samsung.N` tag from that reviewed main commit. **Release Samsung APK** checks the source/tag, tests and lints the app, signs it, verifies the retained certificate/package/version and a greater versionCode than the previous published APK, then publishes the APK, checksums and source manifest.
5. Confirm the APK appears in Obtainium as an update. Do not uninstall or clear data; stop on a signing mismatch.

Signed release jobs are serialized, so an older build cannot race a newer one into the latest slot. Use a new version instead of replacing a published asset. Only a tag push publishes. Running the release workflow manually on `main` builds and verifies locally on its runner, without creating a release or keeping installer artifacts. Its optional tag input must match `gradle.properties`; leave it empty to verify the configured version.

## Signing setup

The retained APK certificate SHA-256 is:

```text
b478b60b64740b48da9cf33504214f3e0b1c2d4f4996f856d5946e3080f47f6f
```

This is the historical owner development identity used by the installed S23 APK. Retaining the same key permits in-place updates. Keep a private backup. A different key, even with the same package/version, cannot update that installation.

Create an **android-release** Actions environment restricted to `main` manual checks and `v*-samsung.*` tags. Store `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` there. Use a dedicated encrypted copy of the existing key, retaining its certificate; do not commit a keystore or publish it in an artifact. No release secrets belong in ordinary CI. The decoded key stays in the runner’s private temporary directory and is removed after the job.

The release verifier rejects a different certificate, a different package/version, debug/probe manifests and non-increasing update codes. Environment restrictions exclude pull-request refs; they do not prevent a person with trusted write access from changing release code. No extra manual approval gate is assumed.

## Local builds

Use JDK 25, the pinned Gradle wrapper, Android SDK 37 (SDK Manager package `platforms;android-37.0`) and build-tools 36.0.0:

```sh
python3 -m unittest discover -s scripts/tests -v
./gradlew testDebugUnitTest assembleDebug lintDebug
```

To make an installable owner update, privately set `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` for the retained key, then run:

```sh
./gradlew testDebugUnitTest lintRelease assembleRelease
python3 scripts/release-check.py verify app/build/outputs/apk/release/BetterNightLight-1.0.4-samsung.3.apk
```

Use the actual configured version in that path. Ordinary CI/debug keys and newly generated development keys are not the owner update channel. Build/verification does not install an app, start Shizuku or modify Samsung settings.

## Actions storage

Only standard public Ubuntu runners are used. Routine CI keeps tests/builds without installer uploads; failure reports expire after one day. Releases upload directly from the build job to GitHub Releases, with no duplicate Actions installer artifact. Manual release checks keep no installers. Keep dependency caches within their normal free limit and do not raise the account’s $0 Actions spending block.
