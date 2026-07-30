# WorldAreaReset Administrator Guide / 管理员配置与逻辑说明

> [!IMPORTANT]
> WorldAreaReset is fully open source and contains no telemetry, remote administration, hidden data collection, or backdoor functionality. Server content is not uploaded; plugin-created files remain local. The only optional runtime network access is the official GitHub Release update check/download. / WorldAreaReset 完全开源，不含遥测、远程管理、隐藏数据收集或后门功能，不会上传服务器内容，插件创建的文件仅保存在本机；唯一可选联网行为是官方 GitHub Release 更新检查与下载。详见 [PRIVACY.md](PRIVACY.md)。

> Applies to / 适用于：`WorldAreaReset 1.2.5`<br>
> Project / 项目地址：https://github.com/Lazyzouo/WorldAreaReset

## English

### 1. Compatibility limits

- Tested and supported server: Paper/Folia 1.21.11 only.
- Required runtime: Java 21 or newer.
- Other Minecraft versions, Spigot, CraftBukkit, Purpur, and hybrid/modded servers are untested and are not guaranteed to work.
- The `api-version: 1.21` value in `plugin.yml` is Bukkit API metadata; it does not mean every 1.21.x server is supported.

Always include the exact server implementation and build when reporting a problem.

### 2. Purpose and runtime model

WorldAreaReset provides scheduled terrain maintenance for administrator-defined free-for-all PvP zones. It cleans an inclusive cuboid in one loaded world: every non-air block not listed in `cleanup.keep_blocks` is changed to air without physics, and every non-player entity inside the cuboid is removed.

It does not create regions, enable PvP, manage combat permissions, regenerate terrain, restore a schematic, respect claims, or keep an automatic backup. Configure the same boundaries separately in the server's region or PvP-management system.

### 3. Configuration reference

| Path | Default | Meaning |
| --- | --- | --- |
| `config_version` | `3` | Public configuration format marker |
| `language` | Package-specific | `en_US` in `WorldAreaReset-1.2.5-en.us.jar`; `zh_CN` in `WorldAreaReset-1.2.5-zh.cn.jar` |
| `cleanup.enabled` | `false` | Enables automatic scheduling; manual cleanup remains available when false |
| `cleanup.interval_minutes` | `180` | Fixed automatic schedule interval in minutes |
| `cleanup.countdown_seconds` | `10` | Shared warning delay for automatic and manual cleanup |
| `cleanup.world` | `world_nether` | Exact name of an already loaded world |
| `cleanup.min_x/max_x` | `-200/200` | Inclusive X boundaries |
| `cleanup.min_y/max_y` | `0/128` | Inclusive Y boundaries |
| `cleanup.min_z/max_z` | `-200/200` | Inclusive Z boundaries |
| `cleanup.keep_blocks` | `BEDROCK`, `BARRIER` | Bukkit Material names preserved throughout the area |
| `updates.enabled` | `true` | Checks the official latest release at startup |
| `updates.auto_download` | `true` | Downloads a newer JAR to Bukkit's update directory |
| `updates.notify_latest` | `true` | Reports when the installed version is current |

Official defaults are stored in `src/main/resources/config-en_US.yml`, `src/main/resources/config.yml`, and `defaults/`. Both release JARs contain identical code and preserve the complete comments from the corresponding source configuration; only the bundled official defaults differ. Live server configuration belongs only in `plugins/WorldAreaReset/config.yml` and is excluded from the repository.

### 4. Message and language logic

Bundled language files are copied to:

```text
plugins/WorldAreaReset/lang/en_US.yml
plugins/WorldAreaReset/lang/zh_CN.yml
```

The official configurations restore editable command feedback, broadcasts, help menus, and updater notices under `messages.en_US.*` or `messages.zh_CN.*`. The namespace matching `language` overrides the bundled language file. Existing flat `messages.*` values from older configurations still take priority for backward compatibility.

Only server-specific settings such as world, boundaries, interval, allowlist, language, and updater switches use sanitized official presets. Message text and other non-server-specific presentation settings remain complete in the public templates.

