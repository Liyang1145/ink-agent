// InkAgent animation runtime (runs inside the animation card's iframe, three.js r147 global THREE).
// The agent's code is a function body called with A (helpers below) and THREE.
// v0.6.24: screen-space thick lines (crisp at any DPR, in-place setPoints), 2D teaching style (mode2d grid/axes/ticks,
// px-sized points and arrow heads), polygons/arcs/circles, choice/info widgets, timeline (A.duration) with scrubbing,
// pause when off-screen, resize-aware fitting, scroll-friendly touch in the inline card.
(function(){
var errors=[], frameFns=[], labels=[], scaled=[], paused=false, t=0, last=null, reported=false, visible=true, big=false;
var DUR=0, LOOP=true, scrubbing=false, is2d=false, B2=null, lastScale=0, userMoved=false, home=null;
var host=document.getElementById('stage'), lay=document.getElementById('labels'), cap=document.getElementById('cap'), ui=document.getElementById('ui'),
    info=document.getElementById('info'), tl=document.getElementById('tl'), err=document.getElementById('err');
var W=host.clientWidth||600, H=host.clientHeight||320;
function errMsg(m){ errors.push(m); if(err){ err.textContent='动画出错：'+errors[0]; err.style.display='block'; } }
function report(){ if(reported) return; reported=true; var snap=''; try{ step(0); renderer.render(scene,camera); snap=renderer.domElement.toDataURL('image/png'); }catch(e){}
  try{ parent.animReport(window.ANIM_KEY, JSON.stringify({errors:errors, snapshot:snap, labels:labels.length, frames:Math.round(t*60)})); }catch(e){} }
window.onerror=function(m,s,l,c,e){ errMsg(String(m)+(l?' (第'+l+'行)':'')); if(!reported) setTimeout(report,50); };
var renderer=new THREE.WebGLRenderer({antialias:true,preserveDrawingBuffer:true});
renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,2)); renderer.setSize(W,H); renderer.setClearColor(0xffffff,1);
host.appendChild(renderer.domElement);
var scene=new THREE.Scene();
var camera=new THREE.PerspectiveCamera(45,W/H,0.01,1000); camera.position.set(6,5,8); camera.lookAt(0,0,0);
var controls=new THREE.OrbitControls(camera,renderer.domElement); controls.enableDamping=true; controls.dampingFactor=0.12;
scene.add(new THREE.HemisphereLight(0xffffff,0xdde3ee,0.55)); var sun=new THREE.DirectionalLight(0xffffff,0.7); sun.position.set(5,10,7); scene.add(sun);
var sun2=new THREE.DirectionalLight(0xffffff,0.25); sun2.position.set(-6,3,-5); scene.add(sun2);
// palette shared with the app (accent #4d6bfe family); names usable anywhere a color is accepted
var C={ink:'#1f2329',blue:'#3d6cf0',red:'#e5484d',green:'#1f9d6b',orange:'#ef8a1f',purple:'#8b5cf6',teal:'#0e9f9a',pink:'#e0508a',gray:'#8a8f99',light:'#c4c8d0',axis:'#5b6270',grid:'#eef0f3'};
function V(p){ return p instanceof THREE.Vector3? p : Array.isArray(p)? new THREE.Vector3(p[0]||0,p[1]||0,p[2]||0) : new THREE.Vector3(p&&p.x||0,p&&p.y||0,p&&p.z||0); }
function col(c,d){ c=c==null?d:c; if(typeof c==='string'&&C[c]) c=C[c]; return new THREE.Color(c); }
function cssCol(c,d){ c=c==null?d:c; if(typeof c==='string') return C[c]||c; return '#'+new THREE.Color(c).getHexString(); }
function escH(s){ return String(s).replace(/[&<]/g,function(c){return c==='&'?'&amp;':'&lt;';}); }
function tex(s){ s=String(s); if(!window.katex) return escH(s);
  return s.split(/(\$[^$]+\$)/).map(function(p){ if(/^\$[^$]+\$$/.test(p)){ try{ return katex.renderToString(p.slice(1,-1),{throwOnError:false}); }catch(e){ return p; } } return escH(p).replace(/\n/g,'<br>'); }).join(''); }
