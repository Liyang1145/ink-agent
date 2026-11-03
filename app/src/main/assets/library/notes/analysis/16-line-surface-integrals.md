---
title: 曲线积分与曲面积分
subject: 数学分析
chapter: 16
prerequisites: 重积分、多元函数微分学
minutes: 90
---

# 第 16 章　曲线积分与曲面积分

## 为什么要学

重积分在"平的"区域上累积。可现实中很多量是沿着**弯曲**的对象累积的：

- 一根弯曲的铁丝，密度处处不同，总质量是多少？——沿曲线累积一个**数量**（第一型曲线积分）。
- 一个质点在力场中沿弯曲路径运动，力做了多少功？——沿曲线累积力在切线方向的分量（第二型曲线积分）。
- 水流穿过一张弯曲的网，每秒流过多少水？——对曲面累积流速在法向的分量，即**通量**（第二型曲面积分）。

本章的高潮是三个"大定理"——**Green 公式、Gauss 公式、Stokes 公式**。它们说的是同一件事：

> **区域内部的"总变化"等于边界上的"总流量"。**

这正是 Newton–Leibniz 公式 $\int_a^bF'(x)\,\mathrm dx=F(b)-F(a)$ 在高维的推广：一维区间的边界是两个端点，平面区域的边界是闭曲线，空间区域的边界是闭曲面。把这个统一的图景装进脑子里，三个公式就不再是三个需要死记的东西。

## 16.1 第一型曲线积分（对弧长）

**定义**：$L$ 为光滑（或分段光滑）曲线，$f$ 在 $L$ 上有定义。把 $L$ 分成小弧段，长度 $\Delta s_i$，任取点 $(\xi_i,\eta_i)$，
$$\int_Lf(x,y)\,\mathrm ds=\lim_{\lambda\to0}\sum f(\xi_i,\eta_i)\Delta s_i.$$
物理意义：线密度为 $f$ 的曲线的质量。**与曲线方向无关。**

**计算**：若 $L:x=x(t),\ y=y(t)$，$\alpha\le t\le\beta$，则 $\mathrm ds=\sqrt{x'^2(t)+y'^2(t)}\,\mathrm dt$，
$$\int_Lf\,\mathrm ds=\int_\alpha^\beta f\big(x(t),y(t)\big)\sqrt{x'^2+y'^2}\,\mathrm dt\qquad(\text{要求 }\alpha<\beta).$$

**技巧**：被积函数中的 $x,y$ 满足曲线方程，可以**先代入化简**。例如在圆 $x^2+y^2=R^2$ 上，$\oint_L(x^2+y^2)\,\mathrm ds=R^2\cdot2\pi R$。对称性同样适用（曲线对称 + 被积函数奇偶性）。

## 16.2 第二型曲线积分（对坐标）

**定义**：$L$ 为**有向**曲线，向量场 $\boldsymbol F=(P,Q)$，
$$\int_LP\,\mathrm dx+Q\,\mathrm dy=\int_L\boldsymbol F\cdot\mathrm d\boldsymbol r.$$
物理意义：力 $\boldsymbol F$ 沿 $L$ 做的功。**改变方向，积分变号。**

**计算**：$L$ 从 $t=\alpha$（起点）到 $t=\beta$（终点），
$$\int_LP\,\mathrm dx+Q\,\mathrm dy=\int_\alpha^\beta\big[P(x(t),y(t))x'(t)+Q(x(t),y(t))y'(t)\big]\mathrm dt.$$
**这里 $\alpha$ 对应起点、$\beta$ 对应终点，$\alpha$ 不一定小于 $\beta$。**

**两类曲线积分的联系**：$\int_LP\,\mathrm dx+Q\,\mathrm dy=\int_L(P\cos\alpha+Q\cos\beta)\,\mathrm ds$，其中 $(\cos\alpha,\cos\beta)$ 是 $L$ 在该点沿指定方向的单位切向量。

## 16.3 Green 公式

**定理 16.1（Green）** 设 $D$ 是由分段光滑闭曲线围成的有界闭区域，$P,Q$ 在 $D$ 上有连续一阶偏导数，则
$$\oint_{\partial D}P\,\mathrm dx+Q\,\mathrm dy=\iint_D\left(\frac{\partial Q}{\partial x}-\frac{\partial P}{\partial y}\right)\mathrm dx\,\mathrm dy,$$
其中 $\partial D$ 取**正向**：沿边界走时，区域 $D$ 始终在左手边（外边界逆时针，内边界顺时针）。

