---
title: 数项级数
subject: 数学分析
chapter: 10
prerequisites: 数列极限、反常积分
minutes: 75
---

# 第 10 章　数项级数

## 为什么要学

芝诺悖论说：阿基里斯要追上乌龟，先得跑完一半路程，再跑剩下的一半，再一半……无穷多段路，怎么可能跑完？答案是：$\frac12+\frac14+\frac18+\cdots$ 这无穷多项**加起来等于 $1$**——无穷多个数相加，结果可以是有限的。

但也要小心：$1+\frac12+\frac13+\frac14+\cdots$ 每一项都越来越小，加起来却是无穷大。而 $1-1+1-1+\cdots$ 怎么加？括号这样加是 $0$，那样加是 $1$。

所以"无穷求和"不能想当然。我们要用**极限**来定义它：先加前 $n$ 项，再让 $n\to\infty$。这样一来，级数就是一种特殊的数列，第 2 章的所有工具都能用上。这一章的任务是建立一整套"判断收敛还是发散"的工具，你会发现它们和上一章的反常积分判别法一一对应。

级数是后面幂级数、Fourier 级数的基础，也是 CMC 的必考内容。

## 10.1 定义与基本性质

**定义 10.1** 给定数列 $\{u_n\}$，称 $\sum\limits_{n=1}^\infty u_n$ 为**级数**，$S_n=u_1+\cdots+u_n$ 为**部分和**。若 $S_n\to S$，称级数**收敛**，和为 $S$；否则**发散**。

**例 1（几何级数）** $\sum\limits_{n=0}^\infty q^n$ 当 $|q|<1$ 时收敛于 $\dfrac1{1-q}$，当 $|q|\ge1$ 时发散。（$S_n=\frac{1-q^{n+1}}{1-q}$。）

**定理 10.1（必要条件）** 若 $\sum u_n$ 收敛，则 $u_n\to0$。

*证明*：$u_n=S_n-S_{n-1}\to S-S=0$。$\blacksquare$

**这只是必要条件！** 调和级数 $\sum\frac1n$ 满足 $\frac1n\to0$，但发散：
$$S_{2^k}=1+\frac12+\left(\frac13+\frac14\right)+\left(\frac15+\cdots+\frac18\right)+\cdots\ge1+\frac k2.$$
每个括号里有 $2^{j-1}$ 项、每项至少 $\frac1{2^j}$，所以每个括号至少 $\frac12$。

**Cauchy 准则**：$\sum u_n$ 收敛 $\iff\forall\varepsilon>0,\exists N,\forall n>N,\forall p\ge1:\ |u_{n+1}+\cdots+u_{n+p}|<\varepsilon$。

**基本性质**：
- 线性：收敛级数可以逐项相加、数乘；
- 改变有限项不影响敛散性（但影响和）；
- **收敛级数可以任意加括号**（加括号后的部分和是原部分和的子列）；**反之不然**：$(1-1)+(1-1)+\cdots$ 收敛，原级数发散。

## 10.2 正项级数

若 $u_n\ge0$，则 $S_n$ 单调递增，所以：**正项级数收敛 $\iff$ 部分和有界。** 下面所有判别法都是这句话的推论。

**定理 10.2（比较判别法）** 设 $0\le u_n\le v_n$（$n$ 充分大），则 $\sum v_n$ 收敛 $\Rightarrow\sum u_n$ 收敛；$\sum u_n$ 发散 $\Rightarrow\sum v_n$ 发散。

**极限形式**：$u_n,v_n>0$，$\dfrac{u_n}{v_n}\to l\in(0,+\infty)$，则同敛散。

**基准级数（$p$-级数）**：$\sum\dfrac1{n^p}$ 当 $p>1$ 时收敛，$p\le1$ 时发散。

**定理 10.3（积分判别法）** 设 $f$ 在 $[1,+\infty)$ 上非负递减，$u_n=f(n)$，则 $\sum u_n$ 与 $\int_1^{+\infty}f(x)\,\mathrm dx$ 同敛散。