function setTex(el,s){ s=s==null?'':String(s); if(el.__s===s) return; el.__s=s; el.innerHTML=tex(s); }
function fmt(v){ return (+v).toFixed(2).replace(/\.?0+$/,''); }
function safe(fn,what){ return function(){ try{ return fn.apply(this,arguments); }catch(e){ errMsg(what+': '+e.message); } }; }
/** world units per CSS pixel (2D: exact; 3D: at the orbit target distance) */
function unit(){ if(camera.isOrthographicCamera) return (camera.top-camera.bottom)/camera.zoom/H;
  var d=camera.position.distanceTo(controls.target); return 2*d*Math.tan(camera.fov*Math.PI/360)/H; }

// ---------- screen-space thick lines (one quad per segment, square caps) ----------
var RES={value:new THREE.Vector2(W,H)};
var LINE_VS='attribute vec3 other; attribute float side; attribute float dist; uniform vec2 res; uniform float width; varying float vD;'+
 'void main(){ vec4 a=projectionMatrix*modelViewMatrix*vec4(position,1.0); vec4 b=projectionMatrix*modelViewMatrix*vec4(other,1.0);'+
 ' vec2 sa=a.xy/a.w*res*0.5; vec2 sb=b.xy/b.w*res*0.5; vec2 d=sa-sb; float l=length(d); d= l>1e-5? d/l : vec2(1.0,0.0);'+
 ' vec2 n=vec2(-d.y,d.x); vec2 off=(n*side+d)*width*0.5; a.xy+=off/(res*0.5)*a.w; gl_Position=a; vD=dist; }';
var LINE_FS='uniform vec3 color; uniform float opacity; uniform float dash; uniform float gap; varying float vD;'+
 'void main(){ if(dash>0.0 && mod(vD,dash+gap)>dash) discard; gl_FragColor=vec4(color,opacity); }';
function FatLine(o){
  var g=new THREE.BufferGeometry(), cap=0;
  var mat=new THREE.ShaderMaterial({vertexShader:LINE_VS,fragmentShader:LINE_FS,transparent:true,depthWrite:o.depthWrite!==false,side:THREE.DoubleSide,
    uniforms:{res:RES,width:{value:o.width||(is2d?2.5:2)},color:{value:col(o.color,C.ink)},opacity:{value:o.opacity==null?1:o.opacity},dash:{value:0},gap:{value:0}}});
  mat.color=mat.uniforms.color.value;
  var m=new THREE.Mesh(g,mat); m.frustumCulled=false; m.renderOrder=o.renderOrder||1;
  var dashPx=o.dashed? (Array.isArray(o.dashed)?o.dashed:[7,5]) : null;
  if(dashPx) scaled.push(function(u){ mat.uniforms.dash.value=dashPx[0]*u; mat.uniforms.gap.value=dashPx[1]*u; });
  function alloc(n){ cap=Math.max(n,cap*2,4); var nv=cap*4;
    g.setAttribute('position',new THREE.BufferAttribute(new Float32Array(nv*3),3)); g.setAttribute('other',new THREE.BufferAttribute(new Float32Array(nv*3),3));
    g.setAttribute('side',new THREE.BufferAttribute(new Float32Array(nv),1)); g.setAttribute('dist',new THREE.BufferAttribute(new Float32Array(nv),1));
    var idx=new (nv>65535?Uint32Array:Uint16Array)(cap*6); for(var s=0;s<cap;s++){ var v=s*4,k=s*6; idx[k]=v;idx[k+1]=v+1;idx[k+2]=v+2;idx[k+3]=v;idx[k+4]=v+2;idx[k+5]=v+3; }
    g.setIndex(new THREE.BufferAttribute(idx,1)); }
  m.setPoints=function(pts,closed){ pts=(pts||[]).map(V); if(closed&&pts.length>2) pts=pts.concat([pts[0]]); var n=Math.max(0,pts.length-1);
    if(n>cap||!g.attributes.position) alloc(n);
    var P=g.attributes.position.array,O=g.attributes.other.array,S=g.attributes.side.array,D=g.attributes.dist.array, acc=0;
    for(var s=0;s<n;s++){ var a=pts[s], b=pts[s+1], v=s*4, L=a.distanceTo(b);
      for(var j=0;j<4;j++){ var me=j<2?a:b, ot=j<2?b:a, q=(v+j)*3; P[q]=me.x;P[q+1]=me.y;P[q+2]=me.z; O[q]=ot.x;O[q+1]=ot.y;O[q+2]=ot.z; S[v+j]=(j%2===0)?1:-1; D[v+j]=j<2?acc:acc+L; }
      acc+=L; }
    ['position','other','side','dist'].forEach(function(k){ g.attributes[k].needsUpdate=true; }); g.setDrawRange(0,n*6); m.points=pts; return m; };
  m.setColor=function(c){ mat.uniforms.color.value.copy(col(c)); return m; };
  m.setOpacity=function(x){ mat.uniforms.opacity.value=x; return m; };
  m.setWidth=function(w){ mat.uniforms.width.value=w; return m; };
  return m;
}

