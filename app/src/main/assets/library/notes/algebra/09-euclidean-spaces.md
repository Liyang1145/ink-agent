---
title: 欧氏空间
subject: 高等代数
chapter: 9
prerequisites: 二次型, 线性空间, 线性变换
minutes: 90
---

# 第九章 欧氏空间

## 为什么要学

线性空间只有加法和数乘，**没有长度和角度**。在抽象的线性空间里，你无法说两个向量“垂直”，也无法说一个向量“有多长”。可是几何中最有力的工具——勾股定理、投影、最短距离——都离不开长度和角度。

欧氏空间就是在实线性空间上加一个**内积**，把中学的“数量积” $\vec a\cdot\vec b=|\vec a||\vec b|\cos\theta$ 推广到 $n$ 维和抽象空间（例如函数空间，$\int_a^bf(x)g(x)\,\mathrm dx$ 也是内积）。有了内积：

- 可以找**标准正交基**——两两垂直的单位向量，就像 $x,y,z$ 坐标轴，计算变得极其简单；
- 可以定义**正交变换**——保持长度和角度的变换，即旋转和反射；
- 可以证明本课程最漂亮的定理之一：**实对称矩阵一定能用正交矩阵对角化**。几何上，这意味着每个二次曲面都能通过**旋转**坐标轴化为标准形（第五章的配方法允许“拉伸”，这里只用旋转，所以还能保住长度和形状）；
- 可以解决“方程组无解时找最接近的解”（最小二乘法），这是数据拟合的基础。

## 1. 内积与欧氏空间

**定义 1** 设 $V$ 是实数域上的线性空间。若对任意 $\alpha,\beta\in V$ 有一个实数 $(\alpha,\beta)$ 与之对应，满足

1. 对称性：$(\alpha,\beta)=(\beta,\alpha)$；
2. 线性：$(k\alpha,\beta)=k(\alpha,\beta)$，$(\alpha+\beta,\gamma)=(\alpha,\gamma)+(\beta,\gamma)$；
3. 正定性：$(\alpha,\alpha)\ge0$，且等号仅当 $\alpha=0$ 时成立，

称 $(\alpha,\beta)$ 为**内积**，$V$ 称为**欧几里得空间**（欧氏空间）。

**例**：(1) $\mathbb{R}^n$ 上的标准内积 $(\alpha,\beta)=a_1b_1+\cdots+a_nb_n=\alpha^{T}\beta$；(2) $C[a,b]$ 上 $(f,g)=\int_a^bf(x)g(x)\,\mathrm dx$；(3) 设 $A$ 为 $n$ 阶正定矩阵，$(\alpha,\beta)=\alpha^{T}A\beta$ 也是 $\mathbb{R}^n$ 上的内积——第五章的“正定”在这里找到了归宿。

**长度** $|\alpha|=\sqrt{(\alpha,\alpha)}$。$|\alpha|=1$ 称为单位向量；$\dfrac{\alpha}{|\alpha|}$ 称为把 $\alpha$ 单位化。

**定理 1（Cauchy–Schwarz 不等式）** 对任意 $\alpha,\beta\in V$，
$$|(\alpha,\beta)|\le|\alpha|\,|\beta|,$$
等号成立当且仅当 $\alpha,\beta$ 线性相关。

**思路** 考虑实变量 $t$ 的二次函数 $(\alpha+t\beta,\alpha+t\beta)\ge0$；一个恒非负的二次函数，判别式 $\le0$。

**证明** 若 $\beta=0$，两边都为 $0$。设 $\beta\ne0$，对任意实数 $t$，
$$0\le(\alpha+t\beta,\alpha+t\beta)=(\beta,\beta)t^2+2(\alpha,\beta)t+(\alpha,\alpha).$$
这是首项系数为正、恒非负的二次函数，判别式 $4(\alpha,\beta)^2-4(\alpha,\alpha)(\beta,\beta)\le0$，即得不等式。等号成立 $\Leftrightarrow$ 判别式为零 $\Leftrightarrow$ 存在 $t_0$ 使 $(\alpha+t_0\beta,\alpha+t_0\beta)=0\Leftrightarrow\alpha=-t_0\beta$，即线性相关。$\blacksquare$

由此可以定义**夹角** $\langle\alpha,\beta\rangle=\arccos\dfrac{(\alpha,\beta)}{|\alpha||\beta|}\in[0,\pi]$（$\alpha,\beta$ 非零），并得到**三角不等式** $|\alpha+\beta|\le|\alpha|+|\beta|$。

