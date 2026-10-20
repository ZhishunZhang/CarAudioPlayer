# Bug 分析报告

> 项目：CarAudioPlayer（车载风格在线音乐播放器）
> 说明：本报告集记录实习期间实际排查并修复的典型 Bug，每份均包含完整排查流程、根因分析、修复方案与验证结果。

---

## Bug-01：巅峰榜·热歌 每次打开必超时

### 一、问题描述

**现象**：点击「巅峰榜·热歌」进入榜单详情页，列表长时间（超过 10 秒）无数据，最终提示获取失败。其他榜单（如流行指数榜）时好时坏。该问题 100% 复现，且只在「热歌」这一个榜单稳定出现。

**影响范围**：榜单详情页（热歌榜），属核心浏览路径，用户几乎每次进入都会遇到。

**环境**：Android 客户端（Java）+ Node 中转服务（localhost:3200）+ QQ 音乐上游。

### 二、排查流程

**Step 1：先分清是客户端问题还是服务端问题**

不改代码，直接用 curl 实测中转服务接口，分别测量两个参数的耗时：

```
curl "http://127.0.0.1:3200/getRanks?topId=26&limit=30&resolveMid=true"   → 28.4s（HTTP 200 正常返回）
curl "http://127.0.0.1:3200/getRanks?topId=26&limit=30"                   → 0.2s
```

结论：**接口本身能返回正确数据（HTTP 200），只是耗时 28.4 秒**——问题不在"失败"，而在"慢"。

**Step 2：对比其他榜单定位差异**

```
topId=4（流行指数）+ resolveMid=true  → 7.3s（能过 8s 超时线，所以"时好时坏"）
topId=26（热歌）    + resolveMid=true  → 28.4s（必超时）
```

差异锁定在 `resolveMid=true` 这个参数——它会让服务端为每首歌额外解析 songmid。

**Step 3：读服务端源码确认慢的机制**

阅读 `qq-music-api/src/controllers/getRanks.ts`，确认 `resolveMid=true` 的执行逻辑：

- 榜单原始接口只返回数字 `songId`，不返回字符串 `mid`
- 服务端用 worker 池（并发 5）为**每一首**歌单独调用一次 QQ 上游详情接口换取 mid
- 30 首歌 ÷ 5 并发 ≈ 6 轮，每轮约 4-5s，总计约 28s

**Step 4：确认客户端为何"必超时"**

阅读客户端 `QQMusicApi.httpGet()`：`setReadTimeout(8000)`——客户端只肯等 8 秒，服务端需要 28 秒，数学上必然超时。

**Step 5：交叉验证数据能力（能否不解析 mid）**

实测 `resolveMid=false` 的返回体，确认每首歌已带有：`songId / title / singerName / singerMid / albumMid / cover`——**列表渲染所需的全部字段都有，唯独没有 mid**。

### 三、根因分析

**直接根因**：服务端 `resolveMid=true` 要为 30 首歌逐首请求 QQ 上游换取 mid（串行批次，总耗时 28.4s），超过客户端 8s 读超时。

**深层根因**：

1. **数据获取策略错误**：列表展示其实不需要 mid（封面用 albumMid 拼、歌名歌手都有），mid 只在"播放"时才需要。原设计一次性为全列表解析 mid，把"按需"做成了"全量预热"。
2. **服务端无映射缓存**：`songId → songmid` 是**永久不变**的映射（歌曲身份证），服务端却每次重新解析。
3. **客户端超时策略一刀切**：8s 对所有接口统一，未按接口分级。

### 四、修复方案（两层结合）

**第一层：服务端根治（治本）** — 改 `qq-music-api/src/controllers/getRanks.ts`

1. 新增 `midCache`（进程内存 Map）：`songId → songmid` 永久映射缓存，解析过一次的歌曲不再请求上游
2. worker 池并发 5 → 15，降低首批解析耗时

```typescript
const MID_RESOLVE_CONCURRENCY = 15;
const midCache = new Map<number, string>();   // songId → songmid 永久映射

// worker 内先查缓存，命中则跳过上游请求
const cached = midCache.get(songId);
if (cached) { midBySongId.set(songId, cached); continue; }
const mid = await fetchSongMidBySongId(songId);
if (mid) { midCache.set(songId, mid); midBySongId.set(songId, mid); }
```

