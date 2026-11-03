---
title: 多元函数的极限与连续
subject: 数学分析
chapter: 13
prerequisites: 函数的连续性、实数与确界原理
minutes: 60
---

# 第 13 章　多元函数的极限与连续

## 为什么要学

现实中的量很少只依赖一个变量：气温依赖经度、纬度、高度和时间；一个物体的体积依赖长、宽、高。从这一章起，我们研究 $f(x,y)$ 乃至 $f(x_1,\dots,x_n)$。

大部分概念会"平移"过来：距离用欧氏距离代替绝对值，区间用球（圆盘）代替。但有一个本质的新现象：**在一维中，$x$ 只能从左右两个方向趋于 $x_0$；在平面上，点 $(x,y)$ 可以沿无穷多条路径——直线、抛物线、螺旋线——趋于 $(x_0,y_0)$。** 极限存在要求沿**所有**路径的极限都相同，这让二元极限比一元极限微妙得多，也带来许多反直觉的例子。

本章的另一个任务是把实数完备性搬到 $\mathbb R^n$：B–W 定理、Cauchy 准则、有界闭集上连续函数的最值定理与一致连续性，都会在 $\mathbb R^n$ 中重新出现。

## 13.1 $\mathbb R^n$ 中的点集

**距离**：$P=(x_1,\dots,x_n)$，$Q=(y_1,\dots,y_n)$，$|PQ|=\rho(P,Q)=\sqrt{\sum(x_i-y_i)^2}$。三角不等式 $\rho(P,R)\le\rho(P,Q)+\rho(Q,R)$ 成立。

**邻域**：$U(P_0,\delta)=\{P:\rho(P,P_0)<\delta\}$（开球）；去心邻域 $\mathring U(P_0,\delta)$ 去掉中心。（用方形邻域 $\{|x_i-x_i^0|<\delta\}$ 也可以，二者互相包含，定义出的极限一样。）

设 $E\subseteq\mathbb R^n$：
- **内点**：存在邻域 $U(P)\subseteq E$；
- **聚点**：$P$ 的任何去心邻域都含 $E$ 中的点（$P$ 本身可以不属于 $E$）；
- **边界点**：任何邻域内既有 $E$ 中的点又有不属于 $E$ 的点；
- **开集**：每个点都是内点；**闭集**：包含所有聚点（等价地，补集是开集）；
- **区域**：连通的开集；**有界集**：含于某个球内。

**点列收敛**：$P_k\to P_0$ 指 $\rho(P_k,P_0)\to0$，等价于**每个坐标分别收敛**（因为 $|x_i-y_i|\le\rho(P,Q)\le\sum|x_i-y_i|$）。

**完备性定理在 $\mathbb R^n$ 中的形式**：
- **Cauchy 准则**：点列收敛 $\iff$ 它是 Cauchy 列；
- **B–W 定理**：有界点列必有收敛子列（对坐标逐个抽子列）；
- **闭集套定理**、**有限覆盖定理**（Heine–Borel：$\mathbb R^n$ 中有界闭集的任一开覆盖有有限子覆盖）。

## 13.2 二重极限

**定义 13.1** 设 $f$ 定义在 $D\subseteq\mathbb R^2$ 上，$P_0=(x_0,y_0)$ 是 $D$ 的聚点。若
$$\forall\varepsilon>0,\ \exists\delta>0,\ \forall P\in D\cap\mathring U(P_0,\delta):\ |f(P)-A|<\varepsilon,$$
称 $A$ 为 $f$ 当 $P\to P_0$ 时的**二重极限**，记作 $\lim\limits_{(x,y)\to(x_0,y_0)}f(x,y)=A$。

与一元情形一样，四则运算、夹逼准则、Heine 归结原理（$\lim f(P)=A\iff$ 对 $D$ 中任意 $P_k\to P_0$、$P_k\ne P_0$，都有 $f(P_k)\to A$）都成立。

