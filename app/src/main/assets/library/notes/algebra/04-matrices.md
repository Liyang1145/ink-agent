---
title: 矩阵
subject: 高等代数
chapter: 4
prerequisites: 行列式, 线性方程组
minutes: 90
---

# 第四章 矩阵

## 为什么要学

前两章里，矩阵只是“一张数表”。本章让它活起来：矩阵可以**相加、相乘、求逆**，成为一种可以运算的“数”。

最重要的直觉是：**矩阵就是一个变换**。$2\times2$ 矩阵 $A$ 把平面上的向量 $X$ 变成 $AX$：比如 $\begin{pmatrix}\cos\theta&-\sin\theta\\\sin\theta&\cos\theta\end{pmatrix}$ 是旋转，$\begin{pmatrix}2&0\\0&1\end{pmatrix}$ 是横向拉伸。于是：

- **矩阵乘法 $AB$ 就是“先做 $B$ 再做 $A$”**，变换的复合。这解释了乘法为什么是那个奇怪的“行乘列”规则，也解释了为什么 $AB\ne BA$（先转再拉 ≠ 先拉再转）。
- **逆矩阵就是“撤销”这个变换**。把平面压扁成直线的变换无法撤销——它的行列式（面积缩放倍数）为 $0$。
- $|AB|=|A||B|$：两次变换的面积缩放倍数相乘，非常自然。

这一章是纯技术性最强的一章，也是 CMC 中秩不等式、伴随矩阵、分块技巧的主要来源。

## 1. 矩阵的运算

设 $A=(a_{ij})_{m\times n}$。加法、数乘按元素进行。

**定义 1（乘法）** 若 $A$ 为 $m\times s$，$B$ 为 $s\times n$，则 $C=AB$ 为 $m\times n$ 矩阵，
$$c_{ij}=\sum_{k=1}^sa_{ik}b_{kj}.$$

**乘法规则从哪里来？** 设有两次线性替换：先 $y=Bx$，再 $z=Ay$，即
$$y_k=\sum_{j}b_{kj}x_j,\qquad z_i=\sum_{k}a_{ik}y_k.$$
把第一式代入第二式，得 $z_i=\sum_j\Big(\sum_ka_{ik}b_{kj}\Big)x_j$，从 $x$ 直接到 $z$ 的系数矩阵恰好是 $AB$。所以矩阵乘法的定义不是人为规定，而是“复合两次线性替换”自然产生的。这也解释了为什么只有当 $A$ 的列数等于 $B$ 的行数时才能相乘：中间变量 $y$ 的个数必须对得上。

乘法满足结合律、分配律，但**一般不满足交换律和消去律**：
$$\begin{pmatrix}1&0\\0&0\end{pmatrix}\begin{pmatrix}0&0\\1&0\end{pmatrix}=\begin{pmatrix}0&0\\0&0\end{pmatrix},\quad\begin{pmatrix}0&0\\1&0\end{pmatrix}\begin{pmatrix}1&0\\0&0\end{pmatrix}=\begin{pmatrix}0&0\\1&0\end{pmatrix}.$$
两个非零矩阵之积可以为零矩阵！

**转置** $(AB)^{T}=B^{T}A^{T}$（顺序反过来，就像“先穿袜子后穿鞋，脱的时候先脱鞋”）。

**两个有用的视角**：

- **列视角**：$AB$ 的第 $j$ 列 $=A\times(B\text{ 的第 }j\text{ 列})$，是 $A$ 的列的线性组合。所以 $AB$ 的列向量组可由 $A$ 的列向量组线性表出，$r(AB)\le r(A)$。
- **行视角**：$AB$ 的第 $i$ 行是 $B$ 的行的线性组合，$r(AB)\le r(B)$。

因此 **$r(AB)\le\min\{r(A),r(B)\}$**。

## 2. 行列式乘法定理

**定理 1** 设 $A,B$ 为 $n$ 阶方阵，则 $|AB|=|A|\,|B|$。

**思路** 构造 $2n$ 阶分块矩阵 $\begin{pmatrix}A&O\\-E&B\end{pmatrix}$，它的行列式显然是 $|A||B|$；再用“把某些列的倍数加到另一些列”（不改变行列式）把右上角变成 $AB$、右下角变成 $O$。