**想法**：$\frac{\partial Q}{\partial x}-\frac{\partial P}{\partial y}$ 是向量场在一点的"旋转强度"（旋度）。把 $D$ 切成许多小方格，每个小方格边界上的环流量约等于旋度乘面积。相邻方格的公共边被走了两次、方向相反，互相抵消，只剩最外层的边界。

**证明要点**（$D$ 既是 $X$-型又是 $Y$-型时）：只证 $-\iint_D\frac{\partial P}{\partial y}=\oint P\,\mathrm dx$。设 $D=\{a\le x\le b,\ \varphi_1(x)\le y\le\varphi_2(x)\}$：
$$\iint_D\frac{\partial P}{\partial y}\,\mathrm dx\,\mathrm dy=\int_a^b\big[P(x,\varphi_2(x))-P(x,\varphi_1(x))\big]\mathrm dx.$$
而 $\oint P\,\mathrm dx$ 沿下边界（$x$ 从 $a$ 到 $b$）为 $\int_a^bP(x,\varphi_1(x))\,\mathrm dx$，沿上边界（$x$ 从 $b$ 到 $a$）为 $-\int_a^bP(x,\varphi_2(x))\,\mathrm dx$，竖直边上 $\mathrm dx=0$。两者恰好互为相反数。一般区域可分割成这样的小块。$\blacksquare$

**推论（面积公式）**：$|D|=\dfrac12\displaystyle\oint_{\partial D}x\,\mathrm dy-y\,\mathrm dx$。例如椭圆 $x=a\cos t,\ y=b\sin t$：$|D|=\frac12\int_0^{2\pi}ab\,\mathrm dt=\pi ab$。

## 16.4 曲线积分与路径无关

**定理 16.2** 设 $D$ 是平面上的**单连通**区域（无"洞"），$P,Q$ 在 $D$ 内有连续一阶偏导数，则以下四条等价：
1. 沿 $D$ 内任一闭曲线 $\oint_LP\,\mathrm dx+Q\,\mathrm dy=0$；
2. $\int_LP\,\mathrm dx+Q\,\mathrm dy$ 只与起点、终点有关，与路径无关；
3. 存在 $u(x,y)$ 使 $\mathrm du=P\,\mathrm dx+Q\,\mathrm dy$（$P\,\mathrm dx+Q\,\mathrm dy$ 是**全微分**，$u$ 称为**势函数**）；
4. 在 $D$ 内处处 $\dfrac{\partial P}{\partial y}=\dfrac{\partial Q}{\partial x}$。

满足时，$\int_{A}^{B}P\,\mathrm dx+Q\,\mathrm dy=u(B)-u(A)$——又是 Newton–Leibniz！

**单连通条件不可缺**：$P=\dfrac{-y}{x^2+y^2}$，$Q=\dfrac{x}{x^2+y^2}$ 在 $\mathbb R^2\setminus\{(0,0)\}$ 上满足 $P_y=Q_x$，但沿单位圆逆时针一周，
$$\oint\frac{x\,\mathrm dy-y\,\mathrm dx}{x^2+y^2}=\int_0^{2\pi}(\cos^2t+\sin^2t)\,\mathrm dt=2\pi\ne0.$$
原点这个"洞"让 Green 公式无法用在包含原点的区域上。

**例 1（挖洞法）** 设 $L$ 是任一条包围原点的分段光滑正向简单闭曲线，求 $\displaystyle\oint_L\frac{x\,\mathrm dy-y\,\mathrm dx}{x^2+y^2}$。

**解**：在 $L$ 内部取一个以原点为中心、半径 $\varepsilon$ 很小的圆 $C_\varepsilon$（顺时针）。在 $L$ 与 $C_\varepsilon$ 之间的环形区域上 $P,Q$ 光滑且 $Q_x-P_y=0$，由 Green 公式
$$\oint_L+\oint_{C_\varepsilon^{\text{顺}}}=0\ \Rightarrow\ \oint_L=\oint_{C_\varepsilon^{\text{逆}}}=2\pi.$$
（若 $L$ 不包围原点，则直接由 Green 公式得 $0$。）