**第二层：客户端两段式（治标且改善首次体验）** — 改 `QQMusicApi.ranks()` + `PlaybackManager`

1. 榜单列表请求**去掉 `resolveMid=true`**（0.2s 秒回，字段足够渲染列表）
2. `OnlineSong` 增加 `songId` 字段，mid 允许初始为空
3. 播放那一刻才按需解析：`playAt()` 发现 mid 为空 → `resolveMid(songId)` → 拿到 mid 再取播放地址

```java
// PlaybackManager.playAt 片段
if (song.mid == null || song.mid.isEmpty()) {
    QQMusicApi.resolveMid(song.songId, mid -> {   // 只解析要点的那一首
        song.mid = mid;
        fetchPlayUrl(gen, song);
    });
    return;
}
```

### 五、验证结果

| 场景                          | 修复前          | 修复后                   |
| --------------------------- | ------------ | --------------------- |
| 服务端 30 首 resolveMid（首次/冷缓存） | 28.4s        | 7.6s（实际页面显示很快，且不影响播放） |
| 服务端 30 首 resolveMid（缓存热）    | 28.4s        | **0.14s**             |
| **客户端打开榜单（任何情况）**           | **必超时（>8s）** | **0.2s 秒开**           |
| 点榜内歌曲播放（未播过）                | 无法播放         | 约 0.3s 起播（单首解析）       |
| 点播过的歌（切回）                   | —            | 0ms 直接播（playUrl 内存缓存） |

验证方式：curl 实测服务端耗时 + 平板装机走查（打开各榜单均秒出列表，点击可播放，切歌正常）。

### 六、经验总结

1. **先测量再定位**：用 curl 直接对比有无参数的耗时差异，10 分钟就锁定了"resolveMid"这个变量，避免了在客户端盲目改超时时间。
2. **分清"展示数据"和"播放数据"**：列表渲染不需要 mid，按需解析比全量预热更合理。
3. **缓存永久不变的数据**：songId→mid 是永久映射，值得长期缓存；而播放 URL（vkey）会过期，只做短期内存缓存——数据分类决定缓存策略。
4. **并发不是万能药**：并发 5→15 只把 28s 降到 7.6s（仍有上限），真正的数量级改善来自"根本不再请求"（缓存 + 按需）。

---

## Bug-02：大量歌曲点击后无任何反应（无法播放）

### 一、问题描述

**现象**：在多个榜单（动漫音乐榜、国风热歌榜等）点击歌曲，**没有任何反馈**——不播放、不报错、不跳转，界面如同卡死。部分榜单（热歌榜）之前是正常的，容易误判为"这些歌是 VIP 不能播"。

**影响范围**：榜单详情页点击播放（核心播放入口），涉及多首歌曲、多个榜单，非个例。

**环境**：Android 客户端 + Node 中转服务。

### 二、排查流程

**Step 1：确认"无反应"的具体环节**

在客户端加日志 + 服务端日志双管齐下，发现点击后请求链路是：`resolveMid(songId)` 返回失败 → 后续流程中断。即问题在"播放前解析 mid"这一步。

**Step 2：直接实测中转接口**

```
curl "http://127.0.0.1:3200/getSongInfo?songid=722302135"
→ {"response":{"code":500001}}                          （错误码，无数据）
curl "http://127.0.0.1:3200/getRanks?topId=65&limit=3&resolveMid=true"
→ 能正常解析出 mid（如 000gRK0i44cAYG）                    （同源数据却成功）
```

关键矛盾出现：**同一首歌的 songId，榜单内部解析成功，单独调 getSongInfo 却失败**。

**Step 3：对比两处源码找差异**

- 榜单内解析（`getRanks.ts` 的 `fetchSongMidBySongId`）：`song_id: songId`，其中 songId 是 **number 类型**
- getSongInfo 控制器：`song_id: ctx.query.songid`，query 参数是**字符串** `"722302135"`

推断：QQ 上游对 `song_id` 类型严格，字符串一律拒绝。为验证推断，将 getSongInfo 的 `song_id` 改为 `Number()` 后实测——解析立即成功。

**Step 4：排查"为什么失败了用户看不到任何提示"**

