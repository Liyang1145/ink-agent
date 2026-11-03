---
title: 定积分
subject: 数学分析
chapter: 8
prerequisites: 不定积分、函数的连续性
minutes: 75
---

# 第 8 章　定积分

## 为什么要学

怎样求曲线 $y=f(x)$ 下方、$[a,b]$ 上方那块区域的面积？阿基米德早就有了办法：**切成细条，每条近似成矩形，加起来，再让细条无限变窄。** 这就是定积分的全部思想——"分割、近似、求和、取极限"。

同样的四步可以求曲线长度、旋转体体积、变力做功、质心……几乎所有"累积量"都能写成定积分。

这一章最惊人的结论是 **微积分基本定理**：求面积（一个极限过程）和求原函数（求导的逆运算）竟然是同一件事。Newton 和 Leibniz 发现了这座桥，从此面积问题变成了上一章的计算技巧。

但作为数学专业的学生，我们还要问一个更根本的问题：**什么样的函数可以积分？** 这需要比"切细条"更精确的定义，以及上下和的工具。

## 8.1 Riemann 积分的定义

把 $[a,b]$ 分割为 $P:a=x_0<x_1<\cdots<x_n=b$，记 $\Delta x_i=x_i-x_{i-1}$，**模** $\|P\|=\max\Delta x_i$。在每个小区间任取 $\xi_i\in[x_{i-1},x_i]$，作 **Riemann 和**
$$S(f,P,\xi)=\sum_{i=1}^nf(\xi_i)\Delta x_i.$$

**定义 8.1** 若存在 $I\in\mathbb R$，使
$$\forall\varepsilon>0,\ \exists\delta>0,\ \forall P\ (\|P\|<\delta),\ \forall\xi:\ |S(f,P,\xi)-I|<\varepsilon,$$
则称 $f$ 在 $[a,b]$ 上 **Riemann 可积**，记 $I=\displaystyle\int_a^bf(x)\,\mathrm dx$。

注意：要对**所有**分割、**所有**取点方式都成立。这是一个比数列极限复杂得多的极限。

**必要条件**：可积函数必有界。（若无界，在某个小区间上可以选 $\xi_i$ 使 $f(\xi_i)\Delta x_i$ 任意大，Riemann 和无法收敛。）

## 8.2 Darboux 上和、下和与可积准则

设 $f$ 有界，记 $M_i=\sup\limits_{[x_{i-1},x_i]}f$，$m_i=\inf\limits_{[x_{i-1},x_i]}f$，**振幅** $\omega_i=M_i-m_i$。
$$U(f,P)=\sum M_i\Delta x_i,\qquad L(f,P)=\sum m_i\Delta x_i.$$

**直觉**：$U$ 是"外接矩形"面积之和，$L$ 是"内接矩形"面积之和，任何 Riemann 和都夹在两者之间。

**性质**：
1. 加细分割，上和不增、下和不减；
2. 任一下和 $\le$ 任一上和（取两个分割的公共加细）。

**定理 8.1（可积准则）** 有界函数 $f$ 在 $[a,b]$ 上可积，当且仅当
$$\forall\varepsilon>0,\ \exists P:\ U(f,P)-L(f,P)=\sum_{i=1}^n\omega_i\Delta x_i<\varepsilon.$$

**想法**：可积意味着外接面积和内接面积可以任意接近。"$\sum\omega_i\Delta x_i$ 小"可以理解为：**振幅大的小区间，总长度必须小。**

（完整证明需要说明"存在某个分割满足"可以推出"所有足够细的分割都满足"，这是 Darboux 定理的内容，这里略去。）

**定理 8.2（可积函数类）** 以下函数在 $[a,b]$ 上可积：
1. 连续函数；
2. 只有有限个间断点的有界函数；
3. 单调函数。

**证明 1**：由 Cantor 定理 $f$ 一致连续。任给 $\varepsilon>0$，存在 $\delta$ 使 $|x'-x''|<\delta$ 时 $|f(x')-f(x'')|<\frac\varepsilon{b-a}$。取 $\|P\|<\delta$，则每个 $\omega_i\le\frac\varepsilon{b-a}$（由最值定理，$\omega_i$ 在小区间上取到，是两点函数值之差），于是 $\sum\omega_i\Delta x_i\le\varepsilon$。$\blacksquare$

> 这里一致连续是关键：一个 $\delta$ 管住所有小区间。只有逐点连续是不够的。

