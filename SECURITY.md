# Security Policy / 安全政策

For data handling, network access, and the no-telemetry/no-backdoor declaration, read [PRIVACY.md](PRIVACY.md). / 有关数据处理、联网行为及无遥测/无后门声明，请阅读 [PRIVACY.md](PRIVACY.md)。

## English

Security fixes are provided for the latest release line. Do not disclose exploitable issues in a public Issue.

The supported security-testing environment is Java 21 or newer on Paper/Folia 1.21.11. Reports from other Minecraft versions or server implementations may require reproduction on the supported environment.

Report a vulnerability through GitHub's private vulnerability reporting feature on this repository. Include the affected version, server implementation, reproduction steps, impact, and any suggested mitigation. Never include production credentials or private player data.

The built-in updater only contacts the official `Lazyzouo/WorldAreaReset` GitHub Releases API. It downloads the matching `WorldAreaReset-<version>.jar` asset and verifies GitHub's SHA-256 digest when available. Administrators may disable all checks with `updates.enabled: false`.

## 中文

安全修复面向最新发布分支。请勿在公开 Issue 中披露可被利用的漏洞。

安全测试支持环境为 Java 21 或更高版本以及 Paper/Folia 1.21.11。来自其他 Minecraft 版本或服务端实现的问题可能需要先在受支持环境复现。

请通过本仓库的 GitHub 私密漏洞报告功能提交，包含受影响版本、服务端实现、复现步骤、影响及建议缓解方式。不要提交生产环境凭据或玩家隐私数据。

内置更新器只访问官方 `Lazyzouo/WorldAreaReset` GitHub Releases API，只下载对应的 `WorldAreaReset-<version>.jar` 资源，并在 GitHub 提供 SHA-256 摘要时进行校验。管理员可设置 `updates.enabled: false` 完全关闭检查。
