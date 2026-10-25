# 小米定制版维护说明

## 分支和基线

- 仓库：`https://github.com/qingkanc/aerial-tv.git`。
- `mitv-custom`：已合并上游正式标签 `v1.50`（`117ca4e`），合并提交 `1e359d1`；2026-10-25 已覆盖安装 `1.50-mitv / 58`。
- `main`：保持上传前的上游代码状态，本次未改动。
- 当前安装版本：`1.50-mitv`，`versionCode = 58`，standard release；基础显示与 Home 返回已核验，重启/待机尚未验收。
- 上游：`https://github.com/pengchujin/aerial-tv.git`。后续优先合并正式发布标签，避免把未发布主分支当作已验证版本。

## 必须保留的定制

| 位置 | 用途 |
| --- | --- |
| `app/build.gradle.kts` | applicationId 为 `com.thunderstone.idun.dev`；定制版本号 |
| `guard/RevivalSync.kt`、`res/xml/revival_account.xml`、`res/xml/revival_sync.xml` | 同步账号类型与定制包名一致 |
| `ui/control/ControlCenter.kt` | 日期独占网络与时钟下方整行，避免长 SSID 挤压日期 |

1.50 起农历采用上游 `Lunar.kt`：中文大时钟显示农历，控制中心不追加农历；移除本地节气、节日、农历依赖及预热缓存。

上表 Kotlin 路径相对 `app/src/main/java/app/orchard/tv/`，资源相对 `app/src/main/`。

本地 v1.50 验证：两个 release 构建、lintVital 及 31 项测试通过。构建使用 `ANDROID_USER_HOME=local/android-user`，standard APK 包名、版本 58 / 1.50-mitv 与原签名已核对。已安装电视，验收范围见下文。

这台小米通过现有定制包身份与系统默认 HOME 设置直接进入 Aerial，不依赖无障碍桌面守护。升级时保留包名、MainActivity 类名、原签名和已有授权，不停用小米原桌面，不自动启用额外守护。仅改 namespace 或版本名不能代替 applicationId 的保留。

## 签名和用户数据

覆盖升级必须使用当前电视上相同的签名密钥。密钥只在本机保存，不随源码上传。

当前证书 SHA-256（公开校验信息，不是密钥）：

```text
eff67c2cc90babd86f12eb6a1e73598c4de3109d1817f356fa34e3eff9ae7c25
```

本次构建使用已有 Android 用户目录中的 `debug.keystore`，构建类型仍是经过压缩优化的 release。换电脑时应私下迁移原密钥，不能重新生成。

可继续将 `ANDROID_USER_HOME` 指向保存原 `debug.keystore` 的目录；或者按现有 Gradle 配置，在被忽略的 `local/` 中配置 `signing.properties` 和原密钥。配置字段为 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`，使用原密钥的实际值。缺少指定签名时 Gradle 会回退到当前机器的 debug key，构建成功不代表能覆盖更新，安装前必须比对签名。

壁纸照片、应用排序、文件夹、天气城市等保留在电视应用私有数据中。仓库不保存用户数据、IP、手机设置页令牌、签名文件或 APK。通过同包名同签名的 `adb install -r` 保留配置；不要卸载 Aerial、不要执行 `pm clear`。

## 构建和安装

使用 JDK 17、Android SDK 36，沿用仓库 Gradle Wrapper。首次构建会下载依赖。

```bash
# 设置 JAVA_HOME、ANDROID_HOME，以及指向原签名所在目录的 ANDROID_USER_HOME。
./gradlew assembleStandardRelease testStandardReleaseUnitTest

# 安装前核对 applicationId、versionCode、versionName 和证书。
aapt dump badging app/build/outputs/apk/standard/release/app-standard-release.apk
apksigner verify --print-certs app/build/outputs/apk/standard/release/app-standard-release.apk

# TV_SERIAL 是用户当前电视的 ADB 序列号/IP:端口。
adb -s "$TV_SERIAL" install -r app/build/outputs/apk/standard/release/app-standard-release.apk
adb -s "$TV_SERIAL" shell cmd package compile -m speed -f com.thunderstone.idun.dev
adb -s "$TV_SERIAL" shell am start -W -n com.thunderstone.idun.dev/app.orchard.tv.MainActivity
```

不要直接运行上游官网安装脚本或安装上游原版 APK；它们使用不同包名与签名。屏保组件继续为 `com.thunderstone.idun.dev/app.orchard.tv.dream.AerialDream`，不要恢复已移除的 CleanYourTV 时钟屏保。

## 后续升级

1. 从当前 `mitv-custom` 建立升级分支，保持已有工作区改动安全。
2. 首次配置上游远端：`git remote add upstream https://github.com/pengchujin/aerial-tv.git`；已有时直接使用。执行 `git fetch upstream --tags`，核对 GitHub 正式 Release 对应标签。
3. 在升级分支执行 `git merge <正式发布标签>`，解决冲突。重点检查 Gradle 中包名与版本号、HomeChrome 中上游农历和时钟刷新、账号类型 XML；不要整文件覆盖上游的新实现。
4. 保留 `-mitv` 版本名后缀，versionCode 必须大于电视当前值（当前 58）。核对上游是否改变偏好、排序或壁纸数据格式。
5. 完成 release 构建和回归，核对签名，备份旧 APK 并记录当前配置后覆盖安装。
6. 真机检查冷启动、Home 返回、壁纸与应用顺序、农历显示、控制中心、手机设置页和屏保播放/退出；涉及开机路径变更时另做整机重启验证。
7. 验证通过后合并回 `mitv-custom` 并推送，更新本文基线和验证记录。

