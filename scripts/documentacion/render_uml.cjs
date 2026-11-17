// Requiere @viz-js/viz y sharp disponibles en NODE_PATH.
const fs=require('node:fs'),path=require('node:path');
const {instance}=require('@viz-js/viz'),sharp=require('sharp');
const dir=path.resolve(__dirname,'../../docs/diagramas/uml');
(async()=>{
 const viz=await instance();
 for(const file of fs.readdirSync(dir).filter(x=>x.endsWith('.dot'))){
  const svg=viz.renderString(fs.readFileSync(path.join(dir,file),'utf8'),{format:'svg'});
  fs.writeFileSync(path.join(dir,file.replace('.dot','.svg')),svg);
 }
 for(const file of fs.readdirSync(dir).filter(x=>x.endsWith('.svg'))){
  await sharp(Buffer.from(fs.readFileSync(path.join(dir,file))),{density:180}).resize({width:1800,withoutEnlargement:true}).flatten({background:'#fff'}).png().toFile(path.join(dir,file.replace('.svg','.png')));
 }
 console.log('SVG y PNG de UML generados');
})().catch(e=>{console.error(e);process.exit(1)});
