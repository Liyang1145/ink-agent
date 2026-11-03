---
title: 函数列与函数项级数
subject: 数学分析
chapter: 11
prerequisites: 数项级数、函数的连续性、定积分
minutes: 75
---

# 第 11 章　函数列与函数项级数

## 为什么要学

我们常常想用一列"好"函数去逼近一个函数：用多项式逼近 $e^x$，用三角函数逼近方波。逼近完了自然会问：**极限函数还"好"吗？**

- 连续函数的极限还连续吗？
- 积分的极限等于极限的积分吗？$\displaystyle\lim_{n\to\infty}\int f_n=\int\lim_{n\to\infty}f_n$？
- 导数呢？

这些问题都是在问**两个极限能否交换次序**。答案出乎意料：**一般不能！** 看 $f_n(x)=x^n$ 在 $[0,1]$ 上：每个 $f_n$ 都连续，但极限函数在 $x<1$ 时为 $0$、在 $x=1$ 时为 $1$——不连续了。

问题出在哪里？对每个固定的 $x<1$，$x^n\to0$，但 $x$ 越接近 $1$，收敛得越慢，需要的 $N$ 越大，$x\to1$ 时 $N\to\infty$，**没有一个统一的 $N$**。这和第 4 章"连续 vs 一致连续"是完全相同的故事。

解决方案就是 **一致收敛**：要求一个 $N$ 对所有 $x$ 同时有效。有了它，连续性、积分、（加适当条件后的）求导都能和极限交换。这一章是数学分析中最需要"把量词想清楚"的一章。

## 11.1 逐点收敛与一致收敛

设 $f_n,f$ 定义在集合 $D$ 上。

**定义 11.1（逐点收敛）** 若对每个 $x\in D$，$f_n(x)\to f(x)$，即
$$\forall x\in D,\ \forall\varepsilon>0,\ \exists N(\varepsilon,x),\ \forall n>N:\ |f_n(x)-f(x)|<\varepsilon.$$

**定义 11.2（一致收敛）** 若
$$\forall\varepsilon>0,\ \exists N(\varepsilon),\ \forall n>N,\ \forall x\in D:\ |f_n(x)-f(x)|<\varepsilon,$$
称 $\{f_n\}$ 在 $D$ 上**一致收敛**于 $f$，记作 $f_n\rightrightarrows f$。

**区别只在量词顺序**：一致收敛中 $N$ 只依赖于 $\varepsilon$，先于 $x$ 选定。

**图像**：在极限函数 $f$ 的图像上下各画一条距离为 $\varepsilon$ 的曲线，形成一条"$\varepsilon$-管道"。一致收敛意味着：从某个 $N$ 起，$f_n$ 的**整个图像**都落在管道里。

**定理 11.1（上确界判别）** $f_n\rightrightarrows f$ 于 $D$ $\iff$
$$\beta_n=\sup_{x\in D}|f_n(x)-f(x)|\to0.$$

这是最实用的判别方法：**求出 $|f_n-f|$ 的最大值（或上界），看它是否趋于 $0$**。

**否定形式**：$f_n$ 不一致收敛于 $f$ $\iff$ 存在 $\varepsilon_0>0$ 及 $x_n\in D$（$n$ 取一个子列）使 $|f_n(x_n)-f(x_n)|\ge\varepsilon_0$。常用技巧是找一列点 $x_n$ 使 $|f_n(x_n)-f(x_n)|\not\to0$。

**例 1** 讨论 $f_n(x)=x^n$ 在 $[0,1)$ 与 $[0,a]$（$0<a<1$）上的一致收敛性。

**解**：逐点极限 $f\equiv0$（在 $[0,1)$ 上）。
- 在 $[0,1)$ 上：$\sup\limits_{[0,1)}x^n=1\not\to0$，不一致收敛。（或取 $x_n=1-\frac1n$，$f_n(x_n)=\left(1-\frac1n\right)^n\to\frac1e\ne0$。）
- 在 $[0,a]$ 上：$\sup x^n=a^n\to0$，一致收敛。

> 这种"在整个开区间上不一致收敛，但在每个闭子区间上一致收敛"的情况很常见，称为**内闭一致收敛**。对连续性、可导性这类**局部**性质，内闭一致收敛就足够了。

**例 2** $f_n(x)=\dfrac{nx}{1+n^2x^2}$ 在 $[0,1]$ 上是否一致收敛？

**解**：逐点极限为 $0$（$x=0$ 时恒为 $0$；$x>0$ 时 $\le\frac{nx}{n^2x^2}=\frac1{nx}\to0$）。求最大值：由 $1+n^2x^2\ge2nx$ 知 $f_n\le\frac12$，在 $x=\frac1n$ 处取等。所以 $\sup|f_n-0|=\frac12\not\to0$，不一致收敛。