**想法**：画一排宽为 $1$ 的矩形，$f(n+1)\le\int_n^{n+1}f\le f(n)$，级数和积分互相夹住。用 $f(x)=x^{-p}$ 立即得到 $p$-级数的结论。

**定理 10.4（比值判别法，d'Alembert）** 设 $u_n>0$，$\lim\dfrac{u_{n+1}}{u_n}=\rho$。$\rho<1$ 收敛，$\rho>1$ 发散，$\rho=1$ 无法判断。

**定理 10.5（根值判别法，Cauchy）** 设 $u_n\ge0$，$\varlimsup\sqrt[n]{u_n}=\rho$。$\rho<1$ 收敛，$\rho>1$ 发散，$\rho=1$ 无法判断。

**比值判别法的证明**（$\rho<1$）：取 $q$ 使 $\rho<q<1$，则 $n\ge N$ 时 $u_{n+1}<qu_n$，于是 $u_n<u_Nq^{n-N}$，与收敛的几何级数比较。**核心思想：和几何级数比较。** 正因如此，对于 $\sum\frac1{n^p}$ 这类"比几何级数慢得多"的级数，比值和根值判别法都给出 $\rho=1$，失效。

> 选择指南：含 $n!$、$a^n$ 的乘积型 → 比值法；含 $n$ 次幂 $(\ldots)^n$ → 根值法；含 $n^p$、$\ln n$ 的"多项式型" → 与 $p$-级数比较或积分判别法。

## 10.3 一般项级数

**定理 10.6（Leibniz 判别法）** 若 $u_n$ 单调递减且 $u_n\to0$，则交错级数 $\sum(-1)^{n-1}u_n$ 收敛，且余项 $|S-S_n|\le u_{n+1}$。

**证明**：偶数项部分和 $S_{2m}=(u_1-u_2)+\cdots+(u_{2m-1}-u_{2m})$ 递增，又 $S_{2m}=u_1-(u_2-u_3)-\cdots-u_{2m}\le u_1$，有界，所以 $S_{2m}\to S$。而 $S_{2m+1}=S_{2m}+u_{2m+1}\to S$。奇偶子列极限相同，故 $S_n\to S$。$\blacksquare$

例如 $1-\frac12+\frac13-\cdots=\ln2$ 收敛。

**绝对收敛与条件收敛**：若 $\sum|u_n|$ 收敛，称 $\sum u_n$ **绝对收敛**；收敛但不绝对收敛称**条件收敛**。绝对收敛必收敛（Cauchy 准则 + 三角不等式）。

**Abel 变换（分部求和）**：
$$\sum_{k=1}^na_kb_k=A_nb_n-\sum_{k=1}^{n-1}A_k(b_{k+1}-b_k),\qquad A_k=a_1+\cdots+a_k.$$
这是分部积分的离散版本，由它得到：

**定理 10.7（Dirichlet 判别法）** 若 $\sum a_n$ 的部分和有界，$\{b_n\}$ 单调趋于 $0$，则 $\sum a_nb_n$ 收敛。

**定理 10.8（Abel 判别法）** 若 $\sum a_n$ 收敛，$\{b_n\}$ 单调有界，则 $\sum a_nb_n$ 收敛。

Leibniz 判别法是 Dirichlet 判别法在 $a_n=(-1)^{n-1}$ 时的特例。

**例 2** 证明 $\sum\limits_{n=1}^\infty\dfrac{\sin nx}{n}$ 对任意 $x$ 收敛。

