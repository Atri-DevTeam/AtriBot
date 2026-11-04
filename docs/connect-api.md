# Connect API

服务间查询入口为 `/connect`，响应沿用项目 `Result` 格式，`code = 0` 表示成功。接口独立于 WebUI 开关，凭证签发调试入口仍受 WebUI 开关、管理员身份及写请求来源校验保护

## 配置与鉴权

```yaml
connect:
  secret: "填写至少 32 字节的随机密钥"
```

密钥留空或不足 32 字节时，Connect 接口返回 HTTP 503。运行服务与调用方后端保存同一密钥；WebUI 只接收生成的短期 ticket，不接收密钥

所有查询携带 `ticket` 参数，有效期为 300 秒。ticket 可在有效期内重复使用，也可用于其他 Connect 查询。更换配置密钥并重载配置后，旧 ticket 立即失效

签名规则：

```text
timestamp = 当前 Unix 时间（秒）
payload = "v1." + timestamp
message = "connect\n" + payload
signature = Base64URL(HMAC-SHA256(secret, message))，去除末尾 =
ticket = payload + "." + signature
```

密钥与待签名内容均使用 UTF-8，`\n` 表示一个换行字符。该凭证使用签名验证身份，不加密请求或响应正文，外部调用使用 HTTPS。调用方时间不得晚于服务端，服务端时间与签发时间的差值必须小于 300 秒

调用示例（Python 标准库）：

```python
import base64
import hashlib
import hmac
import json
import os
import time
from urllib.parse import urlencode
from urllib.request import urlopen

payload = f"v1.{int(time.time())}"
signature = hmac.new(
    os.environ["CONNECT_SECRET"].encode(),
    f"connect\n{payload}".encode(),
    hashlib.sha256,
).digest()
ticket = payload + "." + base64.urlsafe_b64encode(signature).decode().rstrip("=")
query = urlencode({"b_id": "主项目机器人用户OpenID", "ticket": ticket})
with urlopen("https://机器人接口域名/connect/user/background?" + query, timeout=15) as response:
    result = json.load(response)
```

WebUI「API接口」页自动填写 ticket，可直接发送请求、复制完整地址或 cURL。页面不持久化 ticket；自动生成的凭证过期后，在下次发送前重新获取。手动输入的 ticket 原样使用

## 用户标识

| 参数 | 含义 |
| --- | --- |
| `b_id` | 主项目机器人用户 OpenID，对应统一账号的 `qq_user_open_id` |
| `c_id` | 本项目 QQ 互联应用下的用户 OpenID，对应 `qq_connect_open_id` |
| `u_id` | 统一账号 UUID |

三者必须且只能提供一个，不接受重复参数、空值或额外参数。身份查询不创建账号，未匹配到统一账号时返回 HTTP 404

## 获取用户背景图信息

```http
GET /connect/user/background?b_id=USER_OPEN_ID&ticket=TICKET
```

也可将 `b_id` 替换为 `c_id` 或 `u_id`

响应示例：

```json
{
  "code": 0,
  "message": "请求成功",
  "data": {
    "u_id": "1f9e31cb-7a04-4356-b887-d250658405c6",
    "b_id": "USER_OPEN_ID",
    "background": {
      "owner": "用户OpenID的SHA256小写十六进制值",
      "id": "已审核背景的UUID"
    },
    "submission": {
      "id": "最近投稿的UUID",
      "status": "PENDING",
      "width": 1920,
      "height": 1080,
      "size": 204800,
      "createdAt": 1793376000000,
      "reviewedAt": 0
    }
  },
  "timestamp": 1793376001000,
  "request_id": null
}
```

| 字段 | 说明 |
| --- | --- |
| `background` | 当前生效、已审核的背景引用，可直接赋给 UGC 生图请求的 `background` 字段；无生效背景时为 `null` |
| `background.owner` | UGC 背景存储使用的所有者标识 |
| `background.id` | 当前生效背景 UUID |
| `submission` | 最近一次投稿信息，没有投稿时为 `null` |
| `submission.id` | 投稿 UUID，可能与当前生效背景不同 |
| `submission.status` | `PENDING` 待审核、`REVIEWED` 已通过、`DENIED` 未通过 |
| `submission.width` / `height` | 最近投稿的图片尺寸，单位像素 |
| `submission.size` | 最近投稿文件大小，单位字节 |
| `submission.createdAt` / `reviewedAt` | 提交及审核时间，Unix 毫秒，尚未审核时 `reviewedAt` 为 0 |

新投稿待审核或未通过时，原来的已审核背景仍可能生效。渲染使用 `background`，不使用 `submission.id`。接口仅查询本地数据，不请求或下载 UGC 图片，也不返回投稿群、审核人和审核备注

## 错误响应

| HTTP 状态 | 业务码 | 含义 |
| --- | --- | --- |
| 400 | 10001 | 用户标识缺失、冲突、格式错误或存在重复、额外参数 |
| 401 | 10002 | ticket 缺失、格式错误、签名无效或已过期 |
| 404 | 80014 | 用户不存在 |
| 500 | 90001 | 本地数据读取失败 |
| 503 | 90002 | Connect 密钥未配置或长度不足 |

失败时 `data` 为 `null`。用户存在但未设置背景属于成功响应，不返回 404
