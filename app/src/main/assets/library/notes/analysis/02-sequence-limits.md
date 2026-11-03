---
title: 数列极限
subject: 数学分析
chapter: 2
prerequisites: 实数与确界原理
minutes: 60
---

# 第 2 章　数列极限

## 为什么要学

高中说"当 $n$ 越来越大，$\frac1n$ 越来越接近 $0$"。这句话的问题在于"越来越接近"太含糊：$1+\frac1n$ 也越来越接近 $0$（离 $0$ 的距离在变小），可它的极限显然是 $1$ 而不是 $0$。

我们需要一个**能检验、能证明**的定义。核心想法是一场"挑战—应战"游戏：

> 对手随便指定一个误差范围 $\varepsilon$（可以小得离谱，比如 $10^{-100}$），你必须回答一个位置 $N$，保证从第 $N$ 项以后，**所有项**和 $a$ 的距离都小于 $\varepsilon$。如果无论对手怎么挑战你都能应战，就说 $a_n$ 的极限是 $a$。

画在数轴上：以 $a$ 为中心画一条宽 $2\varepsilon$ 的"带子"，极限为 $a$ 意味着无论带子多窄，数列最终会全部掉进带子里，只有有限项留在外面。

这一章是全书的"语言课"。$\varepsilon$-$N$ 语言一旦熟练，后面的函数极限、连续、一致收敛都只是它的变体。

## 2.1 定义

**定义 2.1** 设 $\{a_n\}$ 是数列，$a\in\mathbb R$。若
$$\forall\varepsilon>0,\ \exists N\in\mathbb N^+,\ \forall n>N:\ |a_n-a|<\varepsilon,$$
则称 $\{a_n\}$ **收敛**于 $a$，记作 $\lim\limits_{n\to\infty}a_n=a$ 或 $a_n\to a$。不收敛的数列称为**发散**。

几点说明：

- $N$ 依赖于 $\varepsilon$，通常 $\varepsilon$ 越小 $N$ 越大。$N$ 不需要是"最小的那个"，只要存在即可，所以证明时**可以放大不等式**。
- $|a_n-a|<\varepsilon$ 换成 $\le\varepsilon$，或换成 $<2\varepsilon$、$<C\varepsilon$（$C$ 是与 $n,\varepsilon$ 无关的常数），定义不变。
- 等价的"邻域"说法：$a$ 的任何邻域 $(a-\varepsilon,a+\varepsilon)$ 之外只有 $\{a_n\}$ 的有限项。

**否定形式**（务必会写）：$a_n\not\to a$ 当且仅当
$$\exists\varepsilon_0>0,\ \forall N,\ \exists n>N:\ |a_n-a|\ge\varepsilon_0,$$
也就是说，有无穷多项落在带子外面。

**无穷大**：$a_n\to+\infty$ 指 $\forall M>0,\ \exists N,\ \forall n>N:\ a_n>M$。注意这种数列仍称为**发散**的。

## 2.2 用定义证明极限：放大法

**例 1** 证明 $\lim\limits_{n\to\infty}\dfrac{n^2+n}{2n^2-1}=\dfrac12$。

**分析**（草稿纸上做）：
$$\left|\frac{n^2+n}{2n^2-1}-\frac12\right|=\frac{2n+1}{2(2n^2-1)}.$$
直接解 $\frac{2n+1}{2(2n^2-1)}<\varepsilon$ 很麻烦。我们**放大**：当 $n\ge1$ 时 $2n+1\le3n$，$2n^2-1\ge n^2$，所以上式 $\le\dfrac{3n}{2n^2}=\dfrac{3}{2n}<\dfrac2n$。只要 $\frac2n<\varepsilon$，即 $n>\frac2\varepsilon$。

**证明**（正式书写）：任给 $\varepsilon>0$，取 $N=\left\lfloor\frac2\varepsilon\right\rfloor+1$。当 $n>N$ 时，
$$\left|\frac{n^2+n}{2n^2-1}-\frac12\right|=\frac{2n+1}{2(2n^2-1)}\le\frac{3n}{2n^2}<\frac2n<\varepsilon.\qquad\blacksquare$$

> 放大的原则：**放大后的式子要简单，而且仍然能趋于 0**。把分子放大、分母缩小，丢掉"拖后腿"的低阶项。

**例 2** 设 $a>1$，证明 $\lim\limits_{n\to\infty}\sqrt[n]a=1$。

**想法**：令 $h_n=\sqrt[n]a-1>0$，用二项式（或 Bernoulli 不等式）把 $a=(1+h_n)^n$ 展开，就能把 $h_n$ 压住。

