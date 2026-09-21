---
title: 配置参考（config.yml）
layout: default
permalink: /config/
---

[返回首页](index.md) · 修改后执行 `/indra reload` 生效。

# config.yml 配置参考

## 顶层

| 键 | 默认 | 说明 |
|---|---|---|
| `debug` | `false` | 调试模式：控制台输出生成拦截日志 |
| `verbose` | `true` | 打印模块加载详情 |

## spawn — 原版生物生成控制

| 键 | 默认 | 说明 |
|---|---|---|
| `disable-vanilla-spawn` | `true` | 总开关：禁止原版生物生成 |
| `allow-worlds` | `[]` | 世界白名单：列表内的世界不受拦截（留空 = 所有世界都拦截） |
| `allow-reasons` | `CUSTOM` `SPAWNER` `EGG` `COMMAND` | 生成原因白名单，符合即放行 |
| `whitelist-entities` | `VILLAGER` `WANDERING_TRADER` `WOLF` `CAT` `HORSE` `IRON_GOLEM` | 实体白名单：这些生物即使自然生成也放行 |
| `disable-spawner` | `false` | 是否禁止刷怪笼（allow-reasons 含 SPAWNER 时被覆盖） |
| `disable-despawn` | `true` | 禁止带 Indra 等级标记的怪被服务器自然清理 |

`allow-reasons` 常用值：`CUSTOM`（自定义刷怪）、`SPAWNER`（刷怪笼）、`EGG`（刷怪蛋）、`COMMAND`（指令）、`BUILDING`（雪傀儡等建造生成）。

## mob-level — 怪物等级强化

| 键 | 默认 | 说明 |
|---|---|---|
| `enable` | `true` | 开关 |
| `blocks-per-level` | `100.0` | 每隔多少格提升 1 级（以世界原点 0,0 为中心） |
| `health-per-level` | `1.15` | 每级血量倍率（累乘） |
| `attack-per-level` | `0.5` | 每级攻击力加成（累加） |
| `show-name` | `true` | 头顶显示等级 |

## player — 玩家等级与经验

| 键 | 默认 | 说明 |
|---|---|---|
| `default-level` / `default-exp` | `1` / `0` | 新玩家初始值 |
| `max-level` | `100` | 等级上限 |
| `exp-per-level` | `100` | 升级所需经验 = 基数 × 当前等级 |
| `points-per-level` | `3` | 每级奖励点数（预留给技能 / 天赋系统） |
| `exp-per-mob-level` | `5` | 杀怪经验 = 该值 × 怪物等级 |
| `welcome-message` | `&a欢迎来到服务器…` | 首次进服欢迎语，可用 `{player}` 占位 |

## death — 死亡惩罚

| 键 | 默认 | 说明 |
|---|---|---|
| `enable-penalty` | `true` | 开关 |
| `exp-penalty-ratio` | `0.1` | 死亡扣除当前经验的比例（0.0–1.0） |

## chat — 聊天格式

| 键 | 默认 | 说明 |
|---|---|---|
| `enable-format` | `true` | 开关 |
| `format` | `&7[&eLv.{level}&7] &f{player} &8» &7{message}` | 占位符：`{player}` 玩家名、`{level}` 等级、`{message}` 消息内容 |

## database / messages

| 键 | 默认 | 说明 |
|---|---|---|
| `database.auto-save-interval` | `300` | 自动保存间隔（秒） |
| `messages.prefix` | `&b[Indra] &r` | 消息前缀 |
| `messages.no-permission` 等 | … | 各类提示文本 |

---

其他配置文件：[datasource.yml 与存储切换](storage.md) · [ban.yml 封禁](ban.md) · [sell.yml 出售](sell.md) · [menu.yml 面板](menu.md)
