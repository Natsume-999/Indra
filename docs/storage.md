---
title: 存储切换（SQLite / MySQL）
layout: default
permalink: /storage/
---

[返回首页](index.md)

# 存储切换

配置文件 `plugins/Indra/datasource.yml`。**两种模式的表结构完全一致，切换不需要改代码。**

## 本地 SQLite（默认，零依赖）

```yaml
database:
  enable: false        # 总开关：false = 本地 SQLite

sqlite:
  file: data.db        # 数据库文件，位于插件数据目录下
```

数据落在 `plugins/Indra/data.db`，开箱即用。

## MySQL（生产环境 / 多服共享）

```yaml
database:
  enable: true              # 总开关
  type: mysql               # mysql / postgresql
  host: 127.0.0.1
  port: 3306
  user: root
  password: ""
  database: indra           # 数据库名，需提前建库
  table-prefix: ""
  flags:                    # 额外连接参数
    useSSL: false
    serverTimezone: Asia/Shanghai
    characterEncoding: utf8
    allowPublicKeyRetrieval: true
  pool:                     # HikariCP 连接池
    maximum-pool-size: 10
    minimum-idle: 2
    connection-timeout: 30000
    max-lifetime: 1800000
```

## 数据表

| 表 | 内容 |
|---|---|
| `indra_player` | 玩家数据：uuid、name、level、exp、expTotal、points、killCount、deathCount、coins、firstJoin、lastSeen |
| `indra_sell_log` | 出售交易日志 |
| 封禁相关表 | 封禁 / 警告 / 历史记录（与玩家数据同库） |

## 多服共享

多个服务器把 `datasource.yml` 指向同一个 MySQL 数据库即可共享玩家数据与封禁记录。切换后历史数据如需迁移，SQLite 的 `data.db` 可用任意 SQLite 工具导出后再导入 MySQL。
