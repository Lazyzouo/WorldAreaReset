# Changelog

All notable changes are documented here. English is listed before Chinese for every release.

## [1.14.7] - 2026-08-30

### English

- Restore terrain at the configured `recreate.blocks_per_tick` budget even when players are online; player protection remains local to nearby blocks.
- Remove the extra one-tick delay between completed chunks and allow four online restore workers, preventing repeat restores from degrading to one slow chunk at a time.
- Use the fast restore path for chunks without nearby protected player blocks, even when another player is elsewhere in the target world.

### 中文

- 即使有玩家在线，地形恢复也使用配置的 `recreate.blocks_per_tick` 批次；玩家保护仍只作用于附近方块。
- 移除已完成区块之间额外的一 tick 延迟，并允许四个在线恢复工作单元，避免再次恢复退化为逐区块慢速执行。
- 目标世界其它位置有玩家时，没有附近玩家保护方块的区块仍使用快速恢复路径。

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
