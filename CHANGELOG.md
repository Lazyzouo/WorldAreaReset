# Changelog

All notable changes are documented here. English is listed before Chinese for every release.

## [1.14.6] - 2026-08-30

### English

- Use visible legacy fallback colors for console panels that do not render Hex section codes; the light-pink cleanup warning is now rendered as `§d` instead of white.
- Keep the complete Hex palette for player-facing messages.

### 中文

- 针对不渲染 Hex 分节码的后台面板使用可见的 Legacy 回退颜色，浅粉色清理警告现在显示为 `§d`，不再变成白色。
- 玩家端消息继续保留完整 Hex 调色板。

## [1.14.5] - 2026-08-30

### English

- Preserve the full KitLoader Hex palette when serializing console and server-panel output, including the light-pink cleanup warning color.
- Add a regression test so future console serialization changes cannot quantize Hex colors to white or another legacy color.

### 中文

- 后台和服务器面板输出保留完整的 KitLoader Hex 调色板，包括浅粉色的清理警告，不再被降级为白色。
- 增加回归测试，防止后续控制台序列化再次把 Hex 颜色量化为白色或其它旧版颜色。
