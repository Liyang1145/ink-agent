// InkAgent animation runtime (runs inside the animation card's iframe, three.js r147 global THREE).
// The agent's code is a function body called with A (helpers below) and THREE.
(function(){
var errors=[], frameFns=[], labels=[], paused=false, t=0, last=null, reported=false;
var host=document.getElementById('stage'), lay=document.getElementById('labels'), cap=document.getElementById('cap'), ui=document.getElementById('ui');
var W=host.clientWidth||600, H=host.clientHeight||320;
function report(){ if(reported) return; reported=true; var snap=''; try{ renderer.render(scene,camera); snap=renderer.domElement.toDataURL('image/png'); }catch(e){}
  try{ parent.animReport(window.ANIM_KEY, JSON.stringify({errors:errors, snapshot:snap, labels:labels.length, frames:Math.round(t*60)})); }catch(e){} }
window.onerror=function(m,s,l,c,e){ errors.push(String(m)+(l?' (第'+l+'行)':'')); if(!reported) setTimeout(report,50); };
var renderer=new THREE.WebGLRenderer({antialias:true,preserveDrawingBuffer:true});
renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,2)); renderer.setSize(W,H); renderer.setClearColor(0xffffff,1);
host.appendChild(renderer.domElement);
var scene=new THREE.Scene();
var camera=new THREE.PerspectiveCamera(45,W/H,0.01,1000); camera.position.set(6,5,8); camera.lookAt(0,0,0);
var controls=new THREE.OrbitControls(camera,renderer.domElement); controls.enableDamping=true;
scene.add(new THREE.AmbientLight(0xffffff,0.55)); var sun=new THREE.DirectionalLight(0xffffff,0.75); sun.position.set(5,10,7); scene.add(sun);
function V(p){ return p instanceof THREE.Vector3? p : new THREE.Vector3(p[0]||0,p[1]||0,p[2]||0); }
function col(c,d){ return new THREE.Color(c==null?d:c); }
function tex(s){ s=String(s); if(!window.katex) return s.replace(/[&<]/g,function(c){return c==='&'?'&amp;':'&lt;';});
  return s.split(/(\$[^$]+\$)/).map(function(p){ if(/^\$[^$]+\$$/.test(p)){ try{ return katex.renderToString(p.slice(1,-1),{throwOnError:false}); }catch(e){ return p; } } return p.replace(/[&<]/g,function(c){return c==='&'?'&amp;':'&lt;';}); }).join(''); }