**证明** 由 Laplace 定理，$D=\begin{vmatrix}A&O\\-E&B\end{vmatrix}=|A|\,|B|$。对 $j=1,\dots,n$，把第 $1,\dots,n$ 列分别乘 $b_{1j},\dots,b_{nj}$ 加到第 $n+j$ 列。下半部分第 $n+j$ 列变为 $B$ 的第 $j$ 列减去 $\sum_kb_{kj}e_k$，即 $0$；上半部分第 $n+j$ 列变为 $\sum_kb_{kj}(A\text{ 的第 }k\text{ 列})$，即 $AB$ 的第 $j$ 列。于是
$$D=\begin{vmatrix}A&AB\\-E&O\end{vmatrix}=(-1)^{n\cdot n}|AB|\,|-E|=(-1)^{n^2}(-1)^n|AB|=|AB|,$$
因为 $n^2+n$ 是偶数。$\blacksquare$

## 3. 逆矩阵与伴随矩阵

**定义 2** 对 $n$ 阶方阵 $A$，若存在 $B$ 使 $AB=BA=E$，称 $A$ **可逆**，$B=A^{-1}$（唯一）。

**定义 3** $A$ 的**伴随矩阵** $A^{*}$ 是把代数余子式 $A_{ij}$ **放在第 $j$ 行第 $i$ 列**所得的矩阵（注意转置）：
$$A^{*}=\begin{pmatrix}A_{11}&A_{21}&\cdots&A_{n1}\\A_{12}&A_{22}&\cdots&A_{n2}\\\vdots&\vdots&&\vdots\\A_{1n}&A_{2n}&\cdots&A_{nn}\end{pmatrix}.$$

**基本公式** 由展开定理（第二章定理 2），
$$AA^{*}=A^{*}A=|A|E.$$

**定理 2** $A$ 可逆 $\Leftrightarrow|A|\ne0$，且此时 $A^{-1}=\dfrac{1}{|A|}A^{*}$。

**证明** 若 $|A|\ne0$，由基本公式 $A\cdot\dfrac{A^{*}}{|A|}=E$。若 $A$ 可逆，$|A||A^{-1}|=|E|=1$，故 $|A|\ne0$。$\blacksquare$

**推论** 对方阵，$AB=E$ 已蕴含 $BA=E$（因为 $|A|\ne0$，$A$ 可逆，$B=A^{-1}AB=A^{-1}$）。

运算规则：$(AB)^{-1}=B^{-1}A^{-1}$，$(A^{T})^{-1}=(A^{-1})^{T}$，$(kA)^{-1}=k^{-1}A^{-1}$，$|A^{-1}|=|A|^{-1}$。

## 4. 分块矩阵

把矩阵用横竖线切成小块，只要切法相容，就可以把小块当作“元素”运算。常用的有：

- **分块对角矩阵** $\operatorname{diag}(A_1,\dots,A_s)$，其逆为 $\operatorname{diag}(A_1^{-1},\dots,A_s^{-1})$，行列式为 $\prod|A_i|$。
- **分块三角矩阵的逆**：
$$\begin{pmatrix}A&C\\O&B\end{pmatrix}^{-1}=\begin{pmatrix}A^{-1}&-A^{-1}CB^{-1}\\O&B^{-1}\end{pmatrix}.$$
- **分块初等变换（“打洞”）**：若 $A$ 可逆，
$$\begin{pmatrix}E&O\\-CA^{-1}&E\end{pmatrix}\begin{pmatrix}A&B\\C&D\end{pmatrix}=\begin{pmatrix}A&B\\O&D-CA^{-1}B\end{pmatrix},$$
所以 $\begin{vmatrix}A&B\\C&D\end{vmatrix}=|A|\,|D-CA^{-1}B|$。$D-CA^{-1}B$ 称为 **Schur 补**，是 CMC 的高频工具。

## 5. 初等矩阵与等价标准形

**定义 4** 单位矩阵经过一次初等变换得到的矩阵称为**初等矩阵**，共三类：$P(i,j)$（交换两行）、$P(i(c))$（第 $i$ 行乘 $c\ne0$）、$P(i,j(k))$（第 $j$ 行的 $k$ 倍加到第 $i$ 行）。

