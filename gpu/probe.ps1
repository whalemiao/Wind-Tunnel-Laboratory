# GPU 求解器数值探针（无头 Edge + swiftshader）
#
# 用法：
#   pwsh -File gpu/probe.ps1 -Query "sim=256&preset=car2" -Warm 240
#   pwsh -File gpu/probe.ps1 -Query "sim=256&preset=car2" -Warm 240 -Shot shots/gpu-car.png
#
# 输出：#hud 里的 dataset-probe 一行（dye@x 剖面 / u@x 剖面 / pMid / divMid）。
# 前提：仓库根目录有一个静态服务器（默认 8902），例：
#   python -m http.server 8902 --bind 127.0.0.1 --directory .
param(
  [string]$Query = "sim=256",
  [int]$Warm = 240,
  [int]$Port = 8901,
  [int]$Budget = 4000,
  [string]$Shot = "",
  [string]$Window = "1280,720",
  [string]$Edge = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

$url = "http://127.0.0.1:$Port/gpu/gpu-fluid.html?$Query&probe=1&warm=$Warm"
$tmp = Join-Path ([IO.Path]::GetTempPath()) ("gpuprobe-" + [guid]::NewGuid().ToString("N").Substring(0, 8))
New-Item -ItemType Directory -Path $tmp -Force | Out-Null
$dom = Join-Path $tmp "dom.html"
$err = Join-Path $tmp "err.txt"

$udArg = "--user-data-dir=" + (Join-Path $tmp "ud")
$edgeArgs = @(
  "--headless=new", "--no-sandbox", "--disable-crash-reporter", "--disable-breakpad",
  "--enable-unsafe-swiftshader", "--window-size=$Window",
  $udArg,
  "--virtual-time-budget=$Budget"
)
if ($Shot) {
  $shotPath = if ([IO.Path]::IsPathRooted($Shot)) { $Shot } else { Join-Path $root $Shot }
  $edgeArgs += "--screenshot=$shotPath"
}
$edgeArgs += @("--dump-dom", $url)

# 用 cmd 拼命令行：PowerShell 5.1 直接 splat 会把带 & 的 URL 拆成多个参数
$quoted = $edgeArgs | ForEach-Object { if ($_ -match '[\s&]') { '"' + $_ + '"' } else { $_ } }
$cmdline = '"' + $Edge + '" ' + ($quoted -join ' ') + ' > "' + $dom + '" 2> "' + $err + '"'
cmd /c $cmdline | Out-Null

Write-Output "URL  $url"
if ($Shot) { Write-Output "SHOT $shotPath" }

$text = ""
if (Test-Path $dom) { $text = [IO.File]::ReadAllText($dom, [Text.Encoding]::UTF8) }

if ($text -match 'data-mask="([^"]*)"') { Write-Output "MASK  $($Matches[1])" }
if ($text -match 'data-probe="([^"]*)"') {
  Write-Output "PROBE $($Matches[1])"
} else {
  Write-Output "HUD   (missing) domBytes=$((Get-Item $dom -ErrorAction SilentlyContinue).Length)"
  Get-Content $err -ErrorAction SilentlyContinue | Select-Object -Last 4 | ForEach-Object { Write-Output "ERRLOG $($_.Trim())" }
}
if ($text -match '(?s)<pre id="dbg"[^>]*>(.*?)</pre>') {
  Write-Output $Matches[1].TrimEnd()
}
if ($text -match '着色器编译失败|缺少 EXT_color_buffer_float|FBO 不完整|不支持 WebGL2|掩码加载失败') {
  Write-Output "ERROR $($Matches[0])"
}

# 控制台里的 [probe] 行（需要 --enable-logging，故一般不取）
Select-String -Path $err -Pattern "CONSOLE|Uncaught|probe" | Select-Object -First 5 | ForEach-Object { Write-Output "LOG   $($_.Line.Trim())" }