**路径的威力与局限**：
- 若沿某两条路径极限不同，或沿某条路径极限不存在，则二重极限**不存在**；
- 沿所有直线的极限都相同，**不能**推出二重极限存在（见例 2）。

**证明二重极限存在的常用方法**：
1. **放缩夹逼**：把 $|f(x,y)-A|$ 放大为只依赖于 $\rho=\sqrt{(x-x_0)^2+(y-y_0)^2}$ 且趋于 $0$ 的量；
2. **极坐标**：令 $x=x_0+\rho\cos\theta$，$y=y_0+\rho\sin\theta$，若 $|f-A|\le g(\rho)\to0$ **且 $g$ 与 $\theta$ 无关**，则极限为 $A$。（只证明"对每个固定的 $\theta$，$\rho\to0$ 时趋于 $A$"是不够的——这只是沿射线。）

**例 1** 证明 $\displaystyle\lim_{(x,y)\to(0,0)}\frac{x^2y}{x^2+y^2}=0$。

**证明**：由 $x^2\le x^2+y^2$，$\left|\dfrac{x^2y}{x^2+y^2}\right|\le|y|\le\sqrt{x^2+y^2}$。任给 $\varepsilon>0$，取 $\delta=\varepsilon$ 即可。$\blacksquare$

**例 2** 讨论 $f(x,y)=\dfrac{x^2y}{x^4+y^2}$ 在 $(0,0)$ 处的极限。

**解**：沿直线 $y=kx$：$f=\dfrac{kx^3}{x^4+k^2x^2}=\dfrac{kx}{x^2+k^2}\to0$（$k=0$ 时 $f\equiv0$；沿 $x=0$ 时 $f\equiv0$）。所以**沿所有直线极限都是 $0$**。

但沿抛物线 $y=x^2$：$f=\dfrac{x^4}{2x^4}=\dfrac12$。所以二重极限**不存在**。

> 这个例子要牢记。"所有直线"看起来已经很多了，但抛物线可以"溜过"所有直线的检验。关键在于分子分母的"次数平衡"：令 $y\sim x^2$ 时，$x^4$ 和 $y^2$ 同阶，此时才能看清函数的真实行为。

## 13.3 累次极限

**累次极限**是先对一个变量取极限，再对另一个取：
$$\lim_{x\to x_0}\lim_{y\to y_0}f(x,y),\qquad\lim_{y\to y_0}\lim_{x\to x_0}f(x,y).$$

二重极限与两个累次极限之间**没有必然联系**：

| 例子 | 二重极限 | 累次极限 |
|---|---|---|
| $\dfrac{xy}{x^2+y^2}$ 在 $(0,0)$ | 不存在 | 两个都是 $0$ |
| $\dfrac{x-y}{x+y}$ 在 $(0,0)$ | 不存在 | 一个是 $1$，一个是 $-1$ |
| $x\sin\dfrac1y+y\sin\dfrac1x$ 在 $(0,0)$ | $0$ | 两个都不存在 |

第三个例子：$|f|\le|x|+|y|\to0$，二重极限为 $0$；但固定 $x\ne0$ 时，$y\to0$ 时 $x\sin\frac1y$ 振荡，内层极限不存在。

**定理 13.1** 若二重极限 $\lim\limits_{(x,y)\to(x_0,y_0)}f=A$ 存在，且对每个 $x\ne x_0$（在 $x_0$ 附近），内层极限 $\lim\limits_{y\to y_0}f(x,y)=\varphi(x)$ 存在，则累次极限 $\lim\limits_{x\to x_0}\varphi(x)=A$。

**证明**：任给 $\varepsilon>0$，取 $\delta$ 使 $0<\rho<\delta$ 时 $|f(x,y)-A|<\varepsilon$。固定满足 $0<|x-x_0|<\frac{\delta}{2}$ 的 $x$，对 $0<|y-y_0|<\frac{\delta}{2}$ 有 $|f(x,y)-A|<\varepsilon$；令 $y\to y_0$ 得 $|\varphi(x)-A|\le\varepsilon$。$\blacksquare$