**证明 3**：设 $f$ 递增，取等分分割，$\Delta x_i=\frac{b-a}n$，则
$$\sum\omega_i\Delta x_i=\frac{b-a}n\sum_{i=1}^n\big(f(x_i)-f(x_{i-1})\big)=\frac{(b-a)(f(b)-f(a))}{n}\to0.\qquad\blacksquare$$

**例 1** Dirichlet 函数 $D(x)$ 在 $[0,1]$ 上不可积：每个小区间上 $\omega_i=1$，$\sum\omega_i\Delta x_i=1$。Riemann 函数（第 4 章例 1）在 $[0,1]$ 上可积且积分为 $0$（间断点虽然稠密，但"振幅大于 $\varepsilon$ 的点"只有有限个）。

## 8.3 定积分的性质

- **线性**；**区间可加性** $\int_a^b=\int_a^c+\int_c^b$；
- **保序性**：$f\le g\Rightarrow\int_a^bf\le\int_a^bg$；特别地 $\left|\int_a^bf\right|\le\int_a^b|f|$；
- 若 $f$ 连续、$f\ge0$ 且 $f\not\equiv0$，则 $\int_a^bf>0$；
- 可积函数的乘积、绝对值仍可积。

**定理 8.3（积分第一中值定理）** 若 $f\in C[a,b]$，$g$ 可积且不变号，则存在 $\xi\in[a,b]$ 使
$$\int_a^bf(x)g(x)\,\mathrm dx=f(\xi)\int_a^bg(x)\,\mathrm dx.$$
特别地（$g\equiv1$），$\int_a^bf=f(\xi)(b-a)$：**连续函数在区间上的平均值能被取到**。

*证明*：设 $g\ge0$，$m\le f\le M$，则 $m\int g\le\int fg\le M\int g$。若 $\int g=0$，则 $\int fg=0$，任取 $\xi$；否则 $\frac{\int fg}{\int g}\in[m,M]$，由介值定理得 $\xi$。$\blacksquare$

## 8.4 微积分基本定理

**定理 8.4（变上限积分）** 若 $f$ 在 $[a,b]$ 上可积，令 $\Phi(x)=\displaystyle\int_a^xf(t)\,\mathrm dt$，则
1. $\Phi$ 在 $[a,b]$ 上连续（甚至 Lipschitz）；
2. 若 $f$ 在 $x_0$ 连续，则 $\Phi$ 在 $x_0$ 可导，且 $\Phi'(x_0)=f(x_0)$。

**想法**：$\Phi(x_0+h)-\Phi(x_0)$ 是一条宽 $h$ 的细条面积，约等于 $f(x_0)\cdot h$。

**证明（2）**：设 $h\ne0$，
$$\left|\frac{\Phi(x_0+h)-\Phi(x_0)}h-f(x_0)\right|=\left|\frac1h\int_{x_0}^{x_0+h}\big(f(t)-f(x_0)\big)\mathrm dt\right|.$$
任给 $\varepsilon>0$，由 $f$ 在 $x_0$ 连续，存在 $\delta$ 使 $|t-x_0|<\delta$ 时 $|f(t)-f(x_0)|<\varepsilon$。于是 $0<|h|<\delta$ 时上式 $\le\varepsilon$。$\blacksquare$

**推论**：连续函数必有原函数，$\Phi$ 就是一个。

**定理 8.5（Newton–Leibniz 公式）** 若 $f$ 在 $[a,b]$ 上可积，$F$ 在 $[a,b]$ 上连续、在 $(a,b)$ 内 $F'=f$，则
$$\int_a^bf(x)\,\mathrm dx=F(b)-F(a).$$

**证明**（不需要 $f$ 连续！）：对任意分割 $P$，在每个小区间上对 $F$ 用 Lagrange 中值定理：$F(x_i)-F(x_{i-1})=f(\eta_i)\Delta x_i$。于是
$$F(b)-F(a)=\sum_{i=1}^n\big(F(x_i)-F(x_{i-1})\big)=\sum_{i=1}^nf(\eta_i)\Delta x_i,$$
这是一个 Riemann 和。由 $f$ 可积，令 $\|P\|\to0$，右边趋于 $\int_a^bf$，而左边是常数。$\blacksquare$

**变限积分求导**（链式法则）：
$$\frac{\mathrm d}{\mathrm dx}\int_{\psi(x)}^{\varphi(x)}f(t)\,\mathrm dt=f(\varphi(x))\varphi'(x)-f(\psi(x))\psi'(x).$$

## 8.5 定积分的计算技巧