若 $(\alpha,\beta)=0$，称 $\alpha$ 与 $\beta$ **正交**，记 $\alpha\perp\beta$。此时有**勾股定理** $|\alpha+\beta|^2=|\alpha|^2+|\beta|^2$。

**度量矩阵** 设 $\varepsilon_1,\dots,\varepsilon_n$ 是一组基，$G=\big((\varepsilon_i,\varepsilon_j)\big)$ 称为度量矩阵（Gram 矩阵）。若 $\alpha,\beta$ 的坐标为 $X,Y$，则 $(\alpha,\beta)=X^{T}GY$。$G$ 是正定矩阵；换基 $\eta=\varepsilon T$ 后度量矩阵变为 $T^{T}GT$，与 $G$ **合同**。

## 2. 标准正交基与 Gram–Schmidt 正交化

**定义 2** 两两正交的非零向量组称为正交向量组；由单位向量组成的正交基称为**标准正交基**。

**性质** 正交向量组必线性无关：若 $\sum k_i\alpha_i=0$，与 $\alpha_j$ 作内积得 $k_j(\alpha_j,\alpha_j)=0$，故 $k_j=0$。

在标准正交基 $\varepsilon_1,\dots,\varepsilon_n$ 下，一切都很简单：
$$\alpha=\sum_{i=1}^n(\alpha,\varepsilon_i)\varepsilon_i,\qquad(\alpha,\beta)=X^{T}Y,$$
即坐标就是与各基向量的内积，内积就是坐标的“点积”（度量矩阵为 $E$）。

**定理 2（Gram–Schmidt 正交化）** 设 $\alpha_1,\dots,\alpha_m$ 线性无关，令
$$\beta_1=\alpha_1,\qquad\beta_k=\alpha_k-\sum_{j=1}^{k-1}\frac{(\alpha_k,\beta_j)}{(\beta_j,\beta_j)}\beta_j\quad(k=2,\dots,m),$$
则 $\beta_1,\dots,\beta_m$ 两两正交、非零，且对每个 $k$，$L(\beta_1,\dots,\beta_k)=L(\alpha_1,\dots,\alpha_k)$。再单位化就得到标准正交组。

**思路** 每一步把 $\alpha_k$ 减去它在前面已正交化的方向上的投影 $\dfrac{(\alpha_k,\beta_j)}{(\beta_j,\beta_j)}\beta_j$，剩下的部分自然与前面都垂直。

**证明** 归纳。设 $\beta_1,\dots,\beta_{k-1}$ 已两两正交、非零，且张成 $L(\alpha_1,\dots,\alpha_{k-1})$。对 $i<k$，
$$(\beta_k,\beta_i)=(\alpha_k,\beta_i)-\frac{(\alpha_k,\beta_i)}{(\beta_i,\beta_i)}(\beta_i,\beta_i)=0$$
（其他项因 $(\beta_j,\beta_i)=0$ 消失）。若 $\beta_k=0$，则 $\alpha_k\in L(\beta_1,\dots,\beta_{k-1})=L(\alpha_1,\dots,\alpha_{k-1})$，与无关性矛盾。生成空间相等是因为 $\beta_k$ 与 $\alpha_k$ 相差 $L(\alpha_1,\dots,\alpha_{k-1})$ 中的向量。$\blacksquare$

**推论** 每个有限维欧氏空间都有标准正交基；任一标准正交组都可扩充成标准正交基。

## 3. 正交矩阵与正交变换

**定义 3** 若实方阵 $Q$ 满足 $Q^{T}Q=E$，称为**正交矩阵**。

等价说法：$Q^{-1}=Q^{T}$；$Q$ 的列（或行）向量组是 $\mathbb{R}^n$ 的标准正交基。正交矩阵的行列式为 $\pm1$；正交矩阵之积、逆仍是正交矩阵。**两组标准正交基之间的过渡矩阵是正交矩阵。**

**定义 4** 欧氏空间的线性变换 $\sigma$ 若保持内积，即 $(\sigma\alpha,\sigma\beta)=(\alpha,\beta)$，称为**正交变换**。

**定理 3** 以下等价：(1) $\sigma$ 是正交变换；(2) $\sigma$ 保持长度 $|\sigma\alpha|=|\alpha|$；(3) $\sigma$ 把标准正交基变成标准正交基；(4) $\sigma$ 在标准正交基下的矩阵是正交矩阵。

