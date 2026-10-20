// CarAudioPlayer 三阶段学习总结 PPT 生成脚本（pptxgenjs）
// 运行：node gen-ppt.js  输出：../CarAudioPlayer-三阶段学习总结.pptx
const pptxgen = require("pptxgenjs");
const path = require("path");

const IMG = (n) => path.join(__dirname, "..", "docs", "ppt-assets", n);
const OUT = path.join(__dirname, "..", "CarAudioPlayer-三阶段学习总结-v2.pptx");

const pres = new pptxgen();
pres.layout = "LAYOUT_16x9"; // 10 x 5.625 in
pres.author = "CarAudioPlayer";
pres.title = "CarAudioPlayer 三阶段学习总结";

const C = {
  bg: "F7F8FA", card: "FFFFFF", ink: "1F2937", muted: "6B7280", line: "D9DDE3",
  dark: "14263C", darkInk: "E8EDF5", darkMuted: "9FB0C6",
  green: "2F8F6B", greenSoft: "EAF6F0",
  blue: "2E6BD6", blueSoft: "EBF2FC",
  orange: "C05A1E", orangeSoft: "FBF0E7",
  code: "F4F6FA", codeInk: "33415C",
};
const ST = {
  1: { tag: "阶段一", color: C.green, soft: C.greenSoft },
  2: { tag: "阶段二", color: C.blue, soft: C.blueSoft },
  3: { tag: "阶段三", color: C.orange, soft: C.orangeSoft },
  0: { tag: "总结", color: C.dark, soft: "E8EDF5" },
};
const FONT = "Microsoft YaHei";
const MONO = "Consolas";
let pageNo = 0;

function header(s, st, title, sub) {
  s.background = { color: C.bg };
  const m = ST[st];
  s.addShape(pres.ShapeType.roundRect, { x: 0.45, y: 0.3, w: 0.92, h: 0.34, rectRadius: 0.17, fill: { color: m.soft }, line: { color: m.color, width: 1 } });
  s.addText(m.tag, { x: 0.45, y: 0.3, w: 0.92, h: 0.34, align: "center", valign: "middle", fontFace: FONT, fontSize: 11, bold: true, color: m.color });
  s.addText(title, { x: 1.52, y: 0.24, w: 8.1, h: 0.46, fontFace: FONT, fontSize: 21, bold: true, color: C.ink, valign: "middle" });
  if (sub) s.addText(sub, { x: 0.45, y: 0.74, w: 9.1, h: 0.3, fontFace: FONT, fontSize: 10.5, color: C.muted });
  s.addShape(pres.ShapeType.rect, { x: 0.45, y: 1.06, w: 0.55, h: 0.05, fill: { color: m.color }, line: { type: "none" } });
}
function footer(s) {
  pageNo += 1;
  s.addText("CarAudioPlayer 三阶段学习总结", { x: 0.45, y: 5.3, w: 4, h: 0.25, fontFace: FONT, fontSize: 8.5, color: C.muted });
  s.addText(String(pageNo).padStart(2, "0"), { x: 9.1, y: 5.3, w: 0.45, h: 0.25, align: "right", fontFace: FONT, fontSize: 8.5, color: C.muted });
}
function card(s, x, y, w, h, o = {}) {
  s.addShape(pres.ShapeType.roundRect, { x, y, w, h, rectRadius: 0.06, fill: { color: o.fill || C.card }, line: { color: o.line || C.line, width: 1 } });
}
function ctitle(s, x, y, w, text, color, size = 12) {
  s.addText(text, { x, y, w, h: 0.3, fontFace: FONT, fontSize: size, bold: true, color });
}
function bullets(s, x, y, w, h, items, o = {}) {
  const rows = items.map((t) => {
    const sub = t.startsWith("- ");
    const text = sub ? t.slice(2) : t;
    return { text, options: { bullet: { code: sub ? "2013" : "2022", indent: 12 }, breakLine: true, fontSize: o.size || 10, color: o.color || C.ink, fontFace: FONT, paraSpaceAfter: o.gap == null ? 4 : o.gap, indentLevel: sub ? 1 : 0 } };
  });
  s.addText(rows, { x, y, w, h, valign: "top", fontFace: FONT, fontSize: o.size || 10, color: o.color || C.ink });
}
function conclusion(s, x, y, w, h, title, lines, st) {
  const m = ST[st];
  card(s, x, y, w, h, { fill: m.soft, line: m.color });
  if (title) s.addText(title, { x: x + 0.14, y: y + 0.08, w: w - 0.28, h: 0.26, fontFace: FONT, fontSize: 10.5, bold: true, color: m.color });
  bullets(s, x + 0.14, y + (title ? 0.34 : 0.1), w - 0.28, h - (title ? 0.42 : 0.18), lines, { size: 9.5, gap: 2 });
}
function code(s, x, y, w, h, title, lines) {
  card(s, x, y, w, h, { fill: C.code, line: C.line });
  s.addText(title, { x: x + 0.12, y: y + 0.06, w: w - 0.24, h: 0.24, fontFace: FONT, fontSize: 9.5, bold: true, color: C.blue });
  s.addText(lines.join("\n"), { x: x + 0.12, y: y + 0.3, w: w - 0.24, h: h - 0.38, fontFace: MONO, fontSize: 8, color: C.codeInk, valign: "top", lineSpacing: 11 });
}
function flow(s, x, y, w, steps, color, soft, h = 0.4) {
  const n = steps.length, gap = 0.16;
  const bw = (w - gap * (n - 1)) / n;
  steps.forEach((t, i) => {
    const bx = x + i * (bw + gap);
    s.addShape(pres.ShapeType.roundRect, { x: bx, y, w: bw, h, rectRadius: 0.06, fill: { color: soft }, line: { color, width: 1 } });
    s.addText(t, { x: bx, y, w: bw, h, align: "center", valign: "middle", fontFace: FONT, fontSize: 8.5, color: C.ink });
    if (i < n - 1) s.addShape(pres.ShapeType.line, { x: bx + bw + 0.015, y: y + h / 2, w: gap - 0.03, h: 0, line: { color, width: 1.2, endArrowType: "triangle" } });
  });
}
function img(s, file, x, y, w, h, cap) {
  card(s, x - 0.05, y - 0.05, w + 0.1, h + (cap ? 0.3 : 0.1), { fill: C.card });
  s.addImage({ path: IMG(file), x, y, w, h, sizing: { type: "contain", w, h } });
  if (cap) s.addText(cap, { x, y: y + h + 0.02, w, h: 0.24, align: "center", fontFace: FONT, fontSize: 8.5, color: C.muted });
}
function estLines(text, colWIn, fs) {
  const avail = colWIn * 72 - 12;
  let need = 0;
  for (const ch of String(text)) {
    need += /[\u4e00-\u9fff\u3000-\u303f\uff00-\uffef]/.test(ch) ? fs : fs * 0.56;
  }
  return Math.max(1, Math.ceil(need / avail));
}
function table(s, x, y, w, head, rows, colW, st, fs = 9) {
  const m = ST[st];
  const n = head.length;
  const total = colW.reduce((a, b) => a + b, 0);
  const widths = colW.map((c) => (c / total) * w);
  const pad = 0.08;
  const headH = 0.34;
  let cy = y;
  let cx = x;
  for (let i = 0; i < n; i++) {
    s.addShape(pres.ShapeType.rect, { x: cx, y: cy, w: widths[i], h: headH, fill: { color: m.color }, line: { color: m.color, width: 0.75 } });
    s.addText(head[i], { x: cx + pad, y: cy, w: widths[i] - 2 * pad, h: headH, valign: "middle", fontFace: FONT, fontSize: fs, bold: true, color: "FFFFFF" });
    cx += widths[i];
  }
  cy += headH;
  rows.forEach(function (r, ri) {
    let lines = 1;
    for (let i = 0; i < n; i++) {
      const l = estLines(r[i], widths[i] - 2 * pad, fs);
      if (l > lines) lines = l;
    }
    const rh = lines * (fs * 1.38 / 72) + 0.14;
    let rx = x;
    for (let i = 0; i < n; i++) {
      s.addShape(pres.ShapeType.rect, { x: rx, y: cy, w: widths[i], h: rh, fill: { color: ri % 2 ? "F2F4F7" : C.card }, line: { color: C.line, width: 0.75 } });
      s.addText(r[i], { x: rx + pad, y: cy + 0.05, w: widths[i] - 2 * pad, h: rh - 0.1, valign: "top", fontFace: FONT, fontSize: fs, color: C.ink });
      rx += widths[i];
    }
    cy += rh;
  });
  return cy;
}

