# 指令面板

[API 目录](../../README.md) · 包 `top.yzljc.atricore.chat.official.panel`

`Panel` 管理 QQ 客户端的指令面板，包括列表、详情、创建、修改、删除和关联对象

::: tip 配置对应的指令执行器
面板负责客户端展示，指令处理需通过 [指令注册](../../commands.md) 接入。创建面板不会自动注册执行器
:::

## 创建群聊面板

```java
import top.yzljc.atricore.chat.official.panel.Panel;
import java.util.List;

var content = new Panel.PanelData(List.of(
        new Panel.PanelItem("help", "查看使用帮助", "command", false, null),
        new Panel.PanelItem("网站", "打开网站", "link", false, "https://example.com")),
        "群聊常用指令");
String panelId = Panel.createPanel("group", "specific", null,
        List.of(groupOpenId), content);
if (panelId == null) {
    throw new IllegalStateException("创建面板失败");
}
```

## 方法

| 调用 | 返回 | 行为 |
| --- | --- | --- |
| `listPanels(scope, cursor, limit)` | `PanelListResult` / null | 按场景分页查询 |
| `createPanel(scope, targetType, userOpenIds, groupOpenIds, panel)` | `String` / null | 创建并返回面板 ID |
| `getPanelDetail(panelId)` | `PanelDetail` / null | 查询内容及关联用户、群 |
| `updatePanel(panelId, panel)` | `Integer` / null | 完整替换内容，返回版本号，不改变关联对象 |
| `updatePanelTarget(panelId, op, userOpenIds, groupOpenIds)` | `boolean` | 添加或移除关联对象 |
| `deletePanel(panelId)` | `boolean` | 删除面板 |

所有方法同步请求；null 或 false 表示本次操作未成功，不能把空列表与查询失败混为一谈

## 参数

| 参数 | 类型 / 值 | 说明 |
| --- | --- | --- |
| `scope` | `c2c` / `group` / `channel` / `dm` | 私聊、群聊、文字子频道、频道私信 |
| `targetType` | `all` / `specific` | 全部对象或指定对象；指定对象用于私聊和群聊 |
| `userOpenIds` | `List<String>` | 私聊关联用户，不使用 QQ 号码 |
| `groupOpenIds` | `List<String>` | 关联群 OpenID，不使用群号码 |
| `panelId` | `String` | 创建或查询得到的面板 ID |
| `op` | `Panel.TargetOp.ADD` / `DEL` | 增加或移除关联对象，`value()` 返回平台字符串 |
| `cursor` | `String` | 首次传 null，后续使用 `nextCursor()` |
| `limit` | `int` | 小于等于 0 使用 20，当前实现最多请求 50 条 |

`updatePanelTarget` 至少提供一组非空对象列表；按面板场景选择列表，不同时混用私聊与群聊目标

## 面板内容和查询结果

| 类型 / 字段 | 说明 |
| --- | --- |
| `PanelItem.name` | 指令或链接项名称 |
| `PanelItem.desc` | 显示说明 |
| `PanelItem.type` | `command` 或 `link` |
| `PanelItem.onlyAdmin` | 是否仅管理员可用 |
| `PanelItem.link` | 链接项地址，指令项传 null |
| `PanelData.items` / `remark` | 有序项目列表及备注 |
| `PanelRecord` | ID、场景、关联类型、面板内容、创建与更新时间、版本 |
| `PanelListResult.records` | 当前页记录 |
| `PanelListResult.nextCursor` / `isEnd` | 下一页游标及是否结束 |
| `PanelDetail` | `PanelRecord` 的信息以及 `userOpenIds`、`groupOpenIds` |

## 分页与修改

```java
String cursor = null;
do {
    var result = Panel.listPanels("group", cursor, 50);
    if (result == null) throw new IllegalStateException("查询面板失败");
    for (var item : result.records()) {
        System.out.println(item.panelId() + " " + item.panel().remark());
    }
    if (result.isEnd()) break;
    cursor = result.nextCursor();
} while (cursor != null && !cursor.isBlank());
```

修改内容时先读取详情，再构造完整 `PanelData`；移除关联使用 `TargetOp.DEL`，删除整个面板使用 `deletePanel`

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### Panel

```java
public record PanelItem(String name, String desc, String type, boolean onlyAdmin, String link);
public record PanelData(List<PanelItem> items, String remark);
public record PanelRecord(String panelId, String scope, String targetType, PanelData panel, String createdAt, String updatedAt, int version);
public record PanelListResult(List<PanelRecord> records, String nextCursor, boolean isEnd);
public record PanelDetail(String panelId, String scope, String targetType, PanelData panel, String createdAt, String updatedAt, int version, List<String> userOpenIds, List<String> groupOpenIds);
public static PanelListResult listPanels(String scope, String cursor, int limit);
public static String createPanel(String scope, String targetType, List<String> userOpenIds, List<String> groupOpenIds, PanelData panel);
public static PanelDetail getPanelDetail(String panelId);
public static Integer updatePanel(String panelId, PanelData panel);
public static boolean deletePanel(String panelId);
public static boolean updatePanelTarget(String panelId, TargetOp op, List<String> userOpenIds, List<String> groupOpenIds);
```
