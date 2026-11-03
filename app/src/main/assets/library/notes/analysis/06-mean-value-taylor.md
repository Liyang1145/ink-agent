---
title: 微分中值定理与Taylor公式
subject: 数学分析
chapter: 6
prerequisites: 导数与微分
minutes: 75
---

# 第 6 章　微分中值定理与 Taylor 公式

## 为什么要学

导数是一个**局部**概念：$f'(x_0)$ 只反映 $x_0$ 附近无穷小范围的信息。可我们关心的往往是**整体**的问题：函数在区间上是增还是减？两点的函数值差多少？方程有几个根？

中值定理就是"从局部到整体"的桥梁。它的直觉非常朴素：你开车从 A 地到 B 地，平均时速 60 公里，那么**途中一定有某个时刻**，速度表恰好指向 60。用图像说：连接曲线两端的弦，一定平行于曲线上某点的切线。

Taylor 公式则把"局部线性化"升级为"局部多项式化"：用 $n$ 次多项式逼近函数，并且**精确地控制误差**。它是计算极限、证明不等式、估计误差的万能工具，也是 CMC 中出现频率最高的技术之一。

## 6.1 Fermat 引理与 Rolle 定理

**引理 6.1（Fermat）** 若 $f$ 在 $x_0$ 取局部极值，且 $f'(x_0)$ 存在，则 $f'(x_0)=0$。

**证明**：设为局部极大。当 $x\to x_0^+$ 时 $\dfrac{f(x)-f(x_0)}{x-x_0}\le0$，故 $f'_+(x_0)\le0$；当 $x\to x_0^-$ 时该商 $\ge0$，故 $f'_-(x_0)\ge0$。两者相等，只能为 $0$。$\blacksquare$

**定理 6.2（Rolle）** 若 $f\in C[a,b]$，在 $(a,b)$ 内可导，且 $f(a)=f(b)$，则存在 $\xi\in(a,b)$ 使 $f'(\xi)=0$。

**证明**：由最值定理，$f$ 在 $[a,b]$ 上取到最大值 $M$ 和最小值 $m$。若 $M=m$，$f$ 为常数，任取 $\xi$。否则 $M,m$ 至少有一个不等于端点值 $f(a)=f(b)$，它在某内点 $\xi$ 处取到，由 Fermat 引理 $f'(\xi)=0$。$\blacksquare$

> 看看这个证明用了什么：闭区间上连续 → 最值定理 → 实数完备性。中值定理不是"显然"的，它深深依赖实数没有缝隙。

## 6.2 Lagrange 与 Cauchy 中值定理

**定理 6.3（Lagrange）** 若 $f\in C[a,b]$，在 $(a,b)$ 内可导，则存在 $\xi\in(a,b)$ 使
$$f(b)-f(a)=f'(\xi)(b-a).$$

**想法**：把曲线"扳平"——减去弦所在的直线，就变成两端等高的情形，套用 Rolle 定理。

**证明**：令 $\varphi(x)=f(x)-f(a)-\dfrac{f(b)-f(a)}{b-a}(x-a)$，则 $\varphi(a)=\varphi(b)=0$。由 Rolle 定理有 $\xi$ 使 $\varphi'(\xi)=f'(\xi)-\dfrac{f(b)-f(a)}{b-a}=0$。$\blacksquare$

**重要推论**：
1. 若在区间 $I$ 上 $f'\equiv0$，则 $f$ 为常数。
2. 若 $f'>0$（$\ge0$），则 $f$ 严格递增（递增）。
3. 若 $|f'|\le L$，则 $|f(x)-f(y)|\le L|x-y|$（Lipschitz 条件），从而一致连续。

**定理 6.4（Cauchy 中值定理）** 若 $f,g\in C[a,b]$，在 $(a,b)$ 内可导，且 $g'(x)\ne0$，则存在 $\xi\in(a,b)$ 使
$$\frac{f(b)-f(a)}{g(b)-g(a)}=\frac{f'(\xi)}{g'(\xi)}.$$

*证明*：令 $\varphi(x)=f(x)-\dfrac{f(b)-f(a)}{g(b)-g(a)}g(x)$，验证 $\varphi(a)=\varphi(b)$，用 Rolle 定理。（$g(b)\ne g(a)$ 由 Rolle 定理和 $g'\ne0$ 保证。）

> 注意：**不能**对 $f$ 和 $g$ 分别用 Lagrange 定理再相除，因为两次得到的 $\xi$ 一般不同。

**定理 6.5（Darboux，导函数的介值性）** 若 $f$ 在 $[a,b]$ 上可导（端点为单侧导数），且 $f'(a)<c<f'(b)$，则存在 $\xi\in(a,b)$ 使 $f'(\xi)=c$。