**图像**：每个 $f_n$ 都有一个高度为 $\frac12$ 的"驼峰"，驼峰位置 $\frac1n$ 随 $n$ 向 $0$ 移动。对每个固定的 $x$，驼峰终会经过它而离开，所以逐点趋于 $0$；但驼峰本身永不消失。

## 11.2 Cauchy 准则与函数项级数

**定理 11.2（一致收敛的 Cauchy 准则）** $\{f_n\}$ 在 $D$ 上一致收敛 $\iff$
$$\forall\varepsilon>0,\ \exists N,\ \forall m,n>N,\ \forall x\in D:\ |f_m(x)-f_n(x)|<\varepsilon.$$
它的好处是不需要事先知道极限函数。

**函数项级数** $\sum u_n(x)$ 的一致收敛定义为部分和函数列 $S_n(x)$ 一致收敛。等价地，余项 $R_n(x)=\sum_{k>n}u_k(x)$ 满足 $\sup\limits_D|R_n(x)|\to0$。

**必要条件**：若 $\sum u_n$ 在 $D$ 上一致收敛，则 $u_n\rightrightarrows0$。

**定理 11.3（Weierstrass M 判别法）** 若 $|u_n(x)|\le M_n$ 对所有 $x\in D$ 成立，且 $\sum M_n$ 收敛，则 $\sum u_n(x)$ 在 $D$ 上一致收敛（且绝对收敛）。

**证明**：对 $x\in D$，$\left|\sum_{k=n+1}^{n+p}u_k(x)\right|\le\sum_{k=n+1}^{n+p}M_k$，右边与 $x$ 无关，由 $\sum M_n$ 的 Cauchy 准则可以任意小；再用一致收敛的 Cauchy 准则。$\blacksquare$

例如 $\sum\dfrac{\sin nx}{n^2}$ 在 $\mathbb R$ 上一致收敛（$M_n=\frac1{n^2}$）。

M 判别法简单好用，但只能处理绝对收敛的情形。对条件收敛的级数（如 $\sum\frac{\sin nx}n$），有一致收敛版本的 **Dirichlet 判别法**：若 $\sum a_n(x)$ 的部分和在 $D$ 上**一致有界**，$b_n(x)$ 对每个 $x$ 关于 $n$ 单调且 $b_n\rightrightarrows0$，则 $\sum a_n(x)b_n(x)$ 在 $D$ 上一致收敛。（Abel 判别法也有类似的一致版本。）

## 11.3 一致收敛的三大定理

**定理 11.4（连续性）** 若每个 $f_n$ 在 $D$ 上连续，且 $f_n\rightrightarrows f$，则 $f$ 在 $D$ 上连续。

**想法（$\varepsilon/3$ 论证）**：要让 $|f(x)-f(x_0)|$ 小，把它拆成三段：$f$ 到 $f_N$（一致收敛管）、$f_N(x)$ 到 $f_N(x_0)$（$f_N$ 连续管）、$f_N$ 回到 $f$（一致收敛管）。

**证明**：任给 $\varepsilon>0$，由一致收敛取定一个 $N$ 使 $|f_N(x)-f(x)|<\frac\varepsilon3$ **对所有** $x\in D$ 成立。由 $f_N$ 在 $x_0$ 连续，存在 $\delta>0$ 使 $|x-x_0|<\delta$ 时 $|f_N(x)-f_N(x_0)|<\frac\varepsilon3$。于是
$$|f(x)-f(x_0)|\le|f(x)-f_N(x)|+|f_N(x)-f_N(x_0)|+|f_N(x_0)-f(x_0)|<\varepsilon.\qquad\blacksquare$$

> 注意"对所有 $x$"在哪里用到了：第一项中的 $x$ 是邻域里**任意**的点，我们无法预先知道它是哪个，所以需要一个对所有 $x$ 都有效的 $N$。若只是逐点收敛，$N$ 会随 $x$ 变化，论证就断了。

用级数语言：**一致收敛的连续函数项级数，和函数连续。** 因此 $\lim\limits_{x\to x_0}\sum u_n(x)=\sum\lim\limits_{x\to x_0}u_n(x)$，可以逐项取极限。

**推论**：若连续函数列逐点收敛到不连续函数，则它一定不一致收敛。（例 1 中 $x^n$ 在 $[0,1]$ 上即是如此。）

**定理 11.5（积分）** 若 $f_n$ 在 $[a,b]$ 上可积，$f_n\rightrightarrows f$，则 $f$ 可积，且
$$\lim_{n\to\infty}\int_a^bf_n(x)\,\mathrm dx=\int_a^bf(x)\,\mathrm dx.$$

