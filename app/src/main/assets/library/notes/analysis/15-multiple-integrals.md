---
title: 重积分
subject: 数学分析
chapter: 15
prerequisites: 定积分、多元函数微分学
minutes: 90
---

# 第 15 章　重积分

## 为什么要学

定积分求的是曲线下的面积；把"曲线下"换成"曲面下"，就得到**二重积分**——一块平面区域 $D$ 上方、曲面 $z=f(x,y)$ 下方那个"柱体"的体积。同样的道理，如果 $f(x,y,z)$ 是空间中一块物体的密度，那么三重积分就是它的总质量。

思路仍然是熟悉的四步：**分割、近似、求和、取极限**。只不过现在把区域切成小块而不是小段。

但真正让重积分能算的，是两个工具：
1. **化为累次积分（Fubini 定理）**：像切面包一样，先算每一片的面积，再把所有片叠起来。二重积分化为两次定积分。
2. **换元（变量替换）**：在圆形区域上，直角坐标很别扭，极坐标却很自然。换元时会多出一个因子——**Jacobi 行列式**，它衡量坐标变换把小面积"拉伸"了多少倍。

这一章的计算量较大，但规律清晰：**画区域、选坐标、定次序、定限**。CMC 中重积分题往往考查"选对坐标系"和"巧用对称性"。

## 15.1 二重积分的定义

设 $D$ 是平面上的有界闭区域（边界由有限条光滑曲线组成，面积有意义），$f$ 在 $D$ 上有界。把 $D$ 分成小区域 $\sigma_1,\dots,\sigma_n$，面积为 $\Delta\sigma_i$，直径（区域内两点最大距离）的最大值记为 $\lambda$。任取 $(\xi_i,\eta_i)\in\sigma_i$。若
$$\lim_{\lambda\to0}\sum_{i=1}^nf(\xi_i,\eta_i)\Delta\sigma_i$$
存在且与分割、取点无关，称 $f$ 在 $D$ 上**可积**，极限记为 $\displaystyle\iint_Df(x,y)\,\mathrm d\sigma$。

**可积性**：有界闭区域上的连续函数可积；更一般地，有界且间断点集"面积为零"（如落在有限条光滑曲线上）的函数可积。证明和一元情形一样，用一致连续。

**性质**：线性、区域可加性、保序性，以及
- $\iint_D1\,\mathrm d\sigma=$ $D$ 的面积；
- **积分中值定理**：$f$ 在 $D$ 上连续，则存在 $(\xi,\eta)\in D$ 使 $\iint_Df\,\mathrm d\sigma=f(\xi,\eta)\cdot|D|$（$D$ 连通时）。

**对称性**（极其常用）：
- 若 $D$ 关于 $y$ 轴对称，$f$ 关于 $x$ 为奇函数（$f(-x,y)=-f(x,y)$），则 $\iint_Df=0$；为偶函数则等于右半部分的 $2$ 倍。
- **轮换对称**：若 $D$ 关于直线 $y=x$ 对称（交换 $x,y$ 后 $D$ 不变），则 $\iint_Df(x,y)\,\mathrm d\sigma=\iint_Df(y,x)\,\mathrm d\sigma$。

## 15.2 化为累次积分

**定理 15.1（Fubini）** 设 $f$ 在 $D$ 上连续。

**$X$-型区域** $D=\{(x,y):a\le x\le b,\ \varphi_1(x)\le y\le\varphi_2(x)\}$：
$$\iint_Df(x,y)\,\mathrm d\sigma=\int_a^b\mathrm dx\int_{\varphi_1(x)}^{\varphi_2(x)}f(x,y)\,\mathrm dy.$$

**$Y$-型区域** $D=\{(x,y):c\le y\le d,\ \psi_1(y)\le x\le\psi_2(y)\}$：
$$\iint_Df(x,y)\,\mathrm d\sigma=\int_c^d\mathrm dy\int_{\psi_1(y)}^{\psi_2(y)}f(x,y)\,\mathrm dx.$$

**想法**：固定 $x$，内层积分 $A(x)=\int_{\varphi_1(x)}^{\varphi_2(x)}f(x,y)\,\mathrm dy$ 是"在 $x$ 处横截柱体得到的截面面积"；再把截面面积沿 $x$ 方向积起来，就得体积（祖暅原理："幂势既同，则积不容异"）。

