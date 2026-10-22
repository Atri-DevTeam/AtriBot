# 站点授权

[文档目录](../README.md) · [响应格式与业务码](api-response.md)

本站可作为已登记站点的 QQ 登录入口。来源站发起签名请求，用户在本站完成 QQ 登录后返回来源站，再由来源站后端兑换用户信息

此接口使用本站的认证交接协议，不提供完整的 OAuth 或 OpenID Connect 服务。QQ 互联的 AppKey、访问令牌及本站登录 Cookie 不向接入方传递

## 配置

将下列配置合并至运行目录的 `config.yml`。`dashboard` 与 `miniapp` 均须启用，并完成 QQ 互联配置

```yaml
dashboard:
  authorization:
    enabled: true
    sites:
      main:
        secret: "替换为至少 32 字节的随机密钥"
        redirect-uris:
          - "https://www.example.com/auth/callback"
        patch-url: "https://www.example.com/api/auth/account"
      minecraft:
        secret: "替换为另一份至少 32 字节的随机密钥"
        redirect-uris:
          - "https://mc.example.com/auth/callback"
        patch-url: "https://mc.example.com/api/auth/account"
```

每个站点使用独立密钥，只保存在两端服务器。返回地址逐项精确匹配，生产环境使用 HTTPS，不允许用户信息、查询参数或片段；本地测试允许 `http://localhost` 和 `http://127.0.0.1`

`patch-url` 是接收账号状态变更的服务端接口，使用相同的地址校验规则。未配置时允许登录，但不向该站点投递变更

::: important 密钥配置
示例文本不能作为实际密钥。可使用 `node -e "console.log(require('node:crypto').randomBytes(32).toString('base64url'))"` 生成随机密钥

密钥不得写入前端代码或提交到公开仓库。签名载荷没有加密，浏览器可读取其中的返回地址、state 和有效期
:::

配置错误时停用站点授权并记录原因，不影响其他业务配置的加载。服务重启、接入方移除、密钥轮换或返回地址变更后，未完成的授权及兑换凭证失效

## 签名规则

所有 JSON 先编码为 UTF-8，再转换为不带 `=` 填充的 Base64URL，结果称为 `payload`

待签名文本由以下四行构成，使用换行符 `\n` 连接，末尾不添加换行：

```text
atri-site-auth-v1
用途
client_id
payload
```

使用站点密钥计算 HMAC-SHA256，将结果编码为无填充 Base64URL，得到 `signature`

| 用途 | 值 |
| --- | --- |
| 发起授权 | `authorize` |
| 兑换授权码 | `exchange` |
| 用户信息响应 | `result` |
| 账号变更 PATCH | `account_patch` |

同一套算法和站点密钥用于以上用途，用途字段用于隔离不同类型的签名。验签时使用收到的原始 `payload`，不要先解析 JSON 再重新序列化

## 发起授权

```http
GET /auth/authorize?client_id=main&payload=...&signature=...
```

入口域名使用 `dashboard.base-url`，与本站登录 Cookie 所在域名一致。不要使用独立的 QQ 回调域名发起请求

`payload` 解码后：

```json
{
  "redirect_uri": "https://www.example.com/auth/callback",
  "state": "来源站为当前浏览器生成的随机标识",
  "expires_at": 1792541100,
  "nonce": "6ac9513c61994e36af7f8126f1e55d53",
  "code_challenge": "7w_YNF9DSfIdPf_pRjSq646_kPr-2-o9NAl16JGghdM"
}
```

| 字段 | 约束 |
| --- | --- |
| `client_id` | 配置中的站点标识，1～64 位字母、数字、下划线或连字符 |
| `redirect_uri` | 最长 2048 字符，与配置中的完整地址精确匹配 |
| `state` | 非空，最长 256 字符，不允许控制字符，回跳及兑换结果中原样返回 |
| `expires_at` | Unix 秒时间戳，必须晚于服务器当前时间且不超过当前时间 300 秒 |
| `nonce` | 22～128 位字母、数字、下划线或连字符，同一站点同一用途只允许使用一次 |
| `code_challenge` | `Base64URL(SHA-256(code_verifier))`，固定 43 位，只支持 S256 |