（(2)⇒(1)：用“极化恒等式” $(\alpha,\beta)=\dfrac12\big(|\alpha+\beta|^2-|\alpha|^2-|\beta|^2\big)$，保长度就保内积。）

$|Q|=1$ 的称为旋转（第一类），$|Q|=-1$ 的称为第二类（含反射）。

## 4. 正交补

**定义 5** 若 $\beta$ 与子空间 $W$ 中每个向量正交，记 $\beta\perp W$。$W^{\perp}=\{\beta:\beta\perp W\}$ 称为 $W$ 的**正交补**。

**定理 4** 对有限维欧氏空间的子空间 $W$，$V=W\oplus W^{\perp}$，且 $(W^{\perp})^{\perp}=W$。

（取 $W$ 的标准正交基扩充为 $V$ 的标准正交基，新增部分恰好张成 $W^{\perp}$。）

于是每个 $\alpha$ 可唯一写成 $\alpha=\alpha_1+\alpha_2$，$\alpha_1\in W$，$\alpha_2\perp W$。$\alpha_1$ 称为 $\alpha$ 在 $W$ 上的**正交投影**，它是 $W$ 中**离 $\alpha$ 最近**的向量：对任意 $\gamma\in W$，
$$|\alpha-\gamma|^2=|\alpha_2|^2+|\alpha_1-\gamma|^2\ge|\alpha-\alpha_1|^2.$$

**最小二乘法** 方程组 $AX=\beta$ 可能无解。寻找使 $|AX-\beta|$ 最小的 $X$，就是找 $\beta$ 在 $A$ 的列空间上的投影 $AX_0$，条件是 $\beta-AX_0$ 与 $A$ 的每一列正交：
$$A^{T}(\beta-AX_0)=0\iff A^{T}AX_0=A^{T}\beta.$$
这个“法方程”总有解（第三章例 5：$r(A^{T}A)=r(A)$，可证 $r(A^{T}A)=r(A^{T}A\mid A^{T}\beta)$）。

## 5. 实对称矩阵的正交对角化

这是本章乃至整门课的高潮。

**引理 1** 实对称矩阵的特征值都是实数。

**证明** 设 $AX=\lambda X$，$X\ne0$ 为复向量。取共轭：$A\bar X=\bar\lambda\bar X$（$A$ 是实矩阵）。计算 $\bar X^{T}AX$ 两种方式：
$$\bar X^{T}AX=\lambda\bar X^{T}X,\qquad\bar X^{T}AX=(A^{T}\bar X)^{T}X=(A\bar X)^{T}X=\bar\lambda\bar X^{T}X.$$
而 $\bar X^{T}X=\sum|x_i|^2>0$，所以 $\lambda=\bar\lambda$，$\lambda$ 是实数。$\blacksquare$

**引理 2** 实对称矩阵属于不同特征值的特征向量正交。

**证明** $AX=\lambda X$，$AY=\mu Y$，$\lambda\ne\mu$。则 $\lambda X^{T}Y=(AX)^{T}Y=X^{T}AY=\mu X^{T}Y$，故 $(\lambda-\mu)X^{T}Y=0$，$X^{T}Y=0$。$\blacksquare$

**定理 5（谱定理）** 对任一 $n$ 阶实对称矩阵 $A$，存在正交矩阵 $Q$ 使
$$Q^{T}AQ=Q^{-1}AQ=\operatorname{diag}(\lambda_1,\dots,\lambda_n),$$
其中 $\lambda_i$ 是 $A$ 的全部特征值。

**思路** 归纳：先找一个单位特征向量（引理 1 保证存在实的），把它作为第一个基向量扩充成标准正交基；由对称性，矩阵变成 $\begin{pmatrix}\lambda_1&0\\0&A_1\end{pmatrix}$，$A_1$ 仍是实对称的，对它用归纳假设。

**证明** 对 $n$ 归纳，$n=1$ 显然。由引理 1，$A$ 有实特征值 $\lambda_1$ 和实的单位特征向量 $\xi_1$。把 $\xi_1$ 扩充为 $\mathbb{R}^n$ 的标准正交基 $\xi_1,\dots,\xi_n$，令 $Q_1=(\xi_1,\dots,\xi_n)$，它是正交矩阵。$Q_1^{T}AQ_1$ 的第一列是 $Q_1^{T}A\xi_1=\lambda_1Q_1^{T}\xi_1=\lambda_1e_1$；又 $Q_1^{T}AQ_1$ 是对称的，所以第一行也是 $(\lambda_1,0,\dots,0)$：
$$Q_1^{T}AQ_1=\begin{pmatrix}\lambda_1&0\\0&A_1\end{pmatrix},$$
$A_1$ 为 $n-1$ 阶实对称矩阵。由归纳假设，存在 $n-1$ 阶正交矩阵 $Q_2$ 使 $Q_2^{T}A_1Q_2$ 为对角阵。令 $Q=Q_1\begin{pmatrix}1&0\\0&Q_2\end{pmatrix}$，它是正交矩阵，且 $Q^{T}AQ$ 为对角阵。$\blacksquare$

