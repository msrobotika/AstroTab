const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const source = process.argv[2];
if (!source) throw Error('Uso: node tools/generate-catalog.cjs <AstroCYDMS.ino>');
const text = fs.readFileSync(source, 'utf8');
function block(name) {
 const match=text.match(new RegExp('const [^\\n]*\\b'+name+'\\[\\][^=]*= \\{([\\s\\S]*?)\\n\\};'));
 if(!match) throw Error('Bloque no encontrado: '+name);
 return match[1];
}
function rows(name) { return [...block(name).matchAll(/\{([^}]+)\}/g)].map(m=>m[1].split(',').map(Number)); }
function strings(name) { return [...block(name).matchAll(/"([^"\\]*(?:\\.[^"\\]*)*)"/g)].map(m=>JSON.parse('"'+m[1]+'"')); }
const stars=rows('STARS'), points=rows('CPOINTS'), polys=rows('CPOLYS'), labels=rows('CLABELS');
const names=strings('STAR_NAMES'), constellations=strings('CON_NAMES');
if(stars.length!==5044 || points.length!==893 || polys.length!==150 || labels.length!==89 || constellations.length!==89) throw Error('Recuentos inesperados');
const chunks=[];
function byte(n) { const b=Buffer.alloc(1); b.writeUInt8(n & 255); chunks.push(b); }
function short(n) { const b=Buffer.alloc(2); b.writeUInt16BE(n & 65535); chunks.push(b); }
chunks.push(Buffer.from('ATC1'));
[stars.length,points.length,polys.length,labels.length,names.length,constellations.length].forEach(short);
stars.forEach(r=>{short(r[0]);short(r[1]);r.slice(2).forEach(byte);});
points.forEach(r=>r.forEach(short));
polys.forEach(r=>{byte(r[0]);short(r[1]);byte(r[2]);});
labels.forEach(r=>{short(r[0]);short(r[1]);byte(r[2]);});
[...names,...constellations].forEach(s=>{const b=Buffer.from(s,'utf8');short(b.length);chunks.push(b);});
const out=Buffer.concat(chunks);
const dest='core/src/main/resources/catalog/stars.bin';
fs.mkdirSync(path.dirname(dest),{recursive:true});fs.writeFileSync(dest,out);
fs.mkdirSync('docs',{recursive:true});
fs.writeFileSync('docs/CATALOGO.md',`# Catálogo local\n\nExtraído de los bloques stars.h de AstroCYD MS, sin red. Reproducir: \`node tools/generate-catalog.cjs <AstroCYDMS.ino>\`.\n\n5044 estrellas, 893 puntos, 150 polilíneas, 89 etiquetas/entradas de constelaciones (Serpiente dividida), ${names.length} nombres. Catálogo J2000 hasta magnitud 6. No incluye precesión ni extinción atmosférica.\n\nFuente SHA-256: ${crypto.createHash('sha256').update(text).digest('hex')}\n\nBinario SHA-256: ${crypto.createHash('sha256').update(out).digest('hex')} (${out.length} bytes).\n\nFormato ATC1 big-endian: seis recuentos uint16; estrella uint16 AR, int16 declinación, int8 magnitud, int8 B−V, uint8 constelación/nombre (255 ausente); puntos uint16/int16; polilíneas uint8/uint16/uint8; etiquetas uint16/int16/uint8; cadenas con longitud uint16 y UTF-8. AR ×360/65536, declinación /100, magnitud /10. BSD-3 íntegra en THIRD_PARTY_NOTICES.md y Acerca de.\n`);
console.log({stars:stars.length,points:points.length,polys:polys.length,labels:labels.length,names:names.length,bytes:out.length});
