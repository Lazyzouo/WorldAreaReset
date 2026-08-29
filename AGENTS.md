# Repository Instructions

## Release discipline

- Every plugin code or configuration update must increment `pluginVersion` in `build.gradle`.
- Patch fixes increment `x.y.z`; substantial features increment `x.y.0`; incompatible redesigns increment `x.0.0`.
- Keep `CHANGELOG.md`, `release-notes/vX.Y.Z.md`, `README.md`, and `WorldAreaReset.md` synchronized when behavior or administrator-facing configuration changes.
- English content must appear before Chinese content in public-facing bilingual documentation and release notes.
- Run `gradlew clean build` with Java 21 before publishing.
- Treat Paper/Folia 1.21.11 as the only tested and supported server version until a later release explicitly validates another version.
- Commit the update, push `main`, create and push the matching `vX.Y.Z` tag. The release workflow publishes the GitHub Release.
- The release JAR must be named exactly `WorldAreaReset-X.Y.Z.jar`, using the same `pluginVersion` value.
- Never rename, relabel, or alias the built JAR during upload. The Release workflow must publish the single `build/libs/WorldAreaReset-X.Y.Z.jar` file under its original filename and fail on any missing, extra, or mismatched JAR.
- Treat `v1.2.5` as the permanent release-retention floor. After `v1.2.5` is verified, remove GitHub Releases and tags for `v1.2.4` and earlier; keep `v1.2.5` and every later Release and matching tag unless the user explicitly reverses this policy.

## Configuration safety

- Never commit a live server directory or personal server configuration.
- Public defaults belong in `src/main/resources/config.yml`, `src/main/resources/config-en_US.yml`, and `defaults/`.
- Official defaults must keep destructive automatic cleanup disabled unless a release explicitly documents a safety change.
- Do not include tokens, credentials, server addresses, player data, world files, logs, or private paths.

## Command documentation

- Every new command or command alias must be added to the English and Chinese administrator help menus, bundled language files, tab completion, command usage, and command documentation.
- 新增指令或指令别名时，必须同步补充英文和中文管理员 help 菜单、内置语言文件、Tab 补全、指令 usage 及指令文档。
