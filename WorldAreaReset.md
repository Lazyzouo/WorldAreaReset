# WorldAreaReset Administrator Guide / 管理员配置与逻辑说明

> [!IMPORTANT]
> WorldAreaReset is fully open source and contains no telemetry, remote administration, hidden data collection, or backdoor functionality. Server content is not uploaded; plugin-created files remain local. The only optional runtime network access is the official GitHub Release update check/download. / WorldAreaReset 完全开源，不含遥测、远程管理、隐藏数据收集或后门功能，不会上传服务器内容，插件创建的文件仅保存在本机；唯一可选联网行为是官方 GitHub Release 更新检查与下载。详见 [PRIVACY.md](PRIVACY.md)。

> Applies to / 适用于：`WorldAreaReset 1.14.0`<br>
> Project / 项目地址：https://github.com/Lazyzouo/WorldAreaReset

## English

### 1. Compatibility limits

- Tested and supported server: Paper/Folia 1.21.11 only.
- Required runtime: Java 21 or newer.
- Other Minecraft versions, Spigot, CraftBukkit, Purpur, and hybrid/modded servers are untested and are not guaranteed to work.
- The `api-version: 1.21` value in `plugin.yml` is Bukkit API metadata; it does not mean every 1.21.x server is supported.

Always include the exact server implementation and build when reporting a problem.

### 2. Purpose and runtime model

WorldAreaReset provides scheduled terrain maintenance for administrator-defined free-for-all PvP zones. The default mode cleans an inclusive cuboid in one or more loaded worlds: every non-air block not listed in `cleanup.keep_blocks` and outside the configured player-protection radius is changed to air without physics, and every non-player entity inside the cuboid is removed. Optional hot restoration reads block data from external `region/` templates and applies it after the same countdown while protecting the corresponding radius.

It does not create regions, enable PvP, manage combat permissions, regenerate terrain from a seed, restore tile-entity inventories, respect claims, or keep an automatic backup. Configure the same boundaries separately in the server's region or PvP-management system.

The plugin creates `plugins/WorldAreaReset/templates/` on startup only for recreate. Cleanup reads no template settings: its `worlds` list directly groups each loaded world with its cleanup regions. For hot restoration, each `recreate.worlds[].name` must match a target world loaded by the server and `plugins/WorldAreaReset/templates/<world-name>/region/`. Target chunks are loaded asynchronously and held by plugin tickets, so a player does not need to be online in that world. Templates are read directly from Anvil files, so Folia never creates a second Bukkit world.

Cleanup and recreate use the same world-module shape. Each module owns one or more cuboid regions, while additional modules add more worlds. Recreate modules with empty `regions` restore every chunk actually stored in that world's template region files, regardless of current chunk loading or player presence:

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

The first newly created `config.yml` contains copy-ready world modules for both modes. Upgrading to configuration format 15 converts the retired world, bounds, switches, and global region lists into these modules and removes those retired fields. Format 18 adds cleanup interval units; format 22 migrates plugin-owned legacy message formatting; format 23 flattens the selected language, removes retired formatting/replacement settings, and applies the fixed KitLoader palette and `✧` divider.

Sparse template data is merged per block position. A template state that exists, including explicit `minecraft:air`, replaces the target state; an omitted template section has no state and preserves the target block. Omitted data does not claim an overlap, so another configured region can still supply a saved state there. This layered `world_nether` configuration therefore preserves the outer area's lower terrain while restoring its saved upper layer:

```yaml
recreate:
  worlds:
    - name: world_nether
      regions:
        - {min_x: -251, max_x: 251, min_y: 113, max_y: 127, min_z: -251, max_z: 251}
        - {min_x: -116, max_x: 116, min_y: 0, max_y: 112, min_z: -116, max_z: 116}
```

### Commands

| Command | Permission | Meaning |
| --- | --- | --- |
| `/war` or `/war help` | None | Show the localized help menu |
| `/war cleanup` | `worldareareset.admin` | Start ordinary cleanup and reset the automatic timer |
| `/war recreate` | `worldareareset.admin` | Start hot restoration and reset the automatic timer |
| `/war reload` | `worldareareset.admin` | Reload configuration/language and restart scheduling |