// ============ P1 封面 ============
{
  const s = pres.addSlide();
  s.background = { color: C.dark };
  s.addShape(pres.ShapeType.rect, { x: 0, y: 0, w: 10, h: 0.09, fill: { color: C.blue }, line: { type: "none" } });
  s.addShape(pres.ShapeType.rect, { x: 0, y: 0.09, w: 10, h: 0.035, fill: { color: C.orange }, line: { type: "none" } });
  s.addText("ANDROID LEARNING · STAGE 1 → 3", { x: 0.7, y: 0.85, w: 6, h: 0.3, fontFace: FONT, fontSize: 10, color: C.darkMuted, charSpacing: 2 });
  s.addText("CarAudioPlayer 三阶段学习总结", { x: 0.7, y: 1.2, w: 6.4, h: 0.75, fontFace: FONT, fontSize: 30, bold: true, color: C.darkInk });
  s.addText("从布局与控件，到 MVVM 数据驱动，再到在线播放完整链路", { x: 0.7, y: 1.98, w: 6.2, h: 0.32, fontFace: FONT, fontSize: 12, color: C.darkMuted });
  s.addShape(pres.ShapeType.rect, { x: 0.7, y: 2.42, w: 0.8, h: 0.05, fill: { color: C.blue }, line: { type: "none" } });
  const bars = [
    ["阶段一  UI 层", "单 Activity 装下全 App：容器 / 控件 / 数据绑定 / 生命周期 / 交互细节", C.green],
    ["阶段二  架构层", "MVVM + LiveData + ViewBinding + Room + Media3：数据驱动与播放架构", C.blue],
    ["阶段三  数据与性能层", "点歌链路 / 多级缓存 / 使用延迟三条线 / VIP 播放排障", C.orange],
  ];
  bars.forEach((b, i) => {
    const y = 2.75 + i * 0.62;
    s.addShape(pres.ShapeType.roundRect, { x: 0.7, y, w: 6.0, h: 0.5, rectRadius: 0.05, fill: { color: "1D3450" }, line: { color: b[2], width: 1.2 } });
    s.addShape(pres.ShapeType.rect, { x: 0.7, y, w: 0.07, h: 0.5, fill: { color: b[2] }, line: { type: "none" } });
    s.addText(b[0], { x: 0.9, y, w: 1.75, h: 0.5, valign: "middle", fontFace: FONT, fontSize: 11.5, bold: true, color: C.darkInk });
    s.addText(b[1], { x: 2.7, y, w: 3.9, h: 0.5, valign: "middle", fontFace: FONT, fontSize: 8.5, color: C.darkMuted });
  });
  s.addText([
    { text: "学习成果 · 阶段汇报\n", options: { fontSize: 12, bold: true, color: C.darkInk, breakLine: true } },
    { text: "基础 → 架构 → 实战\n\n", options: { fontSize: 9.5, color: C.darkMuted, breakLine: true } },
    { text: "3\n", options: { fontSize: 34, bold: true, color: C.blue, breakLine: true } },
    { text: "一 · UI 层\n二 · 架构层\n三 · 数据与性能层", options: { fontSize: 9.5, color: C.darkMuted } },
  ], { x: 7.3, y: 1.2, w: 2.1, h: 2.6, fontFace: FONT, align: "center" });
  s.addText([
    { text: "答辩人：＿＿＿\n", options: { breakLine: true } },
    { text: "汇报时长：10 – 15 分钟\n", options: { breakLine: true } },
    { text: "日期：2026-10", options: {} },
  ], { x: 0.7, y: 4.85, w: 5, h: 0.6, fontFace: FONT, fontSize: 9.5, color: C.darkMuted });
}

// ============ P2 目录 ============
{
  const s = pres.addSlide();
  header(s, 0, "汇报目录：三阶段学习路线", "一条主线：先搭 UI 骨架（阶段一）→ 再立数据驱动架构（阶段二）→ 最后通网络数据与性能（阶段三）");
  const cols = [
    [1, "01", "UI 层：把界面搭对", [
      "单 Activity + 左导航 4 Tab + 底部常驻迷你条",
      "四大容器在本 App 的真实落点",
      "控件与列表数据绑定：ViewBinding / Adapter / DiffUtil",
      "生命周期与三种 Fragment 驻留策略",
      "交互细节：点击穿透 / 键盘 / 图片淡入 / 歌词滚动",
      "工程工具链：gradlew / adb / 隧道坑",
    ]],
    [2, "02", "架构层：让数据自己流", [
      "MVVM 三层职责与本项目映射",
      "数据驱动三件套与「回调只写 LiveData」规矩",
      "播放架构：后台常驻 / 跨进程通道 / 蓝牙耳机 / 音频焦点",
      "状态拆流：换歌 / 播放态 / 进度各走一条流",
      "代数竞态防护：快速点歌与快速输入",
      "通用知识点：观察者 / 单一数据源 / 事件与状态",
    ]],
    [3, "03", "数据与性能层：把等待藏起来", [
      "点歌主链路一句话流程",
      "完整链路时序：从点击到出声的七个环节",
      "多级缓存全景：每层缓存省了哪一步网络时间",
      "使用延迟三条线：并发 / 缓存 / 视觉缓冲",
      "排障案例：VIP 歌曲播放问题的根因分层",
    ]],
  ];
  cols.forEach((c, i) => {
    const x = 0.45 + i * 3.1, m = ST[c[0]];
    card(s, x, 1.3, 2.95, 3.85, { line: m.color });
    s.addText(c[1], { x: x + 0.16, y: 1.42, w: 1.2, h: 0.42, fontFace: FONT, fontSize: 20, bold: true, color: m.color });
    s.addText(c[2], { x: x + 0.16, y: 1.86, w: 2.65, h: 0.3, fontFace: FONT, fontSize: 11.5, bold: true, color: C.ink });
    s.addShape(pres.ShapeType.rect, { x: x + 0.16, y: 2.18, w: 0.5, h: 0.035, fill: { color: m.color }, line: { type: "none" } });
    bullets(s, x + 0.16, 2.3, 2.65, 2.75, c[3], { size: 8.8, gap: 5 });
  });
  footer(s);
}

