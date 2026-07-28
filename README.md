# WorldAreaReset

[![CI](https://github.com/Lazyzouo/WorldAreaReset/actions/workflows/ci.yml/badge.svg)](https://github.com/Lazyzouo/WorldAreaReset/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Lazyzouo/WorldAreaReset?display_name=tag)](https://github.com/Lazyzouo/WorldAreaReset/releases/latest)
[![License](https://img.shields.io/github/license/Lazyzouo/WorldAreaReset)](LICENSE)
[![Paper](https://img.shields.io/badge/Paper%20%2F%20Folia-1.21.x-2ea44f)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-e76f00)](https://adoptium.net/)

A configurable Paper/Folia plugin for scheduled or administrator-triggered cuboid area cleanup, with bilingual messages and GitHub-based update delivery.

> [!CAUTION]
> “Reset” means replacing non-allowlisted blocks with air and removing non-player entities. It does not regenerate terrain from the world seed and provides no rollback. Back up your world first.

## Features

- Scheduled cleanup with a configurable interval and warning countdown.
- Administrator cleanup through `/war cleanup`, using the same countdown as automatic cleanup.
- Inclusive X/Y/Z cuboid boundaries and a material allowlist.
- Simplified Chinese (`zh_CN`) and English (`en_US`) language packs.
- Official Chinese and English configuration downloads.
- Startup GitHub Release checks with optional automatic JAR download.
- Localized latest-version, update-success, and update-failure console notices.
- Folia region scheduling and a visible startup banner.
- Automated CI, tagged releases, checksums, and bilingual release notes.

## Requirements

| Component | Requirement |
| --- | --- |
| Server | Paper/Folia-compatible 1.21.x server |
| Java | 21 or newer |
| Permission | `worldareareset.admin` for administrator commands |

## Install

1. Download `WorldAreaReset-1.1.0.jar` from the [latest release](https://github.com/Lazyzouo/WorldAreaReset/releases/latest).
2. Place it in the server's `plugins` directory.
3. Start the server once.
4. Review `plugins/WorldAreaReset/config.yml` and make a world backup.
5. Set `cleanup.enabled: true` only after confirming the world and boundaries.

The public defaults intentionally keep automatic cleanup disabled.

## Language

Set one of the following in `config.yml`:

```yaml
language: "en_US"
```

```yaml
language: "zh_CN"
```

Language files are extracted to `plugins/WorldAreaReset/lang/` and can be customized. The release also contains `config.en_US.yml` and `config.zh_CN.yml` as clean official templates.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/war` or `/war help` | None | Show the localized help menu |
| `/war cleanup` | `worldareareset.admin` | Start the shared countdown, clean the area, and reset the automatic timer |
| `/war reload` | `worldareareset.admin` | Reload configuration/language and restart scheduling |

`worldareareset.admin` defaults to server operators.

## Minimal configuration

```yaml
language: "en_US"

cleanup:
  enabled: false
  interval_minutes: 180
  countdown_seconds: 10
  world: "world_nether"
  min_x: -200
  max_x: 200
  min_y: 0
  max_y: 128
  min_z: -200
  max_z: 200
  keep_blocks:
    - BEDROCK
    - BARRIER

updates:
  enabled: true
  auto_download: true
  notify_latest: true
```

## Updates

At startup, the plugin checks the official GitHub latest release asynchronously.

- If current: the console reports that the installed version is latest.
- If newer and automatic download is enabled: the release JAR is downloaded to Bukkit's update directory and installed on the next restart.
- If download fails: the console prints the official release URL for manual download.
- If `updates.auto_download` is disabled: the plugin only reports the new release and URL.

The updater only accepts a release asset named `WorldAreaReset-*.jar`. When GitHub provides a SHA-256 asset digest, it is verified before installation.

## Known limitation

The current cleanup engine submits one task per covered chunk and may synchronously load chunks. Large areas can block a Folia region long enough to trigger Watchdog warnings. Pre-generate chunks, use conservative boundaries, test off-peak, and read the [administrator guide](WorldAreaReset.md) before production use.

## Build

```bash
./gradlew clean build
```

The JAR is written to `build/libs/`.

## Documentation and community

- [Administrator guide / 管理员说明](WorldAreaReset.md)
- [Changelog / 更新日志](CHANGELOG.md)
- [Contributing / 贡献指南](CONTRIBUTING.md)
- [Support / 支持](SUPPORT.md)
- [Security policy / 安全政策](SECURITY.md)

Licensed under the [MIT License](LICENSE). Copyright © 2026 Lazyz.

---

## 中文说明

WorldAreaReset 是一个面向 Paper/Folia 的长方体区域定时清理插件，支持管理员手动清理、中英双语消息以及基于 GitHub Releases 的更新下载。

> [!CAUTION]
> “重置”实际是把非白名单方块替换为空气，并删除非玩家实体。插件不会根据世界种子重新生成地形，也没有回滚功能。启用前必须备份世界。

## 功能

- 可配置周期与警告倒计时的自动清理。
- `/war cleanup` 管理员手动清理，并与自动清理共用倒计时。
- 包含边界的 X/Y/Z 长方体范围与材质白名单。
- 简体中文 (`zh_CN`) 与英文 (`en_US`) 语言包。
- 可下载的官方中文、英文配置模板。
- 启动时检查 GitHub Release，并可自动下载新版 JAR。
- 本地化的最新版、下载成功和更新失败后台提示。
- Folia 区域调度与醒目的启动横幅。
- 自动 CI、标签发布、校验文件及双语 Release 更新日志。

## 安装

1. 从[最新 Release](https://github.com/Lazyzouo/WorldAreaReset/releases/latest)下载 `WorldAreaReset-1.1.0.jar`。
2. 放入服务器 `plugins` 目录。
3. 启动一次服务器。
4. 检查 `plugins/WorldAreaReset/config.yml` 并备份世界。
5. 确认世界名称和坐标后，再将 `cleanup.enabled` 改为 `true`。

公开默认配置会关闭自动清理，防止首次安装误删世界。

## 语言切换

在 `config.yml` 设置 `language: "zh_CN"` 或 `language: "en_US"`，然后执行 `/war reload`。语言文件位于 `plugins/WorldAreaReset/lang/`，可以自行修改。

## 自动更新

服务器启动时会异步检查官方 GitHub 最新 Release。发现新版本后，可将 JAR 下载到 Bukkit 更新目录，并在下一次重启时安装；下载失败则在后台显示官方手动下载地址。

## 已知限制

当前清理引擎会为范围内每个区块提交任务，并可能同步加载区块。范围过大时可能阻塞 Folia region 并触发 Watchdog。正式使用前请预生成区块、缩小范围、避开高峰并阅读 [管理员配置与逻辑说明](WorldAreaReset.md)。

项目采用 [MIT License](LICENSE)，作者：Lazyz。
