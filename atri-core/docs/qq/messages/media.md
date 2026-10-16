# 图片、语音与文件上传

[API 目录](../../README.md)

群聊 / C2C 通过聊天方法完成上传和发送。`OfficialMediaUploader`、`OfficialFileTransfer` 是包内实现，外部无需直接处理 upload_id、分片地址或 file_info

## URL 与 Base64 图片

```java
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.ImageType;
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.chat.official.RT;

var image = ImageComponent.imageOf(imageUrl).setText("查询结果");
String sentId = GroupChat.replyMessage(groupOpenId, RT.message(messageId), image);
var encoded = ImageComponent.imageOf(base64, ImageType.BASE64);
String base64Id = GroupChat.replyMessage(groupOpenId, RT.message(messageId), encoded);
```

| ImageComponent 入口 | 说明 |
| --- | --- |
| `new ImageComponent(data)` / `imageOf(url)` | 默认 ImageType.URL |
| `new ImageComponent(data, type)` / `imageOf(data, type)` | 显式指定 URL 或 BASE64 |
| `setData` / `setType` / `setText` | 链式设置数据、类型和附加文本 |
| `getData` / `getType` / `getText` | 读取字段 |

::: warning 上传失败时的返回值
普通图片上传失败时，部分入口会发送错误文字回退；拿到消息 ID 不一定代表发送了原图片。`replyAudioMessage` 则在上传失败时直接返回 null
:::

## 本地文件

```java
import top.yzljc.atricore.platform.qq.FileType;
import java.nio.file.Path;

String sentId = GroupChat.replyMessage(groupOpenId, RT.message(messageId),
        FileType.FILE, Path.of("report.zip"));
```

主动发送使用 `sendMessage(target, fileType, path)`，C2C 将 GroupChat 换为 C2CChat；非阻塞调用使用对应 Async 类

| FileType | 值 | 内容 |
| --- | --- | --- |
| IMAGE | 1 | 图片 |
| VIDEO | 2 | 视频 |
| AUDIO | 3 | 语音 |
| FILE | 4 | 文件 |

| 本地文件大小 | 当前上传策略 |
| --- | --- |
| 1 至 5,000,000 字节 | Base64 编码后使用 file_data 直传 |
| 超过 5,000,000 字节且不超过 200 MiB | 预上传、分片 PUT、逐片完成通知、合并 |
| 空文件或超过 200 MiB | 本地校验失败 |

::: note 文件读取与上传限制
分片按文件区间读取，不一次性加载整个大文件。上传完成前不要改动源文件；这些阈值不替代平台按媒体类型设置的限制
:::

## 远程媒体

群聊通用媒体回复使用 `GroupChat.replyMessage(groupOpenId, rt, fileType, url)`；语音可使用 `replyAudioMessage`。C2C 的召回媒体使用 `wakeupMessage(userOpenId, fileType, url)`

频道图片不经过此上传流程，详见 [QQ 频道消息](guild.md)。独立的备用图片上传接口见 [备用图片上传](backup-upload.md)

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### ImageComponent

```java
public ImageComponent(String data);
public ImageComponent(String data, ImageType type);
public static ImageComponent imageOf(String url);
public static ImageComponent imageOf(String data, ImageType type);
public ImageComponent setData(String data);
public ImageComponent setText(String text);
public ImageComponent setType(ImageType type);
```
