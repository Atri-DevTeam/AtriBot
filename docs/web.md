# WebUI 与 Miniapp

[文档目录](README.md) · [开发与构建](development.md)

## 入口与职责

| 项目 | WebUI | Miniapp |
| --- | --- | --- |
| 面向对象 | 机器人维护者 | 机器人用户与已绑定群的群主 |
| 页面入口 | `/webui/` | `/atrimeow/profile/` |
| API 前缀 | `/webui/api` | `/atrimeow/profile/api` |
| 前端目录 | `webui/` | `miniapp/` |
| 后端入口 | `WebUIRouter` | `MiniappRouter` |
| 认证 | 管理密钥验证后签发 Cookie 会话 | 入口票据交换内存 Bearer Token |

两套认证独立。页面隐藏控件不能代替后端授权

## WebUI

在 `qq.official-webui-token` 配置登录密钥。生产配置默认不自动开启 WebUI，机器人管理员可通过 `/webui` 操作开关；控制台执行 `webui` 切换状态。`env: dev` 在启动时自动开启

登录流程使用挑战值与证明值验证密钥，成功后签发会话 Cookie。会话固定有效期为 24 小时。关闭或重新开启 WebUI 会清除原认证状态并断开旧连接

主要页面包括聊天与群配置、指令面板、用户和权限、发送及事件记录、频道管理。群管理和加群欢迎从聊天侧栏进入

公开查询接口位于 `/webui/api/public/official`，使用独立频控，豁免后台开关与登录检查。具体范围以 [WebUIRouter](../src/main/java/top/yzljc/atribot/webui/WebUIRouter.java) 中注册的路由为准

## Miniapp

```yaml
miniapp:
  enabled: true
  base-url: "https://bot.example.com"
```

`base-url` 可填写外部根地址或完整页面地址，外部地址要求 HTTPS，本机调试允许 localhost / 127.0.0.1 使用 HTTP。留空时不签发页面入口。用户在 QQ 官方 C2C 中执行 `/profile` 获取绑定本人身份的链接

入口票据有效期 5 分钟。GET 页面只加载外壳，POST 交换票据后取得会话，交换绑定 IP 与 User-Agent。Bearer Token 仅保存在前端内存，不写入 localStorage 或 Cookie

会话最长 2 小时，空闲 90 秒失效，前端通过心跳维持活动。进程重启后票据与会话全部失效。服务端从会话取得身份，不接受请求中的用户 ID 作为授权依据

### 群管理

群配置以已完成的群主绑定为归属依据。显示和访问群管区域均要求 `group.moderation`，自定义 AI 审查词的编辑还要求 `atri.custom_prompt`

共有配置包括关键词、审查模式、URL 正则白名单、提醒开关及生效时间。WebUI 专有高级设置在 Miniapp 只读，提交时由后端保护原值

关键词保存后仅显示首字，其余字符遮盖；URL 正则保留首尾字符，其余等长遮盖。已有内容通过规则 ID 操作，展示文本不能作为原始规则回写。URL 默认全放行使用 `.*`

Miniapp 新增规则使用固定提醒词；WebUI 已配置的提醒内容和自定义输出作为只读字段保留。Miniapp 不提供自定义输出编辑，具体规则见 [MiniappModerationService](../src/main/java/top/yzljc/atribot/miniapp/service/MiniappModerationService.java)

## 构建与部署

```sh
npm --prefix webui ci
npm --prefix webui run build
npm --prefix miniapp ci
npm --prefix miniapp run build
./gradlew build
```

构建输出分别进入 `src/main/resources/official-webui` 和 `src/main/resources/miniapp`，随 JAR 提供静态资源。修改 Vite 的部署路径时，必须同时核对后端路由与反向代理路径

源码入口：[WebUI](../webui/src)、[Miniapp](../miniapp/src)、[WebUISessionManager](../src/main/java/top/yzljc/atribot/webui/WebUISessionManager.java)、[MiniappRouter](../src/main/java/top/yzljc/atribot/miniapp/MiniappRouter.java)、[MiniappSessions](../src/main/java/top/yzljc/atribot/miniapp/MiniappSessions.java)