var A={THREE:THREE, scene:scene, camera:camera, renderer:renderer, controls:controls, colors:C, get width(){return W;}, get height(){return H;}, get t(){return t;},
  onFrame:function(fn){ frameFns.push(fn); },
  /** declare the timeline length (seconds): shows a scrubber; t passed to onFrame loops in [0,T) (loop:false → stops at T) */
  duration:function(T,o){ o=o||{}; DUR=Math.max(0.1,+T||0); LOOP=o.loop!==false; if(tl){ tl.style.display='flex'; tl.querySelector('input').max=DUR; } layout(); },
  label:function(text,pos,o){ o=o||{}; var e=document.createElement('div'); e.className='lb'+(o.bg?' bg':''); setTex(e,text);
    e.style.color=cssCol(o.color,C.ink); if(o.size) e.style.fontSize=o.size+'px'; if(o.weight) e.style.fontWeight=o.weight; lay.appendChild(e);
    var al=o.align||'center', ax=al==='left'?0:al==='right'?-100:-50, ay=al==='above'?-100:al==='below'?0:-50; if(al==='left'||al==='right') ay=-50;
    var gap=al==='center'?0:6, dx=(o.dx||0)+(al==='left'?gap:al==='right'?-gap:0), dy=(o.dy||0)+(al==='above'?-gap:al==='below'?gap:0);
    var L={el:e,pos:V(pos||[0,0,0]),ax:ax,ay:ay,dx:dx,dy:dy,set:function(t){ setTex(e,t); return L; },move:function(p){ L.pos=V(p); return L; },hide:function(h){ e.style.display=h?'none':''; return L; },
      color:function(c){ e.style.color=cssCol(c); return L; }}; labels.push(L); return L; },
  caption:function(text){ setTex(cap,text||''); cap.style.display=text?'':'none'; },
  info:function(text){ var box=document.createElement('div'); info.appendChild(box); function upd(){ var any=false; for(var k=0;k<info.children.length;k++){ var c=info.children[k]; if(c.__s&&c.style.display!=='none') any=true; } info.style.display=any?'':'none'; }
    setTex(box,text||''); upd(); return {set:function(s){ setTex(box,s); upd(); }, hide:function(h){ box.style.display=h?'none':''; upd(); }}; },
  slider:function(name,min,max,value,step,fn){ if(typeof step==='function'){ fn=step; step=null; }
    var w=document.createElement('label'); w.className='sl'; w.innerHTML='<span></span><input type="range"><b></b>'; ui.appendChild(w); setTex(w.firstChild,name);
    var i=w.querySelector('input'), b=w.querySelector('b'); i.min=min; i.max=max; i.step=step||((max-min)/100); i.value=value; b.textContent=fmt(value);
    i.oninput=function(){ b.textContent=fmt(i.value); try{ fn&&fn(+i.value); }catch(e){ errMsg('slider: '+e.message); } kick(); }; try{ fn&&fn(+value); }catch(e){ errMsg('slider: '+e.message); }
    layout(); return {get value(){return +i.value;}, set:function(v){ i.value=v; i.oninput(); }}; },
  /** segmented choice: A.choice('$b$', [6, 8], 6, v => ...) (labels: opts.labels) */
  choice:function(name,opts,value,fn,o){ o=o||{}; var w=document.createElement('div'); w.className='ch'; var n=document.createElement('span'); setTex(n,name); w.appendChild(n); ui.appendChild(w);
    var cur=value==null?opts[0]:value, bs=opts.map(function(v,k){ var b=document.createElement('button'); setTex(b,o.labels?o.labels[k]:String(v)); b.onclick=function(){ pick(v); }; w.appendChild(b); return b; });
    function pick(v){ cur=v; bs.forEach(function(b,k){ b.className=opts[k]===v?'on':''; }); try{ fn&&fn(v); }catch(e){ errMsg('choice: '+e.message); } kick(); }
    pick(cur); layout(); return {get value(){return cur;}, set:pick}; },
  button:function(name,fn){ var b=document.createElement('button'); b.className='ub'; setTex(b,name); b.onclick=function(){ try{fn();}catch(e){errMsg(e.message);} kick(); }; ui.appendChild(b); layout(); return b; },
  line:function(pts,o){ o=o||{}; var l=FatLine(o); l.setPoints(pts||[],o.closed); scene.add(l); return l; },
  curve:function(f,t0,t1,o){ o=o||{}; var n=o.samples||200, pts=[]; for(var i=0;i<=n;i++){ pts.push(V(f(t0+(t1-t0)*i/n))); }
    if(o.tube){ var c=new THREE.CatmullRomCurve3(pts); var m=new THREE.Mesh(new THREE.TubeGeometry(c,n,o.tube,8,false),new THREE.MeshStandardMaterial({color:col(o.color,C.blue),roughness:.5})); scene.add(m); return m; }
    var l=A.line(pts,o); l.redraw=function(g,a,b){ var p=[]; a=a==null?t0:a; b=b==null?t1:b; for(var i=0;i<=n;i++) p.push(V((g||f)(a+(b-a)*i/n))); return l.setPoints(p); }; return l; },
  point:function(p,o){ o=o||{}; var c=col(o.color,C.red), g;
    if(is2d&&o.r==null){ g=new THREE.Group(); var ring=new THREE.Mesh(new THREE.CircleGeometry(1,32),new THREE.MeshBasicMaterial({color:0xffffff,depthTest:false,transparent:true}));
      var dot=new THREE.Mesh(new THREE.CircleGeometry(1,32),new THREE.MeshBasicMaterial({color:c,depthTest:false,transparent:true})); ring.renderOrder=3; dot.renderOrder=4; dot.position.z=0.001; g.add(ring); g.add(dot);
      g.material=dot.material; var px=o.size||5; scaled.push(function(u){ dot.scale.setScalar(px*u); ring.scale.setScalar((px+2)*u); }); }
    else { g=new THREE.Mesh(new THREE.SphereGeometry(o.r||0.08,24,16),new THREE.MeshStandardMaterial({color:c,roughness:.45})); }
    g.position.copy(V(p)); scene.add(g); g.move=function(q){ g.position.copy(V(q)); return g; }; return g; },
  arrow:function(from,to,o){ o=o||{}; var c=col(o.color,C.blue), grp=new THREE.Group(), shaft=FatLine({color:c,width:o.width||(is2d?2.5:2.5)}), head;
    if(is2d){ var sh=new THREE.Shape(); sh.moveTo(0,0); sh.lineTo(-1,0.42); sh.lineTo(-0.82,0); sh.lineTo(-1,-0.42); sh.closePath();
      head=new THREE.Mesh(new THREE.ShapeGeometry(sh),new THREE.MeshBasicMaterial({color:c,depthTest:false,transparent:true})); head.renderOrder=2; }
    else head=new THREE.Mesh(new THREE.ConeGeometry(0.4,1,20).translate(0,-0.5,0).rotateZ(-Math.PI/2),new THREE.MeshStandardMaterial({color:c,roughness:.5}));
    grp.add(shaft); grp.add(head); scene.add(grp); grp.material=head.material;
    var F=V(from), T=V(to);
    function upd(){ var d=T.clone().sub(F), L=d.length(), u=unit(), hl=Math.min(L*0.4, (o.head||(is2d?12:14))*u); head.visible=L>1e-9;
      if(L>1e-9){ var dir=d.clone().normalize(); head.position.copy(T); head.scale.setScalar(hl);
        if(is2d) head.rotation.set(0,0,Math.atan2(d.y,d.x)); else head.quaternion.setFromUnitVectors(new THREE.Vector3(1,0,0),dir);
        shaft.setPoints([F,T.clone().sub(dir.multiplyScalar(hl*0.8))]); } else shaft.setPoints([]); }
    grp.update=function(f2,t2){ F=V(f2); T=V(t2); upd(); return grp; }; grp.setColor=function(cc){ var k=col(cc); shaft.setColor(k); head.material.color.copy(k); return grp; };
    scaled.push(upd); upd(); return grp; },
  /** filled polygon (points in a plane, usually z=0): {color, opacity, stroke:true|color, width}; .setPoints(points) */
  polygon:function(pts,o){ o=o||{}; var c=col(o.color,C.blue), grp=new THREE.Group();
    var fill=new THREE.Mesh(new THREE.BufferGeometry(),new THREE.MeshBasicMaterial({color:c,transparent:true,opacity:o.opacity==null?0.18:o.opacity,side:THREE.DoubleSide,depthWrite:false}));
    fill.renderOrder=0; grp.add(fill); var edge=o.stroke===false?null:FatLine({color:o.stroke&&o.stroke!==true?o.stroke:c,width:o.width||2,dashed:o.dashed}); if(edge) grp.add(edge);
    grp.material=fill.material; scene.add(grp);
    grp.setPoints=function(p){ p=(p||[]).map(V); var pos=[]; if(p.length>=3){ var a=p[0]; for(var i=1;i<p.length-1;i++) pos.push(a.x,a.y,a.z,p[i].x,p[i].y,p[i].z,p[i+1].x,p[i+1].y,p[i+1].z); }
      if(p.length>=3&&!o.convex){ try{ var tr=THREE.ShapeUtils.triangulateShape(p.map(function(q){return new THREE.Vector2(q.x,q.y);}),[]); pos=[]; tr.forEach(function(f){ f.forEach(function(k){ pos.push(p[k].x,p[k].y,p[k].z); }); }); }catch(e){} }
      fill.geometry.dispose(); fill.geometry=new THREE.BufferGeometry(); fill.geometry.setAttribute('position',new THREE.Float32BufferAttribute(pos,3));
      if(edge) edge.setPoints(p,true); grp.points=p; return grp; };
    grp.setColor=function(cc){ var k=col(cc); fill.material.color.copy(k); if(edge) edge.setColor(k); return grp; };
    grp.setPoints(pts); return grp; },
  circle:function(center,r,o){ o=o||{}; var pts=[], c=V(center); for(var i=0;i<=96;i++){ var a=i/96*Math.PI*2; pts.push([c.x+r*Math.cos(a),c.y+r*Math.sin(a),c.z]); }
    return o.fill? A.polygon(pts.slice(0,96),{color:o.color,opacity:o.opacity,width:o.width,convex:true}) : A.line(pts,o); },
  /** arc in the xy-plane (angles in radians, counter-clockwise from +x): marks an angle; .set(center, r, a0, a1) */
  arc:function(center,r,a0,a1,o){ o=o||{}; var l=A.line([],o); l.set=function(cn,rr,b0,b1){ var c=V(cn), n=Math.max(8,Math.ceil(Math.abs(b1-b0)*24)), p=[]; for(var i=0;i<=n;i++){ var a=b0+(b1-b0)*i/n; p.push([c.x+rr*Math.cos(a),c.y+rr*Math.sin(a),c.z]); } return l.setPoints(p); }; return l.set(center,r,a0,a1); },
  axes:function(size,o){ o=o||{}; size=size||4; var g=new THREE.Group(), cs=[C.red,C.green,C.blue], nm=o.names||['x','y','z'], neg=o.negative!==false;
    for(var k=0;k<(o.dims||(is2d?2:3));k++){ var d=[0,0,0]; d[k]=1; var a=A.arrow(V(d).multiplyScalar(neg?-size:0),V(d).multiplyScalar(size+0.3),{color:is2d?C.axis:cs[k],width:is2d?1.6:2}); g.add(a);
      A.label(nm[k],V(d).multiplyScalar(size+(is2d?0.3:0.6)),{color:is2d?C.axis:cs[k],align:is2d?(k===0?'below':'left'):'center',dx:is2d&&k===0?-2:0,size:16}); }
    scene.add(g); return g; },
  grid:function(size,div){ if(is2d){ size=size||10; div=div||10; var s=size/div, p=[]; return A.grid2d(-size/2,size/2,-size/2,size/2,s); }
    var g=new THREE.GridHelper(size||10,div||10,0xc9cdd4,0xe8eaee); scene.add(g); return g; },
  grid2d:function(x0,x1,y0,y1,s){ var segs=[]; s=s||1; var g=new THREE.Group();
    for(var x=Math.ceil(x0/s)*s;x<=x1+1e-9;x+=s){ var l=FatLine({color:C.grid,width:1,depthWrite:false,renderOrder:-1}); l.setPoints([[x,y0,-0.01],[x,y1,-0.01]]); g.add(l); }
    for(var y=Math.ceil(y0/s)*s;y<=y1+1e-9;y+=s){ var l2=FatLine({color:C.grid,width:1,depthWrite:false,renderOrder:-1}); l2.setPoints([[x0,y,-0.01],[x1,y,-0.01]]); g.add(l2); }
    scene.add(g); return g; },
  surface:function(f,xr,yr,o){ o=o||{}; var n=o.samples||60, pos=[], idx=[];
    for(var i=0;i<=n;i++) for(var j=0;j<=n;j++){ var x=xr[0]+(xr[1]-xr[0])*i/n, y=yr[0]+(yr[1]-yr[0])*j/n, r=f(x,y); if(Array.isArray(r)) pos.push(r[0],r[1],r[2]); else pos.push(x,(isFinite(r)?r:0),y); }
    for(i=0;i<n;i++) for(j=0;j<n;j++){ var a=i*(n+1)+j, b=a+n+1; idx.push(a,b,a+1,b,b+1,a+1); }
    var g=new THREE.BufferGeometry(); g.setAttribute('position',new THREE.Float32BufferAttribute(pos,3)); g.setIndex(idx); g.computeVertexNormals();
    var m=new THREE.Mesh(g,new THREE.MeshStandardMaterial({color:col(o.color,'#7aa2f0'),side:THREE.DoubleSide,roughness:.6,transparent:o.opacity!=null,opacity:o.opacity==null?1:o.opacity,flatShading:false,polygonOffset:true,polygonOffsetFactor:1,polygonOffsetUnits:1}));
    scene.add(m); if(o.wire!==false){ var w=new THREE.LineSegments(new THREE.WireframeGeometry(g),new THREE.LineBasicMaterial({color:0x2a4a8a,transparent:true,opacity:0.12})); m.add(w); } return m; },
  /** flat 2D teaching view: x right, y up, the box [x0,x1]×[y0,y1] always fits. o: {grid:true|step|false, axes:false|true, ticks:step} */
  mode2d:function(x0,x1,y0,y1,o){ o=o||{}; is2d=true; B2={x0:x0,x1:x1,y0:y0,y1:y1};
    var c=new THREE.OrthographicCamera(-1,1,1,-1,-100,100); camera=c; A.camera=c; controls.dispose(); controls=new THREE.OrbitControls(c,renderer.domElement); A.controls=controls;
    controls.enableRotate=false; controls.enableDamping=true; controls.dampingFactor=0.15; controls.screenSpacePanning=true; controls.mouseButtons.LEFT=THREE.MOUSE.PAN; controls.touches.ONE=THREE.TOUCH.PAN;
    controls.zoomSpeed=1.2; controls.minZoom=0.4; controls.maxZoom=8; hookControls(); fit2d(); controls.target.set((x0+x1)/2,(y0+y1)/2,0); c.position.set((x0+x1)/2,(y0+y1)/2,10); controls.update();
    var step=o.grid===true||o.grid==null? niceStep(Math.max(x1-x0,y1-y0)/12) : +o.grid;
    if(o.grid!==false&&step>0){ var m=step*2; A.grid2d(Math.floor(x0/step)*step-m,Math.ceil(x1/step)*step+m,Math.floor(y0/step)*step-m,Math.ceil(y1/step)*step+m,step); }
    if(o.axes){ var ts=o.ticks||niceStep(Math.max(x1-x0,y1-y0)/8);
      var ax=A.arrow([Math.min(x0,0)-0.02*(x1-x0),0,0],[x1,0,0],{color:C.axis,width:1.6,head:10}), ay=A.arrow([0,Math.min(y0,0)-0.02*(y1-y0),0],[0,y1,0],{color:C.axis,width:1.6,head:10});
      var nm=o.names||['x','y']; A.label(nm[0],[x1,0,0],{color:C.axis,align:'below',dy:4,dx:-6,size:16}); A.label(nm[1],[0,y1,0],{color:C.axis,align:'left',dx:4,size:16});
      if(o.ticks!==false){ var tk=[];
        for(var x=Math.ceil(x0/ts)*ts;x<x1-ts*0.4;x+=ts){ if(Math.abs(x)<1e-9) continue; tk.push([[x,0,0],[x,0,0]]); A.label(fmt(x),[x,0,0],{color:C.gray,size:12,align:'below',dy:3}); }
        for(var y=Math.ceil(y0/ts)*ts;y<y1-ts*0.4;y+=ts){ if(Math.abs(y)<1e-9) continue; A.label(fmt(y),[0,y,0],{color:C.gray,size:12,align:'right',dx:-3}); }
        A.label('O',[0,0,0],{color:C.gray,size:12,align:'right',dx:-3,dy:10}); } }
    return c; },
  ease:function(x){ x=Math.max(0,Math.min(1,x)); return x<0.5?2*x*x:1-Math.pow(-2*x+2,2)/2; },
  lerp:function(a,b,s){ return a+(b-a)*s; },
  /** 0→1 progress of the window [a,b] of t (clamped), handy for staged animations */
  seg:function(t,a,b){ return Math.max(0,Math.min(1,(t-a)/(b-a))); },
};
function niceStep(r){ var p=Math.pow(10,Math.floor(Math.log10(r))), m=r/p; return (m<1.5?1:m<3.5?2:m<7.5?5:10)*p; }
function fit2d(){ if(!B2) return; var c=camera, w=B2.x1-B2.x0, h=B2.y1-B2.y0, r=W/H; w*=1.04; h*=1.04; if(w/h<r) w=h*r; else h=w/r;
  c.left=-w/2; c.right=w/2; c.top=h/2; c.bottom=-h/2; c.updateProjectionMatrix(); }