Supported placeholders include `{name}`, `{version}`, `{author}`, `{interval}`, `{countdown}`, `{world}`, `{time}`, `{blocks}`, `{entities}`, `{current}`, `{reason}`, and `{url}` where relevant.

The startup banner and all updater notices use the same configured gold `WorldAreaReset` prefix. Banner fields use separate colors for labels and values; updater states use cyan for checking, green for current/downloaded, yellow for available/manual download, red for failure, and gray for disabled.

The startup banner has a 64-column interior. Its identity section contains the centered service name/version and the centered bilingual `PVP TERRAIN MAINTENANCE / PVP 地形维护` subtitle, followed by a full-width dashed separator before runtime metadata. Every title, metadata, project URL, and privacy row is enclosed by matching left and right borders. Padding ignores legacy color codes and counts Chinese characters as double-width terminal columns, keeping the right border aligned with space after the longest field.

Every player-facing plugin message is forced bold and left aligned at runtime. Leading whitespace is removed independently from every line without rewriting configuration, while leading legacy color/style codes and hex colors are preserved; bold styling is reapplied after color and reset codes. Help menus, command feedback, countdown warnings, cleanup broadcasts, and completion messages all use this alignment. In prefixed multiline messages, content rows retain the configured plugin prefix while decorative divider rows omit it so the full divider does not wrap. Divider detection ignores formatting codes and recognizes the bundled divider glyphs plus common ASCII and box-drawing separators.

### 5. Automatic cleanup sequence

1. The plugin loads configuration and language files.
2. If `cleanup.enabled` is false, no recurring cleanup is scheduled.
3. If enabled, a fixed-rate asynchronous timer waits `cleanup.interval_minutes`.
4. The plugin broadcasts `warning` and waits `cleanup.countdown_seconds`.
5. It broadcasts `start_cleanup` and submits one Folia Region Scheduler task per covered chunk.
6. Each task clears blocks and removes non-player entities in that chunk.
7. After all tasks report completion, `finish_cleanup` is broadcast with totals and elapsed time.

The fixed interval starts when the plugin is enabled or reloaded, not when a cleanup finishes.

### 6. Manual cleanup sequence

`/war cleanup` requires `worldareareset.admin`:

1. Cancel the existing recurring timer and any pending countdown.
2. Start the same localized countdown used by automatic cleanup.
3. Run the same cleanup engine after the delay.
4. If automatic cleanup is enabled, start a fresh interval from command execution time.

The reset duration is the configured `cleanup.interval_minutes`; it is not hardcoded to three hours.

### 7. Reload sequence

`/war reload` reloads `config.yml`, reloads the selected language file, cancels the recurring timer and pending countdown, then creates a new timer if enabled. Region tasks that have already begun are not cancelled.

### 8. Update sequence

At startup the updater requests:

```text
https://api.github.com/repos/Lazyzouo/WorldAreaReset/releases/latest
```

Version tags are compared numerically. The updater selects `WorldAreaReset-<latest-version>-en.us.jar` for `language: en_US` and `WorldAreaReset-<latest-version>-zh.cn.jar` otherwise, then downloads it to Bukkit's update folder under the currently running JAR filename. GitHub's SHA-256 digest is checked when present. The new JAR takes effect on the following restart.

Each GitHub Release has exactly these two uploaded JAR assets. GitHub's automatically generated source archives remain visible separately and cannot be removed from the Release page.

The required filename template is `WorldAreaReset-<version>-<language>.jar`, concretely `en.us` and `zh.cn`. CI and Release automation upload the exact build outputs without renaming, relabeling, or adding another JAR.

Disable the network check with `updates.enabled: false`, or keep notifications without downloading by setting `updates.auto_download: false`.

### 9. Limits and operational risks