*证明要点*：令 $g(x)=f(x)-cx$，则 $g'(a)<0<g'(b)$，说明 $g$ 在 $a$ 右侧先下降、在 $b$ 左侧是上升而来，所以 $g$ 在 $[a,b]$ 上的最小值不在端点，取在某内点 $\xi$，由 Fermat 引理 $g'(\xi)=0$。

推论：导函数**没有第一类间断点**。例如 $\operatorname{sgn}x$ 不可能是任何函数的导函数。

## 6.3 L'Hospital 法则

**定理 6.6（$\frac00$ 型）** 设 $\lim\limits_{x\to x_0^+}f(x)=\lim\limits_{x\to x_0^+}g(x)=0$，$f,g$ 在 $(x_0,x_0+\delta)$ 可导且 $g'\ne0$。若 $\lim\limits_{x\to x_0^+}\dfrac{f'(x)}{g'(x)}=A$（$A$ 可为 $\pm\infty$），则 $\lim\limits_{x\to x_0^+}\dfrac{f(x)}{g(x)}=A$。

*证明*：补充定义 $f(x_0)=g(x_0)=0$，对 $[x_0,x]$ 用 Cauchy 中值定理：$\dfrac{f(x)}{g(x)}=\dfrac{f'(\xi_x)}{g'(\xi_x)}$，$\xi_x\in(x_0,x)$。当 $x\to x_0^+$ 时 $\xi_x\to x_0^+$。$\blacksquare$

对 $\frac\infty\infty$ 型（只需分母趋于无穷）、$x\to\infty$ 也有类似结论。

**使用注意**：
- 条件是"导数之比的极限存在"。若 $\lim\frac{f'}{g'}$ 不存在，**不能断定**原极限不存在。例：$\lim\limits_{x\to\infty}\dfrac{x+\sin x}{x}=1$，但 $\dfrac{1+\cos x}{1}$ 无极限。
- 每用一次前都要检查是否仍是不定型。

## 6.4 Taylor 公式

**想法**：用一次函数逼近，误差是 $o(x-x_0)$；能否用 $n$ 次多项式逼近，让误差变成 $o((x-x_0)^n)$？自然的选择是让多项式在 $x_0$ 处与 $f$ 的各阶导数都相同：
$$T_n(x)=\sum_{k=0}^n\frac{f^{(k)}(x_0)}{k!}(x-x_0)^k.$$

**定理 6.7（带 Peano 余项）** 若 $f$ 在 $x_0$ 处 $n$ 阶可导，则
$$f(x)=T_n(x)+o\big((x-x_0)^n\big)\quad(x\to x_0).$$

*证明*：令 $R(x)=f(x)-T_n(x)$，则 $R(x_0)=R'(x_0)=\cdots=R^{(n)}(x_0)=0$。对 $\dfrac{R(x)}{(x-x_0)^n}$ 连续用 $n-1$ 次 L'Hospital 法则，得 $\dfrac{R^{(n-1)}(x)}{n!(x-x_0)}$，再由 $R^{(n)}(x_0)$ 的**定义**（而不是再用一次 L'Hospital，因为不知道 $R^{(n)}$ 在附近存在）得极限为 $\dfrac{R^{(n)}(x_0)}{n!}=0$。$\blacksquare$

**定理 6.8（带 Lagrange 余项）** 若 $f$ 在含 $x_0$ 的区间上 $n+1$ 阶可导，则对区间中任一 $x$，存在介于 $x_0$ 与 $x$ 之间的 $\xi$ 使
$$f(x)=T_n(x)+\frac{f^{(n+1)}(\xi)}{(n+1)!}(x-x_0)^{n+1}.$$

*证明*：对 $R(t)$ 与 $(t-x_0)^{n+1}$ 在 $[x_0,x]$ 上反复用 Cauchy 中值定理 $n+1$ 次。

两种余项的用途不同：**Peano 余项用于求极限（定性，局部）；Lagrange 余项用于估计误差、证明不等式（定量，整体）。**

**常用 Maclaurin 展开**（$x\to0$），务必熟记：
$$e^x=1+x+\frac{x^2}{2!}+\cdots+\frac{x^n}{n!}+o(x^n),$$
$$\sin x=x-\frac{x^3}{3!}+\frac{x^5}{5!}-\cdots+(-1)^{m}\frac{x^{2m+1}}{(2m+1)!}+o(x^{2m+2}),$$
$$\cos x=1-\frac{x^2}{2!}+\frac{x^4}{4!}-\cdots+(-1)^m\frac{x^{2m}}{(2m)!}+o(x^{2m+1}),$$
$$\ln(1+x)=x-\frac{x^2}2+\frac{x^3}3-\cdots+(-1)^{n-1}\frac{x^n}n+o(x^n),$$
$$(1+x)^\alpha=1+\alpha x+\frac{\alpha(\alpha-1)}{2!}x^2+\cdots+\binom\alpha nx^n+o(x^n).$$

## 6.5 函数性态：单调、极值、凸性

- **极值的充分条件**：若 $f'(x_0)=0$，$f''(x_0)>0$，则 $x_0$ 为严格极小点（由 Taylor 公式 $f(x)-f(x_0)=\frac{f''(x_0)}2(x-x_0)^2+o((x-x_0)^2)$）。
- **凸函数**：称 $f$ 在 $I$ 上凸（下凸），若对任意 $x_1,x_2\in I$，$\lambda\in[0,1]$，
$$f(\lambda x_1+(1-\lambda)x_2)\le\lambda f(x_1)+(1-\lambda)f(x_2).$$
若 $f''\ge0$，则 $f$ 凸。凸函数满足 **Jensen 不等式** $f\left(\sum\lambda_ix_i\right)\le\sum\lambda_if(x_i)$。
- 凸函数的图像在任一切线的上方：$f(x)\ge f(x_0)+f'(x_0)(x-x_0)$。

## 例题

**例 1（Lagrange 证不等式）** 证明：当 $x>0$ 时，$\dfrac x{1+x}<\ln(1+x)<x$。

**证明**：对 $\ln t$ 在 $[1,1+x]$ 上用 Lagrange 定理：$\ln(1+x)-\ln1=\dfrac1\xi\cdot x$，$1<\xi<1+x$，所以 $\dfrac x{1+x}<\dfrac x\xi<x$。$\blacksquare$

**例 2（Taylor 求极限）** 求 $\displaystyle\lim_{x\to0}\frac{\cos x-e^{-x^2/2}}{x^4}$。

**解**：分母是 $x^4$，所以分子展开到 $x^4$：
$$\cos x=1-\frac{x^2}2+\frac{x^4}{24}+o(x^4),\qquad e^{-x^2/2}=1-\frac{x^2}2+\frac{x^4}8+o(x^4).$$
分子 $=\left(\frac1{24}-\frac18\right)x^4+o(x^4)=-\frac{x^4}{12}+o(x^4)$，极限为 $-\dfrac1{12}$。

> 展开到几阶？**"上下同阶"原则**：分母是 $x^k$，分子就展开到 $x^k$ 项。

**例 3（构造辅助函数）** 设 $f\in C[0,1]$，在 $(0,1)$ 内可导，$f(0)=f(1)=0$。证明存在 $\xi\in(0,1)$ 使 $f'(\xi)+2f(\xi)=0$。

**想法**：$f'+2f=0$ 让人想到 $\left(e^{2x}f\right)'=e^{2x}(f'+2f)$。

**证明**：令 $F(x)=e^{2x}f(x)$，$F(0)=F(1)=0$，由 Rolle 定理有 $\xi$ 使 $F'(\xi)=e^{2\xi}(f'(\xi)+2f(\xi))=0$。$\blacksquare$

> 一般规律：要证 $f'(\xi)+g'(\xi)f(\xi)=0$，令 $F=e^{g(x)}f(x)$。这种"把结论积分回去"的思路是构造辅助函数的核心。

**例 4（双中值）** 设 $f\in C[a,b]$，在 $(a,b)$ 内可导，$0<a<b$。证明存在 $\xi,\eta\in(a,b)$ 使 $f'(\xi)=\dfrac{a+b}{2\eta}f'(\eta)$。

**证明**：对 $f$ 用 Lagrange：$f(b)-f(a)=f'(\xi)(b-a)$。对 $f$ 与 $g(x)=x^2$ 用 Cauchy：$\dfrac{f(b)-f(a)}{b^2-a^2}=\dfrac{f'(\eta)}{2\eta}$。两式联立消去 $f(b)-f(a)$：$f'(\xi)(b-a)=\dfrac{(b^2-a^2)f'(\eta)}{2\eta}$，即得。$\blacksquare$