function hookControls(){ controls.addEventListener('start',function(){ userMoved=true; var r=document.getElementById('home'); if(r) r.style.display='flex'; kick(); });
  controls.addEventListener('change',kick); touchMode(); }
function touchMode(){ // inline card: one-finger vertical drags scroll the chat; expanded: full control
  renderer.domElement.style.touchAction=big?'none':(is2d?'pan-y':'pan-y'); }
hookControls();
window.__A=A;
var kicked=0; function kick(){ kicked=3; }
function layout(){} // caption and timeline are strips below the stage (flex column); the stage's ResizeObserver refits
var prefSent=false;
function sendPref(){ // 2D: ask the card for the height that fits the requested box at this width (no empty bands)
  if(prefSent||!B2||big) return; prefSent=true; var w=B2.x1-B2.x0, h=B2.y1-B2.y0; if(!(w>0&&h>0)) return;
  var extra=document.body.clientHeight-host.clientHeight; try{ parent.animPrefH(window.ANIM_KEY, Math.round(W*h/w+extra+8)); }catch(e){} }
function step(dt){ var tt=t; if(DUR){ tt=LOOP?(t%DUR):Math.min(t,DUR); }
  for(var i=0;i<frameFns.length;i++){ try{ frameFns[i](tt,dt); }catch(e){ if(errors.length<5) errMsg('onFrame: '+e.message); frameFns.splice(i,1); i--; } }
  return tt; }