## 16.5 曲面积分

**第一型曲面积分（对面积）**：$\displaystyle\iint_\Sigma f(x,y,z)\,\mathrm dS$，物理意义是面密度为 $f$ 的曲面质量，与曲面的侧无关。若 $\Sigma:z=z(x,y)$，$(x,y)\in D_{xy}$，则
$$\mathrm dS=\sqrt{1+z_x^2+z_y^2}\,\mathrm dx\,\mathrm dy,\qquad\iint_\Sigma f\,\mathrm dS=\iint_{D_{xy}}f\big(x,y,z(x,y)\big)\sqrt{1+z_x^2+z_y^2}\,\mathrm dx\,\mathrm dy.$$

**第二型曲面积分（对坐标）**：曲面 $\Sigma$ 选定一侧（指定了单位法向量 $\boldsymbol n=(\cos\alpha,\cos\beta,\cos\gamma)$），向量场 $\boldsymbol F=(P,Q,R)$，
$$\iint_\Sigma P\,\mathrm dy\,\mathrm dz+Q\,\mathrm dz\,\mathrm dx+R\,\mathrm dx\,\mathrm dy=\iint_\Sigma\boldsymbol F\cdot\boldsymbol n\,\mathrm dS,$$
物理意义是流速场 $\boldsymbol F$ 穿过 $\Sigma$ 流向指定一侧的**通量**。换侧则变号。

**计算**（以 $R\,\mathrm dx\,\mathrm dy$ 为例）：若 $\Sigma:z=z(x,y)$，
$$\iint_\Sigma R\,\mathrm dx\,\mathrm dy=\pm\iint_{D_{xy}}R\big(x,y,z(x,y)\big)\,\mathrm dx\,\mathrm dy,$$
取上侧（法向量与 $z$ 轴正向夹锐角）为正，下侧为负。**"一投、二代、三定号"。**

## 16.6 Gauss 公式与 Stokes 公式

**定理 16.3（Gauss）** 设空间闭区域 $\Omega$ 由分片光滑闭曲面 $\Sigma$ 围成，$P,Q,R$ 在 $\Omega$ 上有连续一阶偏导数，则
$$\oiint_\Sigma P\,\mathrm dy\,\mathrm dz+Q\,\mathrm dz\,\mathrm dx+R\,\mathrm dx\,\mathrm dy=\iiint_\Omega\left(\frac{\partial P}{\partial x}+\frac{\partial Q}{\partial y}+\frac{\partial R}{\partial z}\right)\mathrm dV,$$
其中 $\Sigma$ 取**外侧**。

被积函数 $\operatorname{div}\boldsymbol F=P_x+Q_y+R_z$ 称为**散度**，表示一点处"源"的强度（单位体积每秒涌出多少流体）。Gauss 公式说：**流出闭曲面的总通量 = 内部所有源的总强度。**

**定理 16.4（Stokes）** 设 $\Sigma$ 是分片光滑的有向曲面，边界 $\Gamma$ 为分段光滑闭曲线，$\Gamma$ 的方向与 $\Sigma$ 的侧符合**右手法则**（右手四指沿 $\Gamma$ 方向弯曲，拇指指向 $\Sigma$ 的法向），则
$$\oint_\Gamma P\,\mathrm dx+Q\,\mathrm dy+R\,\mathrm dz=\iint_\Sigma\begin{vmatrix}\mathrm dy\,\mathrm dz&\mathrm dz\,\mathrm dx&\mathrm dx\,\mathrm dy\\\dfrac{\partial}{\partial x}&\dfrac\partial{\partial y}&\dfrac\partial{\partial z}\\P&Q&R\end{vmatrix}.$$
右边就是旋度 $\operatorname{rot}\boldsymbol F=\nabla\times\boldsymbol F$ 穿过 $\Sigma$ 的通量。Green 公式是 Stokes 公式在平面上的特例。

**统一图景**：

