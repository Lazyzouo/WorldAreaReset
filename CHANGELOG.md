# Changelog

All notable changes are documented here. English is listed before Chinese for every release.

## [1.14.5] - 2026-08-30

### English

- Preserve the full KitLoader Hex palette when serializing console and server-panel output, including the light-pink cleanup warning color.
- Add a regression test so future console serialization changes cannot quantize Hex colors to white or another legacy color.

### 中文

- 后台和服务器面板输出保留完整的 KitLoader Hex 调色板，包括浅粉色的清理警告，不再被降级为白色。
- 增加回归测试，防止后续控制台序列化再次把 Hex 颜色量化为白色或其它旧版颜色。

## [1.14.4] - 2026-08-30

### English

- Serialize console messages through the same Legacy section-code path as KitLoader so every fixed severity color is rendered by server panels.

### 中文

- 后台消息改用与 KitLoader 相同的 Legacy 分节颜色码序列化路径，确保服务器面板能渲染所有固定 severity 颜色。

## [1.14.3] - 2026-08-30

### English

- Separate cleanup and terrain-restoration broadcasts between players and the server console.
- Keep configured KitLoader gradients for players while rendering console business messages as fixed single-color severity notices.
- Prefix every non-divider console line with the fixed KitLoader-style WorldAreaReset prefix.

### 中文

- 分离清理与地形热恢复的玩家广播和服务器后台输出。
- 玩家继续使用配置中的 KitLoader 渐变，后台业务消息改为固定单色 severity 提示。
- 后台每个非分割线消息行都使用固定 KitLoader 风格的 WorldAreaReset 前缀。

## [1.14.2] - 2026-08-30

### English

- Match KitLoader's console prefix colors for WorldAreaReset plugin information and important messages.
- Strip configured gradients and legacy color tags from console message bodies before applying fixed severity colors.

### 中文

- 让 WorldAreaReset 后台插件信息和重要消息使用与 KitLoader 一致的前缀颜色。
- 在应用固定 severity 颜色前清理配置渐变和旧式颜色标签，避免后台消息继续显示渐变色。

## [1.14.1] - 2026-08-30

### English

- Color the `/war cleanup` help command with the KitLoader palette and split plugin metadata into name, author, and version rows.
- Keep cleanup and restoration schedule rows visible when disabled, showing `not scheduled` until enabled.
- Show live cleanup and restoration time remaining down to seconds using the configured maximum unit.
- Render updater and startup console notices with the documented single severity colors instead of gradients.
- Migrate format 23 configurations to the new help metadata layout as format 24.

### 中文

- 使用 KitLoader 调色板为帮助菜单中的 `/war cleanup` 指令上色，并将插件名称、作者和版本拆分为三行。
- 定时清理或热恢复关闭时仍显示对应状态行，未启用时显示“未排程”。
- 按配置最大单位显示清理与热恢复剩余时间，并精确到秒。
- 更新器和启动后台提示改用文档规定的单色状态色，不再使用渐变色。
- 以格式 24 迁移格式 23 配置中的帮助菜单元数据布局。
