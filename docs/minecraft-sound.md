# Minecraft 听声辨物 /sound

2026-09-16 完成全量源文件映射、解码及出题一致性检查，见 [检查报告](minecraft-sound-audit-2026-09-16.md)。

支持官机群聊与私聊（C2C）。私聊按用户独立开题、答题和计时，各用户及群聊互不影响。先发送 Markdown 题面及同一行的 A～D 按钮，再发送音频；两者成功后才开始计时。私聊使用单聊音频上传、回复接口，上传失败会结束当前题目并记录错误。

题面为 Markdown，A～D 四个指令按钮排列在同一行；首个有效作答无论对错均立即结束并公布答案。默认限时 120 秒，超时无人作答也会揭晓答案。没有重听按钮或重听指令。

## 配置与上传

在机器人实际运行目录的 `config.yml` 中添加：

```yaml
sound:
  resource-base-url: "https://cnb.cool/Atri-DevTeam/AtriBot-Minecraft/-/git/raw/main/bot/"
  index-path: "minecraft/26.3-rc-3/index.json"
  answer-seconds: 120
```

资源前缀可以有或没有末尾斜杠。只使用这一节点获取题库、播放音频，运行中的机器人不访问 Mojang。修改后重启机器人；已有配置重载也会更新这三个字段，下一次开题采用新配置。

当前使用 CNB Git 仓库 `Atri-DevTeam/AtriBot-Minecraft` 的 `main` 分支，资源位于仓库根目录的 `bot/`。索引地址为：

```text
https://cnb.cool/Atri-DevTeam/AtriBot-Minecraft/-/git/raw/main/bot/minecraft/26.3-rc-3/index.json
```

将交付资源包解压，把里面的 `minecraft/` 文件夹原样上传到 `/bot/` 对应目录。不要再套一层 `sound-upload/`。

```text
https://xxxx.example.com/bot/
└── minecraft/26.3-rc-3/
    ├── index.json
    ├── sounds.json
    ├── lang/zh_cn.json
    ├── sounds/.../*.ogg     原版完整文件，路径与资源索引对齐
    ├── playback/*.ogg      出题播放副本
    ├── version.json
    └── asset-index.json
```

索引里的 `path` 是原版文件路径，`playbackPath` 是播放副本路径，均相对于 `resource-base-url`。例如：

```text
原版路径：minecraft/26.3-rc-3/sounds/mob/zombie/hurt1.ogg
原版 URL：https://xxxx.example.com/bot/minecraft/26.3-rc-3/sounds/mob/zombie/hurt1.ogg
播放 URL：resource-base-url + playbackPath
```

节点上的索引和音频需要允许直接 GET 下载。索引首次开题加载，缓存 10 分钟；配置改变会重新加载。节点应直接返回文件，索引请求不跟随重定向。

## 指令与玩法

| 指令 | 功能 |
|---|---|
| `/sound` | 从全部题目中随机开题 |
| `/sound help` | 查看玩法 |
| `/sound answer A`（或 `/sound A`） | 回答当前题目，支持 A～D，不区分大小写 |

同群同时一道题，点击 A～D 会发送 `/sound answer <题号> <选项>` 指令，也可以手动输入答题指令。首个有效作答立即结束本题，以普通文本告知对错及正确答案；其他答案及原超时任务不能重复结算。无人作答时在截止后以普通文本公布答案。暂不接入金币、排行榜或数据库积分。

音频和题面均发送成功才开始计时；上传失败不会将文字提示当作成功的语音。时限可配置为 15～120 秒，状态层也限制最长 2 分钟，重复初始化不能延长截止时间。每题均有独立随机标识，重复点击、旧按钮、跨群题号、截止后作答均会被拒绝。答题和开题统一经过指令权限及停用检查。

## 完整资源保留与分组