**核心事实** 对 $A$ 做一次初等**行**变换，相当于在 $A$ 的**左边**乘相应的初等矩阵；做一次初等**列**变换，相当于在**右边**乘。（“左行右列”。）初等矩阵都可逆，其逆仍是同类初等矩阵。

为什么是“左行”？回忆行视角：$PA$ 的第 $i$ 行是 $A$ 的各行以 $P$ 的第 $i$ 行为系数的线性组合。$P=P(i,j(k))$ 的第 $i$ 行是 $e_i+ke_j$，于是 $PA$ 的第 $i$ 行恰为“$A$ 的第 $i$ 行加上第 $j$ 行的 $k$ 倍”，其余行不变。记忆口诀之外，最好每种都用 $2\times2$ 的例子亲手乘一遍，留下直观印象。

初等矩阵的逆：$P(i,j)^{-1}=P(i,j)$，$P(i(c))^{-1}=P(i(c^{-1}))$，$P(i,j(k))^{-1}=P(i,j(-k))$——“撤销”一次初等变换，就是做一次相反的初等变换。

**定理 3（等价标准形）** 任一 $m\times n$ 矩阵 $A$ 都可以经过初等行、列变换化为
$$\begin{pmatrix}E_r&O\\O&O\end{pmatrix},$$
其中 $r=r(A)$。即存在可逆矩阵 $P,Q$ 使 $PAQ=\begin{pmatrix}E_r&O\\O&O\end{pmatrix}$。

**推论** (1) $A$ 可逆 $\Leftrightarrow A$ 可写成初等矩阵之积 $\Leftrightarrow A$ 可只用初等行变换化为 $E$。(2) 乘可逆矩阵不改变秩。(3) 两个同型矩阵等价 $\Leftrightarrow$ 秩相等。

**求逆的实用方法** 对 $(A\mid E)$ 做初等行变换，当左边变为 $E$ 时，右边就是 $A^{-1}$。原理：若 $P_s\cdots P_1A=E$，则 $P_s\cdots P_1=A^{-1}$，同样的变换作用在 $E$ 上就得到 $A^{-1}$。

## 6. 秩不等式

以下不等式是 CMC 的常客（$A$ 为 $m\times n$，$B$ 为 $n\times p$）：

1. $r(A+B)\le r(A)+r(B)$（同型时）；
2. $r(AB)\le\min\{r(A),r(B)\}$；
3. **Sylvester 不等式**：$r(AB)\ge r(A)+r(B)-n$；
4. 若 $AB=O$，则 $r(A)+r(B)\le n$（3 的特例）；
5. $r\begin{pmatrix}A&O\\O&B\end{pmatrix}=r(A)+r(B)$，$r\begin{pmatrix}A&O\\C&B\end{pmatrix}\ge r(A)+r(B)$。

**Sylvester 不等式的证明** 思路：用分块初等变换（不改变秩）把一个容易估计的矩阵变成一个秩容易算的矩阵。
$$\begin{pmatrix}A&O\\E_n&B\end{pmatrix}\xrightarrow{\ r_1-A\,r_2\ }\begin{pmatrix}O&-AB\\E_n&B\end{pmatrix}\xrightarrow{\ c_2-c_1B\ }\begin{pmatrix}O&-AB\\E_n&O\end{pmatrix}.$$
（第一步是左乘 $\begin{pmatrix}E_m&-A\\O&E_n\end{pmatrix}$，第二步是右乘 $\begin{pmatrix}E_n&-B\\O&E_p\end{pmatrix}$，都是可逆矩阵。）右端秩为 $n+r(AB)$，左端秩 $\ge r(A)+r(B)$（性质 5），所以 $r(A)+r(B)\le n+r(AB)$。$\blacksquare$

性质 4 的另一证法：$AB=O$ 说明 $B$ 的每一列都是 $AX=0$ 的解，而解空间维数为 $n-r(A)$，所以 $r(B)\le n-r(A)$。

## 7. 例题

### 例 1（矩阵的幂，基础）

求 $J=\begin{pmatrix}\lambda&1&0\\0&\lambda&1\\0&0&\lambda\end{pmatrix}$ 的 $k$ 次幂。

