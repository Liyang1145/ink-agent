---
title: 不定积分
subject: 数学分析
chapter: 7
prerequisites: 导数与微分
minutes: 60
---

# 第 7 章　不定积分

## 为什么要学

求导是"已知位置求速度"；反过来，"已知速度求位置"就是求原函数。这在物理里天天发生：知道加速度，求速度；知道速度，求路程。

不定积分本身更像一门**手艺**：没有一个万能算法能对所有初等函数求出初等原函数（事实上像 $e^{-x^2}$、$\frac{\sin x}x$ 的原函数根本不是初等函数）。我们能做的是掌握几种基本技巧——换元、分部、有理函数分解——并学会**识别**一个被积函数该用哪种技巧。

学好这一章的意义有两层：一是为定积分（第 8 章）提供计算手段——Newton–Leibniz 公式要求先找到原函数；二是训练"把复杂式子化归为已知形式"的眼光。熟练度只能靠练习获得，但这一章会告诉你练习时应该关注什么。

## 7.1 原函数与不定积分

**定义 7.1** 若在区间 $I$ 上 $F'(x)=f(x)$，称 $F$ 是 $f$ 在 $I$ 上的一个**原函数**。$f$ 的全体原函数称为 $f$ 的**不定积分**，记作 $\displaystyle\int f(x)\,\mathrm dx$。

**定理 7.1** 若 $F$ 是 $f$ 在区间 $I$ 上的一个原函数，则 $f$ 的全体原函数为 $F(x)+C$。

*证明*：若 $G$ 也是原函数，则 $(G-F)'=0$，由中值定理推论，$G-F$ 在**区间**上为常数。$\blacksquare$

> **"区间"二字很关键。** 在 $\mathbb R\setminus\{0\}$ 上，$\ln|x|$ 和 $\ln|x|+\begin{cases}1,&x>0\\2,&x<0\end{cases}$ 都是 $\frac1x$ 的原函数，相差的不是常数。写 $\int\frac{\mathrm dx}x=\ln|x|+C$ 时默认在不含 $0$ 的某个区间上讨论。

**哪些函数有原函数？**
- 区间上的**连续函数一定有原函数**（第 8 章用变上限积分证明）。
- 有第一类间断点的函数**没有**原函数（由 Darboux 定理，导函数没有第一类间断点）。
- 有第二类间断点的函数可能有原函数，例如上一章的 $2x\sin\frac1x-\cos\frac1x$（补充定义 $0$ 处为 $0$）。

**基本积分表**（求导表反过来读）：
$$\int x^\alpha\mathrm dx=\frac{x^{\alpha+1}}{\alpha+1}+C\ (\alpha\ne-1),\quad\int\frac{\mathrm dx}x=\ln|x|+C,$$
$$\int e^x\mathrm dx=e^x+C,\quad\int\sin x\,\mathrm dx=-\cos x+C,\quad\int\cos x\,\mathrm dx=\sin x+C,$$
$$\int\frac{\mathrm dx}{1+x^2}=\arctan x+C,\quad\int\frac{\mathrm dx}{\sqrt{1-x^2}}=\arcsin x+C,$$
$$\int\sec^2x\,\mathrm dx=\tan x+C,\quad\int\frac{\mathrm dx}{\sqrt{x^2\pm a^2}}=\ln\left|x+\sqrt{x^2\pm a^2}\right|+C,$$
$$\int\frac{\mathrm dx}{x^2-a^2}=\frac1{2a}\ln\left|\frac{x-a}{x+a}\right|+C,\quad\int\sec x\,\mathrm dx=\ln|\sec x+\tan x|+C.$$

## 7.2 第一换元法（凑微分）

**定理 7.2** 若 $\int f(u)\,\mathrm du=F(u)+C$，$u=\varphi(x)$ 可导，则
$$\int f(\varphi(x))\varphi'(x)\,\mathrm dx=F(\varphi(x))+C.$$

证明就是链式法则。其精髓在于**一阶微分形式不变性**：$\varphi'(x)\,\mathrm dx=\mathrm d\varphi(x)$，把 $\varphi'(x)\,\mathrm dx$ "凑"成 $\mathrm du$。