全工程搜索 `PlaybackManager.error()` 的观察者：**没有任何地方订阅它**。错误信息写了进 LiveData，但无人消费——错误链路是断的。这就是"无反应"的第二层原因（VIP 提示也是这么被吞掉的）。

**Step 5：区分 Bug 与产品限制**

实测失败歌曲与成功歌曲的 `pay` 字段：

```
失败歌曲（茶汤）:   "pay":{"pay_play":1, ...}   → VIP 付费歌曲
成功歌曲（那页夏天）: "pay":{"pay_play":0, ...}   → 免费歌曲
```

证明：部分"无反应"属于 VIP 歌曲的产品限制（取不到播放地址），不是 Bug；但 getSongInfo 类型问题导致**所有歌曲**都无法播放，是真 Bug。

### 三、根因分析

**直接根因（两个叠加）**：

1. `getSongInfo` 控制器把 query 里的字符串 `songid` 原样传给 QQ 上游，上游对类型严格，返回 500001，mid 解析必然失败 → 所有榜单歌曲都无法播放
2. 客户端 `PlaybackManager.error()` 无任何观察者，错误提示（含 VIP 提示）被静默吞掉 → 用户感知为"无反应"

**深层根因**：

1. **类型安全意识缺失**：HTTP query 参数天然是字符串，涉及数值型上游参数时必须显式转换
2. **错误链路未闭环**：只写了错误状态，没有终端消费方；错误处理和成功处理不对称

### 四、修复方案

**修复 1：服务端类型转换**（`qq-music-api/src/controllers/getSongInfo.ts`）

```typescript
// 修复前：song_id 为字符串，上游返回 500001
const song_id = ctx.query.songid || '';
// 修复后：显式转数字，与榜单内解析逻辑对齐
const song_id = ctx.query.songid ? Number(ctx.query.songid) : 0;
```

**修复 2：错误链路闭环**（`MainActivity` 观察 `playback.error()` 弹 Toast）

```java
playback.error().observe(this, msg -> {
    if (msg != null && !msg.isEmpty()) showToast(msg);
});
```

（注：Toast 提示在验证期帮助定位，后期按产品需求移除提示，但保留"自动跳下一首"的兜底逻辑不变。）

**配套：VIP 标识**（利用 Step 5 发现的 pay 字段）

`parseSong` 提取 `pay.pay_play/payplay` → `OnlineSong.payPlay` → 列表行显示金色 VIP 角标，让用户在点击前就知道哪些歌受会员限制。

### 五、验证结果

| 验证项                               | 修复前       | 修复后                                                   |
| --------------------------------- | --------- | ----------------------------------------------------- |
| getSongInfo?songid=722302135（国风榜） | 500001 错误 | 正常返回 mid                                              |
| 四大榜单（热歌/国风/动漫/流行）点击播放             | 全部无反应     | 免费歌曲全部可播，VIP 歌曲自动跳下一首                                 |
| 抽样可播比例（各榜前 4 首）                   | 0/4       | 热歌 1/4、国风 3/4、动漫 1/4、流行 3/4（与 pay_play 字段完全吻合，证实修复彻底） |
| 错误可见性                             | 完全静默      | 验证期可见明确 Toast（后按需求移除）                                 |

验证方式：curl 逐榜单实测 + 平板装机走查全部榜单点击播放 + pay 字段交叉验证。

### 六、经验总结

1. **"同样的数据一个接口成功一个失败"是强信号**：立即对比两处代码的参数构造，差异往往就是根因（本次是 number vs string）。
2. **HTTP 参数永远是字符串**：涉及数值型上游字段必须显式转换，这是跨服务联调的高频坑。
3. **错误链路必须闭环**：每个错误状态都要有终端消费方（Toast/日志/降级 UI），否则等于没写错误处理。
4. **用数据区分 Bug 和产品限制**：pay 字段一测，"VIP 不能播"和"全都不能播"两种性质完全不同的问题就分清了，避免把产品限制当 Bug 修。

---

## Bug-03：搜索联想列表不显示封面

### 一、问题描述

**现象**：在搜索页输入关键词（如"周杰伦"），下方联想列表中的歌曲**全部没有封面**（显示占位图），而进入搜索结果页后封面正常。点击联想项可以播放，仅缺封面。

