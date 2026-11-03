---
title: 多元函数微分学
subject: 数学分析
chapter: 14
prerequisites: 多元函数的极限与连续、导数与微分
minutes: 90
---

# 第 14 章　多元函数微分学

## 为什么要学

一元函数的导数回答"$x$ 变一点，$y$ 变多少"。对二元函数 $z=f(x,y)$，我们可以只让 $x$ 变、固定 $y$——这就是**偏导数**。可是偏导数只看了两个方向（东西、南北），而站在山坡上，你可以朝任何方向迈步。

真正的"多元导数"是第 5 章那个思想的推广：**局部线性化**。在一点附近把曲面放大，可微的曲面越来越像一张**平面**——切平面。微分 $\mathrm dz=A\,\mathrm dx+B\,\mathrm dy$ 就是这张平面给出的近似。

这一章会看到一个一元时没有的现象：**偏导数都存在，函数却可能连切平面都没有，甚至不连续。** 理解"偏导存在""可微""偏导连续"三者的层级关系，是本章最核心的概念任务。

此外，多元微分学有大量实用工具：链式法则、方向导数与梯度（"最陡上坡方向"）、隐函数定理（何时能从方程 $F(x,y)=0$ 解出 $y=y(x)$）、条件极值与 Lagrange 乘数法。它们在物理、经济、优化中无处不在。

## 14.1 偏导数

**定义 14.1** 设 $z=f(x,y)$ 在 $(x_0,y_0)$ 附近有定义。若
$$f_x(x_0,y_0)=\lim_{\Delta x\to0}\frac{f(x_0+\Delta x,y_0)-f(x_0,y_0)}{\Delta x}$$
存在，称为 $f$ 在 $(x_0,y_0)$ 处关于 $x$ 的**偏导数**，也记作 $\dfrac{\partial f}{\partial x}(x_0,y_0)$。$f_y$ 类似。

**几何意义**：用平面 $y=y_0$ 去截曲面，得到一条曲线，$f_x(x_0,y_0)$ 是这条曲线在该点的切线斜率。

**计算**：求 $f_x$ 时把 $y$ 当常数，用一元求导法则。**在分段点则必须用定义。**

**偏导存在 ⇏ 连续。** 例：$f(x,y)=\dfrac{xy}{x^2+y^2}$，$f(0,0)=0$。因为 $f(x,0)\equiv0$，$f(0,y)\equiv0$，所以 $f_x(0,0)=f_y(0,0)=0$；但上一章已知 $f$ 在 $(0,0)$ 不连续。原因：偏导数只反映两条坐标轴上的信息，管不住其他方向。

## 14.2 全微分

**定义 14.2** 若存在常数 $A,B$ 使
$$\Delta z=f(x_0+\Delta x,y_0+\Delta y)-f(x_0,y_0)=A\Delta x+B\Delta y+o(\rho),\quad\rho=\sqrt{\Delta x^2+\Delta y^2}\to0,$$
称 $f$ 在 $(x_0,y_0)$ **可微**，$\mathrm dz=A\Delta x+B\Delta y$ 称为**全微分**。

**定理 14.1（可微的必要条件）** 若 $f$ 在 $(x_0,y_0)$ 可微，则
1. $f$ 在该点连续；
2. 偏导数存在，且 $A=f_x(x_0,y_0)$，$B=f_y(x_0,y_0)$，即 $\mathrm dz=f_x\,\mathrm dx+f_y\,\mathrm dy$。

（令 $\Delta y=0$，除以 $\Delta x$ 取极限即得 $A=f_x$。）

**判断可微的标准程序**：
1. 先求 $f_x(x_0,y_0),f_y(x_0,y_0)$（不存在则不可微）；
2. 检查
$$\lim_{\rho\to0}\frac{\Delta z-f_x\Delta x-f_y\Delta y}{\rho}=0$$
是否成立。成立则可微，否则不可微。

**定理 14.2（可微的充分条件）** 若 $f_x,f_y$ 在 $(x_0,y_0)$ 的某邻域内存在，且在该点**连续**，则 $f$ 在该点可微。

