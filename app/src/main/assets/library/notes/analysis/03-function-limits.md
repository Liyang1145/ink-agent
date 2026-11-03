---
title: 函数极限
subject: 数学分析
chapter: 3
prerequisites: 数列极限
minutes: 60
---

# 第 3 章　函数极限

## 为什么要学

数列只能"一步一步"地走向无穷，而函数的自变量可以**连续地**滑向某一点。我们想描述"当 $x$ 靠近 $x_0$ 时，$f(x)$ 靠近 $A$"，而且——这一点至关重要——**$f$ 在 $x_0$ 这一点本身的值无关紧要**，甚至可以没有定义。

为什么要这样设计？因为导数就是这样一个极限：$\dfrac{f(x)-f(x_0)}{x-x_0}$ 在 $x=x_0$ 处是 $\frac00$，根本没有定义，可我们偏偏关心它在 $x$ 靠近 $x_0$ 时的趋势。

"挑战—应战"游戏升级为：对手给出纵向的误差带 $(A-\varepsilon,A+\varepsilon)$，你要回答一个横向的窗口 $(x_0-\delta,x_0+\delta)$，使得只要 $x$ 在窗口里（去掉中心 $x_0$），函数图像就待在误差带里。想象用一个矩形框住图像：横宽 $2\delta$，纵高 $2\varepsilon$，图像只从左右两侧穿出，不从上下穿出。

## 3.1 定义

设 $f$ 在 $x_0$ 的某个去心邻域 $\mathring U(x_0)=\{x:0<|x-x_0|<r\}$ 内有定义。

**定义 3.1（$\varepsilon$-$\delta$）** 称 $\lim\limits_{x\to x_0}f(x)=A$，若
$$\forall\varepsilon>0,\ \exists\delta>0,\ \forall x:\ 0<|x-x_0|<\delta\Rightarrow|f(x)-A|<\varepsilon.$$

注意 "$0<|x-x_0|$"：**$x=x_0$ 被排除在外**。

**单侧极限**：$\lim\limits_{x\to x_0^+}f(x)=A$ 只要求 $x_0<x<x_0+\delta$ 时成立，记为 $f(x_0+0)$；左极限 $f(x_0-0)$ 类似。

**定理 3.1** $\lim\limits_{x\to x_0}f(x)=A\iff f(x_0+0)=f(x_0-0)=A$。

**自变量趋于无穷**：$\lim\limits_{x\to+\infty}f(x)=A$ 指 $\forall\varepsilon>0,\exists X>0,\forall x>X:|f(x)-A|<\varepsilon$。

**函数趋于无穷**：$\lim\limits_{x\to x_0}f(x)=+\infty$ 指 $\forall M>0,\exists\delta>0,\ 0<|x-x_0|<\delta\Rightarrow f(x)>M$。

一共有"6 种自变量趋向 × 若干种函数趋向"的组合，但模式完全一样：**"函数值的目标"用 $\varepsilon$ 或 $M$ 描述，"自变量的范围"用 $\delta$ 或 $X$ 描述。**

## 3.2 用定义证明

**例 1** 证明 $\lim\limits_{x\to2}x^2=4$。

**分析**：$|x^2-4|=|x+2||x-2|$。$|x-2|$ 是我们能控制的，$|x+2|$ 需要先"框住"：先限制 $|x-2|<1$，则 $1<x<3$，$|x+2|<5$。于是 $|x^2-4|<5|x-2|$。

**证明**：任给 $\varepsilon>0$，取 $\delta=\min\left\{1,\dfrac\varepsilon5\right\}$。当 $0<|x-2|<\delta$ 时，$|x+2|<5$，所以 $|x^2-4|<5\delta\le\varepsilon$。$\blacksquare$

> **"先限制 $\delta\le1$"** 是处理乘积型式子的标准技巧：先用一个固定的小范围把"捣乱因子"控制成常数，再处理剩下的部分。

**例 2** 证明 $\lim\limits_{x\to+\infty}\dfrac{\sin x}{x}=0$。

**证明**：$\left|\frac{\sin x}x\right|\le\frac1x$。任给 $\varepsilon>0$，取 $X=\frac1\varepsilon$，则 $x>X$ 时 $\left|\frac{\sin x}x\right|<\varepsilon$。$\blacksquare$

## 3.3 性质

函数极限有一套和数列极限平行的性质，证明方法几乎逐字照搬：