> 双中值题的口诀：**$\xi,\eta$ 分开找，各用一次中值定理，再拼起来。**

**例 5（Lagrange 余项估计）** 设 $f$ 在 $[0,1]$ 上二阶可导，$f(0)=f(1)=0$，$|f''(x)|\le A$。证明 $|f'(x)|\le\dfrac A2$ 对 $x\in[0,1]$ 成立。

**证明**：固定 $x\in[0,1]$，在 $x$ 处展开到一阶并带 Lagrange 余项，分别代入 $t=0$ 和 $t=1$：
$$0=f(0)=f(x)-f'(x)x+\frac{f''(\xi_1)}2x^2,\qquad0=f(1)=f(x)+f'(x)(1-x)+\frac{f''(\xi_2)}2(1-x)^2.$$
相减得 $f'(x)=\dfrac{f''(\xi_1)}2x^2-\dfrac{f''(\xi_2)}2(1-x)^2$，所以
$$|f'(x)|\le\frac A2\big(x^2+(1-x)^2\big)\le\frac A2,$$
因为在 $[0,1]$ 上 $x^2+(1-x)^2\le1$。$\blacksquare$

**例 6（CMC 风格）** 设 $f$ 在 $\mathbb R$ 上二阶可导，$f$ 有界，且 $f''(x)\ge0$ 对一切 $x$ 成立。证明 $f$ 为常数。