## 2026-10-24 验证记录

- release 构建、lintVital 及 26 项测试通过，0 失败/错误。
- MiTV-MFFU0 上从 1.41-mitv 覆盖升级为 1.46-mitv；签名相同，首次安装时间未变。
- 默认 HOME 保持定制组件；从小米设置按 Home 返回 Aerial；无障碍服务仍未启用。
- 桌面与控制中心正确显示农历九月十五；原壁纸、首排应用顺序、大时钟及单排布局保留。
- 手机配置接口中的壁纸、屏保、焦点样式不变，应用集合保留；新版手机设置页可访问。
- 航拍屏保预览正常播放，返回后恢复桌面。固件的标准自动 Dream 限制未改变。
- 本次验证应用冷启动，未做整机重启；未来节气/节日通过日期回归测试验证，未修改电视时间。

## 2026-10-25 本地升级 v1.48

- 本地构建版本为 `1.48-mitv`，`versionCode = 56`；保留定制包名、签名配置、同步账号类型、农历和日期/玻璃绘制缓存优化。
- 移除尚未上机的控制中心玻璃增强（高亮边缘和冷暖内倒角），采用上游普通玻璃、300dp 面板和动效，保留上游流畅模式。
- 合并前的未提交改动另存于本机备份；除玻璃增强实现及其专属对照夹具外，其余改动保持未提交。控制中心浅色壁纸、导航及重开回归继续保留。
- 本次只做本地验证，不安装电视；上游真机性能数据不代表本机已验收。
- 本地验证：JDK 17 下 `assembleStandardRelease assembleGuardRelease testStandardReleaseUnitTest` 通过，两个 release 变体的 lintVital 通过；补充完整/流畅模式及主页空闲预热后的控制中心导航、关闭重开像素一致性回归后，再次运行全部 35 项测试，0 失败、0 错误、0 跳过。standard APK 包名、版本 56 / 1.48-mitv 和签名（`eff67c2c…9ae7c25`）核对通过。真实电视性能、启动和遥控器操作未验证。

## 2026-10-25 真机覆盖安装

- `8814793` 的 standard release 同包名、同签名覆盖安装成功，版本 `1.48-mitv / 56`；首次安装时间未变，未卸载或清空数据。
- 原壁纸、首排顺序、大时钟、农历与天气显示保留，19 个磁贴加载；控制中心日期和有线网络分行显示正常。
- 默认 HOME、无障碍、Dream 组件和停用包清单未改；从小米设置注入 HOME 后返回桌面。未做实体遥控器、重启、待机、视频屏保和手机页验收。
- 已执行规定的 `speed` 编译命令；固件最终报告实际编译过滤器为 `speed-profile`，没有改写系统编译属性。
- 面板底层模式为 3840×2160@60Hz，但系统 `config_maxUiWidth=1920`，DisplayManager、应用窗口和界面合成空间均为 1920×1080。本次未修改分辨率；系统是否允许原生 4K UI 尚待进一步验证。
- 安装包备份、签名核对、截图和原始输出保存在本机 `local/artifacts/aerial-tv/upgrade-1.48/`。

## 2026-10-25 v1.50 真机覆盖安装

- `1e359d1` 已推送至 `origin/mitv-custom`；MiTV-MFFU0 同包名同签名覆盖升级至 `1.50-mitv / 58`，首次安装时间保持不变。
- 截图核对原壁纸、首排顺序、大时钟和天气城市保留；大时钟显示上游农历一次，控制中心只显示公历，版本显示 1.50。
- 从系统设置注入 HOME 返回 Aerial；默认 HOME、无障碍、Dream 组件及停用包清单与安装前一致。未清空数据、卸载应用或修改系统接管设置。
- 执行 `cmd package compile -m speed -f`，固件实际编译过滤器为 `speed-profile`（PERFORMED）。当前崩溃缓冲未发现本包崩溃。
- 未做实体遥控器、整机重启、待机唤醒、手机页及新增视频/信号源壁纸验收。
- 旧 APK、截图和原始输出保存在本机 `local/artifacts/aerial-tv/upgrade-1.50/`。