The bundled English and Chinese administrator help menus must contain every command listed here. When a new command or alias is added, update both help menus, language files, tab completion, `plugin.yml` usage, and this command table together.

### 3. Configuration reference

| Path | Default | Meaning |
| --- | --- | --- |
| `config_version` | `23` | Automatically maintained public configuration format marker |
| `language` | `en_US` | The single `WorldAreaReset-1.14.0.jar` starts with English defaults; set `zh_CN` to apply the Simplified Chinese configuration template |
| `language-applied` | Selected language | Plugin-maintained marker; change `language` only, then reload or restart |
| `messages` | Flat selected-language messages | The active language is written directly to `messages`; legacy nested language sections and retired formatting settings are removed during format 23 migration |
| `cleanup.enabled` | `false` | Enables automatic scheduling; manual cleanup remains available when false |
| `cleanup.interval` / `cleanup.interval_unit` | `3` / `hours` | Automatic cleanup period amount and unit: `minutes`, `hours`, or `days` |
| `cleanup.countdown_seconds` | `10` | Cleanup-mode warning delay for automatic and `/war cleanup` |
| `cleanup.worlds` | One `world_nether` module | World modules containing `name` and one or more inclusive `regions` |
| `cleanup.keep_blocks` | `BEDROCK`, `BARRIER` | Bukkit Material names preserved throughout the area |
| `cleanup.player_protection_radius` | `2` | Spherical block radius protected around players during cleanup; reload to apply changes |
| `recreate.enabled` | `false` | Makes the automatic countdown use hot restoration; `/war recreate` always does |
| `recreate.interval` / `recreate.interval_unit` | `3` / `hours` | Automatic recreate cycle in hours or days |
| `recreate.countdown_seconds` | `10` | Recreate-mode warning delay for automatic and manual restoration |
| `recreate.player_protection_radius` | `2` | Spherical block radius protected around players during restoration; reload to apply changes |
| `recreate.blocks_per_tick` | `4096` | Maximum blocks written per Region tick; online-player worlds automatically use a safer lower cap |
| `recreate.ignore_liquids` | `false` | Leaves template water, lava, and bubble columns unchanged in the target world |
| `recreate.ignore_blocks` | `[]` | Bukkit Material names whose saved template states are ignored; target blocks remain unchanged |
| `recreate.worlds` | One `arena_world` module | World modules containing `name` and `regions`; empty regions use all stored template chunks |
| `updates.enabled` | `true` | Checks the official latest release at startup |
| `updates.auto_download` | `true` | Downloads a newer JAR to Bukkit's update directory |
| `updates.notify_latest` | `true` | Reports when the installed version is current |

Official defaults and first-creation examples are stored in `src/main/resources/config-en_US.yml`, `src/main/resources/config.yml`, and `defaults/`. The single release JAR contains identical code, the English `config.yml`, and the Chinese `config-zh_CN.yml` template; live server configuration belongs only in `plugins/WorldAreaReset/config.yml` and is excluded from the repository. When `language` changes, plugin-owned comments, the configuration header, and flat `messages.*` values are refreshed to the selected template while administrator parameters and unknown keys remain unchanged.

At every startup, the plugin parses the live `config.yml` and matching bundled default as structured YAML. Existing parameters, unknown custom keys, selected language, and parsed comments are retained. Format 15 migrates retired world and region fields, format 18 converts cleanup interval units, format 22 migrates legacy message formatting, and format 23 flattens the selected language, removes `formatting`, `inline-replacements`, and root `gradient-colors`, and applies the fixed KitLoader palette and `✧` divider. Legacy `lang` files are removed after migration.

Before changing the main `config.yml`, the plugin creates `config-backups/config-v<old>-to-v<new>-<timestamp>.yml`; it then writes a same-directory temporary file, flushes it, and atomically replaces the original. `config_version` advances only after the entire structure is compatible. Invalid YAML, a scalar where a section is required, a section where a value is required, or incompatible value types block the merge without modifying the original. A blocked main configuration stops plugin startup before cleanup scheduling.

