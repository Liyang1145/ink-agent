---
title: 实数与确界原理
subject: 数学分析
chapter: 1
prerequisites: 无（高中数学）
minutes: 60
---

# 第 1 章　实数与确界原理

## 为什么要学

高中时我们天天用实数，却从没认真问过：**实数到底是什么？它和有理数差在哪里？**

想象一条数轴。有理数在上面"密密麻麻"：任意两个有理数之间还有无穷多个有理数。可是它们并没有把数轴填满——$\sqrt 2$ 就落在一个"缝隙"里。用有理数 $1, 1.4, 1.41, 1.414,\dots$ 去逼近它，这串数越挤越紧，但"终点"不在有理数里。

数学分析的所有核心概念——极限、连续、导数、积分——本质上都是"无限逼近之后，终点存在"。如果终点可能不存在，整座大厦就塌了。所以第一章要做的事情只有一件：**把"实数没有缝隙"这个直觉，说成一句可以拿来证明定理的话。** 这句话就是**确界原理**。

后面你会看到，单调有界定理、闭区间套定理、Bolzano–Weierstrass 定理、Cauchy 收敛准则、闭区间上连续函数的最值定理……全部都是从确界原理一路推出来的。这一章是地基，值得慢慢读。

## 1.1 有界集、上确界与下确界

**定义 1.1（有界）** 设 $S\subseteq\mathbb R$ 非空。若存在 $M\in\mathbb R$，使对所有 $x\in S$ 都有 $x\le M$，称 $S$ **有上界**，$M$ 是 $S$ 的一个上界。类似定义下界。既有上界又有下界称为**有界**，等价于存在 $K>0$ 使 $|x|\le K$ 对所有 $x\in S$ 成立。

一个集合如果有上界，它就有无穷多个上界（$M$ 是上界，$M+1$ 也是）。我们关心的是**最紧的那个**。

**定义 1.2（上确界）** 设 $S$ 非空。称 $\beta$ 为 $S$ 的**上确界**，记作 $\beta=\sup S$，如果

1. $\beta$ 是 $S$ 的上界：$\forall x\in S,\ x\le\beta$；
2. 任何比 $\beta$ 小的数都不是上界：$\forall \varepsilon>0,\ \exists x_0\in S,\ x_0>\beta-\varepsilon$。

第 2 条也可写成"若 $M$ 是 $S$ 的上界，则 $\beta\le M$"，即 **$\sup S$ 是最小上界**。类似地，**下确界** $\inf S$ 是最大下界：

1. $\forall x\in S,\ x\ge\alpha$；
2. $\forall\varepsilon>0,\ \exists x_0\in S,\ x_0<\alpha+\varepsilon$。

> 图像：把 $S$ 的点画在数轴上，从右边拿一块"挡板"往左推，推到再也推不动（再往左就会越过某个 $S$ 中的点）时，挡板的位置就是 $\sup S$。

注意 $\sup S$ **不一定属于** $S$。例如 $S=(0,1)$，$\sup S=1\notin S$。当 $\sup S\in S$ 时，它就是 $S$ 的最大值 $\max S$。

**唯一性**：若 $\beta_1,\beta_2$ 都是上确界，则两者都是上界，又都是最小上界，所以 $\beta_1\le\beta_2$ 且 $\beta_2\le\beta_1$。

## 1.2 确界原理

**公理（确界原理）** 实数集 $\mathbb R$ 的任何非空、有上界的子集都有上确界（在 $\mathbb R$ 中）。

在许多教材里，这是作为实数的一条公理（"完备性公理"）引入的；也有教材先用 Dedekind 分割或十进制小数构造实数，再把它证成定理。对初学者而言，你只要记住：**它正是有理数所缺少的那条性质**。

**例**：$S=\{x\in\mathbb Q: x^2<2\}$ 在 $\mathbb Q$ 中有上界（比如 $2$），却没有有理数的最小上界；在 $\mathbb R$ 中，$\sup S=\sqrt2$。

**推论（下确界）** 非空有下界的集合必有下确界。

*证明思路*：把集合"翻过来"。令 $-S=\{-x:x\in S\}$，它非空有上界，于是 $\sup(-S)$ 存在，可以验证 $\inf S=-\sup(-S)$。

## 1.3 确界原理的第一批推论

### 阿基米德性质

**定理 1.1** 对任意 $a>0$ 和任意 $b\in\mathbb R$，存在正整数 $n$ 使 $na>b$。

**想法**：如果"$na$ 永远追不上 $b$"，那么 $\{na\}$ 有上界，就有上确界 $\beta$；但 $\beta-a$ 已经不是上界了，再往后走一步 $a$ 就越过 $\beta$，矛盾。

