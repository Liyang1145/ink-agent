---
title: 幂级数与Fourier级数
subject: 数学分析
chapter: 12
prerequisites: 函数列与函数项级数、微分中值定理与Taylor公式
minutes: 90
---

# 第 12 章　幂级数与 Fourier 级数

## 为什么要学

上一章我们学会了判断"函数项级数什么时候能放心地逐项求极限、积分、求导"。这一章看两类最重要的函数项级数，它们代表两种完全不同的"把函数拆成简单零件"的思路：

- **幂级数** $\sum a_nx^n$：用 $1,x,x^2,\dots$ 当零件。它是"无穷次多项式"，是 Taylor 公式让 $n\to\infty$ 的结果。它擅长描述**光滑**函数，在收敛区间内部几乎可以当作多项式一样随意运算。
- **Fourier 级数** $\frac{a_0}2+\sum(a_n\cos nx+b_n\sin nx)$：用不同频率的正弦波当零件。它擅长描述**周期**现象，甚至能表示有跳跃的函数（比如方波）。声音、图像压缩、热传导都建立在它之上。

一个形象的比喻：Taylor 展开像是只在一点附近"拍特写"，把这一点的各阶导数信息铺开；Fourier 展开像是对整个周期"做频谱分析"，看每个频率的成分有多少。

## 12.1 幂级数的收敛半径

**定理 12.1（Abel 引理）** 若 $\sum a_nx^n$ 在 $x_0\ne0$ 处收敛，则它在 $|x|<|x_0|$ 内**绝对收敛**；若在 $x_1$ 处发散，则在 $|x|>|x_1|$ 处发散。

**证明**：收敛则 $a_nx_0^n\to0$，有界，设 $|a_nx_0^n|\le M$。对 $|x|<|x_0|$，
$$|a_nx^n|=|a_nx_0^n|\left|\frac x{x_0}\right|^n\le M\left|\frac{x}{x_0}\right|^n,$$
是收敛的几何级数。第二句是第一句的逆否。$\blacksquare$

由此，幂级数的收敛域一定是以 $0$ 为中心的区间（端点可能属于也可能不属于）。存在 $R\in[0,+\infty]$，在 $|x|<R$ 内绝对收敛，在 $|x|>R$ 处发散。$R$ 称为**收敛半径**。

**定理 12.2（Cauchy–Hadamard）**
$$R=\frac1{\varlimsup\limits_{n\to\infty}\sqrt[n]{|a_n|}}\quad(\text{约定 }\tfrac10=+\infty,\ \tfrac1{+\infty}=0).$$
若 $\lim\left|\dfrac{a_{n+1}}{a_n}\right|=\rho$ 存在，则 $R=\dfrac1\rho$。

（对 $\sum|a_nx^n|$ 用根值/比值判别法即得。）

**端点要单独讨论**——那里什么都可能发生：
- $\sum x^n$：$R=1$，两个端点都发散；
- $\sum\frac{x^n}n$：$R=1$，$x=-1$ 收敛、$x=1$ 发散；
- $\sum\frac{x^n}{n^2}$：$R=1$，两端都收敛。

## 12.2 幂级数的分析性质

**定理 12.3（内闭一致收敛）** 幂级数在 $(-R,R)$ 内的任一闭区间 $[-r,r]$（$r<R$）上一致收敛。

*证明*：在 $[-r,r]$ 上 $|a_nx^n|\le|a_n|r^n$，而 $\sum|a_n|r^n$ 收敛（$r$ 在收敛区间内绝对收敛），M 判别法。$\blacksquare$

结合上一章的三大定理，得到幂级数的"优良性质"：设 $S(x)=\sum a_nx^n$，收敛半径 $R>0$，则

