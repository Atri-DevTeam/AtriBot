# 文档目录

[项目首页](../README.md)

## 部署与开发

| 文档 | 内容 |
| --- | --- |
| [部署](getting-started.md) | 环境、构建、首次运行与更新 |
| [配置](configuration.md) | 核心配置、平台凭据、运行数据 |
| [开发与构建](development.md) | 源码结构、前端开发、测试与文档维护 |

## 框架

| 文档 | 内容 |
| --- | --- |
| [架构与生命周期](framework/architecture.md) | 请求流转、模块职责、初始化与关闭 |
| [平台与消息](framework/platforms.md) | 平台接入、用户和消息模型、平台能力 |
| [QQ 频道帖子](framework/platforms.md#频道帖子) | 富文本构建与解析、发表、删除与返回值 |
| [事件](framework/events.md) | 注册、类型匹配、执行顺序、取消与频道帖子事件 |
| [指令](framework/commands.md) | 命令声明、执行器、两种参数模型 |
| [账号与权限](framework/accounts.md) | 平台身份、统一账号、权限与临时登录 |
| [插件开发](framework/plugins.md) | 插件结构、编译、依赖与资源释放 |
| [运行时服务](framework/runtime.md) | 调度、线程、存储与语言资源 |

## 接入与应用

| 文档 | 内容 |
| --- | --- |
| [WebUI 与 Miniapp](web.md) | 页面入口、构建产物、认证与权限边界 |
| [图片与业务接口](integrations/images.md) | OpenAPI 目录、请求结果、图片分发方式 |
| [AI 与文本审查](integrations/ai.md) | AI 配置、调用接口、文本替换规则 |
| [腾讯频道第二账号](integrations/tencent-channel.md) | CLI 配置、Java 调用与管理入口 |
| [功能索引](features.md) | 业务模块与源码入口 |

框架阅读顺序：架构与生命周期 → 平台与消息 → 事件 → 指令 → 账号与权限。开发独立插件时，继续阅读插件开发与运行时服务