**解** 写 $J=\lambda E+N$，$N=\begin{pmatrix}0&1&0\\0&0&1\\0&0&0\end{pmatrix}$，$N^2=\begin{pmatrix}0&0&1\\0&0&0\\0&0&0\end{pmatrix}$，$N^3=O$。由于 $\lambda E$ 与 $N$ **可交换**，可以用二项式定理：
$$J^k=\lambda^kE+k\lambda^{k-1}N+\binom k2\lambda^{k-2}N^2=\begin{pmatrix}\lambda^k&k\lambda^{k-1}&\binom k2\lambda^{k-2}\\0&\lambda^k&k\lambda^{k-1}\\0&0&\lambda^k\end{pmatrix}.$$
这个公式在第八章 Jordan 标准形中会反复用到。

### 例 2（求逆，基础）

用初等行变换求 $A=\begin{pmatrix}1&2\\3&4\end{pmatrix}$ 的逆。

**解**
$$\left(\begin{array}{cc|cc}1&2&1&0\\3&4&0&1\end{array}\right)\to\left(\begin{array}{cc|cc}1&2&1&0\\0&-2&-3&1\end{array}\right)\to\left(\begin{array}{cc|cc}1&0&-2&1\\0&1&\frac32&-\frac12\end{array}\right),$$
$A^{-1}=\begin{pmatrix}-2&1\\\frac32&-\frac12\end{pmatrix}$。（核对：$\dfrac{1}{|A|}A^{*}=\dfrac{1}{-2}\begin{pmatrix}4&-2\\-3&1\end{pmatrix}$。）

### 例 3（伴随矩阵的性质，必会）

设 $A$ 为 $n$ 阶方阵（$n\ge2$）。证明：

(1) $|A^{*}|=|A|^{n-1}$；
(2) $r(A^{*})=\begin{cases}n,&r(A)=n,\\1,&r(A)=n-1,\\0,&r(A)<n-1.\end{cases}$

**证明** (2) 先做。若 $r(A)=n$，$|A^{*}|=|A|^{n-1}\ne0$（见 (1) 的可逆情形）。若 $r(A)<n-1$，所有 $n-1$ 阶子式为零，$A^{*}=O$。若 $r(A)=n-1$：一方面有非零的 $n-1$ 阶子式，$A^{*}\ne O$，$r(A^{*})\ge1$；另一方面 $AA^{*}=|A|E=O$，由秩不等式 $r(A)+r(A^{*})\le n$，得 $r(A^{*})\le1$。

(1) 取行列式 $|A||A^{*}|=|A|^n$。若 $|A|\ne0$，两边除以 $|A|$ 即得。若 $|A|=0$，由 (2)，$r(A^{*})\le1<n$，故 $|A^{*}|=0=|A|^{n-1}$。$\blacksquare$

类似地可证：$A$ 可逆时 $(A^{*})^{*}=|A|^{n-2}A$。

### 例 4（CMC 风格：幂等矩阵的秩）

设 $n$ 阶方阵 $A$ 满足 $A^2=A$，证明 $r(A)+r(E-A)=n$。

**证明** “$\le$”：$A(E-A)=A-A^2=O$，由秩不等式 4，$r(A)+r(E-A)\le n$。

“$\ge$”：$A+(E-A)=E$，由秩不等式 1，$n=r(E)\le r(A)+r(E-A)$。$\blacksquare$

同样的思路可证：若 $A^2=E$，则 $r(A+E)+r(A-E)=n$。

### 例 5（CMC 风格：Schur 补与行列式恒等式）

设 $A$ 为 $m\times n$，$B$ 为 $n\times m$ 矩阵。证明
$$|E_m-AB|=|E_n-BA|.$$

**思路** 对同一个分块矩阵 $\begin{pmatrix}E_m&A\\B&E_n\end{pmatrix}$，分别“打掉”左下角和右上角，得到两种表达。

**证明**
$$\begin{pmatrix}E_m&O\\-B&E_n\end{pmatrix}\begin{pmatrix}E_m&A\\B&E_n\end{pmatrix}=\begin{pmatrix}E_m&A\\O&E_n-BA\end{pmatrix},$$
$$\begin{pmatrix}E_m&-A\\O&E_n\end{pmatrix}\begin{pmatrix}E_m&A\\B&E_n\end{pmatrix}=\begin{pmatrix}E_m-AB&O\\B&E_n\end{pmatrix}.$$
左边乘的都是行列式为 $1$ 的矩阵，取行列式得 $\begin{vmatrix}E_m&A\\B&E_n\end{vmatrix}=|E_n-BA|=|E_m-AB|$。$\blacksquare$