1. $S$ 在 $(-R,R)$ 内连续；
2. **逐项积分**：$\displaystyle\int_0^xS(t)\,\mathrm dt=\sum_{n=0}^\infty\frac{a_n}{n+1}x^{n+1}$，$|x|<R$；
3. **逐项求导**：$S'(x)=\sum\limits_{n=1}^\infty na_nx^{n-1}$，$|x|<R$；
4. 逐项求导、积分后**收敛半径不变**（因为 $\sqrt[n]n\to1$），但端点处的敛散性可能改变；
5. 因而 $S$ 在 $(-R,R)$ 内**任意阶可导**，且 $a_n=\dfrac{S^{(n)}(0)}{n!}$——幂级数就是它和函数的 Taylor 级数（展开的唯一性）。

**定理 12.4（Abel 第二定理）** 若 $\sum a_nx^n$ 在 $x=R$ 处收敛，则它在 $[0,R]$ 上一致收敛，从而 $S$ 在 $x=R$ 处**左连续**：$\lim\limits_{x\to R^-}S(x)=\sum a_nR^n$。

（证明用 Abel 判别法的一致版本：$\sum a_nR^n$ 收敛，$\left(\frac xR\right)^n$ 对每个 $x$ 关于 $n$ 单调且一致有界。）

这个定理让我们能在端点处"算出"级数的和。例如由 $\ln(1+x)=\sum\frac{(-1)^{n-1}}nx^n$（$|x|<1$）及 $x=1$ 处级数收敛，得 $1-\frac12+\frac13-\cdots=\ln2$。

## 12.3 函数展开为幂级数

若 $f$ 在 $x_0$ 附近任意阶可导，可以形式地写出它的 **Taylor 级数** $\sum\frac{f^{(n)}(x_0)}{n!}(x-x_0)^n$。**但它不一定收敛到 $f$！**

经典反例：$f(x)=e^{-1/x^2}$（$x\ne0$），$f(0)=0$。可以证明 $f^{(n)}(0)=0$ 对所有 $n$ 成立，所以 Taylor 级数恒为 $0$，只在 $x=0$ 处等于 $f$。

**定理 12.5** $f$ 的 Taylor 级数在某区间内收敛到 $f$ $\iff$ 在该区间内 Taylor 余项 $R_n(x)\to0$。一个常用的充分条件是：各阶导数一致有界，$|f^{(n)}(x)|\le M$（由 Lagrange 余项 $|R_n|\le\frac{M|x-x_0|^{n+1}}{(n+1)!}\to0$）。

**基本展开式**：
$$e^x=\sum_{n=0}^\infty\frac{x^n}{n!}\ (x\in\mathbb R),\qquad\sin x=\sum_{n=0}^\infty\frac{(-1)^nx^{2n+1}}{(2n+1)!}\ (x\in\mathbb R),$$
$$\frac1{1-x}=\sum_{n=0}^\infty x^n\ (|x|<1),\qquad\ln(1+x)=\sum_{n=1}^\infty\frac{(-1)^{n-1}x^n}{n}\ (-1<x\le1),$$
$$\arctan x=\sum_{n=0}^\infty\frac{(-1)^nx^{2n+1}}{2n+1}\ (|x|\le1),\qquad(1+x)^\alpha=\sum_{n=0}^\infty\binom\alpha nx^n\ (|x|<1).$$

**间接展开法**（实战中最常用）：从已知展开出发，通过代换、四则运算、逐项求导/积分得到新的展开。例如 $\arctan x=\int_0^x\frac{\mathrm dt}{1+t^2}=\int_0^x\sum(-1)^nt^{2n}\,\mathrm dt$，逐项积分即得。

## 12.4 Fourier 级数

**三角函数系的正交性**：在 $[-\pi,\pi]$ 上，$1,\cos x,\sin x,\cos2x,\sin2x,\dots$ 中任意两个不同函数之积的积分为 $0$，且
$$\int_{-\pi}^\pi\cos^2nx\,\mathrm dx=\int_{-\pi}^\pi\sin^2nx\,\mathrm dx=\pi\ (n\ge1).$$