来源站后端生成并保留 `code_verifier`，格式为 43～128 位字母、数字或 `-._~`。该值只在后端兑换时提交，发起授权的浏览器链接只携带 `code_challenge`

重复 JSON 字段、未知字段、重复的外层查询参数和超长载荷均不接受

验签通过后先进入本站 miniapp 的统一账号登录页，用户点击“继续认证”后才继续处理：本站已有有效 QQ 登录会话时返回来源站，否则跳转至 QQ 互联，登录完成后再返回来源站。此流程不会进入个人面板或管理工作台

“继续认证”下方的“切换账号”会先清除本站当前 QQ 登录会话，再重新进入 QQ 授权页，原来源站授权请求继续保留

页面交接凭证绑定当前浏览器的 HttpOnly Cookie，只能使用一次；继续认证时重新生成 QQ OAuth state。来源站仍使用原来的签名请求和授权码兑换接口，无需修改接入方式。整个交接请求最长保留 10 分钟。本站不根据该请求自动创建统一账号或修改机器人账号绑定

## 返回来源站

成功时以 HTTP 302 跳转：

```text
https://www.example.com/auth/callback?state=原标识&code=一次性授权码
```

授权码由 `SecureRandom` 生成，具有 256 位随机性，有效期 60 秒，只能兑换一次。授权码绑定来源站、完整返回地址、QQ 应用及 PKCE 校验值

::: important 浏览器请求关联
来源站必须将 `state` 绑定到最初发起登录的浏览器会话，并在接收回跳时校验。不能仅凭地址栏中出现 `code` 就信任该回跳
:::

如果收到 QQ 取消或失败回调，则返回 `state` 和 `error`，不签发授权码：

```text
https://www.example.com/auth/callback?state=原标识&error=LOGIN_CANCELLED
```

可能的返回原因包括 `LOGIN_CANCELLED`、`LOGIN_FAILED`、`ACCOUNT_NOT_REGISTERED`、`ACCOUNT_RESTRICTED` 和 `AUTHORIZATION_BUSY`。`ACCOUNT_NOT_REGISTERED` 表示尚未注册或绑定机器人账号，接入方应提示用户私聊机器人发送 `/dash` 完成注册绑定后再登录。用户直接关闭授权页面时不保证存在回调；来源请求已经失效或来源配置变更时，不再跳转到来源地址

## 兑换用户信息

来源站后端请求：

```http
POST /auth/exchange
Content-Type: application/json
```

```json
{
  "client_id": "main",
  "payload": "兑换请求JSON的Base64URL",
  "signature": "exchange用途的签名"
}
```

`payload` 解码后：

```json
{
  "code": "回跳收到的一次性授权码",
  "redirect_uri": "https://www.example.com/auth/callback",
  "code_verifier": "vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv",
  "expires_at": 1792541100,
  "nonce": "86b3cb311a42406693bc45b91e57d53f"
}
```

`redirect_uri` 必须与发起授权时完全一致。`expires_at`、`nonce` 的格式与发起请求相同，签名用途改为 `exchange`

成功返回：

```json
{
  "code": 0,
  "message": "请求成功",
  "data": {
    "client_id": "main",
    "request_id": "本站生成的授权请求标识",
    "state": "来源站原始state",
    "issued_at": 1792540800,
    "expires_at": 1792540860,
    "user": {
      "qq_connect_open_id": "QQ互联OpenID",
      "avatar_url": "https://q.qlogo.cn/example/0",
      "account_id": "c04c7fb7-c881-4b44-aed1-e6cff560fb78",
      "username": "示例用户",
      "user_open_id": "官机用户OpenID",
      "role": "USER",
      "permissions": [],
      "status": "ACTIVE",
      "account_version": 1792540800000
    }
  },
  "timestamp": 1792540800000,
  "request_id": "本站生成的授权请求标识"
}
```

