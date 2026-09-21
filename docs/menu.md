---
title: 面板（Menu）
layout: default
permalink: /menu/
---

[返回首页](index.md)

# 面板（Menu 模块）

`/indra menu` 打开图形主菜单。面板**不依赖指令**——其他插件可通过 API 调 `MenuManager.open(player, "main")` 直接打开，指令只是入口之一。

## 配置（menu.yml）

只管「外观」：标题、行列数、材质、文案。玩法数值在 [config.yml](config.md)。开启了 **autoReload**——改完执行 `/indra reload` 即生效。

```yaml
menus:
  main:
    enabled: true            # false = 打开时提示已禁用
    audit: true              # 面板开关审计记录到控制台
    title: "&8Indra 主菜单"
    rows: 6
    filler-material: "GRAY_STAINED_GLASS_PANE"   # 空槽底板
    info-slot: 4
    info-material: "NETHER_STAR"
    info-name: "&b&lIndra &7RPG 系统"
    buttons:
      player:
        slot: 13
        material: "PLAYER_HEAD"
        name: "&b我的信息"
        lore:
          - "&7等级：&eLv.{level}"
          - "&7经验：&e{exp} &8/ &7{expNext}"
          - "&7击杀 / 死亡：&a{kills} &8/ &c{deaths}"
          - "&7可用点数：&e{points}"
```

## 材质与颜色

- **材质**：写 Bukkit 材质名（全大写，如 `STONE` / `DIAMOND` / `PLAYER_HEAD`），底层用 **XMaterial** 解析，老版本材质名（如 `SKULL_ITEM`）也能识别；写错不会导致面板打不开——自动退回石头并提示一次
- **颜色**：`&` 传统色码（`&a` `&c` `&l` 等）；十六进制用 `&x&f&f&0&0&0&0` 形式

## 占位符

| 占位符 | 说明 |
|---|---|
| `{player}` | 当前玩家名 |
| `{level}` / `{exp}` / `{expNext}` | 等级 / 经验 / 距下一级还差的经验 |
| `{kills}` / `{deaths}` / `{points}` | 击杀 / 死亡 / 可用点数 |

其余占位符在各面板内部填充，写不写都不会报错。
