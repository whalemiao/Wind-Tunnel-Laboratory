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

## 待办（交接单）

> 下次开场只看这一节就够了。按顺序做，每一步都能用截图验证。

### ⚠️ 第一件：定位「条纹不流动」

**现象**：`?preset=car2` 时染料只出现在入口左侧 2%，右侧全黑；
但 `?wide=1&probe=1` 读回 `dyeMid=0.972`（染料在中段）。**两者矛盾。**

**判断**：`advectDye` pass 本身是好的，问题在 **inlet pass 的读写绑定**
（很可能是「读自己正在写的纹理」，WebGL 下返回未定义值 → 全 0）。

**一步验证**：把 `FS_INLET` 临时改成纯拷贝

```glsl
void main(){ fragColor = vec4(texture(uDye, vUv).xyz, 1.0); }
```

- 画面出现「条纹在动」→ **inlet 是元凶**：改成「先整体拷贝，再只覆盖最左一列」，
  或者用两个独立纹理，绝不能让 `uDye` 绑到正在写的那个 attachment
- 画面仍然静止 → 回到 `advectDye`，检查 `uVel` 绑的是不是当前速度

**验证 URL**

```
http://127.0.0.1:8901/gpu-fluid.html?sim=256&preset=car2
```

### 第二件：三层显示接到 GPU

CPU 版有 `layers = { physics, smoke, particles }`，GPU 版目前只有「烟 / 速度」两档：

- 烟 → 已有 `uDye`
- 速度 → 已有 `uVel`
- 压力 → `uPressure` 纹理现成，显示 pass 加个分支即可
- 粒子 → 需要额外的粒子 pass，或先在 JS 里按 GPU 速度场推进

### 第三件：竖屏

CPU 版用「画布整体旋转 90° + 指针反变换」，GPU 版照抄：
改 `gl.viewport` 和顶点着色器的坐标映射即可，物理不动。

### 第四件：替换主页 + 保留回退

```js
const gl = cv.getContext('webgl2');
if (gl && gl.getExtension('EXT_color_buffer_float')) {
  // GPU 版
} else {
  // 回退到现有 index.html（CPU 版）
}
```

改完 `index.html` 会自动触发 APK / EXE 的构建（工作流已配好）。

### 已知坑（别再踩）

| 坑 | 教训 |
|---|---|
| 用 `idx = 1 - idx` 翻转计数做 ping-pong | 改成显式 `read`/`write` 变量；翻转次数错一次就绑到空纹理，且不报错 |
| 改 shader 的 `uniform` 声明用字符串替换 | 容易插错 shader（把 `uInflow` 插进 pressure 而不是 gradient，整页白屏） |
| 入口速度 | 必须启动时把整场灌成均匀来流；半拉格朗日自对流不会把速度"扩散"出去 |
| 画布尺寸变化后 | 要重新验证染料对流：`dt` 与 UV 单位的比例变了 |