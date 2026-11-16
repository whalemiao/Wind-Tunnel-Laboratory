# GPU 求解器（WebGL2）· 已跑通

把求解器从 CPU 搬到 GPU。参考
[PavelDoGreat/WebGL-Fluid-Simulation](https://github.com/PavelDoGreat/WebGL-Fluid-Simulation)（MIT）
与 [GPU Gems Ch.38](https://developer.nvidia.com/gpugems/gpugems/part-vi-beyond-triangles/chapter-38-fast-fluid-dynamics-simulation-gpu)。

## 能跑的部分

- WebGL2 + 浮点渲染目标
- 8 个着色器：速度对流 / 散度 / Jacobi 压力(24 次) / 减梯度 / 涡量约束 / 染料对流 / 入口注入 / 显示
- 启动灌入均匀来流，进出口 Dirichlet 边界
- 障碍物掩码从 PNG 上传（`?preset=` / `?img=`）
- 条纹烟绕过障碍物、尾部有尾流

## 关键修复（曾卡了很久）

主循环原来用 `xIdx = 1 - xIdx` 翻转计数来 ping-pong，
染料 pass 里 `uVel` 绑到了空纹理 → 染料纹丝不动。
改成显式的读写变量后一次通过：

```js
let vA = vel[0], vB = vel[1];
blit(vB, prog, u => bindTex(0, vA.tex, u.uVel));   // 明确读 A 写 B
[vA, vB] = [vB, vA];
```

## 调试入口

```
?sim=256      网格分辨率（默认 512）
?preset=car2  障碍物预设
?img=xxx.png  自定义掩码
?probe=1      readPixels 读回数值打 HUD
?wide=1       宽注入，肉眼判断对流
?view=vel     速度场视图
?iters=24     压力迭代次数
```

## 性能

软件光栅（swiftshader）下 256² 约 31fps；真实 GPU 会高一个量级。
这正是把求解器搬到 GPU 的原因：CPU 版 320×180 就已经 20-40fps 了。

## 下一步

把 CPU 版的 UI（导入/画笔/预设/三层显示/竖屏）接到这个 GPU 内核上，
保留 WebGL2 不可用时的 CPU 回退。