- 固定版本为 `26.3-rc-3`，导出官方资源索引中的 **全部 4,961 个 OGG**，包含音乐、环境音和短音效。
- **全部 1,991 个声音事件保留在索引中**。原版没有音频的空事件保留记录；其余事件均建立以自己为正确答案的候选组。
- 自动读取 `sounds.json`、中文字幕、实体/方块/物品/生物群系翻译。缺字幕的事件使用规则生成名称，无法翻译的词保留可读原文；可直接校正索引中的 `name`。
- 递归展开声音事件引用，保留音频变体权重，应用资源中声明的音调与音量。共享文件和相同解码音频只影响同题干扰项选择，不据此移除整个声音事件。
- 音频分析采用频带能量、音量包络与时长。每个事件优先寻找来源/动作相关且声音接近的候选，不足时扩大范围。每组最多 7 个互不冲突的选项，运行时随机抽其中 3 个干扰项。
- 所有题目统一随机抽取，没有简单、普通、困难模式或难度按钮。索引中已有的难度字段仅保留为分析元数据，不影响抽题；个别分组和名称仍需要试听打磨。

播放副本为 48 kHz 单声道 OGG。短于 1 秒的片段只补尾部静音；长于 12 秒的取前 12 秒出题。**原版 `sounds/` 文件不裁剪、不转码、不覆盖**。播放副本由 PCM 内容哈希命名，同时避免在语音地址中直接泄露答案。

资源中没有包含的游戏代码侧随机音调、位置衰减等参数不会自动还原。最终 QQ 播放听感及 Markdown/按钮权限，需要在部署后的实际群聊中验证。

## 重建资源包

### 显示名称与发送问题定位

`scripts/minecraft_sound/display_names.json` 按事件 ID 修正少量容易歧义或未翻译的名称，构建时自动应用。被修正事件的 `originalName` 保留原名称供核对，原版 `sounds.json`、语言文件和全部音频保持完整。题面和普通文本结算均带有 Minecraft 听声辨物标题。

已有资源目录可离线刷新名称，不重新生成音频或候选组：

```powershell
python scripts/minecraft_sound/build_pool.py --refresh-names
```

发送失败日志记录发送阶段、题号、正确事件 ID、播放路径及 SHA-1、选项事件 ID，并在平台响应提供时记录错误码及 trace ID。上传和消息接口的原始响应仍由官机发送日志保存。错误码缺失、下载失败或限频均不自动认定为违规；遇到明确的内容审核拦截，应按日志定位具体文件，经人工检查后暂停对应播放文件或向平台申诉。名称修正不代表平台审核保证。

### 完整构建

构建阶段需要 Python 3.10+、NumPy、支持 libvorbis 的 FFmpeg；机器人运行时不需要 Python 或 FFmpeg。

从仓库根目录执行：

```powershell
python scripts/minecraft_sound/build_pool.py
python scripts/minecraft_sound/prepare_audio.py --ffmpeg "C:/path/to/ffmpeg.exe"
python scripts/minecraft_sound/verify_pool.py
python scripts/minecraft_sound/package_pool.py
```

第一步联网访问 Mojang 官方清单、元数据与资源节点，校验 SHA-1 并缓存到 `build/sound-cache/`；其余步骤离线运行。输出目录为 `build/sound-upload/`，压缩包为 `build/minecraft-sound-26.3-rc-3.zip`。完整执行后再上传；仅执行第一步尚未生成可玩的候选组。

`build-report.json` 记录原版导出，`audio-analysis.json` 记录播放副本和候选组。`verify_pool.py` 检查全部原始文件的哈希、全部事件是否保留、播放文件完整性、每组歧义与每个非空事件的正确答案覆盖。

测试：

```powershell
python -m unittest discover -s scripts/minecraft_sound -p "test_*.py"
.\gradlew.bat test --tests "top.yzljc.atribot.function.games.sound.*"
```

生成完整资源池后，Java 测试还会加载实际索引，并抽样验证全部难度；没有本地资源包时仅跳过此项集成检查。

## 参考

- [Mojang 版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
- [26.3 RC 3 官方公告](https://www.minecraft.net/en-us/article/minecraft-26-3-release-candidate-3)
- [官机富媒体文档](https://bot.q.qq.com/wiki/develop/api-v2/server-inter/message/rich-media.html)：URL 上传及 OGG 语音条。

当前实现沿用 `MusicCommand` 的群聊媒体上传/发送链路，并为游戏增加严格失败返回，防止上传失败后继续开题。