**证明**：$x=2k\pi$ 时每项为 $0$。否则，用积化和差：
$$2\sin\frac x2\sum_{k=1}^n\sin kx=\sum_{k=1}^n\left[\cos\left(k-\tfrac12\right)x-\cos\left(k+\tfrac12\right)x\right]=\cos\frac x2-\cos\left(n+\tfrac12\right)x,$$
所以 $\left|\sum_{k=1}^n\sin kx\right|\le\dfrac1{|\sin\frac x2|}$，部分和有界；$\frac1n$ 单调趋于 $0$，由 Dirichlet 判别法收敛。$\blacksquare$

**重排**：
- **绝对收敛级数任意重排，和不变**（可以像有限和一样随意交换次序）。
- **Riemann 重排定理**：条件收敛级数经过适当重排，可以收敛到任意指定的实数，甚至发散到 $\pm\infty$。

这个惊人的结论说明：条件收敛的"和"依赖于求和顺序，它的收敛靠的是正负项的精妙平衡。这也是为什么数学家如此看重"绝对收敛"。

## 例题

**例 3（比较判别法）** 判断 $\sum\limits_{n=1}^\infty\left(1-\cos\dfrac1n\right)$ 和 $\sum\limits_{n=2}^\infty\dfrac1{\ln n}$。

**解**：$1-\cos\frac1n\sim\frac1{2n^2}$，与 $\sum\frac1{n^2}$ 同敛散，收敛。$\frac1{\ln n}>\frac1n$（$n\ge2$），调和级数发散，故发散。

**例 4（比值与根值）** 判断 $\sum\dfrac{n!}{n^n}$ 和 $\sum\left(\dfrac{n}{2n+1}\right)^n$。

**解**：$\dfrac{u_{n+1}}{u_n}=\dfrac{(n+1)!}{(n+1)^{n+1}}\cdot\dfrac{n^n}{n!}=\left(\dfrac n{n+1}\right)^n\to\dfrac1e<1$，收敛。

$\sqrt[n]{u_n}=\dfrac n{2n+1}\to\dfrac12<1$，收敛。

**例 5（含参数的综合判断）** 讨论 $\sum\limits_{n=2}^\infty\dfrac{(-1)^n}{n^p+(-1)^n}$（$p>0$）的敛散性。

**分析**：看起来像交错级数，但分母里的 $(-1)^n$ 破坏了单调性，Leibniz 不能直接用。用 Taylor 展开拆出主项：
$$\frac{(-1)^n}{n^p+(-1)^n}=\frac{(-1)^n}{n^p}\cdot\frac1{1+\frac{(-1)^n}{n^p}}=\frac{(-1)^n}{n^p}-\frac1{n^{2p}}+O\left(\frac1{n^{3p}}\right).$$
- $\sum\frac{(-1)^n}{n^p}$ 对 $p>0$ 收敛（Leibniz）；
- $\sum\frac1{n^{2p}}$ 当 $p>\frac12$ 收敛，$p\le\frac12$ 发散；
- 余项 $O\left(\frac1{n^{3p}}\right)$：当 $p>\frac12$ 时 $3p>1$，绝对收敛；当 $p\le\frac12$ 时，$-\frac1{n^{2p}}+O\left(\frac1{n^{3p}}\right)$ 合起来仍是负的、且 $\sim-\frac1{n^{2p}}$，用极限比较法得发散。

**结论**：$p>1$ 绝对收敛；$\frac12<p\le1$ 条件收敛；$0<p\le\frac12$ 发散。

> 这个例子说明：**"$\sum a_n$ 收敛且 $a_n\sim b_n$"不能推出 $\sum b_n$ 收敛**——等价比较只对**正项**级数有效。$p=\frac12$ 时，$\frac{(-1)^n}{\sqrt n+(-1)^n}\sim\frac{(-1)^n}{\sqrt n}$，后者收敛，前者却发散。

**例 6（CMC 风格）** 设 $a_n>0$，$\sum a_n$ 发散，$S_n=a_1+\cdots+a_n$。证明 $\sum\dfrac{a_n}{S_n}$ 发散，而 $\sum\dfrac{a_n}{S_n^2}$ 收敛。