**换元**：$\displaystyle\int_a^bf(x)\,\mathrm dx=\int_\alpha^\beta f(\varphi(t))\varphi'(t)\,\mathrm dt$，其中 $\varphi(\alpha)=a$，$\varphi(\beta)=b$。**换元必换限**，且不需要换回原变量。

**分部**：$\displaystyle\int_a^bu\,\mathrm dv=\big[uv\big]_a^b-\int_a^bv\,\mathrm du$。

**对称性**：
- $f$ 奇函数：$\int_{-a}^af=0$；偶函数：$\int_{-a}^af=2\int_0^af$；
- 周期 $T$：$\int_a^{a+T}f=\int_0^Tf$；
- **区间再现**：$\int_a^bf(x)\,\mathrm dx=\int_a^bf(a+b-x)\,\mathrm dx$。

**Wallis 公式**：
$$\int_0^{\pi/2}\sin^nx\,\mathrm dx=\int_0^{\pi/2}\cos^nx\,\mathrm dx=\begin{cases}\dfrac{(n-1)!!}{n!!}\cdot\dfrac\pi2,&n\text{ 为偶数}\\\dfrac{(n-1)!!}{n!!},&n\text{ 为奇数}\end{cases}$$
（由分部积分得递推 $I_n=\frac{n-1}nI_{n-2}$。）

**几何应用**（简述）：
- 面积 $A=\int_a^b|f(x)-g(x)|\,\mathrm dx$；极坐标 $A=\frac12\int_\alpha^\beta r^2(\theta)\,\mathrm d\theta$；
- 旋转体体积（绕 $x$ 轴）$V=\pi\int_a^bf^2(x)\,\mathrm dx$；
- 弧长 $s=\int_a^b\sqrt{1+f'^2(x)}\,\mathrm dx$，参数式 $s=\int_\alpha^\beta\sqrt{x'^2(t)+y'^2(t)}\,\mathrm dt$。

## 例题

**例 2（和式极限化为积分）** 求 $\displaystyle\lim_{n\to\infty}\sum_{k=1}^n\frac{n}{n^2+k^2}$。

**解**：$\dfrac n{n^2+k^2}=\dfrac1n\cdot\dfrac1{1+(k/n)^2}$，这是 $f(x)=\frac1{1+x^2}$ 在 $[0,1]$ 上等分、取右端点的 Riemann 和。$f$ 连续可积，故极限为 $\int_0^1\frac{\mathrm dx}{1+x^2}=\frac\pi4$。

> 识别方法：把和式写成 $\frac1n\sum f\left(\frac kn\right)$ 的形式。

**例 3（区间再现）** 求 $\displaystyle I=\int_0^\pi\frac{x\sin x}{1+\cos^2x}\mathrm dx$。

**解**：令 $x=\pi-t$：$I=\displaystyle\int_0^\pi\frac{(\pi-t)\sin t}{1+\cos^2t}\mathrm dt=\pi\int_0^\pi\frac{\sin t}{1+\cos^2t}\mathrm dt-I$。所以
$$I=\frac\pi2\int_0^\pi\frac{\sin t}{1+\cos^2t}\mathrm dt=\frac\pi2\Big[-\arctan(\cos t)\Big]_0^\pi=\frac\pi2\cdot\frac\pi2=\frac{\pi^2}4.$$

**例 4（变限积分 + 极限）** 求 $\displaystyle\lim_{x\to0}\frac{\int_0^{x^2}\sin\sqrt t\,\mathrm dt}{x^3}$（$x>0$）。

**解**：$\frac00$ 型，用 L'Hospital 法则，分子求导得 $\sin\sqrt{x^2}\cdot2x=2x\sin x$（$x>0$）：
$$\lim_{x\to0^+}\frac{2x\sin x}{3x^2}=\frac23.$$

**例 5（CMC 风格：积分不等式）** 设 $f$ 在 $[0,1]$ 上连续可导，$f(0)=0$。证明
$$\int_0^1f^2(x)\,\mathrm dx\le\frac12\int_0^1f'^2(x)\,\mathrm dx.$$

**想法**：用 Newton–Leibniz 把 $f$ 写成 $f'$ 的积分，再用 Cauchy–Schwarz 不等式。

**证明**：$f(x)=\int_0^xf'(t)\,\mathrm dt$。由 Cauchy–Schwarz 不等式 $\left(\int_0^x1\cdot f'\right)^2\le\int_0^x1^2\cdot\int_0^xf'^2$：
$$f^2(x)\le x\int_0^xf'^2(t)\,\mathrm dt\le x\int_0^1f'^2(t)\,\mathrm dt.$$
两边在 $[0,1]$ 上积分，$\int_0^1x\,\mathrm dx=\frac12$，即得。$\blacksquare$

