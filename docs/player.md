---
title: 玩家成长（Player）
layout: default
permalink: /player/
---

[返回首页](index.md)

# 玩家成长（Player 模块）

## 等级与经验（config.yml → player 节）

- 新玩家初始 **1 级 0 经验**，等级上限 **100 级**
- 升级所需经验 = `exp-per-level`（100）× 当前等级——**越升越贵**
- 杀怪获得经验 = `exp-per-mob-level`（5）× 怪物等级 → 远处的怪经验更多（见 [怪物等级](moblevel.md)）
- 每升 1 级奖励 **3 点数**（`points-per-level`），存于玩家数据，预留给技能 / 天赋系统
- 新玩家首次进服发送欢迎语（`welcome-message`，支持 `{player}` 占位）

## 死亡惩罚（config.yml → death 节）

| 键 | 默认 | 说明 |
|---|---|---|
| `enable-penalty` | `true` | 开关 |
| `exp-penalty-ratio` | `0.1` | 死亡扣除当前经验的比例（0.0–1.0） |

## 排行榜

`/indra top [数量]` —— 默认前 10，范围 1–50。

## 玩家数据字段

存储于 `indra_player` 表（[存储说明](storage.md)）：

| 字段 | 说明 |
|---|---|
| `level` / `exp` / `expTotal` | 等级 / 当前经验 / 累计经验 |
| `points` | 可用点数 |
| `killCount` / `deathCount` | 击杀数 / 死亡数 |
| `coins` | 金币（Vault 经济） |
| `firstJoin` / `lastSeen` | 首次 / 最近登录时间 |

每 300 秒自动保存（`database.auto-save-interval` 可调）。
