---
title: 反常积分
subject: 数学分析
chapter: 9
prerequisites: 定积分
minutes: 60
---

# 第 9 章　反常积分

## 为什么要学

Riemann 积分有两个先天限制：**区间有限、函数有界**。可很多重要的量偏偏突破了这两个限制：

- 把火箭从地面送到"无穷远"所做的功：$\displaystyle\int_R^{+\infty}\frac{GMm}{r^2}\,\mathrm dr$；
- 概率论里的正态分布：$\displaystyle\int_{-\infty}^{+\infty}e^{-x^2/2}\,\mathrm dx$；
- $\displaystyle\int_0^1\frac{\mathrm dx}{\sqrt x}$——被积函数在 $0$ 附近无界。

思路很自然：**先在"安全"的范围内积分，再取极限。** 一条无限长的区域，面积可能有限（就像 $1+\frac12+\frac14+\cdots=2$）；也可能无限。判断"有限还是无限"，就是这一章的核心问题——这和下一章的级数敛散性几乎是同一个故事，学完这章，级数会非常好懂。

## 9.1 定义

**无穷区间上的反常积分**：设 $f$ 在每个 $[a,A]$ 上可积，定义
$$\int_a^{+\infty}f(x)\,\mathrm dx=\lim_{A\to+\infty}\int_a^Af(x)\,\mathrm dx.$$
极限存在（有限）称为**收敛**，否则**发散**。

对 $\int_{-\infty}^{+\infty}$，要求**分别**在 $(-\infty,c]$ 和 $[c,+\infty)$ 上收敛：
$$\int_{-\infty}^{+\infty}f=\lim_{A'\to-\infty}\int_{A'}^cf+\lim_{A\to+\infty}\int_c^Af,$$
**两个极限独立**，不能写成 $\lim\limits_{A\to+\infty}\int_{-A}^A$（后者叫 Cauchy 主值，例如 $\int_{-A}^Ax\,\mathrm dx=0$，但 $\int_{-\infty}^{+\infty}x\,\mathrm dx$ 发散）。

**无界函数的反常积分（瑕积分）**：若 $f$ 在 $a$ 附近无界（$a$ 称为**瑕点**），在每个 $[a+\eta,b]$ 上可积，定义
$$\int_a^bf(x)\,\mathrm dx=\lim_{\eta\to0^+}\int_{a+\eta}^bf(x)\,\mathrm dx.$$
瑕点在内部时，同样要拆开分别讨论。

**两个基准积分**（以后所有比较都以它们为尺子）：
$$\int_1^{+\infty}\frac{\mathrm dx}{x^p}\ \begin{cases}\text{收敛},&p>1\\\text{发散},&p\le1\end{cases}\qquad\int_0^1\frac{\mathrm dx}{x^p}\ \begin{cases}\text{收敛},&p<1\\\text{发散},&p\ge1\end{cases}$$

*验证*：$p\ne1$ 时 $\int_1^A x^{-p}\mathrm dx=\dfrac{A^{1-p}-1}{1-p}$，$A\to+\infty$ 时有限当且仅当 $p>1$；$p=1$ 时得 $\ln A\to+\infty$。瑕积分同理。

> 记忆窍门：在无穷远处，函数要衰减**得比 $\frac1x$ 快**才收敛；在瑕点 $0$ 处，函数要爆炸**得比 $\frac1x$ 慢**才收敛。$\frac1x$ 是两边都发散的临界情形。

## 9.2 Cauchy 准则

**定理 9.1** $\int_a^{+\infty}f$ 收敛 $\iff\forall\varepsilon>0,\exists A_0,\forall A'',A'>A_0:\ \left|\int_{A'}^{A''}f(x)\,\mathrm dx\right|<\varepsilon$。

这是函数极限 Cauchy 准则的直接翻译："尾巴上任意一段的积分都很小"。

**绝对收敛与条件收敛**：若 $\int_a^{+\infty}|f|$ 收敛，称 $\int_a^{+\infty}f$ **绝对收敛**。

**定理 9.2** 绝对收敛 $\Rightarrow$ 收敛。

*证明*：$\left|\int_{A'}^{A''}f\right|\le\int_{A'}^{A''}|f|$，用 Cauchy 准则。$\blacksquare$

