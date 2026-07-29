# Changelog

All notable changes are documented here. English is listed before Chinese for every release.

## [1.2.0] - 2026-07-29

### English

- Added the plugin version to both official package names: `WorldAreaReset-1.2.0-en.us.jar` and `WorldAreaReset-1.2.0-zh.cn.jar`.
- Updated the automatic updater to select `WorldAreaReset-<latest-version>-<language>.jar` dynamically while retaining safe next-restart installation behavior.
- Reworked the startup console banner with a consistent gold plugin prefix, bilingual metadata, tested-platform information, project URL, privacy statement, and localized success line.
- Added status colors to update checking, current-version, available-version, manual-download, downloaded, disabled, and failure console notices.
- Strengthened CI, Release automation, and repository rules so the two versioned build outputs must be uploaded under their original filenames without aliases or extra JARs.
- Kept all cleanup parameters, official defaults, configuration comments, and cleanup behavior unchanged.

### 中文

- 为两个官方语言包加入插件版本号：`WorldAreaReset-1.2.0-en.us.jar` 与 `WorldAreaReset-1.2.0-zh.cn.jar`。
- 自动更新器改为动态选择 `WorldAreaReset-<最新版本>-<语言>.jar`，并保留下一次重启时安全安装的逻辑。
- 重新设计服务器后台启动横幅，统一使用金色插件前缀，并显示双语元数据、测试平台、项目地址、隐私声明和本地化启动成功提示。
- 为检查更新、已是最新版、发现新版、手动下载、下载完成、更新关闭和更新失败通知加入对应状态颜色。
- 强化 CI、Release 自动化和仓库维护规则，两个版本化构建产物必须保持原名上传，禁止别名、改名或额外 JAR。
- 所有清理参数、官方默认值、配置注释及清理行为保持不变。

## [1.1.5] - 2026-07-29

### English

- Replaced the single generic release JAR with `WorldAreaReset-en.us.jar` and `WorldAreaReset-zh.cn.jar`.
- Bundled complete, commented official English or Simplified Chinese defaults without changing plugin behavior or removing source configuration content.
- Changed automatic updates to select the release JAR matching the active `language` setting.
- Limited project-uploaded GitHub Release assets to the two language JARs; standalone configuration, documentation, privacy, and checksum attachments are no longer uploaded.
- Added build and release checks that compare each packaged `config.yml` byte-for-byte with its official source configuration.

### 中文

- 将原有单一通用 Release JAR 改为 `WorldAreaReset-en.us.jar` 与 `WorldAreaReset-zh.cn.jar`。
- 分别内置保留完整注释的官方英文或简体中文默认配置，不改变插件行为，也不删除任何源配置内容。
- 自动更新改为根据当前 `language` 设置选择对应 Release JAR。
- 项目上传至 GitHub Release 的附件仅保留两个语言 JAR，不再上传独立配置、说明、隐私声明或校验文件。
- 新增构建与发布校验，逐字节比较包内 `config.yml` 和对应官方源配置。

## [1.1.4] - 2026-07-29

### English

- Refined the official project positioning around scheduled terrain maintenance for administrator-defined free-for-all PvP zones.
- Explained that scheduled and manual cleanup remove non-allowlisted blocks and non-player entities within the configured cuboid.
- Clarified that WorldAreaReset does not create regions, enable PvP, or manage combat permissions.
- Updated the plugin metadata and GitHub project description while keeping cleanup behavior and configuration unchanged.

### 中文

- 将官方项目定位完善为面向管理员指定自由 PvP 区域的定时地形维护插件。
- 说明自动与手动清理会移除配置长方体范围内的非白名单方块和非玩家实体。
- 明确 WorldAreaReset 不负责创建区域、开启 PvP 或管理战斗权限。
- 同步更新插件元数据与 GitHub 项目介绍，清理逻辑和配置保持不变。

## [1.1.3] - 2026-07-29

### English

- Added a prominent English-first bilingual open-source and privacy statement to the top of the project README and administrator guide.
- Added `PRIVACY.md` with an auditable description of local data handling, updater network requests, third-party connection metadata, and the opt-out setting.
- Declared that the plugin contains no backdoor, telemetry, analytics, remote administration, hidden collection, or server-data upload functionality.
- Added the privacy statement to the public release documentation.

### 中文

- 在项目 README 与管理员手册顶部新增英文优先的双语开源与隐私声明。
- 新增 `PRIVACY.md`，完整说明本地数据处理、更新器联网请求、第三方连接元数据以及关闭联网的方法。
- 明确插件不含后门、遥测、统计分析、远程管理、隐藏收集或服务器资料上传功能。
- 将隐私声明加入公开发布文档。

## [1.1.2] - 2026-07-29

### English

- Added complete inline documentation for every official configuration parameter.
- Documented defaults, accepted values, units, boundary rules, dependencies, and operational risks.
- Kept all parameter values, message text, notification styling, and plugin behavior unchanged.

### 中文

- 为官方配置中的每个参数补充完整行内注释。
- 说明默认值、可选值、单位、坐标规则、参数依赖及运行风险。
- 所有参数值、消息文本、通知样式与插件逻辑均保持不变。

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