> **积分形式的 Cauchy–Schwarz 不等式** $\left(\int_a^bfg\right)^2\le\int_a^bf^2\int_a^bg^2$ 是 CMC 积分不等式题的头号武器。证明：对任意实数 $\lambda$，$\int(f+\lambda g)^2\ge0$，展开为 $\lambda$ 的二次式，判别式 $\le0$。

**例 6（CMC 风格：Riemann–Lebesgue 型）** 设 $f$ 在 $[a,b]$ 上连续可导，证明 $\displaystyle\lim_{n\to\infty}\int_a^bf(x)\sin nx\,\mathrm dx=0$。

**证明**：分部积分：
$$\int_a^bf(x)\sin nx\,\mathrm dx=\left[-\frac{f(x)\cos nx}n\right]_a^b+\frac1n\int_a^bf'(x)\cos nx\,\mathrm dx.$$
设 $|f|\le M$，$|f'|\le M_1$，则绝对值 $\le\dfrac{2M+M_1(b-a)}{n}\to0$。$\blacksquare$

（结论对任意可积函数也成立，证明需要用阶梯函数逼近，第 12 章 Fourier 级数会用到。）

## 易错点

1. **可积 ≠ 连续 ≠ 有原函数。** 有跳跃间断点的函数可积但没有原函数；有原函数的函数也可能不可积（导函数可以无界）。
2. **换元必换限**，且新变量的取值范围要和代换函数匹配。
3. **Newton–Leibniz 公式要求 $F$ 在整个闭区间上连续。** 例如 $\int_{-1}^1\frac{\mathrm dx}{x^2}$ 不能套公式得 $-2$——被积函数在 $0$ 处无界，这是反常积分（第 9 章），而且发散。
4. **变上限积分求导时别忘了内层导数**，且被积函数里若含 $x$，要先提出或换元再求导。例如 $\frac{\mathrm d}{\mathrm dx}\int_0^xxf(t)\,\mathrm dt=\int_0^xf(t)\,\mathrm dt+xf(x)$。
5. **"$\int f>0$"需要连续且非零**；仅可积、非负且某点为正不够（可在有限个点非零而积分为零）。

## 自测清单

1. 写出 Riemann 可积的 $\varepsilon$-$\delta$ 定义。
2. Dirichlet 函数为什么不可积？
3. 求 $\dfrac{\mathrm d}{\mathrm dx}\displaystyle\int_x^{x^2}e^{t^2}\mathrm dt$。
4. 计算 $\displaystyle\int_0^{\pi/2}\sin^5x\,\mathrm dx$ 和 $\displaystyle\int_0^{\pi/2}\cos^4x\,\mathrm dx$。
5. 求 $\displaystyle\lim_{n\to\infty}\frac1n\sum_{k=1}^n\sqrt{1+\frac kn}$。
6. 若 $f$ 在 $[a,b]$ 上可积，$|f|$ 一定可积吗？反过来呢？
7. 求 $\displaystyle\int_{-1}^1\frac{x^3\cos x+1}{1+x^2}\mathrm dx$。

## 参考答案

### 点击展开参考答案

1. 存在 $I$，$\forall\varepsilon>0,\exists\delta>0$，对任何 $\|P\|<\delta$ 的分割和任何取点 $\xi$，$|S(f,P,\xi)-I|<\varepsilon$。
2. 任何小区间上振幅为 $1$，上和恒为 $1$、下和恒为 $0$，可积准则不满足。
3. $2xe^{x^4}-e^{x^2}$。
4. $\frac{4!!}{5!!}=\frac{8}{15}$；$\frac{3!!}{4!!}\cdot\frac\pi2=\frac{3\pi}{16}$。
5. $\int_0^1\sqrt{1+x}\,\mathrm dx=\frac23(2\sqrt2-1)$。
6. 一定（因为 $\big||f(x)|-|f(y)|\big|\le|f(x)-f(y)|$，振幅不增）。反过来不一定：$f=2D-1$（有理点 $1$、无理点 $-1$）不可积，但 $|f|\equiv1$ 可积。
7. 奇函数部分积分为 $0$，剩下 $\int_{-1}^1\frac{\mathrm dx}{1+x^2}=\frac\pi2$。