### 4. Message and language logic

The active configuration is stored at:

```text
plugins/WorldAreaReset/config.yml (active language messages)
```

The official configurations keep editable command feedback, broadcasts, help menus, and updater notices in one flat `messages.*` section. Switching `language` replaces the bundled message values with the selected language; no second language namespace or generated language file remains.

Only server-specific settings such as world, boundaries, interval, allowlist, language, and updater switches use sanitized official presets. Message text and other non-server-specific presentation settings remain complete in the public templates.

Supported placeholders include `{name}`, `{version}`, `{author}`, `{interval}`, `{countdown}`, `{cleanup_interval}`, `{cleanup_interval_unit}`, `{cleanup_remaining}`, `{cleanup_countdown}`, `{recreate_interval}`, `{recreate_interval_unit}`, `{recreate_remaining}`, `{recreate_countdown}`, `{world}`, `{worlds}`, `{time}`, `{blocks}`, `{protected}`, `{ignored}`, `{entities}`, `{failed}`, `{current}`, `{reason}`, and `{url}` where relevant. `{cleanup_remaining}` and `{recreate_remaining}` show a live countdown in the configured minutes, hours, or days unit. Their help rows are omitted when the corresponding `cleanup.enabled` or `recreate.enabled` switch is false. In a restore completion message, a line containing `{ignored}` is omitted when no template blocks were ignored. For multi-world cleanup or restoration, `{world}` and `{worlds}` both contain the comma-separated world list.

The startup banner is prefix-free and follows the selected language; all other updater notices use the configured purple/red `WorldAreaReset` prefix. Banner fields use border `#8A2387`, title `#E62028`, labels/separators `#D7C7FF`, values `#B9E7FF`, and open-source rows `#FF69B4`. Updater states use `#D7C7FF` for checking, `#B9E7FF` for current/downloaded, bold `#FFB7D5` for available/manual download, and bold `#E62028` for failures.

The startup banner uses a dynamic interior width: at least 60 columns, or one column wider than the widest visible content line. Its identity section contains the centered service name/version and a centered subtitle in the selected language, followed by a full-width dashed separator before runtime metadata. Every title, metadata, project URL, and privacy row is enclosed by matching left and right borders. Padding ignores legacy color codes and counts Chinese characters as double-width terminal columns, keeping the right border aligned with space after the longest field.

Every player-facing plugin message is forced bold and left aligned at runtime. Leading whitespace is removed independently from every line without rewriting configuration, while leading legacy color/style codes and hex colors are preserved; bold styling is reapplied after color and reset codes. Help menus, command feedback, countdown warnings, cleanup broadcasts, and completion messages all use this alignment and the fixed KitLoader five-color gradient. In prefixed multiline messages, content rows retain the configured plugin prefix while decorative divider rows omit it so the full divider does not wrap. All divider rows use the exact KitLoader-style `✧` divider; old rows are migrated safely while other custom text is preserved. The bundled configuration header uses the exact 103-column ASCII layout, with the brand contained in the multi-line symbol art and no separate plain brand line.

### 5. Automatic cleanup sequence

1. The plugin safely migrates the configuration and selected-language messages, then loads them.
2. If both `cleanup.enabled` and `recreate.enabled` are false, no recurring task is scheduled.
3. Each enabled mode gets its own fixed-rate asynchronous timer: cleanup converts `cleanup.interval` using `cleanup.interval_unit` (`minutes`, `hours`, or `days`); recreate converts `recreate.interval` using `recreate.interval_unit` (`hours` or `days`).
4. The plugin broadcasts `warning` or `restore_warning` and waits the countdown configured for the selected mode.
5. It broadcasts one `start_cleanup` (or `start_restore`) message containing the affected world list, then submits the work through bounded asynchronous chunk-load queues.
6. Clear mode removes blocks outside each player's configured protection radius and non-player entities. Cleanup loads at most 16 chunks at once and uses bounded Region-tick write batches, with a smaller batch within one chunk of a player to reduce block-update bursts. Restore mode reads source block data asynchronously from Anvil files, asynchronously loads at most 32 target chunks at once, skips protection-radius scan chunks when no player is in the target world, and uses packet-safe batches when players are present; player entities are never removed. Explicitly saved air is restored; an omitted template section preserves the target block.
7. After all tasks report completion, one `finish_cleanup` or `finish_restore` message is broadcast with aggregate totals, failed chunks, and elapsed time. The restore message omits the ignored-template-block line when its count is zero.