**定限的方法**：画出区域，**穿线法**——沿内层积分变量的方向画一条直线穿过区域，进入处的边界是下限，穿出处的边界是上限；外层限是区域在另一个轴上的投影范围。

**交换积分次序**：当内层积分"积不出来"（如 $\int e^{-y^2}\mathrm dy$、$\int\frac{\sin y}y\mathrm dy$）时，换一个次序往往就能算。

**例 1（交换次序）** 计算 $\displaystyle I=\int_0^1\mathrm dx\int_x^1e^{-y^2}\,\mathrm dy$。

**解**：内层 $\int e^{-y^2}\mathrm dy$ 不是初等函数。区域为 $D=\{0\le x\le1,\ x\le y\le1\}=\{0\le y\le1,\ 0\le x\le y\}$。交换次序：
$$I=\int_0^1\mathrm dy\int_0^ye^{-y^2}\,\mathrm dx=\int_0^1ye^{-y^2}\,\mathrm dy=\frac12\left(1-e^{-1}\right).$$

## 15.3 二重积分的换元

**定理 15.2** 设变换 $T:x=x(u,v),\ y=y(u,v)$ 把 $uv$ 平面上的区域 $D'$ 一一地映成 $D$，有连续偏导数，且 Jacobi 行列式
$$J=\frac{\partial(x,y)}{\partial(u,v)}=\begin{vmatrix}x_u&x_v\\y_u&y_v\end{vmatrix}\ne0,$$
则
$$\iint_Df(x,y)\,\mathrm dx\,\mathrm dy=\iint_{D'}f\big(x(u,v),y(u,v)\big)|J|\,\mathrm du\,\mathrm dv.$$

**想法**：$uv$ 平面上一个 $\Delta u\times\Delta v$ 的小矩形，被 $T$ 映成 $xy$ 平面上一个近似平行四边形，两条边是向量 $(x_u,y_u)\Delta u$ 与 $(x_v,y_v)\Delta v$，面积是 $|J|\Delta u\Delta v$。所以 $|J|$ 就是"面积放大倍数"。（在个别点或一条曲线上 $J=0$ 或不一一，不影响结论。）

**极坐标**：$x=r\cos\theta$，$y=r\sin\theta$，$J=r$：
$$\iint_Df(x,y)\,\mathrm dx\,\mathrm dy=\iint_{D'}f(r\cos\theta,r\sin\theta)\,r\,\mathrm dr\,\mathrm d\theta.$$
**别忘了那个 $r$！** 当区域是圆、扇形、环形，或被积函数含 $x^2+y^2$ 时，优先考虑极坐标。

**例 2（Gauss 积分）** 证明 $\displaystyle\int_0^{+\infty}e^{-x^2}\,\mathrm dx=\frac{\sqrt\pi}2$。

**证明**：记 $I(R)=\int_0^Re^{-x^2}\mathrm dx$。考虑第一象限内的三个区域：$D_1$ 为半径 $R$ 的四分之一圆，$S$ 为边长 $R$ 的正方形，$D_2$ 为半径 $\sqrt2R$ 的四分之一圆，$D_1\subseteq S\subseteq D_2$。由被积函数为正：
$$\iint_{D_1}e^{-x^2-y^2}\le\iint_Se^{-x^2-y^2}=I(R)^2\le\iint_{D_2}e^{-x^2-y^2}.$$
用极坐标，$\iint_{D_1}e^{-x^2-y^2}\mathrm d\sigma=\int_0^{\pi/2}\mathrm d\theta\int_0^Re^{-r^2}r\,\mathrm dr=\frac\pi4\left(1-e^{-R^2}\right)$，同理 $D_2$ 上为 $\frac\pi4\left(1-e^{-2R^2}\right)$。令 $R\to+\infty$，两端都趋于 $\frac\pi4$，所以 $I(R)^2\to\frac\pi4$，即 $\int_0^{+\infty}e^{-x^2}\mathrm dx=\frac{\sqrt\pi}2$。$\blacksquare$

> 一元积分"积不出来"，升到二维反而能算——这是重积分最漂亮的应用之一。它也给出 $\Gamma\left(\frac12\right)=\sqrt\pi$。

## 15.4 三重积分

定义完全类似：$\displaystyle\iiint_\Omega f(x,y,z)\,\mathrm dV$。计算方法：

**投影法（先一后二，"穿针"）**：$\Omega=\{(x,y)\in D_{xy},\ z_1(x,y)\le z\le z_2(x,y)\}$，
$$\iiint_\Omega f\,\mathrm dV=\iint_{D_{xy}}\mathrm dx\,\mathrm dy\int_{z_1(x,y)}^{z_2(x,y)}f\,\mathrm dz.$$

**截面法（先二后一，"切片"）**：$\Omega=\{c\le z\le d,\ (x,y)\in D_z\}$，
$$\iiint_\Omega f\,\mathrm dV=\int_c^d\mathrm dz\iint_{D_z}f\,\mathrm dx\,\mathrm dy.$$
当被积函数只含 $z$、且截面 $D_z$ 面积好算时，截面法特别省力。

**柱坐标**：$x=r\cos\theta$，$y=r\sin\theta$，$z=z$，$\mathrm dV=r\,\mathrm dr\,\mathrm d\theta\,\mathrm dz$。适用于旋转体、圆柱。

**球坐标**：$x=\rho\sin\varphi\cos\theta$，$y=\rho\sin\varphi\sin\theta$，$z=\rho\cos\varphi$（$0\le\varphi\le\pi$，$0\le\theta\le2\pi$），
$$\mathrm dV=\rho^2\sin\varphi\,\mathrm d\rho\,\mathrm d\varphi\,\mathrm d\theta.$$
适用于球、锥，或被积函数含 $x^2+y^2+z^2$。

**例 3（截面法）** 计算 $\displaystyle\iiint_\Omega z^2\,\mathrm dV$，$\Omega$ 为椭球 $\dfrac{x^2}{a^2}+\dfrac{y^2}{b^2}+\dfrac{z^2}{c^2}\le1$。

**解**：固定 $z\in[-c,c]$，截面是椭圆 $\dfrac{x^2}{a^2}+\dfrac{y^2}{b^2}\le1-\dfrac{z^2}{c^2}$，面积 $\pi ab\left(1-\dfrac{z^2}{c^2}\right)$。所以
$$\iiint_\Omega z^2\,\mathrm dV=\int_{-c}^cz^2\cdot\pi ab\left(1-\frac{z^2}{c^2}\right)\mathrm dz=2\pi ab\left(\frac{c^3}3-\frac{c^3}5\right)=\frac{4}{15}\pi abc^3.$$

**例 4（球坐标）** 求球 $x^2+y^2+z^2\le R^2$ 与锥 $z\ge\sqrt{x^2+y^2}$ 的公共部分（"冰淇淋筒"）的体积。

**解**：锥面 $z=\sqrt{x^2+y^2}$ 在球坐标中是 $\varphi=\frac\pi4$。区域为 $0\le\rho\le R$，$0\le\varphi\le\frac\pi4$，$0\le\theta\le2\pi$：
$$V=\int_0^{2\pi}\mathrm d\theta\int_0^{\pi/4}\sin\varphi\,\mathrm d\varphi\int_0^R\rho^2\,\mathrm d\rho=2\pi\left(1-\frac{\sqrt2}2\right)\frac{R^3}3=\frac{(2-\sqrt2)\pi R^3}3.$$

## 15.5 应用简述

- **曲面面积**：曲面 $z=f(x,y)$，$(x,y)\in D$ 的面积 $S=\displaystyle\iint_D\sqrt{1+f_x^2+f_y^2}\,\mathrm dx\,\mathrm dy$；
- **质量、质心**：密度 $\mu$，质心 $\bar x=\dfrac{\iint x\mu\,\mathrm d\sigma}{\iint\mu\,\mathrm d\sigma}$；
- **转动惯量**：绕 $z$ 轴 $I_z=\iiint(x^2+y^2)\mu\,\mathrm dV$。

## 更多例题

**例 5（轮换对称，CMC 风格）** 设 $f$ 在 $[0,1]$ 上连续且恒正，$D=[0,1]\times[0,1]$。求
$$I=\iint_D\frac{af(x)+bf(y)}{f(x)+f(y)}\,\mathrm dx\,\mathrm dy.$$

**解**：$D$ 关于 $y=x$ 对称，交换 $x,y$ 得
$$I=\iint_D\frac{af(y)+bf(x)}{f(y)+f(x)}\,\mathrm dx\,\mathrm dy.$$
两式相加：$2I=\iint_D\dfrac{(a+b)(f(x)+f(y))}{f(x)+f(y)}\,\mathrm dx\,\mathrm dy=a+b$，所以 $I=\dfrac{a+b}2$。

> 被积函数复杂但"$x,y$ 地位对称"时，先想到轮换对称。不需要知道 $f$ 是什么！

**例 6（CMC 风格：二重积分证明不等式）** 设 $f$ 在 $[a,b]$ 上连续且恒正，证明
$$\int_a^bf(x)\,\mathrm dx\cdot\int_a^b\frac{\mathrm dx}{f(x)}\ge(b-a)^2.$$

**证明**：把乘积写成二重积分，$D=[a,b]^2$：
$$\int_a^bf(x)\,\mathrm dx\int_a^b\frac{\mathrm dy}{f(y)}=\iint_D\frac{f(x)}{f(y)}\,\mathrm d\sigma.$$
由轮换对称，它也等于 $\iint_D\dfrac{f(y)}{f(x)}\,\mathrm d\sigma$。取平均：
$$\text{左边}=\frac12\iint_D\left(\frac{f(x)}{f(y)}+\frac{f(y)}{f(x)}\right)\mathrm d\sigma\ge\frac12\iint_D2\,\mathrm d\sigma=(b-a)^2.\qquad\blacksquare$$

> **"两个一元积分之积 = 一个二重积分"** 是 CMC 积分不等式题的另一件利器，常配合轮换对称使用。（这个不等式也可以由 Cauchy–Schwarz 直接得到。）

## 易错点

1. **极坐标漏乘 $r$，球坐标漏乘 $\rho^2\sin\varphi$。** 这是最常见的计算错误。
2. **定限时画图！** 不画图凭空写积分限，十有八九会错。外层限必须是常数，内层限可以依赖外层变量。
3. **换元要用 $|J|$（绝对值）**，并保证变换一一（至多除去零面积的集合）。
4. **对称性要同时检查区域和被积函数**：区域对称而被积函数无相应奇偶性时，不能直接得零。
5. **交换积分次序时要重新画区域、重新定限**，不是简单地把两个积分号对调。
6. **三重积分选坐标系**：看区域边界和被积函数中哪种形式（$x^2+y^2$ 还是 $x^2+y^2+z^2$）出现，就选柱坐标或球坐标。

## 自测清单

1. 交换积分次序：$\displaystyle\int_0^1\mathrm dy\int_{y^2}^{y}f(x,y)\,\mathrm dx$。
2. 计算 $\displaystyle\iint_{x^2+y^2\le1}(x^2+y^2)\,\mathrm d\sigma$。
3. 计算 $\displaystyle\iint_{x^2+y^2\le1}(x^3+y)\,\mathrm d\sigma$。
4. 变换 $x=au$，$y=bv$ 的 Jacobi 行列式是多少？用它求椭圆 $\frac{x^2}{a^2}+\frac{y^2}{b^2}\le1$ 的面积。
5. 写出球坐标的体积元。
6. 计算 $\displaystyle\int_0^1\mathrm dy\int_y^1\frac{\sin x}{x}\,\mathrm dx$。
7. 若 $D$ 关于 $y=x$ 对称，求 $\displaystyle\iint_D\frac{x^2-y^2}{1+x^2+y^2}\,\mathrm d\sigma$。

## 参考答案

### 点击展开参考答案

1. 区域 $\{0\le y\le1,\ y^2\le x\le y\}=\{0\le x\le1,\ x\le y\le\sqrt x\}$，结果为 $\displaystyle\int_0^1\mathrm dx\int_x^{\sqrt x}f(x,y)\,\mathrm dy$。
2. $\int_0^{2\pi}\mathrm d\theta\int_0^1r^2\cdot r\,\mathrm dr=\frac\pi2$。
3. $0$（$x^3$ 关于 $x$ 奇、$y$ 关于 $y$ 奇，圆盘关于两轴都对称）。
4. $J=ab$；面积 $=\iint_{u^2+v^2\le1}ab\,\mathrm du\,\mathrm dv=\pi ab$。
5. $\mathrm dV=\rho^2\sin\varphi\,\mathrm d\rho\,\mathrm d\varphi\,\mathrm d\theta$。
6. 交换次序：$\int_0^1\frac{\sin x}x\,\mathrm dx\int_0^x\mathrm dy=\int_0^1\sin x\,\mathrm dx=1-\cos1$。
7. 轮换对称下 $\iint x^2/(\ldots)=\iint y^2/(\ldots)$，结果为 $0$。


