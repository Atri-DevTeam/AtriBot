# Markdown 图片素材

[API 目录](../../README.md) · [图片与文件](media.md)

`MaterialImages` 管理当前机器人的 Markdown 图片素材。上传完成后保存 `imageId` 和 `url`，后续发送使用 `qqbot-media://image/v1/...` 引用

## 上传与引用

```java
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.media.MaterialImages;
import java.nio.file.Path;

var image = MaterialImages.upload(Path.of("images/banner.webp"));
String reference = image.url();
var message = TC.md(Markdown.materialImage(reference, 800, 400));
```

已有图片字节时使用 `MaterialImages.upload(bytes, "banner.webp")`。上传方法计算 MD5、SHA1，依次申请上传、PUT 二进制和确认上传，不发送消息

`Markdown.materialImage(reference, width, height)` 接受完整素材引用或引用中的 token，返回 Markdown 图片语法。宽高以像素为单位，必须大于 0。已有的 `Markdown.img(url, width, height)` 同样可以接收完整素材引用

::: important 配额与有效期
单张图片最大 10MB，支持 PNG、JPEG、GIF 和 WebP，文件扩展名必须与内容格式一致。每个机器人最多保存 100 张有效素材

每次消息成功引用素材后，有效期顺延至 90 天；连续 90 天未被引用则失效。素材引用与机器人绑定，不能用于其他机器人
:::

## 查询与删除

```java
var page = MaterialImages.list(null);
for (var image : page.images()) {
    String id = image.imageId();
    String reference = image.url();
    long expiresAt = image.expiresAt();
}
if (!page.nextCursor().isEmpty()) {
    var next = MaterialImages.list(page.nextCursor());
}

boolean deleted = MaterialImages.delete(imageId);
```

`list` 按上传时间倒序返回当前页，`total` 为当前机器人的有效素材总数，`nextCursor` 为空字符串时表示末页。上传和过期时间均为 Unix 秒

删除成功返回 true，同时释放配额。删除后不能再被新消息引用，已发送消息中的图片不受影响

::: note 预览地址
`previewUrl` 是平台提供的临时预览地址，仅用于管理界面预览。地址过期后重新查询列表，不将它作为长期消息引用保存
:::

## 分步上传

| 方法 | 参数与结果 |
| --- | --- |
| `prepare(fileName, fileSize, contentType, md5, sha1)` | 返回 `UploadTask(uploadId, presignedUrl, expiresIn)` |
| `complete(uploadId, md5)` | 返回 `UploadedImage(imageId, url, ttl, previewUrl)` |

分步上传时，调用方自行向 `presignedUrl` 执行 HTTP PUT。请求体为原始图片字节，`Content-Type` 必须与申请时完全一致，不向预签名地址附加机器人 Authorization 请求头

PUT 成功后，使用响应 ETag 去掉双引号的值调用 `complete`。上传任务有效期为 4 小时，上传地址的有效期以 `expiresIn` 为准

## 异常处理

方法同步执行网络请求，应放在允许等待网络的业务线程中调用

| 异常 | 含义 |
| --- | --- |
| `IllegalArgumentException` | 图片大小、格式、文件名或参数不合法 |
| `MaterialImages.ApiException` | QQ 平台拒绝请求，通过 `code()` 和 `httpStatus()` 读取平台错误 |
| `IOException` | 文件读取、上传传输或响应校验失败 |
| `InterruptedException` | 请求被中断，调用方应恢复中断标记并停止后续操作 |

常见平台错误：`40035007` 为素材数量超限，`40035008` 为图片不存在，`40035009` 为引用已过期或已删除，`40035010` 为引用无效或不属于当前机器人

上传或删除请求超时后，可先重新查询列表确认平台状态，避免立即重复操作

[QQ 官方素材管理文档](https://bot.q.qq.com/wiki/develop/api-v2/server-inter/message/material-management.html)
