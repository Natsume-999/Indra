---
title: 命令与权限
layout: default
permalink: /commands/
---

[返回首页](index.md)

# 命令与权限

主命令 **`/indra`**，别名 **`/ind`**。所有子命令需要 **`indra.admin`** 权限（OP 默认拥有）。

> 💡 想把部分功能开放给普通玩家（例如 `/indra menu`），用 LuckPerms 等权限插件授予 `indra.admin` 即可。

## 基础

| 命令 | 用途 |
|---|---|
| `/indra status` | 查看已启用模块 |
| `/indra reload` | 重载配置 |
| `/indra modules` | 模块列表与计数 |
| `/indra menu [面板]` | 打开图形面板（不带参数时列出可用面板） |

## 玩家成长

| 命令 | 用途 |
|---|---|
| `/indra level [玩家]` | 查看自己 / 指定玩家的等级（控制台必须指定玩家） |
| `/indra exp add <玩家> <数量>` | 增加经验 |
| `/indra top [数量]` | 等级排行榜（默认 10，范围 1–50） |
| `/indra mob level` | 查询所站位置的怪物等级（由离原点距离决定） |

## 封禁（Ban 模块）

| 命令 | 用途 |
|---|---|
| `/indra ban …` | 封禁玩家 |
| `/indra unban …` | 解封 |
| `/indra kick …` | 请出玩家 |
| `/indra whitelist …` | 白名单管理 |
| `/indra warn …` / `/indra delwarn …` | 警告 / 删除警告 |
| `/indra warnings …` | 警告列表 |
| `/indra banstatus <玩家>` | 封禁状态查询 |
| `/indra banhistory …` | 封禁历史 |

参数支持 **Tab 补全**，具体格式以游戏内补全为准。

## 出售（Sell 模块）

| 命令 | 用途 |
|---|---|
| `/indra sell` | 打开出售界面（仅一个界面时是否直接打开由 `open.direct-when-single` 决定） |
| `/indra sell open <界面>` | 打开指定界面 |
| `/indra sell list` | 列出已加载的规则与界面 |
| `/indra sell reload` | 重载规则与界面 |
| `/indra sell log` | 查看交易日志 |

## 权限一览

| 权限 | 覆盖 | 默认 |
|---|---|---|
| `indra.admin` | 全部 `/indra` 子命令 | OP |
