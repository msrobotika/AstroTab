// Convierte únicamente el GeoJSON autorizado; no infiere campos ausentes.
const fs = require('fs');
const crypto = require('crypto');
const source = process.argv[2];
if (!source) throw Error('Uso: node tools/generate-messier.cjs <messier.json>');
const raw = fs.readFileSync(source);
const features = JSON.parse(raw).features.sort((a,b) => +a.id.slice(1)- +b.id.slice(1));
if (features.length !== 110 || features.some((f,i) => f.id !== `M${i+1}`)) throw Error('Catálogo incompleto');
const clean = v => v == null ? '' : String(v).replace(/[\t\r\n]/g,' ');
const lines = features.map(f => {
  const p=f.properties, [ra,dec]=f.geometry.coordinates;
  return [f.id,((ra%360)+360)%360,dec,p.type,p.mag,p.dim,p.con || '',p.alt,p.desig].map(clean).join('\t');
});
fs.writeFileSync('core/src/main/resources/catalog/messier.tsv',lines.join('\n')+'\n');
console.log(`110 objetos; SHA-256 fuente ${crypto.createHash('sha256').update(raw).digest('hex')}`);