**证明**：把增量拆成"先动 $x$，再动 $y$"两步，每步用一元 Lagrange 中值定理：
$$\Delta z=\big[f(x_0+\Delta x,y_0+\Delta y)-f(x_0,y_0+\Delta y)\big]+\big[f(x_0,y_0+\Delta y)-f(x_0,y_0)\big]$$
$$=f_x(x_0+\theta_1\Delta x,y_0+\Delta y)\Delta x+f_y(x_0,y_0+\theta_2\Delta y)\Delta y,\quad0<\theta_1,\theta_2<1.$$
由偏导数的连续性，$f_x(x_0+\theta_1\Delta x,y_0+\Delta y)=f_x(x_0,y_0)+\alpha$，$f_y(\ldots)=f_y(x_0,y_0)+\beta$，其中 $\alpha,\beta\to0$。于是
$$\left|\Delta z-f_x\Delta x-f_y\Delta y\right|=|\alpha\Delta x+\beta\Delta y|\le(|\alpha|+|\beta|)\rho=o(\rho).\qquad\blacksquare$$

**层级关系**（务必记牢）：
$$\text{偏导数连续}\Rightarrow\text{可微}\Rightarrow\begin{cases}\text{连续}\\\text{偏导数存在}\end{cases}$$
每一个箭头都**不可逆**；"连续"与"偏导存在"之间互不蕴含。

**例 1（可微但偏导不连续）** $f(x,y)=(x^2+y^2)\sin\dfrac1{x^2+y^2}$，$f(0,0)=0$。

在 $(0,0)$：$f_x(0,0)=\lim\limits_{h\to0}h\sin\frac1{h^2}=0$，同理 $f_y(0,0)=0$；而 $\left|\dfrac{\Delta z}{\rho}\right|=\left|\rho\sin\frac1{\rho^2}\right|\le\rho\to0$，所以**可微**。但 $x\ne0$ 时
$$f_x(x,0)=2x\sin\frac1{x^2}-\frac2x\cos\frac1{x^2},$$
$x\to0$ 时无界，$f_x$ 在原点**不连续**。这是一元例子 $x^2\sin\frac1x$ 的二元版本。

**例 2（连续、偏导存在但不可微）** $f(x,y)=\sqrt{|xy|}$ 在 $(0,0)$。

连续显然；$f(x,0)\equiv0$ 给出 $f_x(0,0)=0$，同理 $f_y(0,0)=0$。检查
$$\frac{\Delta z-0}{\rho}=\frac{\sqrt{|\Delta x\Delta y|}}{\sqrt{\Delta x^2+\Delta y^2}},$$
沿 $\Delta y=\Delta x$ 等于 $\frac1{\sqrt2}\not\to0$，所以**不可微**。（几何上，这个曲面在原点沿对角线方向有"折痕"，没有切平面。）

## 14.3 链式法则

**定理 14.3** 若 $u=u(x,y)$，$v=v(x,y)$ 在 $(x,y)$ 处偏导数存在，$z=f(u,v)$ 在对应点**可微**，则
$$\frac{\partial z}{\partial x}=\frac{\partial z}{\partial u}\frac{\partial u}{\partial x}+\frac{\partial z}{\partial v}\frac{\partial v}{\partial x},\qquad\frac{\partial z}{\partial y}=\frac{\partial z}{\partial u}\frac{\partial u}{\partial y}+\frac{\partial z}{\partial v}\frac{\partial v}{\partial y}.$$

**口诀**：画出变量关系树（$z\to u,v\to x,y$），**"沿线相乘，分路相加"**。

**外层可微的条件不能去掉**：取 $f(u,v)=\dfrac{u^2v}{u^2+v^2}$（$f(0,0)=0$），$u=v=t$。$f_u(0,0)=f_v(0,0)=0$，若盲目套公式得 $\frac{\mathrm d}{\mathrm dt}f(t,t)\big|_{t=0}=0$，但实际上 $f(t,t)=\frac t2$，导数是 $\frac12$。

