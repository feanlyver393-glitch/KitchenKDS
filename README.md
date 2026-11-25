# KitchenKDS V4.0 — 商用级后厨显示系统

LG V50 强制横屏挂载，接收 Easy POS 经 CH340 串口推送的 ESC/POS 热敏字节流，
剥离控制指令 → EUC-KR 解码 → 中韩双语 → 两级分类 → Room 持久化 → 取消单自动合并 → 可调尺寸/主题/音效大屏。

---

## ⚡ V4.2 真机回归修复（本次）

| # | 问题 | 修复 |
|---|---|---|
| 1 | 前台自助机表头 `주문자:주문전용 2026-11` 被当成菜品 | NoiseLineFilter 新增 SYSTEM_NOISE_TOKENS（주문자/주문전용/주문접수/주문번호/영수증/매출），去空格后 contains 即整行丢弃 |
| 2 | 菜名带 `1 추가` 后缀导致词典查不到 | TextNormalizer.cleanDishName()：`\s*[0-9]*\s*추\s*가\s*# KitchenKDS V4.0 — 商用级后厨显示系统

LG V50 强制横屏挂载，接收 Easy POS 经 CH340 串口推送的 ESC/POS 热敏字节流，
剥离控制指令 → EUC-KR 解码 → 中韩双语 → 两级分类 → Room 持久化 → 取消单自动合并 → 可调尺寸/主题/音效大屏。

---

 剥掉行尾追加描述，再查词典 |
| 3 | 菜品多时底部状态栏被列表滚出去 | OrderCard 结构重排：状态栏移到 Column 第一项，列表独立滚动 |
| 4 | 主界面顶部大片黑边把卡片往下挤 | MainScreen 拆掉顶栏，Box 叠放，网格撑满，图标 TopEnd 悬浮 |

### 菜名清洗的边界

    향라오징어 1 추가   → 향라오징어   ✔
    향라오징어 2추가    → 향라오징어   ✔
    향라오징어추가      → 향라오징어   ✔
    마라쇼롱샤 추가요리 → 原样保留     ✔（只吃行尾，不动中间）
    명품양갈비 -1취소   → 原样保留     ✔（취소 由 CancelSignal 处理，两条链路互不干扰）

### 卡片为什么用 Column+scroll 而不是 LazyColumn

LazyColumn 在有界约束下会**填满**父高度（测出来就是 maxH），
套进 heightIn(max) 的卡片里会让每张卡都变成半屏高，等于废掉「按内容自适应」。
后厨一单通常 3~10 道菜，虚拟化收益可以忽略，所以这里是
`Column + verticalScroll(状态栏在外层不参与滚动)`，既保住自适应高度，又保证状态栏永不被遮。

---

## 一、V4 六大模块

| 模块 | 内容 |
|---|---|
| 1 | 设置页可滚动、数据导出下沉三级、主界面安全区、卡片去掉冗余留白 |
| 2 | Slider 全改 Stepper、尺寸真正全局生效、预览左右布局、底部栏改版、卡片高度自适应 |
| 3 | 菜品单行「韩文 - 中文」+ 跑马灯 |
| 4 | MediaPlayer 来单音效（内置 5 档 + 自定义导入 / 闹钟通道 / 次数·间隔可调） |
| 5 | 数据重置（手动二次确认 + 按系统时间的定时清理） |
| 6 | POS 취소 取消单自动合并 + 红线渲染 + 取消后重单自动恢复 |

---

## 二、模块 1：布局与交互修复

- **设置页可滚动**：根 Column 加 verticalScroll(rememberScrollState())，横屏 411dp 高度也能滑到底。
- **数据导出下沉**：从设置首页的四个平铺项收进独立三级页「数据导出」（ExportScreen），首页只留一个入口。
- **主界面防误触**：根 Box 叠 safeDrawingPadding() + 16dp 横向安全边距，戴手套也不会蹭到系统手势区。
- **卡片留白**：内容区 padding 由 12/6 收到 10/3，SectionLabel 的 top padding 4dp，卡片顶部不再空一条。

---

## 三、模块 2：尺寸面板与卡片高度

### Slider → Stepper

Slider 在横屏小高度里必重叠（三个滑块 + 标签 > 300dp），全部换成 +/- 步进器：

| 项 | 步长 | 范围 |
|---|---|---|
| 卡片宽度 | 10dp | 200 ~ 400 |
| 行间距 | 1dp | 0 ~ 16 |
| 字体缩放 | 0.05 | 0.8 ~ 1.5 |

### 预览布局

    设置 → 尺寸调整
    ┌──────────────┬──────────────┐
    │  Dummy 卡片  │  卡片宽度     │
    │  scale 0.8   │  [-] 260dp [+]│
    │              │  行间距       │
    │              │  [-] 4dp   [+]│
    │              │  字体缩放     │
    │              │  [-] 1.00x [+]│
    └──────────────┴──────────────┘

去掉「实时预览」字样，左右结构，点一下立即写 DataStore 并全局生效。

### 底部状态栏

- 底色：< 15 分钟天蓝 #29B6F6，>= 15 分钟浅黄 #F9A825
- **移除「新单/旧单」文字**
- 左侧桌号：**黑色**（含「号」字），不论蓝底黄底
- 右侧：出单时间 + 计时器，**白色**
- 卡片变窄时自动降字号（< 230dp 降一档，< 180dp 再降一档），不会遮挡

### 高度自适应

    弃用 fillMaxHeight
    卡片高度 = wrapContent（按菜品数量自然包裹）
    上限     = configuration.screenHeightDp.dp / 2

超出上限时卡片内部滚动，不再强行撑满一屏。

---

## 四、模块 3：菜品排版

单行显示，前韩文后中文：

    마라쇼롱샤 - 麻辣小龙虾        × 2

超宽走 Modifier.basicMarquee() 横向滚动，不再截断成省略号。
点击仍是变绿 + 删除线；取消态见模块 6。

---

## 五、模块 4：音效提醒（V5 重构）

**为什么换掉 SoundPool**：SoundPool 有 1MB 解码缓冲上限，且部分机型对用户导入的 MP3/M4A 解码不兼容，
表现就是"设置页能听、装到别的手机来单没声"。V5 改用 MediaPlayer + 闹钟通道 + ToneGenerator 兜底。

- 引擎：`core/audio/AlertSoundPlayer.kt`
  - `AudioAttributes`：`USAGE_ALARM`（默认，静音/震动也会响，可在设置页切 `USAGE_MEDIA`）
    + `CONTENT_TYPE_SONIFICATION`
  - 每次播放前 `release()` 上一个实例；新单到达取消上一轮协程循环，不叠音
  - 降级链：自定义文件 → 内置 raw → `ToneGenerator(STREAM_ALARM)` 蜂鸣（绝不哑火）
  - 每次播放把 stream / 音量 / 铃声模式写进 RingLog，换机排查有据可查
- 内置音源：`res/raw/alert_01..05.wav`（16bit PCM 单声道 44.1kHz，随 APK 一起分发，已声明 noCompress）
  1. 叮咚（双音） 2. 清脆铃（单响） 3. 低频双响 4. 韩式点单（三连音） 5. 长警报
- 自定义音效：设置页「从手机选择音频文件」→ SAF `OpenDocument("audio/*")` →
  在 IO 线程把文件完整复制到 `filesDir/custom_alert_sound`，DataStore 只存展示文件名
  （绝不存 content:// URI，否则重启/换目录后读权限丢失 → 哑火）
- 设置路径：设置 → 音效设置（开关 · 闹钟/媒体通道 · 5 个内置音 + 自定义 · 导入 · 次数 1~5 · 间隔 0~10 秒 · 试听）

**触发时机**：只有真正插入了新卡才响。取消单与「取消后重单恢复」都不响 —— 后厨不该为一次撤销听到提示音。

---

## 六、模块 5：数据重置

### 手动

设置 → 数据重置 → 订单重置 → AlertDialog 二次确认 → deleteEverything()。

### 定时（V4.1 重写：按系统时间，不是倒计时）

设置 → 数据重置 → 定时重置：
- Checkbox 开启
- 小时 / 分钟 Stepper（5 分钟一档）+ 04:00 / 09:00 / 23:00 快捷档
- 显示**下次执行的绝对时间**（11-04 04:00 周二），不是「还剩几小时」

**为什么不用倒计时**

delay / WorkManager initialDelay 算的是「从入队那一刻起过了多久」，
设备休眠、Doze、进程重启都会让触发点漂移，而且与系统时钟脱钩。

**现在用系统墙上时间**

    AlarmManager.setExactAndAllowWhileIdle(
        RTC_WAKEUP,
        下一个 HH:mm 的绝对时间戳,
        pendingIntent
    )

语义与手机自带「定时关机」完全一致：到系统时间 09:00 就触发。
用户改系统时间、改时区、重启手机后，由 BootReceiver 按新时间重新排队。

| 场景 | 处理 |
|---|---|
| 闹钟触发 | DailyResetReceiver 收广播 → 清库 → 立刻排明天 → finish |
| 手机重启 | BOOT_COMPLETED → BootReceiver 读 DataStore 重排 |
| 改系统时间 / 改时区 | TIME_SET / TIMEZONE_CHANGED → 重排 |
| 覆盖安装 | MY_PACKAGE_REPLACED → 重排 |
| 无精确闹钟权限（A12+） | 降级 setAndAllowWhileIdle，仍是绝对时间点，可能延迟几分钟；UI 给出授权入口 |

---

## 七、模块 6：취소 取消单合并（核心业务逻辑）

### 数据模型

order_items 新增 isCancelled INTEGER NOT NULL DEFAULT 0（Room version 1 → 2，带 Migration）。

### 合并闸门 OrderIngestUseCase

    ① 取消单（菜名含 취소）
       还原菜名：마라쇼롱샤 -1취소 → 마라쇼롱샤
       查该桌最新未完成单 → 命中则 isCancelled = true
       整单都是取消项 → 不插入新卡，直接 return

    ② 取消后重新下单（正常单，原单同名菜 isCancelled = true）
       → isCancelled = false，数量更新为新单数量
       全部菜都被复活 → 不插入新卡

    ③ 纯新单 / 混合单
       → 剩下的菜组装成新卡插入

菜名比较忽略空格与大小写（打印机常插对齐空格）。

### UI 渲染

isCancelled 为 true 时：
- 文字变灰（Color.Gray alpha 0.55）
- Modifier.drawWithContent 画**红色双横线**（y = 高度一半 ± 2dp，线宽 2dp）

由于是 Room Flow 驱动，数据库里 isCancelled 改回 false 的瞬间红线自动消失，厨师立刻知道这道菜又要做了。

---

## 八、数据库

    orders
      id, tableNo, kind, receivedAtMillis, completedAtMillis,
      isCompleted, seq, foodItemCount, beverageItemCount, totalQuantity, rawText

    order_items
      id, orderId(FK CASCADE), koreanName, chineseName,
      category(FOOD|BEVERAGE), quantity, unitHint, isDone,
      isCancelled(V4), sortIndex, rawLine

schema：app/schemas/com.kitchenkds.data.local.AppDatabase/2.json

---

## 九、目录结构

    com.kitchenkds
    ├── KitchenKdsApp.kt            Application：DB / Repo / Prefs / Sound / Ingest
    ├── MainActivity.kt             横屏 + 常亮 + 沉浸全屏 + 主题注入
    ├── core/
    │   ├── audio/                  SoundManager / ToneSynth（V4）
    │   ├── log / ext / state
    ├── data/
    │   ├── serial/                 UsbSerialHub / SerialConfig / SerialEvent
    │   ├── decode/                 EscPosByteFilter / EucKrDecoder / TextNormalizer
    │   ├── parser/                 FrameSplitter / OrderLineParser / TableNoExtractor / NoiseLineFilter
    │   ├── pipeline/               OrderPipeline
    │   ├── local/                  AppDatabase(2) / OrderDao / OrderMapper / entity
    │   ├── prefs/AppPreferences.kt DataStore（尺寸/主题/音效/定时）
    │   ├── work/                   ResetScheduler / DailyResetReceiver / BootReceiver（V4.1）
    │   ├── export/ExportUtil.kt
    │   └── repository/OrderRepository.kt
    ├── domain/
    │   ├── model/                  Order / OrderItem(isCancelled) / ItemCategory / OrderKind / UiSettings
    │   ├── dict/                   MenuTable / MenuDictionary / MenuEntry
    │   ├── parser/CancelSignal.kt  取消单识别（V4）
    │   └── usecase/                AssembleOrderUseCase / OrderIngestUseCase（V4）
    └── ui/
        ├── KdsApp.kt               十页导航
        ├── common/CommonUi.kt
        ├── main/                   MainScreen / OrderGrid / OrderCard / ItemSection / OrderItemRow
        ├── history/HistoryScreen.kt
        ├── settings/               Settings / Connection / Size / Theme / Export / Sound / DataReset / ScheduleReset
        ├── theme/                  KdsColors / Theme / Type
        └── viewmodel/              KdsViewModel / KdsUiState

---

## 十、构建

    gradlew.bat assembleDebug

JDK 17 / SDK 34 / build-tools 34.0.0 / Gradle 8.7
Room 2.6.1 + KSP / DataStore 1.1.1 / usb-serial-for-android 3.7.3（jitpack）