---
title: 怪物等级（MobLevel）
layout: default
permalink: /moblevel/
---

[返回首页](index.md)

# 怪物等级（MobLevel 模块）

**离世界原点 (0,0) 越远，怪物越强**——天然形成「越远越危险」的地图梯度，引导玩家逐步向外探索。

## 数值规则（config.yml → mob-level 节）

| 项 | 默认 | 说明 |
|---|---|---|
| `blocks-per-level` | `100.0` | 每隔多少格距离提升 1 级（以世界原点 0,0 为中心） |
| `health-per-level` | `1.15` | 每级血量倍率（累乘） |
| `attack-per-level` | `0.5` | 每级攻击力加成（累加） |
| `show-name` | `true` | 头顶显示等级 |

**举例**：距离原点 2500 格 → 25 级怪物，血量 ×1.15²⁵ ≈ 21 倍，攻击 +12.5。

## 与其他系统的联动

- **杀怪经验** = `exp-per-mob-level`（默认 5）× 怪物等级 → 越远处的怪给的经验越多（见 [玩家成长](player.md)）
- **防误清**：[刷怪控制](spawn.md) 的 `spawn.disable-despawn: true` 可防止带等级标记的怪被服务器自然清理——建议保持开启

## 调参建议

- 想要平缓曲线：调低 `health-per-level`（如 `1.08`）、调大 `blocks-per-level`（如 `200`）
- 查询当前位置的怪物等级：`/indra mob level`
