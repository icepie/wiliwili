# Android ExoPlayer Playback Status

本文档记录 Android 播放兼容性改造当前阶段状态，便于后续继续开发。

## 当前目标

Android 平台在启用 `ExoPlayer` 播放器内核时，不再显示旧的 C++/borealis 视频详情页，而是直接进入 Android 原生 ExoPlayer 播放详情页。该页负责拉取视频详情、播放地址、弹幕，并使用 Android 原生硬解播放，以改善 4K、HDR、杜比等视频在 mpv Android 路径下的兼容性和性能。

非 Android 平台、以及 Android 上选择 `mpv` 播放器内核时，继续使用原有 wiliwili 播放页逻辑。

## 已实现

### 播放器内核设置

- 新增 `SettingItem::PLAYER_CORE`。
- Android 默认播放器内核为 `ExoPlayer`，其它平台默认仍为 `mpv`。
- 设置页新增 Android 专属“播放器内核”选项：`mpv` / `ExoPlayer`。
- 保留 wiliwili 原有设置体系，没有引入 BLBL 的全局设置体系。

### Android 入口分流

- Android + `ExoPlayer` 模式下，`Intent::openBV()` 会直接通过 JNI 调用 `PlatformUtils.openExoPlayerBv()`。
- 新入口打开 `cn.xfangfang.wiliwili.player.ExoPlayerActivity`。
- 旧 C++ `PlayerActivity` 不再作为 BV 视频的可见中间页出现。
- `mpv` 模式和其它平台仍走原有 `PlayerActivity`。

### Android ExoPlayer 播放页

新增 Java 播放页：

- `android-project/app/src/main/java/cn/xfangfang/wiliwili/player/ExoPlayerActivity.java`
- `android-project/app/src/main/java/cn/xfangfang/wiliwili/player/CdnFailoverDataSource.java`
- `android-project/app/src/main/java/cn/xfangfang/wiliwili/player/DanmakuOverlayView.java`

能力：

- 通过 `/x/web-interface/view` 拉取 BV 视频详情。
- 通过 `/x/web-interface/nav` 获取 WBI keys。
- Java 侧实现 WBI 签名，调用 `/x/player/wbi/playurl` 获取播放地址。
- 解析 DASH video/audio，选择 HEVC 优先的视频轨和标准 AAC 优先的音频轨。
- 使用 ExoPlayer + Media3 + OkHttpDataSource 播放。
- 支持视频 URL 和音频 URL 候选列表。
- 参考 BLBL，实现 DataSource 级 CDN failover：
  - 单个 CDN 打开失败时，在 DataSource 内切换候选 URI。
  - 避免频繁重建整个播放器导致卡顿或黑屏。
- 支持播放、暂停、进度拖动、画质切换、音频候选切换、倍速切换、画面适应/填充。
- 默认隐藏详情面板，只在 OSD 中点击“详情”或设置项时展开。
- 添加基础弹幕 Overlay View，并接入播放时间。

### wiliwili 风格 OSD

当前 Android ExoPlayer 页已改为接近 wiliwili 原播放器的结构：

- 顶部渐变标题栏。
- 底部渐变控制栏。
- 大播放按钮。
- 进度条和左右时间。
- 弹幕、画质、倍速、详情、音频、全屏入口。
- 中央 loading/hint。
- 详情面板默认隐藏，避免遮挡视频。

当前 UI 只是阶段版，还不是像素级复刻，后续还需要继续精修图标、焦点、TV 遥控体验、动画和菜单样式。

### Android mpv 兼容修复

保留并合并了前置 Android mpv 兼容修复：

- Android mpv 初始化显式设置音频输出优先级。
- Android 主题 `auto` 可读取系统深浅色。
- 桌面窗口/全屏/置顶等选项从 Android 平台排除。
- Android 默认硬解方法调整。
- Android 上 Dolby/Hi-Res 音轨优先回退到标准 AAC，避免设备支持 Dolby Atmos 但应用链路无声。

## 真机验证

测试设备日志显示：

- 新 Android 播放页成功启动并拉取视频详情。
- 成功请求 WBI 播放地址。
- 成功解析 DASH 播放地址。
- ExoPlayer 可进入 `Playback ready`。
- 视频硬解使用 Qualcomm HEVC decoder：
  - `OMX.qcom.video.decoder.hevc`
- 音频使用 AAC decoder：
  - `c2.android.aac.decoder`
- CDN failover 生效：
  - 首个 CDN 超时后自动切换到下一个候选。

示例日志：