// ============ P3 阶段一总览 ============
{
  const s = pres.addSlide();
  header(s, 1, "UI 层总览：一个 Activity 装下整个 App", "车机横屏场景：左导航四 Tab、底部常驻迷你播放条；本页讲整体骨架与「先搭骨架再谈数据」的顺序");
  bullets(s, 0.45, 1.3, 5.9, 2.5, [
    "导航架构：MainActivity + 左侧导航（首页 / 歌单 / 榜单 / 搜索）+ 底部迷你条；「我的」从顶部头像进入，一级导航只有一层",
    "Tab 驻留：一级 Tab 用 add + hide/show，Fragment 实例与 ViewModel 状态保留，切换不重新加载",
    "二级页面：add + addToBackStack 压栈式导航，返回键逐层弹出；重复点当前 Tab 清空该 Tab 二级栈回顶部",
    "播放页：replace 进独立容器、不进返回栈，从迷你条「长出」动画展开；关闭只收 UI 不中断播放",
    "横屏适配：车机横屏大控件高对比、列表优先少输入，驾驶中一眼可读",
  ], { size: 10 });
  conclusion(s, 0.45, 3.95, 5.9, 1.15, "本阶段一句话", [
    "先搭一个「能显示、能导航、切页不丢状态」的骨架，数据驱动是阶段二的事",
    "容器与 Fragment 策略决定了后面所有页面的写法",
  ], 1);
  img(s, "01-home.jpg", 6.65, 1.35, 2.95, 1.97, "首页：Banner + 推荐歌单 + 热门榜单 + 迷你条");
  img(s, "02-player.jpg", 6.65, 3.62, 2.95, 1.5, "全屏播放页：歌词滚动 + 进度 + 控制区");
  footer(s);
}

// ============ P4 布局与容器篇 ============
{
  const s = pres.addSlide();
  header(s, 1, "布局篇：四大容器在本 App 的真实落点", "容器选择只看两件事：排列方向、叠放关系；下表每一行都对应项目里的真实布局文件");
  table(s, 0.45, 1.28, 6.1, ["容器", "类型", "本 App 用在哪（真实文件）"], [
    ["LinearLayout", "线性布局", "activity_main 左导航竖排；item_song_rich 歌曲行横排（封面+歌名+时长）；迷你条横排"],
    ["FrameLayout", "帧布局", "activity_main 舞台层：内容区 + 迷你条 + 播放页叠放；fragment_player 根（背景层+内容层）；卡片封面叠 VIP 角标"],
    ["NestedScrollView", "滚动容器", "fragment_home / fragment_mine 整页竖滚，内嵌多条 RecyclerView（嵌套滚动需协调）"],
    ["RecyclerView", "列表容器", "全部列表：首页多列、歌单广场 GridLayoutManager 网格、榜单/搜索/详情竖排；复用 ViewHolder"],
  ], [1.35, 0.95, 3.8], 1, 8.8);
  conclusion(s, 0.45, 3.62, 6.1, 1.5, "本页结论", [
    "单向排列用 Linear（weight 分配宽度），叠放用 Frame（后加的盖在上面），长列表一律 RecyclerView",
    "ScrollView 里不再套 ScrollView；首页用 NestedScrollView + 多条横向 RecyclerView 组合",
    "舞台层 FrameLayout 是「播放页从迷你条长出」动画的前提：覆盖层与内容层同在一个容器里",
  ], 1);
  img(s, "03-songlist-square.jpg", 6.85, 1.35, 2.75, 1.83, "歌单广场：分类 Chip 行 + 网格卡片");
  img(s, "05-songlist-detail.jpg", 6.85, 3.5, 2.75, 1.62, "歌单详情：两栏模板（左列表 + 右信息）");
  footer(s);
}

// ============ P5 控件与列表数据绑定篇 ============
{
  const s = pres.addSlide();
  header(s, 1, "控件篇：界面是怎么绑上数据的", "以歌曲行为例：一行控件填什么、什么时候刷新、谁决定刷新，三层各管一段");
  bullets(s, 0.45, 1.28, 6.1, 2.6, [
    "ViewBinding：编译期生成 binding 类替代 findViewById，写错控件名编译就报错",
    "行绑定流程：onBindViewHolder 依次填序号 / 歌名 / 歌手 / 时长 / 封面 / 收藏星标；点击位置在绑定时捕获，防列表刷新后 position 失效越界",
    "图片加载：统一 ImageLoader（内存 / 磁盘 / 网络三级缓存 + 降采样 + tag 防滚动错位 + 200ms 淡入）",
    "局部刷新：ListAdapter + DiffUtil，areItemsTheSame 比 mid、areContentsTheSame 只比 6 个展示字段，只有变化的行重新绑定",
    "高亮绑定：song() 流 → adapter.setCurrentMid(mid)，mid 去重后才 notifyDataSetChanged（列表通常不满一屏，代价可接受）",
    "收藏态：批量注入内存 Set，行绑定时零查库",
  ], { size: 9.6 });
  conclusion(s, 0.45, 4.0, 6.1, 1.12, "本页结论", [
    "「这行显示什么」由 Adapter 决定，「什么时候刷」由 diff 与去重决定，View 只负责显示",
    "绑定与刷新分离后，换歌只刷高亮行与迷你条，不会整表重绘",
  ], 1);
  img(s, "06-rank-detail.jpg", 6.85, 1.35, 2.75, 1.83, "榜单详情：当前播放行绿色高亮");
  img(s, "04-search.jpg", 6.85, 3.5, 2.75, 1.62, "搜索页：联想列表 + VIP 角标 + 序号");
  footer(s);
}

