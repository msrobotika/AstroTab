// Conversión reproducible de datos d3-celestial BSD-3. Sin dependencias npm.
const fs=require('fs'),crypto=require('crypto');
const source=process.argv[2];
if (!source) throw Error('Uso: node tools/generate-milkyway.cjs <mw.json>');
const url='https://raw.githubusercontent.com/ofrohn/d3-celestial/master/data/mw.json';
function convert() {
 const raw=fs.readFileSync(source),json=JSON.parse(raw); const rings=[];
 function simplify(points) {
  if(points.length<5) return points;
  const unwrapped=points.map(p=>p.slice()); for(let i=1;i<unwrapped.length;i++) {while(unwrapped[i][0]-unwrapped[i-1][0]>180) unwrapped[i][0]-=360; while(unwrapped[i][0]-unwrapped[i-1][0]<-180) unwrapped[i][0]+=360;}
  function rdp(a,b) {let max=0,index=-1; const dx=unwrapped[b][0]-unwrapped[a][0],dy=unwrapped[b][1]-unwrapped[a][1];for(let i=a+1;i<b;i++) {const t=Math.max(0,Math.min(1,((unwrapped[i][0]-unwrapped[a][0])*dx+(unwrapped[i][1]-unwrapped[a][1])*dy)/(dx*dx+dy*dy||1))); const dist=Math.hypot(unwrapped[i][0]-unwrapped[a][0]-t*dx,unwrapped[i][1]-unwrapped[a][1]-t*dy);if(dist>max){max=dist;index=i;}}return max>0.2?[...rdp(a,index).slice(0,-1),...rdp(index,b)]:[points[a],points[b]];}
  const mid=Math.floor(points.length/2);const result=[...rdp(0,mid).slice(0,-1),...rdp(mid,points.length-1)];return result.length<4?points:result;
 }
 for(const [layer,f] of json.features.entries()) {
  const polygons=f.geometry.type==='MultiPolygon'?f.geometry.coordinates:[f.geometry.coordinates];
  for(const polygon of polygons) for(let i=0;i<polygon.length;i++) rings.push({flags:layer*2+(i>0?1:0),points:simplify(polygon[i])});
 }
 const count=rings.reduce((n,r)=>n+r.points.length,0),out=Buffer.alloc(8+rings.length*3+count*4);let at=0;
 function u16(v) {out.writeUInt16BE(v&65535,at);at+=2;}
 out.write('MW01',at);at+=4;u16(rings.length);u16(count);
 for(const r of rings) {out[at++]=r.flags;u16(r.points.length);for(const [ra,dec] of r.points) {u16(Math.round(((ra+360)%360)*65536/360));u16(Math.round(dec*100));}}
 fs.writeFileSync('core/src/main/resources/catalog/milkyway.bin',out);
 const meta={url,license:'BSD-3-Clause (THIRD_PARTY_NOTICES.md)',features:json.features.length,rings:rings.length,points:count,bytes:out.length,sourceSHA256:crypto.createHash('sha256').update(raw).digest('hex'),binarySHA256:crypto.createHash('sha256').update(out).digest('hex')};
 console.log(meta);
}
convert();
