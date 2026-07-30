# WorldAreaReset

> [!IMPORTANT]
> **Open Source & Privacy Statement / 开源与隐私声明**
>
> WorldAreaReset is a fully open-source project whose published source code can be independently audited and built. The plugin contains no telemetry, analytics, remote administration, hidden data collection, or backdoor functionality. It does not upload server configurations, worlds, logs, player data, credentials, or other server content. Files created by the plugin are stored only on the administrator's server. Its only runtime network activity is the optional outbound HTTPS update check and download against the official `Lazyzouo/WorldAreaReset` GitHub Releases. As with any HTTPS request, GitHub necessarily receives standard connection metadata such as the server's public IP address and User-Agent under GitHub's own policies. Administrators can disable all update network access with `updates.enabled: false`. See the [full privacy statement](PRIVACY.md).
>
> WorldAreaReset 是一个完全开源的项目，公开源代码可供任何人独立审查与构建。插件不包含遥测、统计分析、远程管理、隐藏数据收集或后门功能，也不会上传服务器配置、世界文件、日志、玩家资料、凭据或其他服务器内容。插件创建的文件只会保存在管理员自己的服务器上。插件运行时唯一的联网行为，是按配置向官方 `Lazyzouo/WorldAreaReset` GitHub Releases 发起出站 HTTPS 更新检查与下载请求。与所有 HTTPS 请求相同，GitHub 会依据其自身政策接收服务器公网 IP、User-Agent 等必要连接元数据。管理员可设置 `updates.enabled: false` 完全关闭更新联网。详见[完整隐私声明](PRIVACY.md)。