**全微分形式不变性**：无论 $u,v$ 是自变量还是中间变量，$\mathrm dz=f_u\,\mathrm du+f_v\,\mathrm dv$ 都成立。

## 14.4 方向导数与梯度

**定义 14.3** 设 $\boldsymbol l=(\cos\alpha,\cos\beta)$ 为单位向量。$f$ 在 $P_0$ 沿方向 $\boldsymbol l$ 的**方向导数**为
$$\frac{\partial f}{\partial\boldsymbol l}\bigg|_{P_0}=\lim_{t\to0^+}\frac{f(x_0+t\cos\alpha,y_0+t\cos\beta)-f(x_0,y_0)}{t}.$$

**定理 14.4** 若 $f$ 在 $P_0$ 可微，则沿任意方向的方向导数存在，且
$$\frac{\partial f}{\partial\boldsymbol l}=f_x\cos\alpha+f_y\cos\beta=\nabla f\cdot\boldsymbol l,$$
其中 $\nabla f=(f_x,f_y)$ 称为**梯度**。

由 Cauchy–Schwarz 不等式，$\left|\frac{\partial f}{\partial\boldsymbol l}\right|\le|\nabla f|$，当 $\boldsymbol l$ 与 $\nabla f$ 同向时取最大值 $|\nabla f|$。所以：**梯度方向是函数增长最快的方向，梯度的模是最大增长率**；与梯度垂直的方向是等高线的切线方向。

**注意**：沿所有方向的方向导数都存在 ⇏ 可微（例如 $f(x,y)=\sqrt{x^2+y^2}$ 在原点沿每个方向的方向导数都是 $1$，但偏导数 $f_x(0,0)$ 都不存在——单侧方向导数和双侧偏导数不同）。

## 14.5 高阶偏导数与 Taylor 公式

二阶偏导数有四个：$f_{xx},f_{xy},f_{yx},f_{yy}$。其中 $f_{xy}=\dfrac{\partial}{\partial y}\left(\dfrac{\partial f}{\partial x}\right)$。

**定理 14.5（Schwarz）** 若 $f_{xy}$ 和 $f_{yx}$ 在 $(x_0,y_0)$ 处都连续，则 $f_{xy}(x_0,y_0)=f_{yx}(x_0,y_0)$。

（不连续时可以不相等，经典反例是 $f=\dfrac{xy(x^2-y^2)}{x^2+y^2}$，$f(0,0)=0$，有 $f_{xy}(0,0)=-1$，$f_{yx}(0,0)=1$。）

**二元 Taylor 公式**（到二阶）：若 $f$ 在 $P_0$ 附近有连续的二阶偏导数，
$$f(x_0+h,y_0+k)=f(x_0,y_0)+f_xh+f_yk+\frac12\left(f_{xx}h^2+2f_{xy}hk+f_{yy}k^2\right)+o(h^2+k^2).$$
二次项系数组成的矩阵 $H=\begin{pmatrix}f_{xx}&f_{xy}\\f_{xy}&f_{yy}\end{pmatrix}$ 称为 **Hesse 矩阵**。

## 14.6 极值

**必要条件**：若 $f$ 在 $P_0$ 取极值且偏导数存在，则 $f_x(P_0)=f_y(P_0)=0$（$P_0$ 为**驻点**）。

**充分条件**：设 $P_0$ 为驻点，$f$ 有连续二阶偏导，记 $A=f_{xx}$，$B=f_{xy}$，$C=f_{yy}$（在 $P_0$ 处取值）：
- $AC-B^2>0$：取极值，$A>0$ 极小，$A<0$ 极大；
- $AC-B^2<0$：不是极值（鞍点）；
- $AC-B^2=0$：无法判断。

**想法**：在驻点处，Taylor 公式的一次项消失，$f$ 的增量由二次型 $Ah^2+2Bhk+Ck^2$ 决定。$AC-B^2>0$ 意味着这个二次型正定或负定。

