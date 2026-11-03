---
title: 线性变换
subject: 高等代数
chapter: 7
prerequisites: 多项式, 矩阵, 线性空间
minutes: 90
---

# 第七章 线性变换

## 为什么要学

上一章研究的是“空间本身”，这一章研究**空间到自身的保持线性结构的映射**——线性变换。旋转、反射、投影、伸缩、求导……都是线性变换。

本章的核心问题是：**能不能选一组好的基，让变换的矩阵尽可能简单？**

想象平面上的变换 $\sigma$：把 $(1,1)$ 方向拉长 $3$ 倍，把 $(1,-1)$ 方向保持不动。在标准基下它的矩阵是 $\begin{pmatrix}2&1\\1&2\end{pmatrix}$，看不出什么；但若以 $(1,1),(1,-1)$ 为基，矩阵就是 $\begin{pmatrix}3&0\\0&1\end{pmatrix}$，一目了然。这两个**特殊方向**就是**特征向量**，伸缩倍数 $3,1$ 就是**特征值**。

所以：

- 换基 $\leftrightarrow$ 相似变换 $T^{-1}AT$；
- 找“只被伸缩、不被转向”的方向 $\leftrightarrow$ 求特征向量；
- 特征向量够多（能组成一组基）$\leftrightarrow$ 可对角化。

不是所有变换都能对角化：平面上旋转 $90^\circ$ 没有任何方向被保持（没有实特征值）；错切 $\begin{pmatrix}1&1\\0&1\end{pmatrix}$ 只有一个方向被保持（特征向量不够）。后一种“缺陷”如何处理，是第八章 Jordan 标准形的任务。

## 1. 线性变换及其运算

**定义 1** 线性空间 $V$ 到自身的映射 $\sigma$ 若满足
$$\sigma(\alpha+\beta)=\sigma(\alpha)+\sigma(\beta),\qquad\sigma(k\alpha)=k\sigma(\alpha),$$
称为 $V$ 的**线性变换**。

基本性质：$\sigma(0)=0$；$\sigma$ 保持线性组合；线性相关的向量组变换后仍线性相关（反之不然：线性无关的可能变成相关，比如投影）。

**例**：(1) 恒等变换 $\varepsilon$，零变换；(2) 数乘变换 $k\varepsilon$；(3) $P[x]$ 上的求导 $\mathcal{D}$；(4) $P^n$ 上 $X\mapsto AX$（$A$ 为固定矩阵）。

线性变换可以相加、数乘、相乘（复合）。乘法满足结合律，不满足交换律。于是对多项式 $f(x)=a_mx^m+\cdots+a_0$，可以定义 $f(\sigma)=a_m\sigma^m+\cdots+a_0\varepsilon$，且 $f(\sigma)g(\sigma)=g(\sigma)f(\sigma)$。

## 2. 线性变换的矩阵

设 $\varepsilon_1,\dots,\varepsilon_n$ 是 $V$ 的一组基。$\sigma$ 由它在基上的作用完全决定：
$$\sigma(\varepsilon_1,\dots,\varepsilon_n)=(\sigma\varepsilon_1,\dots,\sigma\varepsilon_n)=(\varepsilon_1,\dots,\varepsilon_n)A,$$
$A$ 的第 $j$ 列是 $\sigma\varepsilon_j$ 的坐标。$A$ 称为 $\sigma$ 在这组基下的矩阵。

**定理 1** 取定一组基后，线性变换与 $n$ 阶矩阵一一对应，且这种对应保持加法、数乘、乘法：$\sigma\tau\leftrightarrow AB$，$\sigma^{-1}\leftrightarrow A^{-1}$。

**坐标公式** 若 $\alpha$ 的坐标为 $X$，则 $\sigma\alpha$ 的坐标为 $AX$。

**定理 2** 设 $\sigma$ 在基 $\varepsilon$ 和基 $\eta$ 下的矩阵分别为 $A,B$，过渡矩阵为 $T$（$\eta=\varepsilon T$），则
$$B=T^{-1}AT.$$

**证明** $\sigma(\eta)=\sigma(\varepsilon)T=\varepsilon AT=\eta\,T^{-1}AT$。由矩阵的唯一性，$B=T^{-1}AT$。$\blacksquare$

**定义 2** 若存在可逆矩阵 $T$ 使 $B=T^{-1}AT$，称 $A$ 与 $B$ **相似**，记 $A\sim B$。