var tmp=new THREE.Vector3(), tIn=tl&&tl.querySelector('input'), tTx=tl&&tl.querySelector('span');
function syncTl(tt){ if(!DUR||!tIn) return; if(!scrubbing) tIn.value=tt; var p=tt/DUR*100; tIn.style.setProperty('--p',p.toFixed(2)+'%'); tTx.textContent=tt.toFixed(1)+' / '+fmt(DUR)+' 秒'; }
function loop(now){ requestAnimationFrame(loop);
  if(!visible&&reported) { last=null; return; }
  var dt=0; if(!paused&&!scrubbing){ if(last!=null) dt=Math.min(0.1,(now-last)/1000); last=now; t+=dt; } else last=null;
  if(DUR&&!LOOP&&t>=DUR&&!paused){ t=DUR; setPaused(true); }
  var moving=!paused||scrubbing||kicked>0; if(kicked>0) kicked--;
  var tt=(moving||!reported)?step(dt):t; controls.update();
  var u=unit(); if(Math.abs(u-lastScale)>1e-9*Math.max(1,u)){ lastScale=u; for(var i=0;i<scaled.length;i++){ try{ scaled[i](u); }catch(e){} } }
  renderer.render(scene,camera); syncTl(tt); if(!prefSent&&t>0) sendPref();
  for(i=0;i<labels.length;i++){ var L=labels[i]; tmp.copy(L.pos).project(camera); var vis=tmp.z<1&&tmp.z>-1;
    L.el.style.visibility=vis?'':'hidden'; L.el.style.transform='translate('+((tmp.x+1)/2*W+L.dx)+'px,'+((1-tmp.y)/2*H+L.dy)+'px) translate('+L.ax+'%,'+L.ay+'%)'; } }