The fixed interval starts when the plugin is enabled or reloaded, not when a cleanup finishes.

### 6. Manual cleanup sequence

`/war cleanup` requires `worldareareset.admin` and always runs ordinary area cleanup. `/war recreate` requires the same permission and always runs hot restoration. Each command has its own settings and only resets its own timer:

1. Cancel the existing recurring timer and any pending countdown.
2. Start the same localized countdown used by automatic cleanup.
3. Run the same cleanup engine after the delay.
4. If the corresponding automatic mode is enabled, start a fresh interval from command execution time.

The reset duration follows the selected automatic mode: cleanup uses `cleanup.interval` plus `cleanup.interval_unit`, while recreate uses `recreate.interval` plus `recreate.interval_unit`. Neither mode is hardcoded to three hours.

### 7. Reload sequence

`/war reload` runs the safe schema migration for `config.yml`, recreates a missing file from bundled defaults, reloads the selected language file, cancels the recurring timer and pending countdown, then creates a new timer if enabled. A reload migration error leaves the current plugin state running; startup still stops on invalid configuration. The same migration also runs during plugin startup; replacing the JAR still requires a normal server restart to load the new code. Region tasks that have already begun are not cancelled.

### 8. Update sequence

At startup the updater requests:

```text
https://api.github.com/repos/Lazyzouo/WorldAreaReset/releases/latest
```

Version tags are compared numerically. The updater downloads the single `WorldAreaReset-<latest-version>.jar` release asset to Bukkit's update folder under the currently running JAR filename. GitHub's SHA-256 digest is checked when present. The new JAR takes effect on the following restart.

Each GitHub Release has exactly one uploaded JAR asset. GitHub's automatically generated source archives remain visible separately and cannot be removed from the Release page.

The required filename template is `WorldAreaReset-<version>.jar`. CI and Release automation upload the exact build output without renaming, relabeling, or adding another JAR.

Disable the network check with `updates.enabled: false`, or keep notifications without downloading by setting `updates.auto_download: false`.

### 9. Limits and operational risks

- The default cuboid checks up to 20,744,529 block positions across approximately 676 chunks.
- Cleanup asynchronously loads at most 16 chunks and gives every chunk a bounded per-tick write budget. Chunks within one chunk of a player use a smaller budget to reduce block-update bursts. Recreate chunk loading is asynchronous and limited to 32 concurrent target chunks when no player is present. When players are online, only two target chunks are processed at once; each Region tick writes a small block batch and a one-tick gap separates chunks to reduce packet bursts and contention on player regions. Each target chunk reuses one current-state snapshot, so large areas still consume region tick time.
- There is no running-cleanup lock; overlapping manual/automatic executions are possible.
- Invalid min/max ordering is not automatically corrected.
- Invalid Material names are logged and ignored.
- Player entities are preserved. Restore mode skips the configured spherical radius around players; every other block is written to the template state.
- All non-player entity categories are removed; no entity allowlist exists.
- WorldGuard, claims, protected regions, and ownership are not consulted.
- Automatic migration preserves unknown custom keys; format 15 removes only the retired world/bounds/region fields it converts into modules, format 16 adds missing restore pacing values, format 17 adds template filter values, and format 18 converts the legacy cleanup interval field.
- A failed or partial cleanup has no built-in rollback.

Back up the world, pre-generate chunks, test a small range, and run large cleanups off-peak.

---

## 中文

### 1. 版本与兼容限制

