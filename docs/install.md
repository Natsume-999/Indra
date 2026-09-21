---
title: 安装与快速上手
layout: default
permalink: /install/
---

[返回首页](index.md)

# 安装与快速上手

## 环境要求

| 项目 | 要求 |
|---|---|
| 服务端 | **Paper 26.3**（Build 19） |
| Java | **25**（Paper 26.1+ 强制要求，不可用 17） |
| 网络 | **首次启动需联网**（运行期自动下载 TabooLib 模块与 Kotlin，已内置阿里云镜像） |
| 可选 | Vault + 经济插件（出售系统金币结算需要） |

## 安装步骤

1. 下载 `Indra-1.6.0.jar`：[Release v1.6.0](https://github.com/Natsume-999/Indra/releases/latest)
2. 放入服务器 `plugins/` 目录
3. 启动服务器，首次启动会自动下载运行依赖并生成配置文件
4. 修改 `plugins/Indra/config.yml` 后执行 `/indra reload` 生效

## 验证安装成功

启动日志出现以下内容即为正常：

```text
[Indra] §b[Indra] §f正在加载 RPG 基础模块...
[Indra] §b[Indra] §f启动自检：
[Indra]   config.yml     = 已生成 (注解管线正常)
[Indra] [Indra] §a启用完成，共加载 §e8 §a个模块。
```

## 首次启动生成的文件

| 文件 | 用途 | 文档 |
|---|---|---|
| `config.yml` | 主配置（玩法数值） | [配置参考](config.md) |
| `datasource.yml` | 数据源（SQLite / MySQL 切换） | [存储切换](storage.md) |
| `ban.yml` / `ban-messages.yml` | 封禁模块 | [封禁系统](ban.md) |
| `sell.yml` / `sell/*.yml` / `table/*.yml` | 出售模块 | [出售系统](sell.md) |
| `menu.yml` | 面板外观 | [面板](menu.md) |
| `kether.yml` | TabooLib Kether（框架自带） | — |
| `data.db` | 本地 SQLite 数据库 | [存储切换](storage.md) |

## 常见问题

**Q：启动时报「当前 Minecraft 版本不受支持」？**
TabooLib 运行模块的版本白名单表里没有你的 MC 版本。需要 `6.3.0-f9483b9` 以上（本仓库构建已内置），老版本插件的该警告属于上游版本表覆盖问题。

**Q：首次启动没联网会怎样？**
运行期找不到 Kotlin 环境而禁用插件。离线部署需手动准备运行依赖，不建议。

**Q：想搬数据到 MySQL？**
见 [存储切换](storage.md)——两种模式表结构完全一致，改配置即可。
