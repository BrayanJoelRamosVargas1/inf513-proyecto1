"""Genera las clases de datos y el mapeo a partir del DDL, incluidas migraciones.

No ejecuta SQL. Las asociaciones continuas corresponden a claves foráneas reales.
"""
from pathlib import Path
import re, json, html
from actualizar_puds import split_columns

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/diagramas/datos'
OUT.mkdir(exist_ok=True)
sql = (ROOT / 'database/schema.sql').read_text(encoding='utf-8')
tables = {}
for name, body in re.findall(r'create table if not exists (\w+)\s*\((.*?)\);', sql, re.S | re.I):
    columns = []
    for definition in split_columns(body):
        m = re.match(r'(\w+)\s+((?:bigserial|bigint|integer|boolean|date|timestamptz|text|bytea)|(?:varchar|char|numeric)\([\d,]+\))', definition, re.I)
        if m: columns.append([m[1], m[2], definition])
    tables[name] = columns
for name, col, typ in re.findall(r'alter table (\w+) add column if not exists (\w+)\s+((?:varchar|char|numeric)\([\d,]+\)|\w+)', sql, re.I):
    if col not in [x[0] for x in tables[name]]: tables[name].append([col, typ, 'migración'])

names = {'p1_usuario':'Usuario','p1_convocatoria':'Convocatoria','p1_cargo':'Cargo',
 'p1_oferta':'Oferta','p1_area_examen':'AreaExamen','p1_postulacion':'Postulacion',
 'p1_expediente':'Expediente','p1_evaluacion_requisito':'EvaluacionRequisito',
 'p1_merito':'Merito','p1_examen':'Examen','p1_desempate':'Desempate',
 'p1_asignacion_evaluador':'AsignacionEvaluador', 'p1_auditoria':'Auditoria',
 'p1_correo_control':'ControlCorreo','p1_adjunto_correo':'AdjuntoCorreo',
 'p1_correo_procesado':'CorreoProcesado','p1_confirmacion':'Confirmacion',
 'p1_resultado_orden':'ResultadoOrden','p1_servicio_estado':'EstadoServicio','persona':'Persona'}
relations = [
 ('p1_convocatoria','p1_oferta','ofrece','0..*'),
 ('p1_cargo','p1_oferta','se oferta en','0..*'),
 ('p1_oferta','p1_area_examen','define áreas','0..*'),
 ('p1_convocatoria','p1_postulacion','recibe','0..*'),
 ('p1_cargo','p1_postulacion','es solicitado en','0..*'),
 ('p1_usuario','p1_postulacion','presenta','0..*'),
 ('p1_postulacion','p1_expediente','adjunta','0..*'),
 ('p1_postulacion','p1_evaluacion_requisito','se verifica con','0..*'),
 ('p1_postulacion','p1_merito','acredita','0..*'),
 ('p1_postulacion','p1_examen','se califica con','0..*'),
 ('p1_postulacion','p1_desempate','se resuelve con','0..1'),
 ('p1_postulacion','p1_asignacion_evaluador','recibe evaluadores mediante','0..*'),
 ('p1_usuario','p1_asignacion_evaluador','evalúa mediante','0..*'),
 ('p1_correo_control','p1_adjunto_correo','conserva PDF','0..1')]

def graph(stem, selected, title):
    lines = ['digraph Datos {', 'graph [rankdir=LR, bgcolor="white", pad="0.35", nodesep="0.38", ranksep="1.15", splines=polyline, outputorder=edgesfirst, fontname="Times New Roman", fontsize=18, label="'+title+'", labelloc=t];',
      'node [shape=plain, fontname="Times New Roman", fontsize=16];',
      'edge [dir=none, color="#4a4540", penwidth=1, fontname="Times New Roman", fontsize=13, labeldistance=1.7, labelangle=25];']
    if stem == 'clases_soporte': lines.append('graph [pack=true, packmode="array_u3"];')
    for name in selected:
        rows = ''.join('<TR><TD ALIGN="LEFT">- '+html.escape(col)+': '+html.escape(typ)+'</TD></TR>' for col,typ,_ in tables[name])
        label = '<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="6" BGCOLOR="#F8EACB" COLOR="#8E887D"><TR><TD><B>'+names[name]+'</B></TD></TR><HR/>'+rows+'</TABLE>'
        lines.append(name+' [label=<'+label+'>];')
    for a,b,verb,mult in relations:
        if a in selected and b in selected:
            lines.append(f'{a} -> {b} [label="{verb}", taillabel="1", headlabel="{mult}"];')
    lines.append('}')
    (OUT/(stem+'.dot')).write_text('\n'.join(lines)+'\n', encoding='utf-8')

business = list(names)[:12]
graph('clases_datos', business, 'class Diagrama de clases del dominio de auxiliaturas')
graph('clases_soporte', list(names)[12:], 'class Persistencia de auditoría y soporte del correo')
# La clave de cada mapeo se obtiene del DDL y sus restricciones de tabla.
mapping=[]
for name, cols in tables.items():
    body = re.search(r'create table if not exists '+name+r'\s*\((.*?)\);', sql, re.S|re.I)[1]
    pk = set()
    for keys in re.findall(r'primary key\s*\(([^)]+)\)', body, re.I): pk.update(x.strip() for x in keys.split(','))
    fk = set()
    for keys in re.findall(r'foreign key\s*\(([^)]+)\)', body, re.I): fk.update(x.strip() for x in keys.split(','))
    for col,_,definition in cols:
        if re.search(r'\bprimary key\b', definition, re.I): pk.add(col)
        if re.search(r'\breferences\b', definition, re.I): fk.add(col)
    mapping.append({'tabla':name,'clase':names[name], 'columnas':[
      {'nombre':col,'tipo':typ,'clave':' / '.join(k for k,cond in [('PK',col in pk),('FK',col in fk),('UQ',bool(re.search(r'\bunique\b',definition,re.I)))] if cond)}
      for col,typ,definition in cols]})
assert len(tables)==20 and len(business)==12
assert len(tables['p1_expediente'])==9 and len(tables['p1_postulacion'])==12
(OUT/'mapeo.json').write_text(json.dumps(mapping,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'{len(tables)} tablas; {sum(len(x) for x in tables.values())} columnas; 14 asociaciones físicas')