**例 1** 求 $\displaystyle\int\frac{\ln x}{x}\,\mathrm dx$ 和 $\displaystyle\int\tan x\,\mathrm dx$。

**解**：$\dfrac{\mathrm dx}x=\mathrm d(\ln x)$，故 $\int\ln x\,\mathrm d(\ln x)=\frac12\ln^2x+C$。

$\int\tan x\,\mathrm dx=\int\dfrac{\sin x}{\cos x}\mathrm dx=-\int\dfrac{\mathrm d(\cos x)}{\cos x}=-\ln|\cos x|+C$。

常见的凑法：$x\,\mathrm dx=\frac12\mathrm d(x^2)$，$\dfrac{\mathrm dx}{\sqrt x}=2\,\mathrm d\sqrt x$，$\dfrac{\mathrm dx}{1+x^2}=\mathrm d\arctan x$，$e^x\mathrm dx=\mathrm de^x$，$\sin x\,\mathrm dx=-\mathrm d\cos x$。看到被积函数里有"某个函数和它的导数同时出现"，就是凑微分的信号。

## 7.3 第二换元法

**定理 7.3** 设 $x=\psi(t)$ 严格单调可导且 $\psi'(t)\ne0$，若 $\int f(\psi(t))\psi'(t)\,\mathrm dt=G(t)+C$，则
$$\int f(x)\,\mathrm dx=G(\psi^{-1}(x))+C.$$

单调性保证可以**换回** $x$。常用代换：

| 被积函数含 | 代换 | 效果 |
|---|---|---|
| $\sqrt{a^2-x^2}$ | $x=a\sin t$ | $\sqrt{a^2-x^2}=a\cos t$ |
| $\sqrt{a^2+x^2}$ | $x=a\tan t$ | $\sqrt{a^2+x^2}=a\sec t$ |
| $\sqrt{x^2-a^2}$ | $x=a\sec t$ | $\sqrt{x^2-a^2}=a\tan t$ |
| $\sqrt[n]{ax+b}$ | $t=\sqrt[n]{ax+b}$ | 化为有理函数 |

**例 2** 求 $\displaystyle\int\sqrt{a^2-x^2}\,\mathrm dx\ (a>0)$。

**解**：令 $x=a\sin t$，$t\in\left[-\frac\pi2,\frac\pi2\right]$，则 $\cos t\ge0$，$\sqrt{a^2-x^2}=a\cos t$，$\mathrm dx=a\cos t\,\mathrm dt$：
$$\int a^2\cos^2t\,\mathrm dt=\frac{a^2}2\left(t+\sin t\cos t\right)+C=\frac{a^2}2\arcsin\frac xa+\frac x2\sqrt{a^2-x^2}+C.$$

> 换回 $x$ 时画一个直角三角形（斜边 $a$、对边 $x$），$\cos t$、$\tan t$ 一目了然。

## 7.4 分部积分

**定理 7.4** $\displaystyle\int u\,\mathrm dv=uv-\int v\,\mathrm du$。

来自乘积求导法则。关键是**选谁当 $u$**：选求导后变简单的。经验顺序（"反对幂三指"）：反三角函数、对数函数、幂函数、三角函数、指数函数——越靠前越优先当 $u$。

**例 3** 求 $\displaystyle\int x^2e^x\,\mathrm dx$ 与 $\displaystyle\int\arctan x\,\mathrm dx$。

**解**：$\int x^2\,\mathrm de^x=x^2e^x-2\int xe^x\,\mathrm dx=x^2e^x-2(xe^x-e^x)+C=(x^2-2x+2)e^x+C$。

$\int\arctan x\,\mathrm dx=x\arctan x-\int\dfrac{x}{1+x^2}\mathrm dx=x\arctan x-\frac12\ln(1+x^2)+C$。

**例 4（循环型）** 求 $I=\displaystyle\int e^x\sin x\,\mathrm dx$。

