# GPU 求解器（WebGL2）

把求解器从 CPU 搬到 GPU。参考
[PavelDoGreat/WebGL-Fluid-Simulation](https://github.com/PavelDoGreat/WebGL-Fluid-Simulation)（MIT）
与 [GPU Gems Ch.38](https://developer.nvidia.com/gpugems/gpugems/part-vi-beyond-triangles/chapter-38-fast-fluid-dynamics-simulation-gpu)。

## 现在能跑的部分

- WebGL2 + 浮点渲染目标（RGBA16F / RG16F / R16F）
- 8 个物理着色器：速度对流 / 散度 / Jacobi 压力(24 次) / 减梯度 / 涡量约束 / 染料对流 / 入口注入 / 初始灌流
- **四层显示**：烟（染料）/ 速度（彩虹色标）/ 压力（发散色标，自动对比度）/ 粒子（GPU 状态纹理）
- 障碍物掩码从 PNG 上传（`?preset=` / `?img=`）
- **竖屏**：`?orient=portrait`，或按窗口比例自动判断（手机即竖屏）；物体补 90° 旋转
- 已接到主页：`index.html` 探测 WebGL2 + `EXT_color_buffer_float`，有就走 GPU，没有退回 `cpu.html`

## 交接单第一件「条纹不流动」：结论是**没有这个 bug**

上一轮写的是「染料只出现在入口左侧 2%，但 `?wide=1&probe=1` 读回 `dyeMid=0.972`，两者矛盾」。
现在用确定性探针跑完，结论是**两个读数都不可靠，求解器本身是好的**：

| 证据 | 数字 |
| --- | --- |
| 染料前沿位置跟随 `u·t` | t=1.08s 前沿≈0.55、t=2.0s ≈0.87；实测速度 (0.87−0.55)/(2.0−1.08)=**0.348**，正好等于 u=0.35 |
| 速度场均匀 | `u@x=[0.35 0.35 0.34 0.34 … 0.34 0.35]`，全场平 |
| 散度压干净 | `divMid≈0.0000`（投影正常） |
| 染料整场剖面 | `dye[1.00 1.00 0.99 … 0.93 0.87 0.45 0.04]`（`?wide=1&warm=120`） |

两个「证据」为什么骗人：

1. **探针只采了 y=0.5 这一行**，而条纹注入是 `step(0.5, fract(vUv.y*14.0))`，
   `fract(0.5*14)=0` —— y=0.5 恰好落在两条亮带之间的暗带上，那一行**永远是 0.00**。
   `?wide=1` 绕过条纹函数把左侧 20% 全填 1.0，所以它读得到 0.97。两回事。
2. **无头浏览器不驱动 requestAnimationFrame**。`--virtual-time-budget=5000 --screenshot`
   拍到的其实是 t≈0.03 的那一帧（HUD 实测 `t=0.07`，只跑了 4 帧），
   染料当然还在入口那 2% 里。这不是模拟不流动，是截图太早。

修法不是改着色器，是**把验证手段修好**：加 `?warm=N`，渲染前同步跑 N 个定步长帧。
`?warm=360` 出来的就是 t=6.00 的稳态场（见 `shots/gpu-stripes.png`）。

## 怎么验证：数字优先

```powershell
# 在仓库根起一个静态服务器（随便哪个都行，这里用 8901）
python -m http.server 8901 --bind 127.0.0.1 --directory .

# 读完 N 步后直接读回纹理，打出一行数字
powershell -File gpu/probe.ps1 -Query "sim=256&preset=car2" -Warm 360
```

浏览器直接看：

```
http://127.0.0.1:8901/gpu/gpu-fluid.html?sim=256&preset=car2&warm=360
http://127.0.0.1:8901/                       # 入口页：自动选 GPU 版
```

输出示例（`sim=256&preset=car2&warm=360`）：

```
PROBE t=6.00 S=256 py=0.5 | dye[0.00 …] | u[0.35 0.00 0.00 … 0.30 0.32] | p[0.05 0.00 … -0.38 -0.45]
```

- 探针读的是**整行**（16 个采样点），不是一列：`probeText()` 用一次 `readPixels` 读一行
- `?py=` 换采样行（默认 0.5）；有障碍物时别只看 0.5，车身正好挡在那
- `?grid=dye|u|p` 把整场降采样成 12×40 的 ASCII 灰度图，几行就能看出场死没死

## URL 参数

```
?sim=256        网格分辨率（默认 512，正方形）
?preset=car2    障碍物预设（../presets/*.png）
?img=xxx.png    自定义掩码
?view=dye|vel|pressure|particles   显示层
?orient=portrait|landscape         竖屏/横屏（不填按窗口比例）
?warm=NN        渲染前同步跑 NN 步定步长（确定性验证 / 出截图，**截图必须用它**）
?still=1        只出静帧，不启动循环
?probe=1        HUD 打印数值剖面
?py=0.55        探针采样行
?grid=dye|u|p   ASCII 全场景
?drive=timer    用 setTimeout 驱动主循环（无头环境验证「循环本身」用）
?keepbuffer=1   保留绘制缓冲（截图更稳）
?pn=96          粒子数 = pn²（默认 96）
?streak=0.09    粒子短线长度（秒 × 当地速度）
?plife=3.5      粒子寿命（秒）
?pscale=1       压力色标的额外增益
?wide=1         入口注入退化成左侧 20% 全白（诊断用）
?iters=24       压力迭代次数
```

## 实现要点（这一轮新写的）

### 三层显示

- 烟 / 速度 / 压力都只是 `FS_DISPLAY` 里加分支，纹理都是现成的
- 速度、压力用**自动对比度**：每 0.25s 在 11×11 的粗网格上 `readPixels` 取 `max|p|`、`max|v|`
  （121 次 1×1 读，开销可忽略），再喂给色标。跟 CPU 版 `fieldRange()` 的行为一致
- 压力是「伪压力」，进出口的 Dirichlet 会在全场留一条线性斜坡；显示时按全场对称最大值归一

### 粒子层（GPU）

- 状态纹理 `PW×PW`（RGBA16F）：`xy`=位置、`z`=年龄、`w`=个体随机
- 推进 pass：`pos += vel(pos)*dt`，出界 / 进固体 / 超龄就全场随机重投
- **寿命带个体随机** `lifeMax = uLife*(0.35+0.65*s.w)`：不加这一项，同一批粒子会同时死、
  同时重生，出现一条横着走的「粒子浪」
- 渲染：一次 `drawArrays(LINES, PCOUNT*2)`，每个粒子两个顶点；尾巴位置在顶点着色器里
  用 `pos - v*streak` 现算，不用存历史位置。叠加混合 → 像发光的脉线

## 性能

软件光栅（swiftshader）下 256² 带粒子约 62fps（`?drive=timer` 实测）；
真实 GPU 会高一个量级。这正是把求解器搬到 GPU 的原因：CPU 版 320×180 已经 20-40fps。

## 已知坑（别再踩）

| 坑 | 教训 |
| --- | --- |
| 用 `idx = 1 - idx` 翻转计数做 ping-pong | 改成显式 `read`/`write` 变量；翻转次数错一次就绑到空纹理，且不报错 |
| 改 shader 的 `uniform` 声明用字符串替换 | 容易插错 shader（把 `uInflow` 插进 pressure 而不是 gradient，整页白屏） |
| 入口速度 | 必须启动时把整场灌成均匀来流；半拉格朗日自对流不会把速度"扩散"出去 |
| 画布尺寸变化后 | 要重新验证染料对流：`dt` 与 UV 单位的比例变了 |
| **无头浏览器不跑 rAF** | `--virtual-time-budget` 只推进定时器；截图前必须 `?warm=N`，否则拍到的是第 4 帧 |
| **探针只采一行** | y=0.5 正好落在条纹暗带上，会读到"整场全 0"；改读整行 + `?grid=1` |
| 出口改零梯度 | 试过：`u` 从 0.35 沿程掉到 0.20，全场速度塌掉；保持两端 Dirichlet 吹入 |
| 粒子寿命全一样 | 会同步重生形成"粒子浪"；寿命乘以个体随机数 |

## 下一步（新交接单）

> 下次开场只看这一节。每一条都能用 `probe.ps1` 出数字。

### 第一件：GPU 版补齐 CPU 版剩下的两种显示

CPU 版有流线 / 脉线 / 条纹烟 / 涡量 / 速度 / 压强；GPU 版现在只有烟 / 速度 / 压力 / 粒子。

- 涡量：`(∂v/∂x − ∂u/∂y)` 在显示 pass 里现算即可，不用额外纹理
- 流线：CPU 版的平滑 + RK2 积分没法照搬；先做「粒子长曝光」近似（`?streak=` 加大到 0.3~0.5）

验证：`?view=curl&grid=u&warm=240` 的 ASCII 图里，圆柱/车尾应该出现交替符号。

### 第二件：APK 实机验证

GPU 版在 `minSdk 24` 的真机上还没跑过（只在 swiftshader 上验过数值）。
要确认的是 `EXT_color_buffer_float` 在低端机上是否存在——不存在会自动退回 `cpu.html`，
但**这条回退路径也只是逻辑验证过，没在真机上验过**。

验证：装 APK，看首屏是 GPU 版（HUD 有 `GPU 256×256`）还是 CPU 版。

### 第三件：导出当前帧

`?keepbuffer=1` 已经能保住绘制缓冲，只差一个 `canvas.toBlob()` + 下载/分享。
APK 那边 WebView 不支持系统分享，先做「保存到相册/下载」。

### 第四件：自适应分辨率

掉帧时自动降 `S`（现在是启动参数，改了要重建纹理，注意 `makeTarget` 之后所有 ping-pong 变量都要重新绑定）。