于是，**同一个线性变换在不同基下的矩阵彼此相似**；反之，相似的矩阵可以看作同一个变换在不同基下的矩阵。

## 3. 特征值与特征向量

**定义 3** 若存在数 $\lambda_0\in P$ 和**非零**向量 $\xi$ 使 $\sigma\xi=\lambda_0\xi$，称 $\lambda_0$ 为 $\sigma$ 的**特征值**，$\xi$ 为属于 $\lambda_0$ 的**特征向量**。

转成矩阵：设 $\xi$ 的坐标为 $X$，$\sigma\xi=\lambda_0\xi\Leftrightarrow AX=\lambda_0X\Leftrightarrow(\lambda_0E-A)X=0$。有非零解 $\Leftrightarrow|\lambda_0E-A|=0$。

**定义 4** $f(\lambda)=|\lambda E-A|$ 称为 $A$ 的**特征多项式**。

**求法**：(1) 解 $|\lambda E-A|=0$，求出 $P$ 中的全部根；(2) 对每个根 $\lambda_i$，求 $(\lambda_iE-A)X=0$ 的基础解系，它们（的非零线性组合）就是全部特征向量。属于 $\lambda_i$ 的特征向量连同零向量构成**特征子空间** $V_{\lambda_i}$。

**特征多项式的系数**：
$$|\lambda E-A|=\lambda^n-(\operatorname{tr}A)\lambda^{n-1}+\cdots+(-1)^n|A|.$$
所以若 $\lambda_1,\dots,\lambda_n$ 是全部复特征值（计重数），则
$$\lambda_1+\cdots+\lambda_n=\operatorname{tr}A=a_{11}+\cdots+a_{nn},\qquad\lambda_1\lambda_2\cdots\lambda_n=|A|.$$

**定理 3** 相似矩阵有相同的特征多项式。

**证明** $|\lambda E-T^{-1}AT|=|T^{-1}(\lambda E-A)T|=|T^{-1}||\lambda E-A||T|=|\lambda E-A|$。$\blacksquare$

因此特征多项式、特征值、迹、行列式都是**线性变换本身**的属性，与基的选取无关。（注意：反过来，特征多项式相同的矩阵**不一定**相似，例如 $E_2$ 与 $\begin{pmatrix}1&1\\0&1\end{pmatrix}$。）

**常用性质**：若 $\lambda$ 是 $A$ 的特征值，$\xi$ 是特征向量，则 $g(\lambda)$ 是 $g(A)$ 的特征值（同一个 $\xi$）；$A$ 可逆时 $\lambda^{-1}$ 是 $A^{-1}$ 的特征值，$\dfrac{|A|}{\lambda}$ 是 $A^{*}$ 的特征值。

## 4. Hamilton–Cayley 定理

**定理 4** 设 $f(\lambda)=|\lambda E-A|$，则 $f(A)=O$。

**思路** 想直接“把 $\lambda=A$ 代入 $|\lambda E-A|$ 得 $|A-A|=0$”是**错误**的（左边是矩阵多项式，右边是数）。正确做法是用伴随矩阵：$(\lambda E-A)\cdot(\lambda E-A)^{*}=f(\lambda)E$，把两边都按 $\lambda$ 的幂展开，比较系数。

**证明** 记 $B(\lambda)=(\lambda E-A)^{*}$。它的元素是 $\lambda E-A$ 的 $n-1$ 阶子式（带符号），都是次数 $\le n-1$ 的多项式，所以可以写成
$$B(\lambda)=\lambda^{n-1}B_{n-1}+\lambda^{n-2}B_{n-2}+\cdots+B_0,$$
$B_k$ 为数字矩阵。设 $f(\lambda)=\lambda^n+a_{n-1}\lambda^{n-1}+\cdots+a_0$。由 $(\lambda E-A)B(\lambda)=f(\lambda)E$，比较 $\lambda$ 各次幂的系数：
$$B_{n-1}=E,\quad B_{n-2}-AB_{n-1}=a_{n-1}E,\quad\dots,\quad B_0-AB_1=a_1E,\quad-AB_0=a_0E.$$
依次用 $A^n,A^{n-1},\dots,A,E$ 左乘这些等式，然后全部相加，左边逐项抵消为 $O$，右边恰为 $A^n+a_{n-1}A^{n-1}+\cdots+a_1A+a_0E=f(A)$。$\blacksquare$

