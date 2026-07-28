# Repository Instructions

## Release discipline

- Every plugin code or configuration update must increment `pluginVersion` in `build.gradle`.
- Patch fixes increment `x.y.z`; substantial features increment `x.y.0`; incompatible redesigns increment `x.0.0`.
- Keep `CHANGELOG.md`, `release-notes/vX.Y.Z.md`, `README.md`, and `WorldAreaReset.md` synchronized when behavior or administrator-facing configuration changes.
- English content must appear before Chinese content in public-facing bilingual documentation and release notes.
- Run `gradlew clean build` with Java 21 before publishing.
- Commit the update, push `main`, create and push the matching `vX.Y.Z` tag. The release workflow publishes the GitHub Release.

## Configuration safety

- Never commit a live server directory or personal server configuration.
- Public defaults belong in `src/main/resources/config.yml`, `src/main/resources/config-en_US.yml`, and `defaults/`.
- Official defaults must keep destructive automatic cleanup disabled unless a release explicitly documents a safety change.
- Do not include tokens, credentials, server addresses, player data, world files, logs, or private paths.
