---
title: 导数与微分
subject: 数学分析
chapter: 5
prerequisites: 函数的连续性
minutes: 60
---

# 第 5 章　导数与微分

## 为什么要学

一辆车从 $t=0$ 出发，位置是 $s(t)$。"$t_0$ 时刻的速度"是什么意思？我们能算的只有平均速度 $\dfrac{s(t)-s(t_0)}{t-t_0}$——时间间隔越短，平均速度越能代表那一瞬间。让间隔趋于零，平均速度的极限就是瞬时速度。

几何上也是一样：曲线上两点连一条割线，让一点沿曲线滑向另一点，割线转动到的极限位置就是切线，斜率就是导数。

导数还有一个更深的含义：**局部线性化**。在 $x_0$ 附近把曲线放大再放大，可导的曲线会越来越像一条直线。微分 $\mathrm dy=f'(x_0)\,\mathrm dx$ 就是这条直线给出的近似——"用线性函数代替复杂函数，误差是比 $\Delta x$ 更高阶的无穷小"。这个观点在多元微分学（第 14 章）中会变成主角。

## 5.1 导数的定义

**定义 5.1** 设 $f$ 在 $x_0$ 的某邻域内有定义。若极限
$$f'(x_0)=\lim_{\Delta x\to0}\frac{f(x_0+\Delta x)-f(x_0)}{\Delta x}=\lim_{x\to x_0}\frac{f(x)-f(x_0)}{x-x_0}$$
存在（有限），称 $f$ 在 $x_0$ **可导**，此极限为 $f$ 在 $x_0$ 的**导数**。

**单侧导数**：$f'_+(x_0)=\lim\limits_{\Delta x\to0^+}\dfrac{f(x_0+\Delta x)-f(x_0)}{\Delta x}$，$f'_-(x_0)$ 类似。$f$ 在 $x_0$ 可导 $\iff f'_+(x_0)=f'_-(x_0)$ 且都存在。

**定理 5.1** 可导必连续，反之不然。

*证明*：$f(x)-f(x_0)=\dfrac{f(x)-f(x_0)}{x-x_0}\cdot(x-x_0)\to f'(x_0)\cdot0=0$。反例：$|x|$ 在 $0$ 连续，但 $f'_+(0)=1\ne-1=f'_-(0)$。$\blacksquare$

**例 1** 讨论 $f(x)=\begin{cases}x^2\sin\frac1x,&x\ne0\\0,&x=0\end{cases}$ 在 $0$ 处的可导性及 $f'$ 的连续性。

**解**：$\dfrac{f(x)-f(0)}{x}=x\sin\frac1x\to0$，所以 $f'(0)=0$。当 $x\ne0$ 时
$$f'(x)=2x\sin\frac1x-\cos\frac1x,$$
$x\to0$ 时第一项趋于 $0$，第二项极限不存在，所以 $\lim\limits_{x\to0}f'(x)$ 不存在。结论：$f$ **处处可导，但导函数在 $0$ 不连续**（第二类间断）。

> 这个例子要记住。它说明：在分段点求导数，**必须回到定义**，不能用"先求导再取极限"代替。

## 5.2 求导法则

**四则运算**：$(u\pm v)'=u'\pm v'$，$(uv)'=u'v+uv'$，$\left(\dfrac uv\right)'=\dfrac{u'v-uv'}{v^2}$。

**乘积法则的证明**（又是"加一项减一项"）：
$$\frac{u(x+h)v(x+h)-u(x)v(x)}{h}=\frac{u(x+h)-u(x)}{h}v(x+h)+u(x)\frac{v(x+h)-v(x)}{h},$$
令 $h\to0$，并用到 $v$ 连续（可导必连续）。

**链式法则**：若 $u=g(x)$ 在 $x$ 可导，$y=f(u)$ 在 $u=g(x)$ 可导，则 $(f\circ g)'(x)=f'(g(x))\,g'(x)$。

**证明要点**：朴素的写法 $\dfrac{\Delta y}{\Delta x}=\dfrac{\Delta y}{\Delta u}\cdot\dfrac{\Delta u}{\Delta x}$ 有漏洞——$\Delta u$ 可能等于 $0$。修补办法：定义
$$\varphi(u)=\begin{cases}\dfrac{f(u)-f(u_0)}{u-u_0},&u\ne u_0\\f'(u_0),&u=u_0\end{cases}$$
则 $\varphi$ 在 $u_0$ 连续，且 $f(u)-f(u_0)=\varphi(u)(u-u_0)$ **对所有 $u$ 成立**。于是
$$\frac{f(g(x))-f(g(x_0))}{x-x_0}=\varphi(g(x))\cdot\frac{g(x)-g(x_0)}{x-x_0}\to f'(u_0)g'(x_0).\qquad\blacksquare$$