**解**：两次分部积分：
$$I=e^x\sin x-\int e^x\cos x\,\mathrm dx=e^x\sin x-e^x\cos x-I,$$
解出 $I=\dfrac{e^x(\sin x-\cos x)}2+C$。

**递推公式**：例如 $I_n=\displaystyle\int\frac{\mathrm dx}{(x^2+a^2)^n}$，分部积分得
$$I_{n+1}=\frac{x}{2na^2(x^2+a^2)^n}+\frac{2n-1}{2na^2}I_n.$$

## 7.5 有理函数的积分

**定理 7.5（部分分式分解）** 任何真分式 $\dfrac{P(x)}{Q(x)}$（$\deg P<\deg Q$）都可以分解为以下两类简单分式之和：
$$\frac{A}{(x-a)^k},\qquad\frac{Bx+C}{(x^2+px+q)^k}\quad(p^2<4q).$$
（这依赖代数基本定理：实系数多项式可分解为一次和不可约二次因式之积。）

于是**有理函数的原函数总是初等函数**，由有理函数、对数和反正切组成。

**例 5** 求 $\displaystyle\int\frac{\mathrm dx}{x(x^2+1)}$。

**解**：设 $\dfrac1{x(x^2+1)}=\dfrac Ax+\dfrac{Bx+C}{x^2+1}$，通分比较系数（或令 $x=0$ 得 $A=1$），得 $A=1,B=-1,C=0$。所以
$$\int\left(\frac1x-\frac x{x^2+1}\right)\mathrm dx=\ln|x|-\frac12\ln(x^2+1)+C.$$

**可化为有理函数的积分**：
- 三角有理式 $R(\sin x,\cos x)$：万能代换 $t=\tan\frac x2$，$\sin x=\dfrac{2t}{1+t^2}$，$\cos x=\dfrac{1-t^2}{1+t^2}$，$\mathrm dx=\dfrac{2\,\mathrm dt}{1+t^2}$。（万能但常常繁琐，能用简单代换就别用它。）
- $R\left(x,\sqrt[n]{\frac{ax+b}{cx+d}}\right)$：令 $t=\sqrt[n]{\frac{ax+b}{cx+d}}$。

## 7.6 拿到一个积分，先问自己什么

求不定积分最怕的是"盯着式子发呆"。下面是一个从简到繁的检查顺序，练习时有意识地走一遍，很快就会形成直觉：

1. **能不能拆？** 先用代数恒等变形把被积函数拆成几项基本积分之和：多项式除法、拆分子（如 $\frac{x^2}{1+x^2}=1-\frac1{1+x^2}$）、三角恒等式（降幂公式 $\sin^2x=\frac{1-\cos2x}2$、积化和差）。
2. **有没有"函数和它的导数"同时出现？** 如 $\frac{f'(x)}{f(x)}$、$f(x)^nf'(x)$、$e^{f(x)}f'(x)$——凑微分，一步到位。
3. **有根号吗？** 二次根式用三角代换，$\sqrt[n]{ax+b}$ 直接令整个根式为 $t$。
4. **是两类不同函数的乘积吗？** 如多项式乘指数、多项式乘三角、对数乘多项式、反三角乘多项式——分部积分，按"反对幂三指"选 $u$。
5. **是有理函数吗？** 先做多项式除法化为真分式，再部分分式分解。
6. **是三角有理式吗？** 先试 $\sin x$ 或 $\cos x$ 或 $\tan x$ 的简单代换：若被积函数关于 $\sin x$ 是奇函数，令 $t=\cos x$；关于 $\cos x$ 是奇函数，令 $t=\sin x$；若 $R(-\sin x,-\cos x)=R(\sin x,\cos x)$，令 $t=\tan x$。都不行再用万能代换。

还有一个心态上的提醒：**同一个积分，用不同方法算出的结果可以"长得不一样"**。例如 $\int\sin x\cos x\,\mathrm dx$ 可以得 $\frac12\sin^2x+C$，也可以得 $-\frac12\cos^2x+C$，还可以得 $-\frac14\cos2x+C$。它们相差常数，都是正确的。判断对错的唯一标准是：**求导后是否回到被积函数**。

## 例题（综合）

**例 6（CMC 风格：巧拆配对）** 求 $\displaystyle I=\int\frac{\sin x}{\sin x+\cos x}\mathrm dx$。

**想法**：单独算很繁，但配一个"伙伴" $J=\displaystyle\int\frac{\cos x}{\sin x+\cos x}\mathrm dx$，则 $I+J$ 和 $J-I$ 都很好算。

**解**：$I+J=\int\mathrm dx=x+C_1$；
$$J-I=\int\frac{\cos x-\sin x}{\sin x+\cos x}\mathrm dx=\int\frac{\mathrm d(\sin x+\cos x)}{\sin x+\cos x}=\ln|\sin x+\cos x|+C_2.$$
所以 $I=\dfrac12\big(x-\ln|\sin x+\cos x|\big)+C$。

**例 7（分部积分抵消）** 求 $\displaystyle\int e^x\frac{1+\sin x}{1+\cos x}\mathrm dx$。

**解**：利用半角公式 $1+\cos x=2\cos^2\frac x2$，$\sin x=2\sin\frac x2\cos\frac x2$：
$$\frac{1+\sin x}{1+\cos x}=\frac1{2\cos^2\frac x2}+\tan\frac x2.$$
注意 $\left(\tan\frac x2\right)'=\dfrac1{2\cos^2\frac x2}$，所以被积函数是 $e^x\big(g(x)+g'(x)\big)$ 的形式，$g=\tan\frac x2$，而 $(e^xg)'=e^x(g+g')$。故原积分 $=e^x\tan\dfrac x2+C$。

