---
title: 聊天格式（Chat）
layout: default
permalink: /chat/
---

[返回首页](index.md)

# 聊天格式（Chat 模块）

开关：`config.yml → chat.enable-format`（默认开启）。

```yaml
chat:
  enable-format: true
  format: "&7[&eLv.{level}&7] &f{player} &8» &7{message}"
```

效果示例：`[Lv.12] Steve » 大家好`

## 占位符

| 占位符 | 含义 |
|---|---|
| `{player}` | 玩家名 |
| `{level}` | 玩家等级 |
| `{message}` | 消息内容 |

支持 `&` 传统色码。修改后 `/indra reload` 生效。