var A={THREE:THREE, scene:scene, camera:camera, renderer:renderer, controls:controls, get width(){return W;}, get height(){return H;},
  onFrame:function(fn){ frameFns.push(fn); },
  label:function(text,pos,o){ o=o||{}; var e=document.createElement('div'); e.className='lb'; e.innerHTML=tex(text); if(o.color) e.style.color=o.color; if(o.size) e.style.fontSize=o.size+'px'; lay.appendChild(e);
    var L={el:e,pos:V(pos||[0,0,0]),set:function(t){ e.innerHTML=tex(t); },move:function(p){ L.pos=V(p); },hide:function(h){ e.style.display=h?'none':''; }}; labels.push(L); return L; },
  caption:function(text){ cap.innerHTML=text?tex(text):''; cap.style.display=text?'':'none'; },
  slider:function(name,min,max,value,step,fn){ var w=document.createElement('label'); w.className='sl'; w.innerHTML='<span>'+tex(name)+'</span><input type="range"><b></b>'; ui.appendChild(w);
    var i=w.querySelector('input'), b=w.querySelector('b'); i.min=min; i.max=max; i.step=step||((max-min)/100); i.value=value; b.textContent=(+value).toFixed(2).replace(/\.?0+$/,'');
    i.oninput=function(){ b.textContent=(+i.value).toFixed(2).replace(/\.?0+$/,''); try{ fn&&fn(+i.value); }catch(e){ errors.push('slider: '+e.message); } }; try{ fn&&fn(+value); }catch(e){ errors.push('slider: '+e.message); }
    return {get value(){return +i.value;}, set:function(v){ i.value=v; i.oninput(); }}; },
  button:function(name,fn){ var b=document.createElement('button'); b.className='ub'; b.innerHTML=tex(name); b.onclick=function(){ try{fn();}catch(e){errors.push(e.message);} }; ui.appendChild(b); return b; },
  axes:function(size,o){ o=o||{}; size=size||4; var g=new THREE.Group(), cs=[0xd04040,0x30a050,0x3060d0], nm=o.names||['x','y','z'];
    for(var k=0;k<(o.dims||3);k++){ var d=[0,0,0]; d[k]=1; var a=new THREE.ArrowHelper(V(d),V(d).multiplyScalar(-(o.negative===false?0:size)),size*(o.negative===false?1:2)+0.3,cs[k],0.25,0.12); g.add(a); A.label(nm[k],V(d).multiplyScalar(size+0.55),{color:'#'+cs[k].toString(16).padStart(6,'0')}); }
    scene.add(g); return g; },
  grid:function(size,div){ var g=new THREE.GridHelper(size||10,div||10,0xbbbbbb,0xe2e2e2); scene.add(g); return g; },
  point:function(p,o){ o=o||{}; var m=new THREE.Mesh(new THREE.SphereGeometry(o.r||0.08,20,14),new THREE.MeshStandardMaterial({color:col(o.color,0xe04040)})); m.position.copy(V(p)); scene.add(m); return m; },
  arrow:function(from,to,o){ o=o||{}; var f=V(from), d=V(to).clone().sub(f), l=d.length(); var a=new THREE.ArrowHelper(d.clone().normalize(),f,l,col(o.color,0x2f5fd0).getHex(),Math.min(0.3,l*0.25),Math.min(0.15,l*0.12)); scene.add(a);
    a.update=function(f2,t2){ var F=V(f2), D=V(t2).clone().sub(F), L=D.length(); a.position.copy(F); if(L>1e-9){ a.setDirection(D.normalize()); a.setLength(L,Math.min(0.3,L*0.25),Math.min(0.15,L*0.12)); } }; return a; },
  line:function(pts,o){ o=o||{}; var g=new THREE.BufferGeometry().setFromPoints(pts.map(V)); var l=new THREE.Line(g,new THREE.LineBasicMaterial({color:col(o.color,0x222222)})); scene.add(l);
    l.setPoints=function(p){ l.geometry.dispose(); l.geometry=new THREE.BufferGeometry().setFromPoints(p.map(V)); }; return l; },
  curve:function(f,t0,t1,o){ o=o||{}; var n=o.samples||200, pts=[]; for(var i=0;i<=n;i++){ pts.push(V(f(t0+(t1-t0)*i/n))); }
    if(o.tube){ var c=new THREE.CatmullRomCurve3(pts); var m=new THREE.Mesh(new THREE.TubeGeometry(c,n,o.tube,8,false),new THREE.MeshStandardMaterial({color:col(o.color,0x2f5fd0)})); scene.add(m); return m; }
    return A.line(pts,o); },
  surface:function(f,xr,yr,o){ o=o||{}; var n=o.samples||60, pos=[], idx=[];
    for(var i=0;i<=n;i++) for(var j=0;j<=n;j++){ var x=xr[0]+(xr[1]-xr[0])*i/n, y=yr[0]+(yr[1]-yr[0])*j/n, r=f(x,y); if(Array.isArray(r)) pos.push(r[0],r[1],r[2]); else pos.push(x,(isFinite(r)?r:0),y); }
    for(i=0;i<n;i++) for(j=0;j<n;j++){ var a=i*(n+1)+j, b=a+n+1; idx.push(a,b,a+1,b,b+1,a+1); }
    var g=new THREE.BufferGeometry(); g.setAttribute('position',new THREE.Float32BufferAttribute(pos,3)); g.setIndex(idx); g.computeVertexNormals();
    var m=new THREE.Mesh(g,new THREE.MeshStandardMaterial({color:col(o.color,0x6f9be8),side:THREE.DoubleSide,transparent:o.opacity!=null,opacity:o.opacity==null?1:o.opacity,flatShading:false}));
    scene.add(m); if(o.wire!==false){ var w=new THREE.LineSegments(new THREE.WireframeGeometry(g),new THREE.LineBasicMaterial({color:0x335599,transparent:true,opacity:0.18})); m.add(w); } return m; },
  mode2d:function(x0,x1,y0,y1){ var r=W/H, cx=(x0+x1)/2, cy=(y0+y1)/2, w=x1-x0, h=y1-y0; if(w/h<r) w=h*r; else h=w/r;
    var c=new THREE.OrthographicCamera(cx-w/2,cx+w/2,cy+h/2,cy-h/2,-100,100); c.position.set(0,0,10); camera=c; A.camera=c; controls.dispose(); controls=new THREE.OrbitControls(c,renderer.domElement); controls.enableRotate=false; controls.target.set(cx,cy,0); c.position.set(cx,cy,10); controls.update(); A.controls=controls; return c; },
  ease:function(x){ x=Math.max(0,Math.min(1,x)); return x<0.5?2*x*x:1-Math.pow(-2*x+2,2)/2; },
  lerp:function(a,b,s){ return a+(b-a)*s; },
};
window.__A=A;
window.animPause=function(p){ paused=p; last=null; };
window.animRestart=function(){ t=0; last=null; paused=false; };
window.animResize=function(){ W=host.clientWidth; H=host.clientHeight; renderer.setSize(W,H); if(camera.isPerspectiveCamera){ camera.aspect=W/H; camera.updateProjectionMatrix(); } };
var tmp=new THREE.Vector3();
function loop(now){ requestAnimationFrame(loop); var dt=0; if(!paused){ if(last!=null) dt=Math.min(0.1,(now-last)/1000); last=now; t+=dt; }
  for(var i=0;i<frameFns.length;i++){ try{ frameFns[i](t,dt); }catch(e){ if(errors.length<5) errors.push('onFrame: '+e.message); frameFns.splice(i,1); i--; } }
  controls.update(); renderer.render(scene,camera);
  for(i=0;i<labels.length;i++){ var L=labels[i]; tmp.copy(L.pos).project(camera); var vis=tmp.z<1&&tmp.z>-1;
    L.el.style.visibility=vis?'':'hidden'; L.el.style.transform='translate(-50%,-50%) translate('+((tmp.x+1)/2*W)+'px,'+((1-tmp.y)/2*H)+'px)'; } }
window.runAnim=function(code){
  try{ (new Function('A','THREE',code))(A,THREE); }catch(e){ errors.push(e.name+': '+e.message); }
  requestAnimationFrame(loop);
  setTimeout(report,1600);
};
})();