- 仅测试并支持 Paper/Folia 1.21.11。
- 运行环境必须为 Java 21 或更高版本。
- 其他 Minecraft 版本、Spigot、CraftBukkit、Purpur 及混合/模组服务端均未测试，不提供兼容保证。
- `plugin.yml` 中的 `api-version: 1.21` 是 Bukkit API 元数据，不代表所有 1.21.x 服务端都受支持。

报告问题时必须提供准确的服务端实现与构建号。

### 2. 用途与运行模型

WorldAreaReset 用于对管理员指定的自由 PvP 区域进行定时地形维护。插件会清理一个或多个已加载世界内、包含边界的长方体区域：所有不在 `cleanup.keep_blocks` 白名单且不在玩家保护半径内的非空气方块会在不触发物理更新的情况下改为空气；所有位于范围内的非玩家实体会被删除。

插件不会创建区域、开启 PvP、管理战斗权限、重新生成种子地形、恢复 schematic、识别领地或自动备份。请在服务器的区域或 PvP 管理系统中另行配置相同边界。

插件只为 recreate 创建 `plugins/WorldAreaReset/templates/`；cleanup 不读取任何模板参数，而是从 `cleanup.worlds` 直接取得每个已加载世界及其清理区域。热恢复时，每个 `recreate.worlds[].name` 必须同时匹配服务器已加载的目标世界和 `plugins/WorldAreaReset/templates/<世界名>/region/`。目标区块会异步加载并由插件区块票保持，因此目标世界没有玩家在线时也能恢复。模板会直接读取 Anvil region 文件，Folia 不会创建第二个 Bukkit 世界。

cleanup 与 recreate 使用相同的世界模块结构。每个模块包含一个世界及一个或多个长方体区域，继续添加模块即可处理更多世界。recreate 模块的 `regions` 留空时恢复模板 region 文件中实际存在的全部区块，不依赖当前已加载区块或玩家在线状态：

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

首次新建的 `config.yml` 会直接包含两种模式的可复制世界模块。升级到配置格式 15 时，旧世界、坐标、开关和全局区域列表会转换为这些模块并被移除；格式 16 会补充恢复速度参数，格式 17 会补充模板方块过滤参数。

稀疏模板会按方块位置融合。模板明确保存的状态（包括 `minecraft:air`）会覆盖目标；模板缺失的 section 没有状态，会保留目标方块。缺失数据不会占用重叠坐标，因此其他配置区域仍可在该位置提供已保存状态。下面的 `world_nether` 分层配置会恢复外层已保存的高层，同时保留外层下方原有地形：

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

### 指令

| 指令 | 权限 | 作用 |
| --- | --- | --- |
| `/war` 或 `/war help` | 无 | 查看本地化帮助菜单 |
| `/war cleanup` | `worldareareset.admin` | 启动普通清理并重置自动计时器 |
| `/war recreate` | `worldareareset.admin` | 启动地形热恢复并重置自动计时器 |
| `/war reload` | `worldareareset.admin` | 重载配置/语言并重新排程 |

内置英文和中文管理员 help 菜单必须包含此处列出的全部指令。新增指令或别名时，要同步更新两个 help 菜单、语言文件、Tab 补全、`plugin.yml` usage 以及本表。

### 3. 配置说明