**二次型的版本（主轴定理）** 任一实二次型 $X^{T}AX$ 都可经**正交替换** $X=QY$ 化为
$$\lambda_1y_1^2+\lambda_2y_2^2+\cdots+\lambda_ny_n^2,$$
系数恰为 $A$ 的特征值。因此：**$A$ 正定 $\Leftrightarrow$ 特征值全为正**；正惯性指数 = 正特征值个数。

**实际操作步骤**：(1) 求特征值；(2) 对每个特征值求特征子空间的基；(3) 对**重特征值**的那组基做 Gram–Schmidt 正交化（不同特征值之间已自动正交）；(4) 全部单位化，按列排成 $Q$。

## 6. 例题

### 例 1（Gram–Schmidt，基础）

把 $\alpha_1=(1,1,0,0)$，$\alpha_2=(1,0,1,0)$，$\alpha_3=(-1,0,0,1)$ 化为标准正交组。

**解** $\beta_1=\alpha_1=(1,1,0,0)$。

$\beta_2=\alpha_2-\dfrac{(\alpha_2,\beta_1)}{(\beta_1,\beta_1)}\beta_1=(1,0,1,0)-\dfrac12(1,1,0,0)=\left(\dfrac12,-\dfrac12,1,0\right)$。

$(\alpha_3,\beta_1)=-1$，$(\alpha_3,\beta_2)=-\dfrac12$，$(\beta_2,\beta_2)=\dfrac32$，所以
$$\beta_3=\alpha_3+\frac12\beta_1+\frac13\beta_2=\left(-\frac13,\frac13,\frac13,1\right).$$
（检验：$(\beta_3,\beta_1)=0$，$(\beta_3,\beta_2)=-\dfrac16-\dfrac16+\dfrac13=0$。）单位化：
$$\eta_1=\frac{1}{\sqrt2}(1,1,0,0),\quad\eta_2=\frac{1}{\sqrt6}(1,-1,2,0),\quad\eta_3=\frac{1}{2\sqrt3}(-1,1,1,3).$$

### 例 2（正交对角化，必会）

设 $A=\begin{pmatrix}0&-1&1\\-1&0&1\\1&1&0\end{pmatrix}$，求正交矩阵 $Q$ 使 $Q^{T}AQ$ 为对角阵。

**解** $|\lambda E-A|=(\lambda-1)^2(\lambda+2)$，特征值 $1$（二重）、$-2$。

$\lambda=1$：$(E-A)X=0$，$E-A=\begin{pmatrix}1&1&-1\\1&1&-1\\-1&-1&1\end{pmatrix}$，即 $x_1+x_2-x_3=0$。基础解系 $\alpha_1=(-1,1,0)^{T}$，$\alpha_2=(1,0,1)^{T}$。正交化：
$$\beta_1=\alpha_1,\qquad\beta_2=\alpha_2-\frac{(\alpha_2,\beta_1)}{(\beta_1,\beta_1)}\beta_1=(1,0,1)^{T}+\frac12(-1,1,0)^{T}=\frac12(1,1,2)^{T}.$$

$\lambda=-2$：$(-2E-A)X=0$，得 $\alpha_3=(1,1,-1)^{T}$（验证 $A\alpha_3=(-2,-2,2)^{T}$）。它自动与 $\beta_1,\beta_2$ 正交。

单位化后得
$$Q=\begin{pmatrix}-\dfrac{1}{\sqrt2}&\dfrac{1}{\sqrt6}&\dfrac{1}{\sqrt3}\\\dfrac{1}{\sqrt2}&\dfrac{1}{\sqrt6}&\dfrac{1}{\sqrt3}\\0&\dfrac{2}{\sqrt6}&-\dfrac{1}{\sqrt3}\end{pmatrix},\qquad Q^{T}AQ=\begin{pmatrix}1&0&0\\0&1&0\\0&0&-2\end{pmatrix}.$$
对应地，二次型 $f=-2x_1x_2+2x_1x_3+2x_2x_3$ 经正交替换化为 $y_1^2+y_2^2-2y_3^2$。

