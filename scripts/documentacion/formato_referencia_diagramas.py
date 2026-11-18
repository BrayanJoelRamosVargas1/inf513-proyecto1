"""Vistas del PUDS con la notación y presentación de la referencia Grupo05SA.

Conserva clases y métodos existentes; los paquetes funcionales son de análisis.
"""
from pathlib import Path
from html import escape
import json,re,textwrap
from modelos_uml import CASES, generate, sequence
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'docs/diagramas/puds_referencia';OUT.mkdir(exist_ok=True)
BEIGE='#F8EACB';INK='#403b32'

def txt(x,y,s,size=18,anchor='middle',width=35,bold=False):
    lines=textwrap.wrap(s,width,break_long_words=False) or ['']
    return ''.join(f'<text x="{x}" y="{y+i*(size+4)}" font-family="Times New Roman" font-size="{size}" text-anchor="{anchor}" fill="{INK}"'+(' font-weight="bold"' if bold else '')+'>'+escape(line)+'</text>' for i,line in enumerate(lines))
def canvas(w,h,title):
    return [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><defs><marker id="arrow" markerWidth="9" markerHeight="8" refX="8" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8" fill="none" stroke="{INK}"/></marker></defs><rect width="{w}" height="{h}" fill="white"/><rect x="5" y="5" width="{w-10}" height="{h-10}" fill="none" stroke="#80786a"/><path d="M5,5 H{min(w-20,len(title)*8+25)} V25 L{min(w-35,len(title)*8+10)},40 H5 Z" fill="white" stroke="#80786a"/>',txt(15,26,title,15,'start',100)]
def finish(name,s):
    (OUT/(name+'.svg')).write_text(''.join(s)+'</svg>',encoding='utf8')
def actor(s,x,y,name):
    s.append(f'<circle cx="{x}" cy="{y}" r="15" fill="{BEIGE}" stroke="#7b756b"/><path d="M{x},{y+15} v48 m-28,-30 h56 m-28,30 l-25,36 m25,-36 l25,36" fill="none" stroke="#7b756b"/>');s.append(txt(x,y+122,name,17,width=14))
def line(s,x1,y1,x2,y2,label='',dashed=False):
    s.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{INK}"'+(' stroke-dasharray="7 5"' if dashed else '')+' marker-end="url(#arrow)"/>')
    if label:s.append(txt((x1+x2)/2,(y1+y2)/2-10,label,16,width=32))
def package(s,x,y,w,h,title):
    s.append(f'<rect x="{x+4}" y="{y+4}" width="{w}" height="{h}" fill="#bdb8af"/><rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#FCF3E2" stroke="#80786a"/><rect x="{x}" y="{y-27}" width="{w*.78}" height="27" fill="{BEIGE}" stroke="#80786a"/>');s.append(txt(x+8,y-8,title,17,'start',40,bold=True))

def generate_all():
    generate() # Verifica identificadores de métodos y suites contra el código.
    for c in CASES:
        n=c['n'];s=canvas(900,255,f'uc CU{n} {c["title"]}')
        actor(s,100,83,c['actor']);s.append(f'<ellipse cx="500" cy="143" rx="220" ry="66" fill="{BEIGE}" stroke="#80786a"/>');s.append(txt(500,133,f'CU{n} {c["title"]}',21,width=30,bold=True));s.append('<path d="M130,130 H280" fill="none" stroke="#80786a"/>');finish(f'caso_cu{n}',s)
        # Mismo escenario validado, con el estilo de líneas de vida de la referencia.
        old=sequence(c).replace('#edf2f7',BEIGE).replace('#34465d',INK).replace('#172331',INK).replace('#52667d','#80786a').replace('font-family="Arial"','font-family="Times New Roman"')
        old=old.replace('<rect width="1000" height="1210" fill="white"/>','<rect width="1000" height="1210" fill="white"/><rect x="3" y="3" width="994" height="1204" fill="none" stroke="#80786a"/>')
        (OUT/f'secuencia_cu{n}.svg').write_text(old,encoding='utf8')
        # Colaboración: mensajes numerados cerca de cada enlace, no solo índices.
        s=canvas(1000,490,f'communication CU{n} {c["title"]}')
        actor(s,70,110,c['actor'])
        for x,y,w,h,name in [(210,110,190,80,'Main'),(535,110,220,80,'AnalizadorOrden'),(710,335,250,95,'ProcesadorOrden'),(350,335,260,95,'RepositorioPostgres'),(40,335,215,95,'PasarelaCorreo')]:
            s.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{BEIGE}" stroke="#80786a"/>');s.append(txt(x+w/2,y+45,name,19,width=23,bold=True))
        line(s,105,150,210,150,'1, 5 correo')
        line(s,400,150,535,150,'2 analizar')
        line(s,400,190,800,335,'3 procesar')
        line(s,710,380,610,380,'4, 6')
        line(s,440,335,330,190,'7 resultado',True)
        line(s,250,190,165,335,'8 envío')
        line(s,85,335,85,265,'9 respuesta',True)
        s.append(txt(500,465,'4 solicitarConfirmacion / 5 CONFIRMAR desde el actor / 6 ejecutar: '+c['example'],17,width=110))
        finish(f'colaboracion_cu{n}',s)
        source=(ROOT/f'docs/diagramas/uml/clases_cu{n}.dot').read_text(encoding='utf8')
        source=source.replace('#f3f6fa',BEIGE).replace('#34465d',INK).replace('fontname="Arial"','fontname="Times New Roman"').replace('</TR><TR>','</TR><HR/><TR>')
        (OUT/f'clases_cu{n}.dot').write_text(source,encoding='utf8')
    groups=[('Usuarios',[1]),('Convocatorias y postulaciones',[2,3]),('Evaluación y resultados',[4,5,6]),('Auditoría y reportes',[7,8])]
    for i,(title,nums) in enumerate(groups,1):
        s=canvas(650,310,'pkg Identificación de paquete');package(s,90,110,460,155,title);finish(f'paquete_{i}',s)
        s=canvas(920,360,'uc Vista del paquete '+title);package(s,45,135,340,170,title)
        for j,n in enumerate(nums):
            y=95+j*100;s.append(f'<ellipse cx="670" cy="{y+30}" rx="195" ry="42" fill="{BEIGE}" stroke="#80786a"/>');s.append(txt(670,y+25,f'CU{n} {CASES[n-1]["title"]}',18,width=28,bold=True));line(s,385,200,475,y+30,'«trace»',True)
        finish(f'vista_paquete_{i}',s)
    graph='digraph G {graph[rankdir=TB,bgcolor="white",pad=".25",nodesep=".65",ranksep=".8"];node[shape=folder,style=filled,fillcolor="'+BEIGE+'",fontname="Times New Roman",fontsize=18,margin=".2,.18"];edge[style=dashed,arrowhead=empty,fontname="Times New Roman",fontsize=14];u[label="Usuarios"];c[label="Convocatorias y postulaciones"];e[label="Evaluación y resultados"];a[label="Auditoría y reportes"];c->u[label="usa"];e->u[label="usa"];e->c[label="usa"];a->c[label="consulta"];a->e[label="consulta"];}'
    (OUT/'dependencias.dot').write_text(graph,encoding='utf8')
    for target,source in [('clases_general','clases'),('arquitectura_logica','arquitectura'),('despliegue','despliegue'),('casos_general','casos_uso')]:
        dot=(ROOT/f'docs/diagramas/{source}.dot').read_text(encoding='utf8').replace('#f3f6fa',BEIGE).replace('#34465d',INK).replace('fontname="Arial"','fontname="Times New Roman"')
        if target=='casos_general':dot=dot.replace('shape=ellipse','shape=ellipse,style=filled,fillcolor="'+BEIGE+'"')
        (OUT/(target+'.dot')).write_text(dot,encoding='utf8')
    # Arquitectura por paquetes y despliegue por nodos, no flujos de actividad.
    common='graph[rankdir=TB,bgcolor="white",pad=".3",nodesep=".5",ranksep=".7",fontname="Times New Roman",fontsize=17];node[shape=folder,style=filled,fillcolor="'+BEIGE+'",fontname="Times New Roman",fontsize=17,margin=".2,.18"];edge[style=dashed,arrowhead=open,fontname="Times New Roman",fontsize=14];'
    logical='digraph G {'+common+'subgraph cluster_coordinacion {label="Coordinación";main[label="Main"];cfg[label="config\\nAppConfig"];}subgraph cluster_correo {label="email";mail[label="PasarelaCorreo\\ny adaptadores"];pdf[label="LectorAdjuntos\\nInspectorPdf"];}subgraph cluster_comandos {label="comandos";parser[label="AnalizadorOrden\\nOrden"];proc[label="ProcesadorOrden\\nCatalogoComandos\\nResultado"];}subgraph cluster_persistencia {label="persistencia";iface[label="RepositorioProyecto"];repo[label="RepositorioPostgres"];cat[label="GestionCategorias"];}main->cfg;main->mail;main->parser;main->proc;main->repo;mail->pdf;proc->iface;repo->iface[label="implementa",arrowhead=empty];repo->cat;}'
    (OUT/'arquitectura_logica.dot').write_text(logical,encoding='utf8')
    deployment='digraph G {'+common+'node[shape=box3d];dev[label="Equipo de desarrollo\\nJava 17 y Maven\\nCódigo y JAR"];cliente[label="Equipo del participante\\nCliente de correo"];host[label="Equipo de presentación\\nFedora nativo\\nJava 17 y servicio JAR\\nConfiguración privada y logs"];mail[label="Servidor institucional de correo\\nBuzón del Grupo09SA\\nRoundcube HTTPS o transporte autorizado"];db[label="Servidor PostgreSQL\\ndb_grupo09sa\\nEsquema y transacciones"];dev->host[label="paquete JAR"];cliente->mail[label="asunto y PDF"];mail->cliente[label="respuesta"];host->mail[label="lectura y envío"];host->db[label="JDBC"];{rank=same;dev;cliente}{rank=same;mail;db}}'
    (OUT/'despliegue.dot').write_text(deployment,encoding='utf8')
    # Las asociaciones actor/caso son líneas sin flechas de flujo.
    path=OUT/'casos_general.dot';path.write_text(path.read_text(encoding='utf8').replace('edge [','edge [dir=none,'),encoding='utf8')
    # Organigrama funcional del proceso; no representa el organigrama oficial UAGRM.
    (OUT/'organizacion.dot').write_text('digraph G {graph[rankdir=TB,bgcolor="white",pad=".25",ranksep=".7"];node[shape=box,style=filled,fillcolor="'+BEIGE+'",fontname="Times New Roman",fontsize=18,margin=".2,.15"];edge[fontname="Times New Roman",fontsize=14];d[label="Decano"];v[label="Vicedecano"];e[label="Evaluadores asignados"];p[label="Postulantes"];d->v[label="administración del proceso"];v->e[label="asigna y habilita"];v->p[label="convoca y decide plazas"];p->e[style=dashed,label="expediente y evaluación",dir=none];}',encoding='utf8')
    # Modelo conceptual completo, distribuido en una hoja vertical como la referencia.
    data={x['clase']:x for x in json.loads((ROOT/'docs/diagramas/datos/mapeo.json').read_text(encoding='utf8'))}
    positions={'Cargo':(30,80),'Convocatoria':(360,80),'Usuario':(690,80),'AreaExamen':(30,390),'Postulacion':(360,390),'AsignacionEvaluador':(690,390),'Oferta':(30,750),'Expediente':(360,750),'EvaluacionRequisito':(690,750),'Desempate':(30,1080),'Examen':(360,1080),'Merito':(690,1080)}
    s=canvas(1000,1350,'class Diagrama de clases de datos')
    routes=[('Convocatoria','Postulacion',[(500,294),(500,390)],'recibe',510,337,'0..*'),('Usuario','AsignacionEvaluador',[(830,316),(830,390)],'evalúa',870,350,'0..*'),('Usuario','Postulacion',[(690,255),(665,255),(665,450),(640,450)],'presenta',620,320,'0..*'),('Cargo','Postulacion',[(310,140),(332,140),(332,440),(360,440)],'solicita',280,295,'0..*'),('Cargo','Oferta',[(30,145),(15,145),(15,800),(30,800)],'se oferta',69,680,'0..*'),('Convocatoria','Oferta',[(360,245),(320,245),(320,800),(310,800)],'ofrece',265,725,'0..*'),('Oferta','AreaExamen',[(170,750),(170,516)],'define',205,640,'0..*'),('Postulacion','AsignacionEvaluador',[(640,485),(690,485)],'asigna',665,470,'0..*'),('Postulacion','Expediente',[(500,698),(500,750)],'adjunta',537,728,'0..*'),('Postulacion','EvaluacionRequisito',[(640,580),(655,580),(655,820),(690,820)],'verifica',710,705,'0..*'),('Postulacion','Merito',[(640,635),(678,635),(678,1130),(690,1130)],'acredita',740,1040,'0..*'),('Postulacion','Examen',[(560,698),(560,724),(646,724),(646,1050),(500,1050),(500,1080)],'califica',570,1035,'0..*'),('Postulacion','Desempate',[(400,698),(400,724),(324,724),(324,1160),(310,1160)],'resuelve',260,1055,'0..1')]
    for a,b,pts,verb,lx,ly,mult in routes:
        s.append('<polyline points="'+' '.join(f'{x},{y}' for x,y in pts)+'" fill="none" stroke="#80786a"/>');s.append(txt(lx,ly-18 if verb=='asigna' else ly,verb,14,width=22))
    for name,(x,y) in positions.items():
        cols=data[name]['columnas'];h=43+22*len(cols)
        s.append(f'<rect x="{x+3}" y="{y+3}" width="280" height="{h}" fill="#c5c0b6"/><rect x="{x}" y="{y}" width="280" height="{h}" fill="{BEIGE}" stroke="#8e887d"/><line x1="{x}" y1="{y+33}" x2="{x+280}" y2="{y+33}" stroke="#8e887d"/>');s.append(txt(x+140,y+24,name,19,bold=True,width=35))
        for i,col in enumerate(cols):s.append(txt(x+8,y+55+i*22,'- '+col['nombre']+': '+col['tipo'],16,'start',60))
    for a,b,pts,verb,lx,ly,mult in routes:
        for name,point,value in [(a,pts[0],'1'),(b,pts[-1],mult)]:
            bx,by=positions[name];bh=43+22*len(data[name]['columnas']);x,y=point
            side=min([(abs(x-bx),'l'),(abs(x-bx-280),'r'),(abs(y-by),'t'),(abs(y-by-bh),'b')])[1]
            dx,dy={'l':(-15,-7),'r':(18,-7),'t':(-13,-10),'b':(12,18)}[side]
            s.append(txt(x+dx,y+dy,value,12))
    finish('datos_negocio',s)
    # La vista de soporte conserva todas las columnas y su única FK real.
    (OUT/'datos_soporte.dot').write_text((ROOT/'docs/diagramas/datos/clases_soporte.dot').read_text(encoding='utf8'),encoding='utf8')
    print('Fuentes generadas: 8 fichas, 8 colaboraciones, 8 secuencias, 8 clases y vistas de arquitectura y datos')

if __name__=='__main__':generate_all()
