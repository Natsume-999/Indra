---
title: 刷怪控制（MobSpawn）
layout: default
permalink: /spawn/
---

[返回首页](index.md)

# 刷怪控制（MobSpawn 模块）

核心目标：**为自定义怪物让路**——把原版生成拦下来，只放行你想要的部分。生成事件按以下顺序逐层判定：

```text
世界白名单 → 生成原因白名单 → 实体白名单 → 拦截
```

## 配置（config.yml → spawn 节）

| 键 | 默认 | 说明 |
|---|---|---|
| `disable-vanilla-spawn` | `true` | 总开关：禁止原版生物生成 |
| `allow-worlds` | `[]` | 世界白名单：列表内的世界不受拦截（留空 = 所有世界都拦截） |
| `allow-reasons` | `CUSTOM` `SPAWNER` `EGG` `COMMAND` | 生成原因白名单，符合这些原因的生成放行 |
| `whitelist-entities` | `VILLAGER` `WANDERING_TRADER` `WOLF` `CAT` `HORSE` `IRON_GOLEM` | 实体白名单：这些生物即使自然生成也放行 |
| `disable-spawner` | `false` | 是否单独禁止刷怪笼（allow-reasons 含 SPAWNER 时被覆盖） |
| `disable-despawn` | `true` | 禁止带 Indra 等级标记的生物被服务器自然清理 |

## 生成原因速查

| 原因 | 含义 | 什么时候想放行 |
|---|---|---|
| `CUSTOM` | 插件自定义刷怪 | 自己的 RPG 怪物 |
| `SPAWNER` | 刷怪笼 | 保留传统刷怪塔玩法 |
| `EGG` | 刷怪蛋 | 管理员手动放怪 |
| `COMMAND` | 指令生成 | `/summon` 等 |
| `BUILDING` | 建造生成 | 雪傀儡、铁傀儡（也可走实体白名单） |

## 典型场景

**场景 A：全自定义怪物** —— 保持默认配置：原版全拦，只放行 CUSTOM / SPAWNER / EGG / COMMAND 与村民等保护生物。

**场景 B：主世界保留原版，资源世界拦截** —— `allow-worlds: [world]`（主世界放行），其余世界全部拦截。

**场景 C：配合怪物等级** —— 拦截后由 [怪物等级模块](moblevel.md) 给生成的怪按距离打等级；保持 `disable-despawn: true`，避免刷出来的等级怪被服务器自然清理。
