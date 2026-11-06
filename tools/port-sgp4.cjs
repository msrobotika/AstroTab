// Conversión mecánica reproducible de la referencia pública Vallado/Gano 2009 a Kotlin.
// Las fórmulas y orden de operaciones se conservan; verificar con vectores oficiales.
const fs=require('fs');
const dir=process.argv[2] ? require('path').resolve(process.argv[2])+require('path').sep : null;
if (!dir) throw Error('Uso: node tools/port-sgp4.cjs <directorio-JAVA-SGP4-v2>');
const out='core/src/main/kotlin/org/msrobotika/astrotab/satellites/';
fs.mkdirSync(out,{recursive:true});
const clean=s=>s.replace(/\/\*[\s\S]*?\*\//g,'').replace(/\/\/[^\n]*/g,'');
const type=t=>({double:'Double',int:'Int',char:'Char',boolean:'Boolean','double[]':'DoubleArray',SGP4SatData:'Sgp4Record',Gravconsttype:'Gravconsttype'}[t]);
const defaults={Double:'0.0',Int:'0',Char:"' '",Boolean:'false',DoubleArray:'DoubleArray(0)'};
const split=s=>{let depth=0,start=0,a=[];for(let i=0;i<s.length;i++){if('({['.includes(s[i]))depth++;if(')}]'.includes(s[i]))depth--;if(s[i]==','&&depth==0){a.push(s.slice(start,i));start=i+1}}a.push(s.slice(start));return a};
const header='package org.msrobotika.astrotab.satellites\n\n// Vallado, Crawford, Hujsak, Kelso, AIAA 2006-6753; Java: Shawn E. Gano (2009).\n// Port Kotlin AstroTab 2026. Uso libre con cita según FAQ oficial de CelesTrak.\n// https://celestrak.org/publications/AIAA/2006-6753/ (fuente y condiciones en notices).\n\n';
let rec=clean(fs.readFileSync(dir+'SGP4SatData.java','utf8'));
let fields=[];
for(const m of rec.matchAll(/public\s+(double|int|char)\s+([^;]+);/g)) for(const n of m[2].split(',')) fields.push('    var '+n.trim()+': '+type(m[1])+' = '+defaults[type(m[1])]);
fields.push('    var gravconsttype = Sgp4Math.Gravconsttype.wgs72');
fs.writeFileSync(out+'Sgp4Record.kt',header+'internal class Sgp4Record {\n'+fields.join('\n')+'\n}\n');
let source=clean(fs.readFileSync(dir+'SGP4unit.java','utf8'));
let output=header+'@Suppress("UNUSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "NAME_SHADOWING", "UNUSED_VALUE", "UNUSED_PARAMETER")\ninternal object Sgp4Math {\n    const val pi = 3.14159265358979323846\n    enum class Gravconsttype { wgs72old, wgs72, wgs84 }\n';
for(const m of source.matchAll(/(private|public)\s+static\s+(double\[\]|double|boolean)\s+(\w+)\s*\(([^)]*)\)\s*\{/g)) {
    let start=m.index+m[0].length,depth=1,end=start;
    while(depth){if(source[end]=='{')depth++;if(source[end]=='}')depth--;end++}
    let body=source.slice(start,end-1);
    const params=m[4].split(',').map(p=>p.trim().replace(/^final\s+/,'' )).filter(Boolean).map(p=>{let [t,n]=p.split(/\s+/);return{t:type(t),n}});
    body=body.replace(/(?:final\s+)?(double\[\]|double|int|boolean)\s+(\w+[^;]*);/g,(_,t,decl)=>split(decl).map(d=>{let eq=d.indexOf('=');let n=(eq<0?d:d.slice(0,eq)).trim();let v=eq<0?defaults[type(t)]:d.slice(eq+1).trim(); if(t==='double'&&/^-?\d+$/.test(v))v+='.0';return 'var '+n+': '+type(t)+' = '+v+';'}).join('\n'));
    body=body.replace(/new double\[(\d+)\]/g,'DoubleArray($1)').replace(/new double\[\]\s*\{([^}]+)\}/g,(_,v)=>'doubleArrayOf('+v+')');
    body=body.replace(/switch\(whichconst\)/g,'when(whichconst)').replace(/case (wgs\w+):/g,'Gravconsttype.$1 -> {').replace(/break;/g,'}').replace(/default:[\s\S]*?\n\s*}\s*\n\s*}/g,'\n}');
    body=body.replace(/for\((\w+) = (\d+);\s*\1 <= (\d+);\s*\1\+\+\)/g,'for($1 in $2..$3)');
    body=body.replace(/tem5 > 0.0 \? 0.95 : -0.95/g,'if(tem5 > 0.0) 0.95 else -0.95');
    body=body.replace(/Math.pow\(([^,]+),\s*(\d+)\)/g,'Math.pow($1, $2.0)');
    body=body.replace(/\(double\)\s*(\w+)/g,'$1.toDouble()');
    const locals=params.map(p=>'        var '+p.n+' = input_'+p.n).join('\n');
    // Los parámetros Java son mutables; se generan copias locales explícitas en Kotlin.
    output+='    '+(m[1]==='private'?'private ':'')+'fun '+m[3]+'('+params.map(p=>'input_'+p.n+': '+p.t).join(', ')+'): '+type(m[2])+' {\n'+locals+'\n'+body+'\n    }\n';
}
output+='}\n';
// Las constantes de gravedad son inmutables: evitar sqrt/asignación en cada propagación.
output=output.replace('fun getgravconst(input_whichconst: Gravconsttype): DoubleArray {','private val gravityCache = Gravconsttype.entries.associateWith { calculateGravity(it) }\n    fun getgravconst(input_whichconst: Gravconsttype): DoubleArray = gravityCache.getValue(input_whichconst)\n    private fun calculateGravity(input_whichconst: Gravconsttype): DoubleArray {');
// Types de littéraux dans les affectations Double et arguments de propagation.
const doubleNames=new Set([...output.matchAll(/var (\w+): Double\b/g)].map(m=>m[1]));
for(const m of fields.join('\n').matchAll(/var (\w+): Double\b/g)) doubleNames.add(m[1]);
for(const n of doubleNames) output=output.replace(new RegExp('(?<![\\w])('+n+'\\s*=\\s*)(-?\\d+)(\\s*;)','g'),'$1$2.0$3');
fs.writeFileSync(out+'Sgp4Math.kt',output);
console.log('Port generado: '+output.split('\n').length+' líneas.');