- **唯一性**；
- **局部有界性**：若 $\lim\limits_{x\to x_0}f(x)=A$，则 $f$ 在 $x_0$ 的某去心邻域内有界（注意只是"局部"！$\frac1x$ 在 $x\to1$ 时有极限，但在 $(0,2)$ 上无界）；
- **局部保号性**：若 $A>0$，则在某去心邻域内 $f(x)>\frac A2>0$；
- **保不等式**、**四则运算**、**夹逼准则**。

**复合函数极限**：若 $\lim\limits_{x\to x_0}g(x)=u_0$，$\lim\limits_{u\to u_0}f(u)=A$，**并且**在 $x_0$ 的某去心邻域内 $g(x)\ne u_0$，则 $\lim\limits_{x\to x_0}f(g(x))=A$。（若 $f$ 在 $u_0$ 连续，则不需要 $g(x)\ne u_0$ 这个条件。）

> 反例：$f(u)=\begin{cases}1,&u\ne0\\0,&u=0\end{cases}$，$g(x)\equiv0$。则 $\lim\limits_{u\to0}f(u)=1$，但 $f(g(x))\equiv0$。

## 3.4 Heine 定理：连接函数与数列

**定理 3.2（Heine 归结原理）** $\lim\limits_{x\to x_0}f(x)=A$ 当且仅当：对**任何**满足 $x_n\to x_0$、$x_n\ne x_0$ 的数列，都有 $f(x_n)\to A$。

**想法**：$\Rightarrow$ 是直接的（$\delta$ 对应着 $N$）。$\Leftarrow$ 用反证：若函数极限不是 $A$，就能对每个 $\delta=\frac1n$ 找到一个"坏点" $x_n$，这些坏点组成一个数列，$f(x_n)$ 不趋于 $A$。

**证明（$\Leftarrow$）**：反设 $\lim\limits_{x\to x_0}f(x)\ne A$，则
$$\exists\varepsilon_0>0,\ \forall\delta>0,\ \exists x:\ 0<|x-x_0|<\delta,\ |f(x)-A|\ge\varepsilon_0.$$
取 $\delta=\frac1n$，得 $x_n$ 满足 $0<|x_n-x_0|<\frac1n$，$|f(x_n)-A|\ge\varepsilon_0$。于是 $x_n\to x_0$，$x_n\ne x_0$，但 $f(x_n)\not\to A$，矛盾。$\blacksquare$

Heine 定理最常见的用途是**证明极限不存在**：找两个数列 $x_n,x_n'\to x_0$ 使 $f(x_n),f(x_n')$ 趋于不同的值。

**例 3** 证明 $\lim\limits_{x\to0}\sin\dfrac1x$ 不存在。

**证明**：取 $x_n=\dfrac1{n\pi}$，$x_n'=\dfrac1{2n\pi+\frac\pi2}$，都趋于 $0$ 且不为 $0$。但 $\sin\frac1{x_n}=0\to0$，$\sin\frac1{x_n'}=1\to1$。由 Heine 定理，极限不存在。$\blacksquare$

**函数极限的 Cauchy 准则**：$\lim\limits_{x\to x_0}f(x)$ 存在 $\iff\forall\varepsilon>0,\exists\delta>0$，当 $0<|x'-x_0|<\delta$，$0<|x''-x_0|<\delta$ 时 $|f(x')-f(x'')|<\varepsilon$。

## 3.5 两个重要极限

**定理 3.3** $\displaystyle\lim_{x\to0}\frac{\sin x}{x}=1$。

**想法**：在单位圆里比较三个面积——三角形 $\subseteq$ 扇形 $\subseteq$ 大三角形。

**证明**：当 $0<x<\frac\pi2$ 时，单位圆中圆心角为 $x$ 的扇形面积 $\frac x2$ 介于两个三角形面积之间：
$$\frac12\sin x<\frac12x<\frac12\tan x.$$
除以 $\frac12\sin x>0$，取倒数得 $\cos x<\dfrac{\sin x}x<1$。由于 $\cos x\to1$（因为 $0\le1-\cos x=2\sin^2\frac x2\le\frac{x^2}2$），夹逼得右极限为 $1$。$\frac{\sin x}x$ 是偶函数，左极限也为 $1$。$\blacksquare$

**定理 3.4** $\displaystyle\lim_{x\to\infty}\left(1+\frac1x\right)^x=e$，等价地 $\displaystyle\lim_{t\to0}(1+t)^{1/t}=e$。

**证明要点**：对 $x\to+\infty$，令 $n=\lfloor x\rfloor$，则
$$\left(1+\frac1{n+1}\right)^n<\left(1+\frac1x\right)^x<\left(1+\frac1n\right)^{n+1},$$
两端都趋于 $e$（由数列极限 $\left(1+\frac1n\right)^n\to e$）。$x\to-\infty$ 时令 $x=-y$，化为 $\left(1+\frac1{y-1}\right)^{y}$。$\blacksquare$

