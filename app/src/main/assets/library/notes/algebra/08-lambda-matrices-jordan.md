---
title: λ-矩阵与Jordan标准形
subject: 高等代数
chapter: 8
prerequisites: 多项式, 线性变换
minutes: 90
---

# 第八章 $\lambda$-矩阵与 Jordan 标准形

## 为什么要学

上一章留下了一个缺口：不是每个矩阵都能对角化。比如错切
$$\begin{pmatrix}1&1\\0&1\end{pmatrix}$$
只有一个特征方向，怎么换基也变不成对角阵。那么，**不能对角化的矩阵，最简单能化成什么样？两个矩阵什么时候相似？**

答案是 **Jordan 标准形**：在复数域上，每个矩阵都相似于若干个“Jordan 块”拼成的分块对角阵，Jordan 块形如
$$J(\lambda_0,k)=\begin{pmatrix}\lambda_0&1&&\\&\lambda_0&\ddots&\\&&\ddots&1\\&&&\lambda_0\end{pmatrix}_{k\times k}.$$
它“几乎”是对角阵，只在对角线上方多了一条 $1$。几何上，一个 Jordan 块描述的是：沿着一条“链” $\xi_1\leftarrow\xi_2\leftarrow\cdots\leftarrow\xi_k$，变换 $\sigma-\lambda_0\varepsilon$ 把每个向量推到前一个，最后把 $\xi_1$ 推到零。$k=1$ 时就是普通的特征向量。

为了严格地判断相似，本章引入一个巧妙的工具：把特征矩阵 $\lambda E-A$ 看作元素为**多项式**的矩阵（$\lambda$-矩阵），在它上面做初等变换。**两个数字矩阵相似，当且仅当它们的特征矩阵作为 $\lambda$-矩阵等价**。这把一个难的问题（是否存在可逆 $T$ 使 $T^{-1}AT=B$）变成了一个可计算的问题（比较几个多项式），而计算这些多项式用的正是第一章的整除与最大公因式。

## 1. $\lambda$-矩阵

**定义 1** 元素是 $P[\lambda]$ 中多项式的矩阵称为 **$\lambda$-矩阵**，记为 $A(\lambda)$。它的秩是不恒为零的子式的最高阶数。

**可逆性** $A(\lambda)$ 可逆（存在 $\lambda$-矩阵 $B(\lambda)$ 使 $AB=BA=E$）$\Leftrightarrow|A(\lambda)|$ 是**非零常数**。

这与数字矩阵不同：$\begin{pmatrix}\lambda&0\\0&1\end{pmatrix}$ 满秩但不可逆，因为它的逆 $\begin{pmatrix}\lambda^{-1}&0\\0&1\end{pmatrix}$ 不是多项式矩阵。（理由：$|A||B|=1$，两个多项式乘积为 $1$，只能都是非零常数。）

**初等变换**（三种，行列都可以）：

1. 交换两行（列）；
2. 用**非零常数** $c$ 乘某行（列）——注意，不能乘一个多项式；
3. 把某行（列）的 $\varphi(\lambda)$ 倍加到另一行（列），$\varphi$ 是任意多项式。

若 $A(\lambda)$ 能经过有限次初等变换变成 $B(\lambda)$，称二者**等价**，记 $A(\lambda)\cong B(\lambda)$。等价 $\Leftrightarrow$ 存在可逆 $\lambda$-矩阵 $P(\lambda),Q(\lambda)$ 使 $B=PAQ$。

## 2. Smith 标准形

**定理 1** 任一秩为 $r$ 的 $n\times n$ 的 $\lambda$-矩阵都等价于
$$\begin{pmatrix}d_1(\lambda)&&&&&\\&d_2(\lambda)&&&&\\&&\ddots&&&\\&&&d_r(\lambda)&&\\&&&&0&\\&&&&&\ddots\end{pmatrix},$$
其中 $d_i(\lambda)$ 首项系数为 $1$，且 $d_1\mid d_2\mid\cdots\mid d_r$。称为 **Smith 标准形**，$d_i$ 称为**不变因子**。

