# Xaero 世界地图圈地 — 架构与协议契约

> **进度**：Phase 0（契约）✅ · Phase 1（服务端 MapClaimChannel）✅ · Phase 1.5（命令解锁实时刷新）✅ ·
> Phase 2（客户端 mod：highlighter + 协议 + payload）✅ 编译通过 ·
> Phase 3（地图点击输入：Shift+左键读 GuiMap.mouseBlockPosX/Z 反射 → sendClaim）✅ 实现 ·
> **全功能就绪，待服主端实测**（混合端插件消息直达客户端 payload + Xaero 反射字段 + ScreenEvent 签名）·
> 打磨待做：颜色 config 下发 / unclaim / 节流 / PLOTS 扩到空闲格。

> 方向（2026-06-21 定）：**自带配套客户端 mod**。Xaero 世界地图的 highlight API 是<b>纯客户端</b>
> （`AbstractHighlighter.getChunkHighlitColor()` 返回地图 GUI 上色，客户端调用），服务端插件画不了。
> 故 GuildShelter 出一个轻量 NeoForge **客户端 mod**：注册 Xaero highlighter 画公会领地 + 经自定义载荷
> 从服务端收地皮数据 + 在地图上点击圈地（请求回服务端走 `/gs unlock` 等价逻辑）。

## 0. 已验证事实（jar 反编译）

- `xaero.map.highlight.HighlighterRegistry.register(AbstractHighlighter)` — 客户端注册入口。
- `AbstractHighlighter`（客户端抽象类，需实现）：
  - `int[] getChunkHighlitColor(ResourceKey<Level> dim, int chunkX, int chunkZ)` — 该 chunk 的 ARGB 颜色。
  - `boolean chunkIsHighlit(dim, cx, cz)` / `regionHasHighlights(dim, regionX, regionZ)` / `calculateRegionHash(...)`（脏检测/缓存）。
  - `Component getBlockHighlightBluntTooltip(dim, cx, cz)` / `...Subtle...` / `addMinimapBlockHighlightTooltips(...)` — 悬停 tooltip。
- `xaero.map.mods.pac.SupportOpenPartiesAndClaims` — Xaero **硬编码**检测 OPAC 客户端组件来画其领地；无通用第三方服务端 API。故必须走自己的客户端 mod。
- jar 坐标：本地 `libs/[世界地图]xaeroworldmap-neoforge-26.2-1.41.0.jar`，客户端 mod 侧 `compileOnly`。

## 1. 模块结构

新增 Gradle 子项目（与主插件分离，各自产物）：

```
GuildShelter/                 ← 现有主插件（Bukkit/NeoForge 混合端，服务端）
  settings.gradle             ← 加 include 'xaero-client'
  xaero-client/               ← 新：NeoForge 客户端 mod（仅客户端运行）
    build.gradle              ← ModDevGradle；compileOnly Xaero worldmap jar
    src/main/java/.../client/ GsMapHighlighter / GsMapClaimData / GsMapPayloadClient / GsMapClaimInput
    src/main/resources/       neoforge.mods.toml（dist=CLIENT）
```

- 客户端 mod **不依赖**主插件代码；二者只靠**字节协议**对齐（见 §3）。协议常量两边各存一份（极少、稳定）。
- 客户端 mod 只在客户端加载（`@Mod` + `Dist.CLIENT` 事件）；服主无需在服务端装它，玩家想要地图圈地才装。

## 2. 通信通道

通道 id：`guildshelter:map`（命名空间载荷）。Bukkit 插件消息 = 自定义载荷包，NeoForge 客户端 mod 注册同名
`CustomPacketPayload` 处理器即可互通。

- **服务端**（主插件，Bukkit 侧）：`getServer().getMessenger().registerOutgoingPluginChannel/IncomingPluginChannel`
  + `PluginMessageListener`。出站 `player.sendPluginMessage(plugin, "guildshelter:map", bytes)`。
- **客户端**（mod）：注册 `guildshelter:map` 的 payload 编解码 + 接收处理；发包用 NeoForge 的
  `PacketDistributor.sendToServer(payload)`（到达服务端即触发 Bukkit IncomingPluginChannel）。

## 3. 字节协议（v1，DataInput/Output 大端）

每包首字节 = type。

- **S→C `0x01 PLOTS`**（玩家进公会世界/地皮变更时下发整张图）：
  ```
  byte  type=1
  utf   dimensionId           // guild_xxx
  int   originChunkX, originChunkZ
  int   count
  count× { int chunkX; int chunkZ; int argb; byte kind; utf label }
      kind: 0=主城 1=自己庄园(已解锁) 2=自己庄园(预留未解锁) 3=他人庄园 4=空闲可认领 5=路
  ```
  客户端据此填充 `GsMapClaimData`（dim → Map<chunkLong, Entry>），highlighter 直接查。
