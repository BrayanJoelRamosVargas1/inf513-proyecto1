// Dependencias del runtime de documentos: @viz-js/viz y sharp.
const fs=require('node:fs'), path=require('node:path');
const {instance}=require('@viz-js/viz'), sharp=require('sharp');
const dir=path.resolve(__dirname,'../../docs/diagramas/datos');
(async()=>{
 const viz=await instance();
 for(const file of fs.readdirSync(dir).filter(x=>x.endsWith('.dot'))){
  const svg=viz.renderString(fs.readFileSync(path.join(dir,file),'utf8'),{format:'svg'});
  fs.writeFileSync(path.join(dir,file.replace('.dot','.svg')),svg);
  await sharp(Buffer.from(svg),{density:160}).flatten({background:'#fff'}).png().toFile(path.join(dir,file.replace('.dot','.png')));
  console.log(file, svg.match(/viewBox="([^"]+)"/)[1]);
 }
})().catch(e=>{console.error(e);process.exit(1)});