| 公式 | 区域 | 边界 | 内部的"导数" |
|---|---|---|---|
| Newton–Leibniz | 区间 $[a,b]$ | 两端点 | $F'$ |
| Green | 平面区域 $D$ | 闭曲线 $\partial D$ | $Q_x-P_y$ |
| Stokes | 曲面 $\Sigma$ | 闭曲线 $\Gamma$ | 旋度 $\nabla\times\boldsymbol F$ |
| Gauss | 空间区域 $\Omega$ | 闭曲面 $\Sigma$ | 散度 $\nabla\cdot\boldsymbol F$ |

## 例题

**例 2（第一型曲线积分）** 计算 $\displaystyle\oint_L(x+y)\,\mathrm ds$，$L$ 为以 $O(0,0)$、$A(1,0)$、$B(0,1)$ 为顶点的三角形边界。

**解**：分三段。$OA$：$y=0$，$\mathrm ds=\mathrm dx$，$\int_0^1x\,\mathrm dx=\frac12$。$OB$：同理 $\frac12$。$AB$：$x+y=1$，长度 $\sqrt2$，被积函数恒为 $1$，积分为 $\sqrt2$。总和 $1+\sqrt2$。

**例 3（Green 公式补线）** 计算 $\displaystyle I=\int_L(e^x\sin y-y)\,\mathrm dx+(e^x\cos y-1)\,\mathrm dy$，$L$ 为上半圆周 $x^2+y^2=2x$（$y\ge0$）从 $A(2,0)$ 到 $O(0,0)$。

**解**：$L$ 不封闭，**补上**线段 $OA$（从 $O$ 到 $A$），$L+OA$ 围成上半圆盘 $D$，方向为逆时针（正向）。$Q_x-P_y=e^x\cos y-(e^x\cos y-1)=1$。由 Green 公式
$$\oint_{L+OA}=\iint_D1\,\mathrm d\sigma=\frac\pi2.$$
在 $OA$ 上 $y=0$，$\mathrm dy=0$，$\int_{OA}=\int_0^2(0-0)\,\mathrm dx=0$。所以 $I=\dfrac\pi2$。

> 补线法：开曲线 + 简单线段 = 闭曲线，用 Green 公式，再减去补上的那段。补线要选得简单，通常是坐标轴上的线段。

**例 4（Gauss 公式补面）** 计算 $\displaystyle I=\iint_\Sigma x\,\mathrm dy\,\mathrm dz+y\,\mathrm dz\,\mathrm dx+z\,\mathrm dx\,\mathrm dy$，$\Sigma$ 为上半球面 $z=\sqrt{R^2-x^2-y^2}$ 的上侧。

**解**：补上圆盘 $\Sigma_1:z=0$，$x^2+y^2\le R^2$，取下侧，与 $\Sigma$ 合成闭曲面的外侧。散度 $=3$，由 Gauss 公式
$$\oiint_{\Sigma+\Sigma_1}=3\cdot\frac23\pi R^3=2\pi R^3.$$
在 $\Sigma_1$ 上 $z=0$，且 $\mathrm dy\,\mathrm dz$、$\mathrm dz\,\mathrm dx$ 项因 $\Sigma_1$ 垂直于 $z$ 轴而为零，所以 $\iint_{\Sigma_1}=0$。故 $I=2\pi R^3$。

（直接验证：在球面上 $\boldsymbol F\cdot\boldsymbol n=\frac{x^2+y^2+z^2}R=R$，$I=R\cdot2\pi R^2$。）

**例 5（CMC 风格：Stokes 公式）** 计算 $\displaystyle\oint_\Gamma(y-z)\,\mathrm dx+(z-x)\,\mathrm dy+(x-y)\,\mathrm dz$，$\Gamma$ 为圆柱面 $x^2+y^2=a^2$ 与平面 $\dfrac xa+\dfrac zh=1$（$a,h>0$）的交线，从 $z$ 轴正向看去为逆时针。