**想法**：如果 $f(x)=\frac{a_0}2+\sum(a_n\cos nx+b_n\sin nx)$ 并且可以逐项积分，两边乘以 $\cos mx$ 积分，由正交性只剩一项，就能"解出"系数。这和线性代数里用内积求正交基下的坐标是同一回事。

**定义 12.1** 设 $f$ 以 $2\pi$ 为周期，在 $[-\pi,\pi]$ 上可积（或绝对可积），其 **Fourier 系数**为
$$a_n=\frac1\pi\int_{-\pi}^\pi f(x)\cos nx\,\mathrm dx\ (n\ge0),\qquad b_n=\frac1\pi\int_{-\pi}^\pi f(x)\sin nx\,\mathrm dx\ (n\ge1),$$
称 $\dfrac{a_0}2+\sum\limits_{n=1}^\infty(a_n\cos nx+b_n\sin nx)$ 为 $f$ 的 **Fourier 级数**，记作 $f(x)\sim\cdots$。

用 "$\sim$" 而不是 "$=$"，因为级数是否收敛、收敛到什么，需要另外证明。

**奇偶性简化**：$f$ 为偶函数时 $b_n=0$，得余弦级数；$f$ 为奇函数时 $a_n=0$，得正弦级数。

**定理 12.6（Riemann–Lebesgue 引理）** 若 $f$ 在 $[-\pi,\pi]$ 上可积，则 $a_n\to0$，$b_n\to0$。

**定理 12.7（Dirichlet 收敛定理）** 设 $f$ 以 $2\pi$ 为周期，在 $[-\pi,\pi]$ 上**分段光滑**（至多有限个第一类间断点，且除有限个点外可导，导数分段连续、单侧极限存在），则 $f$ 的 Fourier 级数在每一点 $x$ 收敛于
$$\frac{f(x+0)+f(x-0)}2.$$
特别地，在 $f$ 的连续点收敛于 $f(x)$。

**想法**：部分和可以写成 $f$ 与 Dirichlet 核 $D_n(t)=\dfrac{\sin\left(n+\frac12\right)t}{2\sin\frac t2}$ 的卷积。$D_n$ 在 $t=0$ 附近形成越来越高、越来越窄的尖峰，其余地方高速振荡、在积分中相互抵消（Riemann–Lebesgue 引理）。于是部分和只"看见" $x$ 附近 $f$ 的左右值，各取一半。

**Parseval 等式**：若 $f$ 在 $[-\pi,\pi]$ 上平方可积（例如 Riemann 可积），则
$$\frac{a_0^2}2+\sum_{n=1}^\infty(a_n^2+b_n^2)=\frac1\pi\int_{-\pi}^\pi f^2(x)\,\mathrm dx.$$
可以理解为无穷维的勾股定理："能量"等于各频率成分能量之和。

**一般周期 $2l$**：作变量代换 $x=\frac{l}{\pi}t$，系数 $a_n=\dfrac1l\displaystyle\int_{-l}^lf(x)\cos\frac{n\pi x}l\,\mathrm dx$，$b_n$ 类似。

**定义在 $[0,\pi]$ 上的函数**：可以作**奇延拓**得正弦级数，或作**偶延拓**得余弦级数。

## 例题

**例 1（收敛域）** 求 $\sum\limits_{n=1}^\infty\dfrac{(x-1)^n}{n\cdot3^n}$ 的收敛域。

**解**：令 $t=x-1$，$\left|\dfrac{a_{n+1}}{a_n}\right|=\dfrac{n}{3(n+1)}\to\dfrac13$，$R=3$。端点 $t=3$：$\sum\frac1n$ 发散；$t=-3$：$\sum\frac{(-1)^n}n$ 收敛。所以 $-3\le x-1<3$，收敛域为 $[-2,4)$。

**例 2（求和函数）** 求 $\sum\limits_{n=1}^\infty nx^n$ 的和函数，并求 $\sum\limits_{n=1}^\infty\dfrac{n}{2^n}$。