**证明**：（1）由 Cauchy 准则，对任意 $n$ 与 $p$：
$$\sum_{k=n+1}^{n+p}\frac{a_k}{S_k}\ge\frac1{S_{n+p}}\sum_{k=n+1}^{n+p}a_k=1-\frac{S_n}{S_{n+p}}.$$
固定 $n$，由于 $S_{n+p}\to+\infty$（$p\to\infty$），可取 $p$ 使右边 $>\frac12$。所以 Cauchy 准则不成立，级数发散。

（2）对 $n\ge2$，
$$\frac{a_n}{S_n^2}\le\frac{a_n}{S_{n-1}S_n}=\frac{S_n-S_{n-1}}{S_{n-1}S_n}=\frac1{S_{n-1}}-\frac1{S_n}.$$
右边求和是**裂项相消**：$\sum_{n=2}^N\left(\frac1{S_{n-1}}-\frac1{S_n}\right)=\frac1{S_1}-\frac1{S_N}\le\frac1{a_1}$。部分和有界，故收敛。$\blacksquare$

> 这道题的启示：没有"最慢发散的级数"——由 $\sum a_n$ 发散可以造出 $\sum\frac{a_n}{S_n}$，它发散得更慢；也没有"最慢收敛的级数"。所以任何比较判别法都不可能是万能的。

## 易错点

1. **$u_n\to0$ 不能推出收敛。** 只能用它判断发散（$u_n\not\to0$ 则发散）。
2. **比值/根值法 $\rho=1$ 时失效**，此时要换方法，不能下结论。
3. **比较判别法和等价代换只能用于正项级数**（或在绝对值上使用）。见例 5。
4. **Leibniz 判别法需要单调性。** 只有 $u_n\to0$ 不够（例 5 就是反例）。
5. **条件收敛级数不能随意重排或交换次序**，也不能随意去括号。
6. **两个发散级数之和可能收敛**；收敛 + 发散 = 发散。

## 自测清单

1. 判断 $\sum\dfrac{1}{n\sqrt[n]{n}}$ 的敛散性。
2. 判断 $\sum\dfrac{2^nn!}{n^n}$ 的敛散性。
3. $\sum\dfrac{(-1)^n}{\ln n}$（$n\ge2$）绝对收敛、条件收敛还是发散？
4. 若 $\sum a_n$ 收敛，$\sum a_n^2$ 一定收敛吗？若 $a_n\ge0$ 呢？
5. 若 $\sum a_n^2$ 和 $\sum b_n^2$ 都收敛，证明 $\sum a_nb_n$ 绝对收敛。
6. 判断 $\sum\limits_{n=2}^\infty\dfrac1{n\ln^pn}$ 何时收敛。
7. 求 $\sum\limits_{n=1}^\infty\dfrac{1}{n(n+1)}$。

## 参考答案

### 点击展开参考答案

1. $\sqrt[n]n\to1$，与 $\sum\frac1n$ 极限比较，发散。
2. 比值 $=2\left(\frac n{n+1}\right)^n\to\frac2e<1$，收敛。
3. 条件收敛：Leibniz 判别法给出收敛；$\frac1{\ln n}>\frac1n$ 说明不绝对收敛。
4. 一般不一定：$a_n=\frac{(-1)^n}{\sqrt n}$，$\sum a_n$ 收敛但 $\sum a_n^2=\sum\frac1n$ 发散。若 $a_n\ge0$ 则一定：$a_n\to0$，充分大时 $a_n^2\le a_n$。
5. $|a_nb_n|\le\frac12(a_n^2+b_n^2)$，比较判别法。
6. 积分判别法：$\int_2^{+\infty}\frac{\mathrm dx}{x\ln^px}=\int_{\ln2}^{+\infty}\frac{\mathrm du}{u^p}$，$p>1$ 收敛，$p\le1$ 发散。
7. 裂项 $\frac1n-\frac1{n+1}$，和为 $1$。


