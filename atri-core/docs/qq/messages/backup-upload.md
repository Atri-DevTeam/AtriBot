# 备用图片上传

[API 目录](../../README.md) · `top.yzljc.atricore.chat.official.HackMPUploader`

`HackMPUploader.upload(byte[] imageData)` 同步上传图片并返回可访问 URL，不发送聊天消息，失败返回 null

```java
import top.yzljc.atricore.chat.official.HackMPUploader;
import java.nio.file.Files;
import java.nio.file.Path;

byte[] imageData = Files.readAllBytes(Path.of("preview.webp"));
String imageUrl = HackMPUploader.upload(imageData);
if (imageUrl == null) throw new IllegalStateException("备用上传失败");
```

## 输入与配置

| 项目 | 当前要求 |
| --- | --- |
| 平台 | QQ 已启用 |
| 群标识 | 配置 `qq.debug-group-openId`，作为上传目标 |
| 数据 | 非空 byte[]，最多 20 MiB |
| 图片格式 | 根据文件头识别 PNG、JPEG、GIF、WebP |
| 结果 | 可访问地址或 null，不保证永久有效 |

此入口使用平台资源地址的备用上传流程，不参与普通聊天媒体上传。调用方已持有整张图片字节数组，应避免同时堆积大量大图任务

参数、配置、格式或上传校验失败均可能返回 null；等待中断会恢复线程中断标记。常规发送优先使用 [图片与文件接口](media.md)

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### HackMPUploader

```java
public static String upload(byte[] image);
```
