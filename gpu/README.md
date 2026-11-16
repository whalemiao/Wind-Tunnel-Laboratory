# GPU 求解器（WebGL2）· 进行中

`gpu-fluid.html` 是把求解器从 CPU 搬到 GPU 的试验版，参考
[PavelDoGreat/WebGL-Fluid-Simulation](https://github.com/PavelDoGreat/WebGL-Fluid-Simulation)（MIT）
与 [GPU Gems Ch.38](https://developer.nvidia.com/gpugems/gpugems/part-vi-beyond-triangles/chapter-38-fast-fluid-dynamics-simulation-gpu) 的架构。

## 已完成

- WebGL2 + 浮点渲染目标（`EXT_color_buffer_float`）
- 8 个着色器：速度对流 / 散度 / Jacobi 压力 / 减梯度 / 涡量约束 / 染料对流 / 入口注入 / 显示
- 启动时把整场灌成均匀来流；进出口 Dirichlet
- 障碍物掩码从 PNG 上传（`?preset=` 或 `?img=`）
- 数值探针：`?probe=1` 用 `readPixels` 读回真实数值打到 HUD
- 视图切换：`?view=vel` 看速度场

## 已验证

```
u = 0.356  v = 0.000     ← 速度场正确（均匀来流全场都在吹）
dye = 0.000              ← 染料不随流移动（待修）
```

## 已知问题

染料不随流场移动：入口注入的色带停在原处。怀疑是染料 pass 的
ping-pong 索引或 `uVel` 绑定错位（`?wide=1` 可以稳定复现：
左侧 20% 的亮块边界完全不动）。

## 调试入口

```
?sim=256&probe=1      # 数值探针
?wide=1               # 宽注入，肉眼判断对流是否生效
?view=vel             # 速度场
?iters=24&speed=1     # 压力迭代 / 风速
```