// ============ P6 生命周期篇 ============
{
  const s = pres.addSlide();
  header(s, 1, "生命周期篇：Activity 与 Fragment 的双生命周期", "两套生命周期不同步——这是 NPE 与内存泄漏的共同源头，也是判断状态该放哪一层的依据");
  flow(s, 0.45, 1.26, 9.1, ["onCreate 创建", "onStart 可见", "onResume 可交互", "onPause 失去焦点", "onStop 不可见", "onDestroy 销毁"], C.green, C.greenSoft, 0.42);
  s.addText("Fragment 视图生命周期：onCreateView → onViewCreated → onDestroyView，与 Activity 六步错开；视图销毁 ≠ 实例销毁", { x: 0.45, y: 1.76, w: 9.1, h: 0.26, fontFace: FONT, fontSize: 9, color: C.muted });
  table(s, 0.45, 2.1, 9.1, ["页面类型", "事务策略", "状态后果"], [
    ["一级 Tab（首页/歌单/榜单/搜索）", "add + hide/show 驻留", "实例与 ViewModel 一起保留：切回不重新加载，滚动位置与数据都在"],
    ["二级页（详情/搜索结果）", "add + addToBackStack 压栈", "返回 = 弹掉最上面那层（实例 + ViewModel 一起销毁）；下层原样保留，所以返回到的页面数据还在"],
    ["全屏播放页", "replace 独立容器，不进返回栈", "收起即销毁界面，但播放不受影响——ExoPlayer 活在 Service 里，不依赖界面生命周期"],
  ], [2.5, 2.5, 4.1], 1, 9);
  conclusion(s, 0.45, 4.2, 9.1, 1.05, "本页结论", [
    "错位的两个后果：视图已销毁而实例还活着 → 回调打到空 binding（NPE）；实例还抓着已销毁的视图 → 回收不掉（内存泄漏）",
    "对策已固化：onDestroyView 置空 binding；订阅一律绑 getViewLifecycleOwner；网络回调只写 LiveData 不碰 View",
    "界面销毁/重建时播放不受影响（ExoPlayer 在 Service 里）；进程被杀会一起中断，前台服务只降低被杀概率",
  ], 1);
  footer(s);
}
// ============ P6b 状态归属 ============
{
  const s = pres.addSlide();
  header(s, 1, "状态归属：放错位置会出什么病", "判断法则：一个状态该活多久，就决定它放哪一层——三层各归其位，放错就出对应的病");
  const items = [
    ["视图状态", "跟视图同生共死", "→ 放 Fragment 字段 / View", "例：seeking（正在拖进度条）、lyricBrowsing、binding"],
    ["页面状态", "跨视图重建仍存活", "→ 放 ViewModel", "例：歌词结果 lyricResult、详情数据、榜单列表"],
    ["全局状态", "跨页面共享", "→ 放进程级单例", "例：播放状态 song() / playing() / progress()"],
  ];
  items.forEach((it, i) => {
    const y = 1.24 + i * 1.08;
    card(s, 0.45, y, 3.8, 0.96, { line: C.green });
    s.addShape(pres.ShapeType.rect, { x: 0.45, y, w: 0.06, h: 0.96, fill: { color: C.green }, line: { type: "none" } });
    s.addText(it[0] + "（" + it[1] + "）", { x: 0.62, y: y + 0.05, w: 3.55, h: 0.28, fontFace: FONT, fontSize: 10, bold: true, color: C.green });
    s.addText(it[2], { x: 0.62, y: y + 0.33, w: 3.55, h: 0.26, fontFace: FONT, fontSize: 9.5, color: C.ink });
    s.addText(it[3], { x: 0.62, y: y + 0.61, w: 3.55, h: 0.3, fontFace: FONT, fontSize: 8, color: C.muted });
  });
  img(s, "07-state-ownership.png", 4.5, 1.3, 4.7, 2.2, "放错地方会出的病（四种典型）");
  conclusion(s, 0.45, 4.52, 9.1, 0.72, "本页结论", [
    "判断只问一句：这个状态该活多久——跟视图同生共死放 Fragment，跨视图重建放 ViewModel，跨页面放单例",
    "项目落地：视图状态在 Fragment 字段、页面数据在各页 ViewModel、播放状态在 PlaybackManager 单例",
  ], 1);
  footer(s);
}
// ============ P7 交互细节篇 ============
{
  const s = pres.addSlide();
  header(s, 1, "交互细节篇：默认行为不满足车机场景的四件事", "每条 = 问题（默认行为）+ 做法（项目实现），都是踩出来或评审出来的");
  const quads = [
    ["点击穿透", "问题：叠在可交互页面上方的纯展示层不拦截点击，事件穿透到下层页面", "做法：覆盖层根布局 clickable=true 拦截；仅最底层一级 Tab 页面不加"],
    ["键盘收起", "问题：安卓默认点输入框外不收键盘，车机上键盘挡住列表", "做法：MainActivity 重写 dispatchTouchEvent，落点不在输入框内则收键盘并清焦点"],
    ["图片闪切与灰罩", "问题：封面加载完成瞬间硬切；ImageView tint 把真人头像罩灰", "做法：加载完成 200ms 淡入；加载头像前显式清空 tint"],
    ["歌词滚动", "问题：歌词跟进度硬跳行，驾驶中难读；手动拖动后又被进度拽走", "做法：平滑滚动居中当前行；拖动后显示「6 秒后回到播放」提示再恢复跟随"],
  ];
  quads.forEach((q, i) => {
    const x = 0.45 + (i % 2) * 4.65, y = 1.28 + Math.floor(i / 2) * 1.72;
    card(s, x, y, 4.45, 1.6, { line: C.green });
    ctitle(s, x + 0.14, y + 0.08, 4.1, q[0], C.green);
    bullets(s, x + 0.14, y + 0.4, 4.15, 1.12, [q[1], q[2]], { size: 9, gap: 3 });
  });
  conclusion(s, 0.45, 4.78, 9.1, 0.62, "本页结论", ["交互细节的本质：系统默认行为面向手机，车机场景要逐条显式处理并写进编码规范"], 1);
  footer(s);
}

// ============ P10 阶段二总览 ============
{
  const s = pres.addSlide();
  header(s, 2, "架构层总览：MVVM + LiveData 让代码分层", "目标：把「界面」与「数据」彻底解耦，任何一层换实现不影响另一层");
  bullets(s, 0.45, 1.28, 5.9, 2.3, [
    "技术栈：MVVM 三层架构 + ViewBinding + LiveData + ViewModel + Room + Media3（前台服务 / MediaSession / MediaController）",
    "业务面：在线搜索 / 歌单 / 榜单 / 专辑 / 歌手、本地收藏、播放队列、歌词同步",
    "工程结构：ui/（View 层）· player/ + data/（Model 层）· 各包 ViewModel（状态层）",
    "交付物：完整可运行 App + 项目技术文档 + 本汇报",
  ], { size: 10 });
  conclusion(s, 0.45, 3.72, 5.9, 1.4, "一句话概括", [
    "View 只用 ViewBinding 绑视图，不持有数据",
    "ViewModel 只持状态（LiveData），不碰 View",
    "Model 只供数据（网络 / 数据库 / 播放器），不关心谁在用",
  ], 2);
  card(s, 6.65, 1.28, 2.95, 3.84, { line: C.blue });
  ctitle(s, 6.8, 1.4, 2.6, "三层数据流向", C.blue);
  flow(s, 6.85, 1.8, 2.55, ["View 渲染"], C.blue, C.blueSoft, 0.36);
  s.addShape(pres.ShapeType.line, { x: 8.1, y: 2.2, w: 0, h: 0.3, line: { color: C.blue, width: 1.2, endArrowType: "triangle", beginArrowType: "triangle" } });
  flow(s, 6.85, 2.56, 2.55, ["ViewModel 状态"], C.blue, C.blueSoft, 0.36);
  s.addShape(pres.ShapeType.line, { x: 8.1, y: 2.96, w: 0, h: 0.3, line: { color: C.blue, width: 1.2, endArrowType: "triangle", beginArrowType: "triangle" } });
  flow(s, 6.85, 3.32, 2.55, ["Model 数据源"], C.blue, C.blueSoft, 0.36);
  bullets(s, 6.85, 3.85, 2.6, 1.15, [
    "View → VM：用户动作",
    "VM → View：LiveData 推送",
    "VM ↔ Model：请求与回调",
  ], { size: 8.8, gap: 3 });
  footer(s);
}

