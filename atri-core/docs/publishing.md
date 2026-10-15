# Maven Central 发布

[文档目录](README.md)

## 发布内容

| 项目 | 配置 |
| --- | --- |
| Group ID | `top.yzljc.atricore` |
| Artifact ID | `atri-core` |
| 开发版本 | `0.1.0-SNAPSHOT` |
| 版本参数 | `-PcoreVersion=<版本>` |
| 发布文件 | 主 JAR、源码 JAR、JavaDoc JAR、POM、Gradle 模块元数据及签名 |
| 许可证 | MIT，沿用仓库 LICENSE |

发布入口为仓库根目录的 `.github/workflows/publish-core.yml`，支持代码推送后自动发布和手动指定版本。运行服务与前端不在发布范围内

## 发布凭据

在 GitHub 仓库 Settings → Secrets and variables → Actions 中添加以下 Repository secrets

| Secret | 内容 |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central Portal 生成的 User Token Username |
| `MAVEN_CENTRAL_PASSWORD` | 同一 User Token 的 Password |
| `SIGNING_KEY` | ASCII 格式的 GPG 私钥，包含 BEGIN / END 行和全部换行 |
| `SIGNING_PASSWORD` | 该私钥的保护口令 |

Central Token 与网页登录密码不同，发布账号必须拥有 `top.yzljc.atricore` 命名空间。签名公钥须上传到 Central 支持的公钥服务器，例如 `keyserver.ubuntu.com`，以便平台校验产物签名

::: important 密钥保存
私钥、口令和 Central Token 均不写入仓库、文档或构建命令。GitHub Actions 在发布步骤通过环境变量注入凭据；本地备份保存在仓库之外，并限制文件访问权限
:::

## 自动发布

向 `AtriMeow` 分支推送以下文件的修改后，自动运行 core 测试、构建、签名并发布到 Maven Central

- `atri-core/src/` 中的源码、资源和测试
- core 构建脚本、Gradle 属性和依赖锁文件
- 根项目共享构建配置、Gradle Wrapper、许可证或发布工作流

仅修改 core 文档、运行服务业务代码、WebUI 或 miniapp 不触发发布

自动版本为 `0.1.<工作流运行编号>`，例如 `0.1.1`、`0.1.2`。运行编号由 GitHub Actions 递增，手动运行、失败或取消也会占用编号，因此版本号可能不连续。此规则不修改源码中的默认开发版本，也不自动回写 Git 提交

重新运行同一次工作流仍使用原版本号。若该版本已可从 Maven Central 下载，工作流直接跳过发布；若上次部署仍处于校验或分发阶段，先在 Central Portal 确认状态，避免重复上传

工作流摘要显示发布坐标和对应源码提交。正式版本分发完成后，其他项目更新依赖版本即可使用；当前主项目仍直接引用本地 core 源码

## 手动发布

1. 将代码和工作流提交到 `AtriMeow` 分支
2. 打开 Actions → Publish atri-core → Run workflow
3. 分支选择 `AtriMeow`，版本留空时自动编号，也可填写正式版本，例如 `0.1.0`
4. 运行工作流，在 Central Portal 的 Deployments 中查看发布状态

工作流先运行 core 测试，再构建、签名并上传。Central 校验通过后自动发布，分发完成后才可从 Maven Central 下载

::: note 首次添加工作流
手动运行入口要求工作流文件已存在于仓库默认分支。若默认分支不是 `AtriMeow`，也需将工作流入口加入默认分支，运行时仍选择 `AtriMeow`
:::

同一正式版本不能覆盖。手动指定版本时避免占用后续自动编号；如需切换到 `0.2.x` 或 `1.0.x` 系列，应同步调整工作流中的自动版本前缀与并发分组前缀。上传或校验失败时，先在 Central Portal 检查该次部署状态

## 本地开发

无需发布凭据，使用默认 SNAPSHOT 版本安装到本地 Maven 仓库

```sh
./gradlew :atri-core:publishToMavenLocal
```

主项目使用 `project(":atri-core")`，直接编译本地源码，不依赖 Maven Central，也不要求在修改 core 后重新发布

需要从本地执行正式发布时，通过环境变量提供以下 Gradle 属性，再运行发布任务

| 环境变量 | 对应内容 |
| --- | --- |
| `ORG_GRADLE_PROJECT_mavenCentralUsername` | Central Token Username |
| `ORG_GRADLE_PROJECT_mavenCentralPassword` | Central Token Password |
| `ORG_GRADLE_PROJECT_signingInMemoryKey` | 完整 ASCII 私钥 |
| `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` | 私钥口令 |

```sh
./gradlew :atri-core:test
./gradlew :atri-core:publishAndReleaseToMavenCentral -PcoreVersion=0.1.0 --no-daemon --no-configuration-cache
```

## 使用已发布版本

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("top.yzljc.atricore:atri-core:0.1.0")
}
```

将示例版本替换为已发布版本。使用方无需 Token、GPG 密钥或 GitHub 登录，JDK 与编译选项见 [快速开始](getting-started.md)

发布要求参考 [Central 签名文档](https://central.sonatype.org/publish/requirements/gpg/)，Gradle 任务和凭据配置参考 [Maven Publish 插件文档](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)