**证明思路** 像在整数矩阵上做“辗转相除”：
1. 用交换把次数最低的非零元素移到左上角 $a_{11}(\lambda)$。
2. 若第一列某元素 $a_{i1}$ 不被 $a_{11}$ 整除，做带余除法 $a_{i1}=qa_{11}+r$，用第 $i$ 行减去第 $1$ 行的 $q$ 倍，得到次数更低的余式 $r$，换到左上角重来。次数每次严格下降，必然终止。第一行同理。
3. 第一行、第一列都被 $a_{11}$ 整除后，消成零。若右下角还有元素不被 $a_{11}$ 整除，把那一行加到第一行，回到第 2 步。
4. 最终左上角 $d_1$ 整除剩下的所有元素，再对右下角的小矩阵归纳。

**定义 2** $A(\lambda)$ 中所有 $k$ 阶子式的首一最大公因式 $D_k(\lambda)$ 称为 **$k$ 阶行列式因子**。

**定理 2** 等价的 $\lambda$-矩阵有相同的各阶行列式因子。

**证明思路** 只需对每种初等变换验证 $D_k$ 不变。交换、乘常数只使子式变号或乘非零常数。第三种：把第 $j$ 行的 $\varphi$ 倍加到第 $i$ 行，新矩阵的每个 $k$ 阶子式，要么不含第 $i$ 行（不变）；要么同时含第 $i$、$j$ 行（由行列式性质不变）；要么含第 $i$ 行不含第 $j$ 行，此时等于“原子式 $\pm\varphi\times$ 另一个原 $k$ 阶子式”。总之新的 $k$ 阶子式都是原 $k$ 阶子式的组合，都被原 $D_k$ 整除，故新 $D_k$ 被原 $D_k$ 整除；由于变换可逆，反过来也成立。$\blacksquare$

对 Smith 标准形直接计算，利用整除链得 $D_k=d_1d_2\cdots d_k$，所以
$$d_k(\lambda)=\frac{D_k(\lambda)}{D_{k-1}(\lambda)}\quad(D_0=1).$$
**推论** Smith 标准形唯一；两个 $\lambda$-矩阵等价 $\Leftrightarrow$ 行列式因子相同 $\Leftrightarrow$ 不变因子相同。

## 3. 相似的判定

**定理 3** 设 $A,B$ 是数域 $P$ 上的 $n$ 阶数字矩阵，则
$$A\sim B\iff\lambda E-A\cong\lambda E-B\iff A,B\text{ 的不变因子相同}.$$

（“$\Rightarrow$”容易：$T^{-1}(\lambda E-A)T=\lambda E-B$，而 $T$ 作为常数 $\lambda$-矩阵是可逆的。“$\Leftarrow$”的证明较长，利用 $\lambda$-矩阵的带余除法，这里略去，初学时承认即可。）

注意 $|\lambda E-A|$ 是首一 $n$ 次多项式，所以 $\lambda E-A$ 满秩，有 $n$ 个不变因子，且
$$d_1(\lambda)d_2(\lambda)\cdots d_n(\lambda)=|\lambda E-A|.$$
**相似是与数域无关的**：不变因子用辗转相除计算，与数域无关，所以两个实矩阵若在复数域上相似，在实数域上也相似。

## 4. 初等因子与 Jordan 标准形

**定义 3** 在复数域上把每个次数 $\ge1$ 的不变因子分解为一次因式的方幂，
$$d_i(\lambda)=(\lambda-\lambda_1)^{k_{i1}}(\lambda-\lambda_2)^{k_{i2}}\cdots,$$
所有指数 $k_{ij}\ge1$ 的方幂 $(\lambda-\lambda_j)^{k_{ij}}$（相同的按出现次数计算）称为 $A$ 的**初等因子**。

由于整除链 $d_1\mid\cdots\mid d_n$，初等因子与不变因子可以互相确定：把每个 $\lambda_j$ 的各个方幂按从大到小的顺序“从 $d_n$ 往前”排入即可。