**用途**：把 $A$ 的高次幂降为低次；求 $A^{-1}$（若 $a_0\ne0$，$A^{-1}=-\dfrac{1}{a_0}(A^{n-1}+a_{n-1}A^{n-2}+\cdots+a_1E)$）。

**最小多项式** 以 $A$ 为根的首一多项式中次数最低的一个，称为 $A$ 的**最小多项式** $m(\lambda)$。它唯一，且对任意 $g$：$g(A)=O\Leftrightarrow m(\lambda)\mid g(\lambda)$（用带余除法）。特别地 $m(\lambda)\mid f(\lambda)$。$m$ 与 $f$ 有相同的根（不计重数）。相似矩阵的最小多项式相同。

## 5. 对角化

**定理 5** 属于不同特征值的特征向量线性无关。

**思路** 对个数归纳。若 $\xi_1,\dots,\xi_k$ 之间有关系 $\sum c_i\xi_i=0$，用 $\sigma$ 作用一次得 $\sum c_i\lambda_i\xi_i=0$，再减去 $\lambda_k$ 倍的原式，就消掉了 $\xi_k$，变成 $k-1$ 个的情形。

**证明** $k=1$ 时特征向量非零，成立。设对 $k-1$ 成立，若
$$c_1\xi_1+\cdots+c_k\xi_k=0,\tag{1}$$
作用 $\sigma$：$c_1\lambda_1\xi_1+\cdots+c_k\lambda_k\xi_k=0$；减去 (1) 的 $\lambda_k$ 倍：
$$c_1(\lambda_1-\lambda_k)\xi_1+\cdots+c_{k-1}(\lambda_{k-1}-\lambda_k)\xi_{k-1}=0.$$
由归纳假设各系数为零，又 $\lambda_i\ne\lambda_k$，故 $c_1=\cdots=c_{k-1}=0$，代回 (1) 得 $c_k=0$。$\blacksquare$

更一般地：各特征子空间之和是直和，把各个特征子空间的基拼起来仍线性无关。

**定理 6（可对角化的判定）** 对 $n$ 维空间上的 $\sigma$（或 $n$ 阶矩阵 $A$），以下等价：

1. $\sigma$ 在某组基下的矩阵是对角矩阵（$A$ 相似于对角矩阵）；
2. $\sigma$ 有 $n$ 个线性无关的特征向量；
3. 各特征子空间维数之和为 $n$；
4. 特征多项式在 $P$ 中能完全分解为一次因式，且每个特征值的**几何重数** $\dim V_{\lambda_i}$ 等于其**代数重数**（作为特征多项式根的重数）；
5. 最小多项式在 $P$ 中能分解为**互不相同**的一次因式之积（证明见第八章）。

1⇔2 的理由：$\sigma$ 在基 $\xi_1,\dots,\xi_n$ 下的矩阵是对角阵 $\operatorname{diag}(\lambda_1,\dots,\lambda_n)$，当且仅当每个 $\sigma\xi_i=\lambda_i\xi_i$。矩阵语言：若 $T=(\xi_1,\dots,\xi_n)$，则 $AT=T\operatorname{diag}(\lambda_1,\dots,\lambda_n)$，即 $T^{-1}AT=\operatorname{diag}(\lambda_1,\dots,\lambda_n)$。

**一般事实**：几何重数 $\le$ 代数重数。（取 $V_{\lambda_0}$ 的基扩充为全空间的基，矩阵为 $\begin{pmatrix}\lambda_0E_s&*\\O&B\end{pmatrix}$，特征多项式含因子 $(\lambda-\lambda_0)^s$。）

**推论** 若 $n$ 阶矩阵有 $n$ 个互不相同的特征值，则可对角化。

## 6. 值域、核与不变子空间

**定义 5** $\sigma V=\{\sigma\alpha:\alpha\in V\}$ 称为**值域**，$\sigma^{-1}(0)=\{\alpha:\sigma\alpha=0\}$ 称为**核**。它们都是子空间，$\dim\sigma V$ 称为 $\sigma$ 的秩，等于矩阵 $A$ 的秩。

**定理 7（秩–零化度定理）** $\dim\sigma V+\dim\sigma^{-1}(0)=n$。

