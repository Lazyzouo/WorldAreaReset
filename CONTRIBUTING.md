# Contributing / 贡献指南

## English

Thank you for improving WorldAreaReset.

1. Open an issue for behavioral changes before starting a large implementation.
2. Fork the repository and create a focused branch.
3. Keep Paper/Folia thread ownership in mind for every Bukkit API call.
4. Update both language files when adding administrator-facing text.
5. Update official defaults and `WorldAreaReset.md` when configuration changes.
6. Add an English entry before the matching Chinese entry in `CHANGELOG.md`.
7. Build with Java 21 using `./gradlew clean build` and validate runtime behavior on Paper/Folia 1.21.11.
8. Do not claim compatibility with another server version without testing and documenting it.
9. Submit a pull request using the repository template.

Do not include server configs, worlds, logs, credentials, player data, or unrelated generated files.

## 中文

感谢你帮助改进 WorldAreaReset。

1. 大型行为变更请先创建 Issue 讨论。
2. Fork 仓库并创建范围明确的分支。
3. 所有 Bukkit API 调用都必须考虑 Paper/Folia 线程归属。
4. 新增管理员可见文本时同时更新两个语言文件。
5. 配置变化时同步更新官方默认配置与 `WorldAreaReset.md`。
6. 在 `CHANGELOG.md` 中先写英文，再写对应中文。
7. 使用 Java 21 执行 `./gradlew clean build`，并在 Paper/Folia 1.21.11 上验证运行行为。
8. 未经测试和记录，不得宣称兼容其他服务端版本。
9. 按仓库模板提交 Pull Request。

不得提交服务器配置、世界、日志、凭据、玩家数据或无关生成文件。
