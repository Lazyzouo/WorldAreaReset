# Changelog

All notable changes are documented here. English is listed before Chinese for every release.

## [1.1.1] - 2026-07-28

### English

- Declared Paper/Folia 1.21.11 as the only tested and supported server version; other versions and implementations remain unsupported and unguaranteed.
- Restored complete editable notifications, broadcasts, help menus, and updater messages to the official configuration templates.
- Added language-scoped `messages.en_US.*` and `messages.zh_CN.*` overrides while preserving legacy flat `messages.*` compatibility.
- Kept server-specific world, range, schedule, allowlist, and update settings on sanitized official defaults.
- Updated the startup banner, metadata, administrator guide, support policy, contribution guide, and public documentation.

### 中文

- 明确仅测试并支持 Paper/Folia 1.21.11；其他版本与服务端实现不提供兼容保证。
- 在官方配置模板中恢复完整且可编辑的通知、广播、帮助菜单和更新提示。
- 新增按语言区分的 `messages.en_US.*` 与 `messages.zh_CN.*` 覆盖，同时保留旧版扁平 `messages.*` 兼容。
- 世界、范围、周期、白名单和更新开关等服务器专用参数继续采用脱敏后的官方预设。
- 同步更新启动横幅、插件元数据、管理员手册、支持政策、贡献指南与公开说明。

## [1.1.0] - 2026-07-28

### English

- Added built-in Simplified Chinese and English language packs.
- Added the downloadable official English configuration.
- Added startup update checks against official GitHub Releases.
- Added optional automatic JAR download into Bukkit's update directory.
- Added localized latest-version, update-success, and update-failure console messages.
- Added a prominent colored startup banner with version, author, language, and project URL.
- Changed the public default configuration to keep automatic cleanup disabled for safety.
- Changed the plugin author to Lazyz.
- Added complete open-source documentation, policies, templates, CI, and automated releases.
- Retained the administrator manual cleanup command, shared countdown, and automatic timer reset.

### 中文

- 新增内置简体中文与英文语言包。
- 新增可单独下载的官方英文配置。
- 新增启动时检查官方 GitHub Releases 更新。
- 新增可选的自动 JAR 下载，并写入 Bukkit 更新目录。
- 新增已是最新版、下载成功及更新失败的本地化后台提示。
- 新增醒目的彩色启动横幅，显示版本、作者、语言和项目地址。
- 官方公开默认配置改为关闭自动清理，防止首次安装误删世界。
- 插件作者改为 Lazyz。
- 新增完整开源文档、项目规范、模板、CI 与自动 Release。
- 保留管理员手动清理、共用倒计时以及自动计时重置逻辑。

## [1.0.0] - 2026-07-26

### English

- Initial area cleanup scheduler for Paper/Folia.
- Added `/war help`, `/war cleanup`, and `/war reload`.
- Added configurable cleanup bounds, block allowlist, countdown, interval, and messages.

### 中文

- 首个 Paper/Folia 区域清理调度版本。
- 新增 `/war help`、`/war cleanup` 与 `/war reload`。
- 新增清理范围、方块白名单、倒计时、周期及消息配置。