**证明**：反设对一切 $n\in\mathbb N^+$ 都有 $na\le b$，则 $S=\{na:n\in\mathbb N^+\}$ 非空有上界，令 $\beta=\sup S$。因为 $a>0$，$\beta-a<\beta$ 不是上界，故存在 $m$ 使 $ma>\beta-a$，即 $(m+1)a>\beta$。但 $(m+1)a\in S$，与 $\beta$ 是上界矛盾。$\blacksquare$

**推论**：$\forall\varepsilon>0,\ \exists n,\ \dfrac1n<\varepsilon$。这一句话在后面证明极限时会用上百次。

### 有理数的稠密性

**定理 1.2** 任意两个实数 $a<b$ 之间必有有理数。

**证明**：由阿基米德性质取 $n$ 使 $n(b-a)>1$。于是区间 $(na,nb)$ 长度大于 $1$，必含一个整数 $m$（取 $m=\lfloor na\rfloor+1$，则 $na<m\le na+1<nb$）。于是 $a<\dfrac mn<b$。$\blacksquare$

同理，任意两个实数之间也有无理数（对 $a-\sqrt2<b-\sqrt2$ 用定理，再加回 $\sqrt2$）。

## 1.4 确界的运算性质

设 $A,B$ 非空有界，记 $A+B=\{a+b:a\in A,b\in B\}$。

**命题 1.3** $\sup(A+B)=\sup A+\sup B$。

**证明**：记 $\alpha=\sup A,\beta=\sup B$。对任意 $a+b$，$a+b\le\alpha+\beta$，所以 $\alpha+\beta$ 是上界。任给 $\varepsilon>0$，存在 $a_0\in A$ 使 $a_0>\alpha-\frac\varepsilon2$，存在 $b_0\in B$ 使 $b_0>\beta-\frac\varepsilon2$，于是 $a_0+b_0>\alpha+\beta-\varepsilon$。由定义 $\sup(A+B)=\alpha+\beta$。$\blacksquare$

> 这个证明是"$\varepsilon/2$ 技巧"的第一次登场：要让总误差小于 $\varepsilon$，就给每一部分分一半。

其他常用性质（请自行验证）：

- $\sup(cA)=c\sup A\ (c>0)$，$\sup(-A)=-\inf A$；
- 若 $A\subseteq B$，则 $\sup A\le\sup B$，$\inf A\ge\inf B$；
- 若对任意 $a\in A,b\in B$ 都有 $a\le b$，则 $\sup A\le\inf B$。

最后一条很有用："每个 $a$ 都 $\le$ 每个 $b$"推出"$a$ 中最大的不超过 $b$ 中最小的"。证明：固定 $b$，$b$ 是 $A$ 的上界，所以 $\sup A\le b$；再让 $b$ 变，$\sup A$ 是 $B$ 的下界，所以 $\sup A\le\inf B$。

## 1.5 一个必须会用的工具：绝对值不等式

- $|a+b|\le|a|+|b|$（三角不等式）；
- $\big||a|-|b|\big|\le|a-b|$；
- $|x-a|<\varepsilon\iff a-\varepsilon<x<a+\varepsilon$。

后两条是把"距离小"翻译成"夹在两数之间"的桥梁。

## 1.6 学会读写量词：$\forall$ 与 $\exists$

数学分析的第一道坎往往不是数学本身，而是**语言**。从这一章开始，几乎每个定义都是"对任意……存在……使得……"的形式。这里先把规则说清楚，后面就不再重复。

- $\forall$ 读作"对任意"，$\exists$ 读作"存在"。
- **顺序非常重要。** "$\forall x\ \exists y,\ y>x$"（对每个数都能找到更大的数）是真的；"$\exists y\ \forall x,\ y>x$"（有一个数比所有数都大）是假的。前者的 $y$ 可以随 $x$ 而变，后者的 $y$ 必须一劳永逸。
- **否定的规则**：把 $\forall$ 换成 $\exists$，$\exists$ 换成 $\forall$，最后的结论取反。例如"$\beta$ 是上界"即 $\forall x\in S,\ x\le\beta$，它的否定"$\beta$ 不是上界"就是 $\exists x\in S,\ x>\beta$。

用这个规则看上确界定义的第 2 条："$\beta-\varepsilon$ 不是上界"恰好就是"$\exists x_0\in S,\ x_0>\beta-\varepsilon$"。你看，定义并不神秘，它只是把"再小一点就不是上界了"逐字翻译了一遍。