**证明**：$a=(1+h_n)^n\ge1+nh_n$，所以 $0<h_n\le\dfrac{a-1}n$。任给 $\varepsilon>0$，取 $N=\left\lfloor\frac{a-1}\varepsilon\right\rfloor+1$，当 $n>N$ 时 $|\sqrt[n]a-1|=h_n<\varepsilon$。$\blacksquare$

同样的技巧（展开到二次项 $\binom n2h_n^2$）可证 $\sqrt[n]n\to1$：由 $n=(1+h_n)^n\ge\frac{n(n-1)}2h_n^2$ 得 $0\le h_n\le\sqrt{\frac{2}{n-1}}$（$n\ge2$）。

## 2.3 收敛数列的性质

**定理 2.1（唯一性）** 收敛数列的极限唯一。

*想法*：如果同时趋于 $a$ 和 $b\ne a$，取 $\varepsilon=\frac{|a-b|}2$，两条带子不相交，而数列最终要同时待在两条带子里，不可能。

**定理 2.2（有界性）** 收敛数列必有界。

*证明*：取 $\varepsilon=1$，存在 $N$ 使 $n>N$ 时 $|a_n|<|a|+1$。令 $M=\max\{|a_1|,\dots,|a_N|,|a|+1\}$ 即可。（有限项总是有界的——这是"只关心尾巴"的典型手法。）

**定理 2.3（保号性）** 若 $a_n\to a>0$，则存在 $N$，当 $n>N$ 时 $a_n>\frac a2>0$。

*证明*：取 $\varepsilon=\frac a2$。

**推论（保不等式）** 若 $a_n\to a$，$b_n\to b$，且 $n$ 充分大时 $a_n\le b_n$，则 $a\le b$。**注意：严格不等号不能保持**，$\frac1n>0$ 但极限 $0\not>0$。

**定理 2.4（四则运算）** 若 $a_n\to a,\ b_n\to b$，则 $a_n\pm b_n\to a\pm b$，$a_nb_n\to ab$；若 $b\ne0$，则 $\dfrac{a_n}{b_n}\to\dfrac ab$。

*乘法的证明要点*：
$$|a_nb_n-ab|=|a_n(b_n-b)+b(a_n-a)|\le M|b_n-b|+|b||a_n-a|,$$
其中 $M$ 是 $|a_n|$ 的界。"加一项减一项"是分析里最常用的手法。

**定理 2.5（夹逼准则）** 若 $n$ 充分大时 $a_n\le c_n\le b_n$，且 $a_n\to L,\ b_n\to L$，则 $c_n\to L$。

## 2.4 单调有界定理与 $e$

**定理 2.6（单调有界定理）** 单调递增且有上界的数列收敛，且极限等于 $\sup\{a_n\}$。

**想法**：数列只往右走，又被一堵墙挡住，它最终会"贴"到最靠左的那堵墙——上确界上。

**证明**：由确界原理，$\beta=\sup\{a_n\}$ 存在。任给 $\varepsilon>0$，$\beta-\varepsilon$ 不是上界，故存在 $N$ 使 $a_N>\beta-\varepsilon$。当 $n>N$ 时，由单调性
$$\beta-\varepsilon<a_N\le a_n\le\beta<\beta+\varepsilon,$$
即 $|a_n-\beta|<\varepsilon$。$\blacksquare$

这是确界原理的第一次"实战"。注意它的价值：**不需要事先知道极限是多少，就能断定极限存在**。

**例 3（数 $e$）** 证明 $a_n=\left(1+\frac1n\right)^n$ 单调递增且有上界，从而收敛。其极限记为 $e$。

**证明**：单调性：用 $n+1$ 个正数的均值不等式（AM–GM），取 $n$ 个 $1+\frac1n$ 和一个 $1$：
$$\sqrt[n+1]{\left(1+\tfrac1n\right)^n\cdot1}\le\frac{n\left(1+\frac1n\right)+1}{n+1}=1+\frac1{n+1},$$
两边 $n+1$ 次方得 $a_n\le a_{n+1}$。有界性：类似地考虑 $b_n=\left(1+\frac1n\right)^{n+1}$，它单调递减（对 $\left(1-\frac1{n+1}\right)^{n+1}$ 用 AM–GM 再取倒数），且 $a_n<b_n\le b_1=4$。$\blacksquare$

## 2.5 子列、Bolzano–Weierstrass 定理与 Cauchy 准则

**子列**：从 $\{a_n\}$ 中按原顺序选出无穷多项 $a_{n_1},a_{n_2},\dots$（$n_1<n_2<\cdots$），记作 $\{a_{n_k}\}$。注意 $n_k\ge k$。

**定理 2.7** $a_n\to a$ 当且仅当它的每个子列都收敛于 $a$。

