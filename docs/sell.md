---
title: 出售系统（Sell）
layout: default
permalink: /sell/
---

[返回首页](index.md)

# 出售系统（Sell 模块）

规则与界面格式和 **Phoenix（VitaSell）100% 兼容**——把 Phoenix 的 `sell/` 与 `table/` 目录内容直接拷进来即可使用。

## 文件分工

| 文件 / 目录 | 职责 |
|---|---|
| `sell.yml` | 模块自身行为（见下） |
| `plugins/Indra/sell/*.yml` | **出售规则**（一个文件可写多条规则） |
| `plugins/Indra/table/*.yml` | **出售界面**（一个文件可写多个界面） |

## 出售规则（sell/*.yml）

匹配时按「文件名字母序 + 文件内顶层键顺序」，**命中第一条即生效**。

### 规则字段

| 字段 | 说明 |
|---|---|
| `Item` | 物品匹配串（分号分隔多条件 AND） |
| `Table` | 限定生效的界面名；留空 = 全部界面生效 |
| `Condition` | 出售条件（Kether 脚本列表，全部为真才可出售） |
| `Action.Money` | 金币，格式 `"数值 权重"` |
| `Action.Point` | 点券，格式同上 |
| `Action.Kether` | 成交后额外执行的脚本 |

### Item 匹配串条件（`&a` 与 `§a` 等价）

| 条件 | 说明 |
|---|---|
| `name:<文本>` / `name:contains(<文本>)` / `name:!contains(<文本>)` | 显示名 等于 / 包含 / 不包含 |
| `lore:<文本>` / `lore:contains(<文本>)` / `lore:!contains(<文本>)` | Lore 任一行 |
| `type:<材质>` | 材质（逗号写多个：`type:STONE,DIRT`） |
| `amount:<n>` / `>=n` / `>n` / `=n` / `<n` / `<=n` / `!=n` | 数量 |
| `custommodeldata:<n>` | 自定义模型数据 |
| `unbreakable:true\|false` | 是否不可破坏 |

⚠️ 不支持 `nbt:` / `tag:` / `itemmodel:` 及各类插件物品——复杂判断请改用 `Condition` 的 Kether 脚本。

### ⚠️ 数值符号语义（继承 VitaSell，务必注意）

Money / Point 的数值 **负数 = 给予玩家，正数 = 扣除**（保证 Phoenix 配置迁移后不会反向扣钱）。

```yaml
"-100 75"        # 75% 概率给予 100 金币
```

### 权重与区间

| 写法 | 含义 |
|---|---|
| `"-100 75"` | 固定权重 75，按比例抽取 |
| `"-500 20~25"` | 数值 -500；权重在 20~25 之间（按期望 22.5 处理） |
| `"-200~-100 25"` | 数值在 -200~-100 之间随机；权重 25 |

权重只在**同一条规则内**比较，不同规则之间互不影响。

## 出售界面（table/*.yml）

| 字段 | 说明 |
|---|---|
| `Title` | 界面标题（支持 `&` 色码） |
| `Layout` | 布局网格，**每行必须 9 个字符**，最多 6 行 |
| `Auto-Sell` | `true` = 物品放入后立即结算 |
| `Icon` | 字符 → 图标定义（`Type` / `Data` / `Name` / `Lore` / `Bind`） |

**布局核心规则**：Layout 里的每个字符去 Icon 找同名键——

- Icon 里**有**这个字符 → 该槽位放图标（装饰或按钮）
- Icon 里**没有**这个字符 → 该槽位是**玩家可放入物品的空位**（空格无需定义）

所以「一行 9 个空格」（整行都是空位、不在 Icon 里定义）就是「一整行都能放物品」。

**Bind 绑定动作**（写在 Icon 里）：

| 动作 | 含义 |
|---|---|
| `Close` | 关闭界面 |
| `Sell` | 点击出售界面内的物品 |
| `Put` | 一键放入：把背包里可出售的物品搬进界面（可用 `Put-Match: "匹配串"` 限定类型） |

```yaml
Example:
  Title: "§6出售界面"
  Layout:
    - '####S####'
    - '         '
    - '         '
    - '         '
    - '         '
    - '####P###C'
  Auto-Sell: false
  Icon:
    '#': { Type: 'GRAY_STAINED_GLASS_PANE', Data: 7, Name: '§8' }
    'C': { Type: 'GRAY_STAINED_GLASS_PANE', Data: 14, Name: '§c关闭菜单', Bind: "Close" }
```

## 模块配置（sell.yml）

| 键 | 默认 | 说明 |
|---|---|---|
| `open.direct-when-single` | `true` | `/indra sell` 只加载到 1 个界面时直接打开 |
| `options.auto-release-defaults` | `true` | 无界面时自动释放默认示例并重扫（首次安装友好） |

## 指令与经济

见 [命令与权限](commands.md) 的出售节。金币结算走 **Vault**（需已安装经济插件）。
