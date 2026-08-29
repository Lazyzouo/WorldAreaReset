# Changelog

All notable changes are documented here. English is listed before Chinese for every release.

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