// ============ P11 三层职责映射 ============
{
  const s = pres.addSlide();
  header(s, 2, "MVVM 三层职责与本项目映射", "数据流的流动：View → ViewModel → Model；状态变化经 LiveData 回流自动刷新 UI");
  table(s, 0.45, 1.28, 6.3, ["分层", "职责（原文）", "本项目落地"], [
    ["View 视图层", "显示数据、接收用户输入、观察 ViewModel 数据变化（只绑 UI 交互）", "MainActivity / 各 Fragment + ViewBinding（回调里只做渲染）"],
    ["ViewModel 状态层", "持有 UI 状态、处理业务逻辑、与 Repository 交互；屏幕旋转后 View 重建状态不丢", "7 个页面级 ViewModel（LiveData 对外只读、对内 setValue）"],
    ["Model 模型层", "数据实体、数据访问、业务规则（网络 / DB / 本地）", "QQMusicApi / Room / ImageLoader / PlaybackManager（进程级单例）"],
  ], [1.25, 2.5, 2.55], 2, 9);
  conclusion(s, 0.45, 3.3, 6.3, 1.82, "为什么这样分（踩坑换来的）", [
    "网络回调直接碰 ViewBinding：Fragment 视图销毁后回调到达 → NPE 崩溃；规矩改为「回调只写 LiveData」",
    "ViewModel 比 View 长寿：旋转 / 压栈返回后状态还在，不重复请求",
    "播放状态不能放 ViewModel：它要跨页面共享且比任何页面都长寿 → 进程级单例 PlaybackManager",
  ], 2);
  card(s, 7.0, 1.28, 2.6, 3.84, { fill: C.code, line: C.line });
  ctitle(s, 7.15, 1.4, 2.3, "本项目 ViewModel 清单", C.blue, 10.5);
  bullets(s, 7.15, 1.75, 2.35, 3.2, [
    "HomeViewModel",
    "RankViewModel",
    "DetailViewModel（四种详情共用）",
    "PlayerViewModel（歌词）",
    "SearchInputViewModel",
    "SearchResultViewModel",
    "SongListSquareViewModel",
    "- 播放状态不在其中：归 PlaybackManager 单例",
  ], { size: 8.6, gap: 4 });
  footer(s);
}

// ============ P12 数据驱动三件套 ============
{
  const s = pres.addSlide();
  header(s, 2, "数据驱动三件套：ViewBinding + LiveData + ViewModel", "文档「关键点名册」章节的核心：布局层绑定、ViewModel 显存状态、Activity 显监听");
  const quads = [
    ["ViewBinding：布局直接绑数据", ["layout 根布局即生成 binding 类，控件名编译期检查", "替代 findViewById，杜绝空指针与强转", "视图销毁 onDestroyView 置空 binding 防泄漏"]],
    ["LiveData：可观察的数据容器", ["ViewModel 中每个对外状态一个 MutableLiveData", "对外只暴露 LiveData getter，写入权留在 VM 内部", "生命周期感知：observe 绑 getViewLifecycleOwner，视图销毁自动解绑"]],
    ["ViewModel：比屏幕活得久", ["主线程创建于 Activity / Fragment，配置变更（旋转）后存活", "只有 Activity 真正销毁（用户退出）才 onCleared", "跨 Fragment 共享状态：同一 VM 实例供多个页面观察"]],
    ["串联起来的踩坑（文档原文）", ["早期在回调里直接 binding.xxx 更新 UI", "快速切页时 Fragment 视图已销毁 → 回调到达即 NPE", "改为回调只写 LiveData 后，此类崩溃归零"]],
  ];
  quads.forEach((q, i) => {
    const x = 0.45 + (i % 2) * 4.65, y = 1.28 + Math.floor(i / 2) * 1.86;
    card(s, x, y, 4.45, 1.74, { line: C.blue });
    ctitle(s, x + 0.14, y + 0.08, 4.1, q[0], C.blue, 10.5);
    bullets(s, x + 0.14, y + 0.42, 4.15, 1.24, q[1], { size: 9, gap: 3 });
  });
  conclusion(s, 0.45, 5.06, 9.1, 0.42, "", ["三件套合起来 = 「View 等数据、VM 存数据、回调喂数据」，谁也不越界"], 2);
  footer(s);
}

// ============ P13 播放架构 ============
{
  const s = pres.addSlide();
  header(s, 2, "播放架构：后台常驻 / 跨进程 / 蓝牙耳机 / 音频焦点", "播放能力必须「脱离界面存在」：界面可以死，音乐不能停");
  const quads = [
    ["后台常驻：前台服务", ["PlaybackService 前台服务 + 媒体通知，进程优先级提升不被系统回收", "ExoPlayer（Media3）活在 Service 里，Activity 销毁播放继续", "通知栏控制与界面控制走同一套 MediaSession 回调"]],
    ["组件控制通道：MediaController", ["UI 侧持 MediaController、服务侧持 MediaSession，两者是 Binder 形态的通道（底层 AIDL：接口 + Stub/Proxy）", "本项目 Service 未声明独立进程 → 两侧同进程，通道退化为本地调用；加 android:process 即可启用真跨进程，代码一行不用改", "controller.play/pause/seekTo 是唯一的播放入口，状态经 Listener 回推 UI"]],
    ["蓝牙耳机 / 线控按键", ["MediaSession 注册回调：蓝牙耳机播放/暂停/下一首键 → 与界面点按钮同一入口 PlaybackManager", "车机方向盘按键同理映射到 MediaSession", "好处：控制源再多，状态只有一个出口"]],
    ["音频焦点", ["播放前 requestAudioFocus，失去焦点（导航播报）按策略 duck 降音量或暂停", "播报结束焦点归还自动恢复，不打断用户听歌连续性", "车机场景焦点冲突高频，策略必须显式声明"]],
  ];
  quads.forEach((q, i) => {
    const x = 0.45 + (i % 2) * 4.65, y = 1.28 + Math.floor(i / 2) * 1.86;
    card(s, x, y, 4.45, 1.74, { line: C.blue });
    ctitle(s, x + 0.14, y + 0.08, 4.1, q[0], C.blue, 10.5);
    bullets(s, x + 0.14, y + 0.42, 4.15, 1.24, q[1], { size: 9, gap: 3 });
  });
  conclusion(s, 0.45, 5.06, 9.1, 0.42, "", ["对应同事汇报的 AIDL 跨进程章：他手写五件套，我用 Media3 现成通道 + 讲清 Binder 原理"], 2);
  footer(s);
}