*证明*（在 $f_n$ 连续的情形，此时 $f$ 连续从而可积）：
$$\left|\int_a^bf_n-\int_a^bf\right|\le\int_a^b|f_n-f|\le(b-a)\sup_{[a,b]}|f_n-f|\to0.\qquad\blacksquare$$

级数形式：**一致收敛的级数可以逐项积分**。

**反例**：$f_n(x)=n^2xe^{-nx}$ 在 $[0,1]$ 上逐点趋于 $0$，但 $\int_0^1f_n\,\mathrm dx\to1$（作代换 $t=nx$：$\int_0^nte^{-t}\mathrm dt\to1$）。积分和极限不能交换，当然也不一致收敛（$f_n\left(\frac1n\right)=\frac ne\to\infty$）。

**定理 11.6（求导）** 设 $f_n$ 在 $[a,b]$ 上连续可导，且
1. 存在 $x_0\in[a,b]$ 使 $\{f_n(x_0)\}$ 收敛；
2. $f_n'\rightrightarrows g$ 于 $[a,b]$，

则 $f_n$ 在 $[a,b]$ 上一致收敛于某个 $f$，$f$ 可导且 $f'=g$，即 $\left(\lim f_n\right)'=\lim f_n'$。

**证明**：由 Newton–Leibniz 公式 $f_n(x)=f_n(x_0)+\int_{x_0}^xf_n'(t)\,\mathrm dt$。由条件 1 与定理 11.5，右边一致收敛于 $f(x):=\lim f_n(x_0)+\int_{x_0}^xg(t)\,\mathrm dt$（一致性来自 $\left|\int_{x_0}^x(f_n'-g)\right|\le(b-a)\sup|f_n'-g|$）。$g$ 作为连续函数的一致极限是连续的，由变上限积分求导定理，$f'=g$。$\blacksquare$

**注意**：求导定理要求的是**导函数列**一致收敛，而不是 $f_n$ 本身。反例：$f_n(x)=\dfrac{\sin nx}{\sqrt n}\rightrightarrows0$，但 $f_n'(x)=\sqrt n\cos nx$ 在 $x=0$ 处趋于 $\infty$。

级数形式：若 $\sum u_n'(x)$ 一致收敛，$\sum u_n(x_0)$ 收敛，则可以逐项求导。

## 例题

**例 3（M 判别法 + 逐项求导）** 证明 $f(x)=\sum\limits_{n=1}^\infty\dfrac{\cos nx}{n^3}$ 在 $\mathbb R$ 上连续可导。

**证明**：$\left|\frac{\cos nx}{n^3}\right|\le\frac1{n^3}$，M 判别法给出一致收敛，各项连续，故 $f$ 连续。逐项求导得 $-\sum\frac{\sin nx}{n^2}$，又有 $\left|\frac{\sin nx}{n^2}\right|\le\frac1{n^2}$，一致收敛；所以 $f$ 可导且 $f'(x)=-\sum\frac{\sin nx}{n^2}$，后者连续。$\blacksquare$

**例 4（内闭一致收敛）** 证明 $\zeta(x)=\sum\limits_{n=1}^\infty\dfrac1{n^x}$ 在 $(1,+\infty)$ 上连续，但在 $(1,+\infty)$ 上不一致收敛。

**证明**：**连续性**：任取 $x_0>1$，取 $a$ 满足 $1<a<x_0$。在 $[a,+\infty)$ 上 $\frac1{n^x}\le\frac1{n^a}$，$\sum\frac1{n^a}$ 收敛，M 判别法给出在 $[a,+\infty)$ 上一致收敛，从而 $\zeta$ 在 $[a,+\infty)$ 上连续，特别在 $x_0$ 连续。由 $x_0$ 任意得证。

**不一致收敛**：若在 $(1,+\infty)$ 上一致收敛，由 Cauchy 准则，取 $\varepsilon=\frac14$，存在 $N$，对 $n>N$ 和所有 $x>1$：$\sum_{k=n+1}^{2n}\frac1{k^x}<\frac14$。令 $x\to1^+$（有限项和，可以取极限），得 $\sum_{k=n+1}^{2n}\frac1k\le\frac14$。但左边 $\ge n\cdot\frac1{2n}=\frac12$，矛盾。$\blacksquare$

> **"局部性质只需内闭一致收敛"**：要证在开区间上连续/可导，只要在每个点附近的一个闭区间上一致收敛即可。这一技巧在幂级数中会反复用到。