```text
WiliwiliExoPlayer: 加载视频详情
WiliwiliExoPlayer: 请求播放地址
WiliwiliExoPlayer: Parsed playurl quality=116 videos=3 audios=3
WiliwiliExoPlayer: Video decoder initialized decoder=OMX.qcom.video.decoder.hevc
WiliwiliExoPlayer: Audio decoder initialized decoder=c2.android.aac.decoder
WiliwiliExoPlayer: Playback ready duration=552780 audioIndex=0 videoIndex=0
WiliwiliExoPlayer: CDN failover kind=video selected=1
```

当前验证过的构建命令：

```sh
cd android-project
rtk env JAVA_HOME=/usr/lib/jvm/java-17-openjdk ANDROID_HOME=/home/icepie/Android/Sdk ANDROID_SDK_ROOT=/home/icepie/Android/Sdk ./gradlew --no-daemon :app:assembleDebug -PANDROID_ABIS=arm64-v8a
```

APK 输出：

```text
android-project/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

## 当前未完成

### 弹幕解析

弹幕请求已经接入，`DanmakuOverlayView` 已实现基础滚动/顶部/底部弹幕绘制，但当前真机日志中弹幕 XML 仍解析失败。

现象：

```text
Parse danmaku failed: Unexpected token
弹幕 0 条
```

已尝试：

- 强制 `Accept-Encoding: identity`。
- gzip magic bytes 解压。
- zlib magic bytes 解压。
- raw deflate 兜底。

后续建议：

- 参考 BLBL 当前弹幕接口实现，不再只用 `/x/v1/dm/list.so`。
- 优先接入分段弹幕接口或 BLBL 的弹幕加载逻辑：
  - 弹幕元数据。
  - 分段弹幕。
  - 弹幕过滤配置。
- 如继续使用 XML 接口，需要抓取 response headers/body head，确认真实压缩格式或是否返回 protobuf/错误页。

### 详情页仍是轻量版

Android 新详情页当前只显示：

- 标题。
- UP 名。
- 播放/弹幕计数。
- 分 P。
- 简介。

后续可继续补：

- 点赞、投币、收藏。
- 评论列表。
- 推荐视频。
- UP 信息卡片。
- 多 P/合集更完整的列表。
- 从播放页打开当前详情或 UP 主页。

### 历史进度上报

当前 Java 播放页只在 logcat 中记录进度：

```text
Progress reason=tick aid=... cid=... position=... duration=...
```

尚未真正调用 B 站历史上报接口。

后续可选方案：

- Java 侧直接调用 `/x/v2/history/report`，需要传入 csrf/cookie。
- 或通过 JNI 回调 C++，复用现有 `reportCurrentProgress()`。

### 番剧 / 直播路径

目前 Android 新入口主要覆盖 BV/UGC 视频。

后续需要继续接：

- `Intent::openSeasonBySeasonId()`。
- `Intent::openSeasonByEpId()`。
- `Intent::openLive()`。
- PGC playurl 和剧集详情。
- 直播 HLS/FLV 播放与弹幕。

### UI 精修

当前 OSD 已比 Android 默认 Button 版干净，但还需要继续复刻 wiliwili：

- 替换文字按钮为图标或更接近 wiliwili 的视觉。
- 统一间距、字号、渐变和焦点样式。
- TV/手柄遥控焦点导航。
- 底部进度条样式。
- OSD 自动隐藏与触摸区域。
- 画质、音频、倍速菜单样式。

## 注意事项

### 被 `.gitignore` 忽略的 Java 文件

仓库 `.gitignore` 忽略了 `android-project`，因此新增 Java 文件不会自动出现在普通 `git status` 中。

提交时必须强制添加：

```sh
git add -f android-project/app/src/main/java/cn/xfangfang/wiliwili/player/ExoPlayerActivity.java
git add -f android-project/app/src/main/java/cn/xfangfang/wiliwili/player/CdnFailoverDataSource.java
git add -f android-project/app/src/main/java/cn/xfangfang/wiliwili/player/DanmakuOverlayView.java
```

### 参考 BLBL

后续遇到不确定的功能实现，可以继续参考：

```text
https://github.com/cat3399/blbl
```

已参考的 BLBL 方向：

- ExoPlayer + OkHttpDataSource。
- DataSource 级 CDN failover。
- 播放 OSD 分层。
- 弹幕覆盖层思路。
- 播放设置项名称和菜单组织。

后续重点参考文件：

```text
app/src/main/java/blbl/cat3399/feature/player/PlayerActivity.kt
app/src/main/java/blbl/cat3399/feature/player/CdnFailoverDataSource.kt
app/src/main/java/blbl/cat3399/feature/player/engine/ExoPlayerEngine.kt
app/src/main/java/blbl/cat3399/feature/player/danmaku/
app/src/main/java/blbl/cat3399/core/api/video/web/WebVideoApi.kt
```