**引理** Jordan 块 $J=J(\lambda_0,k)$ 的不变因子是 $1,\dots,1,(\lambda-\lambda_0)^k$，初等因子只有一个：$(\lambda-\lambda_0)^k$。

**证明** $D_k=|\lambda E-J|=(\lambda-\lambda_0)^k$。在 $\lambda E-J$ 中划去第 $1$ 列和第 $k$ 行，得到一个下三角子式，对角线全是 $-1$，值为 $(-1)^{k-1}$，所以 $D_{k-1}=1$，从而 $D_1=\cdots=D_{k-1}=1$。$\blacksquare$

**引理** 分块对角阵 $\operatorname{diag}(A_1,\dots,A_s)$ 的初等因子，是各块 $A_i$ 的初等因子合在一起。

**定理 4（Jordan 标准形）** 每个 $n$ 阶复矩阵 $A$ 都相似于一个 Jordan 形矩阵
$$J=\operatorname{diag}\big(J(\lambda_1,k_1),J(\lambda_2,k_2),\dots,J(\lambda_s,k_s)\big),$$
其中 $(\lambda-\lambda_1)^{k_1},\dots,(\lambda-\lambda_s)^{k_s}$ 恰是 $A$ 的全部初等因子。除去 Jordan 块的排列顺序外，$J$ 由 $A$ 唯一确定。

**证明** 由两个引理，$J$ 的初等因子正是 $A$ 的初等因子，于是二者的不变因子相同，由定理 3，$A\sim J$。唯一性：Jordan 形由初等因子决定，而初等因子是相似不变量。$\blacksquare$

**用秩来数 Jordan 块（实用方法）** 对特征值 $\lambda_0$，记 $r_k=r\big((A-\lambda_0E)^k\big)$，$r_0=n$，则
$$\text{大小}\ \ge k\ \text{的}\ \lambda_0\text{-Jordan 块个数}=r_{k-1}-r_k.$$
特别地，$\lambda_0$ 的 Jordan 块总数 $=n-r(A-\lambda_0E)=$ 几何重数；块的大小之和 = 代数重数。

（理由：对单个块 $J(\lambda_0,m)$，$(J-\lambda_0E)^k$ 的秩是 $\max(m-k,0)$，每多乘一次，大小 $\ge k$ 的块的秩各减 $1$；其他特征值的块可逆，秩不变。）

## 5. 最小多项式与对角化

**定理 5** $A$ 的最小多项式等于它的最后一个不变因子 $d_n(\lambda)$。

对 Jordan 块直接验证：$J(\lambda_0,k)-\lambda_0E=N$ 满足 $N^{k-1}\ne O$，$N^k=O$，所以 $J(\lambda_0,k)$ 的最小多项式是 $(\lambda-\lambda_0)^k$。分块对角阵的最小多项式是各块最小多项式的**最小公倍式**。所以：

> **最小多项式中 $(\lambda-\lambda_0)$ 的次数 = $\lambda_0$ 的最大 Jordan 块的阶数。**

**定理 6** 复矩阵 $A$ 可对角化 $\iff$ 最小多项式无重根 $\iff$ 初等因子全是一次的。

**证明** $A$ 可对角化 $\iff$ Jordan 块都是 $1$ 阶 $\iff$ 每个特征值的最大块阶数为 $1$ $\iff$ 最小多项式中每个因子 $(\lambda-\lambda_0)$ 只出现一次。$\blacksquare$

**推论** 若存在无重根的多项式 $g$ 使 $g(A)=O$，则 $A$ 可对角化（因为最小多项式整除 $g$，也无重根）。

## 6. 例题

### 例 1（计算 Jordan 形，必会）

求 $A=\begin{pmatrix}-1&1&0\\-4&3&0\\1&0&2\end{pmatrix}$ 的不变因子、初等因子和 Jordan 标准形，并求 $T$ 使 $T^{-1}AT=J$。

