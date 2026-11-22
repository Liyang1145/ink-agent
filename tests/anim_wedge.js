// 两个测向扇形求交（roadmap 演示）：A=(0,0)、B=(b,0)，都指向目标 (0,4)，半角 θ=arctan(1/4)
A.mode2d(-4.2, 9.2, -1.2, 10.2, {grid: 1, axes: true, ticks: 2});
const th = Math.atan(1 / 4), T = [0, 4], R = 40;
function clip(poly, nx, ny, c) { // 保留 nx*x+ny*y+c >= 0 的部分
  const out = [];
  for (let i = 0; i < poly.length; i++) {
    const P = poly[i], Q = poly[(i + 1) % poly.length];
    const fp = nx * P[0] + ny * P[1] + c, fq = nx * Q[0] + ny * Q[1] + c;
    if (fp >= 0) out.push(P);
    if ((fp >= 0) !== (fq >= 0)) { const s = fp / (fp - fq); out.push([P[0] + s * (Q[0] - P[0]), P[1] + s * (Q[1] - P[1])]); }
  }
  return out;
}
function wedge(poly, P) { // 与以 P 为顶点、指向 T、半角 th 的扇形求交
  const d = Math.atan2(T[1] - P[1], T[0] - P[0]);
  for (const s of [1, -1]) {
    const a = d + s * th, ux = Math.cos(a), uy = Math.sin(a);
    let nx = -uy, ny = ux; if (s === 1) { nx = -nx; ny = -ny; }
    poly = clip(poly, nx, ny, -(nx * P[0] + ny * P[1]));
  }
  return poly;
}
function fan(P, k) { // 扇形本身（画到 R 远处），k∈[0,1] 控制张开程度
  const d = Math.atan2(T[1] - P[1], T[0] - P[0]), pts = [P];
  for (let i = 0; i <= 24; i++) { const a = d - th * k + 2 * th * k * i / 24; pts.push([P[0] + R * Math.cos(a), P[1] + R * Math.sin(a)]); }
  return pts;
}
const area = p => Math.abs(p.reduce((s, q, i) => { const r = p[(i + 1) % p.length]; return s + q[0] * r[1] - r[0] * q[1]; }, 0)) / 2;

const fa = A.polygon([], {color: 'blue', opacity: 0.07, stroke: false});
const fb = A.polygon([], {color: 'orange', opacity: 0.08, stroke: false});
const ra = [A.line([], {color: 'blue', width: 1.6, dashed: true}), A.line([], {color: 'blue', width: 1.6, dashed: true})];
const rb = [A.line([], {color: 'orange', width: 1.6, dashed: true}), A.line([], {color: 'orange', width: 1.6, dashed: true})];
const region = A.polygon([], {color: 'purple', opacity: 0.26, width: 2.5});
const diam = A.line([], {color: 'red', width: 2.5});
const ang = A.arc([0, 0], 1.1, Math.PI / 2, Math.PI / 2 + th, {color: 'blue', width: 2});
const pA = A.point([0, 0], {color: 'blue'}), pB = A.point([8, 0], {color: 'orange'}), pT = A.point(T, {color: 'gray', size: 4});
A.label('$A$', [0, 0], {color: 'blue', align: 'below', dx: 14, dy: 2, size: 17});
const lB = A.label('$B$', [8, 0], {color: 'orange', align: 'below', dy: 4, size: 17});
A.label('目标', T, {color: 'gray', align: 'left', dx: 6, dy: 12, size: 12});
A.label('$\\theta$', [-0.36, 1.42], {color: 'blue', size: 14});
const lD = A.label('', [0, 0], {color: 'red', size: 14, bg: true});
const info = A.info('');
A.caption('两组测向射线围出的交集（紫色）：$B$ 挪远，面积变大，最大点间距反而变小');

let b = 6, target = 6;
A.choice('$b$', [6, 8], 6, v => { target = v; });
A.onFrame((t, dt) => {
  b += (target - b) * Math.min(1, dt * 7); if (Math.abs(target - b) < 1e-3) b = target;
  const k = A.ease(A.seg(t, 0, 1.2)), Bp = [b, 0];
  pB.move(Bp); lB.move(Bp);
  fa.setPoints(fan([0, 0], k)); fb.setPoints(fan(Bp, k));
  const ea = fan([0, 0], k), eb = fan(Bp, k);
  ra[0].setPoints([ea[0], ea[1]]); ra[1].setPoints([ea[0], ea[25]]);
  rb[0].setPoints([eb[0], eb[1]]); rb[1].setPoints([eb[0], eb[25]]);
  let poly = [[-50, -50], [50, -50], [50, 50], [-50, 50]];
  poly = wedge(wedge(poly, [0, 0]), Bp);
  const show = A.seg(t, 1.0, 1.6);
  region.setPoints(show > 0 ? poly : []); region.material.opacity = 0.26 * show;
  let best = [0, 0, 0];
  for (let i = 0; i < poly.length; i++) for (let j = i + 1; j < poly.length; j++) {
    const d = Math.hypot(poly[i][0] - poly[j][0], poly[i][1] - poly[j][1]); if (d > best[0]) best = [d, i, j];
  }
  if (show > 0 && poly.length > 1) {
    const P = poly[best[1]], Q = poly[best[2]];
    diam.setPoints([P, Q]); lD.move([(P[0] + Q[0]) / 2 + 0.25, (P[1] + Q[1]) / 2]).set('$d=' + best[0].toFixed(3) + '$').hide(false);
  } else { diam.setPoints([]); lD.hide(true); }
  info.set('$b=' + b.toFixed(2) + '$\n面积 $S=' + area(poly).toFixed(3) + '$\n最大点间距 $d=' + best[0].toFixed(3) + '$');
});
