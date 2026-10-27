/* InkAgent: plane geometry, solid geometry and marking (批改) builders for raster.html.
   Uses helpers from raster.html: el, rectOf, overlap, num, compile, warn, note, SVGNS, PALETTE. */

/* "A1" → A + subscript 1, "B'" kept; text with digits after letters becomes sub. */
function setName(t, name){
  name=String(name);
  var m=name.match(/^([A-Za-z\u0391-\u03c9]+'*)(\d+)$/);
  if(!m){ t.textContent=name.replace(/_(\d+)/g,'$1'); return; }
  t.appendChild(document.createTextNode(m[1]));
  var s=document.createElementNS(SVGNS,'tspan'); s.setAttribute('baseline-shift','sub'); s.setAttribute('font-size','70%'); s.textContent=m[2]; t.appendChild(s);
}
/* split "ABC" / "A1B1C" / "A,B,C" into point names */
function splitNames(s){
  if(Array.isArray(s)) return s.map(String);
  s=String(s).replace(/\s/g,'').replace(/₁/g,'1').replace(/₂/g,'2').replace(/₀/g,'0');
  if(s.indexOf(',')>=0) return s.split(',').filter(Boolean);
  return s.match(/[A-Za-z\u0391-\u03c9]'*\d*/g)||[];
}

/* ---------- generic label placer over a set of obstacles ---------- */
function Labeler(svg, fs){
  this.svg=svg; this.fs=fs; this.g=el('g',{stroke:'#fff','stroke-width':4,'stroke-linejoin':'round','paint-order':'stroke'},svg);
  this.obs=[]; this.labs=[];
}
Labeler.prototype.seg=function(a,b,n){ n=n||Math.max(6,Math.round(Math.hypot(b[0]-a[0],b[1]-a[1])/8));
  for(var k=0;k<=n;k++) this.obs.push({x:a[0]+(b[0]-a[0])*k/n-2,y:a[1]+(b[1]-a[1])*k/n-2,w:4,h:4}); };
Labeler.prototype.dot=function(p,r){ r=r||5; this.obs.push({x:p[0]-r,y:p[1]-r,w:2*r,h:2*r}); };
/* place name near p, preferring direction (dx,dy) (screen, unit-ish) */
Labeler.prototype.put=function(name,p,dir,color,size,italic){
  var fs=size||this.fs, best=null, self=this;
  var dirs=[]; for(var k=0;k<16;k++){ var a=k*Math.PI/8; dirs.push([Math.cos(a),Math.sin(a)]); }
  var d0=dir&&(dir[0]||dir[1])?dir:[0.7,-0.7]; var L=Math.hypot(d0[0],d0[1]); d0=[d0[0]/L,d0[1]/L];
  dirs.sort(function(u,v){ return (v[0]*d0[0]+v[1]*d0[1])-(u[0]*d0[0]+u[1]*d0[1]); });
  [fs*0.75, fs*1.15].forEach(function(dist){ dirs.forEach(function(u,i){
    var t=el('text',{fill:color||'#222','font-size':fs,'font-style':italic===false?null:'italic','text-anchor':'middle'},self.g); setName(t,name);
    var bb=t.getBBox(); var cx=p[0]+u[0]*(dist+bb.width*0.35), cy=p[1]+u[1]*(dist+bb.height*0.25);
    t.setAttribute('x',cx); t.setAttribute('y',cy+fs*0.35);
    var r=rectOf(t), s=i*2+(dist>fs?40:0);
    self.obs.forEach(function(q){ s+=overlap(q,r)*3; }); self.labs.forEach(function(q){ s+=overlap(q,r)*20; });
    if(!best||s<best.s){ if(best) best.t.remove(); best={t:t,r:r,s:s}; } else t.remove();
  }); });
  this.labs.push(best.r);
  if(best.s>200) warn('标签「'+name+'」周围太挤');
  return best.t;
};

/* ===================== plane geometry ===================== */
function buildGeometry(spec, box){
  if(typeof spec==='string') spec=JSON.parse(spec);
  var P={}, order=[];
  var pts=spec.points||{};
  if(Array.isArray(pts)) pts.forEach(function(p){ P[p.name]=[num(p.x,0),num(p.y,0)]; order.push(p.name); });
  else Object.keys(pts).forEach(function(k){ var v=pts[k]; P[k]=[num(v[0],0),num(v[1],0)]; order.push(k); });
  function need(n){ if(!P[n]) throw new Error('没有点 '+n+'（先在 points 或 derived 里定义）'); return P[n]; }
  function two(s){ var n=splitNames(s); if(n.length<2) throw new Error('需要两个点：'+s); return [need(n[0]),need(n[1])]; }
  function three(s){ var n=splitNames(s); if(n.length<3) throw new Error('需要三个点：'+s); return [need(n[0]),need(n[1]),need(n[2])]; }
  function sub(a,b){return [a[0]-b[0],a[1]-b[1]];} function add(a,b){return [a[0]+b[0],a[1]+b[1]];} function mul(a,k){return [a[0]*k,a[1]*k];}
  function dot(a,b){return a[0]*b[0]+a[1]*b[1];} function len(a){return Math.hypot(a[0],a[1]);}
  function lineX(p1,p2,p3,p4){ var d=(p1[0]-p2[0])*(p3[1]-p4[1])-(p1[1]-p2[1])*(p3[0]-p4[0]); if(Math.abs(d)<1e-12) throw new Error('两直线平行，没有交点');
    var a=p1[0]*p2[1]-p1[1]*p2[0], b=p3[0]*p4[1]-p3[1]*p4[0]; return [(a*(p3[0]-p4[0])-(p1[0]-p2[0])*b)/d,(a*(p3[1]-p4[1])-(p1[1]-p2[1])*b)/d]; }
  function circum(A,B,C){ var d=2*(A[0]*(B[1]-C[1])+B[0]*(C[1]-A[1])+C[0]*(A[1]-B[1])); if(Math.abs(d)<1e-12) throw new Error('三点共线');
    var a2=dot(A,A),b2=dot(B,B),c2=dot(C,C); var o=[(a2*(B[1]-C[1])+b2*(C[1]-A[1])+c2*(A[1]-B[1]))/d,(a2*(C[0]-B[0])+b2*(A[0]-C[0])+c2*(B[0]-A[0]))/d]; return {c:o,r:len(sub(A,o))}; }
  function incir(A,B,C){ var a=len(sub(B,C)), b=len(sub(A,C)), c=len(sub(A,B)), s=a+b+c; var o=[(a*A[0]+b*B[0]+c*C[0])/s,(a*A[1]+b*B[1]+c*C[1])/s];
    var area=Math.abs((B[0]-A[0])*(C[1]-A[1])-(C[0]-A[0])*(B[1]-A[1]))/2; return {c:o,r:2*area/s}; }
  var derivedNames={};
  (spec.derived||[]).forEach(function(d){
    var t=(d.type||'').toLowerCase(), v, of=d.of||d.on||'';
    if(t==='midpoint'){ var q=two(of); v=mul(add(q[0],q[1]),0.5); }
    else if(t==='foot'){ var p=need(splitNames(d.from)[0]), q2=two(d.to||of), u=sub(q2[1],q2[0]); v=add(q2[0],mul(u,dot(sub(p,q2[0]),u)/dot(u,u))); }
    else if(t==='on'||t==='point_on'){ var q3=two(of); v=add(q3[0],mul(sub(q3[1],q3[0]),num(d.t,0.5))); }
    else if(t==='intersection'){ var L=d.lines||[]; var a=two(L[0]), b=two(L[1]); v=lineX(a[0],a[1],b[0],b[1]); }
    else if(t==='circumcenter'){ var T=three(of); v=circum(T[0],T[1],T[2]).c; }
    else if(t==='incenter'){ var T2=three(of); v=incir(T2[0],T2[1],T2[2]).c; }
    else if(t==='centroid'){ var T3=three(of); v=[(T3[0][0]+T3[1][0]+T3[2][0])/3,(T3[0][1]+T3[1][1]+T3[2][1])/3]; }
    else if(t==='orthocenter'){ var T4=three(of); var f1=(function(p,a,b){var u=sub(b,a);return add(a,mul(u,dot(sub(p,a),u)/dot(u,u)));})(T4[0],T4[1],T4[2]);
      var f2=(function(p,a,b){var u=sub(b,a);return add(a,mul(u,dot(sub(p,a),u)/dot(u,u)));})(T4[1],T4[0],T4[2]); v=lineX(T4[0],f1,T4[1],f2); }
    else if(t==='reflect'){ var p2=need(splitNames(d.point||d.from)[0]); if(d.over&&splitNames(d.over).length>=2){ var q4=two(d.over), u2=sub(q4[1],q4[0]); var f=add(q4[0],mul(u2,dot(sub(p2,q4[0]),u2)/dot(u2,u2))); v=sub(mul(f,2),p2);} else { var c=need(splitNames(d.over||d.center)[0]); v=sub(mul(c,2),p2);} }
    else if(t==='rotate'){ var p3=need(splitNames(d.point||d.from)[0]), c2=need(splitNames(d.center)[0]), ang=num(d.angle,90)*Math.PI/180, r0=sub(p3,c2); v=add(c2,[r0[0]*Math.cos(ang)-r0[1]*Math.sin(ang),r0[0]*Math.sin(ang)+r0[1]*Math.cos(ang)]); }
    else throw new Error('不认识的 derived 类型：'+d.type+'（可用 midpoint/foot/on/intersection/circumcenter/incenter/centroid/orthocenter/reflect/rotate）');
    P[d.name]=v; order.push(d.name); derivedNames[d.name]=1;
  });
  // circles in model units
  var circles=(spec.circles||[]).map(function(c){
    var t=(c.type||'').toLowerCase(), r;
    if(t==='circumcircle'){ var T=three(c.of); r=circum(T[0],T[1],T[2]); }
    else if(t==='incircle'){ var T2=three(c.of); r=incir(T2[0],T2[1],T2[2]); }
    else { var cc=need(splitNames(c.center)[0]); r={c:cc,r:c.through?len(sub(need(splitNames(c.through)[0]),cc)):num(c.r,1)}; }
    return {c:r.c,r:r.r,color:c.color,dashed:c.dashed};
  });
  // bounds → screen
  var xs=[],ys=[]; order.forEach(function(n){ xs.push(P[n][0]); ys.push(P[n][1]); });
  circles.forEach(function(c){ xs.push(c.c[0]-c.r,c.c[0]+c.r); ys.push(c.c[1]-c.r,c.c[1]+c.r); });
  if(!xs.length) throw new Error('没有任何点');
  var x0=Math.min.apply(null,xs), x1=Math.max.apply(null,xs), y0=Math.min.apply(null,ys), y1=Math.max.apply(null,ys);
  var w=Math.max(x1-x0,1e-6), h=Math.max(y1-y0,1e-6), u=Math.min(460/w,400/h); if(!isFinite(u)) u=40;
  var S=function(p){ return [(p[0]-x0)*u, (y1-p[1])*u]; };
  var W=w*u, H=h*u, fs=+spec.font_size||19;
  var svg=el('svg',{xmlns:SVGNS,width:W,height:H,viewBox:'0 0 '+W+' '+H,'font-family':"'Times New Roman',Georgia,serif",'font-size':fs},box);
  var gF=el('g',{},svg), gL=el('g',{fill:'none','stroke-linecap':'round','stroke-linejoin':'round'},svg), gM=el('g',{},svg), gP=el('g',{},svg);
  var lab=new Labeler(svg,fs);
  var main=spec.color||'#1f2a44';
  function segEl(a,b,o){ o=o||{}; var A=S(a),B=S(b); el('line',{x1:A[0],y1:A[1],x2:B[0],y2:B[1],stroke:o.color||main,'stroke-width':o.width||2.2,'stroke-dasharray':o.dashed?'7 5':null},gL); lab.seg(A,B); }
  (spec.polygons||[]).forEach(function(pg){ var o=typeof pg==='string'?{of:pg}:pg; var n=splitNames(o.of||o.points);
    var pts2=n.map(function(k){return S(need(k));});
    if(o.fill) el('polygon',{points:pts2.map(function(p){return p.join(',');}).join(' '),fill:o.fill,'fill-opacity':0.18,stroke:'none'},gF);
    for(var i=0;i<n.length;i++) segEl(need(n[i]),need(n[(i+1)%n.length]),o); });
  (spec.segments||[]).forEach(function(sg){ var o=typeof sg==='string'?{of:sg}:sg; var q=two(o.of||o.points); segEl(q[0],q[1],o); });
  (spec.dashed||[]).forEach(function(sg){ var q=two(sg); segEl(q[0],q[1],{dashed:true,color:'#555',width:1.8}); });
  (spec.lines||[]).forEach(function(sg){ var o=typeof sg==='string'?{of:sg}:sg; var q=two(o.of); var d=sub(q[1],q[0]), k=Math.max(w,h)*0.25/len(d); segEl(sub(q[0],mul(d,k)),add(q[1],mul(d,k)),o); });
  circles.forEach(function(c){ var C=S(c.c); el('circle',{cx:C[0],cy:C[1],r:c.r*u,fill:'none',stroke:c.color||'#1a4fd0','stroke-width':2,'stroke-dasharray':c.dashed?'7 5':null},gL);
    for(var k=0;k<48;k++){ var a=k*Math.PI/24; lab.dot([C[0]+c.r*u*Math.cos(a),C[1]+c.r*u*Math.sin(a)],2); } });
  // right-angle marks "AHB": corner at middle letter
  (spec.right_angles||[]).forEach(function(s){ var n=splitNames(s); var V=S(need(n[1])), A=S(need(n[0])), B=S(need(n[2]));
    var ua=sub(A,V), ub=sub(B,V); ua=mul(ua,11/len(ua)); ub=mul(ub,11/len(ub));
    el('path',{d:'M'+add(V,ua).join(',')+' L'+add(add(V,ua),ub).join(',')+' L'+add(V,ub).join(','),fill:'none',stroke:'#444','stroke-width':1.5},gM); });
  // angle arcs {at:"B",from:"A",to:"C",label:"60°",arcs:1}
  (spec.angles||[]).forEach(function(a){ var n=a.of?splitNames(a.of):[a.from,a.at,a.to]; var V=S(need(n[1])), A=S(need(n[0])), B=S(need(n[2]));
    var a1=Math.atan2(A[1]-V[1],A[0]-V[0]), a2=Math.atan2(B[1]-V[1],B[0]-V[0]); var da=a2-a1; while(da<=-Math.PI) da+=2*Math.PI; while(da>Math.PI) da-=2*Math.PI;
    var arcs=+a.arcs||1, col=a.color||'#c62828';
    for(var k=0;k<arcs;k++){ var r=20+k*5; var p1=[V[0]+r*Math.cos(a1),V[1]+r*Math.sin(a1)], p2=[V[0]+r*Math.cos(a1+da),V[1]+r*Math.sin(a1+da)];
      el('path',{d:'M'+p1.join(',')+' A'+r+','+r+' 0 0,'+(da>0?1:0)+' '+p2.join(','),fill:'none',stroke:col,'stroke-width':1.6},gM); }
    if(a.label){ var mid=a1+da/2; var t=el('text',{x:V[0]+(34+arcs*5)*Math.cos(mid),y:V[1]+(34+arcs*5)*Math.sin(mid)+fs*0.3,'text-anchor':'middle',fill:col,'font-size':fs*0.8},lab.g); t.textContent=a.label; lab.labs.push(rectOf(t)); } });
  // equal-length ticks: [["AB","AC"],["BD","DC"]] → group k gets k+1 ticks
  (spec.equal_marks||[]).forEach(function(grp,gi){ (Array.isArray(grp)?grp:[grp]).forEach(function(s){ var q=two(s); var A=S(q[0]),B=S(q[1]), m=mul(add(A,B),0.5), d=sub(B,A); d=mul(d,1/len(d)); var nrm=[-d[1],d[0]];
    for(var k=0;k<=gi;k++){ var off=(k-gi/2)*5; var c=add(m,mul(d,off)); el('line',{x1:c[0]-nrm[0]*7,y1:c[1]-nrm[1]*7,x2:c[0]+nrm[0]*7,y2:c[1]+nrm[1]*7,stroke:'#444','stroke-width':1.6},gM); } }); });
  // segment labels {"AB":"5"} placed at the middle, pushed outward
  var cen=[0,0]; order.forEach(function(n){ cen=add(cen,S(P[n])); }); cen=mul(cen,1/order.length);
  Object.keys(spec.lengths||{}).forEach(function(k){ var q=two(k), A=S(q[0]), B=S(q[1]), m=mul(add(A,B),0.5); var d=sub(m,cen); lab.put(String(spec.lengths[k]),m,d,'#2e7d32',fs*0.8,false); });
  // points + names (away from the figure centre)
  var hide=splitNames((spec.hide_points||[]).join(','));
  order.forEach(function(n){ if(hide.indexOf(n)>=0) return; var p=S(P[n]);
    el('circle',{cx:p[0],cy:p[1],r:3,fill:derivedNames[n]?'#1a4fd0':main},gP); lab.dot(p,4);
    lab.put(n,p,sub(p,cen),derivedNames[n]?'#1a4fd0':main,fs,true); });
  note('点坐标：'+order.map(function(n){ return n+'('+fmtNum(+P[n][0].toFixed(3))+', '+fmtNum(+P[n][1].toFixed(3))+')'; }).join(' '));
  circles.forEach(function(c,i){ note('圆'+(i+1)+'：圆心('+fmtNum(+c.c[0].toFixed(3))+', '+fmtNum(+c.c[1].toFixed(3))+')，半径 '+fmtNum(+c.r.toFixed(3))); });
  return svg;
}

/* ===================== solid geometry (斜二测) ===================== */
function buildSolid(spec, box){
  if(typeof spec==='string') spec=JSON.parse(spec);
  var type=(spec.type||'cube').toLowerCase();
  var D=[-0.5*Math.SQRT1_2,1,-0.5*Math.SQRT1_2]; // view direction (into the page)
  var proj=function(p){ return [p[0]+0.5*p[1]*Math.SQRT1_2, -(p[2]+0.5*p[1]*Math.SQRT1_2)]; };
  var V=[], names=[], faces=[], round=null;
  function sz(k,d){ return num((spec.size||{})[k], d); }
  function ngon(n,r){ var out=[]; for(var k=0;k<n;k++){ var a=(-90-180/n+k*360/n)*Math.PI/180; out.push([r*Math.cos(a),r*Math.sin(a)]); } return out; }
  var n=Math.max(3,Math.round(num(spec.n,type==='prism'?3:4)));
  if(type==='cube'||type==='cuboid'){
    var a=sz('a',num(spec.edge,4)), b=type==='cube'?a:sz('b',a), c=type==='cube'?a:sz('c',a);
    var base=[[0,0],[a,0],[a,b],[0,b]];
    base.forEach(function(p){ V.push([p[0],p[1],0]); }); base.forEach(function(p){ V.push([p[0],p[1],c]); });
    names=['A','B','C','D','A1','B1','C1','D1']; n=4;
    faces=[[0,3,2,1],[4,5,6,7],[0,1,5,4],[1,2,6,5],[2,3,7,6],[3,0,4,7]];
  } else if(type==='prism'||type==='frustum'){
    var r=num(spec.r,2), hh=num(spec.h,4), k2=type==='frustum'?num(spec.ratio,0.5):1, B=ngon(n,r);
    if(n===4&&type==='prism'){ B=[[0,0],[2*r*Math.SQRT1_2,0],[2*r*Math.SQRT1_2,2*r*Math.SQRT1_2],[0,2*r*Math.SQRT1_2]]; }
    var cx=0,cy=0; B.forEach(function(p){cx+=p[0]/n;cy+=p[1]/n;});
    B.forEach(function(p){ V.push([p[0],p[1],0]); }); B.forEach(function(p){ V.push([cx+(p[0]-cx)*k2,cy+(p[1]-cy)*k2,hh]); });
    var L='ABCDEFGH'.split('').slice(0,n); names=L.concat(L.map(function(s){return s+'1';}));
    var bot=[],top=[]; for(var i=0;i<n;i++){ bot.push(n-1-i); top.push(n+i); faces.push([i,(i+1)%n,n+(i+1)%n,n+i]); } faces.push(bot,top);
  } else if(type==='pyramid'){
    var r2=num(spec.r,2.2), h2=num(spec.h,4), B2=ngon(n,r2);
    if(n===4) B2=[[0,0],[2*r2*Math.SQRT1_2,0],[2*r2*Math.SQRT1_2,2*r2*Math.SQRT1_2],[0,2*r2*Math.SQRT1_2]];
    var gx=0,gy=0; B2.forEach(function(p){gx+=p[0]/n;gy+=p[1]/n;});
    B2.forEach(function(p){ V.push([p[0],p[1],0]); });
    var apexAt=String(spec.apex_over||'center'); var ax=gx, ay=gy;
    var L2='ABCDEFGH'.split('').slice(0,n), ia=L2.indexOf(apexAt.toUpperCase()); if(ia>=0){ ax=B2[ia][0]; ay=B2[ia][1]; }
    V.push([ax,ay,h2]); names=L2.concat([spec.apex_name||'P']);
    var bot2=[]; for(var j=0;j<n;j++){ bot2.push(n-1-j); faces.push([j,(j+1)%n,n]); } faces.push(bot2);
  } else if(type==='cylinder'||type==='cone'||type==='sphere'){ round=type; }
  else throw new Error('不认识的立体类型：'+spec.type+'（可用 cube/cuboid/prism/pyramid/frustum/cylinder/cone/sphere）');
  if(spec.names){ var nn=Array.isArray(spec.names)?spec.names:String(spec.names).split(/[\s,]+/); if(nn.length===names.length) names=nn; else warn('names 数量（'+nn.length+'）和顶点数（'+names.length+'）不一致，用了默认名字'); }
  var idx={}; names.forEach(function(s,i){ idx[s]=i; });
  var u=1, fs=+spec.font_size||19;
  if(round){ if((spec.points||[]).length||(spec.segments||[]).length) warn('旋转体不支持 points/segments，已忽略'); return buildRound(spec, box, round, fs); }
  // fit to ~420px
  var P2=V.map(proj), xs=P2.map(function(p){return p[0];}), ys=P2.map(function(p){return p[1];});
  var mx=Math.min.apply(null,xs), my=Math.min.apply(null,ys), W=Math.max.apply(null,xs)-mx, H=Math.max.apply(null,ys)-my;
  u=Math.min(420/W, 420/H);
  var S=function(p){ var q=proj(p); return [(q[0]-mx)*u,(q[1]-my)*u]; };
  var svg=el('svg',{xmlns:SVGNS,width:W*u,height:H*u,viewBox:'0 0 '+W*u+' '+H*u,'font-family':"'Times New Roman',Georgia,serif",'font-size':fs},box);
  var gF=el('g',{},svg), gE=el('g',{fill:'none','stroke-linecap':'round'},svg), gP=el('g',{},svg);
  var lab=new Labeler(svg,fs), col=spec.color||'#1f3b8b';
  // face visibility
  var cen=[0,0,0]; V.forEach(function(p){ cen[0]+=p[0]/V.length; cen[1]+=p[1]/V.length; cen[2]+=p[2]/V.length; });
  function vsub(a,b){return [a[0]-b[0],a[1]-b[1],a[2]-b[2]];} function cross(a,b){return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];} function vdot(a,b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
  var vis=faces.map(function(f){ var nrm=cross(vsub(V[f[1]],V[f[0]]),vsub(V[f[2]],V[f[0]])); var fc=[0,0,0]; f.forEach(function(i){ fc=[fc[0]+V[i][0]/f.length,fc[1]+V[i][1]/f.length,fc[2]+V[i][2]/f.length]; });
    if(vdot(nrm,vsub(fc,cen))<0) nrm=[-nrm[0],-nrm[1],-nrm[2]]; return vdot(nrm,D)<-1e-9; });
  var edges={}; faces.forEach(function(f,fi){ for(var i=0;i<f.length;i++){ var a=f[i],b=f[(i+1)%f.length], k=Math.min(a,b)+'-'+Math.max(a,b); (edges[k]=edges[k]||{a:a,b:b,f:[]}).f.push(fi); } });
  // shaded faces (by name, e.g. "ACC1A1" — any planar section, not only faces)
  (spec.faces||spec.shade||[]).forEach(function(s,i){ var o=typeof s==='string'?{of:s}:s; var ids=splitNames(o.of).map(function(k){ if(!(k in idx)) throw new Error('没有顶点 '+k); return idx[k]; });
    el('polygon',{points:ids.map(function(j){return S(V[j]).join(',');}).join(' '),fill:o.color||PALETTE[(i+1)%PALETTE.length],'fill-opacity':0.16,stroke:'none'},gF); });
  var hiddenCount=0;
  Object.keys(edges).forEach(function(k){ var e=edges[k]; var hidden=e.f.every(function(fi){return !vis[fi];}); if(hidden) hiddenCount++;
    var A=S(V[e.a]),B=S(V[e.b]); el('line',{x1:A[0],y1:A[1],x2:B[0],y2:B[1],stroke:col,'stroke-width':hidden?1.7:2.3,'stroke-dasharray':hidden?'7 5':null},gE); lab.seg(A,B); });
  // extra points: {name:"M", mid:"AB"} | {name:"E", on:"PC", t:0.3} | {name:"O", center:"ABCD"}
  var faceOf=function(ids){ return faces.map(function(f,fi){ return ids.every(function(i){return f.indexOf(i)>=0;})?fi:-1; }).filter(function(x){return x>=0;}); };
  var memb={}; V.forEach(function(p,i){ memb[i]=faceOf([i]); });
  (spec.points||[]).forEach(function(q){
    var on=splitNames(q.mid||q.on||q.center||''); var ids=on.map(function(k){ if(!(k in idx)) throw new Error('没有顶点 '+k); return idx[k]; });
    var p;
    if(q.center){ p=[0,0,0]; ids.forEach(function(i){ p=[p[0]+V[i][0]/ids.length,p[1]+V[i][1]/ids.length,p[2]+V[i][2]/ids.length]; }); }
    else { var t=q.mid?0.5:num(q.t,0.5); var A=V[ids[0]],B=V[ids[1]]; p=[A[0]+(B[0]-A[0])*t,A[1]+(B[1]-A[1])*t,A[2]+(B[2]-A[2])*t]; }
    V.push(p); names.push(q.name); idx[q.name]=V.length-1;
    var common=null; ids.forEach(function(i){ common=common===null?memb[i].slice():common.filter(function(f){return memb[i].indexOf(f)>=0;}); });
    memb[V.length-1]=common||[];
  });
  // extra segments "A1C": solid iff both ends share a visible face (it lies on the visible surface), else dashed
  (spec.segments||[]).forEach(function(s){ var o=typeof s==='string'?{of:s}:s; var nm=splitNames(o.of);
    if(nm.length<2) throw new Error('线段写法：A1C 或 ["A1","C"]：'+o.of);
    var i=idx[nm[0]], j=idx[nm[1]]; if(i===undefined||j===undefined) throw new Error('没有顶点 '+(i===undefined?nm[0]:nm[1]));
    var shared=memb[i].filter(function(f){return memb[j].indexOf(f)>=0&&vis[f];});
    var hidden=o.dashed!==undefined?!!o.dashed:shared.length===0;
    var A=S(V[i]),B=S(V[j]); el('line',{x1:A[0],y1:A[1],x2:B[0],y2:B[1],stroke:o.color||'#d32f2f','stroke-width':hidden?1.7:2.2,'stroke-dasharray':hidden?'7 5':null},gE); lab.seg(A,B);
  });
  var c2=[0,0]; V.forEach(function(p){ var s=S(p); c2=[c2[0]+s[0]/V.length,c2[1]+s[1]/V.length]; });
  if(spec.labels!==false) names.forEach(function(nm,i){ var p=S(V[i]); el('circle',{cx:p[0],cy:p[1],r:2.4,fill:col},gP); lab.dot(p,3); lab.put(nm,p,[p[0]-c2[0],p[1]-c2[1]],i>=(spec.points?V.length-spec.points.length:V.length)?'#1a4fd0':'#222',fs,true); });
  note('斜二测画法；虚线棱 '+hiddenCount+' 条（看不见的棱自动画成虚线；不在可见面上的连线也画虚线）');
  return svg;
}
function buildRound(spec, box, type, fs){
  var r=num(spec.r,2), h=num(spec.h,4), u=60, R=r*u, ry=R*0.32, H=h*u;
  var W=2*R, top=type==='sphere'?R:(type==='cone'?H:H);
  var svg=el('svg',{xmlns:SVGNS,width:W+40,height:top+ry+40,viewBox:(-20)+' '+(-20)+' '+(W+40)+' '+(top+ry+40),'font-family':"'Times New Roman',Georgia,serif",'font-size':fs},box);
  var col=spec.color||'#1f3b8b', g=el('g',{fill:'none',stroke:col,'stroke-width':2.2},svg), lab=new Labeler(svg,fs);
  var cx=R, by=type==='sphere'?R:H; // base ellipse centre
  function ell(y,dashedBack){ el('path',{d:'M'+(cx-R)+','+y+' A'+R+','+ry+' 0 0,0 '+(cx+R)+','+y},g); el('path',{d:'M'+(cx-R)+','+y+' A'+R+','+ry+' 0 0,1 '+(cx+R)+','+y,'stroke-dasharray':dashedBack?'7 5':null,'stroke-width':dashedBack?1.7:2.2},g); }
  if(type==='sphere'){ el('circle',{cx:cx,cy:R,r:R},g); ell(R,true); el('circle',{cx:cx,cy:R,r:2.5,fill:col},svg); lab.put(spec.center_name||'O',[cx,R],[0.3,1],'#222',fs,true); }
  else {
    ell(by,true);
    if(type==='cylinder'){ el('ellipse',{cx:cx,cy:0,rx:R,ry:ry},g); el('line',{x1:cx-R,y1:0,x2:cx-R,y2:by},g); el('line',{x1:cx+R,y1:0,x2:cx+R,y2:by},g);
      if(spec.axis!==false){ el('line',{x1:cx,y1:0,x2:cx,y2:by,'stroke-dasharray':'7 5','stroke-width':1.5},g); lab.put(spec.top_name||'O1',[cx,0],[0,-1],'#222',fs,true); lab.put(spec.center_name||'O',[cx,by],[0.4,1],'#222',fs,true); } }
    else { el('line',{x1:cx-R,y1:by,x2:cx,y2:0},g); el('line',{x1:cx+R,y1:by,x2:cx,y2:0},g);
      if(spec.axis!==false){ el('line',{x1:cx,y1:0,x2:cx,y2:by,'stroke-dasharray':'7 5','stroke-width':1.5},g); el('line',{x1:cx,y1:by,x2:cx+R,y2:by,'stroke-dasharray':'7 5','stroke-width':1.5},g);
        lab.put(spec.apex_name||'P',[cx,0],[0,-1],'#222',fs,true); lab.put(spec.center_name||'O',[cx,by],[-0.3,1],'#222',fs,true); } }
  }
  note('旋转体：底面画成椭圆，背面半圈为虚线；已标出'+(type==='sphere'?'球心 '+(spec.center_name||'O'):(type==='cone'?'顶点 '+(spec.apex_name||'P')+'、底面圆心 '+(spec.center_name||'O'):'上下底面圆心 '+(spec.top_name||'O1')+'、'+(spec.center_name||'O'))));
  return svg;
}

/* ===================== 批改 marks in page coordinates ===================== */
function buildMarks(spec, box){
  if(typeof spec==='string') spec=JSON.parse(spec);
  var marks=spec.marks||[]; if(!marks.length) throw new Error('marks 为空');
  var col0=spec.color||'#e53935', bb=null;
  function grow(x,y,w,h){ w=w||0; h=h||0; if(!bb) bb={l:x,t:y,r:x+w,b:y+h}; else { bb.l=Math.min(bb.l,x); bb.t=Math.min(bb.t,y); bb.r=Math.max(bb.r,x+w); bb.b=Math.max(bb.b,y+h); } }
  var tmp=el('svg',{xmlns:SVGNS},box); // measure text first
  marks.forEach(function(m){ var t=(m.type||'').toLowerCase(), s=num(m.size,40);
    if(t==='check'||t==='cross') grow(num(m.x,0)-s/2,num(m.y,0)-s/2,s,s);
    else if(t==='arrow'){ var f=m.from||[0,0], to=m.to||[0,0]; grow(num(f[0],0),num(f[1],0)); grow(num(to[0],0),num(to[1],0)); }
    else if(t==='text'||t==='note'){ var fz=num(m.size,30); var tt=el('text',{'font-size':fz,'font-family':'sans-serif'},tmp); tt.textContent=m.text||''; var w=tt.getBBox().width; grow(num(m.x,0),num(m.y,0)-fz,w,fz*1.3); }
    else grow(num(m.x,0),num(m.y,0)-(t==='underline'||t==='wavy'?6:0),num(m.w,100),t==='underline'||t==='wavy'?14:num(m.h,40));
  });
  tmp.remove();
  var pad=24; bb.l-=pad; bb.t-=pad; bb.r+=pad; bb.b+=pad;
  var W=bb.r-bb.l, H=bb.b-bb.t, k=Math.min(1.6, 1000/Math.max(W,H));
  var svg=el('svg',{xmlns:SVGNS,viewBox:[bb.l,bb.t,W,H].join(' '),width:W*k,height:H*k,'font-family':'sans-serif'},box);
  svg.style.overflow='hidden';
  // near-invisible corner dots: the capture is cropped to drawn pixels, these pin it to exactly this box
  [[bb.l,bb.t],[bb.r-1,bb.t],[bb.l,bb.b-1],[bb.r-1,bb.b-1]].forEach(function(p){ el('rect',{x:p[0],y:p[1],width:1,height:1,fill:'#000','fill-opacity':0.06},svg); });
  var g=el('g',{fill:'none','stroke-linecap':'round','stroke-linejoin':'round'},svg);
  marks.forEach(function(m){ var t=(m.type||'').toLowerCase(), c=m.color||col0, x=num(m.x,0), y=num(m.y,0), s=num(m.size,40), sw=num(m.width,4);
    if(t==='check') el('path',{d:'M'+(x-s/2)+','+(y)+' L'+(x-s/8)+','+(y+s/2.4)+' L'+(x+s/2)+','+(y-s/2),stroke:c,'stroke-width':sw+1},g);
    else if(t==='cross'){ el('path',{d:'M'+(x-s/2.4)+','+(y-s/2.4)+' L'+(x+s/2.4)+','+(y+s/2.4)+' M'+(x+s/2.4)+','+(y-s/2.4)+' L'+(x-s/2.4)+','+(y+s/2.4),stroke:c,'stroke-width':sw+1},g); }
    else if(t==='circle'){ var w=num(m.w,100), h=num(m.h,40); el('ellipse',{cx:x+w/2,cy:y+h/2,rx:w/2+6,ry:h/2+6,stroke:c,'stroke-width':sw},g); }
    else if(t==='box') el('rect',{x:x-4,y:y-4,width:num(m.w,100)+8,height:num(m.h,40)+8,rx:6,stroke:c,'stroke-width':sw},g);
    else if(t==='underline') el('line',{x1:x,y1:y+4,x2:x+num(m.w,100),y2:y+4,stroke:c,'stroke-width':sw},g);
    else if(t==='wavy'){ var w2=num(m.w,100), d='M'+x+','+(y+4); for(var i=0;i<w2;i+=12) d+=' q3,-7 6,0 t6,0'; el('path',{d:d,stroke:c,'stroke-width':sw-1},g); }
    else if(t==='arrow'){ var f=m.from||[0,0], to=m.to||[0,0]; var x1=num(f[0],0),y1=num(f[1],0),x2=num(to[0],0),y2=num(to[1],0); var a=Math.atan2(y2-y1,x2-x1), L=16;
      el('path',{d:'M'+x1+','+y1+' L'+x2+','+y2+' M'+(x2-L*Math.cos(a-0.45))+','+(y2-L*Math.sin(a-0.45))+' L'+x2+','+y2+' L'+(x2-L*Math.cos(a+0.45))+','+(y2-L*Math.sin(a+0.45)),stroke:c,'stroke-width':sw},g); }
    else if(t==='text'||t==='note'){ var tt=el('text',{x:x,y:y,'font-size':num(m.size,30),fill:c,stroke:'#fff','stroke-width':5,'paint-order':'stroke'},svg); tt.textContent=m.text||''; }
    else warn('不认识的批改标记：'+m.type+'（可用 check/cross/circle/box/underline/wavy/arrow/text）');
  });
  diag.box=[bb.l,bb.t,W,H];
  return svg;
}
