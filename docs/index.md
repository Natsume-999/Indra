---
title: 首页
layout: default
permalink: /
---

# Indra

> Minecraft RPG 服务器基础功能插件
> **TabooLib 6.3 + Kotlin** · Paper 26.3 · 双存储（本地 SQLite / MySQL）

Indra 是一套 RPG 服务器的**地基**：禁止原版生物生成、怪物按距离强化、玩家等级经验、聊天前缀、封禁系统、出售系统、图形面板——开箱即用，所有玩法参数在 `config.yml` 里改配置即可，**不用重编译**。

## 功能模块（8 个）

| 模块 | 功能 | 文档 |
|---|---|---|
| 🚫 **MobSpawn** | 原版生物生成控制（世界 / 原因 / 实体三层过滤） | [刷怪控制](spawn.md) |
| ⚔️ **MobLevel** | 怪物按离原点距离强化等级与攻击 | [怪物等级](moblevel.md) |
| 🧑 **Player** | 玩家等级 / 经验 / 死亡惩罚 / 排行榜 / 点数 | [玩家成长](player.md) |
| 💬 **Chat** | 聊天等级前缀格式 | [聊天格式](chat.md) |
| ⛔ **Ban** | 封禁 / 警告 / 白名单 / 自动解封（Phoenix 兼容） | [封禁系统](ban.md) |
| 💰 **Sell** | 出售系统（VitaSell 格式兼容 + Vault 经济） | [出售系统](sell.md) |
| 🖥 **Menu** | 图形面板（menu.yml 可配） | [面板](menu.md) |
| ⌨️ **Command** | `/indra` 命令族（别名 `/ind`） | [命令与权限](commands.md) |

## 快速开始

1. 准备 **Paper 26.3**（Build 19）服务端 + **Java 25**
2. 下载 jar 放入 `plugins/` 目录：[Release v1.6.0](https://github.com/Natsume-999/Indra/releases)
3. 启动服务器。**首次启动需要联网**（自动下载 TabooLib 运行模块与 Kotlin，已内置阿里云镜像）
4. 日志出现 `[Indra] 启用完成，共加载 8 个模块。` 即成功

详细步骤见 [安装与快速上手](install.md)。

## 文档导航

- [安装与快速上手](install.md)
- [命令与权限](commands.md)
- [配置参考（config.yml）](config.md)
- [存储切换（SQLite / MySQL）](storage.md)
- 各模块文档见上表
- 开发者：[功能清单与验证](功能清单.md) · [TabooLib 6.3 开发实战指南](TabooLib-6.3-开发实战指南.md)

## 相关链接

- [GitHub 仓库](https://github.com/Natsume-999/Indra)
- [问题反馈（Issues）](https://github.com/Natsume-999/Indra/issues)
- 开源协议：[MIT](https://github.com/Natsume-999/Indra/blob/main/LICENSE)