**解** $\lambda E-A=\begin{pmatrix}\lambda+1&-1&0\\4&\lambda-3&0\\-1&0&\lambda-2\end{pmatrix}$。

$D_1=1$（有元素 $-1$）。二阶子式中，取第 $1,3$ 行、第 $1,2$ 列：$\begin{vmatrix}\lambda+1&-1\\-1&0\end{vmatrix}=-1$，所以 $D_2=1$。$D_3=|\lambda E-A|=(\lambda-2)\big[(\lambda+1)(\lambda-3)+4\big]=(\lambda-2)(\lambda-1)^2$。

不变因子：$1,\ 1,\ (\lambda-1)^2(\lambda-2)$；初等因子：$(\lambda-1)^2,\ \lambda-2$。
$$J=\begin{pmatrix}1&1&0\\0&1&0\\0&0&2\end{pmatrix}.$$
最小多项式 $(\lambda-1)^2(\lambda-2)$ 有重根，不可对角化。

**求 $T$**：设 $T=(\xi_1,\xi_2,\xi_3)$，由 $AT=TJ$ 得
$$A\xi_1=\xi_1,\qquad A\xi_2=\xi_1+\xi_2,\qquad A\xi_3=2\xi_3.$$
先解 $(A-E)\xi_1=0$：$A-E=\begin{pmatrix}-2&1&0\\-4&2&0\\1&0&1\end{pmatrix}$，取 $\xi_1=(-1,-2,1)^{T}$。再解 $(A-E)\xi_2=\xi_1$，可取 $\xi_2=(1,1,0)^{T}$（验证：$(-2+1,-4+2,1)=(-1,-2,1)$）。最后 $(A-2E)\xi_3=0$，取 $\xi_3=(0,0,1)^{T}$。于是
$$T=\begin{pmatrix}-1&1&0\\-2&1&0\\1&0&1\end{pmatrix},\qquad T^{-1}AT=J.$$

用秩法核对：$r(A-E)=2$，所以特征值 $1$ 的 Jordan 块只有 $3-2=1$ 个，而代数重数为 $2$，只能是一个 $2$ 阶块。

### 例 2（Jordan 形的分类）

(1) 特征多项式为 $(\lambda-2)^3$ 的 $3$ 阶复矩阵，按相似分为几类？
(2) 说明 $4$ 阶时“特征多项式 + 最小多项式”不足以决定相似类。

**解** (1) 特征值 $2$ 的块大小之和为 $3$：$3=3=2+1=1+1+1$，共三类：
$$J(2,3),\qquad\operatorname{diag}(J(2,2),2),\qquad 2E.$$
它们的最小多项式分别是 $(\lambda-2)^3,(\lambda-2)^2,\lambda-2$，互不相同。所以 $3$ 阶时特征多项式与最小多项式一起就能决定 Jordan 形。

(2) $\operatorname{diag}(J(0,2),J(0,2))$ 与 $\operatorname{diag}(J(0,2),0,0)$：特征多项式都是 $\lambda^4$，最小多项式都是 $\lambda^2$，但前者秩为 $2$、后者秩为 $1$，不相似。

### 例 3（有限阶矩阵可对角化）

设 $A$ 为复矩阵，$A^m=E$（$m$ 为正整数）。证明 $A$ 可对角化。

**证明** $g(\lambda)=\lambda^m-1$ 满足 $g(A)=O$。$g'(\lambda)=m\lambda^{m-1}$，唯一的根 $0$ 不是 $g$ 的根，故 $(g,g')=1$，$g$ 无重根（第一章）。由定理 6 的推论，$A$ 可对角化。$\blacksquare$

注：在实数域上结论不成立（旋转 $90^\circ$ 满足 $A^4=E$，但没有实特征值），这是因为“分解为一次因式”的条件在 $\mathbb{R}$ 上可能不满足。

### 例 4（幂零矩阵）

设 $n$ 阶矩阵 $N$ 满足 $N^2=O$，$r(N)=r$。求 $N$ 的 Jordan 标准形。