// ============ P14 状态拆流 ============
{
  const s = pres.addSlide();
  header(s, 2, "状态拆流：换歌 / 播放态 / 进度各走一条流", "先一句话流程，再展开：为什么胖 LiveData 不行、为什么光加判断不够、拆完谁被唤醒");
  flow(s, 0.45, 1.26, 9.1, ["换歌 → song()", "播放/暂停 → playing()", "500ms 轮询 → progress()", "各页面按需订阅"], C.blue, C.blueSoft, 0.4);
  const quads = [
    ["问题：胖 LiveData 全量广播", ["早期一条 state 流装 PlayingState 全量字段（歌/播放态/进度/队列）", "进度每 500ms 推一次 = 12 个观察者全部被唤醒", "列表页只关心歌名高亮，却每秒陪跑两次"]],
    ["拆流的两条判据（缺一不可）", ["① 消费者关心的字段不同：列表页只要 song()，播放页才要 progress()", "② 字段变化频率差得大：progress 每 500ms 变，song 换歌才发", "两条都成立才值得拆；只有 1 个消费者时拆不拆无所谓"]],
    ["为什么光加判断不够", ["判断只能拦「没变的推送」（同值去重，对 song/playing 有效）", "进度值每次采样都在变，判断拦不住 → 该醒的还是全醒", "减少「叫醒谁」只能改观察者名单 = 拆流"]],
    ["拆完的效果", ["progress() 只有播放页 1 个观察者；暂停即停轮询", "9 个列表页只订阅 song()（换歌才醒一次）", "迷你条订阅 song()+playing()；渲染端再用 mid 去重兜底"]],
  ];
  quads.forEach((q, i) => {
    const x = 0.45 + (i % 2) * 4.65, y = 1.82 + Math.floor(i / 2) * 1.62;
    card(s, x, y, 4.45, 1.5, { line: i === 3 ? C.blue : C.line });
    ctitle(s, x + 0.14, y + 0.06, 4.1, q[0], i === 3 ? C.blue : C.ink, 10);
    bullets(s, x + 0.14, y + 0.38, 4.15, 1.06, q[1], { size: 8.6, gap: 2 });
  });
  conclusion(s, 0.45, 5.06, 9.1, 0.42, "", ["判断管「这次推不推」，拆流管「推给谁」；两者分层互补，不是二选一"], 2);
  footer(s);
}

// ============ P15 代数竞态防护 ============
{
  const s = pres.addSlide();
  header(s, 2, "代数竞态防护：快速点歌与快速输入", "乐观切换的配套正确性保障：UI 先切，异步结果只认「最新一代」");
  const quads = [
    ["场景一：快速点歌", ["连点四首歌，第 2 首的播放地址回调比第 4 首晚到", "不防护：旧回调 setMediaItem → 正在听第 4 首却播了第 2 首", "防护：playAt 时 generation++，回调里 gen != generation 直接丢弃"]],
    ["场景二：快速输入联想", ["输入「星辰大海」每字触发一次搜索请求", "旧关键词的慢响应后到，覆盖新关键词联想列表", "防护：SearchInputViewModel 同款 suggestGeneration 校验"]],
    ["本质：乐观切换 + 身份校验", ["展示数据本地已有 → 点歌瞬间先切 UI（不留上一首残影）", "异步结果带「代数」回来，只接受最新一代", "同一范式两处落地，说明它是通用竞态解法而非巧合"]],
    ["与去重的分工", ["去重（mid/值比较）：拦重复推送，省渲染", "代数（generation）：拦过期响应，保正确", "两者都写在源头分发处，View 层无感知"]],
  ];
  quads.forEach((q, i) => {
    const x = 0.45 + (i % 2) * 4.65, y = 1.28 + Math.floor(i / 2) * 1.86;
    card(s, x, y, 4.45, 1.74, { line: C.blue });
    ctitle(s, x + 0.14, y + 0.08, 4.1, q[0], C.blue, 10.5);
    bullets(s, x + 0.14, y + 0.42, 4.15, 1.24, q[1], { size: 9, gap: 3 });
  });
  conclusion(s, 0.45, 5.06, 9.1, 0.42, "", ["异步请求响应必须做身份校验（代数 / 响应自带标识），已写入项目编码规范"], 2);
  footer(s);
}

// ============ P16 完整数据流 ============
{
  const s = pres.addSlide();
  header(s, 2, "完整数据流：三条链，一个闭环", "控件怎么来 / 数据怎么来 / 点下去怎么回去——前两条链在 onBindViewHolder 汇合，第三条链把结果又推回屏幕");
  const chains = [
    ["链一：控件怎么来", C.blue, [
      "设计稿（样式图）",
      "↓ 人写",
      "XML 图纸",
      "↓ 编译（aapt2 / AGP）",
      "R.id + Binding 清单",
      "↓ 运行时 inflate",
      "控件对象诞生",
      "↓ bind()",
      "binding.tvXxx = 控件引用",
    ]],
    ["链二：数据怎么来", C.blue, [
      "接口（QQMusicApi）",
      "↓ 回调",
      "ViewModel.setValue()",
      "↓ observe（绑 getViewLifecycleOwner）",
      "Fragment 收到数据",
      "↓ setSongs → submitList",
      "DiffUtil 挑出变化的行",
      "↓",
      "交给 onBindViewHolder",
    ]],
    ["链三：点下去怎么回去", C.orange, [
      "用户点某一行",
      "↓",
      "行上挂着监听（绑定时挂的）",
      "↓",
      "listener.onSongClick(song, pos)",
      "↓",
      "宿主传进来的 lambda",
      "↓",
      "main.playAndOpenPlayer()",
    ]],
  ];
  chains.forEach((c, i) => {
    const x = 0.45 + i * 3.1;
    card(s, x, 1.18, 2.95, 3.08, { line: c[1] });
    s.addText(c[0], { x: x + 0.14, y: 1.26, w: 2.67, h: 0.28, fontFace: FONT, fontSize: 10.5, bold: true, color: c[1] });
    s.addShape(pres.ShapeType.rect, { x: x + 0.14, y: 1.56, w: 0.45, h: 0.03, fill: { color: c[1] }, line: { type: "none" } });
    const rows = [];
    c[2].forEach((t) => {
      const arrow = t.startsWith("↓");
      rows.push({ text: t, options: { breakLine: true, fontSize: arrow ? 8 : 9, bold: !arrow, color: arrow ? C.muted : C.ink, fontFace: FONT, align: arrow ? "center" : "left", paraSpaceAfter: 6 } });
    });
    s.addText(rows, { x: x + 0.14, y: 1.64, w: 2.67, h: 2.54, valign: "top", fontFace: FONT });
  });
  conclusion(s, 0.45, 4.42, 9.1, 0.78, "", [
    "链一（造控件）+ 链二（送数据）→ 在 onBindViewHolder 汇合 → 合成屏幕上的那一行",
    "链三：用户点击 → 回调 → 改播放状态 → 状态又顺着链二推回屏幕，形成闭环（不是一条直线）",
  ], 2);
  footer(s);
}
// ============ P18 阶段三总览 ============
{
  const s = pres.addSlide();
  header(s, 3, "数据与性能层总览：从点击到出声要过几道手", "本阶段回答一个问题：一次点歌的等待时间里，我们在哪些步骤省了时间、怎么省的");
  flow(s, 0.45, 1.32, 9.1, ["点歌 playAt", "乐观切换 UI", "resolveMid(缺则)", "中转 getPlayUrl", "MediaController 播放", "轮询回推进度"], C.orange, C.orangeSoft, 0.44);
  bullets(s, 0.45, 2.0, 5.9, 2.0, [
    "链路角色：Fragment（触发）→ PlaybackManager（编排）→ 电脑中转服务（代理 QQ 上游）→ PlaybackService（出声）→ UI（回推）",
    "乐观切换：歌名封面本地已有，点歌瞬间先切 UI，取地址在后台进行",
    "等待被拆成两段：用户可见段（0ms，已切换）与不可见段（取地址），后者被缓存与并发压缩",
  ], { size: 10 });
  conclusion(s, 0.45, 4.12, 5.9, 1.0, "本阶段一句话", [
    "性能不是「做得快」，是「把等待藏起来 + 把重复请求省掉」",
    "每个优化点都有改前 / 改后 / 实测三件套",
  ], 3);
  card(s, 6.65, 2.0, 2.95, 3.12, { fill: C.orangeSoft, line: C.orange });
  ctitle(s, 6.8, 2.12, 2.6, "三条延迟线（P21 展开）", C.orange, 10.5);
  bullets(s, 6.8, 2.5, 2.65, 2.4, [
    "并发线：线程池扩容",
    "- 图片池 4→6 实测 8s→2.5s",
    "缓存线：多级缓存秒回",
    "- 歌单广场磁盘缓存命中",
    "视觉线：骨架屏先行",
    "- 先出占位再换内容",
  ], { size: 9, gap: 3 });
  footer(s);
}