**证明** 取核的基 $\varepsilon_1,\dots,\varepsilon_k$，扩充为 $V$ 的基 $\varepsilon_1,\dots,\varepsilon_n$。则 $\sigma V=L(\sigma\varepsilon_{k+1},\dots,\sigma\varepsilon_n)$（因为 $\sigma\varepsilon_1=\cdots=\sigma\varepsilon_k=0$）。若 $\sum_{i>k}c_i\sigma\varepsilon_i=0$，则 $\sum_{i>k}c_i\varepsilon_i\in\sigma^{-1}(0)$，可由 $\varepsilon_1,\dots,\varepsilon_k$ 表出，由基的无关性，所有 $c_i=0$。故 $\dim\sigma V=n-k$。$\blacksquare$

（这正是第三章“基础解系含 $n-r$ 个向量”的变换版本。）

**注意**：$\dim\sigma V+\dim\sigma^{-1}(0)=n$，但 $\sigma V+\sigma^{-1}(0)$ **不一定**是 $V$。例如求导 $\mathcal D$ 在 $P[x]_3$ 上：值域是 $P[x]_2=L(1,x)$，核是常数多项式 $L(1)$，二者的交含非零常数，它们的和只有 $2$ 维，不是整个 $P[x]_3$。

**定义 6** 若 $\sigma W\subseteq W$，称子空间 $W$ 为 $\sigma$ 的**不变子空间**。值域、核、特征子空间、与 $\sigma$ 可交换的变换的值域与核，都是不变子空间。

若 $V=W_1\oplus W_2$ 且两者都是 $\sigma$-不变的，取各自的基拼成 $V$ 的基，$\sigma$ 的矩阵就是**分块对角**的 $\operatorname{diag}(A_1,A_2)$。这就是“化简矩阵”的一般思路：把空间拆成不变子空间的直和。

## 7. 例题

### 例 1（求导变换的矩阵，基础）

求 $\mathcal D$ 在 $P[x]_3$ 的基 $1,x,x^2$ 下的矩阵，并判断能否对角化。

**解** $\mathcal D1=0$，$\mathcal Dx=1$，$\mathcal Dx^2=2x$，按列写坐标：
$$A=\begin{pmatrix}0&1&0\\0&0&2\\0&0&0\end{pmatrix}.$$
$|\lambda E-A|=\lambda^3$，特征值只有 $0$（代数重数 $3$）。$r(A)=2$，特征子空间维数 $3-2=1$（只有常数多项式），几何重数 $1<3$，**不可对角化**。

### 例 2（对角化与矩阵幂，必会）

设 $A=\begin{pmatrix}1&2&2\\2&1&2\\2&2&1\end{pmatrix}$，求可逆矩阵 $T$ 使 $T^{-1}AT$ 为对角阵，并求 $A^n$。

**解** 写 $A=2J-E$，$J$ 为全 $1$ 矩阵。$J$ 的特征值为 $3,0,0$（$r(J)=1$，迹为 $3$），所以 $A$ 的特征值为 $2\cdot3-1=5$ 和 $-1$（二重）。

$\lambda=5$：$(5E-A)X=0$，得 $\xi_1=(1,1,1)^{T}$。

$\lambda=-1$：$(-E-A)X=0$ 即 $x_1+x_2+x_3=0$，基础解系 $\xi_2=(-1,1,0)^{T}$，$\xi_3=(-1,0,1)^{T}$。

取 $T=\begin{pmatrix}1&-1&-1\\1&1&0\\1&0&1\end{pmatrix}$，则 $T^{-1}AT=\operatorname{diag}(5,-1,-1)$。

求 $A^n$ 有一个更快的办法：$\dfrac13J$ 是到 $\xi_1$ 方向的投影（$\left(\dfrac13J\right)^2=\dfrac13J$），$A=5\cdot\dfrac13J+(-1)\left(E-\dfrac13J\right)$，两个投影互相“正交”（乘积为零），于是
$$A^n=5^n\cdot\frac13J+(-1)^n\left(E-\frac13J\right)=(-1)^nE+\frac{5^n-(-1)^n}{3}J.$$
（$n=1$ 时验证：$-E+2J=A$。）

### 例 3（Hamilton–Cayley 求逆，基础）

用 Hamilton–Cayley 定理求 $A=\begin{pmatrix}1&2\\3&4\end{pmatrix}$ 的逆。

**解** $f(\lambda)=\lambda^2-5\lambda-2$，所以 $A^2-5A-2E=O$，即 $A(A-5E)=2E$，
$$A^{-1}=\frac12(A-5E)=\frac12\begin{pmatrix}-4&2\\3&-1\end{pmatrix}=\begin{pmatrix}-2&1\\\frac32&-\frac12\end{pmatrix}.$$
与第四章例 2 一致。

