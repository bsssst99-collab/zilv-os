# 自律 OS · v1.3 交付包

一个人的自律打卡 App：起床洗漱拍照、出门定位/实拍、健身、心情日记，四项全达成才在日历上"点亮"当天表情。

## 一、这一版改了什么（v1.3 / versionCode 4）

1. **重做心情表情 + 月度日历 UI**：换成小清新水彩风的 5 档表情（丧 / 平静 / 开心 / 愤怒 / 烦恼），月度展示只显示「日期 + 自己的表情」，去掉了原来把日期和表情用绿色高亮框起来的样式，整体走方格纸小清新风格。
2. **日历点亮逻辑**：
   - 当天什么都没记 → 浅灰空色块；
   - 只记了心情、其他三项不全 → 表情 40% 透明度（半透明）；
   - 四项打卡全部完成 → 表情 100% 全亮。
3. **修复通知 / 闹钟不生效**：旧版用 Web Notification API，而 Android WebView 不支持，所以点「系统通知」会提示"这台设备不支持通知"。v1.3 改为走原生桥接（`NativeUI`），用系统 `NotificationManager` 发通知，并用 `AlarmManager` 调度后台起床 / 就寝提醒，**App 被杀也能响**。
4. **设置里新增版本更新**：显示当前版本号，点「检查更新」会拉取 GitHub 上的 `version.json`，有新版就下载并覆盖安装（需同签名）。详见下文「开启自动更新」。

## 二、安装

- 直接安装 `自律OS-v1.3.apk`。包名 `com.selfdrive.os`、签名与之前一致（SHA-256：`5cc30830…7ed2`），**覆盖安装不会清数据**，打卡与日记都在。
- 如果系统拦了"未知来源"，在设置里允许本次安装即可。

### 小米（MIUI）让闹钟/通知稳定响的额外设置

小米对后台管得比较严，装完后建议顺手开一下（一次性）：

1. **通知**：设置 → 通知与状态栏 → 通知管理 → 找到「自律 OS」→ 打开「允许通知」，并打开「悬浮通知」「锁屏通知」。
2. **自启动**：设置 → 应用设置 → 应用管理 → 自律 OS → 打开「自启动」（否则清后台后闹钟可能不响）。
3. **省电策略**：同上页面 → 省电策略 → 选「无限制」。
4. **精确闹钟**（Android 12+）：系统会自动授权；若发现闹钟不准点，在 App 通知设置里确认「闹钟和提醒」已开。

在 App 内：设置 → 提醒 → 打开「系统通知」开关，会自动跳到系统通知设置页引导你开启。

## 三、开启自动更新（GitHub 托管）

> ✅ **本仓库已配置好自动更新**：`自律OS.html` 顶部的 `UPDATE_JSON_URL` 已指向 `https://raw.githubusercontent.com/bsssst99-collab/zilv-os/master/version.json`，`version.json` 的 `apkUrl` 已指向 Releases 下载地址。下面的步骤仅供**将来发新版**时参考。

App 内「检查更新」会读取一个 JSON 来判断是否有新版。以后改完发新版，手机上点一下就能更新：

1. **建仓库**：在 GitHub 新建一个仓库（比如 `zilv-os`），把本交付包推上去。
2. **上传 APK**：在仓库的 **Releases** 里发一个 `v1.3.0`，把 `自律OS-v1.3.apk` 作为附件上传，得到下载地址。
3. **填 `version.json`**：把根目录的 `version.json` 里的 `apkUrl` 改成上一步的下载地址，`version` 改成最新版本号。`version.json` 也放在仓库根目录。
4. **告诉 App 去哪查**：打开 `自律OS.html`，找到顶部的：
   ```js
   const UPDATE_JSON_URL = "";
   ```
   改成 `version.json` 的原始地址，例如：
   ```js
   const UPDATE_JSON_URL = "https://raw.githubusercontent.com/你的用户名/zilv-os/main/version.json";
   ```
5. 重新打包（见下一节），把新 APK 装到手机。之后每次发新版：改 `自律OS.html` 里的 `APP_VERSION` + `build_apk.sh` 里的 `version-name`，打包后上传到 Releases，并把 `version.json` 的 `version`/`apkUrl` 更新一下，手机上点「检查更新」就会下载覆盖安装。

> 覆盖安装的前提：**包名相同 + 签名相同**。签名密钥是 `安卓工程/签名/selfdrive.jks`（alias `selfdrive`，口令 `selfdrive2026`），千万别丢、别换。

## 四、目录

```
自律OS.html                 网页版源码（单文件，自包含）
自律OS-v1.3.apk             安卓安装包（targetSdk 32 / minSdk 21）
version.json                更新信息（GitHub 托管后填地址）
素材/                       旧版表情（已弃用）
素材/新表情/                 v1.3 水彩风 5 张心情表情 PNG（丧/平静/开心/愤怒/烦恼）
安卓工程/                   Android 源码（Manifest + Java + 资源 + assets/index.html）
安卓工程/src/com/selfdrive/os/
  MainActivity.java         WebView 宿主
  NativeUI.java             网页↔原生桥（主题/通知/闹钟/更新）
  Notify.java               通知渠道与发送
  AlarmSched.java           AlarmManager 后台闹钟调度
  AlarmReceiver.java        闹钟到点接收器
  BootReceiver.java         开机 / 覆盖安装后重建闹钟
  FileProviderLite.java     拍照与安装 APK 的文件共享
安卓工程/签名/selfdrive.jks 签名密钥（升级必须用同一把，别丢）
build_apk.sh                一键重打包脚本
预览/                       界面截图
```

## 五、重新打包（改完代码后）

依赖：JDK 17、Android SDK（build-tools 33.0.2、platforms/android-33）。

```bash
export ANDROID_HOME=/opt/android-sdk     # 改成你的 SDK 路径
./build_apk.sh
```

脚本会：
1. 把 `自律OS.html` 同步到 `安卓工程/assets/index.html`；
2. 编译 Java → dex → 打包 → 签名；
3. 输出 `自律OS-v1.3.apk` 并校验签名。

版本号在 `build_apk.sh` 顶部的 `VER_CODE` / `VER_NAME`，以及 `自律OS.html` 顶部的 `APP_VERSION`（两处要一致）。

> 构建须用 JDK 17：`build-tools 33.0.2` 里的 D8 与 JDK 25 不兼容（会抛 NPE），脚本里已默认指向 JDK 17。

## 六、数据

- 全部存在 WebView 的 localStorage，键名 `selfdrive.v1`；结构 `{settings, days:{"YYYY-MM-DD":{wake,out,gym,mood}}, flags}`。
- 照片只留最近 24 天，压缩到最长边 620px。
- 设置页可导出/导入 JSON 备份。

## 七、素材许可

v1.3 心情表情为按参考风格重新绘制的水彩风素材；旧版表情取自 Microsoft Fluent Emoji（MIT License）。