### 例 3（正定性与特征值）

设 $A$ 为 $n$ 阶实对称矩阵，证明：当 $t$ 充分大时 $tE+A$ 正定。

**证明** 设 $A$ 的特征值为 $\lambda_1,\dots,\lambda_n$（都是实数），则 $tE+A$ 的特征值为 $t+\lambda_i$。取 $t>\max_i|\lambda_i|$，全部为正，由主轴定理 $tE+A$ 正定。$\blacksquare$

### 例 4（CMC 风格：正交矩阵的特征值）

设 $Q$ 为 $n$ 阶正交矩阵，$|Q|=-1$。证明 $-1$ 是 $Q$ 的特征值。

**思路** 要证 $|E+Q|=0$。把 $E$ 写成 $Q^{T}Q$，提出公因子，得到 $|E+Q|$ 等于它自己的相反数。

**证明**
$$|E+Q|=|Q^{T}Q+Q|=|(Q^{T}+E)Q|=|Q^{T}+E|\,|Q|=-|(Q+E)^{T}|=-|E+Q|,$$
所以 $|E+Q|=0$，即 $|(-1)E-Q|=(-1)^n|E+Q|=0$，$-1$ 是特征值。$\blacksquare$

类似地：若 $|Q|=1$ 且 $n$ 为奇数，则 $1$ 是 $Q$ 的特征值。（几何意义：三维空间中的旋转必有一条转轴。）另外，正交矩阵的复特征值模长都是 $1$。

### 例 5（CMC 风格：正定矩阵的平方根）

设 $A$ 为正定矩阵，证明存在正定矩阵 $B$ 使 $A=B^2$。

**证明** 由谱定理，$A=Q\operatorname{diag}(\lambda_1,\dots,\lambda_n)Q^{T}$，$\lambda_i>0$。令
$$B=Q\operatorname{diag}(\sqrt{\lambda_1},\dots,\sqrt{\lambda_n})Q^{T}.$$
$B$ 对称，特征值 $\sqrt{\lambda_i}>0$，所以正定；且 $B^2=Q\operatorname{diag}(\lambda_1,\dots,\lambda_n)Q^{T}=A$（用到 $Q^{T}Q=E$）。$\blacksquare$

（还可以证明这样的 $B$ 唯一，有兴趣可以思考：$B$ 的每个特征子空间都包含在 $A$ 的某个特征子空间里。）

### 例 6（CMC 风格：Rayleigh 商）

设 $A$ 为 $n$ 阶实对称矩阵，最大、最小特征值分别为 $\lambda_{\max},\lambda_{\min}$。证明对任意非零 $X\in\mathbb{R}^n$，
$$\lambda_{\min}\le\frac{X^{T}AX}{X^{T}X}\le\lambda_{\max}.$$

**证明** 作正交替换 $X=QY$，$Q^{T}AQ=\operatorname{diag}(\lambda_1,\dots,\lambda_n)$。正交替换保持长度，$X^{T}X=Y^{T}Y$，且
$$X^{T}AX=\sum\lambda_iy_i^2\le\lambda_{\max}\sum y_i^2=\lambda_{\max}X^{T}X.$$
下界同理。等号分别在对应的特征向量处取到。$\blacksquare$

这说明 $\max\limits_{|X|=1}X^{T}AX=\lambda_{\max}$，二次型在单位球面上的最值就是特征值——在多元微积分中求条件极值时会再见到它。

## 7. 易错点

1. **内积必须正定**。$(\alpha,\beta)=a_1b_1-a_2b_2$ 不是 $\mathbb{R}^2$ 的内积（$(e_2,e_2)=-1$）。
2. **正交化之后要单位化**，否则 $Q$ 不是正交矩阵。
3. **只需对同一特征值内部做正交化**；不同特征值的特征向量自动正交（对实对称矩阵而言）。对一般矩阵这不成立。
4. **正交矩阵是 $Q^{T}Q=E$，不是 $Q^2=E$**；也不是“列向量两两正交”就行，还要每列是单位向量。
5. **正交相似 $\Rightarrow$ 相似且合同**。所以对实对称矩阵，相似 $\Leftrightarrow$ 特征值相同（含重数）；合同 $\Leftrightarrow$ 正、负、零特征值个数分别相同。
6. **正交变换的“保持长度”是对内积给出的长度而言**，换了内积，正交变换的集合也就变了。
7. **主轴定理中的系数必须是特征值**；而配方法（第五章）得到的标准形系数一般不是特征值，两者只有惯性指数相同。