- The default cuboid checks up to 20,744,529 block positions across approximately 676 chunks.
- Chunk access can be synchronous. Large or unloaded areas can trigger Folia Watchdog warnings and region lag.
- There is no running-cleanup lock; overlapping manual/automatic executions are possible.
- Invalid min/max ordering is not automatically corrected.
- Invalid Material names are logged and ignored.
- Players are preserved but not moved to safety.
- All non-player entity categories are removed; no entity allowlist exists.
- WorldGuard, claims, protected regions, and ownership are not consulted.
- Configuration updates are not merged into an existing server config automatically.
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

WorldAreaReset 用于对管理员指定的自由 PvP 区域进行定时地形维护。插件会清理一个已加载世界内、包含边界的长方体区域：所有不在 `cleanup.keep_blocks` 白名单中的非空气方块会在不触发物理更新的情况下改为空气；所有位于范围内的非玩家实体会被删除。

插件不会创建区域、开启 PvP、管理战斗权限、重新生成种子地形、恢复 schematic、识别领地或自动备份。请在服务器的区域或 PvP 管理系统中另行配置相同边界。

### 3. 配置说明

| 路径 | 默认值 | 说明 |
| --- | --- | --- |
| `config_version` | `3` | 公开配置格式版本 |
| `language` | 按语言包决定 | `WorldAreaReset-1.2.5-en.us.jar` 为 `en_US`；`WorldAreaReset-1.2.5-zh.cn.jar` 为 `zh_CN` |
| `cleanup.enabled` | `false` | 是否启用自动排程；关闭时仍可手动清理 |
| `cleanup.interval_minutes` | `180` | 自动清理固定周期，单位分钟 |
| `cleanup.countdown_seconds` | `10` | 自动与手动清理共用倒计时 |
| `cleanup.world` | `world_nether` | 已加载世界的准确名称 |
| `cleanup.min_x/max_x` | `-200/200` | 包含边界的 X 范围 |
| `cleanup.min_y/max_y` | `0/128` | 包含边界的 Y 范围 |
| `cleanup.min_z/max_z` | `-200/200` | 包含边界的 Z 范围 |
| `cleanup.keep_blocks` | `BEDROCK`, `BARRIER` | 整个区域内保留的 Bukkit Material |
| `updates.enabled` | `true` | 启动时检查官方最新 Release |
| `updates.auto_download` | `true` | 将新版 JAR 下载到 Bukkit 更新目录 |
| `updates.notify_latest` | `true` | 当前已是最新版时在后台提示 |

官方默认配置位于 `src/main/resources/config-en_US.yml`、`src/main/resources/config.yml` 与 `defaults/`。两个 Release JAR 的插件代码完全相同，并原样保留对应源配置的全部注释；差异仅限内置的官方默认配置。服务器实际配置只应位于 `plugins/WorldAreaReset/config.yml`，该运行目录已排除在仓库之外。

### 4. 消息与语言逻辑

内置语言文件会释放到：

```text
plugins/WorldAreaReset/lang/en_US.yml
plugins/WorldAreaReset/lang/zh_CN.yml
```

官方配置已在 `messages.en_US.*` 或 `messages.zh_CN.*` 中恢复可编辑的指令反馈、广播、帮助菜单和更新提示。与 `language` 相同的命名空间会覆盖内置语言文件。旧配置中的扁平 `messages.*` 仍具有最高优先级，以保持向后兼容。

只有世界、坐标范围、周期、白名单、语言和更新开关等服务器专用参数使用脱敏后的官方预设；消息文本及其他与服务器参数无关的显示内容会完整保留在公开模板中。

根据消息场景可使用 `{name}`、`{version}`、`{author}`、`{interval}`、`{countdown}`、`{world}`、`{time}`、`{blocks}`、`{entities}`、`{current}`、`{reason}` 和 `{url}` 等变量。

启动横幅和全部更新器通知共用配置中的金色 `WorldAreaReset` 前缀。横幅标签与值分别着色；更新状态依次使用青色（检查中）、绿色（最新版/下载完成）、黄色（发现更新/手动下载）、红色（失败）和灰色（已关闭）。