**推论** 把 $A$ 换成 $\dfrac1\lambda A$（$\lambda\neq 0$）可得 $\lambda^n|\lambda E_m-AB|=\lambda^m|\lambda E_n-BA|$：$AB$ 与 $BA$ 的非零特征值（连同重数）完全相同。

### 例 6（CMC 风格：秩一矩阵）

设 $\alpha,\beta$ 为 $n$ 维非零列向量，$A=\alpha\beta^{T}$。求 $A^k$ 和 $|E+A|$。

**解** 注意 $\beta^{T}\alpha=c$ 是一个数。由结合律
$$A^k=\alpha(\beta^{T}\alpha)(\beta^{T}\alpha)\cdots(\beta^{T}\alpha)\beta^{T}=c^{k-1}A.$$
在例 5 中取 $A=-\alpha$（$n\times1$ 矩阵）、$B=\beta^{T}$，得，$|E_n+\alpha\beta^{T}|=|E_1+\beta^{T}\alpha|=1+\beta^{T}\alpha$。

### 拓展：与给定矩阵可交换的矩阵

求所有与 $A=\begin{pmatrix}1&1\\0&1\end{pmatrix}$ 可交换的 $2$ 阶矩阵。

**解** 设 $B=\begin{pmatrix}a&b\\c&d\end{pmatrix}$。注意 $A=E+N$，$N=\begin{pmatrix}0&1\\0&0\end{pmatrix}$，而 $E$ 与一切矩阵可交换，所以 $AB=BA\Leftrightarrow NB=BN$。计算
$$NB=\begin{pmatrix}c&d\\0&0\end{pmatrix},\qquad BN=\begin{pmatrix}0&a\\0&c\end{pmatrix}.$$
比较得 $c=0$，$d=a$。所以
$$B=\begin{pmatrix}a&b\\0&a\end{pmatrix}=aE+bN,\quad a,b\text{ 任意}.$$
这个结果有一个漂亮的解读：与 $A$ 可交换的矩阵恰好都是 $A$ 的多项式（$aE+bN=(a-b)E+bA$）。这种“把 $A$ 拆成 $E$ 加一个简单矩阵”的技巧，可以大大减少计算量。

## 8. 易错点

1. **$(A+B)^2\ne A^2+2AB+B^2$**，除非 $AB=BA$。同理 $(AB)^k\ne A^kB^k$，二项式定理只对可交换矩阵成立。
2. **$AB=O$ 推不出 $A=O$ 或 $B=O$**；$AB=AC$ 推不出 $B=C$（除非 $A$ 可逆）。
3. **伴随矩阵是代数余子式的“转置排列”**，并且别忘了符号 $(-1)^{i+j}$。
4. **$|A^{*}|=|A|^{n-1}$，$(kA)^{*}=k^{n-1}A^{*}$**，常被误写为 $|A|^n$、$kA^{*}$。
5. **左乘是行变换，右乘是列变换**。求逆时只能用行变换（或只用列变换对 $\begin{pmatrix}A\\E\end{pmatrix}$），不能混用。
6. **分块矩阵相乘要注意块的顺序**，块本身是矩阵，不能交换。
7. **$|A+B|\ne|A|+|B|$**；$\begin{vmatrix}A&B\\C&D\end{vmatrix}=|AD-BC|$ 一般不成立（$A$、$C$ 可交换时等特殊情形才成立）。

## 本章小结与学习建议

**矩阵运算的三个“不”**：不可交换（$AB\ne BA$）、无零因子律（$AB=O\not\Rightarrow A=O$ 或 $B=O$）、无消去律（$AB=AC\not\Rightarrow B=C$）。初学者的大多数错误都出在不自觉地把数的运算律套到矩阵上。反过来，**当两个矩阵可交换时**，数的运算律（如二项式定理、因式分解 $A^2-B^2=(A-B)(A+B)$）又都可以用了，例 1 正是利用了 $\lambda E$ 与 $N$ 可交换。

**可逆的十种说法**：对 $n$ 阶方阵 $A$，以下互相等价，必须烂熟于心：