**反函数求导**：若 $x=\varphi(y)$ 严格单调连续，在 $y_0$ 可导且 $\varphi'(y_0)\ne0$，则其反函数 $y=f(x)$ 在 $x_0=\varphi(y_0)$ 可导，且 $f'(x_0)=\dfrac1{\varphi'(y_0)}$。例如 $(\arcsin x)'=\dfrac1{\cos y}=\dfrac1{\sqrt{1-x^2}}$。

**基本导数表**（必须熟记）：
$$(x^\alpha)'=\alpha x^{\alpha-1},\quad(a^x)'=a^x\ln a,\quad(\log_ax)'=\frac1{x\ln a},$$
$$(\sin x)'=\cos x,\quad(\cos x)'=-\sin x,\quad(\tan x)'=\sec^2x,$$
$$(\arcsin x)'=\frac1{\sqrt{1-x^2}},\quad(\arctan x)'=\frac1{1+x^2}.$$

**参数方程**：$x=x(t),\ y=y(t)$，则 $\dfrac{\mathrm dy}{\mathrm dx}=\dfrac{y'(t)}{x'(t)}$，二阶导 $\dfrac{\mathrm d^2y}{\mathrm dx^2}=\dfrac{\mathrm d}{\mathrm dt}\left(\dfrac{y'}{x'}\right)\Big/x'(t)=\dfrac{y''x'-y'x''}{x'^3}$。

**隐函数**：对方程两边关于 $x$ 求导，把 $y$ 看作 $x$ 的函数。

**对数求导法**：对 $y=u(x)^{v(x)}$ 或多个因子的乘积，先取对数再求导。

## 5.3 高阶导数与 Leibniz 公式

