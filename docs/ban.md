---
title: 封禁系统（Ban）
layout: default
permalink: /ban/
---

[返回首页](index.md)

# 封禁系统（Ban 模块）

移植自 **Phoenix v1.16.0**（PhoenixBan），配置与消息格式兼容。

## 命令（均在 /indra 下，需 `indra.admin`）

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

## 配置（ban.yml）

| 键 | 默认 | 说明 |
|---|---|---|
| `Whitelist` | `false` | 开启后仅白名单玩家可进服（一般建议用服务器自带 whitelist.json） |
| `Player-ID` | `uuid` | 封禁主键：`uuid`（推荐，改名无法绕过）/ `name`（改名可绕过） |
| `Default-Value.*` | … | 管理员未填原因时的兜底文案（Kick / Ban / Warn） |
| `Warning.Auto-Ban-Threshold` | `0` | 警告累计到 N 次自动封禁（0 = 关闭） |
| `Warning.Auto-Ban-Duration` | `1d` | 自动封禁时长 |
| `Warning.Auto-Ban-Reason` | 警告次数过多 | 自动封禁原因 |
| `Auto-Unban.Enabled` | `true` | 到期自动解封 |
| `Auto-Unban.Interval-Seconds` | `30` | 扫描周期（秒，最小 1） |
| `Time-Format.*` | … | 日期 / 时间 / 时长单位的解析与显示格式 |

## 自动解封脚本（Kether）

到期自动解封后可执行 Kether 动作，可用变量：

`{player}` `{uuid}` `{reason}` `{duration}` `{ban_time}` `{unban_time}` `{banning_admin}`

```yaml
Auto-Unban:
  Enabled: true
  Interval-Seconds: 30
  Actions:
    - 'tell {player} 到 &a你的封禁已到期，现已解除。'
    # - 'command console broadcast &a[系统] {player} 的封禁已到期解除'
```

## 消息格式（ban-messages.yml）

Kick / Ban / Whitelist 三组消息，支持 `&` 色码与 `\n` 换行：

| 消息组 | 占位符 |
|---|---|
| Kick | `{0}` 玩家名 · `{1}` UUID · `{2}` 原因 · `{3}` 操作管理 · `{4}` 操作时间 |
| Ban | `{0}`–`{4}` 同上 · `{5}` 解封时间 · `{6}` 封禁时长 |
| Whitelist | `{0}` 玩家名 · `{1}` UUID |

## 开发者事件

其他插件可监听以下自定义事件做联动：

- `OnlinePlayerBanEvent` / `OfflinePlayerBanEvent`
- `PlayerUnbanEvent`
- `PlayerKickEvent`
- `PlayerWhitelistEvent`

## 数据存储

封禁、警告、历史记录与玩家数据存于同一数据库（[SQLite / MySQL 切换](storage.md)）。