### 例 4（幂等变换）

设 $A^2=A$。证明 $A$ 可对角化，且 $r(A)=\operatorname{tr}A$。

**证明** 对任意 $X$，$X=AX+(X-AX)$，其中 $A(AX)=AX$，$A(X-AX)=AX-A^2X=0$。所以 $P^n=V_1+V_0$，其中 $V_1,V_0$ 分别是特征值 $1,0$ 的特征子空间（$V_1$ 包含 $A$ 的列空间，$V_0$ 是 $AX=0$ 的解空间）。由定理 5，这是直和，各特征子空间维数之和为 $n$，$A$ 可对角化：$A\sim\operatorname{diag}(E_r,O)$。于是 $r(A)=r$ = 对角线上 $1$ 的个数 $=\operatorname{tr}A$。$\blacksquare$

### 例 5（CMC 风格：可交换矩阵有公共特征向量）

设 $A,B$ 为 $n$ 阶复矩阵，$AB=BA$。证明 $A,B$ 至少有一个公共的特征向量。

**思路** $A$ 的特征子空间是 $B$ 的不变子空间；$B$ 限制在这个子空间上仍是一个复线性变换，必有特征向量。

**证明** 设 $\lambda$ 是 $A$ 的一个特征值（复数域上必存在），$V_\lambda=\{X:AX=\lambda X\}\ne\{0\}$。若 $X\in V_\lambda$，则
$$A(BX)=BAX=\lambda(BX),$$
所以 $BX\in V_\lambda$，$V_\lambda$ 是 $B$ 的不变子空间。$B$ 限制在 $V_\lambda$ 上（记为 $B|_{V_\lambda}$）是复数域上有限维空间的线性变换，其特征多项式在 $\mathbb{C}$ 中有根，故存在非零 $\xi\in V_\lambda$ 和 $\mu$ 使 $B\xi=\mu\xi$。而 $A\xi=\lambda\xi$，$\xi$ 就是公共特征向量。$\blacksquare$

**推广**：可交换的复矩阵可以**同时上三角化**；若两者都可对角化，则可以同时对角化。

### 例 6（CMC 风格：值域与核何时成直和）

设 $\sigma$ 是 $n$ 维空间 $V$ 的线性变换，$A$ 为其矩阵。证明
$$V=\sigma V\oplus\sigma^{-1}(0)\iff r(A^2)=r(A).$$

**证明** 总有 $\sigma^{-1}(0)\subseteq(\sigma^2)^{-1}(0)$。由秩–零化度定理，$r(A^2)=r(A)\iff$ 两个核维数相同 $\iff\sigma^{-1}(0)=(\sigma^2)^{-1}(0)$。

“$\Leftarrow$”：设 $\alpha\in\sigma V\cap\sigma^{-1}(0)$，$\alpha=\sigma\beta$，则 $\sigma^2\beta=\sigma\alpha=0$，$\beta\in(\sigma^2)^{-1}(0)=\sigma^{-1}(0)$，故 $\alpha=\sigma\beta=0$。交为零，维数和为 $n$，所以 $V=\sigma V\oplus\sigma^{-1}(0)$。

“$\Rightarrow$”：设 $\sigma^2\beta=0$，则 $\sigma\beta\in\sigma V\cap\sigma^{-1}(0)=\{0\}$，$\beta\in\sigma^{-1}(0)$。所以两个核相等，$r(A^2)=r(A)$。$\blacksquare$

例 1 中 $r(A)=2$，$r(A^2)=1$，所以值域与核不是直和，与前面的观察吻合。

## 8. 易错点

1. **特征向量必须非零**；特征子空间里的零向量不是特征向量。
2. **特征值要在数域 $P$ 中**。实数域上旋转 $90^\circ$ 的矩阵 $\begin{pmatrix}0&-1\\1&0\end{pmatrix}$ 没有特征值；在复数域上有 $\pm\mathrm i$。
3. **特征多项式相同 $\not\Rightarrow$ 相似**；相似必须进一步比较（几何重数、最小多项式、Jordan 形）。
4. **Hamilton–Cayley 不能“直接代入”证明**。
5. **属于同一特征值的两个特征向量之和，若非零，仍是特征向量；属于不同特征值的特征向量之和一定不是特征向量**。
6. **$\dim\sigma V+\dim\sigma^{-1}(0)=n$ 不意味着 $V=\sigma V\oplus\sigma^{-1}(0)$**（例 6）。
7. 对角化时 $T$ 的**列**是特征向量，并且列的顺序必须和对角线上特征值的顺序对应。