- **S→C `0x02 CLEAR`**：`byte type=2; utf dim` — 离开世界清空该维度高亮。
- **C→S `0x10 CLAIM`**：`byte type=16; int chunkX; int chunkZ` — 请求解锁/认领点击的 chunk。
- **C→S `0x11 UNCLAIM`**：`byte type=17; int chunkX; int chunkZ` — 请求释放（可选，v1.1）。
- **S→C `0x20 RESULT`**：`byte type=32; byte ok; utf messageKey` — 圈地结果（客户端弹 actionbar/toast）。

> 颜色 argb 由服务端定（复用 GridAsciiMap 的语义色），客户端只渲染，便于服主调色不重编客户端。

## 4. 服务端实现（主插件，本模块内，Phase 1 可独立写）

- `adapter/bukkit/map/MapClaimChannel`（`PluginMessageListener`）：
  - `register(plugin)`：注册 in/out 通道。
  - 出站 `sendPlots(Player, GuildWorld)`：用 `LayoutCalculator.classify` 遍历当前**边界内**网格 chunk（按
    `borderRingCells` 限定范围，避免无限），逐 chunk 定 kind+argb+label，写 PLOTS 包。复用 `ManorLookup`/
    `WorldCache` 判归属与解锁状态。
  - 入站 `onPluginMessageReceived`：解析 CLAIM → 校验（玩家在该公会世界、classify 命中自己庄园的预留 chunk、
    相邻已解锁、有额度）→ 调 `GuildService.unlockChunk` 等价路径 → 回 RESULT 包 + 重发 PLOTS。
  - 主城 chunk 的 CLAIM → 走 `unlockCityChunk`（会长/副会长 + 城额度）。
- 触发点：`PlayerChangedWorldEvent`/`PlayerJoinEvent`（进公会世界发 PLOTS）、`unlock`/`assignManor`/`upgrade`
  后给该公会在线成员重发（复用 [[guildshelter-camp-spawn-and-help]] 同款"会内在线广播"）。
- 频率/体量：PLOTS 包只在进世界/变更时发，非每 tick；chunk 数受世界边界封顶；可加 2s 合并节流。

## 5. 客户端 mod 实现（Phase 2，需新模块构建）

- `GsMapPayloadClient`：注册 `guildshelter:map` 载荷编解码 + 接收 → 填 `GsMapClaimData`，标记 Xaero 区域脏
  （`RegionHighlightExistenceTracker`/highlighter 的 region hash 变化触发重画）。
- `GsMapHighlighter extends AbstractHighlighter`：`chunkIsHighlit`/`getChunkHighlitColor` 查 `GsMapClaimData`；
  `getBlockHighlight*Tooltip` 返回 label（"公会主城"/"庄园#3 已解锁"/"空闲 点击认领"）。启动时
  `HighlighterRegistry.register(new GsMapHighlighter())`（在 Xaero 加载后，`@Mod` 客户端 setup）。
- `GsMapClaimInput`：地图打开时监听点击（Xaero 的地图屏幕/快捷键；或一个"圈地模式"开关键）。命中 chunk →
  发 CLAIM 包。收 RESULT → actionbar 提示。
- mods.toml：`dependencies` 声明 `xaero_worldmap`（after/optional）+ `dist=CLIENT`。

## 6. 与现有圈地模型的关系（关键：不引入第二真相源）

- **服务端 GuildShelter 仍是唯一真相源**（slot/unlockedChunks/quota/layout）。地图只是它的**可视化 + 触发 UI**。
- 客户端点击 ≠ 直接圈地，只是**请求**；服务端按既有规则（归属/相邻/额度/会长权限）裁决，拒绝则回 RESULT 报错。
- 不双向同步 OPAC，不和 OPAC 抢 chunk 归属（本方案根本不装 OPAC）。
- 离线/没装客户端 mod 的玩家：完全不受影响（服务端照常 /gs unlock；只是看不到地图高亮）。

## 7. 分阶段

0. **本契约定稿**（本文件）。
1. **Phase 1 服务端**（主模块内、可编译）：MapClaimChannel + PLOTS 出站 + CLAIM 入站裁决 + 触发点。
   即便客户端 mod 未就绪也能先合入（无监听端时静默，无副作用）。
2. **Phase 2 客户端 mod 模块**：settings.gradle include + xaero-client build.gradle + highlighter + payload +
   点击输入。需服主端验证构建（ModDevGradle 客户端 run）。
3. **Phase 3 打磨**：圈地模式快捷键、颜色 config 化下发、unclaim、tooltip i18n、节流。

## 8. 风险 / 注意

- Xaero `AbstractHighlighter` 跨版本可能改签名（本 jar=1.41.0/MC26.2）；客户端 mod compileOnly 锁该 jar，升级 Xaero 需复验。
- 混合端 Youer 上 Bukkit 插件消息能否直达 NeoForge 客户端载荷处理器 = **编译+实测验证点**（理论通：插件消息就是 custom payload）。
- 客户端 mod 是**额外产物**，发布/更新独立于主插件；协议加字段要前后兼容（type+长度前缀，未知 type 跳过）。
- 地图可视范围可能很大 → PLOTS 只发世界边界内网格，超大公会再加分页/按区域懒发（Phase 3）。