**影响范围**：搜索页联想列表（输入态），影响视觉完整性和信息密度。

**环境**：Android 客户端 + Node 中转服务。

### 二、排查流程

**Step 1：确认数据源**

阅读 `SearchFragment`/`SearchInputViewModel`：联想列表数据来自 `QQMusicApi.smartbox(key)`，即中转的 `GET /getSmartbox` 接口。

**Step 2：实测联想接口返回的字段**

```
curl "http://127.0.0.1:3200/getSmartbox?key=晴天"
→ song.itemlist[] 中每首歌只有：id / mid / name / singer
  没有 albummid，没有封面相关字段
```

**Step 3：对比搜索接口（已知有封面的链路）**

```
curl "http://127.0.0.1:3200/getSearchByKey?key=周杰伦&limit=2"
→ song.list[] 中每首歌有：albummid / songmid / strMediaMid / singer[] 全字段
```

再验证封面 URL 拼接链路是否可用：

```
curl "https://y.gtimg.cn/music/photo_new/T002R300x300M000000MkMni19ClKG.jpg"
→ HTTP 200，33KB 图片正常
```

结论：**图床和拼接逻辑都没问题，纯粹是 smartbox 接口不返回专辑字段**。

**Step 4：确认客户端解析层无遗漏**

阅读 `QQMusicApi.parseSong`：albummid 的提取逻辑是完善的（兼容 `album.mid / albummid / strMediaMid` 多 key）——排除客户端解析问题，锁定为接口选型错误。

### 三、根因分析

**直接根因**：联想功能选用了 `getSmartbox` 接口，该接口的设计目标是"轻量关键词补全"，歌曲条目只返回 `id/mid/name/singer` 四个字段，**不含专辑 MID**，因此客户端无法拼出封面 URL。

**深层根因**：接口选型时只验证了"能返回歌曲列表"，没有验证"返回字段是否满足 UI 需求"。搜索接口 `getSearchByKey` 返回全字段（含 albummid），且实测耗时仅 0.8s，完全可兼顾联想场景。

### 四、修复方案

**1. 联想数据源切换**：`smartbox` → `searchSongs`（`getSearchByKey`）

```java
// SearchInputViewModel.loadSuggest
public void loadSuggest(String key) {
    int gen = ++suggestGeneration;   // 过期响应丢弃
    QQMusicApi.searchSongs(key, new Callback<>() {
        public void onSuccess(List<OnlineSong> songs) {
            if (gen != suggestGeneration) return;   // 快速输入时旧结果不覆盖新结果
            suggests.setValue(songs);
        }
    });
}
```

**2. 输入防抖**：搜索接口比 smartbox 重（返回 30 条全字段），加 300ms 防抖避免每敲一个字都发请求

```java
debounceHandler.removeCallbacks(pendingSuggest);
pendingSuggest = () -> viewModel.loadSuggest(key);
debounceHandler.postDelayed(pendingSuggest, SUGGEST_DEBOUNCE_MS);
```

**3. 清理死代码**：`smartbox` 封装在 App 端已无调用方，从 `QQMusicApi` 删除。

### 五、验证结果

| 验证项 | 修复前 | 修复后 |
|---|---|---|---|
| 联想列表封面 | 全部占位图 | 封面正常显示 |
| 联想点击播放 | 正常 | 正常（播放链路复用 searchByKey 的 mid，无需额外解析） |
| 快速输入（连续敲键盘） | 无防抖，每字符一发请求 | 300ms 防抖 + 过期响应丢弃，无串图 |
| smartbox 死代码 | 保留 | 已删除 |

验证方式：平板装机输入多组关键词走查 + 快速输入压力测试 + 确认联想项点击播放链路正常。

### 六、经验总结

1. **接口选型要验证"字段完备性"而不只是"能返回"**：UI 需要什么字段，就要先实测接口给不给。
2. **轻量接口有轻量的代价**：smartbox 快但字段少，searchByKey 字段全且实测 0.8s 完全可接受——按 UI 需求选接口，不是按名字选。
3. 换更重的接口时，配套加**防抖 + 过期丢弃**，把接口开销控制在用户无感范围内。