**解** 特征值只有 $0$。最小多项式整除 $\lambda^2$，所以 Jordan 块的阶数 $\le2$。设有 $a$ 个 $2$ 阶块、$b$ 个 $1$ 阶块，则 $2a+b=n$。每个 $J(0,2)$ 的秩为 $1$，$J(0,1)$ 的秩为 $0$，故 $a=r$，$b=n-2r$：
$$N\sim\operatorname{diag}\big(\underbrace{J(0,2),\dots,J(0,2)}_{r},\underbrace{0,\dots,0}_{n-2r}\big).$$
（顺带得到 $r\le\dfrac n2$，与第四章自测第 4 题吻合。）

### 例 5（CMC 风格：$A$ 与 $A^{T}$ 相似）

证明任一 $n$ 阶方阵 $A$ 都与 $A^{T}$ 相似。

**证明** $\lambda E-A^{T}=(\lambda E-A)^{T}$。矩阵的 $k$ 阶子式与其转置的对应子式相等，所以 $\lambda E-A$ 与 $(\lambda E-A)^{T}$ 有相同的行列式因子，从而不变因子相同。由定理 3，$A\sim A^{T}$。$\blacksquare$

（这个结论用“直接构造 $T$”很难证，用不变因子则一行搞定——这正是本章工具的威力。）

### 例 6（CMC 风格：Jordan 块的平方）

设 $N=J(0,n)$（$n\ge2$），求 $N^2$ 的 Jordan 标准形。

**解** $N^k$ 的秩为 $\max(n-k,0)$，所以 $M=N^2$ 满足 $r(M^k)=r(N^{2k})=\max(n-2k,0)$。$M$ 的特征值只有 $0$。

Jordan 块总数 $=n-r(M)=n-(n-2)=2$。

大小 $\ge k$ 的块数 $=r(M^{k-1})-r(M^k)$：当 $2k\le n$ 时为 $(n-2k+2)-(n-2k)=2$；当 $2k=n+1$（$n$ 为奇数）时为 $1-0=1$。

所以：$n$ 为偶数时，$N^2\sim\operatorname{diag}\big(J(0,\tfrac n2),J(0,\tfrac n2)\big)$；$n$ 为奇数时，$N^2\sim\operatorname{diag}\big(J(0,\tfrac{n+1}2),J(0,\tfrac{n-1}2)\big)$。

直观理解：$N$ 把基向量推成一条链 $e_n\to e_{n-1}\to\cdots\to e_1\to0$；$N^2$ 每次跳两步，链就按下标奇偶分裂成两条。

## 7. 易错点

1. **$\lambda$-矩阵的初等变换只能用非零常数乘行（列）**，不能乘多项式；但“加上 $\varphi(\lambda)$ 倍”允许任意多项式。
2. **满秩的 $\lambda$-矩阵不一定可逆**，可逆要求行列式是非零常数。
3. **不变因子要求首一并且满足整除链**；随手对角化得到的 $\operatorname{diag}(\lambda-1,\lambda-2)$ 不是 Smith 标准形，正确的是 $\operatorname{diag}\big(1,(\lambda-1)(\lambda-2)\big)$。
4. **初等因子要把相同的方幂重复计数**。例如不变因子 $1,\lambda-1,(\lambda-1)(\lambda+1)$ 的初等因子是 $\lambda-1,\ \lambda-1,\ \lambda+1$。
5. **特征多项式 + 最小多项式在 $n\ge4$ 时不一定决定相似类**（例 2）。
6. **Jordan 块的个数 = 几何重数，最大块的阶数 = 最小多项式中的指数**，两者别弄混。
7. 求过渡矩阵时，链要从**特征向量**开始往上解 $(A-\lambda_0E)\xi_{i+1}=\xi_i$；若先随便取 $\xi_2$ 再算 $\xi_1$，有时会解不出来或不满足要求，要注意 $\xi_1$ 必须落在 $A-\lambda_0E$ 的列空间里。

## 本章小结与学习建议

**相似不变量的层次**（从粗到细）：

