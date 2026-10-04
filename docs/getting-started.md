# 部署

[文档目录](README.md)

## 环境

| 项目 | 要求 |
| --- | --- |
| Java | JDK 25，运行时传入 `--enable-preview` |
| 构建工具 | 仓库内的 Gradle Wrapper |
| 数据库 | MySQL，提前创建数据库并配置账号 |
| 前端构建 | Node.js 24、npm，仅构建前端时需要 |
| 平台接入 | 至少配置一个平台的凭据和连接方式 |

## 构建产物

在仓库根目录执行：

```sh
npm --prefix webui ci
npm --prefix webui run build
npm --prefix miniapp ci
npm --prefix miniapp run build
./gradlew build
```

Windows 将 `./gradlew` 替换为 `./gradlew.bat`。只修改后端时，可直接运行 Gradle

`build/libs/` 包含两类 JAR：

| 文件 | 用途 |
| --- | --- |
| `AtriMeow-<version>.jar` | 包含运行依赖的部署包 |
| `AtriMeow-<version>-plain.jar` | 不包含运行依赖，供插件编译引用 |

版本号由 [build.gradle.kts](../build.gradle.kts) 定义

## 首次运行

1. 创建固定运行目录，将部署包放入该目录
2. 将 [config.yml 模板](../src/main/resources/config.yml) 复制到运行目录的 `config.yml`
3. 填写 `mysql`、平台配置和需要使用的外部服务地址
4. 在运行目录启动程序

```sh
java --enable-preview -jar AtriMeow-3.3.0-SNAPSHOT.jar
```

程序也会在缺少配置文件时复制内置模板，但模板中的数据库和平台凭据需要自行填写。配置与数据路径均相对于进程工作目录

数据库表由各 Repository 在启动时初始化；程序账号需要相应的建表、查询、写入权限，升级时可能需要修改表结构

## 网络入口

Javalin 监听 `listen-port`，默认 `1234`。平台回调、WebUI 和 Miniapp 共用该服务

| 路径 | 用途 |
| --- | --- |
| `POST /` | Napcat 事件上报 |
| `/qq/webhook` | QQ Webhook，路径可配置 |
| `/kook/webhook` | KOOK Webhook，路径可配置 |
| `/webui/` | 管理后台 |
| `/atrimeow/profile/` | Miniapp |

使用反向代理时保留路径与请求体，转发平台回调所需的请求头。外部访问地址与本地监听地址分别配置，Miniapp 的外部地址填写在 `miniapp.base-url`

WebUI 在生产配置下需先开启，操作方式见 [WebUI 与 Miniapp](web.md)。平台接入步骤见 [平台与消息](framework/platforms.md)

## 更新与备份

停止进程后替换部署包，保留原运行目录。更新前备份：

- MySQL 数据库
- `config.yml`、`groupconfig.json`、`request.json`、`filter.yml` 等运行配置
- `data/`、业务历史文件和缓存中需要保留的数据
- `plugins/` 中的插件及插件数据

通过退出流程关闭程序，使插件、任务和平台连接完成清理。配置字段与初始化逻辑以当前版本源码为准，发布包中的模板不会覆盖已有 `config.yml`

## 常见启动问题

| 现象 | 检查位置 |
| --- | --- |
| 预览特性或 class 版本错误 | JDK 是否为 25，是否传入 `--enable-preview` |
| 数据库初始化失败 | `mysql` 配置、库是否存在、账号权限 |
| WebUI 返回空响应或 503 | WebUI 是否已开启 |
| 静态页面缺失或仍为旧版 | 是否先构建对应前端，再打包后端 |
| 平台未连接 | `enabled`、凭据、连接方式及平台初始化日志 |
| 某条启动日志后长时间无输出 | 后续初始化调用与线程栈；最后一条成功日志不一定是阻塞位置 |