收敛但不绝对收敛的称为**条件收敛**。

## 9.3 非负函数的比较判别法

对 $f\ge0$，$F(A)=\int_a^Af$ 关于 $A$ 单调递增，所以**收敛 $\iff F(A)$ 有界**（单调有界原理！）。由此：

**定理 9.3（比较判别法）** 设 $0\le f(x)\le g(x)$（$x\ge a$），则 $\int_a^{+\infty}g$ 收敛 $\Rightarrow\int_a^{+\infty}f$ 收敛；$\int_a^{+\infty}f$ 发散 $\Rightarrow\int_a^{+\infty}g$ 发散。

**极限形式**：若 $f,g>0$ 且 $\lim\limits_{x\to+\infty}\dfrac{f(x)}{g(x)}=l$，
- $0<l<+\infty$：两者同敛散；
- $l=0$：$\int g$ 收敛 $\Rightarrow\int f$ 收敛；
- $l=+\infty$：$\int g$ 发散 $\Rightarrow\int f$ 发散。

最常用的是取 $g=\frac1{x^p}$，即看 $f$ 是 $\frac1x$ 的"几阶无穷小"。

**例 1** 判断 $\displaystyle\int_1^{+\infty}\frac{\ln x}{x^2}\mathrm dx$ 和 $\displaystyle\int_0^1\frac{\mathrm dx}{\sqrt{x}(1+x)}$、$\displaystyle\int_0^1\frac{\ln x}{\sqrt x}\mathrm dx$ 的敛散性。

**解**：
- $\dfrac{\ln x}{x^2}\Big/\dfrac1{x^{3/2}}=\dfrac{\ln x}{\sqrt x}\to0$，而 $\int_1^{+\infty}x^{-3/2}$ 收敛，故收敛。（技巧：$\ln x$ 比任何正次幂增长慢，可以"吃掉"一点点幂次。）
- $x\to0^+$ 时 $\dfrac1{\sqrt x(1+x)}\sim\dfrac1{\sqrt x}$，$p=\frac12<1$，收敛。
- $\left|\dfrac{\ln x}{\sqrt x}\right|\Big/\dfrac1{x^{3/4}}=x^{1/4}|\ln x|\to0$，而 $\int_0^1x^{-3/4}$ 收敛，故绝对收敛。

## 9.4 一般函数：Dirichlet 与 Abel 判别法

当 $f$ 变号时，可能"正负抵消"而收敛，比较判别法用不上。典型例子是 $\displaystyle\int_1^{+\infty}\frac{\sin x}x\mathrm dx$。

**定理 9.4（Dirichlet 判别法）** 若
1. $F(A)=\int_a^Af(x)\,\mathrm dx$ 在 $[a,+\infty)$ 上有界；
2. $g$ 在 $[a,+\infty)$ 上单调，且 $\lim\limits_{x\to+\infty}g(x)=0$，

则 $\int_a^{+\infty}f(x)g(x)\,\mathrm dx$ 收敛。

**定理 9.5（Abel 判别法）** 若 $\int_a^{+\infty}f$ 收敛，$g$ 单调有界，则 $\int_a^{+\infty}fg$ 收敛。