**条件极值与 Lagrange 乘数法**：求 $f(x,y)$ 在约束 $\varphi(x,y)=0$ 下的极值。构造
$$L(x,y,\lambda)=f(x,y)+\lambda\varphi(x,y),$$
在 $\nabla\varphi\ne0$ 的条件下，极值点必满足 $L_x=L_y=L_\lambda=0$。

**几何解释**：在极值点，$f$ 的等高线与约束曲线**相切**，即两者的梯度平行：$\nabla f=-\lambda\nabla\varphi$。如果不相切，沿约束曲线走一小步就能让 $f$ 变大或变小。

## 14.7 隐函数定理

**定理 14.6** 设 $F(x,y)$ 在 $(x_0,y_0)$ 的某邻域内有连续偏导数，$F(x_0,y_0)=0$，$F_y(x_0,y_0)\ne0$。则在 $x_0$ 附近存在唯一的连续可导函数 $y=y(x)$，满足 $y(x_0)=y_0$，$F(x,y(x))\equiv0$，且
$$y'(x)=-\frac{F_x}{F_y}.$$

**想法**：$F_y\ne0$ 意味着固定 $x$ 时 $F$ 关于 $y$ 严格单调，于是每个 $x$ 恰好对应一个零点 $y$。导数公式只需对 $F(x,y(x))=0$ 用链式法则。

多变量推广：$F(x,y,z)=0$，$F_z\ne0$ 时可解出 $z=z(x,y)$，$z_x=-\dfrac{F_x}{F_z}$，$z_y=-\dfrac{F_y}{F_z}$。

## 例题

**例 3（抽象复合函数的二阶偏导）** 设 $z=f(xy,x+y)$，$f$ 有连续二阶偏导，求 $\dfrac{\partial^2z}{\partial x\partial y}$。

**解**：记 $u=xy$，$v=x+y$，$f_1=f_u$，$f_2=f_v$。
$$\frac{\partial z}{\partial x}=yf_1+f_2.$$
再对 $y$ 求偏导（注意 $f_1,f_2$ 仍是 $u,v$ 的函数，要再用一次链式法则）：
$$\frac{\partial^2z}{\partial x\partial y}=f_1+y\big(xf_{11}+f_{12}\big)+\big(xf_{21}+f_{22}\big)=f_1+xyf_{11}+(x+y)f_{12}+f_{22}.$$
（用到 $f_{12}=f_{21}$。）

**例 4（隐函数求导）** 设 $z=z(x,y)$ 由 $e^z=xyz$ 确定，求 $z_x$。

**解**：令 $F=e^z-xyz$，$F_x=-yz$，$F_z=e^z-xy$。$z_x=-\dfrac{F_x}{F_z}=\dfrac{yz}{e^z-xy}=\dfrac{yz}{xyz-xy}=\dfrac{z}{x(z-1)}$。

**例 5（无条件极值）** 求 $f(x,y)=x^3+y^3-3xy$ 的极值。

**解**：$f_x=3x^2-3y=0$，$f_y=3y^2-3x=0$，得驻点 $(0,0)$ 和 $(1,1)$。$f_{xx}=6x$，$f_{xy}=-3$，$f_{yy}=6y$。
- $(0,0)$：$AC-B^2=0-9<0$，非极值；
- $(1,1)$：$AC-B^2=36-9>0$，$A=6>0$，极小值 $f(1,1)=-1$。

**例 6（Lagrange 乘数法，CMC 风格）** 在椭球面 $\dfrac{x^2}{a^2}+\dfrac{y^2}{b^2}+\dfrac{z^2}{c^2}=1$ 内嵌入各棱平行于坐标轴的长方体，求最大体积。

