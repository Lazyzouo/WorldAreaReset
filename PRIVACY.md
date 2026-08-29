# Open Source & Privacy Statement / 开源与隐私声明

## English

### Open-source assurance

WorldAreaReset is released in full under the MIT License. Its public repository contains the source code used to build official releases, together with the build configuration, release workflow, GitHub asset digests, and documentation. Administrators and independent reviewers may audit the code and build the plugin themselves.

WorldAreaReset contains no backdoor, telemetry, analytics, advertising, remote administration, remote command execution, hidden data collection, account system, webhook reporting, or developer-operated backend service.

### Server data

The plugin does not transmit or upload server configuration, world or region contents, player information, entity data, logs, credentials, addresses, plugin lists, performance metrics, or any other server content.

Files created or managed by the plugin remain on the administrator's server. These files are limited to:

- `plugins/WorldAreaReset/config.yml`;
- localized files under `plugins/WorldAreaReset/lang/`;
- a downloaded replacement JAR in Bukkit's configured update directory when automatic downloading is enabled.

Terrain and entity changes are performed directly in the configured Minecraft world and are not copied to any remote service.

### Network access

The only runtime network feature is the optional updater. When `updates.enabled: true`, the plugin sends an outbound HTTPS `GET` request to:

```text
https://api.github.com/repos/Lazyzouo/WorldAreaReset/releases/latest
```

If a newer version exists and `updates.auto_download: true`, it downloads the matching `WorldAreaReset-<version>.jar` release asset and verifies the GitHub-provided SHA-256 digest when available.

No server content is included in these requests. However, GitHub necessarily receives standard network metadata associated with any HTTPS connection, including the server's public IP address, request time, and the plugin User-Agent. GitHub processes that metadata under GitHub's own privacy and logging policies; it is not collected by the WorldAreaReset author.

Set the following to disable all runtime network access by WorldAreaReset:

```yaml
updates:
  enabled: false
```

### Verification

Official releases are created by the public GitHub Actions workflow from the corresponding public commit and tag. GitHub exposes a SHA-256 digest for uploaded assets. Administrators who require independent assurance should review the tagged source, inspect the workflow, build the JAR locally, and compare behavior before production deployment.

---

## 中文

### 开源保证

WorldAreaReset 以 MIT License 完整开源。公开仓库包含用于构建官方版本的源代码、构建配置、发布工作流、校验信息和说明文档，服务器管理员及任何独立审查者都可以审计代码并自行构建插件。

WorldAreaReset 不包含后门、遥测、统计分析、广告、远程管理、远程指令执行、隐藏数据收集、账户系统、Webhook 回传或由开发者运营的后台服务。

### 服务器数据

插件不会传输或上传服务器配置、世界或区域内容、玩家资料、实体数据、日志、凭据、地址、插件列表、性能指标或任何其他服务器内容。

插件创建或管理的文件只会保存在管理员自己的服务器上，范围仅包括：

- `plugins/WorldAreaReset/config.yml`；
- `plugins/WorldAreaReset/lang/` 下的本地化文件；
- 开启自动下载时，写入 Bukkit 所配置更新目录的替换 JAR。

地形和实体变更只会直接发生在已配置的 Minecraft 世界中，不会复制到任何远程服务。

### 联网行为

插件运行时唯一的联网功能是可选更新器。`updates.enabled: true` 时，插件会向以下地址发起出站 HTTPS `GET` 请求：

```text
https://api.github.com/repos/Lazyzouo/WorldAreaReset/releases/latest
```

若存在新版本且 `updates.auto_download: true`，插件会从官方 GitHub Release 下载对应的 `WorldAreaReset-<version>.jar` 资源，并在 GitHub 提供 SHA-256 摘要时进行校验。

这些请求不会包含任何服务器内容。但是，与所有 HTTPS 连接相同，GitHub 必然会接收服务器公网 IP、请求时间和插件 User-Agent 等标准网络元数据。相关元数据由 GitHub 按其自身隐私及日志政策处理，并非由 WorldAreaReset 作者收集。

设置以下参数可完全关闭 WorldAreaReset 的运行时网络访问：

```yaml
updates:
  enabled: false
```

### 自主验证

官方版本由公开的 GitHub Actions 工作流根据对应公开提交和标签构建，GitHub 会为上传资源提供 SHA-256 摘要。需要独立保证的管理员应审查标签源代码和发布工作流，在本地自行构建 JAR，并在生产部署前验证其行为。
