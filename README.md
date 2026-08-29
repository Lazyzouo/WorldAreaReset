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
> “Reset” means replacing non-allowlisted blocks with air and removing non-player entities unless optional hot restoration is enabled. Restoration reads a separate template world folder and writes block data after the countdown; it does not restore block-entity inventories. Back up your world first.

## Features

- Scheduled terrain maintenance for administrator-defined free-for-all PvP zones.
- Administrator cleanup through `/war cleanup`, using only `cleanup.*` settings.
- Manual hot restoration through `/war recreate`, using only `recreate.*` settings.
- Cleanup and recreate each use one `worlds` module list; every world owns its own `regions` list.
- Automatic configuration migration that adds new options without replacing administrator values or custom keys.
- MiniMessage/Hex/gradient message rendering with the fixed KitLoader five-color palette, plus safe Legacy-color compatibility.
- Bold, left-aligned rendering for every in-game plugin message; configured leading indentation is removed at runtime, and decorative divider rows omit the plugin prefix to preserve their full width.
- Inclusive X/Y/Z cuboid boundaries, a material allowlist, and independent hot-reloadable player-protection radii for cleanup and restoration (default 2 blocks).
- Optional hot restoration from external `region/` templates, using either configured cuboids or every chunk stored in the template.
- Sparse and layered templates are merged: saved block states, including explicit air, restore normally; source sections that were never saved leave existing target terrain unchanged.
- Player-free restoration: target chunks are loaded asynchronously and kept loaded with plugin tickets, even when no player is in the world.
- Restore writes reuse one current-state snapshot per target chunk and run in scheduled Folia Region batches; online-player worlds use a smaller packet-safe budget.
- Optional template filters can leave liquids or selected Bukkit Material blocks unchanged with `recreate.ignore_liquids` and `recreate.ignore_blocks`.
- Cleanup and restoration each have their own world list, bounds, countdown, timer, and notification.
- One release JAR (`WorldAreaReset-1.14.2.jar`) with English defaults, a Chinese configuration template, and built-in language support.
- Startup GitHub Release checks with optional automatic JAR download.
- Localized, color-coded latest-version, update-success, and update-failure console notices.
- A wider, fully enclosed startup banner with a language-specific PvP terrain-maintenance subtitle and dashed title separator; banner rows are prefix-free while updater notices use the purple/red plugin prefix and distinct status colors.
- Folia region scheduling and a visible startup banner.
- Automated CI, tagged releases, GitHub asset digests, and bilingual release notes.

## Requirements

| Component | Requirement |
| --- | --- |
| Server | Paper or Folia 1.21.11 |
| Java | 21 or newer |
| Permission | `worldareareset.admin` for administrator commands |

WorldAreaReset 1.14.2 is tested and supported only on Paper/Folia 1.21.11. Other Minecraft versions, Spigot, CraftBukkit, Purpur, and hybrid/modded servers are untested and receive no compatibility guarantee. `api-version: 1.21` is Bukkit metadata and does not expand this tested-version range.

## Install