**解**：$R=1$。对 $|x|<1$，$\sum_{n=0}^\infty x^n=\frac1{1-x}$，逐项求导得 $\sum_{n=1}^\infty nx^{n-1}=\frac1{(1-x)^2}$，乘以 $x$：
$$\sum_{n=1}^\infty nx^n=\frac{x}{(1-x)^2},\quad|x|<1.$$
端点 $x=\pm1$ 时通项不趋于 $0$，发散。取 $x=\frac12$ 得 $\sum\frac n{2^n}=2$。

> 求和函数的套路：**"先求导后积分"消去分母中的 $n$，"先积分后求导"消去分子中的 $n$**，目标是化为几何级数。

**例 3（展开）** 将 $f(x)=\dfrac1{x^2-3x+2}$ 展开为 $x$ 的幂级数。

**解**：$\dfrac1{(x-1)(x-2)}=\dfrac1{1-x}-\dfrac1{2-x}=\dfrac{1}{1-x}-\dfrac12\cdot\dfrac1{1-\frac x2}$。于是
$$f(x)=\sum_{n=0}^\infty\left(1-\frac1{2^{n+1}}\right)x^n,\quad|x|<1.$$
（两个级数收敛域的交集为 $|x|<1$。）

**例 4（Fourier 展开 + 求数项级数和）** 将 $f(x)=x^2$（$-\pi\le x\le\pi$）展开为 Fourier 级数，并求 $\sum\frac1{n^2}$ 与 $\sum\frac1{n^4}$。

**解**：$f$ 为偶函数，$b_n=0$。$a_0=\dfrac2\pi\displaystyle\int_0^\pi x^2\mathrm dx=\dfrac{2\pi^2}3$。$n\ge1$ 时分部积分两次：
$$a_n=\frac2\pi\int_0^\pi x^2\cos nx\,\mathrm dx=\frac{4(-1)^n}{n^2}.$$
$f$ 的周期延拓连续且分段光滑，由 Dirichlet 定理，对 $x\in[-\pi,\pi]$：
$$x^2=\frac{\pi^2}3+4\sum_{n=1}^\infty\frac{(-1)^n}{n^2}\cos nx.$$
令 $x=\pi$：$\pi^2=\dfrac{\pi^2}3+4\sum\dfrac1{n^2}$，所以 $\displaystyle\sum_{n=1}^\infty\frac1{n^2}=\frac{\pi^2}6$。

由 Parseval 等式：$\dfrac12\left(\dfrac{2\pi^2}3\right)^2+\sum\dfrac{16}{n^4}=\dfrac1\pi\displaystyle\int_{-\pi}^\pi x^4\mathrm dx=\dfrac{2\pi^4}5$，解得 $\displaystyle\sum_{n=1}^\infty\frac1{n^4}=\frac{\pi^4}{90}$。

**例 5（间断点处的收敛值）** 设 $f(x)=\begin{cases}-1,&-\pi<x<0\\1,&0\le x\le\pi\end{cases}$，以 $2\pi$ 为周期延拓。求其 Fourier 级数在 $x=0$ 和 $x=\pi$ 处的和。

**解**：两点都是跳跃间断点（延拓后 $f(\pi+0)=f(-\pi+0)=-1$，$f(\pi-0)=1$），由 Dirichlet 定理，和都是 $\dfrac{1+(-1)}2=0$——**与 $f$ 在该点的定义无关**。（系数：$f$ 除去间断点处的值后是奇函数，$b_n=\frac2\pi\int_0^\pi\sin nx\,\mathrm dx=\frac{2(1-(-1)^n)}{n\pi}$，展开为 $\frac4\pi\sum\frac{\sin(2k-1)x}{2k-1}$；令 $x=\frac\pi2$ 得 Leibniz 级数 $1-\frac13+\frac15-\cdots=\frac\pi4$。）

