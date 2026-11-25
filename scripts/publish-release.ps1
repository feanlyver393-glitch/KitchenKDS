<#
.SYNOPSIS
    KitchenKDS 一键发版: 改版本号 -> 构建 APK -> 创建 GitHub Release -> 上传 APK 资产。

.DESCRIPTION
    App 内"设置 -> 软件版本与更新"读的是: 
        https://api.github.com/repos/<Owner>/<Repo>/releases/latest
    它取 tag_name 当远程版本、取第一个 .apk 资产当下載地址。所以发版必须满足: 
      1) tag 形如 v5.2.0(AppUpdater.normalizeVersion 会自动去掉 v 前缀)；
      2) tag 版本必须严格大于手机上已安装的版本, 否则 App 判定"已是最新"；
      3) 必须上传 .apk 资产。

.EXAMPLE
    .\scripts\publish-release.ps1 -Token ghp_xxx

.EXAMPLE
    .\scripts\publish-release.ps1 -Token ghp_xxx -Version 5.2.0 -Notes "修复加单数量 / 新增在线更新"
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Token,
    [string]$Version = "",
    [string]$Notes = "",
    [string]$Owner = "feanlyver393-glitch",
    [string]$Repo = "KitchenKDS",
    [string]$JavaHome = "E:\2\as1\tools\jdk\jdk-17.0.21+9",
    [string]$AndroidSdk = "E:\2\as1\tools\sdk",
    [string]$ApkPath = "",
    [switch]$SkipBuild,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root       = Split-Path -Parent $PSScriptRoot
$gradleFile = Join-Path $root "app\build.gradle.kts"
$apiBase    = "https://api.github.com/repos/$Owner/$Repo"
$headers    = @{
    Authorization = "Bearer $Token"
    Accept        = "application/vnd.github+json"
    "User-Agent"  = "KitchenKDS-Publisher"
}

function Get-GradleValue([string]$text, [string]$key) {
    $linesArr = $text.Split([char]10)
    foreach ($l in $linesArr) {
        $t = $l.Trim()
        if ($t.StartsWith($key)) {
            $eq = $t.IndexOf([char]61)
            if ($eq -ge 0) {
                $v = $t.Substring($eq + 1).Trim()
                $v = $v.Trim([char]34).Trim([char]39)
                if ($v.Length -gt 0) { return $v }
            }
        }
    }
    throw ("无法从 app/build.gradle.kts 读取 " + $key)
}

function Set-GradleVersion([string]$path, [string]$versionName, [int]$versionCode) {
    $linesArr = [IO.File]::ReadAllLines($path, (New-Object Text.UTF8Encoding $false))
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($l in $linesArr) {
        $t = $l.Trim()
        if ($t.StartsWith("versionCode")) {
            $out.Add("        versionCode = " + $versionCode)
        } elseif ($t.StartsWith("versionName")) {
            $out.Add("        versionName = " + [char]34 + $versionName + [char]34)
        } else {
            $out.Add($l)
        }
    }
    [IO.File]::WriteAllLines($path, $out, (New-Object Text.UTF8Encoding $false))
}

function Bump-Patch([string]$v) {
    $parts = $v.Split(".")
    if ($parts.Length -lt 3) { return $v + ".1" }
    $patch = [int]$parts[2] + 1
    return $parts[0] + "." + $parts[1] + "." + $patch
}

# -- 1. 计算版本 ------------------------------------------
$gradleText  = [IO.File]::ReadAllText($gradleFile, (New-Object Text.UTF8Encoding $false))
$currentName = Get-GradleValue $gradleText "versionName"
$currentCode = [int](Get-GradleValue $gradleText "versionCode")

if ([string]::IsNullOrWhiteSpace($Version)) {
    $Version = Bump-Patch $currentName
} else {
    $Version = $Version.Trim().TrimStart("v", "V")
}
$newCode = $currentCode + 1
Write-Host ("[1/4] 版本: $currentName ($currentCode)  ->  $Version ($newCode)") -ForegroundColor Cyan

if ((-not $SkipBuild) -and (-not $DryRun)) {
    Set-GradleVersion $gradleFile $Version $newCode
}