常用于**证明发散**：找出两个子列趋于不同极限。例如 $(-1)^n$ 的奇子列趋于 $-1$，偶子列趋于 $1$，所以发散。另一个常用结论：若 $a_{2k}\to a$ 且 $a_{2k-1}\to a$，则 $a_n\to a$。

**定理 2.8（Bolzano–Weierstrass）** 有界数列必有收敛子列。

*想法（二分法）*：数列夹在 $[c,d]$ 中。把区间对半分，至少一半含数列的无穷多项，留下那一半；继续对半分……得到一串长度趋于 0 的闭区间套，每个都含无穷多项。在第 $k$ 个区间中挑一项 $a_{n_k}$（保证 $n_k$ 递增），它们被挤到区间套的公共点上。

**定义 2.2** 称 $\{a_n\}$ 为 **Cauchy 列**，若
$$\forall\varepsilon>0,\ \exists N,\ \forall m,n>N:\ |a_m-a_n|<\varepsilon.$$

**定理 2.9（Cauchy 收敛准则）** 数列收敛 $\iff$ 它是 Cauchy 列。

**想法**：Cauchy 条件只说"项与项之间越来越近"，完全没提极限是谁。这对于**不知道极限**的场合极其有用。

**证明**（$\Leftarrow$，核心方向）：
1. Cauchy 列有界：取 $\varepsilon=1$，$n>N$ 时 $|a_n|<|a_{N+1}|+1$。
2. 由 B–W 定理，有子列 $a_{n_k}\to a$。
3. 任给 $\varepsilon>0$，取 $N$ 使 $m,n>N$ 时 $|a_m-a_n|<\frac\varepsilon2$；取 $k$ 足够大使 $n_k>N$ 且 $|a_{n_k}-a|<\frac\varepsilon2$。则 $n>N$ 时
$$|a_n-a|\le|a_n-a_{n_k}|+|a_{n_k}-a|<\varepsilon.\qquad\blacksquare$$
（$\Rightarrow$ 很容易：$|a_m-a_n|\le|a_m-a|+|a-a_n|$。）

## 2.6 Stolz 定理与上下极限（简介）

**Stolz 定理（$\frac{*}{\infty}$ 型）** 若 $\{b_n\}$ 严格递增且 $b_n\to+\infty$，且
$$\lim_{n\to\infty}\frac{a_{n+1}-a_n}{b_{n+1}-b_n}=L\quad(L\text{ 有限或 }\pm\infty),$$
则 $\lim\limits_{n\to\infty}\dfrac{a_n}{b_n}=L$。它是数列版的 L'Hospital 法则。

**上下极限**：$\varlimsup\limits_{n\to\infty}a_n=\lim\limits_{n\to\infty}\sup\limits_{k\ge n}a_k$，$\varliminf\limits_{n\to\infty}a_n=\lim\limits_{n\to\infty}\inf\limits_{k\ge n}a_k$。它们对任何有界数列都存在（单调有界！），是所有收敛子列极限中的最大者和最小者，且 $a_n$ 收敛 $\iff$ 上下极限相等。

## 更多例题

**例 4（递推数列）** $a_1=\sqrt2$，$a_{n+1}=\sqrt{2+a_n}$。求 $\lim a_n$。

**解**：归纳可证 $0<a_n<2$：若 $a_n<2$，则 $a_{n+1}<\sqrt4=2$。又 $a_{n+1}^2-a_n^2=2+a_n-a_n^2=(2-a_n)(1+a_n)>0$，所以递增。由单调有界定理 $a_n\to A$，在递推式两边取极限得 $A=\sqrt{2+A}$，$A\ge0$，解得 $A=2$。

> **顺序很重要**：必须先证明极限存在，再取极限解方程。否则对 $a_{n+1}=2a_n,a_1=1$ "求得" $A=2A\Rightarrow A=0$，显然荒谬。

**例 5（Cesàro 平均）** 若 $a_n\to a$，证明 $\dfrac{a_1+\cdots+a_n}n\to a$。

**证明一（Stolz）**：令 $A_n=a_1+\dots+a_n$，$b_n=n$，则 $\frac{A_{n+1}-A_n}{(n+1)-n}=a_{n+1}\to a$。

**证明二（直接，体会"拆头尾"）**：任给 $\varepsilon>0$，取 $N_1$ 使 $n>N_1$ 时 $|a_n-a|<\frac\varepsilon2$。则
$$\left|\frac{A_n}n-a\right|\le\frac{\sum_{k=1}^{N_1}|a_k-a|}{n}+\frac{\sum_{k=N_1+1}^{n}|a_k-a|}{n}<\frac{C}{n}+\frac\varepsilon2,$$
其中 $C=\sum_{k=1}^{N_1}|a_k-a|$ 是固定常数。再取 $N>N_1$ 使 $n>N$ 时 $\frac Cn<\frac\varepsilon2$ 即可。$\blacksquare$