**例 6（CMC 风格：利用 ODE 求和）** 求 $S(x)=\sum\limits_{n=0}^\infty\dfrac{x^{2n}}{(2n)!!}$ 的和函数。

**解**：$(2n)!!=2^nn!$，所以 $S(x)=\sum\dfrac{(x^2/2)^n}{n!}=e^{x^2/2}$，$x\in\mathbb R$。

若看不出来，也可以用微分方程：$R=\infty$，逐项求导 $S'(x)=\sum_{n\ge1}\dfrac{2nx^{2n-1}}{(2n)!!}=x\sum_{n\ge1}\dfrac{x^{2n-2}}{(2n-2)!!}=xS(x)$，$S(0)=1$，解得 $S=e^{x^2/2}$。

> 当系数满足递推关系时，"逐项求导 → 得到 $S$ 满足的微分方程 → 解方程"是求和函数的有力方法，CMC 中屡见不鲜。

## 易错点

1. **收敛半径只告诉你开区间，端点必须单独讨论。**
2. **缺项幂级数**（如 $\sum a_nx^{2n}$）不能直接套 $\left|\frac{a_{n+1}}{a_n}\right|$ 的公式，要对整个通项用比值法，或令 $t=x^2$。
3. **逐项求导/积分后收敛半径不变，但端点的敛散性会变**，求和函数时最后要重新检查端点。
4. **Taylor 级数不一定收敛到原函数**（$e^{-1/x^2}$ 反例）。
5. **Fourier 级数在间断点收敛到左右极限的平均值**，不是函数值；周期延拓后，区间端点 $\pm\pi$ 也可能变成间断点。
6. **Fourier 系数公式中的 $\frac{a_0}2$**：写成 $\frac{a_0}{2}$ 是为了让 $a_0$ 和 $a_n$ 用同一个公式。别把常数项写成 $a_0$。

## 自测清单

1. 求 $\sum\dfrac{n!}{n^n}x^n$ 的收敛半径。
2. 求 $\sum\dfrac{x^{2n}}{4^n}$ 的收敛半径。
3. 写出 $\cos x$ 和 $\dfrac1{1+x^2}$ 的 Maclaurin 级数及收敛域。
4. 求 $\sum\limits_{n=1}^\infty\dfrac{x^n}{n}$ 的和函数（含收敛域）。
5. $f(x)=x$（$-\pi<x\le\pi$）的 Fourier 级数在 $x=\pi$ 处的和是多少？
6. 为什么 $e^{-1/x^2}$ 的 Maclaurin 级数不收敛到它本身（$x\ne0$）？
7. 用 $\sum\frac1{n^2}=\frac{\pi^2}6$ 求 $\sum\frac1{(2n-1)^2}$。

## 参考答案

### 点击展开参考答案

1. $\left|\frac{a_{n+1}}{a_n}\right|=\left(\frac n{n+1}\right)^n\to\frac1e$，$R=e$。
2. 令 $t=x^2$，$\sum\frac{t^n}{4^n}$ 的半径为 $4$，所以 $|x|<2$，$R=2$。
3. $\cos x=\sum\frac{(-1)^nx^{2n}}{(2n)!}$，$x\in\mathbb R$；$\frac1{1+x^2}=\sum(-1)^nx^{2n}$，$|x|<1$。
4. 逐项求导得 $\frac1{1-x}$，积分得 $-\ln(1-x)$，收敛域 $[-1,1)$（$x=-1$ 由 Abel 第二定理得和为 $-\ln2$）。
5. 周期延拓后 $f(\pi-0)=\pi$，$f(\pi+0)=-\pi$，和为 $0$。
6. 它的各阶导数在 $0$ 处都为 $0$，Maclaurin 级数恒为 $0$，而 $f(x)>0$（$x\ne0$）。
7. $\sum\frac1{(2n)^2}=\frac14\cdot\frac{\pi^2}6$，所以奇数项之和为 $\frac{\pi^2}6-\frac{\pi^2}{24}=\frac{\pi^2}8$。