1. 特征多项式（含特征值、迹、行列式）；
2. 再加上每个特征值的几何重数（Jordan 块的个数）；
3. 再加上最小多项式（每个特征值的最大块阶数）；
4. 不变因子（等价地：初等因子、Jordan 标准形）——这是**完全**不变量。

前三层都只是必要条件，只有第 4 层能断定相似。在低阶情形，前几层往往已经够用：$2$ 阶时特征多项式加最小多项式即可，$3$ 阶时也够（例 2），$4$ 阶起就不一定了。

**求 Jordan 形的三条路**，可以互相核对：

- **行列式因子法**：算 $D_k$，得不变因子，分解出初等因子。适合 $\lambda E-A$ 中有常数元素、低阶子式容易算的情形。
- **初等变换法**：直接把 $\lambda E-A$ 化成 Smith 标准形。
- **秩法**：对每个特征值 $\lambda_0$ 计算 $r\big((A-\lambda_0E)^k\big)$，数出各种大小的块。数字矩阵的计算中通常最快（例 1 的核对、例 6）。

**为什么本章是全书的“收官”**：第七章问“一个线性变换最简单能用什么矩阵表示”，答案在这里完整给出：在复数域上，就是 Jordan 形；可对角化只是“所有 Jordan 块都是 $1$ 阶”的特殊情形。从此，涉及矩阵多项式、矩阵幂、可交换性的问题，都可以先化成 Jordan 形再处理，因为相似保持这些性质。

**学习建议**：Smith 标准形的存在性证明理解思路即可，不必死记细节；要熟练的是：(1) 由行列式因子求不变因子；(2) 由不变因子与初等因子互相转换；(3) 写出 Jordan 形并能用秩法验证；(4) 用“最小多项式无重根”判断可对角化。

## 8. 自测清单

1. $\begin{pmatrix}\lambda&0\\0&\lambda+1\end{pmatrix}$ 的 Smith 标准形是什么？
2. 某 $5$ 阶矩阵的初等因子为 $(\lambda-1)^2,\ \lambda-1,\ (\lambda+2)^2$，写出它的不变因子与最小多项式。
3. 若 $A$ 的最小多项式为 $(\lambda-1)^2$，$A$ 为 $3$ 阶，写出 Jordan 形。
4. 若 $A^2=A$，用最小多项式说明 $A$ 可对角化。
5. $3$ 阶矩阵 $A$ 的特征值都是 $0$，$r(A)=1$，求 Jordan 形。
6. 两个 $3$ 阶矩阵特征多项式相同、最小多项式相同，是否一定相似？
7. 写出 $J(\lambda_0,3)^m$（用第四章例 1 的公式）。

## 参考答案

1. $D_1=(\lambda,\lambda+1)=1$，$D_2=\lambda(\lambda+1)$，Smith 标准形为 $\operatorname{diag}\big(1,\lambda(\lambda+1)\big)$。
2. 共 $5$ 个不变因子，从最后一个开始放入各特征值的最高方幂：$d_5=(\lambda-1)^2(\lambda+2)^2$，$d_4=\lambda-1$，$d_1=d_2=d_3=1$。最小多项式为 $(\lambda-1)^2(\lambda+2)^2$。
3. 最大块阶数为 $2$，块大小之和为 $3$：$\operatorname{diag}(J(1,2),1)$。
4. 最小多项式整除 $\lambda^2-\lambda=\lambda(\lambda-1)$，无重根。
5. 块数 $=3-1=2$，大小之和为 $3$：$\operatorname{diag}(J(0,2),0)$。
6. $3$ 阶时一定相似（例 2 的讨论对一般特征值同样成立：每个特征值的代数重数 $\le3$，块的划分由最大块阶数唯一确定）。
7. $\begin{pmatrix}\lambda_0^m&m\lambda_0^{m-1}&\binom m2\lambda_0^{m-2}\\0&\lambda_0^m&m\lambda_0^{m-1}\\0&0&\lambda_0^m\end{pmatrix}$。
