# Changelog

All notable changes are documented here. English is listed before Chinese for every release.

## [1.0.0] - 2026-09-05

### English

- Reissue the first public release as `WorldAreaReset-1.0.0.jar` with the English `config.yml` as the default and the Chinese template bundled in the same JAR.
- Start the public configuration schema at `config-version: 1`; future schema changes will increment this marker.
- Preserve safe configuration migration, atomic backups, verified SHA-256 updates, and the 50 MiB download limit.

### Compatibility

- Message, color, prefix, separator, GUI, and severity conventions remain compatible with KitLoader. KitLoader is optional and is not a runtime dependency.

### 中文

- 重新发布首个公开版本 `WorldAreaReset-1.0.0.jar`，默认使用英文 `config.yml`，并在同一 JAR 内附带中文模板。
- 将公开配置结构从 `config-version: 1` 开始；后续配置结构变化会递增该标记。
- 保留安全配置迁移、原子备份、SHA-256 校验更新以及 50 MiB 下载上限。

### 兼容性

- 消息、颜色、前缀、分割线、GUI 和严重级别约定与 KitLoader 兼容；KitLoader 不是运行依赖。