**写证明时的小习惯**：要证"$\forall\varepsilon>0,\ \exists\ldots$"，第一句永远写"任给 $\varepsilon>0$"，然后**把要找的东西具体造出来**（通常依赖 $\varepsilon$）。要用"$\forall\varepsilon>0,\ \exists\ldots$"这样的已知条件，则可以**自己挑一个方便的 $\varepsilon$** 代进去，比如 $\varepsilon/2$、$1$、$\frac1n$。初学者常常卡在"不知道 $\varepsilon$ 该取多少"，其实答案往往是倒推出来的：先写出想要的不等式，再看需要什么。

## 1.7 实数完备性定理家族（预告）

确界原理只是"实数没有缝隙"的一种说法。后面几章会陆续出现下面这些定理，它们**彼此等价**（在实数的有序域性质下可以互相推出）：

1. **确界原理**：非空有上界的集合有上确界；
2. **单调有界定理**：单调有界数列必收敛；
3. **闭区间套定理**：长度趋于 0 的闭区间套有唯一公共点；
4. **有限覆盖定理**：闭区间的任一开覆盖有有限子覆盖；
5. **Bolzano–Weierstrass 定理**：有界数列必有收敛子列；
6. **Cauchy 收敛准则**：数列收敛当且仅当它是 Cauchy 列（还需配合阿基米德性质）。

不同的问题适合不同的工具：要"找出一个点"时常用区间套或确界；要"从局部推到整体"时常用有限覆盖；要"从数列中抽出好东西"时常用 B–W 定理。CMC 中出现"证明存在某点使……"的题，很多就是这些定理的直接应用。现在不必记住全部证明，但请记住：**它们都源于这一章的确界原理。**

## 例题

**例 1（基础）** 求 $S=\left\{1-\dfrac1n:n\in\mathbb N^+\right\}$ 的上、下确界。

**解**：$n=1$ 时取 $0$，且 $1-\frac1n\ge0$，所以 $\inf S=\min S=0$。又 $1-\frac1n<1$，$1$ 是上界；任给 $\varepsilon>0$，由阿基米德性质取 $n>\frac1\varepsilon$，则 $1-\frac1n>1-\varepsilon$。故 $\sup S=1$（不属于 $S$）。

**例 2（按定义证明）** 设 $S=\{x\in\mathbb R: x^2<3\}$，证明 $\sup S=\sqrt3$。

**证明**：若 $x\in S$，则 $|x|<\sqrt3$，所以 $\sqrt3$ 是上界。任给 $0<\varepsilon<\sqrt3$，取 $x_0=\sqrt3-\frac\varepsilon2$，则 $0<x_0<\sqrt3$，$x_0^2<3$，$x_0\in S$ 且 $x_0>\sqrt3-\varepsilon$。（$\varepsilon\ge\sqrt3$ 时取 $x_0=0$ 即可。）$\blacksquare$

**例 3（确界与函数）** 设 $f,g$ 在集合 $D$ 上有界，证明
$$\sup_{x\in D}\big(f(x)+g(x)\big)\le\sup_{D}f+\sup_D g,$$
并举例说明不等号可以严格成立。

**证明**：对每个 $x$，$f(x)+g(x)\le\sup f+\sup g$，右边是上界，最小上界不超过它。例子：$D=[0,1]$，$f(x)=x$，$g(x)=-x$，左边 $=0$，右边 $=1+0=1$。

> 对比命题 1.3：那里 $a,b$ 可以**独立**地取，这里 $f(x),g(x)$ 被同一个 $x$ 绑在一起，所以只能得到不等式。

**例 4（区间套，CMC 常用工具）** 设 $[a_n,b_n]$ 满足 $[a_{n+1},b_{n+1}]\subseteq[a_n,b_n]$ 且 $b_n-a_n\to0$。证明存在唯一的 $\xi$ 属于所有 $[a_n,b_n]$。

**证明**：$a_1\le a_2\le\cdots\le b_2\le b_1$，任一 $a_m\le b_n$（取 $k=\max(m,n)$，$a_m\le a_k\le b_k\le b_n$）。令 $\xi=\sup_n a_n$，由 1.4 最后一条性质，$a_n\le\xi\le b_n$ 对一切 $n$ 成立。若另有 $\eta$ 也在所有区间中，则 $|\xi-\eta|\le b_n-a_n$ 对一切 $n$ 成立，而 $b_n-a_n$ 可以小于任意正数，故 $\xi=\eta$。$\blacksquare$

（"$b_n-a_n\to0$"的严格含义下一章才讲，这里理解为"可以小于任意给定正数"即可。）

**例 5（CMC 风格）** 设 $A\subseteq(0,+\infty)$ 非空有界且 $\inf A>0$。令 $A^{-1}=\{1/x:x\in A\}$。证明 $\sup A^{-1}=\dfrac1{\inf A}$。

