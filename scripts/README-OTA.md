# KitchenKDS 在线更新（OTA）发版说明

App 内的「设置 -> 软件版本与更新」读取：

    https://api.github.com/repos/feanlyver393-glitch/KitchenKDS/releases/latest

它取 `tag_name` 作为远程版本、取 `assets` 里第一个 `.apk` 作为下载地址。
所以一次合法的发版必须满足三条：

1. tag 形如 `v5.1.1`（`AppUpdater.normalizeVersion` 会自动去掉 v/V 前缀）；
2. tag 版本**严格大于**手机上已安装的版本，否则 App 会判定「当前已是最新版本」；
3. Release 必须带一个 `.apk` 附件。

## 一键发版

```powershell
# 1) GitHub -> Settings -> Developer settings -> Personal access tokens (fine-grained)
#    权限：仅选本仓库，Contents = Read and write
# 2) 在项目根目录执行（会自动改版本号、构建、建 Release、传 APK、自检）

.\scripts\publish-release.ps1 -Token ghp_xxxxxxxx

# 指定版本与更新说明
.\scripts\publish-release.ps1 -Token ghp_xxxxxxxx -Version 5.2.0 -Notes "修复加单数量; 新增在线更新"

# 只演练不发布
.\scripts\publish-release.ps1 -Token dummy -DryRun -SkipBuild
```

脚本行为：

1. 读 `app/build.gradle.kts` 的 versionCode/versionName，versionCode +1，versionName 默认 patch +1；
2. `gradlew assembleDebug`（已内置 JAVA_HOME / ANDROID_SDK 参数，默认指向本机 tools 目录）；
3. `POST /repos/{owner}/{repo}/releases` 创建 Release（tag = v<version>）；
4. `POST uploads.github.com/.../assets?name=KitchenKDS-v<version>.apk` 上传安装包；
5. `GET /releases/latest` 回读校验，打印 tag 与下载地址。

## 端到端验证

1. 手机装上一版 APK（例如 5.1.0）；
2. 发版 5.1.1；
3. App 冷启动 → 主界面静默检查 → 弹「发现新版本 v5.1.1」；
4. 点「立即更新」→ 进度条 → 系统安装器（首次需允许「安装未知应用」）；
5. 覆盖安装后版本号变为 5.1.1。

## 签名一致性（重要）

OTA 覆盖安装要求新旧 APK 使用**同一签名**。

- 当前构建沿用本机持久的 debug 签名（跨构建固定，SHA-256 `46853b9d...973f`），所以本机打出来的每一版都能互相覆盖；
- 若要对外正式发布，请在本机用自己的 `.jks` 出包，并保证此后每版都用同一个 keystore —— 换了签名就只能卸载重装。