## 本章小结与学习建议

**“变换”与“矩阵”的双语对照表**（取定一组基后）：

- 线性变换 $\sigma$ $\leftrightarrow$ 矩阵 $A$；复合 $\sigma\tau$ $\leftrightarrow$ 乘积 $AB$；
- 换基 $\leftrightarrow$ 相似 $T^{-1}AT$；
- $\sigma$ 的秩 $\dim\sigma V$ $\leftrightarrow$ $r(A)$；核的维数 $\leftrightarrow$ $n-r(A)$；
- 不变子空间直和分解 $\leftrightarrow$ 分块对角化；
- 特征向量 $\leftrightarrow$ $(\lambda E-A)X=0$ 的非零解。

能在两种语言之间自如切换，是本章最重要的能力。证明题常用变换语言（更直观，不依赖基），计算题用矩阵语言。

**对角化问题的完整流程**：

1. 求特征多项式，分解出全部特征值及其代数重数。若在所给数域中不能完全分解，就不能在该数域上对角化。
2. 对每个特征值 $\lambda_i$，算 $r(\lambda_iE-A)$，得到几何重数 $n-r(\lambda_iE-A)$。
3. 若每个特征值的几何重数都等于代数重数，可对角化；把各特征子空间的基拼成 $T$。只要有一个不等，就不可对角化。
4. 需要时用 $A=T\operatorname{diag}(\lambda_i)T^{-1}$ 计算 $A^n$ 或 $g(A)$。

**三个常用的“降次”思路**：(1) Hamilton–Cayley：用特征多项式把 $A^n$ 降到 $n-1$ 次以下；(2) 最小多项式：若已知 $A$ 满足某个低次多项式（如 $A^2=A$），直接用它降次，同时得到可对角化的信息；(3) 对角化后逐个对角元计算。

**与第九章的预告**：实对称矩阵是一类“永远可以对角化”的矩阵，而且可以用正交矩阵完成，这是第九章的主角。

**学习建议**：特征值问题的计算题较为程式化，务必熟练；而证明题（例 5、例 6）的关键往往是“找一个合适的不变子空间”或“比较两个子空间的维数”，多做几道体会这种思路。

## 9. 自测清单

1. $3$ 阶方阵 $A$ 的特征值为 $1,2,3$，求 $|A^2-2A+E|$ 和 $|A^{*}|$。
2. 若 $A\sim B$，证明 $A^k\sim B^k$，$A^{T}\sim B^{T}$。
3. $A=\begin{pmatrix}1&1\\0&1\end{pmatrix}$ 能否对角化？
4. 若 $A^2=E$，$A$ 的特征值可能是哪些？
5. 设 $n$ 阶矩阵 $A$ 的各行元素之和都为 $3$，写出 $A$ 的一个特征值和特征向量。
6. 若 $A$ 的最小多项式为 $\lambda^2-1$，$A$ 能否对角化？
7. 设 $\sigma$ 是 $V$ 上的线性变换，$\sigma^2=\sigma$，证明 $V=\sigma V\oplus\sigma^{-1}(0)$。

## 参考答案

1. $A^2-2A+E=(A-E)^2$ 的特征值为 $0,1,4$，行列式为 $0$。$|A|=6$，$|A^{*}|=|A|^{2}=36$。
2. $B=T^{-1}AT\Rightarrow B^k=T^{-1}A^kT$；$B^{T}=T^{T}A^{T}(T^{T})^{-1}=S^{-1}A^{T}S$，其中 $S=(T^{T})^{-1}$。
3. 不能：特征值 $1$ 的代数重数 $2$，而 $r(E-A)=1$，几何重数 $1$。
4. 若 $A\xi=\lambda\xi$，则 $\xi=A^2\xi=\lambda^2\xi$，$\lambda^2=1$，所以只可能是 $\pm1$。
5. $A(1,1,\dots,1)^{T}=3(1,1,\dots,1)^{T}$，特征值 $3$，特征向量 $(1,\dots,1)^{T}$。
6. 能：$\lambda^2-1=(\lambda-1)(\lambda+1)$ 是互不相同的一次因式之积。
7. $\sigma^2=\sigma$ 说明 $r(A^2)=r(A)$，由例 6 即得；或直接用 $\alpha=\sigma\alpha+(\alpha-\sigma\alpha)$ 并验证交为零。
