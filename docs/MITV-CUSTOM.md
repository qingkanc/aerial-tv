# 小米定制版维护说明

## 分支和基线

- 仓库：`https://github.com/qingkanc/aerial-tv.git`。
- `mitv-custom`：电视上实际使用的定制代码；本次基于上游正式标签 `v1.46`，提交 `f4e95ff`。
- `main`：保持上传前的上游代码状态，本次未改动。
- 当前安装版本：`1.46-mitv`，`versionCode = 49`，standard release。
- 上游：`https://github.com/pengchujin/aerial-tv.git`。后续优先合并正式发布标签，避免把未发布主分支当作已验证版本。

## 必须保留的定制

| 位置 | 用途 |
| --- | --- |
| `app/build.gradle.kts` | applicationId 为 `com.thunderstone.idun.dev`；农历库 `cn.6tail:lunar:1.7.7`；定制版本号 |
| `guard/RevivalSync.kt`、`res/xml/revival_account.xml`、`res/xml/revival_sync.xml` | 同步账号类型与定制包名一致 |
| `ui/home/LunarDate.kt` | 离线农历、当日节气与传统节日，支持闰月与大小月除夕 |
| `ui/home/HomeChrome.kt` | 中文日期追加农历；桌面大时钟与控制中心共用；沿用上游唤醒及校时刷新 |
| 五种语言的 `strings.xml` | 农历日期资源，占位符一致；非中文界面仍保持上游日期显示 |
| `LunarDateTest.kt` | 农历、节日、闰月、除夕及全年 24 节气回归 |

上表 Kotlin 路径相对 `app/src/main/java/app/orchard/tv/`，资源相对 `app/src/main/`，测试位于 `app/src/test/java/app/orchard/tv/ui/home/`。

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
3. 在升级分支执行 `git merge <正式发布标签>`，解决冲突。重点检查 Gradle 中包名、农历依赖与版本号，HomeChrome 中日期拼接和时钟刷新，账号类型 XML；不要整文件覆盖上游的新实现。
4. 保留 `-mitv` 版本名后缀，versionCode 必须大于电视当前值（当前 49）。核对上游是否改变偏好、排序或壁纸数据格式。
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
