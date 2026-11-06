const fs=require('fs'),path=require('path');
const source=process.argv[2];
if (!source) throw Error('Uso: node tools/import-eclipses.cjs <directorio-con-solar.html-y-lunar.html>');
const months=['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
const rows=[];
for(const kind of ['solar','lunar']) {
 const text=fs.readFileSync(path.join(source,kind+'.html'),'utf8').replace(/<[^>]*>/g,'');
 for(const line of text.split(/\r?\n/)) {
  const m=line.match(/^\s*(\d+)\s+(20(?:2[6-9]|3[0-5]))\s+(\w{3})\s+(\d+)\s+(\d\d:\d\d:\d\d)\s+(\d+)\s+[-\d]+\s+\d+\s+(\w+)/);
  if(!m)continue;
  const td=`${m[2]}-${String(months.indexOf(m[3])+1).padStart(2,'0')}-${m[4].padStart(2,'0')}T${m[5]}Z`;
  const utc=new Date(Date.parse(td)-Number(m[6])*1000).toISOString().replace('.000','');
  rows.push([utc,kind,m[7],m[1],m[6]].join('\t'));
 }
}
rows.sort();fs.mkdirSync('core/src/main/resources/events',{recursive:true});
fs.writeFileSync('core/src/main/resources/events/eclipses.tsv','# NASA/GSFC Fred Espenak; catálogos SE/LE2001-2100. UTC=TD−DeltaT de tabla.\n# UTC\tkind\ttype\tcatalog\tdeltaT_seconds\n'+rows.join('\n')+'\n');
console.log(rows.length+' eclipses NASA 2026–2035');