**解**：设第一卦限的顶点为 $(x,y,z)$，体积 $V=8xyz$。构造
$$L=xyz+\lambda\left(\frac{x^2}{a^2}+\frac{y^2}{b^2}+\frac{z^2}{c^2}-1\right).$$
$L_x=yz+\dfrac{2\lambda x}{a^2}=0$，乘以 $x$：$xyz=-\dfrac{2\lambda x^2}{a^2}$。同理 $xyz=-\dfrac{2\lambda y^2}{b^2}=-\dfrac{2\lambda z^2}{c^2}$。所以 $\dfrac{x^2}{a^2}=\dfrac{y^2}{b^2}=\dfrac{z^2}{c^2}=\dfrac13$，即 $x=\dfrac a{\sqrt3}$ 等。

由于 $V$ 在约束曲面（第一卦限部分的闭包）上连续，在边界上 $V=0$，最大值必在内部取到，而内部唯一的候选点就是上面这个。所以最大体积为
$$V_{\max}=8\cdot\frac{abc}{3\sqrt3}=\frac{8abc}{3\sqrt3}.$$

（也可以用 AM–GM 不等式验证：$\sqrt[3]{\frac{x^2}{a^2}\cdot\frac{y^2}{b^2}\cdot\frac{z^2}{c^2}}\le\frac13$。）

> Lagrange 乘数法只给出**候选点**。要说明它确实是最值，通常用"连续函数在紧集上必有最值 + 边界上不可能 + 候选点唯一"的论证。

## 易错点

1. **偏导存在 ⇏ 连续 ⇏ 可微。** 判断可微要回到定义检查 $o(\rho)$。
2. **可微 ⇏ 偏导连续**（例 1）。偏导连续只是可微的充分条件。
3. **抽象函数求高阶偏导时，$f_1,f_2$ 仍然是复合函数**，求导时还要再用链式法则（例 3）。
4. **链式法则要求外层函数可微**，只有偏导存在不够。
5. **二阶充分条件 $AC-B^2=0$ 时失效**，需要另行分析（如沿特殊路径比较函数值）。
6. **Lagrange 乘数法得到的是必要条件**，最后要论证最值确实在候选点取得。

## 自测清单

1. 写出 $f$ 在 $(x_0,y_0)$ 可微的定义。
2. "偏导连续、可微、连续、偏导存在"之间有哪些蕴含关系？
3. 求 $f(x,y)=x^2+xy+y^2$ 在 $(1,1)$ 沿方向 $\left(\frac35,\frac45\right)$ 的方向导数，以及最大方向导数。
4. 设 $z=f(x^2-y^2)$，$f$ 可导，证明 $y\,z_x+x\,z_y=0$。
5. 由 $x^2+y^2=1$ 确定的隐函数 $y=y(x)$ 在 $(0,1)$ 附近的导数 $y'(0)$ 是多少？在 $(1,0)$ 附近为什么不能用隐函数定理解出 $y=y(x)$？
6. $f(x,y)=x^2-y^2$ 在原点有极值吗？
7. 用 Lagrange 乘数法求 $x+y$ 在 $x^2+y^2=2$ 上的最大值。

## 参考答案

### 点击展开参考答案

1. 存在 $A,B$ 使 $\Delta z=A\Delta x+B\Delta y+o(\rho)$，$\rho=\sqrt{\Delta x^2+\Delta y^2}\to0$。
2. 偏导连续 ⇒ 可微 ⇒ 连续，且可微 ⇒ 偏导存在；所有反向都不成立；连续与偏导存在互不蕴含。
3. $\nabla f(1,1)=(3,3)$，方向导数 $=3\cdot\frac35+3\cdot\frac45=\frac{21}5$；最大方向导数 $|\nabla f|=3\sqrt2$。
4. $z_x=2xf'$，$z_y=-2yf'$，$yz_x+xz_y=2xyf'-2xyf'=0$。
5. $y'=-\frac{x}{y}$，$y'(0)=0$。在 $(1,0)$ 处 $F_y=2y=0$，条件不满足；实际上那里每个 $x<1$ 对应两个 $y$。
6. 没有：$(0,0)$ 是驻点，但 $AC-B^2=2\cdot(-2)-0<0$，是鞍点。
7. $L=x+y+\lambda(x^2+y^2-2)$，得 $x=y=\pm1$，最大值 $2$（在 $(1,1)$）。


