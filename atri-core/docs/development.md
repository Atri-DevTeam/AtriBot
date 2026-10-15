# 构建与开发

[文档目录](../README.md)

## 构建

使用 JDK 25，在仓库根目录执行以下命令，Windows 使用 `gradlew.bat`

```sh
./gradlew :atri-core:build
./gradlew :atri-core:javadoc
./gradlew :atri-core:publishToMavenLocal
```

| 产物 | 路径 |
| --- | --- |
| JAR | `atri-core/build/libs/atri-core-<版本>.jar` |
| 源码 JAR | `atri-core/build/libs/atri-core-<版本>-sources.jar` |
| JavaDoc JAR | `atri-core/build/libs/atri-core-<版本>-javadoc.jar` |
| API 文档 | `atri-core/build/docs/javadoc/index.html` |
| 测试报告 | `atri-core/build/reports/tests/test/index.html` |

core 使用独立版本，默认 `0.1.0-SNAPSHOT`，通过 `-PcoreVersion=<版本>` 指定，不修改运行服务的版本。正式发布见 [Maven Central 发布](publishing.md)

## 源码联调

同一 Gradle 工程通过 `project(":atri-core")` 引用时，构建会直接编译模块源码

外部工程通过 Maven 坐标引用时，修改后重新执行 `publishToMavenLocal`，包名或方法签名变更后需重新编译运行服务及扩展插件

## 测试

```sh
./gradlew :atri-core:test
```

测试使用本地模拟 HTTP 服务，工作目录为 `atri-core/build/test-runtime`，不连接实际机器人平台或业务数据库

## API 文档

`javadoc` 任务先通过 `delombok` 展开 Lombok 类型，再生成 API 文档，生成文件位于 `build/generated/sources/delombok`，原始源码不变

文档使用 UTF-8，校验结构、引用和 HTML，自定义类头标签沿用项目规范

## 注释规范

类头格式

```java
/**
 * 账号与权限查询服务
 *
 * @Author YZ_Ljc_
 * @ClassName AccessService
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.core
 */
```

方法使用标准 JavaDoc，说明参数含义、返回值及必要约束

```java
/**
 * 根据 QQ 用户 OpenID 查询关联账号
 *
 * @param openId QQ 用户 OpenID，频道事件未提供时可为 null
 * @return 关联账号，未找到时返回 null
 */
```

继承方法复用父类文档，普通访问器和直接转发方法不重复说明，内部注释仅保留协议差异、并发约束等必要信息

## 网站文档同步

网站仅发布 core 的 README 和 docs，目标为 `Atri-DevTeam/Atri-Website-Content` 仓库的 main 分支，更新范围为 `亚托莉喵/`

| 文件 | 用途 |
| --- | --- |
| [同步 Action](../.github/actions/sync-docs/action.yml) | 测试、导出和提交步骤 |
| [导出脚本](../.github/scripts/sync_docs.py) | 文件同步、链接改写及旧文档清理 |
| [中文映射](../.github/docs-map.json) | 源文档与网站路径的对应关系 |
| [同步测试](../.github/scripts/test_sync_docs.py) | 导出、路径边界和迁移验证 |

core README 导出为 `介绍.md`，文档使用中文分类名称，目标分类目录最多一级。新增、删除或重命名文档时同步更新映射，映射缺失会使导出失败

### 触发与鉴权

当前 core 位于主仓库子目录，仓库根目录的 `.github/workflows/sync-core-docs.yml` 提供 Actions 触发入口，执行步骤由 core 内的 Action 管理

工作流在 AtriMeow 分支的 core 文档或同步配置变更后运行，也可在 Actions 页面手动运行 Sync Core Documentation，分支选择 AtriMeow。主项目 README 和 docs 的修改不会触发此流程

在运行工作流的仓库中添加 Actions Secret `DOCS_SYNC_TOKEN`，沿用已有配置即可。Token 仅授权目标内容仓库，Contents 权限为 Read and write

### 导出行为

文档间的相对链接按中文映射改写，源码和图片链接指向对应提交，并包含当前模块的 atri-core 路径前缀。提示容器保留原样，容器内的文档链接仍会改写

::: important 已发布文档的迁移
沿用目标目录的 `.atrimeow-docs.json` 清单。首次切换为 core 文档时，会移除旧清单中不再导出的主项目文档；未列入清单的手工文档与目标目录外的内容保留。与新导出文档同名的文件由 core 内容覆盖
:::

无变更时不提交，不强制推送。目标分支拒绝推送时，处理分支规则或并发更新后重新运行

### 本地验证

在 core 目录执行，测试仅使用临时目录，不访问目标仓库

```sh
python3 -B -m unittest discover -s .github/scripts -p 'test_sync_docs.py' -v
```

同步 Action 的 source-root 指向 core 检出目录，source-prefix 为 core 相对源仓库的路径。当前分别为 source/atri-core 和 atri-core；迁入独立仓库后 source-root 使用 source，source-prefix 留空，目标仓库检出到并列的 website 目录
