// Markdown + KaTeX renderer shared by raster.html and library.html (agent.html keeps its own copy).
var md = window.markdownit({html:false, linkify:true, breaks:true});
function esc(s){return String(s).replace(/[&<>"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c];});}
function tex(src, display){
  src=String(src).replace(/\u00b7/g,'\\cdot ');
  try { return katex.renderToString(src,{displayMode:display,throwOnError:true}); }
  catch(e){
    try { return katex.renderToString(src,{displayMode:display,strict:'ignore',throwOnError:false}); }
    catch(e2){ return '<span class="math-err">'+esc(src)+'</span>'; }
  }
}
function renderWithMath(src){
  var maths=[], out='';
  var parts=src.split(/(```[\s\S]*?(?:```|$)|`[^`\n]*`)/);
  for (var p=0;p<parts.length;p++){
    var s=parts[p];
    if (p%2===1){ out+=s; continue; }
    s=s.replace(/\$\$([\s\S]+?)\$\$|\\\[([\s\S]+?)\\\]|\\\(([\s\S]+?)\\\)|\$(?!\s)([^$\n]+?)\$(?!\d)/g,function(m,a,b,c,d){
      var display=(a!==undefined||b!==undefined);
      var body=a!==undefined?a:b!==undefined?b:c!==undefined?c:d;
      if (d!==undefined && /\s$/.test(d)) return m;
      maths.push(tex(body.trim(),display));
      var token='MATHTOKEN'+(maths.length-1)+'X';
      return display?'\n\n'+token+'\n\n':token;
    });
    out+=s;
  }
  var html=md.render(out);
  html=html.replace(/<p>MATHTOKEN(\d+)X<\/p>/g,function(m,n){return maths[+n];});
  return html.replace(/MATHTOKEN(\d+)X/g,function(m,n){return maths[+n];});
}