// ============ P19 时序图 ============
{
  const s = pres.addSlide();
  header(s, 3, "完整链路时序图：从点歌到出声（七个环节）", "时序按实际调用先后自上而下排列；参与者 5 个、消息 7 条，与项目代码一一对应");
  const parts = ["Fragment (UI)", "PlaybackManager", "中转服务 (PC)", "QQ 上游", "PlaybackService"];
  const xs = [0.75, 2.6, 4.55, 6.35, 8.1];
  parts.forEach((p, i) => {
    s.addShape(pres.ShapeType.roundRect, { x: xs[i] - 0.72, y: 1.24, w: 1.55, h: 0.36, rectRadius: 0.05, fill: { color: C.orangeSoft }, line: { color: C.orange, width: 1 } });
    s.addText(p, { x: xs[i] - 0.72, y: 1.24, w: 1.55, h: 0.36, align: "center", valign: "middle", fontFace: FONT, fontSize: 8.5, color: C.ink });
    s.addShape(pres.ShapeType.line, { x: xs[i] + 0.05, y: 1.62, w: 0, h: 3.1, line: { color: C.line, width: 1, dashType: "dash" } });
  });
  const msgs = [
    [0, 1, "① 点歌：playAt(index)，generation++"],
    [1, 0, "② 乐观切换：dispatch song/playing/progress(0,0)，UI 立即换歌"],
    [1, 2, "③ resolveMid / getPlayUrl（mid 缺失先解析；命中中转缓存则秒回）"],
    [2, 3, "④ 缓存未命中才请求上游（songId→mid 内存 Map 去重）"],
    [2, 1, "⑤ 返回 purl 播放地址（gen 校验，过期丢弃）"],
    [1, 4, "⑥ setMediaItem + prepare + play（跨进程 MediaController）"],
    [4, 0, "⑦ onIsPlayingChanged → 启动 500ms 轮询，progress 流回推 UI"],
  ];
  msgs.forEach((m, i) => {
    const y = 1.86 + i * 0.4;
    const x1 = xs[m[0]] + 0.05, x2 = xs[m[1]] + 0.05;
    const left = Math.min(x1, x2), w = Math.abs(x2 - x1);
    s.addShape(pres.ShapeType.line, { x: left, y, w, h: 0, line: { color: C.orange, width: 1.2, endArrowType: "triangle", flipH: x2 < x1 } });
    s.addText(m[2], { x: left, y: y - 0.2, w: Math.max(w, 2.2), h: 0.18, fontFace: FONT, fontSize: 8, color: C.ink });
  });
  conclusion(s, 0.45, 4.86, 9.1, 0.62, "关键路径", ["②③⑤ 是「把等待藏起来」的三个支点：乐观切换、中转缓存、代数校验；⑦ 之后进度只唤醒播放页"], 3);
  footer(s);
}

// ============ P20 多级缓存 ============
{
  const s = pres.addSlide();
  header(s, 3, "多级缓存全景：每层缓存省了哪一步网络时间", "先流程后表格：请求自上而下穿透，命中即返回；表格回答「省了谁的钱」");
  flow(s, 0.45, 1.26, 9.1, ["UI 请求", "内存缓存", "磁盘缓存(TTL)", "中转服务内存 Map", "QQ 上游"], C.orange, C.orangeSoft, 0.4);
  table(s, 0.45, 1.86, 9.1, ["缓存层", "存什么 / TTL", "省掉的开销"], [
    ["图片内存 LruCache", "解码后 Bitmap / 常驻", "磁盘 IO + 解码耗时，滚动复用零开销"],
    ["图片磁盘缓存", "原始字节 / 50MB 上限", "网络下载；冷启动二次进入秒出封面"],
    ["接口 TTL 缓存", "歌单列表 1h、歌词 24h、搜索联想 10min / 磁盘 cacheDir/api_cache", "整次接口往返；过期回退旧数据兜底"],
    ["中转服务内存 Map", "songId→songmid 永久映射", "上游 resolve 往返（该映射永久不变）"],
    ["单飞合并", "同 key 并发请求合并为一次（ConcurrentHashMap+CompletableFuture）", "重复网络请求；缓存未命中时的并发穿透"],
  ], [1.7, 3.6, 3.8], 3, 8.8);
  conclusion(s, 0.45, 4.3, 9.1, 0.82, "本页结论", [
    "缓存不是「加一层」，是「放对步骤」：永久数据放永久层（mid 映射），易变数据放短 TTL 层（搜索联想）",
    "解析结果为空立即清缓存，避免空数据被缓存导致长时间无内容",
  ], 3);
  footer(s);
}