> 认出 $e^x(g+g')$ 结构是一个很实用的技巧：$\int e^x\big(g(x)+g'(x)\big)\mathrm dx=e^xg(x)+C$。

## 易错点

1. **漏写 $+C$**，或在不同区间上把 $C$ 当成同一个常数。
2. **$\int\frac{\mathrm dx}x=\ln|x|+C$ 的绝对值不能丢。**
3. **第二换元法需要单调性**，并且要注意根号开出来的符号（例 2 中 $t$ 的范围保证 $\cos t\ge0$）。
4. **分部积分选错 $u$ 会越算越复杂。** 一次分部之后如果积分变难了，换一种选法。
5. **验算**：求出原函数后求导检查，是最可靠的防错方式。
6. **不是所有初等函数都有初等原函数**：$\int e^{-x^2}\mathrm dx$、$\int\frac{\sin x}x\mathrm dx$、$\int\frac{\mathrm dx}{\ln x}$ 都"积不出来"。遇到它们，题目通常要你换个角度（如定积分技巧、二重积分）。

## 自测清单

1. 求 $\int\dfrac{\mathrm dx}{x\ln x}$。
2. 求 $\int x\cos x\,\mathrm dx$。
3. 求 $\int\dfrac{\mathrm dx}{\sqrt{x}(1+x)}$。
4. 求 $\int\dfrac{x+1}{x^2-x-2}\mathrm dx$。
5. 求 $\int\ln x\,\mathrm dx$。
6. 有第一类间断点的函数为什么没有原函数？
7. 求 $\int\dfrac{\mathrm dx}{\sqrt{x^2+4}}$。

## 参考答案

### 点击展开参考答案

1. $\int\frac{\mathrm d\ln x}{\ln x}=\ln|\ln x|+C$。
2. $x\sin x+\cos x+C$。
3. 令 $t=\sqrt x$，得 $2\int\frac{\mathrm dt}{1+t^2}=2\arctan\sqrt x+C$。
4. $x^2-x-2=(x-2)(x+1)$，所以被积函数 $=\frac1{x-2}$（$x\ne-1$），积分为 $\ln|x-2|+C$。
5. $x\ln x-x+C$。
6. 若 $F'=f$，由 Darboux 定理 $f$ 有介值性，而在跳跃间断点附近介值性被破坏；可去间断点情形则由导数极限定理（若 $\lim_{x\to x_0}F'(x)$ 存在则等于 $F'(x_0)$）矛盾。
7. $\ln\left(x+\sqrt{x^2+4}\right)+C$。