**证明**：记 $\alpha=\inf A>0$。对 $x\in A$，$x\ge\alpha$，所以 $\frac1x\le\frac1\alpha$，即 $\frac1\alpha$ 是上界。任给 $\varepsilon>0$（不妨 $\varepsilon<\frac1\alpha$），要找 $x\in A$ 使 $\frac1x>\frac1\alpha-\varepsilon$，即 $x<\dfrac{\alpha}{1-\alpha\varepsilon}$。由于 $\dfrac\alpha{1-\alpha\varepsilon}>\alpha$，根据下确界定义存在这样的 $x$。$\blacksquare$

**例 6（稠密性的应用）** 对任意实数 $x$，令 $Q_x=\{r\in\mathbb Q:r<x\}$。证明 $\sup Q_x=x$。

**证明**：$Q_x$ 非空（由稠密性，$(x-1,x)$ 中有有理数），且 $x$ 是上界。任给 $\varepsilon>0$，区间 $(x-\varepsilon,x)$ 中有有理数 $r$，它属于 $Q_x$ 且 $r>x-\varepsilon$。故 $\sup Q_x=x$。$\blacksquare$

> 这个例子说明：每个实数都由"比它小的有理数"完全确定。这正是 Dedekind 用"分割"构造实数的出发点——把实数**定义**成有理数集合的一种切口。

**小结一下解确界题的套路**：先猜答案（画数轴、算前几项），再分两步验证——①它是上（下）界；②任给 $\varepsilon$，找出越过 $\beta-\varepsilon$（或低于 $\alpha+\varepsilon$）的元素。第②步里"找"的工具通常是阿基米德性质或稠密性。

## 易错点

1. **确界不一定是最值。** $\sup(0,1)=1$ 但 $(0,1)$ 没有最大值。只有当 $\sup S\in S$ 时才有 $\max S=\sup S$。
2. **忘了"非空"。** 空集的每个实数都是上界，没有最小上界。确界原理要求非空。
3. **定义第 2 条写反。** 上确界的第 2 条是"存在 $x_0>\beta-\varepsilon$"，不是"对所有 $x$"。量词顺序 $\forall\varepsilon\ \exists x_0$ 不能交换。
4. **把命题 1.3 套到函数上。** $\sup(f+g)$ 一般只 $\le\sup f+\sup g$，见例 3。
5. **"$\sup S\le M$"推不出"$S$ 中有元素等于 $M$"**，更推不出严格不等式能保持：$x<1$ 对所有 $x\in S$ 只能得到 $\sup S\le1$，不能得到 $\sup S<1$。

## 自测清单

1. 写出 $\inf S=\alpha$ 的 $\varepsilon$ 语言定义。
2. $S=\{(-1)^n+\frac1n:n\in\mathbb N^+\}$ 的上、下确界各是多少？是否属于 $S$？
3. 为什么确界原理在 $\mathbb Q$ 中不成立？举一个例子。
4. 若对所有 $x\in S$ 都有 $x<1$，能否断言 $\sup S<1$？
5. 用阿基米德性质说明：若 $0\le a<\frac1n$ 对一切正整数 $n$ 成立，则 $a=0$。
6. 设 $A\subseteq B$ 都非空有界，比较 $\inf A$ 与 $\inf B$。
7. 证明 $\sup(-A)=-\inf A$ 的第一步是什么？

## 参考答案

### 点击展开参考答案

1. ① $\forall x\in S,\ x\ge\alpha$；② $\forall\varepsilon>0,\ \exists x_0\in S,\ x_0<\alpha+\varepsilon$。
2. $n=2$ 时取最大值 $\frac32$，故 $\sup S=\frac32\in S$；奇数项 $-1+\frac1n>-1$ 且可任意接近 $-1$，偶数项都 $>1$，故 $\inf S=-1\notin S$。
3. $\{x\in\mathbb Q:x^2<2\}$ 在 $\mathbb Q$ 中有上界但没有最小的有理上界（候选只能是 $\sqrt2$，不是有理数）。
4. 不能。例如 $S=(0,1)$，$\sup S=1$。只能得 $\sup S\le1$。
5. 若 $a>0$，由阿基米德性质有 $n$ 使 $\frac1n<a$，矛盾。
6. $\inf A\ge\inf B$（集合越大，下确界越小）。
7. 先说明 $-\inf A$ 是 $-A$ 的上界：对 $x\in A$，$x\ge\inf A$，故 $-x\le-\inf A$；再用 $\inf A$ 定义的第 2 条验证最小性。


