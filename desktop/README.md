# 桌面版（Windows EXE）

用 WebView2 做的壳，和安卓 APK 同一套网页。

## 直接跑

`WindTunnelLab.exe` 双击即可（约 63 MB，自带 .NET 运行时，不用先装任何东西）。
系统需要 **WebView2 运行时** —— Win10/11 装过 Edge 就有。

## 自己构建

```bash
# 需要 .NET SDK 8
cd desktop
dotnet publish -c Release -r win-x64 --self-contained true -o ..\dist-exe
```

## 实现要点

- 网页以**嵌入资源**打进 exe，首次运行解压到 `%LOCALAPPDATA%\WindTunnelLab\web\`
  → 单文件分发，不用在旁边放一堆文件
- **必须显式指定 `UserDataFolder`**：默认会写 exe 同目录，放进 `Program Files` 就没权限、直接崩
- 全局异常弹 MessageBox，不静默退出
- F11 全屏；F12 开发者工具（在 WebView2 里默认关了右键菜单之外的调试入口）

## 为什么不用 Electron

| 方案 | 体积 | 说明 |
| --- | --- | --- |
| **WebView2 + .NET** | **63 MB** | 用系统自带运行时 |
| Electron | ~120 MB | 自带整个 Chromium |
| Tauri | ~3 MB | 但要 Rust 工具链，构建麻烦 |