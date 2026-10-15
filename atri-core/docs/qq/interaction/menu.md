# 自定义菜单

[API 目录](../../README.md) · 包 `top.yzljc.atricore.chat.official.menu`

`Menu` 查询和替换机器人的全局自定义菜单，菜单内容由 QQ 客户端展示，操作不会注册 Java 指令执行器

## 查询与保存

```java
import top.yzljc.atricore.chat.official.menu.Menu;
import java.util.List;

var query = Menu.queryMenu();
if (query == null) {
    throw new IllegalStateException("查询菜单失败");
}
int currentVersion = query.version();
var existingMenu = query.menu();

var children = List.of(
        new Menu.SubMenuItem("帮助", "send_message", "/help", null),
        new Menu.SubMenuItem("网站", "link", null, "https://example.com"));
var menu = new Menu.MenuData(List.of(
        new Menu.MenuItem("功能", "menu", children, null, null, null, null),
        new Menu.MenuItem("查询", "send_message", null, "/query", null, null, null),
        new Menu.MenuItem("通知", "switch", null, null, null,
                new Menu.Switch("notifications", true), null)));
Integer newVersion = Menu.updateMenu(menu);
if (newVersion == null) {
    throw new IllegalStateException("保存菜单失败");
}
```

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `queryMenu()` | `MenuQueryResult` | 查询失败返回 null；成功但尚未设置菜单时，`menu()` 可以为 null |
| `updateMenu(menu)` | `Integer` | 完整替换菜单，成功返回版本号，失败返回 null |

::: warning 保存会替换整个菜单
保存时提交完整的 `MenuData`，不是局部合并；当前实现拒绝 null 菜单及空 `items`，不能通过空列表清空菜单
:::

## 菜单字段

| 类型 / 字段 | 含义 |
| --- | --- |
| `MenuData.items` | 顶级菜单项，列表顺序即显示顺序 |
| `MenuItem.name` | 显示名称 |
| `MenuItem.type` | `menu`、`send_message`、`link` 或 `switch` |
| `MenuItem.subMenuItems` | `menu` 类型的子项 |
| `MenuItem.sendMessage` | `send_message` 类型发送的文本 |
| `MenuItem.link` | `link` 类型打开的地址 |
| `MenuItem.switchConfig` | `switch` 类型的开关配置 |
| `MenuItem.align` | 平台的排列参数，未指定时传 null |
| `Switch.switchId` | 开关标识，用于识别开关事件 |
| `Switch.defaultOn` | 默认是否开启 |
| `SubMenuItem` | 支持 `send_message`、`link`，不包含嵌套子菜单或开关字段 |
| `MenuQueryResult.version` | 平台返回的菜单版本 |

与当前类型无关的字段传 null，序列化时会省略空值；Java 字段 `switchConfig` 会写入平台的 `switch` 对象

查询和保存均同步等待 HTTP 结果，使用 Provider 的 QQ 配置和访问令牌。开关事件见 [事件目录](../../events.md)

## 方法与数据类型索引

以下签名对应当前源码，`record` 字段通过同名方法读取

### Menu

```java
public record Switch(String switchId, boolean defaultOn);
public record SubMenuItem(String name, String type, String sendMessage, String link);
public record MenuItem(String name, String type, List<SubMenuItem> subMenuItems, String sendMessage, String link, Switch switchConfig, String align);
public record MenuData(List<MenuItem> items);
public record MenuQueryResult(MenuData menu, int version);
public static MenuQueryResult queryMenu();
public static Integer updateMenu(MenuData menu);
```