**解**：取 $\Sigma$ 为平面上被 $\Gamma$ 围成的椭圆面，取上侧（与 $\Gamma$ 方向符合右手法则）。旋度：
$$\nabla\times\boldsymbol F=\big(R_y-Q_z,\ P_z-R_x,\ Q_x-P_y\big)=(-1-1,\ -1-1,\ -1-1)=(-2,-2,-2).$$
平面 $hx+az=ah$ 的上侧单位法向量 $\boldsymbol n=\dfrac{(h,0,a)}{\sqrt{h^2+a^2}}$。于是
$$\oint_\Gamma=\iint_\Sigma(\nabla\times\boldsymbol F)\cdot\boldsymbol n\,\mathrm dS=\frac{-2(h+a)}{\sqrt{a^2+h^2}}\cdot|\Sigma|.$$
$\Sigma$ 在 $xy$ 平面的投影是圆盘 $x^2+y^2\le a^2$，面积 $\pi a^2$；$\Sigma$ 与 $xy$ 平面夹角的余弦为 $\cos\gamma=\dfrac{a}{\sqrt{a^2+h^2}}$，所以 $|\Sigma|=\dfrac{\pi a^2}{\cos\gamma}=\pi a\sqrt{a^2+h^2}$。因此
$$\oint_\Gamma=-2\pi a(a+h).$$

**例 6（路径无关 + 求势函数）** 验证 $(2xy+3)\,\mathrm dx+(x^2-4y)\,\mathrm dy$ 是全微分，并计算从 $(0,0)$ 到 $(1,2)$ 沿任意路径的积分。

**解**：$P_y=2x=Q_x$，在 $\mathbb R^2$（单连通）上成立，故是全微分。求 $u$：由 $u_x=2xy+3$ 得 $u=x^2y+3x+\varphi(y)$，再由 $u_y=x^2+\varphi'(y)=x^2-4y$ 得 $\varphi=-2y^2$。所以 $u=x^2y+3x-2y^2$，积分 $=u(1,2)-u(0,0)=2+3-8=-3$。

## 易错点

1. **第一型积分与方向无关，第二型与方向有关。** 第一型化为定积分时下限必须小于上限；第二型的下限对应起点。
2. **Green 公式的方向：区域在左手边。** 有洞的区域，内边界要顺时针。
3. **Green/Gauss/Stokes 要求 $P,Q,R$ 在整个区域上有连续偏导**，区域内有奇点（如原点）时要"挖洞"。
4. **路径无关需要单连通**，$P_y=Q_x$ 在有洞的区域上不保证路径无关。
5. **第二型曲面积分投影时要定号**：上侧为正、下侧为负；曲面垂直于某坐标面时，对应的那一项为零。
6. **Gauss 公式用于闭曲面的外侧**；非闭曲面要补面，并注意补面的侧与原曲面合成外侧。

## 自测清单

1. 计算 $\displaystyle\oint_{x^2+y^2=1}x^2\,\mathrm ds$。
2. 写出 Green 公式，并说明正向的含义。
3. 用 Green 公式求星形线 $x=a\cos^3t$，$y=a\sin^3t$ 所围面积的表达式（写出积分即可）。
4. $\displaystyle\oint_L\frac{x\,\mathrm dy-y\,\mathrm dx}{x^2+y^2}$ 沿不包围原点的闭曲线等于多少？
5. 写出散度和旋度的定义。
6. 计算 $\displaystyle\oiint_{x^2+y^2+z^2=R^2}z\,\mathrm dx\,\mathrm dy$（外侧）。
7. 第一型曲面积分 $\iint_\Sigma\mathrm dS$ 的几何意义是什么？

## 参考答案

### 点击展开参考答案

1. 由对称性 $\oint x^2\mathrm ds=\oint y^2\mathrm ds=\frac12\oint1\,\mathrm ds=\pi$。
2. $\oint_{\partial D}P\,\mathrm dx+Q\,\mathrm dy=\iint_D(Q_x-P_y)\,\mathrm d\sigma$；正向指沿边界前进时区域在左侧。
3. $|D|=\frac12\int_0^{2\pi}(xy'-yx')\,\mathrm dt=\frac12\int_0^{2\pi}3a^2\sin^2t\cos^2t\,\mathrm dt$（$=\frac{3\pi a^2}8$）。
4. $0$（Green 公式，区域内无奇点，$Q_x-P_y=0$）。
5. $\operatorname{div}\boldsymbol F=P_x+Q_y+R_z$；$\operatorname{rot}\boldsymbol F=(R_y-Q_z,\ P_z-R_x,\ Q_x-P_y)$。
6. Gauss 公式：$\iiint1\,\mathrm dV=\frac43\pi R^3$。
7. 曲面 $\Sigma$ 的面积。