1. Download `WorldAreaReset-1.14.2.jar` from the [latest release](https://github.com/Lazyzouo/WorldAreaReset/releases/latest). It starts with English defaults; set `language: "zh_CN"` to apply the Simplified Chinese configuration template.
2. Place it in the server's `plugins` directory.
3. Start the server once.
4. Review `plugins/WorldAreaReset/config.yml` and make a world backup.
5. Set `cleanup.enabled: true` only after confirming the world and boundaries.

The public defaults intentionally keep automatic cleanup disabled.

For hot restoration, create one folder per target world under `plugins/WorldAreaReset/templates/` and place only the template world's `region/` directory inside it: `plugins/WorldAreaReset/templates/<target-world-name>/region/`. Set `recreate.enabled: true` to make the automatic countdown use hot restoration, or run `/war recreate` for a manual run. Each `recreate.worlds[].name` must match the target world loaded by the server and its template folder. Target chunks are loaded asynchronously, so restoration does not require a player in that world. Both cleanup and restoration protect the configured spherical player radius (default 2 blocks), while player entities themselves are preserved.

Sparse template data is merged block by block. A state actually saved by the template, including `minecraft:air`, replaces the target state. A missing template section has no state and leaves the target block intact. Missing data does not reserve an overlap, so a later configured region can still supply saved data at that coordinate. This supports separately saved layers such as this `world_nether` template without clearing the wide outer area's lower terrain:

```yaml
recreate:
  worlds:
    - name: world_nether
      regions:
        - {min_x: -251, max_x: 251, min_y: 113, max_y: 127, min_z: -251, max_z: 251}
        - {min_x: -116, max_x: 116, min_y: 0, max_y: 112, min_z: -116, max_z: 116}
```

To preserve selected target-world blocks instead of copying them from the template, use:

```yaml
recreate:
  ignore_liquids: true # water, lava, and bubble columns
  ignore_blocks:
    - BEDROCK
    - BARRIER
```

Ignored template states leave the target block unchanged. The completion message reports the ignored count only when filters actually skip saved template positions.

One world module can contain one or many regions, and the same list can contain additional world modules. Regions are combined as a union, so gaps and duplicate overlaps are handled correctly. Cleanup and recreate use the same structure:

```yaml
cleanup:
  worlds:
    - name: world
      regions:
        - {min_x: -50, max_x: 50, min_y: 0, max_y: 128, min_z: -50, max_z: 50}
        - {min_x: 100, max_x: 150, min_y: 0, max_y: 128, min_z: 100, max_z: 150}
    - name: world_nether
      regions:
        - {min_x: -25, max_x: 25, min_y: 0, max_y: 100, min_z: -25, max_z: 25}

recreate:
  worlds:
    - name: arena_world
      regions:
        - {min_x: -4, max_x: 4, min_y: 0, max_y: 40, min_z: -4, max_z: 4}
        - {min_x: -32, max_x: 32, min_y: 32, max_y: 40, min_z: -4, max_z: 4}
    - name: arena_nether
      regions:
        - {min_x: -16, max_x: 16, min_y: 0, max_y: 80, min_z: -16, max_z: 16}
```

The first newly created `config.yml` contains copy-ready world modules for cleanup and hot restoration. A recreate module with an empty `regions` list restores every chunk actually stored in that world's template region files; this scope does not depend on loaded chunks or online players. Existing world, bounds, and global-region fields are migrated once into the module list when upgrading to configuration format 15. Format 18 adds cleanup interval units, format 22 migrates legacy message formatting, format 23 flattens the selected language and removes retired formatting/replacement settings, and format 24 updates the help metadata layout. Use explicit regions when only part of a template should be restored.

## Language

Set one of the following in `config.yml`:

```yaml
language: "en_US"
```

```yaml
language: "zh_CN"
```

`language-applied` is maintained by the plugin and records the language most recently applied at runtime. Change only `language`, then run `/war reload` or restart. All bundled messages use the fixed KitLoader palette `#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5` and the `✧` divider.

Messages accept MiniMessage tags such as `<#RRGGBB>`, `<gradient:...>`, `<bold>`, `<italic>`, `<underlined>`, `<strikethrough>`, `<obfuscated>`, `<reset>`, and `<br>`; existing `&` color codes remain compatible.

The help menus expose `{cleanup_remaining}` and `{recreate_remaining}` as live countdowns with seconds, using the configured minutes, hours, or days as the largest unit. The rows remain visible when a schedule is disabled and show `not scheduled` (or `未排程`) until that schedule is enabled.

Language files are no longer generated. Each release has exactly one uploaded plugin asset: `WorldAreaReset-<version>.jar`, containing the English default configuration and the Chinese configuration template; the active language is stored directly in `config.yml`.

Release filenames always follow `WorldAreaReset-<version>.jar`. CI and the Release workflow publish the exact file produced in `build/libs/` without renaming, relabeling, or adding other JAR assets.

The official templates keep one flat `messages` section containing only the selected language. Server-specific cleanup parameters retain safe official presets. Legacy nested language sections, `formatting`, `inline-replacements`, and root `gradient-colors` are removed during format 23 migration. Ordinary messages use the fixed KitLoader gradient, with the documented banner, severity, and help-heading color exceptions; console updater notices use only their single severity color and never a gradient.

Player-facing text is forced bold and left aligned at runtime without rewriting configured message text or colors. Leading spaces are removed independently from every line while leading legacy and hex formatting codes are preserved. Help menus, command feedback, countdown warnings, cleanup broadcasts, and completion messages use the same alignment and fixed KitLoader five-color gradient. In prefixed multiline messages, content rows keep the configured plugin prefix while decorative divider rows render without it, preventing the divider from wrapping. Every divider uses the KitLoader-style `✧` format; divider rows remain decorative and are not centering anchors.

The top of every bundled configuration uses the exact 103-column ASCII layout. The brand is contained in the multi-line symbol art; no separate plain `# WorldAreaReset by Lazyz` line is included.

## Automatic configuration migration

Administrators no longer need to delete `plugins/WorldAreaReset/config.yml` after an update. During startup or `/war reload`, WorldAreaReset recreates a missing file from bundled defaults, then parses the configuration and adds only missing paths. Existing values, custom messages, unknown extension keys, explicit `false` values, empty lists, comments, and the selected language are preserved. A reload error keeps the current plugin state running; startup stops safely when the configuration is invalid. Legacy generated language files are removed automatically.

When the main `config.yml` changes, it is copied first to `config-backups/config-v<old>-to-v<new>-<timestamp>.yml`, then written through a same-directory temporary file and atomically replaced. `config_version` is updated only after a compatible merge. Malformed YAML, parent/child structure conflicts, or incompatible value types are never overwritten. Unknown custom keys remain intact; format 23 also removes legacy language files and retired formatting/replacement settings, while format 24 splits the official help metadata row into separate plugin name, author, and version rows.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/war` or `/war help` | None | Show the localized help menu |
| `/war cleanup` | `worldareareset.admin` | Start the cleanup countdown, clear configured areas, and reset the automatic timer |
| `/war recreate` | `worldareareset.admin` | Start the recreate countdown, restore templates, and reset the automatic timer |
| `/war reload` | `worldareareset.admin` | Reload configuration/language and restart scheduling |

`worldareareset.admin` defaults to server operators.

The localized `/war help` menu is kept in sync with every supported command: `/war help`, `/war cleanup`, `/war recreate`, and `/war reload`. Custom help entries may continue using the legacy `{interval}` and `{countdown}` variables; the bundled menu labels cleanup and recreate settings separately.

## Minimal configuration

```yaml
language: "en_US"

cleanup:
  enabled: false
  interval: 3
  interval_unit: "hours"
  countdown_seconds: 10
  worlds:
    - name: "world_nether"
      regions:
        - {min_x: -200, max_x: 200, min_y: 0, max_y: 128, min_z: -200, max_z: 200}
  keep_blocks:
    - BEDROCK
    - BARRIER
  player_protection_radius: 2 # spherical blocks protected around each player
recreate:
  enabled: false
  interval: 3
  interval_unit: "hours" # hours or days
  countdown_seconds: 10
  player_protection_radius: 2 # spherical blocks protected around each player
  blocks_per_tick: 4096 # maximum blocks per Region tick; online-player worlds use a safer lower cap
  worlds:
    - name: "arena_world"
      regions: [] # empty restores every chunk stored in the template

updates:
  enabled: true
  auto_download: true
  notify_latest: true
```

## Updates

At startup, the plugin checks the official GitHub latest release asynchronously.

- If current: the console reports that the installed version is latest.
- If newer and automatic download is enabled: `WorldAreaReset-<latest-version>.jar` is downloaded to Bukkit's update directory and installed on the next restart.
- If download fails: the console prints the official release URL for manual download.
- If `updates.auto_download` is disabled: the plugin only reports the new release and URL.

The updater only accepts the single versioned release asset. When GitHub provides a SHA-256 asset digest, it is verified before installation.

## Known limitation

Cleanup asynchronously loads at most 16 chunks at a time and limits each chunk to a bounded per-tick write batch; chunks within one chunk of a player use an even smaller batch to reduce update bursts. Recreate loads at most 32 target chunks at a time when no player is present; with players online it processes only two target chunks at once, limits each Region tick to a small block batch, and waits one tick between chunks. Large areas still consume region tick time and should be tested off-peak; read the [administrator guide](WorldAreaReset.md) before production use.

WorldAreaReset does not define PvP zones or control combat permissions. Configure the same boundaries separately in the server's region or PvP-management system.

## Build

```bash
./gradlew clean build
```

`WorldAreaReset-1.14.2.jar` is written to `build/libs/`. The build verifies that its packaged English `config.yml` and Chinese `config-zh_CN.yml` match the official source configurations.

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
> “重置”默认是把非白名单方块替换为空气，并删除非玩家实体；启用地形热恢复后则从独立模板世界文件夹读取方块并写回。恢复不会还原方块实体库存，启用前必须备份世界。

## 功能

- 面向管理员指定自由 PvP 区域的定时地形维护。
- `/war cleanup` 管理员手动清理，只重置清理自己的倒计时。
- `/war recreate` 管理员手动热恢复，只使用 `recreate.*` 参数。
- 普通清理与热恢复均保护玩家周围可热调节的球形方块半径，默认 2 格。
- `/war help` 查看本地化帮助，管理员帮助会列出全部管理员指令。
- `/war reload` 重载配置、语言文件并重新排程。
- cleanup 与 recreate 均使用一个 `worlds` 世界模块列表，每个世界模块拥有自己的 `regions` 区域列表。
- 自动迁移配置，只补充新选项，不覆盖管理员参数或自定义键。
- 支持 MiniMessage/Hex/渐变消息解析；五色调色板仅用于明确写入渐变标签的文本，并兼容旧 Legacy 色码。
- 所有插件游戏内文本均以粗体、左对齐显示；运行时会移除配置中的行首缩进，并让装饰分割线不显示插件前缀，以保留完整宽度。
- 包含边界的 X/Y/Z 长方体范围与材质白名单。
- 单个通用发布包 (`WorldAreaReset-1.14.2.jar`) 内置英文默认配置和中文配置模板，运行时只保留当前语言。
- 可从 `templates/<世界名>/region/` 外部模板执行地形热恢复，支持多个世界、指定区域或模板中实际存在的全部区块；目标区块会异步加载，因此世界内没有玩家时也能恢复，并保护配置的玩家周围方块半径。
- 稀疏或分层模板会融合恢复：模板明确保存的方块状态（包含空气）会写入，模板未保存的 section 会保留目标地形。
- 恢复写入会合并同一区块内的多个配置区域，复用一次当前状态快照，并通过 Folia Region 分批调度；在线玩家世界自动使用更小的数据包安全批次。
- 可用 `recreate.ignore_liquids` 忽略模板中的液体，或用 `recreate.ignore_blocks` 指定要忽略的 Bukkit Material；被忽略位置保留目标世界原方块。
- 启动时检查 GitHub Release，并可自动下载新版 JAR。
- 本地化并带有状态颜色的最新版、下载成功和更新失败后台提示。
- 加宽并完整封闭启动横幅，按当前语言显示 PvP 地形维护副标题与标题区虚线分隔线；横幅不带消息前缀，更新器提示使用紫红色插件前缀及不同状态颜色。
- Folia 区域调度与醒目的启动横幅。
- 自动 CI、标签发布、GitHub 资源摘要及双语 Release 更新日志。

## 版本限制

WorldAreaReset 1.14.2 仅在 Paper/Folia 1.21.11 上测试并提供兼容支持，需要 Java 21 或更高版本。其他 Minecraft 版本、Spigot、CraftBukkit、Purpur 及混合/模组服务端均未测试，不提供兼容保证。`plugin.yml` 中的 `api-version: 1.21` 只是 Bukkit 元数据，不代表所有 1.21.x 版本均受支持。

## 安装

1. 从[最新 Release](https://github.com/Lazyzouo/WorldAreaReset/releases/latest)下载 `WorldAreaReset-1.14.2.jar`。默认使用英文；将 `language` 设为 `zh_CN` 后重载即可应用简体中文配置模板。
2. 放入服务器 `plugins` 目录。
3. 启动一次服务器。
4. 检查 `plugins/WorldAreaReset/config.yml` 并备份世界。
5. 确认世界名称和坐标后，再将 `cleanup.enabled` 改为 `true`。

公开默认配置会关闭自动清理，防止首次安装误删世界。

启用热恢复时，在插件自动创建的目录中为每个目标世界创建 `plugins/WorldAreaReset/templates/<世界名>/region/`，只放入模板世界的 `region/` 文件夹。将 `recreate.enabled` 设为 `true` 可自动执行，也可用 `/war recreate` 手动触发。每个 `recreate.worlds[].name` 必须同时匹配服务器已加载的目标世界及其模板文件夹；恢复会异步加载目标区块，所以目标世界没有玩家也能执行。普通清理和热恢复都会保护配置的玩家周围球形方块半径（默认 2 格），但不会删除玩家实体。

稀疏模板按方块状态融合：模板中明确保存的状态（包括 `minecraft:air`）会覆盖目标；模板缺失的 section 没有状态，不会清空目标方块。缺失数据不会占用重叠坐标，因此后续配置区域仍可在该坐标提供已保存数据。下列 `world_nether` 配置可直接用于“宽外层只保存高层、窄内层保存低层”的模板，不会再把外层下方清成虚空：

```yaml
recreate:
  worlds:
    - name: world_nether
      regions:
        - {min_x: -251, max_x: 251, min_y: 113, max_y: 127, min_z: -251, max_z: 251}
        - {min_x: -116, max_x: 116, min_y: 0, max_y: 112, min_z: -116, max_z: 116}
```

如需保留目标世界中的液体或指定方块，不从模板复制这些位置，可配置：

```yaml
recreate:
  ignore_liquids: true # 水、岩浆和气泡柱
  ignore_blocks:
    - BEDROCK
    - BARRIER
```

被忽略的模板方块会保留目标世界原方块；只有实际忽略了模板位置时，完成消息才会显示该数量。

一个世界模块可以包含一个或多个区域，同一列表也可继续添加其他世界模块。多个区域按并集处理，区域之间的空缺与重叠部分都能正确处理。cleanup 与 recreate 使用相同结构：

```yaml
cleanup:
  worlds:
    - name: world
      regions:
        - {min_x: -50, max_x: 50, min_y: 0, max_y: 128, min_z: -50, max_z: 50}
        - {min_x: 100, max_x: 150, min_y: 0, max_y: 128, min_z: 100, max_z: 150}
    - name: world_nether
      regions:
        - {min_x: -25, max_x: 25, min_y: 0, max_y: 100, min_z: -25, max_z: 25}

recreate:
  worlds:
    - name: arena_world
      regions:
        - {min_x: -4, max_x: 4, min_y: 0, max_y: 40, min_z: -4, max_z: 4}
        - {min_x: -32, max_x: 32, min_y: 32, max_y: 40, min_z: -4, max_z: 4}
    - name: arena_nether
      regions:
        - {min_x: -16, max_x: 16, min_y: 0, max_y: 80, min_z: -16, max_z: 16}
```

首次新建的 `config.yml` 会直接包含 cleanup 与 recreate 的可复制世界模块。recreate 世界模块的 `regions` 留空时，按模板 region 文件中实际存在的区块恢复，不依赖当前已加载区块或在线玩家；只需恢复模板的一部分时请填写明确区域。升级到配置格式 15 时，旧世界、边界和全局区域字段会一次性迁移为模块列表，格式 18 增加清理周期单位参数，格式 22 迁移旧消息格式；格式 23 将当前语言消息扁平化并移除旧的 formatting 与 inline-replacements 设置，格式 24 将帮助菜单元数据拆分为名称、作者和版本三行。

## 语言切换

每个 Release 仅上传一个通用 JAR，文件名固定遵循 `WorldAreaReset-<版本>.jar`；该包内置英文默认配置和中文配置模板，运行时只保留当前语言。GitHub 页面自动显示的源码 ZIP/TAR 并非项目额外上传的 Release 附件；CI 和 Release 工作流必须原样上传 `build/libs/` 的单个构建文件，禁止改名、改标签或加入其他 JAR。在 `config.yml` 设置 `language: "zh_CN"` 或 `language: "en_US"` 后执行 `/war reload` 仍可随时切换语言；切换时会刷新插件官方配置头、注释和扁平消息。`language-applied` 由插件维护，记录最近一次已应用语言；只修改 `language`，不要手动修改 `language-applied`。旧版 `lang` 文件会自动删除。

所有默认消息统一使用 KitLoader 五色渐变 `#FFB7D5`、`#D7C7FF`、`#B9E7FF`、`#D7C7FF`、`#FFB7D5` 和 `✧` 分割线。消息支持 `<#RRGGBB>`、`<gradient:...>`、`<bold>`、`<italic>`、`<underlined>`、`<strikethrough>`、`<obfuscated>`、`<reset>` 和 `<br>`，旧版 `&` 色码仍可使用。

帮助菜单中的 `{cleanup_remaining}` 与 `{recreate_remaining}` 会按配置的分钟、小时或天作为最大单位，并精确显示到秒；对应计划关闭时仍保留状态行并显示“未排程”。

所有游戏内文本都会在运行时强制显示为粗体并统一左对齐，不会改写配置中的消息文本或颜色。每一行的行首空格都会被移除，同时保留行首旧式颜色码与十六进制颜色码。帮助菜单、指令反馈、清理倒计时、清理广播和完成消息均使用相同对齐规则；带前缀的多行消息仅在正文行显示已配置的插件前缀，装饰分割线不显示前缀，从而避免分割线折行。所有分割线统一使用与 Kitloader 相同的可见文本 `---------------- ✧ ----------------`（两侧各 16 个连字符），分割线仍仅用于装饰，不作为居中基准。每个内置配置顶部均采用精确的 103 列 ASCII 版式，品牌包含在多行符号拼凑图案中，不再添加独立的普通品牌行。

## 配置自动迁移

更新后不再需要删除 `plugins/WorldAreaReset/config.yml`。服务器启动或执行 `/war reload` 时，如果文件缺失，插件会先从内置默认值恢复，再解析配置并补充缺失路径；格式 23 会把旧的双语消息、`formatting`、`inline-replacements` 和 root `gradient-colors` 清理并写入当前语言的扁平消息，同时统一 KitLoader 五色渐变和 `✧` 分割线。其他已有参数、未知扩展键、明确设置的 `false`、注释及语言选择都会保留。

主 `config.yml` 需要更新时，会先备份到 `config-backups/config-v<旧>-to-v<新>-<时间>.yml`，再写入同目录临时文件并原子替换正式文件。只有兼容合并成功后才更新 `config_version`；YAML 损坏或结构冲突时不会覆盖原文件。未知自定义键会保留，格式 23 会移除旧语言文件和已废弃的显示设置，格式 24 会拆分官方帮助菜单元数据行。

## 自动更新

服务器启动时会异步检查官方 GitHub 最新 Release。发现新版本后，更新器会下载 `WorldAreaReset-<最新版本>.jar` 到 Bukkit 更新目录，并在下一次重启时安装；下载失败则在后台显示官方手动下载地址。

## 已知限制

普通清理最多同时异步加载 16 个区块，并为每个区块设置有上限的每 tick 写入批次；玩家一格区块范围内的区块使用更小批次，避免方块更新突发。recreate 在没有玩家时最多异步加载 32 个目标区块；有玩家在线时每次只处理两个目标区块，将每个 Region tick 限制为小批次方块，并在区块之间间隔一个 tick，以减少数据包突发和区域争用。同一区块的多个配置区域会合并处理，并通过当前状态快照避免重复 Bukkit 读取。大范围仍会消耗 region tick 时间，正式使用前请先小范围测试、避开高峰并阅读 [管理员配置与逻辑说明](WorldAreaReset.md)。

WorldAreaReset 不会划分 PvP 区域或控制战斗权限；请在服务器的区域或 PvP 管理系统中另行配置相同边界。

项目采用 [MIT License](LICENSE)，作者：Lazyz。