$f''=(f')'$，依此类推。常用的 $n$ 阶导数：
$$(\sin x)^{(n)}=\sin\left(x+\frac{n\pi}2\right),\quad(\cos x)^{(n)}=\cos\left(x+\frac{n\pi}2\right),$$
$$\left(\frac1{x+a}\right)^{(n)}=\frac{(-1)^nn!}{(x+a)^{n+1}},\quad(e^{ax})^{(n)}=a^ne^{ax}.$$

**Leibniz 公式**：$(uv)^{(n)}=\displaystyle\sum_{k=0}^n\binom nku^{(k)}v^{(n-k)}$。

和二项式定理形式完全一样，用数学归纳法证明（利用 $\binom nk+\binom n{k-1}=\binom{n+1}k$）。

**例 2** 求 $y=x^2e^{3x}$ 的 $n$ 阶导数。

**解**：取 $u=e^{3x}$，$v=x^2$，$v$ 的三阶及以上导数为 $0$，只剩三项：
$$y^{(n)}=3^ne^{3x}x^2+\binom n1 3^{n-1}e^{3x}\cdot2x+\binom n23^{n-2}e^{3x}\cdot2=3^{n-2}e^{3x}\big(9x^2+6nx+n(n-1)\big).$$

## 5.4 微分

**定义 5.2** 若存在常数 $A$ 使
$$f(x_0+\Delta x)-f(x_0)=A\Delta x+o(\Delta x)\quad(\Delta x\to0),$$
称 $f$ 在 $x_0$ **可微**，$A\Delta x$ 称为**微分**，记 $\mathrm dy=A\Delta x$。

**定理 5.2** 一元函数可微 $\iff$ 可导，且 $A=f'(x_0)$。

*证明*：可微时，两边除以 $\Delta x$ 再取极限得 $A=f'(x_0)$；可导时令 $o(\Delta x)=\Delta y-f'(x_0)\Delta x$，验证它除以 $\Delta x$ 趋于 $0$。$\blacksquare$

对自变量本身，$\mathrm dx=\Delta x$，所以 $\mathrm dy=f'(x)\,\mathrm dx$，导数就是"微分之商" $\dfrac{\mathrm dy}{\mathrm dx}$。

**一阶微分形式不变性**：无论 $u$ 是自变量还是中间变量，$\mathrm d f(u)=f'(u)\,\mathrm du$ 都成立。这是换元积分法的理论依据（第 7 章）。注意**二阶微分没有形式不变性**。

**近似计算**：$f(x_0+\Delta x)\approx f(x_0)+f'(x_0)\Delta x$。例如 $\sqrt{1.02}\approx1+\frac12\times0.02=1.01$。

> 在一元情形，"可导"和"可微"是一回事；但到了多元函数，"偏导数存在"和"可微"就大不相同了（第 14 章）。现在用定义 5.2 的形式去理解微分，将来会轻松很多。

## 5.5 导数的几何意义与应用小结

**切线与法线**：曲线 $y=f(x)$ 在点 $(x_0,f(x_0))$ 处的切线方程为
$$y-f(x_0)=f'(x_0)(x-x_0),$$
法线（与切线垂直）方程为 $y-f(x_0)=-\dfrac1{f'(x_0)}(x-x_0)$（$f'(x_0)\ne0$ 时）。若 $f'(x_0)=\pm\infty$（例如 $\sqrt[3]x$ 在 $0$），切线是竖直线 $x=x_0$——此时函数**不可导**，但曲线仍有切线。

**相关变化率**：若两个量 $x(t),y(t)$ 由某个关系 $F(x,y)=0$ 联系，对 $t$ 求导就得到两个变化率之间的关系。例如气球体积 $V=\frac43\pi r^3$，若 $V$ 以恒定速率 $c$ 增加，则 $\frac{\mathrm dV}{\mathrm dt}=4\pi r^2\frac{\mathrm dr}{\mathrm dt}=c$，半径增长速率 $\frac{\mathrm dr}{\mathrm dt}=\frac{c}{4\pi r^2}$ 随半径增大而减慢。

**一个思维方式的转变**：高中把导数当作"一套求导公式"，大学则把它当作"一个极限"。遇到下面几种情况，请自动切换回定义：
- 分段函数的分段点；
- 只知道 $f$ 在某一点可导、不知道在其附近是否可导（这时**不能**用 L'Hospital 法则或中值定理）；
- 抽象函数满足某个函数方程，如 $f(x+y)=f(x)+f(y)+2xy$，要求 $f'(x)$。

最后一种情况的做法：先令 $x=y=0$ 得 $f(0)=0$；再写
$$\frac{f(x+h)-f(x)}h=\frac{f(h)+2xh}{h}=\frac{f(h)-f(0)}h+2x\to f'(0)+2x,$$
所以只要 $f$ 在 $0$ 可导，它就处处可导，且 $f'(x)=f'(0)+2x$。

## 更多例题

**例 3（定义求导）** 设 $f(x)=(x-1)(x-2)\cdots(x-100)$，求 $f'(1)$。

**解**：直接展开不可行，用定义：
$$f'(1)=\lim_{x\to1}\frac{f(x)-0}{x-1}=\lim_{x\to1}(x-2)\cdots(x-100)=(-1)(-2)\cdots(-99)=-99!.$$

**例 4（参数方程）** 摆线 $x=a(t-\sin t)$，$y=a(1-\cos t)$。求 $\dfrac{\mathrm d^2y}{\mathrm dx^2}$。

**解**：$\dfrac{\mathrm dy}{\mathrm dx}=\dfrac{a\sin t}{a(1-\cos t)}=\cot\dfrac t2$。
$$\frac{\mathrm d^2y}{\mathrm dx^2}=\frac{\frac{\mathrm d}{\mathrm dt}\cot\frac t2}{\frac{\mathrm dx}{\mathrm dt}}=\frac{-\frac12\csc^2\frac t2}{a(1-\cos t)}=\frac{-\frac12\csc^2\frac t2}{2a\sin^2\frac t2}=-\frac{1}{4a\sin^4\frac t2}.$$

**例 5（高阶导数在一点的值）** 设 $f(x)=\arctan x$，求 $f^{(n)}(0)$。

**解**：$f'(x)=\dfrac1{1+x^2}$，即 $(1+x^2)f'(x)=1$。两边求 $n$ 阶导（$n\ge1$），用 Leibniz 公式：
$$(1+x^2)f^{(n+1)}(x)+2nxf^{(n)}(x)+n(n-1)f^{(n-1)}(x)=0.$$
令 $x=0$：$f^{(n+1)}(0)=-n(n-1)f^{(n-1)}(0)$。由 $f(0)=0$，$f'(0)=1$ 得：偶数阶为 $0$；$f^{(2k+1)}(0)=(-1)^k(2k)!$。

> 这种"先建立微分方程、再用 Leibniz 公式得递推"的方法是 CMC 求高阶导数的常见套路。也可以用幂级数：$\arctan x=\sum\frac{(-1)^kx^{2k+1}}{2k+1}$，比较系数 $\frac{f^{(2k+1)}(0)}{(2k+1)!}=\frac{(-1)^k}{2k+1}$，结果一致。

**例 6（CMC 风格：用导数定义处理极限）** 设 $f$ 在 $x=0$ 处可导，$f(0)=0$。求
$$\lim_{n\to\infty}\sum_{k=1}^nf\left(\frac{k}{n^2}\right).$$

**想法**：$f$ 在 $0$ 可导，意味着 $f(h)=f'(0)h+o(h)$。每个 $\frac k{n^2}$ 都很小，所以 $f\left(\frac k{n^2}\right)\approx f'(0)\frac k{n^2}$，求和得 $f'(0)\frac{n(n+1)}{2n^2}\to\frac{f'(0)}2$。下面严格化"$\approx$"。

**证明**：令 $\varepsilon(h)=\dfrac{f(h)}h-f'(0)$（$h\ne0$），则 $\varepsilon(h)\to0$。任给 $\eta>0$，存在 $\delta>0$ 使 $0<h<\delta$ 时 $|\varepsilon(h)|<\eta$。当 $n>\frac1\delta$ 时，所有 $\frac k{n^2}\le\frac1n<\delta$，于是
$$\left|\sum_{k=1}^nf\left(\frac k{n^2}\right)-f'(0)\sum_{k=1}^n\frac k{n^2}\right|\le\sum_{k=1}^n\frac k{n^2}\left|\varepsilon\left(\frac k{n^2}\right)\right|\le\eta\cdot\frac{n+1}{2n}\le\eta.$$
而 $f'(0)\sum\frac k{n^2}=f'(0)\frac{n+1}{2n}\to\frac{f'(0)}2$。所以极限为 $\dfrac{f'(0)}2$。$\blacksquare$

> 关键点：**误差 $\varepsilon(h)$ 要对所有 $k$ 同时小**——因为所有 $\frac k{n^2}$ 都落在 $(0,\frac1n]$ 内，一个 $\delta$ 就够了。这是"一致"思想的萌芽。

## 易错点

1. **分段函数在分段点必须用定义求导。** 例 1 说明导函数在该点可能没有极限，但导数存在。
2. **$f'(x_0)$ 存在 ≠ $f$ 在 $x_0$ 附近可导。** 例如 $f(x)=x^2D(x)$（$D$ 为 Dirichlet 函数）只在 $0$ 可导。
3. **$f'(x_0)$ 是一个数，$[f(x_0)]'$ 是 $0$。** 先求导再代值，别先代值再求导。
4. **链式法则别漏掉内层导数**：$(\sin x^2)'=2x\cos x^2$。
5. **参数方程的二阶导不是 $\dfrac{y''(t)}{x''(t)}$。** 正确公式见 5.2。
6. **可导 ⇒ 连续，连续 ⇏ 可导。** 甚至存在处处连续处处不可导的函数（Weierstrass 函数）。

## 自测清单

1. 写出 $f'_-(x_0)$ 的定义。
2. $f(x)=|x|^3$ 在 $0$ 处可导吗？二阶导数呢？
3. 若 $f$ 在 $x_0$ 可导，求 $\lim\limits_{h\to0}\dfrac{f(x_0+2h)-f(x_0-h)}{h}$。
4. 反过来，若上式极限存在，$f$ 一定在 $x_0$ 可导吗？
5. 求 $y=x^x\ (x>0)$ 的导数。
6. 用 Leibniz 公式求 $(x\sin x)^{(10)}$。
7. 一元函数"可微"与"可导"是什么关系？

## 参考答案

### 点击展开参考答案

1. $f'_-(x_0)=\lim\limits_{\Delta x\to0^-}\dfrac{f(x_0+\Delta x)-f(x_0)}{\Delta x}$。
2. $f'(0)=\lim\frac{|h|^3}{h}=0$；$f'(x)=3x|x|$，$f''(0)=\lim\frac{3h|h|}{h}=0$，都存在（三阶导数在 $0$ 不存在）。
3. 拆成 $2\cdot\frac{f(x_0+2h)-f(x_0)}{2h}+\frac{f(x_0-h)-f(x_0)}{-h}\to3f'(x_0)$。
4. 不一定。式子里根本没出现 $f(x_0)$。例如 $x_0=0$，$f(0)=0$，$f(x)=1\ (x\ne0)$：$\frac{f(2h)-f(-h)}{h}=0\to0$，但 $f$ 在 $0$ 都不连续，更不可导。类似地，$|x|$ 在 $0$ 的对称差商 $\frac{|h|-|-h|}{2h}=0$ 有极限，但 $|x|$ 在 $0$ 不可导。这类"变形差商"只有在已知可导时才能化为导数。
5. $y=e^{x\ln x}$，$y'=x^x(\ln x+1)$。
6. $u=\sin x$，$v=x$：$x\sin\left(x+5\pi\right)+10\sin\left(x+\frac{9\pi}{2}\right)=-x\sin x+10\cos x$。
7. 等价，且微分 $\mathrm dy=f'(x_0)\,\mathrm dx$。