| 路径 | 默认值 | 说明 |
| --- | --- | --- |
| `config_version` | `23` | 由插件自动维护的公开配置格式版本 |
| `language` | `en_US` | 单个 `WorldAreaReset-1.14.0.jar` 默认使用英文；设为 `zh_CN` 后重载即可应用简体中文配置模板 |
| `language-applied` | 当前语言 | 插件自动维护的最近应用语言标记；只修改 `language`，然后 reload 或重启 |
| `messages` | 当前语言扁平消息 | 运行时配置只保留 `messages` 下当前语言的一份消息；旧的双语命名空间和 formatting 设置会在格式 23 清理 |
| `cleanup.enabled` | `false` | 是否启用自动排程；关闭时仍可手动清理 |
| `cleanup.interval` / `cleanup.interval_unit` | `3` / `hours` | 自动清理周期数值和单位：`minutes`、`hours` 或 `days` |
| `cleanup.countdown_seconds` | `10` | 普通清理自动倒计时与 `/war cleanup` 倒计时 |
| `cleanup.worlds` | 一个 `world_nether` 模块 | 世界模块列表；每项包含 `name` 及一个或多个包含边界的 `regions` |
| `cleanup.keep_blocks` | `BEDROCK`, `BARRIER` | 整个区域内保留的 Bukkit Material |
| `cleanup.player_protection_radius` | `2` | 普通清理时保护玩家周围的球形方块半径，修改后重载生效 |
| `recreate.enabled` | `false` | 让自动倒计时执行热恢复；`/war recreate` 始终执行热恢复 |
| `recreate.interval` / `recreate.interval_unit` | `3` / `hours` | 自动热恢复周期，单位为小时或天 |
| `recreate.countdown_seconds` | `10` | 热恢复自动倒计时与 `/war recreate` 倒计时 |
| `recreate.player_protection_radius` | `2` | 热恢复时保护玩家周围的球形方块半径，修改后重载生效 |
| `recreate.blocks_per_tick` | `4096` | 每个 Region tick 的最大恢复方块数；在线玩家世界会自动使用更低的安全批次 |
| `recreate.ignore_liquids` | `false` | 忽略模板中的水、岩浆和气泡柱，目标世界对应方块保持不变 |
| `recreate.ignore_blocks` | `[]` | 忽略模板中指定的 Bukkit Material，目标世界对应方块保持不变 |
| `recreate.worlds` | 一个 `arena_world` 模块 | 世界模块列表；每项含 `name` 和 `regions`，空区域使用模板中实际存在的全部区块 |
| `updates.enabled` | `true` | 启动时检查官方最新 Release |
| `updates.auto_download` | `true` | 将新版 JAR 下载到 Bukkit 更新目录 |
| `updates.notify_latest` | `true` | 当前已是最新版时在后台提示 |

官方默认配置和首次创建时的参数示例位于 `src/main/resources/config-en_US.yml`、`src/main/resources/config.yml` 与 `defaults/`。发布 JAR 使用英文 `config.yml`，同时包含中文 `config-zh_CN.yml` 模板和完整配置注释；服务器实际配置只应位于 `plugins/WorldAreaReset/config.yml`，该运行目录已排除在仓库之外。修改 `language` 后重载会刷新插件官方配置头、注释和扁平消息；旧版 `lang` 文件会自动删除。

插件每次启动都会把服务器实际 `config.yml` 与内置默认配置作为结构化 YAML 解析。管理员已有参数、未知自定义键、语言选择和已解析注释都会保留。格式 15 会把旧世界与区域字段迁移为模块列表，格式 18 会将旧的 `cleanup.interval_minutes` 转换为 `cleanup.interval` 与 `cleanup.interval_unit`，格式 22 迁移旧消息格式，格式 23 会清理双语消息、`formatting`、`inline-replacements` 和 root `gradient-colors`，写入当前语言的扁平消息并统一 KitLoader 五色渐变和 `✧` 分割线。

主 `config.yml` 需要变化时，会先创建 `config-backups/config-v<旧>-to-v<新>-<时间>.yml`，再写入同目录临时文件、刷新到磁盘，并在支持时原子替换正式文件。只有整体结构兼容时才推进 `config_version`。YAML 无效、应为配置节的位置出现标量、应为值的位置出现配置节或值类型不兼容时，迁移会停止且不会修改原文件。

### 4. 消息与语言逻辑

运行时只维护 `plugins/WorldAreaReset/config.yml` 一份配置，语言消息直接保存在 `messages.*` 下。

官方配置在一个扁平的 `messages.*` 区域中保存可编辑的指令反馈、广播、帮助菜单和更新提示。切换 `language` 后，插件会将当前语言模板写回该区域，不再同时保留两份语言。

只有世界、坐标范围、周期、白名单、语言和更新开关等服务器专用参数使用脱敏后的官方预设；消息文本及其他与服务器参数无关的显示内容会完整保留在公开模板中。

