# GuildShelter 模块化拆分 — 架构契约

> 决策（2026-06-21 定）：4 模块 `core + bukkit-common + bukkit + neoforge_26_2`（+ 既有 `xaero-client`）。
> 现在就拆。本文件是拆分的单一真相源；执行按 §6 阶段走。

## 1. 为什么这样分

三个载体（普通版、`neoforge_26_2`、将来 `forge_1_18_2`）**本质都是 Bukkit 插件**：从
`plugins/` 加载、以 Bukkit API 为主。区别只是增强版**额外**够得着同 JVM 里的 NeoForge/Forge 原生
API（混合端 Youer/Mohist）。所以这不是"Bukkit 平台 vs NeoForge 平台"，而是"**纯 Bukkit vs
Bukkit+混合端原生**"。故只需 `core`(纯) + `bukkit-common`(所有载体共享的 Bukkit 胶水)。

**拆分收益**：
1. **编译期强制**："Bukkit 入口禁止静态引用 NeoForge"从靠自觉变成结构性——`bukkit-common` 的
   classpath 里根本没有 `net.neoforged`，写了就编不过。
2. **增强版丢掉反射**：`neoforge_26_2` jar 只部署在混合端，可直接 `new NeoForgeXxx()`，不再
   `isNeoForgePresent()` 那套脆弱反射门控。
3. **`forge_1_18_2` 将来照抄一个薄模块**，载体对称。

## 2. 模块与依赖