function setPaused(p){ paused=p; last=null; try{ parent.animState(window.ANIM_KEY,{paused:p}); }catch(e){} }
window.animPause=function(p){ if(!p&&DUR&&!LOOP&&t>=DUR) t=0; setPaused(p); };
window.animRestart=function(){ t=0; last=null; setPaused(false); kick(); };
window.animVisible=function(v){ visible=!!v; last=null; };
window.animBig=function(b){ big=!!b; touchMode(); setTimeout(window.animResize,30); };
window.animResize=function(){ var w=host.clientWidth, h=host.clientHeight; if(!w||!h) return; W=w; H=h; renderer.setSize(W,H); RES.value.set(W,H);
  if(camera.isPerspectiveCamera){ camera.aspect=W/H; camera.updateProjectionMatrix(); } else fit2d(); lastScale=0; kick(); };
window.animHome=function(){ if(!home) return; camera.position.copy(home.p); controls.target.copy(home.t); if(camera.isOrthographicCamera){ camera.zoom=1; camera.updateProjectionMatrix(); } controls.update(); userMoved=false;
  var r=document.getElementById('home'); if(r) r.style.display='none'; kick(); };
if(window.ResizeObserver) new ResizeObserver(function(){ window.animResize(); }).observe(host);
if(tIn){ tIn.addEventListener('input',function(){ scrubbing=true; t=+tIn.value; kick(); });
  tIn.addEventListener('change',function(){ scrubbing=false; t=+tIn.value; if(!paused) setPaused(true); kick(); });
  tIn.addEventListener('pointerdown',function(){ scrubbing=true; }); tIn.addEventListener('pointerup',function(){ scrubbing=false; }); }
var hb=document.getElementById('home'); if(hb) hb.onclick=window.animHome;
window.runAnim=function(code){
  try{ (new Function('A','THREE',code))(A,THREE); }catch(e){ errMsg(e.name+': '+e.message); }
  home={p:camera.position.clone(),t:controls.target.clone()}; window.animResize(); layout();
  requestAnimationFrame(loop);
  setTimeout(report,1600);
};
})();