# -- 2. 构建 APK -----------------------------------------
if (-not $SkipBuild) {
    Write-Host "[2/4] 构建 APK..." -ForegroundColor Cyan
    $env:JAVA_HOME = $JavaHome
    $env:ANDROID_HOME = $AndroidSdk
    $env:ANDROID_SDK_ROOT = $AndroidSdk
    $env:PATH = "$JavaHome\bin;" + $env:PATH

    Push-Location $root
    try {
        & (Join-Path $root "gradlew.bat") assembleDebug --no-daemon
        if ($LASTEXITCODE -ne 0) { throw ("构建失败, 退出码 " + $LASTEXITCODE) }
    } finally {
        Pop-Location
    }
    $ApkPath = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
} elseif ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
}

if (-not (Test-Path $ApkPath)) { throw ("APK 不存在: " + $ApkPath) }
$apkItem = Get-Item $ApkPath
Write-Host ("      APK: " + $apkItem.FullName + "  " + [math]::Round($apkItem.Length / 1MB, 2) + " MB") -ForegroundColor DarkGray

$tag       = "v$Version"
$assetName = "KitchenKDS-$tag.apk"

if ($DryRun) {
    Write-Host ("[DryRun] 将创建 release tag=" + $tag + ", 并上传 " + $assetName) -ForegroundColor Yellow
    return
}

# -- 3. 创建 Release -------------------------------------
Write-Host ("[3/4] 创建 Release " + $tag + " ...") -ForegroundColor Cyan

$exists = $false
try {
    $existing = Invoke-RestMethod -Uri ($apiBase + "/releases/tags/" + $tag) -Headers $headers -TimeoutSec 30
    if ($existing) { $exists = $true }
} catch {
    $exists = $false
}
if ($exists) { throw ("Release " + $tag + " 已存在, 请换一个更高的版本号。") }

if ([string]::IsNullOrWhiteSpace($Notes)) {
    $Notes = "KitchenKDS " + $tag + [Environment]::NewLine + [Environment]::NewLine + "- 由 publish-release.ps1 自动发布"
}

$body = @{
    tag_name         = $tag
    target_commitish = "main"
    name             = "KitchenKDS $tag"
    body             = $Notes
    draft            = $false
    prerelease       = $false
} | ConvertTo-Json -Depth 5

$release = Invoke-RestMethod -Method Post -Uri ($apiBase + "/releases") -Headers $headers -ContentType "application/json; charset=utf-8" -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 60
Write-Host ("      release id=" + $release.id + "  " + $release.html_url) -ForegroundColor DarkGray

# -- 4. 上传 APK 资产 ------------------------------------
Write-Host ("[4/4] 上传 " + $assetName + " ...") -ForegroundColor Cyan
$uploadUrl = "https://uploads.github.com/repos/$Owner/$Repo/releases/" + $release.id + "/assets?name=" + [uri]::EscapeDataString($assetName)
$resp = Invoke-WebRequest -Method Post -Uri $uploadUrl -Headers $headers -ContentType "application/vnd.android.package-archive" -InFile $apkItem.FullName -TimeoutSec 600
$asset = $resp.Content | ConvertFrom-Json
Write-Host ("      asset: " + $asset.name + "  " + [math]::Round($asset.size / 1MB, 2) + " MB") -ForegroundColor DarkGray

# -- 5. 自检: 确认 App 侧能拿到的东西 ---------------------
Write-Host "" 
Write-Host "验证 /releases/latest: " -ForegroundColor Cyan
$latest = Invoke-RestMethod -Uri ($apiBase + "/releases/latest") -Headers $headers -TimeoutSec 30
$firstApk = @($latest.assets | Where-Object { $_.name -like "*.apk" })[0]
Write-Host ("  tag_name  = " + $latest.tag_name)
Write-Host ("  apk 资产   = " + $firstApk.name)
Write-Host ("  下载地址   = " + $firstApk.browser_download_url)
Write-Host ""
Write-Host "完成。App 内: 设置 -> 软件版本与更新 -> 检查更新。" -ForegroundColor Green