# Changelog

All notable changes are documented here. English is listed before Chinese for every release.

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

## [1.14.0] - 2026-08-30

### English

- Flatten the active language into one `messages` section so Chinese and English configurations never render two language copies.
- Remove generated `lang/` files and the retired `formatting`, `inline-replacements`, and `gradient-colors` settings during format 23 migration.
- Apply the fixed KitLoader five-color gradient, bold formatting, and `✧` divider to bundled messages.
- Format cleanup and restoration countdowns in the configured minutes, hours, or days unit.
- Render real author/version values and use `/war reload` in configuration guidance.
- Publish one `WorldAreaReset-1.14.0.jar` containing both configuration templates; no language-suffixed JARs are built.

### 中文

- 将当前语言消息扁平写入单一 `messages` 区域，中文和英文配置不会再同时显示两份消息。
- 格式 23 迁移时删除自动生成的 `lang/` 文件及旧的 `formatting`、`inline-replacements`、`gradient-colors` 设置。
- 所有默认消息统一使用 KitLoader 五色渐变、粗体格式和 `✧` 分割线。
- 清理与热恢复倒计时按配置的分钟、小时或天显示，不再固定显示分钟。
- 配置头显示真实作者与版本，并使用 `/war reload` 热重载提示。
- 仅发布一个 `WorldAreaReset-1.14.0.jar`，内置英文与中文配置模板，不构建带语言后缀的 JAR。