## 3.6 无穷小的比较与等价代换

若 $\lim\alpha(x)=\lim\beta(x)=0$：

- $\alpha=o(\beta)$：$\dfrac\alpha\beta\to0$（$\alpha$ 是 $\beta$ 的高阶无穷小）；
- $\alpha=O(\beta)$：$\dfrac\alpha\beta$ 在该点附近有界；
- $\alpha\sim\beta$（等价）：$\dfrac\alpha\beta\to1$。

当 $x\to0$ 时的常用等价无穷小：
$$\sin x\sim\tan x\sim\arcsin x\sim\arctan x\sim\ln(1+x)\sim e^x-1\sim x,$$
$$1-\cos x\sim\frac{x^2}2,\qquad(1+x)^\alpha-1\sim\alpha x.$$

**定理 3.5（等价代换）** 若 $\alpha\sim\alpha'$，则 $\lim\alpha\cdot\gamma=\lim\alpha'\cdot\gamma$，$\lim\dfrac{\gamma}{\alpha}=\lim\dfrac\gamma{\alpha'}$（一边存在则另一边存在且相等）。

证明只有一行：$\alpha\gamma=\dfrac\alpha{\alpha'}\cdot\alpha'\gamma$。**这个定理只对乘除因子成立，不能对加减项随意代换。**

## 3.7 求极限的工具箱：先判断类型，再选工具

初学阶段大量练习都是"求极限"。与其死记题型，不如养成一个固定的思考顺序：

1. **能直接代入吗？** 如果函数在该点连续（下一章会说明初等函数在定义域内都连续），直接代入就是答案。
2. **是什么不定型？** $\frac00$、$\frac\infty\infty$、$0\cdot\infty$、$\infty-\infty$、$1^\infty$、$0^0$、$\infty^0$。后四种先通过通分、取对数、写成 $e^{v\ln u}$ 等变形化为前两种。
3. **能约去"罪魁祸首"吗？** 因式分解、分子或分母有理化（乘共轭根式）、三角恒等变形，常常能把造成 $\frac00$ 的因子直接约掉。
4. **能用等价无穷小吗？** 只替换乘除因子；遇到加减，换成 Taylor 展开（第 6 章）。
5. **需要夹逼吗？** 含 $\sin$、取整函数 $\lfloor x\rfloor$、或 $n$ 项和时，常常先放缩再夹逼。
6. **L'Hospital 法则**（第 6 章）：有效但不要滥用，求导后若更复杂，就换别的方法。

另外，**"抓大头"** 是处理 $x\to\infty$ 时有理分式的快捷办法：分子分母同除以最高次幂。例如
$$\lim_{x\to\infty}\frac{3x^3-x+1}{2x^3+5x^2}=\lim_{x\to\infty}\frac{3-\frac1{x^2}+\frac1{x^3}}{2+\frac5x}=\frac32.$$
更一般地，当 $x\to+\infty$ 时有增长速度的层级：
$$\ln x\ll x^\alpha\ll a^x\ll x^x\qquad(\alpha>0,\ a>1),$$
其中 "$f\ll g$" 表示 $\frac fg\to0$。记住这个层级，许多极限一眼就能判断。

## 更多例题

**例 4** 求 $\displaystyle\lim_{x\to0}\frac{\tan x-\sin x}{x^3}$。

**错误做法**：$\tan x\sim x$，$\sin x\sim x$，分子"$\sim x-x=0$"，极限为 $0$。这是错的！

**正确做法**：$\tan x-\sin x=\tan x(1-\cos x)$，是乘积，可以代换：
$$\lim_{x\to0}\frac{\tan x(1-\cos x)}{x^3}=\lim_{x\to0}\frac{x\cdot\frac{x^2}2}{x^3}=\frac12.$$

**例 5** 求 $\displaystyle\lim_{x\to0}(\cos x)^{1/x^2}$。

**解**：$1^\infty$ 型，取对数或写成 $e$ 的幂：
$$(\cos x)^{1/x^2}=\exp\left(\frac{\ln(1+(\cos x-1))}{x^2}\right).$$
由 $\ln(1+u)\sim u$（$u=\cos x-1\to0$）及 $\cos x-1\sim-\frac{x^2}2$，指数趋于 $-\frac12$，极限为 $e^{-1/2}$。

> $1^\infty$ 型的通用公式：若 $u\to0$，$v\to\infty$，且 $uv\to L$，则 $(1+u)^v\to e^L$。

**例 6（CMC 风格）** 设 $f$ 在 $(0,+\infty)$ 上满足 $\lim\limits_{x\to+\infty}\big(f(x+1)-f(x)\big)=A$，且 $f$ 在每个有界区间 $[a,b]\subset(0,+\infty)$ 上有界。证明 $\lim\limits_{x\to+\infty}\dfrac{f(x)}x=A$。

**想法**：这是 Cesàro 平均的"连续版"。把 $x$ 写成 $x=n+t$，$t\in[a,a+1)$ 为"零头"，把 $f(x)-f(t)$ 拆成 $n$ 个相邻差之和。

**证明**：任给 $\varepsilon>0$，取 $a>0$ 使 $x\ge a$ 时 $|f(x+1)-f(x)-A|<\varepsilon$。对 $x\ge a+1$，令 $n=\lfloor x-a\rfloor\ge1$，$t=x-n\in[a,a+1)$，则
$$f(x)-f(t)=\sum_{k=0}^{n-1}\big(f(t+k+1)-f(t+k)\big),$$
所以 $|f(x)-f(t)-nA|<n\varepsilon$。于是
$$\left|\frac{f(x)}x-A\right|\le\frac{|f(x)-f(t)-nA|}{x}+\frac{|f(t)|+|t||A|}{x}<\frac{n\varepsilon}{x}+\frac{M+(a+1)|A|}{x},$$
其中 $M$ 是 $|f|$ 在 $[a,a+1]$ 上的界，用到 $x=n+t$ 故 $f(x)-xA=\big(f(x)-f(t)-nA\big)+\big(f(t)-tA\big)$。由于 $n\le x$，第一项 $<\varepsilon$；第二项分子是常数，当 $x$ 充分大时也 $<\varepsilon$。所以 $x$ 充分大时 $\left|\frac{f(x)}x-A\right|<2\varepsilon$。$\blacksquare$

## 易错点

1. **极限与 $f(x_0)$ 无关。** $f(x_0)$ 是否存在、等于多少，都不影响 $\lim\limits_{x\to x_0}f(x)$。
2. **加减中不能用等价代换。** 见例 4。安全的做法：只替换整个乘除因子；或用 Taylor 展开到足够阶。
3. **复合函数极限需要条件。** 内层在去心邻域里不能取到 $u_0$，或外层连续。
4. **Heine 定理证不存在只需两个数列；证存在需要"所有"数列。** 用一两个特殊数列算出的值不能作为极限存在的证明。
5. **$x\to\infty$ 包含 $\pm\infty$ 两个方向。** 如 $\lim\limits_{x\to\infty}e^x$ 不存在，因为 $+\infty$ 和 $-\infty$ 两侧行为不同；$\lim\limits_{x\to\infty}\arctan x$ 也不存在。

## 自测清单

1. 写出 $\lim\limits_{x\to x_0^-}f(x)=A$ 的 $\varepsilon$-$\delta$ 定义。
2. 用定义证明 $\lim\limits_{x\to1}\dfrac1x=1$，$\delta$ 怎么取？
3. 求 $\lim\limits_{x\to0}\dfrac{e^{x}-1-x}{x^2}$ 时，能否把 $e^x-1$ 换成 $x$？
4. 求 $\lim\limits_{x\to0}x\sin\frac1x$ 和 $\lim\limits_{x\to\infty}x\sin\frac1x$。
5. 狄利克雷函数 $D(x)$（有理数取 $1$，无理数取 $0$）在哪些点有极限？
6. 求 $\lim\limits_{x\to0}\left(1+2x\right)^{3/x}$。
7. 函数局部有界性中的"局部"能否去掉？举例。

## 参考答案

### 点击展开参考答案

1. $\forall\varepsilon>0,\exists\delta>0$，当 $x_0-\delta<x<x_0$ 时 $|f(x)-A|<\varepsilon$。
2. $\left|\frac1x-1\right|=\frac{|x-1|}{|x|}$，先限制 $|x-1|<\frac12$ 得 $|x|>\frac12$，于是 $<2|x-1|$；取 $\delta=\min\{\frac12,\frac\varepsilon2\}$。
3. 不能（加减项）。正确结果是 $\frac12$（Taylor 展开或 L'Hospital 法则）。
4. 第一个是 $0$（无穷小乘有界量）；第二个令 $t=\frac1x\to0$，得 $\frac{\sin t}t\to1$。
5. 处处没有极限：任一点附近既有有理数也有无理数，用 Heine 定理取两个数列。
6. $e^6$（$uv=2x\cdot\frac3x=6$）。
7. 不能。$f(x)=\frac1x$ 在 $x\to1$ 有极限，但在 $(0,2)$ 上无界。