| 模块 | 装什么 | 依赖 | 产物 |
|---|---|---|---|
| `core` | domain · service · persistence | 纯 Java（JDBC/Hikari compileOnly） | 库 |
| `bukkit-common` | adapter/** · events · 接缝接口 + Bukkit 默认实现 · 抽象 `GuildShelterPlugin` 引导 · 共享资源 | `api project(':core')`；compileOnly spigot/WE-bukkit/RS2/PAPI/Vault/DH/libs；**禁 net.neoforged** | 库 |
| `bukkit`(普通版) | `GuildShelterBukkitPlugin` + plugin.yml | `implementation project(':bukkit-common')`；compileOnly spigot | `guildshelter-bukkit-<ver>.jar`（shadow） |
| `neoforge_26_2`(增强版) | `neoforge/**`(8 类) + `GuildShelterNeoForgePlugin` + plugin.yml | `implementation project(':bukkit-common')`；NeoGradle neoforge 26.2；compileOnly WE-mod/RS2 | `guildshelter-neoforge-26.2-<ver>.jar`（shadow） |
| `forge_1_18_2`(将来) | 同形，1.18.2 依赖+映射 | `project(':bukkit-common')` + forge 1.18.2 | 后续 |
| `xaero-client` | 不动（独立客户端 mod，只靠 `guildshelter:map` 字节协议） | — | 客户端 mod jar |

**打包**：core/bukkit-common 是库；两个载体用 shadow，`include project(':core')`+`project(':bukkit-common')`
把兄弟模块的类与资源并进 jar，其余依赖（spigot 由服务端、neoforge 由混合端、WE/RS2/PAPI/Vault/DH 由
服务端插件/mod、Hikari/sqlite/mysql 由 plugin.yml `libraries`）全运行期提供，**不入 jar**。

## 3. 主类拆法（零引用改动的关键）

`org.windy.guildshelter.GuildShelterPlugin` **保留包名+类名**，改成 **`abstract`** 放 `bukkit-common`：
- 持有全部 `onEnable` 装配逻辑 + 全部静态访问器（`get()`/`protectionGuard()`/`manorLookup()`/…）。
- 平台决策点改调 `bindings()`（见 §4）。
- 新增 `protected abstract PlatformBindings createBindings();`

两个载体各一个薄子类（仅覆盖工厂）：
- `bukkit`：`org.windy.guildshelter.bukkit.GuildShelterBukkitPlugin extends GuildShelterPlugin`
  → `createBindings() = new BukkitPlatformBindings()`
- `neoforge_26_2`：`org.windy.guildshelter.neoforge.GuildShelterNeoForgePlugin extends GuildShelterPlugin`
  → `createBindings() = new NeoForge262Bindings()`

plugin.yml `main:` 各指向自己的子类。因 `GuildShelterPlugin` 包名类名不变，adapter/**、neoforge/**
里所有 `GuildShelterPlugin.xxx()` 静态引用**无需改动**。

## 4. 接缝 PlatformBindings（bukkit-common，触 org.bukkit 故不在纯 core）

> 以下为**实际落地**签名（与计划稿不同：去掉了 `schematicStore`——见 §6.5；终地/搬家传打散的
> 原始参数而非 config record；保护方法名 `registerNativeProtection`；新增 `registerModDataMovers`）。

```java
package org.windy.guildshelter.platform;
public interface PlatformBindings {
    boolean isHybrid();                                              // UI auto / 日志
    TerrainPreparer terrain(JavaPlugin p, String roadBlock, String bridgeBlock, String bridgeRail,
                            boolean wallEnabled, String wallBlock, int wallHeight);
    ManorMover manorMover(JavaPlugin p);                            // 取 p.getLogger()
    void registerModDataMovers(ModDataMoverRegistry reg, JavaPlugin p); // 混合端注册 RS2;bukkit 空操作
    boolean registerNativeProtection(JavaPlugin p);                 // true=已注册原生→引导跳过 Bukkit 3 监听
    WaterBiomeSampler biomeSampler();                               // null → WorldManager 走高度图兜底
}
```

- `BukkitPlatformBindings`（bukkit-common）：isHybrid=false；terrain→`BukkitTerrainPreparer`；
  manorMover→`BukkitManorMover`；registerModDataMovers→空；registerNativeProtection→false；biomeSampler→null。
- `NeoForge262Bindings`（neoforge_26_2）：isHybrid=true；terrain→`NeoForgeTerrainPreparer`；
  manorMover→`NeoForgeManorMover`；registerModDataMovers→`ModList.isLoaded("refinedstorage")` 时注册
  `RefinedStorageDataMover`；registerNativeProtection→`NeoForgeHooks.register()` 后 true；
  biomeSampler→`NeoForgeWaterBiomeSampler`（包装静态 `NeoForgeBiomeSampler`）。
- `schematicStore` 两载体通用，不走接缝：`GuildShelterPlugin` 直接调 `SchematicStores.autoDetect`
  （内部 `Class.forName` 探 FML，纯 Bukkit 探不到自然回退 FAWE/WE）。

## 5. core 纯净度泄漏（拆时处理）

- `domain/port/SchematicStore.autoDetect(...)`：带 `org.bukkit.plugin.Plugin` 参 + `new
  NeoForgeSchematicStoreAdapter` + 反射 we/fawe。**接口 `SchematicStore` 留 core**；`autoDetect` 工厂
  逻辑搬到 `bukkit-common` 的 `SchematicStores`（we/fawe 分支）+ neoforge 分支进 `NeoForge262Bindings`。
- `adapter/bukkit/world/WorldManager` 直引 `neoforge.NeoForgeBiomeSampler`（FQN 静态调用）：抽
  `WaterBiomeSampler` 接缝（bukkit-common），WorldManager 持可空引用，高度图兜底；neoforge 侧注入。
- UI port 注释里的 `org.bukkit.Material/Player` 只是 javadoc，非代码，不动。

## 6. 执行阶段（每阶段尽量自洽，末尾交用户编译）

0. 本契约 + Gradle 骨架（settings/gradle.properties/root/4×build.gradle）。
1. `git mv` 搬包：core←domain/service/persistence；bukkit-common←adapter/events/api/hook；
   neoforge_26_2←neoforge；共享资源←bukkit-common；plugin.yml 拆两份。
2. 接缝重构：抽 `PlatformBindings`+两实现；`GuildShelterPlugin` 改抽象+调 bindings；两载体薄子类；
   `SchematicStore.autoDetect`→`SchematicStores`；`WorldManager` 抽 `WaterBiomeSampler`。
3. 交用户编译，集中修跨模块可见性/import 残留。

## 6.5 执行状态（2026-06-21）

**已完成（待编译验证）**：
- Phase 0：settings.gradle（5 模块）+ 根父 build.gradle（subprojects 公共配置）+ 4 模块 build.gradle。
- Phase 1：`git mv` 全量搬家（包名不变、import 零改）。途中清掉 7 个幽灵 index 条目（旧死岛6类 GUI +
  LimitRules）、反暂存误入的 `xaero-client/build/`、`.gitignore` 改 `build/` 忽略所有模块构建产物。
- Phase 2 接缝：
  - `SchematicStore` 接口留 core；工厂 `autoDetect` + 反射适配器 → bukkit-common `SchematicStores`。
  - `WorldManager` 抽 `WaterBiomeSampler` 接缝（可空，混合端注入 `NeoForgeWaterBiomeSampler` 包装静态采样）。
  - `PlatformBindings` 接缝 + `BukkitPlatformBindings`(bukkit-common) + `NeoForge262Bindings`(neoforge_26_2)。
  - `GuildShelterPlugin` 改 `abstract`，全部平台分流走 bindings；删 `isNeoForgePresent()/isModLoaded()`。
  - 两载体薄入口：`bukkit.GuildShelterBukkitPlugin` / `neoforge.GuildShelterNeoForgePlugin`；plugin.yml 各一份。
  - RS2 `RefinedStorageDataMover` 归 neoforge_26_2（保留原包名），由 `NeoForge262Bindings` 用 `ModList` 门控注册。

**实测修正的契约**：`schematicStore` 不需接缝方法——`SchematicStores.autoDetect` 内部已用 `Class.forName`
反射探测 FML，两载体通用（纯 Bukkit 探不到 FML 自然回退 FAWE/WE）。故 §4 的 `schematicStore(...)` 未纳入接缝。

**纯净度审计通过**：core 无 org.bukkit/net.neoforged（仅注释提及）；bukkit-common 无 `import net.neoforged`。

## 7. 风险

- **NeoGradle + shadow 同模块**（neoforge_26_2）：neoforge 不能被 shade（混合端已提供），用 shadow
  `include` 白名单只并 `core`+`bukkit-common`。本机无法跑 gradle，此处最可能需用户迭代。
- ModDevGradle 的 `runs{}` 对 Bukkit 插件载体无意义，已删；保留 neoforge 依赖供编译。
- 共享资源经 shadow 进载体 jar，`saveDefaultConfig()`/`saveResource()` 才读得到——验证 config.yml
  在载体 jar 根。
- **shadow 版本**：Gradle 实测 9.4.1，shadow 已用 `com.gradleup.shadow:9.0.0`（8.3.x 仅 Gradle 8）。
  若 9.0.0 坐标/兼容性有问题，按 GradleUp 实际可用版调整。
- **mods.toml 模板**：`neoforge_26_2/src/main/templates/META-INF/neoforge.mods.toml` 是 NeoGradle 约定的
  @Mod 元数据（Guildshelter.java 桩永不被 FML 加载，此 jar 是 Bukkit 插件）。NeoGradle userdev 可能要求
  其占位（${neo_version_range} 等）由 gradle.properties 注入——若构建报缺占位，按需在 neoforge_26_2 的
  ProcessResources 补 expand 或直接删此桩（它对 Bukkit 加载无用）。
- **首次编译预期残留**：未用 import 警告（如 bukkit-common GuildShelterPlugin 的 BukkitTerrainPreparer）；
  跨模块若有漏改的符号，集中在用户编译报错后一并修（[[user-handles-builds]]）。