> "头"是有限项，被 $n$ 除后变小；"尾"每一项都小。这一拆分在 CMC 中反复出现。

**例 6（CMC 风格）** 设 $0<x_1<\pi$，$x_{n+1}=\sin x_n$。证明 $\lim\limits_{n\to\infty}\sqrt n\,x_n=\sqrt3$。

**解**：由 $0<\sin x<x$（$0<x<\pi$），$\{x_n\}$ 正且递减，故收敛于某 $A\in[0,1]$，且 $A=\sin A$，得 $A=0$。

关键：考虑 $\dfrac1{x_n^2}$。用 Stolz 定理求 $\lim\dfrac{n}{1/x_n^2}$，即求
$$\lim_{n\to\infty}\frac{1}{\frac1{x_{n+1}^2}-\frac1{x_n^2}}=\lim_{n\to\infty}\frac{x_n^2\sin^2x_n}{x_n^2-\sin^2x_n}.$$
由于 $x_n\to0$，化为函数极限 $\lim\limits_{t\to0}\dfrac{t^2\sin^2t}{t^2-\sin^2t}$。用 Taylor 展开 $\sin t=t-\frac{t^3}6+o(t^3)$，$\sin^2t=t^2-\frac{t^4}3+o(t^4)$，所以分母 $=\frac{t^4}3+o(t^4)$，分子 $=t^4+o(t^4)$，极限为 $3$。（Taylor 展开见第 6 章，现在先接受这一步。）

Stolz 定理的条件：$\frac1{x_n^2}$ 严格递增趋于 $+\infty$，满足。因此 $nx_n^2\to3$，即 $\sqrt n\,x_n\to\sqrt3$。

## 易错点

1. **$N$ 必须对"所有 $n>N$"都成立。** 只验证某些 $n$ 不够。
2. **先存在、后求值。** 递推数列取极限前必须先证收敛（例 4 的提醒）。
3. **严格不等号取极限会变成非严格**：$a_n<b_n$ 只能推出 $\lim a_n\le\lim b_n$。
4. **四则运算的前提是各自收敛。** $a_n=(-1)^n,b_n=(-1)^{n+1}$，和收敛但各自发散；不能把 $\lim(a_n+b_n)$ 拆开。
5. **无穷多个无穷小之和未必是无穷小。** $\underbrace{\frac1n+\cdots+\frac1n}_{n\text{ 个}}=1$。四则运算只对**有限个**成立。
6. **Stolz 定理是单向的。** $\frac{a_{n+1}-a_n}{b_{n+1}-b_n}$ 的极限不存在时，$\frac{a_n}{b_n}$ 仍可能收敛。

## 自测清单

1. 写出"$\{a_n\}$ 不收敛于 $a$"的 $\varepsilon$-$N$ 表述。
2. 用定义证明 $\lim\frac{3n+1}{n-2}=3$，你会取怎样的 $N$？
3. 若 $|a_n|\to|a|$，是否有 $a_n\to a$？反过来呢？
4. 证明：若 $a_n\to0$ 且 $\{b_n\}$ 有界，则 $a_nb_n\to0$。
5. 有界数列一定收敛吗？单调数列一定收敛吗？
6. 判断：Cauchy 列的定义中能否只要求 $|a_{n+1}-a_n|\to0$？
7. 求 $\lim\limits_{n\to\infty}\dfrac{1+\sqrt2+\sqrt[3]3+\cdots+\sqrt[n]n}{n}$。

## 参考答案

### 点击展开参考答案

1. $\exists\varepsilon_0>0,\ \forall N,\ \exists n>N,\ |a_n-a|\ge\varepsilon_0$。
2. $n\ge4$ 时 $\left|\frac{3n+1}{n-2}-3\right|=\frac{7}{n-2}\le\frac{14}{n}$，取 $N=\max\{4,\lfloor14/\varepsilon\rfloor+1\}$。
3. 反方向成立：$a_n\to a\Rightarrow|a_n|\to|a|$（因为 $\big||a_n|-|a|\big|\le|a_n-a|$）。正方向不成立，如 $a_n=(-1)^n$，$a=1$；但 $a=0$ 时成立。
4. 设 $|b_n|\le M$，则 $|a_nb_n|\le M|a_n|$，任给 $\varepsilon$ 取 $N$ 使 $|a_n|<\varepsilon/M$。
5. 都不一定：$(-1)^n$ 有界不收敛，$n$ 单调不收敛。单调**且**有界才收敛。
6. 不能。$a_n=\sqrt n$ 满足 $a_{n+1}-a_n\to0$ 但发散。
7. 由 $\sqrt[n]n\to1$ 和 Cesàro 平均（例 5），极限为 $1$。