| 用户字段 | 说明 |
| --- | --- |
| `qq_connect_open_id` | 经 QQ 服务端确认的用户 OpenID |
| `avatar_url` | QQ 头像地址，获取失败时为 null |
| `account_id` | 已绑定的统一账号 UUID |
| `username` | 统一账号的用户名，未设置时为 null |
| `user_open_id` | 统一账号关联的官机用户 OpenID |
| `role` | 统一账号权限组，取 USER、ADMIN 或 OWNER |
| `permissions` | 已保存的权限节点，未配置时为空数组 |
| `status` | 统一账号状态，仅 ACTIVE 状态可以兑换成功 |
| `account_version` | 账号状态版本，与 PATCH 共用，接入方仅应用较新的状态 |

授权完成和兑换时均检查机器人账号及账号状态。未注册、未绑定或受限账号不能完成认证，也不返回用户信息

响应头 `X-Auth-Signature` 是 `result` 用途的签名。验签输入的 `payload` 为**完整响应正文原始 UTF-8 字节**的无填充 Base64URL，不是单独的 `data` 字段。来源站先验签，再校验业务码、client_id、state 和有效期

权限组和权限节点按统一账号数据返回，不在接口中展开或替接入方匹配权限。响应不签发本站管理凭证，也不共享本站会话或 QQ 访问令牌；接入方自行维护业务会话

## 账号变更广播

通过统一账号业务入口更新权限组、权限节点、账号状态、用户名或绑定信息后，本站向所有配置了 `patch-url` 的站点异步广播完整状态。数据库提交及账号缓存更新完成后只在内存入队，网络投递和文件保存均在独立工作线程执行

```http
PATCH /api/auth/account
Content-Type: application/json; charset=utf-8
X-Auth-Signature: account_patch用途的签名
```

```json
{
  "client_id": "main",
  "event": "account.changed",
  "event_id": "336e194f-53c3-47b4-9c48-73eab2f1faf1",
  "issued_at": 1792540800,
  "expires_at": 1792541100,
  "user": {
    "qq_connect_open_id": "QQ互联OpenID",
    "account_id": "统一账号UUID",
    "username": "用户名",
    "user_open_id": "机器人用户OpenID",
    "role": "USER",
    "permissions": ["group.moderation"],
    "status": "SUSPENDED",
    "account_version": 1792540800001
  }
}
```

签名输入中的 `payload` 为整个 PATCH 原始正文的无填充 Base64URL，签名用途使用 `account_patch`，密钥为该站点配置的同一份密钥

接收方校验签名、client_id 和 expires_at，按 `qq_connect_open_id` 定位后端用户，再比较 `account_version`。仅应用大于本地版本的状态；重复的 event_id 或较旧版本直接确认接收，不覆盖新状态

::: important 状态替换
`permissions` 是完整列表，应整体替换，不能只追加。`role`、`status` 和绑定字段同样以新版本为准，null 表示清空

账号解绑后，原 QQ 身份的绑定字段和权限清空；账号删除后，status 为 DELETED，role 清空、permissions 为空。接收方据此更新自己的后端会话，本站不直接操作接入方会话
:::

HTTP 2xx 表示已接收；其他状态或网络异常会重试。投递不跟随 HTTP 重定向。每次尝试最多等待 5 秒，失败后按 2、4、8 秒逐步退避，最长间隔 5 分钟。重试保留 event_id 和账号版本，重新生成有效期及签名

广播串行执行，最多每 500 毫秒启动一次投递。同一站点同一身份的待发送状态合并为最新版本，阻塞中的旧投递不会清除后来入队的新状态。PATCH 不携带头像字段，不覆盖登录时获得的头像

未送达状态及版本保存在运行目录 `data/site-authorization/account-updates.json`，不新增数据库表，不保存站点密钥。正常关闭时保存队列，重启后重新核对当前账号，并补发最新状态。最多暂存 8192 个身份和 32768 个投递项，达到上限时记录错误并拒绝新增状态

暂存文件无法读取、超过 64 MiB 或广播初始化失败时，保留文件并停用站点授权交接和广播，不中断机器人启动及本地个人中心登录。修复文件后重启恢复

这是异步同步机制，变更到达前子站仍可能使用上一版本。子站离线期间无法立即刷新；文件保存失败会记录日志，应保留并检查暂存文件。不要将异步广播视为实时强一致校验

## Node.js 服务端示例

以下代码只展示来源站后端的签名和兑换方法。`state` 与 `verifier` 应由来源站保存在对应浏览器的服务端会话中