1. $A$ 可逆；
2. $|A|\ne0$；
3. $r(A)=n$；
4. $A$ 的行（列）向量组线性无关；
5. $AX=0$ 只有零解；
6. 对任意 $\beta$，$AX=\beta$ 有唯一解；
7. $A$ 可表示为初等矩阵之积；
8. $A$ 只用初等行变换可化为 $E$；
9. $A$ 的等价标准形是 $E$；
10. $0$ 不是 $A$ 的特征值（第七章）。

**分块矩阵是 CMC 的“万能钥匙”**。很多看似与分块无关的问题（行列式乘法定理、Sylvester 不等式、$|E-AB|=|E-BA|$），都靠构造一个合适的 $2\times2$ 分块矩阵，再用分块初等变换“打洞”来解决。打洞的要领是：**左乘作用于块行，右乘作用于块列**，乘的初等块矩阵行列式为 $1$（或已知），秩不变。练熟以下两个模型：
$$\begin{pmatrix}A&B\\C&D\end{pmatrix}\to\begin{pmatrix}A&B\\O&D-CA^{-1}B\end{pmatrix},\qquad\begin{pmatrix}A&O\\E&B\end{pmatrix}\to\begin{pmatrix}O&-AB\\E&O\end{pmatrix}.$$

**秩不等式的证明套路**有三种，例 3、例 4 和 Sylvester 不等式各示范了一种：

- **利用列空间**：$AB$ 的列是 $A$ 的列的组合，得 $r(AB)\le r(A)$；
- **利用解空间**：$AB=O$ 说明 $B$ 的列在 $AX=0$ 的解空间里，得 $r(B)\le n-r(A)$；
- **利用分块矩阵**：把要比较的量放进同一个分块矩阵，用初等变换比较。

**伴随矩阵**的一切性质几乎都从 $AA^{*}=A^{*}A=|A|E$ 推出。可逆时 $A^{*}=|A|A^{-1}$，把伴随矩阵问题转化为逆矩阵问题；不可逆时，用秩来讨论（例 3 的分类）。

**学习建议**：本章计算量大、公式多，建议自己整理一张“公式卡”，包括 $(AB)^{T}$、$(AB)^{-1}$、$(AB)^{*}=B^{*}A^{*}$、$|kA|$、$|A^{*}|$、$(kA)^{*}$、$(A^{*})^{*}$ 等，并对每一条都能说出推导的一两句理由，而不是死记。

## 9. 自测清单

1. 设 $A$ 是 $3$ 阶方阵，$|A|=3$，求 $|2A^{*}|$ 和 $|A^{-1}+A^{*}|$。
2. 若 $A^2-A-2E=O$，证明 $A$ 可逆并求 $A^{-1}$。
3. $A$ 为 $5\times4$ 矩阵，$r(A)=3$，$B$ 为 $4$ 阶可逆矩阵，求 $r(AB)$。
4. 设 $A$ 为 $n$ 阶非零矩阵，$A^2=O$，问 $r(A)$ 至多是多少？
5. 求 $\begin{pmatrix}O&A\\B&O\end{pmatrix}^{-1}$（$A,B$ 可逆方阵）。
6. 若 $A$ 为 $4$ 阶方阵，$r(A)=2$，求 $r(A^{*})$。
7. 设 $\alpha=(1,2,3)^{T}$，$\beta=(1,1,1)^{T}$，求 $(\alpha\beta^{T})^{10}$。

## 参考答案

1. $|2A^{*}|=2^3|A|^2=72$。$A^{*}=|A|A^{-1}=3A^{-1}$，$|A^{-1}+3A^{-1}|=4^3|A|^{-1}=\dfrac{64}{3}$。
2. $A(A-E)=2E$，所以 $A^{-1}=\dfrac12(A-E)$。
3. 乘可逆矩阵不改变秩，$r(AB)=3$。
4. $A\cdot A=O\Rightarrow 2r(A)\le n$，$r(A)\le\left\lfloor\dfrac n2\right\rfloor$。
5. $\begin{pmatrix}O&B^{-1}\\A^{-1}&O\end{pmatrix}$（直接相乘验证）。
6. $r(A)=2<n-1=3$，所以 $A^{*}=O$，$r(A^{*})=0$。
7. $\beta^{T}\alpha=6$，所以 $(\alpha\beta^{T})^{10}=6^9\alpha\beta^{T}=6^9\begin{pmatrix}1&1&1\\2&2&2\\3&3&3\end{pmatrix}$。