**Dirichlet 判别法的想法**：$f$ 的积分"来回摆动但不跑远"，$g$ 单调地把摆幅压到零。严格证明用积分第二中值定理：若 $g$ 单调，则存在 $\xi\in[A',A'']$ 使
$$\int_{A'}^{A''}fg=g(A')\int_{A'}^\xi f+g(A'')\int_\xi^{A''}f.$$
若 $|F|\le M$，则任一段 $|\int f|\le2M$，于是 $\left|\int_{A'}^{A''}fg\right|\le2M(|g(A')|+|g(A'')|)$，当 $A',A''$ 充分大时可以任意小，由 Cauchy 准则得证。

（若 $g$ 还连续可导，也可以用分部积分代替第二中值定理，见例 2。）

**例 2** 证明 $\displaystyle\int_1^{+\infty}\frac{\sin x}x\mathrm dx$ 条件收敛。

**收敛**：$\left|\int_1^A\sin x\,\mathrm dx\right|=|\cos1-\cos A|\le2$，$\frac1x$ 单调趋于 $0$，由 Dirichlet 判别法收敛。

（也可直接分部积分：$\int_1^A\frac{\sin x}x\mathrm dx=\left[-\frac{\cos x}x\right]_1^A-\int_1^A\frac{\cos x}{x^2}\mathrm dx$，后者绝对收敛。）

**不绝对收敛**：$\dfrac{|\sin x|}x\ge\dfrac{\sin^2x}x=\dfrac1{2x}-\dfrac{\cos2x}{2x}$。$\int_1^{+\infty}\frac{\cos2x}{2x}$ 收敛（Dirichlet），$\int_1^{+\infty}\frac1{2x}$ 发散，所以右边积分发散，由比较判别法 $\int_1^{+\infty}\frac{|\sin x|}x$ 发散。$\blacksquare$

> "$|\sin x|\ge\sin^2x$" 这个放缩是证明"不绝对收敛"的经典套路。

事实上 $\displaystyle\int_0^{+\infty}\frac{\sin x}x\mathrm dx=\frac\pi2$（Dirichlet 积分，$0$ 不是瑕点，因为 $\frac{\sin x}x\to1$），计算需要含参积分或二重积分。

## 9.5 Γ 函数与 B 函数（简介）

$$\Gamma(s)=\int_0^{+\infty}x^{s-1}e^{-x}\,\mathrm dx\quad(s>0),\qquad B(p,q)=\int_0^1x^{p-1}(1-x)^{q-1}\,\mathrm dx\quad(p,q>0).$$

- $\Gamma(s)$ 收敛性：在 $0$ 附近 $\sim x^{s-1}$，需要 $s-1>-1$；在 $+\infty$ 处 $e^{-x}$ 压倒一切幂函数。
- $\Gamma(s+1)=s\Gamma(s)$（分部积分），$\Gamma(n+1)=n!$，$\Gamma\left(\frac12\right)=\sqrt\pi$。
- $B(p,q)=\dfrac{\Gamma(p)\Gamma(q)}{\Gamma(p+q)}$。

它们把一大批积分统一成可以查表的形式，在 CMC 计算题中很省力。

## 例题

**例 3（瑕点与无穷远并存）** 讨论 $\displaystyle\int_0^{+\infty}\frac{x^{p-1}}{1+x}\mathrm dx$ 的敛散性。

**解**：拆成 $\int_0^1+\int_1^{+\infty}$ 分别讨论。
- $x\to0^+$：$\dfrac{x^{p-1}}{1+x}\sim x^{p-1}=\dfrac1{x^{1-p}}$，收敛 $\iff1-p<1\iff p>0$。
- $x\to+\infty$：$\dfrac{x^{p-1}}{1+x}\sim\dfrac1{x^{2-p}}$，收敛 $\iff2-p>1\iff p<1$。

所以收敛 $\iff0<p<1$。（其值为 $\dfrac\pi{\sin p\pi}$，此处不证。）

**例 4（计算）** 求 $\displaystyle\int_0^{+\infty}\frac{\mathrm dx}{(1+x^2)^2}$。

**解**：令 $x=\tan t$，$\mathrm dx=\sec^2t\,\mathrm dt$，$t\in\left[0,\frac\pi2\right)$：
$$\int_0^{\pi/2}\frac{\sec^2t}{\sec^4t}\mathrm dt=\int_0^{\pi/2}\cos^2t\,\mathrm dt=\frac\pi4.$$
（反常积分换元后变成了常义积分，这是常见现象。）

**例 5（CMC 风格：收敛不蕴含趋于零）** 构造 $f\ge0$ 连续，使 $\int_0^{+\infty}f$ 收敛，但 $\lim\limits_{x\to+\infty}f(x)$ 不存在。又证明：若 $\int_a^{+\infty}f$ 收敛且 $f$ 在 $[a,+\infty)$ 上一致连续，则 $f(x)\to0$。

**构造**：在每个整数 $n\ge2$ 附近放一个高为 $1$、底宽为 $\frac2{n^2}$ 的三角形"尖峰"（以 $n$ 为中心），其余地方为 $0$。每个尖峰面积 $\frac1{n^2}$，总面积 $\sum\frac1{n^2}<\infty$，所以积分收敛；但 $f(n)=1$ 而尖峰之间 $f=0$，极限不存在。

**证明**：反设 $f\not\to0$，则有 $\varepsilon_0>0$ 及 $x_n\to+\infty$ 使 $|f(x_n)|\ge\varepsilon_0$。由一致连续，存在 $\delta>0$ 使 $|x-y|\le\delta$ 时 $|f(x)-f(y)|<\frac{\varepsilon_0}2$。于是在 $[x_n,x_n+\delta]$ 上 $|f|>\frac{\varepsilon_0}2$，且 $f$ 连续不变号（若变号则有零点，与 $|f|>\frac{\varepsilon_0}{2}$ 矛盾），因此
$$\left|\int_{x_n}^{x_n+\delta}f\right|\ge\frac{\varepsilon_0\delta}2.$$
这与 Cauchy 准则（尾部任意一段积分可以任意小）矛盾。$\blacksquare$

> 和级数对比：级数收敛必有通项 $\to0$，但反常积分收敛**不**保证被积函数 $\to0$。原因是函数可以用"越来越窄的尖峰"作弊。加上一致连续（或单调）就能堵住这个漏洞。

## 易错点

1. **$\int_{-\infty}^{+\infty}$ 必须拆成两个独立的极限**，不能用对称极限（主值）。
2. **瑕点要仔细找**，包括积分区间内部的点。$\int_{-1}^1\frac{\mathrm dx}{x}$ 发散，不是 $0$。
3. **比较判别法只用于非负函数**（或用于绝对值）。对变号函数用比较判别法得出"收敛"，只能是绝对收敛的结论。
4. **$\frac{\sin x}x$ 在 $0$ 处不是瑕点**——它有极限 $1$，补充定义后连续。判断瑕点要看函数是否真的无界。
5. **反常积分收敛不能推出被积函数趋于 $0$**（例 5）。
6. **同时有多个"问题点"时**，每一个都要单独讨论，整体收敛当且仅当每一段都收敛。

## 自测清单

1. 判断 $\displaystyle\int_2^{+\infty}\frac{\mathrm dx}{x\ln x}$ 与 $\displaystyle\int_2^{+\infty}\frac{\mathrm dx}{x\ln^2x}$ 的敛散性。
2. $\displaystyle\int_0^1\frac{\mathrm dx}{\sqrt{1-x^2}}$ 收敛吗？值是多少？
3. 求 $\Gamma\left(\frac52\right)$。
4. $\displaystyle\int_0^{+\infty}e^{-x^2}\,\mathrm dx$ 收敛吗？用什么比较？
5. $\displaystyle\int_1^{+\infty}\frac{\cos x}{x^p}\mathrm dx$ 在 $p$ 取何值时绝对收敛、条件收敛？
6. 为什么 $\int_{-\infty}^{+\infty}\frac{x}{1+x^2}\mathrm dx$ 发散，尽管被积函数是奇函数？
7. 判断 $\displaystyle\int_0^1\frac{\sin x}{x^{3/2}}\mathrm dx$ 的敛散性。

## 参考答案

### 点击展开参考答案

1. 第一个：原函数 $\ln\ln x\to+\infty$，发散。第二个：原函数 $-\frac1{\ln x}\to0$，收敛，值为 $\frac1{\ln2}$。
2. 收敛（瑕点 $1$ 处 $\sim\frac1{\sqrt2\sqrt{1-x}}$，$p=\frac12$），值为 $\frac\pi2$。
3. $\Gamma\left(\frac52\right)=\frac32\cdot\frac12\Gamma\left(\frac12\right)=\frac{3\sqrt\pi}4$。
4. 收敛：$x\ge1$ 时 $e^{-x^2}\le e^{-x}$（或 $\le\frac1{x^2}$），$[0,1]$ 上是常义积分。
5. $p>1$ 绝对收敛；$0<p\le1$ 条件收敛（Dirichlet 判别法给出收敛，$|\cos x|\ge\cos^2x$ 给出不绝对收敛）；$p\le0$ 发散。
6. $\int_0^{+\infty}\frac{x}{1+x^2}\mathrm dx=\lim\frac12\ln(1+A^2)=+\infty$，单边就发散了；对称抵消只是主值。
7. $x\to0^+$ 时 $\frac{\sin x}{x^{3/2}}\sim\frac1{x^{1/2}}$，收敛。