**推论**：若两个累次极限都存在但不相等，则二重极限不存在。

## 13.4 多元连续函数

**定义 13.2** 若 $\lim\limits_{P\to P_0}f(P)=f(P_0)$（$P_0\in D$），称 $f$ 在 $P_0$ 连续。

- 连续函数的和、差、积、商（分母不为零）、复合仍连续；**多元初等函数在其定义区域内连续**。
- **对每个变量分别连续 ⇏ 联合连续。** 例如 $f(x,y)=\dfrac{xy}{x^2+y^2}$，$f(0,0)=0$：$f(x,0)\equiv0$，$f(0,y)\equiv0$，分别连续；但沿 $y=x$ 趋于 $\frac12$，不连续。

**有界闭集上连续函数的性质**（$D$ 为 $\mathbb R^n$ 中有界闭集，$f\in C(D)$）：
1. **有界性与最值定理**：$f$ 在 $D$ 上有界并取到最大值和最小值；
2. **一致连续性**：$\forall\varepsilon>0,\exists\delta>0$，$P,Q\in D$，$\rho(P,Q)<\delta\Rightarrow|f(P)-f(Q)|<\varepsilon$；
3. **介值定理**（需要 $D$ 连通，如区域的闭包）：$f$ 取遍最小值与最大值之间的一切值。

证明与一元情形完全相同：反证 + B–W 抽子列 + 闭集保证极限点仍在 $D$ 中。这再次说明：**"有界闭"在 $\mathbb R^n$ 中扮演了 $[a,b]$ 的角色**（这种集合称为紧集）。

## 例题

**例 3（用放缩证明）** 求 $\displaystyle\lim_{(x,y)\to(0,0)}(x^2+y^2)\ln(x^2+y^2)$。

**解**：令 $t=x^2+y^2\to0^+$，它是只依赖于 $\rho$ 的量，化为一元极限 $\lim\limits_{t\to0^+}t\ln t=0$。

**例 4（极坐标的正确用法）** 求 $\displaystyle\lim_{(x,y)\to(0,0)}\frac{x^3+y^3}{x^2+y^2}$。

**解**：令 $x=\rho\cos\theta$，$y=\rho\sin\theta$：
$$\left|\frac{x^3+y^3}{x^2+y^2}\right|=\rho|\cos^3\theta+\sin^3\theta|\le2\rho,$$
上界 $2\rho$ 与 $\theta$ 无关且趋于 $0$，所以极限为 $0$。

**反面教材**：对例 2 用极坐标，$f=\dfrac{\rho\cos^2\theta\sin\theta}{\rho^2\cos^4\theta+\sin^2\theta}$。对每个固定的 $\theta$（$\sin\theta\ne0$），$\rho\to0$ 时趋于 $0$——但这**不能**说明极限是 $0$，因为当 $\theta$ 随 $\rho$ 变化（$\sin\theta\approx\rho\cos^2\theta$，也就是沿抛物线）时，分母可以和分子一样小。

**例 5（判断连续性）** 讨论 $f(x,y)=\begin{cases}\dfrac{\sin(xy)}{x},&x\ne0\\y,&x=0\end{cases}$ 在 $\mathbb R^2$ 上的连续性。

**解**：在 $x\ne0$ 处是初等函数，连续。在点 $(0,y_0)$ 处：当 $x\ne0$ 时，
$$\left|\frac{\sin(xy)}{x}-y_0\right|\le\left|\frac{\sin(xy)}x-y\right|+|y-y_0|.$$
若 $y=0$，第一项为 $0$；否则 $\left|\frac{\sin(xy)}{x}-y\right|=|y|\left|\frac{\sin(xy)}{xy}-1\right|$，当 $(x,y)\to(0,y_0)$ 时 $xy\to0$，故趋于 $0$。当 $x=0$ 时 $|f-y_0|=|y-y_0|\to0$。所以 $f$ 在 $\mathbb R^2$ 上处处连续。