[![CI](https://github.com/Lazyzouo/WorldAreaReset/actions/workflows/ci.yml/badge.svg)](https://github.com/Lazyzouo/WorldAreaReset/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Lazyzouo/WorldAreaReset?display_name=tag)](https://github.com/Lazyzouo/WorldAreaReset/releases/latest)
[![License](https://img.shields.io/github/license/Lazyzouo/WorldAreaReset)](LICENSE)
[![Paper](https://img.shields.io/badge/Tested%20Paper%20%2F%20Folia-1.21.11-2ea44f)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-e76f00)](https://adoptium.net/)

A Paper/Folia terrain-maintenance plugin designed for administrator-defined free-for-all PvP zones. WorldAreaReset periodically clears non-allowlisted blocks and non-player entities inside a configured cuboid so heavily used combat areas remain clean and reusable; administrators can trigger the same process manually. It does not create regions, enable PvP, or manage combat permissions.

> [!CAUTION]
> “Reset” means replacing non-allowlisted blocks with air and removing non-player entities. It does not regenerate terrain from the world seed and provides no rollback. Back up your world first.

## Features

- Scheduled terrain maintenance for administrator-defined free-for-all PvP zones.
- Administrator cleanup through `/war cleanup`, using the same countdown as automatic cleanup.
- Bold, left-aligned rendering for every in-game plugin message; configured leading indentation is removed at runtime while message text and colors remain unchanged.
- Inclusive X/Y/Z cuboid boundaries and a material allowlist.
- Ready-to-use Simplified Chinese (`WorldAreaReset-1.2.4-zh.cn.jar`) and English (`WorldAreaReset-1.2.4-en.us.jar`) packages.
- Startup GitHub Release checks with optional automatic JAR download.
- Localized, color-coded latest-version, update-success, and update-failure console notices.
- A wider, fully enclosed startup banner with a centered bilingual PvP terrain-maintenance subtitle and dashed title separator, while retaining the shared gold plugin prefix and distinct updater status colors.
- Folia region scheduling and a visible startup banner.
- Automated CI, tagged releases, GitHub asset digests, and bilingual release notes.

## Requirements

| Component | Requirement |
| --- | --- |
| Server | Paper or Folia 1.21.11 |
| Java | 21 or newer |
| Permission | `worldareareset.admin` for administrator commands |

WorldAreaReset 1.2.4 is tested and supported only on Paper/Folia 1.21.11. Other Minecraft versions, Spigot, CraftBukkit, Purpur, and hybrid/modded servers are untested and receive no compatibility guarantee. `api-version: 1.21` is Bukkit metadata and does not expand this tested-version range.

## Install

1. Download `WorldAreaReset-1.2.4-en.us.jar` for English defaults or `WorldAreaReset-1.2.4-zh.cn.jar` for Simplified Chinese defaults from the [latest release](https://github.com/Lazyzouo/WorldAreaReset/releases/latest).
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

Language files are extracted to `plugins/WorldAreaReset/lang/` and can be customized. Each release has exactly two uploaded assets: the English-default JAR and the Simplified-Chinese-default JAR. Both contain the same plugin code and complete configuration comments; only the bundled official default configuration differs. GitHub also displays its automatically generated source archives, which are not additional project-uploaded release assets.

Release filenames always follow `WorldAreaReset-<version>-en.us.jar` and `WorldAreaReset-<version>-zh.cn.jar`. CI and the Release workflow publish the exact files produced in `build/libs/` without renaming, relabeling, or adding other JAR assets.

The official templates restore all editable notifications, broadcasts, and help menus under `messages.en_US` or `messages.zh_CN`. Server-specific cleanup parameters retain safe official presets. Legacy flat `messages.*` overrides remain supported.

Player-facing text is forced bold and left aligned at runtime without rewriting configured message text or colors. Leading spaces are removed independently from every line while leading legacy and hex formatting codes are preserved. Help menus, command feedback, countdown warnings, cleanup broadcasts, and completion messages use the same alignment. Multiline broadcasts continue to repeat the configured prefix on every line; divider rows remain decorative and are no longer centering anchors.

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
- If newer and automatic download is enabled: `WorldAreaReset-<latest-version>-en.us.jar` or `WorldAreaReset-<latest-version>-zh.cn.jar` is selected from the current `language` value, downloaded to Bukkit's update directory, and installed on the next restart.
- If download fails: the console prints the official release URL for manual download.
- If `updates.auto_download` is disabled: the plugin only reports the new release and URL.

The updater only accepts the language-specific release asset matching the active configuration. When GitHub provides a SHA-256 asset digest, it is verified before installation.

## Known limitation

The current cleanup engine submits one task per covered chunk and may synchronously load chunks. Large areas can block a Folia region long enough to trigger Watchdog warnings. Pre-generate chunks, use conservative boundaries, test off-peak, and read the [administrator guide](WorldAreaReset.md) before production use.

WorldAreaReset does not define PvP zones or control combat permissions. Configure the same boundaries separately in the server's region or PvP-management system.

## Build

```bash
./gradlew clean build
```

`WorldAreaReset-1.2.4-en.us.jar` and `WorldAreaReset-1.2.4-zh.cn.jar` are written to `build/libs/`. The build verifies that each packaged `config.yml` is byte-for-byte identical to its commented official source configuration.

## Documentation and community

- [Administrator guide / 管理员说明](WorldAreaReset.md)
- [Changelog / 更新日志](CHANGELOG.md)
- [Contributing / 贡献指南](CONTRIBUTING.md)
- [Support / 支持](SUPPORT.md)
- [Security policy / 安全政策](SECURITY.md)
- [Privacy statement / 隐私声明](PRIVACY.md)

Licensed under the [MIT License](LICENSE). Copyright © 2026 Lazyz.

---

## 中文说明

WorldAreaReset 是一款面向 Paper/Folia 服务器、专为管理员指定的自由 PvP 区域设计的定时地形维护插件。插件会周期性清除配置长方体范围内的非白名单方块与非玩家实体，使频繁使用的战斗区域保持整洁并可继续使用；管理员也可手动触发相同流程。插件不负责创建区域、开启 PvP 或管理战斗权限。

> [!CAUTION]
> “重置”实际是把非白名单方块替换为空气，并删除非玩家实体。插件不会根据世界种子重新生成地形，也没有回滚功能。启用前必须备份世界。

## 功能

- 面向管理员指定自由 PvP 区域的定时地形维护。
- `/war cleanup` 管理员手动清理，并与自动清理共用倒计时。
- 所有插件游戏内文本均以粗体、左对齐显示；运行时会移除配置中的行首缩进，同时保持消息文本与颜色不变。
- 包含边界的 X/Y/Z 长方体范围与材质白名单。
- 可直接使用的简体中文默认包 (`WorldAreaReset-1.2.4-zh.cn.jar`) 与英文默认包 (`WorldAreaReset-1.2.4-en.us.jar`)。
- 启动时检查 GitHub Release，并可自动下载新版 JAR。
- 本地化并带有状态颜色的最新版、下载成功和更新失败后台提示。
- 加宽并完整封闭启动横幅，新增居中的双语 PvP 地形维护副标题与标题区虚线分隔线，同时保留与更新器共用的金色插件前缀及不同状态颜色。
- Folia 区域调度与醒目的启动横幅。
- 自动 CI、标签发布、GitHub 资源摘要及双语 Release 更新日志。

## 版本限制

WorldAreaReset 1.2.4 仅在 Paper/Folia 1.21.11 上测试并提供兼容支持，需要 Java 21 或更高版本。其他 Minecraft 版本、Spigot、CraftBukkit、Purpur 及混合/模组服务端均未测试，不提供兼容保证。`plugin.yml` 中的 `api-version: 1.21` 只是 Bukkit 元数据，不代表所有 1.21.x 版本均受支持。

## 安装

1. 从[最新 Release](https://github.com/Lazyzouo/WorldAreaReset/releases/latest)下载英文默认包 `WorldAreaReset-1.2.4-en.us.jar` 或简体中文默认包 `WorldAreaReset-1.2.4-zh.cn.jar`。
2. 放入服务器 `plugins` 目录。
3. 启动一次服务器。
4. 检查 `plugins/WorldAreaReset/config.yml` 并备份世界。
5. 确认世界名称和坐标后，再将 `cleanup.enabled` 改为 `true`。

公开默认配置会关闭自动清理，防止首次安装误删世界。

## 语言切换

每个 Release 仅上传英文默认 JAR 与简体中文默认 JAR。两个包包含完全相同的插件代码与完整配置注释，仅内置的官方默认配置不同；GitHub 页面自动显示的源码 ZIP/TAR 并非项目额外上传的 Release 附件。文件名固定遵循 `WorldAreaReset-<版本>-en.us.jar` 与 `WorldAreaReset-<版本>-zh.cn.jar`；CI 和 Release 工作流必须原样上传 `build/libs/` 的构建文件，禁止改名、改标签或加入其他 JAR。在 `config.yml` 设置 `language: "zh_CN"` 或 `language: "en_US"` 后执行 `/war reload` 仍可随时切换语言。语言文件位于 `plugins/WorldAreaReset/lang/`，可以自行修改。官方配置保留 `messages.zh_CN` 或 `messages.en_US` 下的完整通知、广播与帮助菜单；清理参数继续使用安全的官方预设，旧版扁平 `messages.*` 覆盖也继续兼容。

所有游戏内文本都会在运行时强制显示为粗体并统一左对齐，不会改写配置中的消息文本或颜色。每一行的行首空格都会被移除，同时保留行首旧式颜色码与十六进制颜色码。帮助菜单、指令反馈、清理倒计时、清理广播和完成消息均使用相同对齐规则；多行广播仍会在每一行重复已配置的插件前缀，分割线只保留装饰用途，不再作为居中基准。

## 自动更新

服务器启动时会异步检查官方 GitHub 最新 Release。发现新版本后，更新器会根据当前 `language` 选择 `WorldAreaReset-<最新版本>-en.us.jar` 或 `WorldAreaReset-<最新版本>-zh.cn.jar`，下载到 Bukkit 更新目录，并在下一次重启时安装；下载失败则在后台显示官方手动下载地址。

## 已知限制

当前清理引擎会为范围内每个区块提交任务，并可能同步加载区块。范围过大时可能阻塞 Folia region 并触发 Watchdog。正式使用前请预生成区块、缩小范围、避开高峰并阅读 [管理员配置与逻辑说明](WorldAreaReset.md)。

WorldAreaReset 不会划分 PvP 区域或控制战斗权限；请在服务器的区域或 PvP 管理系统中另行配置相同边界。

项目采用 [MIT License](LICENSE)，作者：Lazyz。
