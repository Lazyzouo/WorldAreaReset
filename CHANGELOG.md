# Changelog

All notable changes are documented here. English is listed before Chinese for every release.

## [1.0.0] - 2026-09-04

### English

- Reset the public release line to `1.0.0` and publish one `WorldAreaReset-1.0.0.jar` containing the English default `config.yml` and bundled Chinese template.
- Standardize player and console messages with Kitloader-compatible MiniMessage, legacy color handling, prefixes, separators, and severity colors.
- Adopt the `config-version: 25` schema with hyphenated updater options, flat updater messages, safe migration, and atomic configuration backups.
- Require a valid SHA-256 release digest and enforce a 50 MiB automatic-update download limit.
- Keep automatic cleanup disabled in official defaults and retain administrator values and custom configuration keys during migration.

### 中文

- 将公开发布版本线重置为 `1.0.0`，只发布一个 `WorldAreaReset-1.0.0.jar`，其中包含英文默认 `config.yml` 和内置中文模板。
- 按照与 Kitloader 兼容的 MiniMessage、Legacy 颜色、前缀、分割线和严重级别颜色规范统一玩家端与后台消息。
- 采用 `config-version: 25` 配置结构、连字符更新参数、扁平更新消息、安全迁移和原子配置备份。
- 自动更新必须提供有效 SHA-256 发布摘要，并限制下载大小为 50 MiB。
- 官方默认配置继续关闭自动清理，配置迁移会保留管理员参数和自定义键。