**证明**：由 $f''\ge0$，$f$ 凸，其图像在任一切线上方：$f(x)\ge f(x_0)+f'(x_0)(x-x_0)$。若某点 $f'(x_0)>0$，令 $x\to+\infty$，右边趋于 $+\infty$，与 $f$ 有界矛盾；若 $f'(x_0)<0$，令 $x\to-\infty$ 同样矛盾。所以 $f'\equiv0$，$f$ 为常数。$\blacksquare$

（切线不等式本身可由 Taylor 公式得到：$f(x)=f(x_0)+f'(x_0)(x-x_0)+\frac{f''(\xi)}2(x-x_0)^2\ge f(x_0)+f'(x_0)(x-x_0)$。）

## 易错点

1. **中值定理的条件要逐条验证**：闭区间连续、开区间可导。$|x|$ 在 $[-1,1]$ 上不满足 Rolle 定理（$0$ 处不可导），结论也确实不成立。
2. **$\xi$ 依赖于区间端点。** 在 $[x_0,x]$ 上用中值定理得到的 $\xi$ 是 $x$ 的函数，不一定连续，更不能随意"令 $x\to x_0$ 后 $\xi$ 的行为"做额外假设（只知道 $\xi\to x_0$）。
3. **L'Hospital 法则只是充分条件**，且每次使用前确认是不定型。
4. **Taylor 展开阶数不够会得出错误结论**，阶数过高只是多费力气。加减时必须展开到足够阶使主项不被消掉。
5. **Peano 余项的 Taylor 公式只需在一点 $n$ 阶可导**；Lagrange 余项需要在区间上 $n+1$ 阶可导。
6. **$f'(x_0)=0$ 不一定是极值点**（$x^3$ 在 $0$）；**极值点也不一定 $f'(x_0)=0$**（$|x|$ 在 $0$，导数不存在）。

## 自测清单

1. Rolle 定理的三个条件是什么？去掉任何一个能否举出反例？
2. 用 Lagrange 定理证明 $|\sin x-\sin y|\le|x-y|$。
3. 求 $\lim\limits_{x\to0}\dfrac{x-\sin x}{x^3}$。
4. 写出 $\tan x$ 的 Maclaurin 展开到 $x^3$ 项。
5. $\lim\limits_{x\to0}\dfrac{x^2\sin\frac1x}{\sin x}$ 能用 L'Hospital 法则吗？极限是多少？
6. 证明：若 $f'(x)=f(x)$ 对一切 $x$ 成立，$f(0)=1$，则 $f(x)=e^x$。
7. 为什么 $\operatorname{sgn}x$ 不是任何函数的导函数？

## 参考答案

### 点击展开参考答案

1. 闭区间连续、开区间可导、端点值相等。反例分别：$f(x)=x\ (0\le x<1),\ f(1)=0$；$|x|$ 在 $[-1,1]$；$f(x)=x$ 在 $[0,1]$。
2. $\sin x-\sin y=\cos\xi\cdot(x-y)$，$|\cos\xi|\le1$。
3. $\sin x=x-\frac{x^3}6+o(x^3)$，极限为 $\frac16$。
4. $\tan x=x+\frac{x^3}3+o(x^3)$。
5. 不能：分子分母求导后 $\frac{2x\sin\frac1x-\cos\frac1x}{\cos x}$ 极限不存在。但原极限 $=\lim\frac{x}{\sin x}\cdot x\sin\frac1x=1\cdot0=0$。
6. 令 $g(x)=e^{-x}f(x)$，$g'=e^{-x}(f'-f)=0$，故 $g\equiv g(0)=1$。
7. 由 Darboux 定理，导函数有介值性，而 $\operatorname{sgn}x$ 在 $[-1,1]$ 上取不到 $\frac12$。