## 本章小结与学习建议

**本章把前面的线索汇合在一起**：

- 第五章的**正定矩阵** = 本章的**内积**（度量矩阵）；
- 第六章的**直和与补空间** = 本章的**正交补**（但正交补是唯一的，而一般补空间不唯一）；
- 第七章的**对角化** = 本章的**正交对角化**（实对称矩阵总能做到）；
- 第五章的**合同标准形** = 本章的**主轴定理**（系数恰是特征值）。

**实对称矩阵的“特权”**，务必记牢：特征值都是实数；不同特征值的特征向量正交；一定可以对角化，而且可以用正交矩阵；几何重数一定等于代数重数。对一般的实矩阵，这些都不一定成立。

**正交对角化计算的检查清单**：

1. 特征值之和是否等于迹？乘积是否等于行列式？
2. 每个特征向量代回 $AX=\lambda X$ 是否成立？
3. 重特征值的特征向量是否做了正交化？
4. 每一列是否都单位化了？
5. $Q$ 的列的顺序是否与对角阵中特征值的顺序一致？

**欧氏空间的思想在别处**：最小二乘法用于数据拟合；Rayleigh 商（例 6）用于多元函数的条件极值；函数空间中的正交多项式与 Fourier 级数，本质上就是无限维欧氏空间中的“标准正交基”与“坐标”。数学分析课程里会再见到它们。

**学习建议**：Gram–Schmidt 正交化的计算容易出错，每算出一个 $\beta_k$，都立刻检查它与前面各向量的内积是否为零。证明题中，“把 $E$ 写成 $Q^{T}Q$”（例 4）和“正交替换保持长度”（例 6）这两个技巧非常常用。

## 8. 自测清单

1. 在 $\mathbb{R}^3$ 中，与 $(1,1,1)$ 和 $(1,-1,0)$ 都正交的单位向量有哪些？
2. 证明：若 $\alpha\perp\beta_1,\dots,\beta_s$，则 $\alpha$ 与 $L(\beta_1,\dots,\beta_s)$ 中每个向量正交。
3. $A$ 为 $3$ 阶实对称矩阵，特征值为 $1,2,3$，属于 $1,2$ 的特征向量分别为 $(1,1,0)^{T},(1,-1,0)^{T}$，求属于 $3$ 的一个特征向量。
4. 实对称矩阵 $A$ 满足 $A^2=O$，证明 $A=O$。
5. 若 $Q$ 是正交矩阵，$\lambda$ 是它的实特征值，$\lambda$ 可能取什么值？
6. 设 $A,B$ 都是 $n$ 阶实对称矩阵，特征值相同，问它们是否相似？是否合同？
7. 求 $f=2x_1^2+2x_2^2+2x_1x_2$ 在 $x_1^2+x_2^2=1$ 上的最大值和最小值。

## 参考答案

1. 向量积 $(1,1,1)\times(1,-1,0)=(1,1,-2)$，所以是 $\pm\dfrac{1}{\sqrt6}(1,1,-2)$。（或解方程组 $x_1+x_2+x_3=0,\ x_1-x_2=0$。）
2. $\left(\alpha,\sum k_i\beta_i\right)=\sum k_i(\alpha,\beta_i)=0$。
3. 属于不同特征值的特征向量正交，所求向量与 $(1,1,0),(1,-1,0)$ 都正交，可取 $(0,0,1)^{T}$。
4. 法一：$A$ 的特征值 $\lambda$ 满足 $\lambda^2=0$，全为 $0$，正交对角化得 $A=Q\,O\,Q^{T}=O$。法二：$A^{T}A=A^2=O$，对角元 $\sum_ka_{ki}^2=0$，所以每个元素为 $0$。
5. $QX=\lambda X$，$|QX|=|X|$，所以 $|\lambda|=1$，$\lambda=\pm1$。
6. 都是：两者都正交相似于同一个对角阵 $\operatorname{diag}(\lambda_1,\dots,\lambda_n)$，故相似；又正交相似即合同，故合同。
7. 矩阵 $\begin{pmatrix}2&1\\1&2\end{pmatrix}$ 的特征值为 $3,1$。由例 6，最大值 $3$（在 $\pm\frac{1}{\sqrt2}(1,1)$ 处），最小值 $1$（在 $\pm\frac{1}{\sqrt2}(1,-1)$ 处）。
