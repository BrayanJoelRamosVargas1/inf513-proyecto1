const fs=require('node:fs'),path=require('node:path');
const {instance}=require('@viz-js/viz'),sharp=require('sharp');
const dir=path.resolve(__dirname,'../../docs/diagramas/puds_referencia');
(async()=>{
 const viz=await instance();
 for(const file of fs.readdirSync(dir).filter(x=>x.endsWith('.dot'))){
  let svg=viz.renderString(fs.readFileSync(path.join(dir,file),'utf8'),{format:'svg'});
  fs.writeFileSync(path.join(dir,file.replace('.dot','.svg')),svg);
 }
 for(const file of fs.readdirSync(dir).filter(x=>x.endsWith('.svg'))){
  await sharp(Buffer.from(fs.readFileSync(path.join(dir,file))),{density:160}).resize({width:2200,withoutEnlargement:true}).flatten({background:'#fff'}).png().toFile(path.join(dir,file.replace('.svg','.png')));
 }
 console.log('SVG y PNG del formato de referencia renderizados');
})().catch(e=>{console.error(e);process.exit(1)});