启动横幅内部宽度为 64 个终端列。身份标题区包含居中的服务名称与版本，以及居中的双语副标题 `PVP TERRAIN MAINTENANCE / PVP 地形维护`，随后使用整行虚线将标题区与运行信息分隔。标题、元数据、项目地址和隐私声明各行均由同色左右边框完整包围。留白计算会忽略旧式颜色码，并将中文字符按双列终端宽度计算，使右侧边框保持对齐，并与最长字段保留空隙。

所有插件游戏内文本都会在运行时强制显示为粗体并统一左对齐。系统会逐行移除行首空白而不改写配置，同时保留行首旧式颜色/样式码与十六进制颜色码；颜色码和重置码之后仍会重新应用粗体。帮助菜单、指令反馈、清理倒计时、清理广播和完成消息都使用该对齐规则。带前缀的多行消息会在正文行保留已配置的插件前缀，但装饰分割线不显示前缀，避免完整分割线折行。分割线识别会忽略格式码，并支持内置分割字符以及常见 ASCII 与框线字符。

### 5. 自动清理逻辑

1. 插件读取配置与语言文件。
2. `cleanup.enabled` 为 false 时不创建自动任务。
3. 开启后，异步固定周期计时器等待 `cleanup.interval_minutes`。
4. 到期后广播 `warning`，并等待 `cleanup.countdown_seconds`。
5. 广播 `start_cleanup`，为范围内每个区块提交 Folia Region Scheduler 任务。
6. 每个任务清除该区块内的目标方块和非玩家实体。
7. 所有任务完成后，广播包含统计与耗时的 `finish_cleanup`。

固定周期从插件启用或重载时开始计算，不是从上一次清理完成时开始计算。

### 6. 手动清理逻辑

`/war cleanup` 需要 `worldareareset.admin`：

1. 取消现有自动周期与尚未执行的旧倒计时。
2. 启动与自动清理完全相同的本地化倒计时。
3. 倒计时结束后运行相同清理引擎。
4. 自动清理开启时，从指令执行时重新计算一个完整周期。

重置时长取自 `cleanup.interval_minutes`，并非固定写死 3 小时。

### 7. 配置重载逻辑

`/war reload` 会重载 `config.yml` 和所选语言文件，取消周期计时器与尚未执行的倒计时，再根据新配置建立任务。已经开始执行的 Region Scheduler 清理不会被取消。

### 8. 自动更新逻辑

服务器启动时会请求官方 GitHub 最新 Release。`language: en_US` 会选择 `WorldAreaReset-<最新版本>-en.us.jar`，其他情况会选择 `WorldAreaReset-<最新版本>-zh.cn.jar`，随后下载到 Bukkit 更新目录；GitHub 提供 SHA-256 摘要时会进行校验。新版本在下一次服务器重启时生效。

每个 GitHub Release 只上传这两个 JAR。GitHub 自动生成且无法从 Release 页面移除的源码压缩包会单独显示，不属于项目额外上传附件。

强制文件名模板为 `WorldAreaReset-<版本>-<语言>.jar`，语言后缀仅为 `en.us` 与 `zh.cn`。CI 和 Release 自动化必须原样上传构建产物，禁止改名、改标签或加入其他 JAR。

设置 `updates.enabled: false` 可完全关闭网络检查；设置 `updates.auto_download: false` 可只提示而不下载。

### 9. 限制与风险

- 默认范围最多检查 20,744,529 个方块位置，覆盖约 676 个区块。
- 区块访问可能同步执行，大范围或未加载区域可能触发 Folia Watchdog 和 region 卡顿。
- 当前没有“清理运行中”锁，手动与自动任务可能重叠。
- 不会自动纠正错误的 min/max 顺序。
- 无效 Material 会记录警告并忽略。
- 玩家不会被删除，但也不会被传送到安全位置。
- 没有实体白名单，所有非玩家实体类别都可能被删除。
- 不识别 WorldGuard、领地、保护区或归属。
- 已有服务器配置不会自动合并新版默认项。
- 清理失败或只完成一部分时没有内置回滚。

正式启用前必须备份世界、预生成区块、使用小范围测试，并在低峰期执行大范围清理。
