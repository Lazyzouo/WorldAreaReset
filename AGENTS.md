# Repository Instructions

## Release discipline

- Every plugin code or configuration update must increment `pluginVersion` in `build.gradle`.
- Patch fixes increment `x.y.z`; substantial features increment `x.y.0`; incompatible redesigns increment `x.0.0`.
- Keep `CHANGELOG.md`, `release-notes/vX.Y.Z.md`, `README.md`, and `WorldAreaReset.md` synchronized when behavior or administrator-facing configuration changes.
- English content must appear before Chinese content in public-facing bilingual documentation and release notes.
- Run `gradlew clean build` with Java 21 before publishing.
- Treat Paper/Folia 1.21.11 as the only tested and supported server version until a later release explicitly validates another version.
- Commit the update, push `main`, create and push the matching `vX.Y.Z` tag. The release workflow publishes the GitHub Release.
- Release JARs must be named exactly `WorldAreaReset-X.Y.Z-en.us.jar` and `WorldAreaReset-X.Y.Z-zh.cn.jar`, using the same `pluginVersion` value.
- Never rename, relabel, or alias built JARs during upload. The Release workflow must publish the two `build/libs/` files under their original filenames and fail on any missing, extra, or mismatched JAR.
- Treat `v1.2.4` as the permanent release-retention floor. Keep the `v1.2.4` GitHub Release and tag, and keep every later Release and matching tag when publishing newer versions; do not delete or replace them unless the user explicitly reverses this policy.

## Configuration safety

- Never commit a live server directory or personal server configuration.
- Public defaults belong in `src/main/resources/config.yml`, `src/main/resources/config-en_US.yml`, and `defaults/`.
- Official defaults must keep destructive automatic cleanup disabled unless a release explicitly documents a safety change.
- Do not include tokens, credentials, server addresses, player data, world files, logs, or private paths.
