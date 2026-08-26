# KineticRecipe

[简体中文](#简体中文) | [English](#english)

## 简体中文

### 模组定位

**KineticRecipe** 是 Kinetic 系列的服务器内存配方编辑模块。当前版本不依赖 KubeJS 脚本，也不生成虚拟数据包；配方和移除规则统一保存在 `recipes.json`，然后直接写入服务器当前运行中的 `RecipeManager`。

这意味着整合包作者可以在游戏内创建、修改、预览和移除配方，保存后由服务器重新组装活动配方列表并同步给在线玩家。

### 主要功能

- **配方中枢**：从一个统一界面进入不同类型的配方编辑器、预览界面和移除管理器。
- **工作台配方**：支持有序 `shaped` 与无序 `shapeless` 配方。
- **熔炉配方**：支持普通熔炉配方。
- **高炉配方**：支持高炉配方。
- **烟熏炉配方**：支持烟熏炉配方。
- **锻造台配方**：支持模板、底材和附加物的锻造转换配方。
- **切石机配方**：支持单输入切石配方。
- **Ghost Slot 可视化编辑**：使用真实物品图标配置输入与输出，不需要直接拼 JSON。
- **标签材料**：输入可以使用 `#item_tag` 作为 Ingredient。
- **NBT 匹配模式**：支持普通匹配、部分 NBT 匹配和严格 NBT 匹配。
- **输出 NBT / 数量**：可保存带数量和自定义 NBT 的输出数据。
- **配方预览**：读取当前服务器配方列表，帮助作者确认现有配方和来源。
- **五种移除模式**：可按 **来源模组 / Recipe ID / 输出物 / 物品标签 / 配方类型** 删除基线配方。
- **内存级替换**：服务器启动或数据包重载时先记录基线配方，再叠加移除规则与自定义配方。
- **即时同步玩家**：保存应用后发送新的配方列表给在线玩家。
- **保留无法识别的配置条目**：读取配置时无法解析的配方不会被随意覆盖丢失，便于后续修复。

### 配置文件

```text
config/kineticcore/recipes.json
```

文件主要包含：

- `recipes`：KineticRecipe 创建的自定义配方。
- `removals`：配方移除规则。

### 当前实现说明

当前版本采用的是 **Server RecipeManager 内存替换** 方案：

- 不要求 KubeJS。
- 不生成 `recipes_adds.js`。
- 不生成用于运行时覆盖的虚拟数据包。
- 保存后由服务器直接重新组装并替换活动配方集合。

### 运行环境

- Minecraft 1.20.1
- Minecraft Forge 47.4.2
- Java 17
- KineticCore：必须

## English

### Overview

**KineticRecipe** is the server-memory recipe editor for the Kinetic family. The current implementation does not require KubeJS and does not generate a runtime datapack. Custom recipes and removal rules are stored in `recipes.json` and applied directly to the active server `RecipeManager`.

### Key Features

- Unified recipe hub.
- Shaped and shapeless crafting recipes.
- Furnace, blast furnace and smoker recipes.
- Smithing transform recipes.
- Stonecutter recipes.
- Ghost-slot visual editing.
- Item-tag ingredients.
- Normal, partial-NBT and strict-NBT ingredient matching.
- Configurable output count and NBT.
- Server recipe preview.
- Removal rules by mod, recipe ID, output item, tag or recipe type.
- Baseline capture plus in-memory recipe replacement.
- Live recipe synchronization to online players.
- Preservation of unresolved config entries instead of silently deleting them.

### Configuration

```text
config/kineticcore/recipes.json
```

### Requirements

- Minecraft 1.20.1
- Minecraft Forge 47.4.2
- Java 17
- KineticCore: required

## 开源协议与版权 (License)

Copyright (C) 2024-2026 XYAT.

本项目基于 **GNU Lesser General Public License v3.0 (LGPLv3)** 协议开源。

This project is open-sourced under the **GNU Lesser General Public License v3.0 (LGPLv3)**.
