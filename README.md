# 风洞实验室 · Wind Tunnel Laboratory

把一张**透明底 PNG** 放进风洞：不透明的像素当成实体，风从左边吹来，
绕过它、在尾部留下尾流和涡街。可以拖动摆放、直接画障碍物、读气动力系数。

**在线玩**：<https://whalemiao.github.io/Wind-Tunnel-Laboratory/>

| 平台 | 下载 | 说明 |
| --- | --- | --- |
| 🌐 网页 | [直接打开](https://whalemiao.github.io/Wind-Tunnel-Laboratory/) | 手机/电脑都能用 |
| 📱 安卓 | [wind-tunnel-lab.apk](https://whalemiao.github.io/Wind-Tunnel-Laboratory/wind-tunnel-lab.apk) | 约 275 KB，debug 签名，允许「未知来源」即可装 |
| 💻 Windows | [WindTunnelLab.exe](https://whalemiao.github.io/Wind-Tunnel-Laboratory/WindTunnelLab.exe) | 约 63 MB 单文件，自带运行时，双击即用 |

三个都在同一个 [Release](https://github.com/whalemiao/Wind-Tunnel-Laboratory/releases) 里。

![流线](shots/v8-01-流线-高速2.5-平滑.png)

## 这是什么

一个**纯前端的 2D 不可压流体模拟**，专门用来做"风洞可视化"：

- 上传的图片 → 用 alpha 通道当**固体边界**
- 求解 **Navier–Stokes**（Stable Fluids，Stam 1999）
- 六种可视化：流线 / 脉线 / 条纹烟 / 涡量 / 速度 / 压强
- 可以调风速、黏性、涡量强度、烟迹长度、亮度、网格分辨率

**没有依赖、没有构建步骤**：一个 `index.html` 打开就能跑。

## 用法

```
直接打开 index.html
或用任意静态服务器（例：python -m http.server）
```

- 拖一张**透明底 PNG** 进去，或点「选择图片」
- **画布上拖动**移动物体，**滚轮**缩放，**Shift+滚轮**旋转
- 「画笔 / 橡皮」直接在画布上画障碍物（笔刷半径可调）
- 「内置形状」每点一次循环切换：圆柱 → 翼型 → 水滴

### URL 参数

```
?mode=stream|streak|stripes|curl|speed|pressure
?shape=circle|airfoil|teardrop
?speed=1.2&visc=0.15&vort=10&life=1&bright=0.7
?ox=0.3&oy=0.5&scale=1&angle=0
?res=240x135|320x180|400x225|480x270|640x360|800x450
?img=test-tank.png        # 直接加载一张图当物体
?still=1200               # 跑固定帧数后出静帧（截图用，不启动动画）
?dbg=1                    # 底部显示中间量（排查"场是不是死了"）
```

例：

```
?img=test-tank.png&mode=stripes&speed=1.4&res=400x225
```

## 物理

- 不可压 Navier–Stokes，**Stable Fluids**（Stam 1999）
- 每帧：涡量约束 → 半拉格朗日对流 → 20 次 Jacobi 压力投影 → 边界条件
- 固体单元速度归零 + 贴壁摩擦
- 涡量 ω = ∂v/∂x − ∂u/∂y
- 流线用**平滑后的速度场 + RK2 中点法**积分（步长 0.7 格，每步限转角 4°）

### 气动力（估算）

用**尾流动量亏损法**（风洞实验里的真实做法），不是表面压力积分：

```
Cd ≈ ∫ u(U − u) dy / (0.5·U²·H)
Cl ≈ ∫ u·v      dy / (0.5·U²·H)
```

为什么不用压力积分：Stam 求解器里的 `p` 是"伪压力"，只保证梯度正确、
绝对值没有物理单位 —— 直接积分会算出 `Cd=1245` 这种没意义的数。

**量级可信，绝对值别当风洞数据。**

## 渲染：网格 ≠ 画布

```
物理网格：240~800（可调）
画布像素：网格 × RS，RS 按窗口宽 × devicePixelRatio 算（1~6）
```

标量场先画在网格分辨率，再**开插值放大**；流线/粒子直接在高分画布上画。
导入的图片**每次都用原图按画布像素绘制**，不在中间缩一次。

## 踩过的坑（都写在代码注释里）

| 症状 | 根因 |
| --- | --- |
| 流线像闪电一样抽搐 | 最近邻采样 + 涡量约束放大噪声 + 每帧方向微变 → 双线性 + 5×5 空间平滑 + **时间低通** |
| 换分辨率后图片变回默认形状 | 用 `location.reload` 换分辨率，图片只在内存里 → 改成**原地重建** |
| 拖动时物体留残影 | 累积层和物体画在同一层 → **分层**：累积层只放烟，主画布每帧重画 |
| 拖到的地方轨迹失踪 | 拖动时整片清烟 → 改成**加速淡出**（0.06 → 0.22） |
| 条纹烟整片纯蓝 / 流线一条没有 | 「不重置流场」把**首次初始化**也跳过了 → 加 `flowInited` 标志 |
| 平滑后流线全没了 | 平滑范围写成 `R..NX-R`，**边界一圈是 0** → 覆盖 `1..NX-2` |
| 18px 的 B 站图标变成灰疙瘩 | CSS `fill: currentColor` 继承到描边图标内部 → `fill` 要写在具体元素上 |

**一条通用规律**：

> **会累积的东西放一层，每帧都要重画的东西放另一层。**
> 混在一层里，就一定会互相污染。

## 文件

```
index.html          主程序（Stable Fluids 版）
v1-lbm.html         第一版：格子玻尔兹曼（保留对比，会有"Q弹"感）
test-tank.png       示例素材（1191×515，透明底）
shots/              截图
```

## 参考

界面上参考了 [WindTunnel（Algorizk）](https://play.google.com/store/apps/details?id=com.algorizk.windtunnel)
的功能划分（流线/脉线/条纹烟/涡量/速度/压强、可调黏性与网格）。

求解器实现参考 Jos Stam, *Stable Fluids*, SIGGRAPH 1999。

## License

MIT

## 安卓 APK

`android/` 是一个最小的 WebView 壳（Java，无 Kotlin），构建前会把仓库根的
`index.html` / `test-tank.png` 拷进 `assets/` —— **网页只维护一份**。

本地构建（需要 Android SDK + JDK 17）：

```bash
cd android && gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

CI 构建：推送到 `main` 时 `.github/workflows/android.yml` 自动编译，
产物发到 Release `apk-latest` 和 Actions artifacts。

**为什么是 debug 签名**：发布签名需要 keystore + 密码，不适合放进公开仓库。
debug 签名能正常安装，只是不能上架应用商店。

## 手机适配

- 侧边栏在窄屏（≤820px）变成抽屉，左上角 ☰ 打开，点遮罩关闭
- 画布 `touch-action: none` + 阻止默认滚动 → **手机能拖动 / 滚轮缩放 / 旋转物体**
- 状态栏在手机上只保留 GitHub / B 站图标