**例 6（CMC 风格）** 设 $f(x,y)$ 在 $\mathbb R^2$ 上连续，且 $\lim\limits_{x^2+y^2\to+\infty}f(x,y)=+\infty$。证明 $f$ 在 $\mathbb R^2$ 上取到最小值。

**想法**：$\mathbb R^2$ 不是有界闭集，不能直接用最值定理。但函数在远处很大，所以最小值只可能在某个大圆盘里——把问题限制到有界闭集上。

**证明**：令 $c=f(0,0)$。由条件，存在 $R>0$ 使 $x^2+y^2>R^2$ 时 $f(x,y)>c$。闭圆盘 $\overline D=\{x^2+y^2\le R^2\}$ 是有界闭集，$f$ 在其上取到最小值 $f(P_0)$，且 $f(P_0)\le f(0,0)=c$。对圆盘外的点，$f>c\ge f(P_0)$。所以 $f(P_0)$ 是 $f$ 在 $\mathbb R^2$ 上的最小值。$\blacksquare$

> 这种"先用增长条件把问题赶进紧集，再用最值定理"的思路，在 CMC 的存在性证明中非常常见。

## 易错点

1. **沿所有直线极限相同 ⇏ 二重极限存在**（例 2）。
2. **极坐标法必须得到与 $\theta$ 无关的上界**，否则只证明了沿射线的极限。
3. **二重极限与累次极限互不蕴含**，见 13.3 的表格。
4. **对各变量分别连续 ⇏ 联合连续。**
5. **最值定理需要有界闭集**，开集或无界集上要先想办法化归（例 6）。
6. 聚点不一定属于集合，定义极限时点 $P_0$ 只需是聚点，不必在定义域内。

## 自测清单

1. 写出 $\lim\limits_{(x,y)\to(0,0)}f(x,y)=A$ 的 $\varepsilon$-$\delta$ 定义。
2. $\lim\limits_{(x,y)\to(0,0)}\dfrac{xy}{\sqrt{x^2+y^2}}$ 是多少？
3. $\lim\limits_{(x,y)\to(0,0)}\dfrac{xy^2}{x^2+y^4}$ 存在吗？
4. $\dfrac{x^2-y^2}{x^2+y^2}$ 在 $(0,0)$ 的两个累次极限各是多少？二重极限呢？
5. 求 $\lim\limits_{(x,y)\to(0,2)}\dfrac{\sin(xy)}{x}$。
6. 集合 $\{(x,y):0<x^2+y^2\le1\}$ 是开集吗？闭集吗？
7. 举例说明有界开集上的连续函数可能取不到最大值。

## 参考答案

### 点击展开参考答案

1. $\forall\varepsilon>0,\exists\delta>0$，当 $0<\sqrt{x^2+y^2}<\delta$（且 $(x,y)\in D$）时 $|f(x,y)-A|<\varepsilon$。
2. $0$：$\left|\frac{xy}{\sqrt{x^2+y^2}}\right|\le\frac{\frac12(x^2+y^2)}{\sqrt{x^2+y^2}}=\frac12\rho$。
3. 不存在：沿 $x=y^2$ 得 $\frac12$，沿 $x=0$ 得 $0$。
4. 先 $y\to0$ 再 $x\to0$ 得 $1$；先 $x\to0$ 再 $y\to0$ 得 $-1$；二重极限不存在。
5. $\frac{\sin(xy)}{xy}\cdot y\to1\cdot2=2$（$y\ne0$ 附近；$x\to0$ 时 $xy\to0$）。
6. 都不是：$(1,0)$ 不是内点（不是开集）；$(0,0)$ 是聚点但不属于它（不是闭集）。
7. $f(x,y)=x$ 在开圆盘 $x^2+y^2<1$ 上，上确界 $1$ 取不到。