// ============ P21 使用延迟三条线 ============
{
  const s = pres.addSlide();
  header(s, 3, "使用延迟三条线：并发 / 缓存 / 视觉缓冲", "每条 = 改前 / 改后 / 实测；从用户感知角度组织，不按技术点罗列");
  const rows = [
    ["并发线", "歌单广场首屏 24 张封面 4 线程下载约 8s", "图片池 4→6（同域名并发上限与车机 CPU 的平衡点）", "实测 2.5~3s，滚动无卡顿"],
    ["缓存线", "歌单广场切分类每次全量请求", "磁盘 + 内存双缓存按 key 命中秒回，失败回退过期旧数据", "命中分类秒开，未命中才走网络"],
    ["视觉线", "等数据全到才渲染，白屏等待", "骨架屏占位先行；列表秒出后再补齐异步字段", "白屏时间归零，感知延迟大幅下降"],
  ];
  rows.forEach((r, i) => {
    const y = 1.28 + i * 1.06;
    card(s, 0.45, y, 9.1, 0.94, { line: C.orange });
    s.addShape(pres.ShapeType.rect, { x: 0.45, y, w: 0.06, h: 0.94, fill: { color: C.orange }, line: { type: "none" } });
    s.addText(r[0], { x: 0.62, y, w: 0.95, h: 0.94, valign: "middle", fontFace: FONT, fontSize: 11, bold: true, color: C.orange });
    s.addText("改前：" + r[1], { x: 1.65, y: y + 0.08, w: 3.6, h: 0.78, valign: "middle", fontFace: FONT, fontSize: 8.8, color: C.ink });
    s.addText("改后：" + r[2], { x: 5.3, y: y + 0.08, w: 2.9, h: 0.78, valign: "middle", fontFace: FONT, fontSize: 8.8, color: C.ink });
    s.addText(r[3], { x: 8.25, y: y + 0.08, w: 1.25, h: 0.78, valign: "middle", fontFace: FONT, fontSize: 8.5, bold: true, color: C.orange });
  });
  conclusion(s, 0.45, 4.52, 9.1, 0.96, "一个「做减法」的决策", [
    "榜单 VIP 角标曾靠 8 线程池逐首补字段：造成列表二次刷新 + 冗余请求，且只影响角标显示",
    "评估收益后整体移除（65 行）：请求量、刷新次数、代码量三降——优化也包括删掉不值的优化",
  ], 3);
  footer(s);
}

// ============ P22 性能优化实测（数据表） ============
{
  const s = pres.addSlide();
  header(s, 3, "性能优化实测：做了什么，工具测出什么", "每条都带「优化前 → 优化后」的数字，全部来自工具实测（dumpsys gfxinfo / curl / 实机秒表）");
  const colW = [2.55, 1.95, 4.6];
  const rows = [
    ["做了什么", "测量工具", "优化前 → 优化后（实测）"],
    ["播放状态拆流 + DiffUtil 局部刷新", "dumpsys gfxinfo（滚动榜单）", "692 帧仅 1 帧卡顿（0.14%）；P99 帧耗时 13ms（< 16.7ms 帧预算）"],
    ["图片加载线程池 4 → 6", "实机秒表（歌单广场 24 张封面）", "首屏封面 ≈8s → 2.5~3s；快速滚动无明显卡顿"],
    ["服务端 mid 映射缓存 + 并发 15", "curl 实测接口", "热歌榜解析 28.4s → 0.14s"],
    ["客户端两段式（列表不带 mid）", "实机 + curl", "打开榜单必超时（>8s）→ 0.2s 秒开"],
    ["ApiCache 两级缓存 + 分级 TTL", "实机", "二次进任何页面 ≈ 0ms"],
    ["ImageLoader 内存 + 磁盘 + 降采样", "实机", "封面二次 0ms；616 张实测落盘"],
  ];
  let cy = 1.18;
  rows.forEach((r, ri) => {
    const h = ri === 1 ? 0.5 : 0.42;
    const head = ri === 0;
    let cx = 0.45;
    r.forEach((cell, ci) => {
      const w = colW[ci];
      s.addShape(pres.ShapeType.rect, { x: cx, y: cy, w: w, h: h, fill: { color: head ? C.blue : (ri % 2 === 1 ? "FFFFFF" : C.blueSoft) }, line: { color: "D8DEE9", width: 0.6 } });
      s.addText(cell, { x: cx + 0.08, y: cy + 0.02, w: w - 0.16, h: h - 0.04, fontFace: FONT, fontSize: 8.4, valign: "middle", bold: head, color: head ? "FFFFFF" : C.ink });
      cx += w;
    });
    cy += h;
  });
  conclusion(s, 0.45, cy + 0.14, 9.1, 0.8, "", [
    "三种工具覆盖三层：帧率用 dumpsys gfxinfo、接口耗时用 curl、加载耗时用实机秒表",
    "先测量再定位：热歌榜那 28.4s 是 curl 先测出「慢」而非「失败」，才没在客户端瞎改超时",
    "首次场景实测：全新安装 0.2~2s、App 重启 0ms、中转重启 0ms、进程内切页 0ms",
  ], 2);
  footer(s);
}
// ============ P23 总结 ============
{
  const s = pres.addSlide();
  header(s, 0, "三阶段收获与下一步计划", "一条主线：基础 → 架构 → 实战");
  const gains = [
    [1, "阶段一 · 打地基", "掌握布局与常用控件、Activity / Fragment 生命周期、列表绑定与局部刷新；形成「单 Activity + Fragment 容器」的骨架范式与五条编码规范意识。"],
    [2, "阶段二 · 立架构", "从「写界面」升级到「分层架构」：MVVM + ViewBinding + LiveData + ViewModel 生命周期；用前台服务 + MediaSession 打通后台播放与跨进程控制；沉淀拆流 / 去重 / 代数三个可迁移范式。"],
    [3, "阶段三 · 上项目", "把架构用到真实在线播放链路上：学会按链路排障、按步骤放缓存、按感知做延迟优化；理解「把等待藏起来」比「做得快」更重要。"],
  ];
  gains.forEach((g, i) => {
    const y = 1.26 + i * 0.86, m = ST[g[0]];
    card(s, 0.45, y, 9.1, 0.74, { fill: m.soft, line: m.color });
    s.addShape(pres.ShapeType.rect, { x: 0.45, y, w: 0.06, h: 0.74, fill: { color: m.color }, line: { type: "none" } });
    s.addText(g[1], { x: 0.62, y, w: 1.5, h: 0.74, valign: "middle", fontFace: FONT, fontSize: 10.5, bold: true, color: m.color });
    s.addText(g[2], { x: 2.2, y, w: 7.2, h: 0.74, valign: "middle", fontFace: FONT, fontSize: 8.8, color: C.ink });
  });
  card(s, 0.45, 3.98, 4.45, 1.14, { line: C.blue });
  ctitle(s, 0.6, 4.06, 4.1, "能力提升", C.blue, 10.5);
  bullets(s, 0.6, 4.36, 4.15, 0.7, [
    "读代码：能从 UI 逆着链路追到数据源（Fragment → VM → Api / 单例）",
    "写代码：去重、代数校验、缓存分层成为默认动作",
  ], { size: 8.8, gap: 2 });
  card(s, 5.1, 3.98, 4.45, 1.14, { line: C.orange });
  ctitle(s, 5.25, 4.06, 4.1, "不足与下一步", C.orange, 10.5);
  bullets(s, 5.25, 4.36, 4.15, 0.7, [
    "单元测试尚未补齐；Room 迁移机制未深入",
    "计划尝试 Flow / StateFlow 的声明式拆流与 distinctUntilChanged",
  ], { size: 8.8, gap: 2 });
  footer(s);
}

pres.writeFile({ fileName: OUT }).then((f) => console.log("OK ->", f));