```js
import {createHash, createHmac, randomBytes, timingSafeEqual} from 'node:crypto'

const baseUrl = 'https://bot.example.com'
const clientId = 'main'
const secret = process.env.BOT_SITE_AUTH_SECRET
const redirectUri = 'https://www.example.com/auth/callback'
const b64 = value => Buffer.from(value, 'utf8').toString('base64url')
const random = () => randomBytes(32).toString('base64url')

function signature(purpose, payload) {
  return createHmac('sha256', secret)
    .update(`atri-site-auth-v1\n${purpose}\n${clientId}\n${payload}`, 'utf8')
    .digest('base64url')
}

function envelope(purpose, data) {
  const payload = b64(JSON.stringify(data))
  return {client_id: clientId, payload, signature: signature(purpose, payload)}
}

export function createAuthorization() {
  const state = random()
  const verifier = random()
  const request = envelope('authorize', {
    redirect_uri: redirectUri,
    state,
    expires_at: Math.floor(Date.now() / 1000) + 300,
    nonce: random(),
    code_challenge: createHash('sha256').update(verifier, 'ascii').digest('base64url')
  })
  return {state, verifier, url: `${baseUrl}/auth/authorize?${new URLSearchParams(request)}`}
}

export async function exchange(code, verifier, expectedState) {
  const response = await fetch(`${baseUrl}/auth/exchange`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(envelope('exchange', {
      code,
      redirect_uri: redirectUri,
      code_verifier: verifier,
      expires_at: Math.floor(Date.now() / 1000) + 60,
      nonce: random()
    })),
    redirect: 'error',
    signal: AbortSignal.timeout(10_000)
  })
  const body = await response.text()
  if (!response.ok) throw new Error('授权码兑换失败')
  const received = response.headers.get('X-Auth-Signature') || ''
  if (!/^[A-Za-z0-9_-]{43}$/.test(received)
      || !timingSafeEqual(Buffer.from(received), Buffer.from(signature('result', b64(body))))) {
    throw new Error('授权结果签名无效')
  }
  const result = JSON.parse(body)
  if (result.code !== 0 || result.data.client_id !== clientId
      || result.data.state !== expectedState || result.data.expires_at <= Date.now() / 1000) {
    throw new Error('授权结果无效或已过期')
  }
  return result.data.user
}
```

## 错误码

接口错误遵循[统一响应格式](api-response.md)，`data` 为 null。业务码与 HTTP 状态分别表示业务结果和传输状态

| code | 名称 | 含义 |
| --- | --- | --- |
| 80001 | AUTHORIZATION_DISABLED | 站点授权未开放 |
| 80002 | AUTHORIZATION_INVALID_CLIENT | 来源站点不存在或格式无效 |
| 80003 | AUTHORIZATION_INVALID_SIGNATURE | 请求签名不匹配 |
| 80004 | AUTHORIZATION_INVALID_REQUEST | 请求结构、字段或返回地址无效 |
| 80005 | AUTHORIZATION_REQUEST_EXPIRED | 签名请求过期或有效期超过上限 |
| 80006 | AUTHORIZATION_REQUEST_REPLAYED | 同一请求 nonce 已使用 |
| 80007 | AUTHORIZATION_BUSY | 暂存请求达到容量限制 |
| 80008 | AUTHORIZATION_EXPIRED | 交接请求过期或接入配置已变更 |
| 80009 | AUTHORIZATION_INVALID_IDENTITY | QQ 身份格式无效 |
| 80010 | AUTHORIZATION_INVALID_GRANT | 授权码不存在、过期、已兑换，或来源、返回地址、PKCE 不匹配 |
| 80011 | ACCOUNT_RESTRICTED | 当前统一账号受限 |
| 80012 | AUTHORIZATION_FAILED | 授权结果读取失败 |
| 80013 | ACCOUNT_NOT_REGISTERED | 尚未注册或绑定机器人账号，需私聊机器人发送 /dash 完成注册绑定后再登录 |

请求体类型错误使用 `10007`，请求体超过 16 KiB 使用 `10009`。收到兑换失败后，不应通过重复请求同一授权码判断登录状态；需要重新发起认证