根据消息场景可使用 `{name}`、`{version}`、`{author}`、`{interval}`、`{countdown}`、`{cleanup_interval}`、`{cleanup_interval_unit}`、`{cleanup_remaining}`、`{cleanup_countdown}`、`{recreate_interval}`、`{recreate_interval_unit}`、`{recreate_remaining}`、`{recreate_countdown}`、`{world}`、`{worlds}`、`{time}`、`{blocks}`、`{protected}`、`{ignored}`、`{entities}`、`{failed}`、`{current}`、`{reason}` 和 `{url}` 等变量。`{cleanup_remaining}` 与 `{recreate_remaining}` 会按配置的分钟、小时或天显示距离下次自动任务的实时倒计时；对应的 `cleanup.enabled` 或 `recreate.enabled` 关闭时，帮助菜单会隐藏对应行。恢复完成消息中，未忽略模板方块时会自动省略含 `{ignored}` 的整行。多世界清理或恢复时，`{world}` 与 `{worlds}` 都会得到逗号分隔的世界列表。

启动横幅之外的全部更新器通知共用配置中的紫红色 `WorldAreaReset` 前缀；横幅本身不添加前缀并遵循当前语言。横幅边框使用 `#8A2387`，标题使用 `#E62028`，标签/分隔线使用 `#D7C7FF`，值使用 `#B9E7FF`，开源行使用 `#FF69B4`；更新状态依次使用 `#D7C7FF`（检查中）、`#B9E7FF`（最新版/下载完成）、粗体 `#FFB7D5`（发现更新/手动下载）和粗体 `#E62028`（失败）。

启动横幅内部宽度动态计算：最小 60 列，并至少比最长可见内容行多 1 列。身份标题区包含居中的服务名称与版本，以及遵循当前语言的居中 PvP 地形维护副标题，随后使用整行虚线将标题区与运行信息分隔。标题、元数据、项目地址和隐私声明各行均由同色左右边框完整包围。留白计算会忽略旧式颜色码，并将中文字符按双列终端宽度计算，使右侧边框保持对齐，并与最长字段保留空隙。

所有插件游戏内文本都会在运行时强制显示为粗体并统一左对齐。系统会逐行移除行首空白而不改写配置，同时保留行首旧式颜色/样式码与十六进制颜色码；颜色码和重置码之后仍会重新应用粗体。默认消息统一使用 KitLoader 五色渐变，帮助菜单、指令反馈、清理倒计时、清理广播和完成消息都使用该对齐规则。带前缀的多行消息会在正文行保留已配置的插件前缀，但装饰分割线不显示前缀，避免完整分割线折行。所有分割线统一使用 KitLoader 风格的 `✧`，旧分割线会安全迁移且不影响其它自定义文本。内置配置顶部采用精确的 103 列 ASCII 版式，品牌包含在多行符号拼凑图案中，不再添加独立的普通品牌行。

### 5. 自动清理逻辑

1. 插件安全迁移配置并加载当前语言消息。
2. `cleanup.enabled` 与 `recreate.enabled` 都为 false 时不创建自动任务。
3. 每个开启的模式分别创建异步固定周期计时器：普通清理按 `cleanup.interval` 与 `cleanup.interval_unit`（`minutes`、`hours` 或 `days`）换算周期；热恢复按 `recreate.interval` 与 `recreate.interval_unit`（`hours` 或 `days`）换算周期。
4. 到期后按模式广播 `warning` 或 `restore_warning`，并等待对应模式的倒计时。
5. 广播包含受影响世界列表的一条 `start_cleanup` 或 `start_restore` 消息，再通过有上限的异步区块加载队列提交任务。
6. 清理模式删除玩家保护半径之外的目标方块和非玩家实体；最多同时加载 16 个区块，并为每个区块设置有上限的 Region tick 写入批次，玩家一格区块范围内的区块使用更小批次以减少方块更新突发。恢复模式异步从 Anvil 文件读取方块数据，每次最多异步加载 32 个目标区块；目标世界没有玩家时跳过保护扫描区块，有玩家时每个 Region tick 只写入小批次方块，玩家保护半径内的方块仍会保留，玩家实体仍会保留。模板明确保存的空气会写入；模板缺失的 section 会保留目标方块；配置的模板过滤项会保留目标方块。
7. 所有任务完成后，只广播一条包含汇总统计、失败区块数与耗时的 `finish_cleanup` 或 `finish_restore`；忽略模板方块为零时，恢复消息会省略该统计行。