**例 5（CMC 风格：Dini 定理）** 设 $f_n$ 在 $[a,b]$ 上连续，对每个 $x$，$f_n(x)$ 关于 $n$ 单调递减且收敛于连续函数 $f(x)$。证明 $f_n\rightrightarrows f$。

**想法**：令 $g_n=f_n-f\ge0$，递减趋于 $0$。对每个点，$g_n$ 最终变小，并且由于连续性，在该点的一个**邻域**内也变小；再用紧性（B–W 定理或有限覆盖）把"局部"拼成"整体"。

**证明**：反设不一致收敛，则存在 $\varepsilon_0>0$ 和子列 $n_k$ 及 $x_k\in[a,b]$ 使 $g_{n_k}(x_k)\ge\varepsilon_0$。由 B–W 定理，不妨 $x_k\to\xi\in[a,b]$。对任意固定的 $m$，当 $n_k\ge m$ 时由单调性 $g_m(x_k)\ge g_{n_k}(x_k)\ge\varepsilon_0$。令 $k\to\infty$，由 $g_m$ 连续得 $g_m(\xi)\ge\varepsilon_0$。这对所有 $m$ 成立，与 $g_m(\xi)\to0$ 矛盾。$\blacksquare$

> Dini 定理的三个条件（闭区间、单调、极限连续）缺一不可。例 1 中 $x^n$ 在 $[0,1)$ 上单调递减趋于连续函数 $0$，但区间不闭，不一致收敛。

## 易错点

1. **逐点收敛 ≠ 一致收敛。** 判断一致收敛的标准动作是计算 $\sup_D|f_n-f|$。
2. **连续函数列的一致极限连续，但逆命题不成立。** 极限连续不能推出一致收敛（例 2：极限是 $0$，连续，但不一致收敛）。
3. **逐项求导需要导函数级数一致收敛**，而不是原级数一致收敛。
4. **M 判别法只是充分条件。** $\sum\frac{(-1)^n}{n+x^2}$ 在 $\mathbb R$ 上一致收敛（Leibniz 余项估计 $|R_n(x)|\le\frac1{n+1+x^2}\le\frac1{n+1}$），但不能用 M 判别法（$\sup_x\frac1{n+x^2}=\frac1n$，$\sum\frac1n$ 发散）。
5. **"在每个 $[a,b]\subset(0,1)$ 上一致收敛"不等于"在 $(0,1)$ 上一致收敛"。**
6. **一致收敛的必要条件**：$u_n\rightrightarrows0$，即 $\sup_D|u_n(x)|\to0$。用它可以快速否定一致收敛。

## 自测清单

1. 写出"$f_n$ 在 $D$ 上不一致收敛于 $f$"的完整表述。
2. $f_n(x)=\dfrac{x}{n}$ 在 $\mathbb R$ 上一致收敛于 $0$ 吗？在 $[0,1]$ 上呢？
3. $f_n(x)=\dfrac{\sin nx}{n}$ 在 $\mathbb R$ 上一致收敛吗？$f_n'$ 呢？
4. $\sum\limits_{n=1}^\infty x^n(1-x)$ 在 $[0,1]$ 上一致收敛吗？
5. 证明 $\sum\limits_{n=1}^\infty\dfrac{x}{n^2+x^2}$ 在 $[0,M]$ 上一致收敛。
6. 一致收敛的函数项级数一定绝对收敛吗？
7. Dini 定理中若 $f_n$ 递增是否仍成立？

## 参考答案

### 点击展开参考答案

1. $\exists\varepsilon_0>0,\ \forall N,\ \exists n>N,\ \exists x\in D:\ |f_n(x)-f(x)|\ge\varepsilon_0$。
2. 在 $\mathbb R$ 上不一致（$\sup_{\mathbb R}\frac{|x|}n=+\infty$）；在 $[0,1]$ 上一致（$\sup=\frac1n\to0$）。
3. $\sup|f_n|=\frac1n\to0$，一致收敛于 $0$。$f_n'=\cos nx$ 不收敛（例如在 $x=\pi$ 处为 $(-1)^n$），更谈不上一致收敛——这说明一致收敛不能保证可以逐项求导。
4. 部分和 $S_n(x)=x(1-x^n)$（$0\le x<1$），$S_n(1)=0$，和函数 $S(x)=x\ (x<1)$，$S(1)=0$，不连续，故不一致收敛。
5. 在 $[0,M]$ 上 $0\le\frac{x}{n^2+x^2}\le\frac{M}{n^2}$，M 判别法。
6. 不一定：$\sum\frac{(-1)^n}{n}$（常函数项）一致收敛但不绝对收敛。
7. 成立（对 $-f_n$ 用原定理）。关键是单调，方向无所谓。