固定周期从插件启用或重载时开始计算，不是从上一次清理完成时开始计算。

### 6. 手动清理逻辑

`/war cleanup` 需要 `worldareareset.admin`，且始终执行普通区域清理。`/war recreate` 使用相同权限，且始终执行地形热恢复。两个指令分别使用各自模式的配置和倒计时，只重置对应模式的自动计时器：

1. 取消现有自动周期与尚未执行的旧倒计时。
2. 启动与自动清理完全相同的本地化倒计时。
3. 倒计时结束后运行相同清理引擎。
4. 对应自动模式开启时，从指令执行时重新计算一个完整周期。

重置时长跟随当前自动模式：普通清理使用 `cleanup.interval` 与 `cleanup.interval_unit`，热恢复使用 `recreate.interval` 与 `recreate.interval_unit`，并非固定写死 3 小时。

### 7. 配置重载逻辑

`/war reload` 会先对 `config.yml` 执行安全配置迁移；如果文件缺失，会从内置默认值恢复，再重载所选语言文件，取消周期计时器与尚未执行的倒计时，并根据新配置建立任务。重载迁移出错时会保持当前插件状态运行；启动阶段仍会阻止无效配置继续启动。更换 JAR 后仍必须正常重启服务器以加载新代码。已经开始执行的 Region Scheduler 清理不会被取消。

### 8. 自动更新逻辑

服务器启动时会请求官方 GitHub 最新 Release，随后下载单个 `WorldAreaReset-<最新版本>.jar` 到 Bukkit 更新目录；GitHub 提供 SHA-256 摘要时会进行校验。新版本在下一次服务器重启时生效。

每个 GitHub Release 只上传这一个 JAR。GitHub 自动生成且无法从 Release 页面移除的源码压缩包会单独显示，不属于项目额外上传附件。

强制文件名模板为 `WorldAreaReset-<版本>.jar`。CI 和 Release 自动化必须原样上传单个构建产物，禁止改名、改标签或加入其他 JAR。

设置 `updates.enabled: false` 可完全关闭网络检查；设置 `updates.auto_download: false` 可只提示而不下载。

### 9. 限制与风险

- 默认范围最多检查 20,744,529 个方块位置，覆盖约 676 个区块。
- cleanup 最多同时异步加载 16 个区块，并为每个区块设置每个 Region tick 的写入上限；玩家一格区块范围内的区块使用更小批次，减少方块更新突发。recreate 的区块加载为异步；目标世界没有玩家时最多同时加载 32 个目标区块，有玩家在线时每次只处理两个目标区块，每个 Region tick 只写入小批次方块，并在区块之间间隔一个 tick，以减少数据包突发和对玩家所在区域的争用。每个目标区块复用一次当前状态快照跳过无需写入的方块；大范围方块写入仍会消耗 region tick 时间。
- 当前没有“清理运行中”锁，手动与自动任务可能重叠。
- 不会自动纠正错误的 min/max 顺序。
- 无效 Material 会记录警告并忽略。
- 玩家实体不会被删除，也不会被传送到安全位置；恢复模式会跳过配置的玩家周围球形保护半径。
- 没有实体白名单，所有非玩家实体类别都可能被删除。
- 不识别 WorldGuard、领地、保护区或归属。
- 自动迁移会保留未知自定义键；格式 15 只移除已转换成世界模块的旧世界、边界与区域字段，格式 16 会补充缺失的恢复速度参数，格式 17 会补充模板方块过滤参数，格式 18 会转换旧的清理周期字段。
- 清理失败或只完成一部分时没有内置回滚。

正式启用前必须备份世界、预生成区块、使用小范围测试，并在低峰期执行大范围清理。
