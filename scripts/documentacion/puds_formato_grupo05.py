"""Reconstruye el PUDS con la estructura del PDF de referencia, usando datos propios.

Entrada: copia del PUDS de 101 páginas anterior al cambio de formato.
No modifica SQL, Java ni configuración privada. Requiere python-docx y Pillow.
"""
from pathlib import Path
import argparse,re,json,base64
from docx import Document
from docx.shared import Cm,Pt,RGBColor,Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT,WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from PIL import Image,ImageDraw,ImageFont
from modelos_uml import CASES
from actualizar_puds import split_columns

ROOT=Path(__file__).resolve().parents[2];ASSETS=ROOT/'docs/diagramas/puds_referencia'
TITLES=[
 'Gestión de Usuarios Decano Vice Evaluadores Postulantes',
 'Gestión de Parametrizar Convocatorias de Auxiliaturas',
 'Gestión de Postulación Múltiple y Cargar Expediente Digital',
 'Gestión para Evaluar Requisitos Habilitar Postulantes y Controlar Acceso a Examen',
 'Gestión de Méritos Ponderar Examen de Competencia y Asignar Evaluadores',
 'Gestión de Resultado Final Generar Ranking y Asignar Plazas',
 'Gestión del Proceso y Auditoría de Calificaciones',
 'Reportes y Estadísticas']
DESCRIPTIONS={
 'id':'Identificador autogenerado del registro.', 'ci':'Identificación numérica del catálogo persona.',
 'registro':'Registro universitario único del usuario.', 'nombres':'Nombres de la persona.',
 'apellidos':'Apellidos de la persona.', 'rol':'DECANO VICEDECANO EVALUADOR o POSTULANTE.',
 'telefono':'Número telefónico de contacto.', 'correo':'Cuenta de correo vinculada al usuario o persona.',
 'activo':'Indica si el registro puede participar en operaciones nuevas.',
 'creado_en':'Fecha y hora de creación con zona horaria.', 'codigo':'Código único de la convocatoria.',
 'titulo':'Denominación de la convocatoria.', 'fecha_inicio':'Primer día habilitado para postular.',
 'fecha_fin':'Último día habilitado para postular.', 'estado':'Estado vigente del registro y su proceso.',
 'plazas':'Cantidad de plazas permitidas en el ámbito del registro.',
 'nombre':'Nombre del cargo o del archivo recibido.', 'unidad':'Unidad académica del cargo.',
 'convocatoria_id':'Identificador de la convocatoria relacionada.', 'cargo_id':'Identificador del cargo de auxiliatura.',
 'area':'Área normalizada del examen; general identifica la modalidad sin áreas.',
 'peso':'Porcentaje del área dentro del examen configurado.',
 'postulante_id':'Usuario de rol POSTULANTE que presenta la solicitud.',
 'postulacion_id':'Postulación a la que pertenece este registro.',
 'observacion':'Fundamento o comentario de la decisión o calificación.',
 'puntaje_meritos':'Nota agregada de méritos entre cero y cien.',
 'puntaje_examen':'Nota agregada del examen entre cero y cien.',
 'puntaje_final':'Resultado redondeado de 40 % méritos más 60 % examen.',
 'plaza_asignada':'Indica si la postulación ocupa una plaza adjudicada.',
 'evaluacion_version':'Versión de evaluación usada para invalidar desempates anteriores.',
 'descripcion':'Descripción del expediente presentado.',
 'ruta_archivo':'Referencia histórica o marcador del PDF adjunto; no provoca descarga externa.',
 'tipo_mime':'Tipo de contenido del archivo asociado.',
 'nombre_archivo':'Nombre validado del PDF conservado.',
 'contenido':'Bytes del PDF recibido; máximo 2 MiB cuando se almacena el adjunto.',
 'sha256':'Huella hexadecimal que permite verificar los bytes del PDF.',
 'requisito':'Requisito revisado en la postulación.', 'cumple':'Resultado de la revisión del requisito.',
 'evaluador_correo':'Correo del evaluador que registra la revisión o nota.',
 'fecha':'Instante de registro de la actuación con zona horaria.',
 'criterio':'Criterio de mérito evaluado.', 'puntaje':'Nota registrada entre cero y cien.',
 'prioridad':'Orden positivo decidido por la autoridad para resolver el empate.',
 'justificacion':'Fundamento de al menos diez caracteres para resolver el empate.',
 'actor':'Cuenta responsable de la actuación.', 'evaluador_id':'Usuario EVALUADOR asignado a la postulación.',
 'tipo':'Tipo o área de la asignación del evaluador.', 'accion':'Comando o acción que origina el evento.',
 'entidad':'Entidad afectada o consultada; referencia lógica de auditoría.',
 'entidad_id':'Identificador lógico del registro; no es una FK polimórfica.',
 'detalle':'Información de la acción y estados anterior y posterior cuando corresponde.',
 'mensaje_id':'Identificador estable del correo original que evita repetir su operación.',
 'remitente':'Cuenta desde la que se recibió el correo.', 'asunto':'Asunto original de la solicitud.',
 'exito':'Indica si la operación terminó correctamente.',
 'procesado_en':'Fecha y hora de procesamiento del correo.',
 'respuesta_asunto':'Asunto de la respuesta pendiente o conservada.',
 'respuesta_cuerpo':'Cuerpo de la respuesta pendiente o conservada.',
 'intentos':'Cantidad de intentos registrados para el procesamiento.',
 'ultimo_error':'Información interna del último fallo de procesamiento.',
 'siguiente_intento':'Momento a partir del cual puede reintentarse el trabajo.',
 'actualizado_en':'Última actualización del estado persistido.',
 'admitido_en':'Momento de admisión efectiva; puede quedar vacío si fue diferido.',
 'token_hash':'Huella del código de confirmación; el código activo no se guarda aquí.',
 'orden_json':'Representación de la orden original pendiente de confirmación.',
 'vence_en':'Límite temporal de la confirmación de treinta minutos.',
 'confirmacion_id':'Identificador del correo que consume la confirmación.',
 'usado':'Indica si la confirmación ya se consumió.',
 'ejecutado_en':'Instante en que se guardó el resultado idempotente.',
 'clave':'Nombre del dato técnico persistido del servicio.',
 'valor':'Contenido del dato técnico identificado por clave.',
 'ocupacion':'Ocupación del catálogo independiente persona.', 'celular':'Número celular del catálogo persona.'}

def schema():
    sql=(ROOT/'database/schema.sql').read_text(encoding='utf8');tables={}
    for name,body in re.findall(r'create table if not exists (\w+)\s*\((.*?)\);',sql,re.S|re.I):
        cols=[];pk=set();fk=set();uniques=[]
        for keys in re.findall(r'primary key\s*\(([^)]+)\)',body,re.I):pk.update(k.strip() for k in keys.split(','))
        for keys in re.findall(r'foreign key\s*\(([^)]+)\)',body,re.I):fk.update(k.strip() for k in keys.split(','))
        for keys in re.findall(r'unique\s*\(([^)]+)\)',body,re.I):uniques.append([k.strip() for k in keys.split(',')])
        for part in split_columns(body):
            m=re.match(r'(\w+)\s+((?:bigserial|bigint|integer|boolean|date|timestamptz|text|bytea)|(?:varchar|char|numeric)\([\d,]+\))',part,re.I)
            if not m:continue
            col,typ=m.groups();cols.append(dict(nombre=col,tipo=typ,definicion=part))
            if 'primary key' in part:pk.add(col)
            if 'references' in part:fk.add(col)
            if 'unique' in part:uniques.append([col])
        tables[name]=dict(cols=cols,pk=pk,fk=fk,uq=uniques)
    for name,col,definition in re.findall(r'alter table (\w+) add column if not exists (\w+)\s+([^;]+);',sql,re.I):
        typ=re.match(r'((?:varchar|char|numeric)\([\d,]+\)|\w+)',definition)[1]
        if col not in [x['nombre'] for x in tables[name]['cols']]:tables[name]['cols'].append(dict(nombre=col,tipo=typ,definicion=definition))
    assert len(tables)==20 and sum(len(t['cols']) for t in tables.values())==134
    assert all(c['nombre'] in DESCRIPTIONS for t in tables.values() for c in t['cols'])
    return tables

def cover_image():
    im=Image.new('RGB',(1100,650),'white');dr=ImageDraw.Draw(im)
    def font(n):return ImageFont.truetype('C:/Windows/Fonts/timesbd.ttf',n)
    dr.rounded_rectangle((20,5,1070,215),radius=34,fill='#e66c00')
    dr.rounded_rectangle((105,55,1090,260),radius=34,fill='#96cf49')
    for y,s in [(95,'UNIVERSIDAD AUTÓNOMA'),(155,'GABRIEL RENÉ MORENO')]:dr.text((590,y),s,font=font(40),fill='#2859a6',anchor='mm')
    dr.rounded_rectangle((585,245,1090,635),radius=65,fill='#d99694')
    dr.rounded_rectangle((585,225,1090,500),radius=65,fill='#8db4e2')
    for y,s in [(285,'FACULTAD DE INGENIERÍA'),(330,'EN CIENCIAS DE LA COMPUTACIÓN'),(375,'Y TELECOMUNICACIONES'),(430,'FICCT')]:dr.text((837,y),s,font=font(25),fill='#17365d',anchor='mm')
    for y,s in [(545,'Materia Tecnología Web'),(590,'Grupo 09 SA')]:dr.text((837,y),s,font=font(29),fill='#17365d',anchor='mm')
    logo=Image.open(ASSETS/'logo-ficct.jpg');logo.thumbnail((385,375));im.paste(logo,(155,265))
    im.save(ASSETS/'portada.png')

def sql_display(sql,limit=72):
    """Envuelve solo en espacios o comas fuera de literales; conserva los tokens."""
    lines=[]
    for original in sql.splitlines():
        if original.lstrip().startswith('--'):
            import textwrap
            lines.extend(textwrap.wrap(original,limit,subsequent_indent='-- '));continue
        rest=original
        while len(rest)>limit:
            quote=False;points=[];i=0
            while i<len(rest):
                ch=rest[i]
                if ch=="'":
                    if quote and i+1<len(rest) and rest[i+1]=="'":i+=2;continue
                    quote=not quote
                elif not quote and (ch.isspace() or ch==','):points.append(i+1)
                i+=1
            eligible=[i for i in points if 8<i<=limit]
            if not eligible:break
            cut=max(eligible);lines.append(rest[:cut].rstrip());rest='        '+rest[cut:].lstrip()
        lines.append(rest)
    token=lambda s:re.findall(r"'(?:''|[^'])*'|[^\s]",re.sub(r'--[^\n]*','',s))
    assert token(sql)==token('\n'.join(lines)), 'El ajuste del SQL cambió sus tokens'
    return lines

def build(base):
    old=Document(base);els=list(old._element.body)
    # Inventario de los CU existentes, conservando sus precondiciones y flujos.
    from docx.text.paragraph import Paragraph
    from docx.table import Table
    cases=[]
    for n in range(1,9):
        start=next(i for i,e in enumerate(els) if e.tag==qn('w:p') and Paragraph(e,old).style.name=='Heading 2' and Paragraph(e,old).text.startswith(f'5 {n} CU{n} '))
        end=next((i for i in range(start+1,len(els)) if els[i].tag==qn('w:p') and Paragraph(els[i],old).style.name=='Heading 2'),len(els))
        if n==8:end=next(i for i in range(start+1,len(els)) if els[i].tag==qn('w:p') and Paragraph(els[i],old).text=='6 Análisis de arquitectura')
        table=next(Table(e,old) for e in els[start:end] if e.tag==qn('w:tbl'))
        vals={r.cells[0].text:r.cells[1].text for r in table.rows[1:]};flows=[];exceptions=[];mode=None
        for e in els[start:end]:
            if e.tag!=qn('w:p'):continue
            s=Paragraph(e,old).text
            if s=='Flujo principal':mode='flow';continue
            if s=='Alternativas y excepciones':mode='exception';continue
            if s.startswith('Persistencia:') or s.startswith('Verificación:') or s.startswith('Figura ') or s=='Realización en el código':mode=None
            if mode=='flow' and s:flows.append(s)
            if mode=='exception' and s:exceptions.append(s)
        cases.append(dict(vals=vals,flow=flows,exceptions=exceptions))
    cover_image();d=Document();sec=d.sections[0];sec.page_width=Cm(21);sec.page_height=Cm(29.7)
    sec.top_margin=Cm(2.5);sec.bottom_margin=Cm(2.3);sec.left_margin=Cm(3);sec.right_margin=Cm(2.5);sec.footer_distance=Cm(1.1)
    normal=d.styles['Normal'];normal.font.name='Times New Roman';normal.font.size=Pt(12);normal.font.color.rgb=RGBColor(0,0,0)
    normal.paragraph_format.line_spacing=1.5;normal.paragraph_format.space_after=Pt(8)
    for level,size in [(1,16),(2,14),(3,12),(4,12)]:
        st=d.styles[f'Heading {level}'];st.font.name='Times New Roman';st.font.size=Pt(size);st.font.bold=True;st.font.color.rgb=RGBColor(0,0,0);st.font.underline=False
        st.paragraph_format.space_before=Pt(13);st.paragraph_format.space_after=Pt(8);st.paragraph_format.keep_with_next=True
    d.styles['Heading 4'].paragraph_format.space_before=Pt(8)
    d.styles['Heading 4'].paragraph_format.space_after=Pt(4)
    d.styles['Title'].font.name='Times New Roman';d.styles['Title'].font.size=Pt(17);d.styles['Title'].font.color.rgb=RGBColor(0,0,0);d.styles['Title'].font.bold=True;d.styles['Title'].font.underline=False
    from lxml import etree
    for relation in d.part.rels.values():
        if relation.reltype.endswith('/theme'):
            theme=etree.fromstring(relation.target_part.blob)
            for latin in theme.xpath('//*[local-name()="majorFont" or local-name()="minorFont"]/*[local-name()="latin"]'):latin.set('typeface','Times New Roman')
            relation.target_part._blob=etree.tostring(theme,xml_declaration=True,encoding='UTF-8',standalone=True)
    settings=d.settings.element
    for el in list(settings):
        if el.tag in (qn('w:updateStyles'),qn('w:attachedTemplate')):settings.remove(el)
    update_style=OxmlElement('w:updateStyles');update_style.set(qn('w:val'),'false');settings.append(update_style)
    for name in ('TOC 1','TOC 2','TOC 3'):
        if name not in d.styles:d.styles.add_style(name,1)
        s=d.styles[name];s.font.name='Times New Roman';s.font.size=Pt(11);s.paragraph_format.space_after=Pt(1);s.paragraph_format.line_spacing=1.1
    footer=sec.footer.paragraphs[0];footer.alignment=WD_ALIGN_PARAGRAPH.CENTER
    f=OxmlElement('w:fldSimple');f.set(qn('w:instr'),'PAGE');footer._p.append(f)
    def p(s='',style=None,align=None):
        x=d.add_paragraph(s,style)
        x.alignment=align if align is not None else WD_ALIGN_PARAGRAPH.JUSTIFY
        return x
    def h(s,l=1):return p(s,'Heading '+str(l),WD_ALIGN_PARAGRAPH.LEFT)
    def page():d.add_page_break()
    def fig(stem,max_h=6.9,width=6.0):
        im=Image.open(ASSETS/(stem+'.png'));w=min(width,max_h*im.width/im.height)
        x=p(align=WD_ALIGN_PARAGRAPH.CENTER);x.paragraph_format.line_spacing=1;x.paragraph_format.space_after=Pt(8)
        x.add_run().add_picture(str(ASSETS/(stem+'.png')),width=Inches(w))
        for el in x._p.xpath('.//wp:docPr'):el.set('descr',stem.replace('_',' '))
        return x
    def table(headers,rows,widths=None,font=10.5,header=True):
        t=d.add_table(rows=1 if header else 0,cols=len(headers));t.alignment=WD_TABLE_ALIGNMENT.CENTER;t.autofit=False
        if header:
            for cell,txt in zip(t.rows[0].cells,headers):cell.text=txt
        for vals in rows:
            for cell,txt in zip(t.add_row().cells,vals):cell.text=str(txt)
        if widths:
            for col,w in zip(t.columns,widths):col.width=Cm(w)
        pr=t._tbl.tblPr;borders=OxmlElement('w:tblBorders')
        for side in ('top','left','bottom','right','insideH','insideV'):
            el=OxmlElement('w:'+side);el.set(qn('w:val'),'single');el.set(qn('w:sz'),'4');el.set(qn('w:color'),'999999');borders.append(el)
        pr.append(borders)
        for ri,row in enumerate(t.rows):
            row._tr.get_or_add_trPr().append(OxmlElement('w:cantSplit'))
            for ci,cell in enumerate(row.cells):
                cell.vertical_alignment=WD_CELL_VERTICAL_ALIGNMENT.TOP
                if widths:cell.width=Cm(widths[ci])
                margins=OxmlElement('w:tcMar')
                for side in ('top','bottom','left','right'):
                    el=OxmlElement('w:'+side);el.set(qn('w:w'),'55');el.set(qn('w:type'),'dxa');margins.append(el)
                cell._tc.get_or_add_tcPr().append(margins)
                if header and ri==0:
                    sh=OxmlElement('w:shd');sh.set(qn('w:fill'),'F2F2F2');cell._tc.get_or_add_tcPr().append(sh)
                for para in cell.paragraphs:
                    para.paragraph_format.line_spacing=1.0;para.paragraph_format.space_after=Pt(2);para.paragraph_format.space_before=Pt(1)
                    if header and ri==0:para.paragraph_format.keep_with_next=True
                    for run in para.runs:run.font.name='Times New Roman';run.font.size=Pt(font);run.bold=header and ri==0
        if header:t.rows[0]._tr.get_or_add_trPr().append(OxmlElement('w:tblHeader'))
        return t
    # Portada y contenido, como las primeras cuatro páginas de la referencia.
    fig('portada',max_h=3.7)
    x=p('Documento de defensa',align=WD_ALIGN_PARAGRAPH.CENTER);x.runs[0].bold=True
    title=p('Sistema de gestión de convocatorias de auxiliaturas por correo electrónico','Title',WD_ALIGN_PARAGRAPH.CENTER)
    border=OxmlElement('w:pBdr');bottom=OxmlElement('w:bottom');bottom.set(qn('w:val'),'nil');border.append(bottom);title._p.get_or_add_pPr().append(border)
    p('Integrantes Grupo 09 SA',align=WD_ALIGN_PARAGRAPH.CENTER)
    for name,reg in [('Acosta Osorio Lin Fernanda','223045888'),('Ramos Vargas Brayan Joel','223043982'),('Rodriguez Aguilera Walter','213114976')]:
        x=p(name+'    '+reg,align=WD_ALIGN_PARAGRAPH.CENTER);x.paragraph_format.line_spacing=1;x.paragraph_format.space_after=Pt(5);x.runs[0].font.size=Pt(11)
    p('Docente Ing. Evans Balcázar Veizaga',align=WD_ALIGN_PARAGRAPH.CENTER)
    p('Santa Cruz Bolivia\nGestión 2 2026',align=WD_ALIGN_PARAGRAPH.CENTER)
    page();p('Contenido',align=WD_ALIGN_PARAGRAPH.LEFT)
    x=p();field=OxmlElement('w:fldSimple');field.set(qn('w:instr'),'TOC \\o "1-3" \\h \\z \\u');x._p.append(field)
    page();h('1 Perfil');h('1.1 Introducción',2)
    p('El sistema del Grupo 09 SA administra convocatorias de auxiliaturas mediante instrucciones enviadas en el asunto del correo electrónico. El servicio interpreta la solicitud, verifica al remitente, consulta o modifica PostgreSQL y devuelve una respuesta a la misma cuenta. Los participantes son Decano, Vicedecano, Evaluadores y Postulantes.')
    p('El documento desarrolla los ocho casos aprobados, desde requisitos y análisis hasta diseño de clases, datos y script. Las operaciones de varios comandos pertenecen a esos casos; la confirmación de correo, la cola y las bitácoras son mecanismos transversales. El código, los manuales y las evidencias acompañan al PUDS en el repositorio del grupo.')
    h('1.2 Antecedentes',2)
    p('El proceso de auxiliaturas reúne convocatorias, plazas por cargo, expedientes, revisión de requisitos, méritos y examen de competencia. Mantener esos datos en registros separados dificulta comprobar quién tomó una decisión, qué versión de una nota se utilizó y por qué se asignó una plaza.')
    p('La actividad INF513 SA Entrega Proyecto No 1 Sistemas vía Mail exige procesar órdenes por correo, persistir información y documentar la solución. El 5 de octubre de 2026 el docente aprobó los ocho casos del Grupo 09. Este proyecto organiza esos casos alrededor de una misma base transaccional y de permisos asociados a los participantes.')
    h('1.3 Justificación',2)
    for label,body in [('Técnica','El correo desacopla el envío de una solicitud de su procesamiento. La validación de la gramática, las consultas preparadas, el control de roles y el resultado idempotente permiten rechazar entradas incorrectas y reintentar fallos sin duplicar modificaciones.'),('Académica','La solución integra modelado UML, programación Java, protocolos de comunicación, JDBC, PostgreSQL y pruebas automatizadas. Los diagramas explican responsabilidades que pueden localizarse en los archivos entregados.'),('Práctica','Una cuenta de correo permite presentar solicitudes y consultar respuestas sin instalar una interfaz propia. El expediente permanece vinculado a su postulación y las decisiones dejan una traza consultable por la autoridad.')]:
        p(label+'. '+body)
    h('1.4 Descripción del problema',2)
    for s in ['Dispersión del expediente. Una persona puede postular a varios cargos; cada solicitud necesita su propia convocatoria, cargo y documentación, sin duplicar la misma combinación.', 'Evaluación sin trazabilidad. Las correcciones deben conservar el historial y recalcular las notas con los aportes vigentes de los evaluadores asignados.', 'Adjudicación inconsistente. No basta ordenar por nota: deben respetarse habilitación, evaluación completa, cupos por cargo y resolución motivada de empates.', 'Repetición de órdenes. Un correo reenviado o una respuesta fallida no debe crear otra postulación ni ejecutar dos veces una asignación de plaza.']:p(s)
    h('1.5 Objetivos',2);h('1.5.1 Objetivo general',3)
    p('Implementar un sistema de gestión de convocatorias de auxiliaturas por correo electrónico que resuelva los ocho casos aprobados, con control de acceso, expediente digital, evaluación, ranking, plazas y auditoría transaccional.')
    h('1.5.2 Objetivos específicos',3)
    for i,s in enumerate(['Analizar los requisitos y vincular cada uno con los ocho casos aprobados y sus actores.','Diseñar la arquitectura, las interacciones y el modelo de datos conforme a la implementación entregada.','Construir el procesamiento de órdenes, las validaciones de negocio y la persistencia en PostgreSQL.','Verificar permisos, cálculos, concurrencia, archivos y entradas adversariales mediante pruebas reproducibles.','Preparar el ejecutable, la instalación en Fedora y la documentación necesaria para la defensa.'],1):p(f'OE{i} {s}')
    h('1.6 Alcance',2);h('Alcance funcional',3)
    for n,title in enumerate(TITLES,1):p(f'CU{n} {title}. '+CASES[n-1]['rule'])
    h('Alcance técnico',3)
    p('Servicio Java 17 construido con Maven; órdenes en Subject; adaptadores de correo; analizador de comandos; persistencia JDBC en PostgreSQL; respuestas al remitente; confirmación de operaciones privadas; cola durable; logging de actividad y excepciones. Los ejemplos INSPER y LISPER de la actividad conservan los siete campos de persona y son independientes de los roles del dominio.')
    h('Limitaciones del sistema',3)
    p('La interfaz de esta parte es el correo electrónico. Las consultas públicas no otorgan roles; un evaluador solo actúa sobre sus asignaciones. La nota final usa 40 % méritos y 60 % examen conforme al planteamiento del grupo. El análisis del PDF no valida su contenido académico. El ensayo nativo completo en Fedora y el recorrido con las cuentas personales de cada rol deben comprobarse antes de la defensa.')
    h('1.7 Tecnología para el desarrollo de software',2);h('1.7.1 Sistema operativo',3)
    p('El desarrollo y las verificaciones locales documentadas se realizaron en Windows con Java 17 y PostgreSQL aislado. La presentación prevista utiliza Fedora nativo en arranque dual. El paquete contiene scripts Linux y una unidad systemd para ejecutar el mismo JAR con configuración privada; las validaciones automáticas de Linux se ejecutan en integración continua.')
    h('1.7.2 Sistema de gestión de base de datos',3)
    p('PostgreSQL conserva las postulaciones, evaluaciones y resultados mediante claves primarias, claves foráneas, unicidad y restricciones CHECK. Las escrituras del negocio, su auditoría y el resultado idempotente comparten transacción. El esquema contiene doce tablas del negocio, auditoría de CU7, seis tablas de soporte del correo y el catálogo persona del ejemplo de la actividad.')
    h('1.7.3 Herramientas CASE',3)
    p('Se utilizan vistas UML de casos de uso, paquetes, colaboración, secuencia, clases y despliegue. La presentación toma como referencia el documento del Grupo 05 SA. Nuestras fuentes se mantienen en SVG y DOT y se renderizan con herramientas del proyecto; no se atribuye su elaboración a Enterprise Architect. Las clases conceptuales de datos se distinguen de las clases Java implementadas.')
    h('2 Marco teórico');h('2.1 Correo electrónico',2)
    p('El correo electrónico permite intercambiar mensajes entre cuentas a través de servicios de transporte y almacenamiento. En este proyecto el asunto representa la orden y el cuerpo de la respuesta comunica el resultado o el error. El identificador estable del mensaje permite correlacionar recepción, confirmación, ejecución y envío.')
    for title,body in [('2.1.1 Origen del correo electrónico','El correo se desarrolló en las primeras redes de computadoras y se consolidó mediante estándares abiertos. Las especificaciones de SMTP, POP3 y MIME describen el intercambio y formato de mensajes y permiten que distintos clientes participen en el mismo proceso.'),('2.1.2 Funcionamiento','El usuario envía una orden al buzón del grupo. El adaptador recupera el mensaje, el analizador obtiene una Orden y el procesador valida parámetros y permiso. Las operaciones privadas solicitan CONFIRMAR desde la cuenta original. Después se guarda el resultado y se envía una respuesta; si falla el transporte, la cola conserva el trabajo pendiente.'),('2.1.3 Protocolo SMTP','SMTP define el envío de correo mediante una sesión con comandos y respuestas. La interfaz PasarelaCorreo separa el envío del negocio. La configuración debe corresponder al transporte autorizado y disponible; no se considera el envío confirmado únicamente por intentar abrir una conexión.'),('2.1.4 Protocolos POP3 e IMAP','POP3 e IMAP permiten recuperar mensajes de un buzón con semánticas diferentes. El proyecto dispone de adaptadores de lectura. La configuración institucional documentada utiliza por defecto la pasarela Roundcube mediante HTTPS; por ello no se afirma que toda ejecución use sockets POP3 como en el proyecto de referencia.'),('2.1.5 MIME','MIME describe tipos y codificaciones del contenido de un mensaje. LectorAdjuntos extrae el expediente PDF con límites de nombre, cantidad y tamaño. El adjunto se conserva de forma durable antes del marcado de lectura y su huella SHA 256 permite comparar los bytes recibidos y guardados.'),('2.1.6 Sockets TCP IP','Las conexiones de red transportan solicitudes entre el servicio, el servidor de correo y PostgreSQL. Los adaptadores mantienen los detalles del protocolo fuera de las reglas académicas. Se fijan tiempos máximos y reintentos para impedir que una conexión interrumpida paralice indefinidamente el ciclo.')]:h(title,3);p(body)
    h('Descripción de la organización',2)
    p('El dominio corresponde al proceso de auxiliaturas de la Facultad de Ingeniería en Ciencias de la Computación y Telecomunicaciones de la UAGRM. Se modelan las responsabilidades de las autoridades, evaluadores y postulantes dentro de ese proceso. No se afirma que este prototipo sea un sistema institucional en producción.')
    for title,body in [('Misión del proceso modelado','Organizar la recepción, evaluación y adjudicación de postulaciones conservando la relación entre datos, decisiones y responsables.'),('Visión del proceso modelado','Disponer de un expediente y un historial verificables que permitan explicar los resultados de cada convocatoria. Estas formulaciones describen el propósito del sistema y no sustituyen la misión o visión oficial de la institución.'),('Valores del proceso','Trazabilidad de decisiones, igualdad en la aplicación de requisitos, confidencialidad del expediente y consistencia de las calificaciones.'),('Organización funcional','Decano y Vicedecano administran el proceso. Los evaluadores revisan y califican sus asignaciones; los postulantes presentan y consultan sus propias solicitudes. La siguiente vista organiza las responsabilidades del proceso, sin presentarse como organigrama institucional oficial.'),('Ubicación','El proyecto académico corresponde a la UAGRM en Santa Cruz de la Sierra, Bolivia. El correo permite interactuar a distancia; la defensa prevista se ejecuta en el equipo del estudiante con Fedora.')]:
        h(title,3);p(body)
        if title=='Organización funcional':fig('organizacion',max_h=2.4)
    h('3 Requisitos');h('3.1 Requisitos del sistema',2)
    p('Los requisitos funcionales describen las operaciones observables; los no funcionales establecen condiciones de seguridad, integridad y operación. Su alcance se limita a los ocho casos aprobados y a los mecanismos exigidos por la actividad.')
    h('3.1.1 Requerimientos funcionales',3)
    rf=next(t for t in old.tables if t.cell(0,0).text=='ID' and 'Requisito verificable' in t.cell(0,1).text)
    for row in rf.rows[1:]:
        a,b,c=[x.text for x in row.cells];p(a+' '+b+' Ámbito '+c+'.')
    p('RF25 Parametrizar cupos por cargo y ponderaciones de examen por área dentro de CU2 y CU5. RF26 Resolver con fundamento los empates que afectan plazas dentro de CU6. Estas operaciones completan los casos aprobados y no crean casos adicionales.')
    h('3.1.2 Requerimientos no funcionales',3)
    rnf=next(t for t in old.tables if t.cell(0,0).text=='ID' and t.cell(0,1).text=='Criterio y mecanismo')
    table(['Identificación','Condición verificable'],[[c.text for c in row.cells] for row in rnf.rows[1:]],[4,11.5])
    h('3.2 Identificación de actores y casos de uso',2)
    for actor,desc in [('Decano','Administra usuarios y convocatorias, habilita postulantes, asigna evaluadores y plazas, y consulta auditoría y reportes.'),('Vicedecano','Dispone de las autorizaciones administrativas equivalentes al Decano previstas en esta implementación.'),('Evaluador','Revisa requisitos y registra méritos o examen de las postulaciones para las que fue asignado.'),('Postulante','Consulta convocatorias, presenta varias solicitudes y revisa sus propias postulaciones.')]:p(actor+'. '+desc)
    p('El remitente público consulta ayuda y catálogos sin adquirir un rol académico. El servicio de correo es un actor de infraestructura; el operador configura y supervisa la ejecución. Esos participantes no amplían la lista de ocho casos aprobados.')
    h('3.3 Lista de casos de uso',2)
    for n,title in enumerate(TITLES,1):p(f'CU{n} {title}')
    h('3.4 Prioridad de casos de uso',2)
    p('La prioridad siguiente es una propuesta de construcción del grupo. Alta identifica funciones que habilitan el proceso; media identifica consultas que se apoyan en datos ya registrados. Todos los ocho casos forman parte de la entrega.')
    table(['CU','Prioridad','Justificación','Orden'],[[f'CU{n}','Alta' if n<7 else 'Media',s,str(n)] for n,s in enumerate(['Establece identidad, roles y acceso.','Define convocatoria, oferta, áreas y cupos.','Crea solicitudes y expedientes.','Decide la habilitación para evaluar.','Conserva asignaciones y calificaciones.','Resuelve ranking, empate y plazas.','Permite explicar las actuaciones registradas.','Resume el estado y los resultados del proceso.'],1)],[1.1,1.8,11,1.6])
    page();h('3.5 Detalle de los casos de uso',2)
    for n,c in enumerate(CASES,1):
        if n>1:page()
        h(f'CU{n} {TITLES[n-1]}',3);fig(f'caso_cu{n}',max_h=1.25)
        src=cases[n-1];v=src['vals'];flow='\n'.join(src['flow'])
        # Completar las fichas previas con las operaciones vigentes por categoría.
        if n==2:flow+='\n8. ASIOFE distribuye plazas por cargo; CFGARE configura pesos por área y LISCFG consulta la configuración.'
        if n==5:flow+='\n8. REGAREA registra notas de las áreas configuradas; los pesos deben sumar 100 % y REGEXA no puede eludir esa configuración.'
        if n==6:flow+='\n8. DESEMP conserva una prioridad y justificación ligadas a la versión de evaluación; un empate en el corte impide adjudicar sin resolución vigente.'
        commands=c['command']+(' / LISCON / LISCAR' if n==2 else '')
        rows=[['Nombre del caso de uso',f'CU{n} {TITLES[n-1]}'],['Propósito',v.get('Objetivo','')],['Descripción','El actor envía una orden al correo del grupo. El sistema valida datos y permisos, ejecuta o consulta la base y responde al remitente.'],['Actores',v.get('Actores','')],['Actor inicial',c['actor']],['Comandos',commands],['Precondición',v.get('Precondiciones','')],['Flujo principal',flow],['Postcondición',v.get('Postcondición','')],['Excepción',' '.join(src['exceptions'])+' '+c['exception']]]
        t=table(['Elemento','Especificación'],rows,[3.2,12.3],font=10,header=False)
        for row in t.rows:
            for run in row.cells[0].paragraphs[0].runs:run.bold=True
    page();h('3.6 Diagrama general de casos de uso',2);fig('casos_general',max_h=7.6)
    page();h('4 Flujo de trabajo Análisis');h('4.1 Análisis de arquitectura',2);h('4.1.1 Identificar paquete',3)
    p('Los paquetes siguientes agrupan responsabilidades funcionales de los ocho casos. Son paquetes del modelo de análisis; el código Java organiza las clases en config, email, comandos y persistencia, coordinadas por Main.')
    for i in range(1,5):
        if i==3:page()
        fig(f'paquete_{i}',max_h=2.5)
    page();h('4.1.2 Vista de cada paquete',3)
    for i in range(1,5):fig(f'vista_paquete_{i}',max_h=2.4)
    h('4.1.3 Dependencia de paquetes',3);fig('dependencias',max_h=3.8)
    page();h('4.2 Análisis de casos de uso',2)
    for n in range(1,9):
        if n>1 and n%2==1:page()
        h(f'CU{n} {CASES[n-1]["title"]}',3);fig(f'colaboracion_cu{n}',max_h=3.3)
    page();h('5 Flujo de trabajo Diseño');h('5.1 Diseño de arquitectura',2);h('5.1.1 Diseño lógico',3)
    fig('arquitectura_logica',max_h=5.5)
    p('Main coordina recepción y salida. AnalizadorOrden produce un valor Orden; ProcesadorOrden valida tipos, permisos y confirmación. RepositorioProyecto define las operaciones y RepositorioPostgres implementa persistencia, transacciones y resultados idempotentes. GestionCategorias concentra cupos, áreas, ranking y desempates. Los adaptadores implementan PasarelaCorreo.')
    page();h('5.1.2 Diseño físico',3);fig('despliegue',max_h=5.4)
    p('El JAR se ejecuta en el host del servicio con Java 17 y configuración privada. La presentación prevista utiliza Fedora del equipo del estudiante; PostgreSQL y el correo pertenecen a la infraestructura configurada de la cátedra. El servicio puede ser supervisado por systemd. El esquema representa nodos y conexiones previstos, no una comprobación de disponibilidad actual.')
    p('La conexión institucional se verificó el 14 de noviembre. Las comprobaciones posteriores registraron fallos DNS; el ensayo completo en Fedora debe repetirse con conectividad disponible. Los resultados históricos no se presentan como una conexión vigente.')
    page();h('5.2 Diagrama de secuencia',2)
    for n,c in enumerate(CASES,1):
        if n>1:page()
        h(f'CU{n} {c["title"]}',3);fig(f'secuencia_cu{n}',max_h=7.3)
        x=p('Escenario representativo '+c['example']+'. '+c['rule']);x.paragraph_format.line_spacing=1.0
        for r in x.runs:r.font.size=Pt(10)
    page();h('5.3 Diseño de detalle procedimental',2);h('Diagrama de clases general',3);fig('clases_general',max_h=7.1)
    p('La vista representa clases Java y sus interfaces existentes. Las operaciones específicas de cada caso se muestran en las vistas siguientes; las clases conceptuales de los datos se presentan en el capítulo 6.')
    page();h('5.3.1 Diseño de clases dinámico',3)
    for n in range(1,9):
        if n>1 and n%2==1:page()
        h(f'CU{n} {CASES[n-1]["title"]}',3);fig(f'clases_cu{n}',max_h=3.7)
    page();h('6 Diseño de datos');h('6.1 Diseño conceptual',2);h('6.1.1 Diagrama de clases',3)
    fig('datos_negocio',max_h=7.7)
    page();h('Auditoría y soporte del mismo modelo',3);fig('datos_soporte',max_h=6.5)
    p('Las doce clases del negocio se complementan con Auditoria de CU7, seis clases de soporte del correo y Persona del ejemplo INSPER y LISPER. Las asociaciones continuas representan FK reales. Las referencias de auditoría y la correlación por mensaje no se dibujan como FK cuando el SQL no las declara. Estas clases conceptuales no agregan casos ni se presentan como nuevas clases Java.')
    tables=schema();h('6.2 Diseño lógico',2);h('6.2.1 Mapeo',3)
    p('Cada clase persistente corresponde a una tabla. PK identifica la clave primaria, FK una referencia física y UQ una unicidad. Un mismo U1 o U2 en varias columnas identifica una restricción compuesta, no unicidades independientes. Las tablas largas se continúan horizontalmente en bloques de hasta seis columnas.')
    for name,t in tables.items():
        h(name,4)
        for offset in range(0,len(t['cols']),6):
            if offset:
                x=p(name+' continuación');x.paragraph_format.keep_with_next=True;x.paragraph_format.space_after=Pt(2);x.paragraph_format.space_before=Pt(0)
                for r in x.runs:r.font.size=Pt(9)
            cols=t['cols'][offset:offset+6];keys=[]
            for col in cols:
                c=col['nombre'];keys.append(' / '.join(k for k,test in [('PK',c in t['pk']),('FK',c in t['fk'])] if test)+''.join((' / ' if c in t['pk'] or c in t['fk'] else '')+(('UQ' if len(u)==1 else 'U'+str(i+1))) for i,u in enumerate(t['uq']) if c in u))
            def breakcol(s):return s.replace('_','_\n')
            table([breakcol(c['nombre']) for c in cols],[keys],[15.5/len(cols)]*len(cols),font=9)
    h('Restricciones compuestas y relaciones',3)
    x=p('Oferta tiene PK (convocatoria_id, cargo_id). AreaExamen agrega area a esa clave y referencia el par de Oferta. Postulacion evita duplicar (convocatoria_id, postulante_id, cargo_id). AsignacionEvaluador evita repetir (postulacion_id, evaluador_id, tipo). Desempate usa postulacion_id como PK y FK, de modo que admite como máximo un registro vigente por postulación. Usuario también dispone de un índice único sobre lower(correo).')
    x.paragraph_format.line_spacing=1.0
    for run in x.runs:run.font.size=Pt(10)
    page();h('6.3 Diseño físico',2);h('6.3.1 Tabla de volumen',3)
    p('El diccionario documenta columnas, tipo, longitud, nulabilidad y clave del esquema real, incluidas las migraciones. No se presenta una cantidad de filas o un tamaño de producción sin medición. BIGSERIAL representa la identidad de PostgreSQL; las longitudes se indican solo para tipos que las declaran. PK y FK identifican las claves; UQ es unicidad individual y U1 una unicidad compuesta. Los CHECK acotan notas, cupos, estados y fechas; los índices y las migraciones completos figuran en el capítulo 7.')
    for name,t in tables.items():
        h(name,4);rows=[]
        for i,col in enumerate(t['cols'],1):
            c=col['nombre'];typ=col['tipo'];m=re.match(r'(\w+)(?:\(([^)]+)\))?',typ)
            nullable=not(c in t['pk'] or 'not null' in col['definicion'].lower())
            labels=[k for k,test in [('PK',c in t['pk']),('FK',c in t['fk'])] if test]+['UQ' if len(u)==1 else 'U'+str(j+1) for j,u in enumerate(t['uq']) if c in u]
            key=' / '.join(labels)
            description='Área normalizada configurada; se excluye el valor general.' if name=='p1_area_examen' and c=='area' else DESCRIPTIONS[c]
            rows.append([i,c.replace('_','_\n'),m[1].upper(),m[2] or '—','Sí' if nullable else 'No',key or '—',description])
        table(['N°','Atributo','Tipo de dato','Longitud','Nulo','Clave','Descripción'],rows,[.7,2.8,2.5,1.6,1.0,1.3,5.6],font=9)
    page();h('7 Script')
    p('El siguiente SQL reproduce database/schema.sql. Se entrega además como archivo independiente legible. Su ejecución requiere la configuración privada autorizada y un respaldo del estado existente; no se incluyen credenciales en el documento.')
    for line_ in sql_display((ROOT/'database/schema.sql').read_text(encoding='utf8')):
        x=p('');x.paragraph_format.space_after=Pt(0);x.paragraph_format.line_spacing=1.0;x.alignment=WD_ALIGN_PARAGRAPH.LEFT
        pieces=re.split(r"('(?:''|[^'])*'|\b(?:create|table|if|not|exists|primary|key|references|default|unique|check|alter|add|column|constraint|index|drop|update|set|where|validate|on|null)\b)",line_,flags=re.I)
        for piece in pieces:
            if not piece:continue
            r=x.add_run(piece);r.font.name='Courier New';r.font.size=Pt(10)
            if piece.startswith("'"):r.font.color.rgb=RGBColor.from_string('A65C23')
            elif re.fullmatch(r'create|table|if|not|exists|primary|key|references|default|unique|check|alter|add|column|constraint|index|drop|update|set|where|validate|on|null',piece,re.I):r.font.color.rgb=RGBColor.from_string('2D40A0')
    page();h('8 Conclusión')
    p('El sistema reúne en un servicio por correo los ocho casos aprobados de auxiliaturas. La estructura documental relaciona actores, requisitos, colaboraciones, secuencias, clases y datos con el comportamiento implementado.')
    for i,s in enumerate(['Se identificaron los participantes y las operaciones de los ocho casos sin convertir cada comando en un caso adicional.','Se documentaron la separación de responsabilidades, la interacción por correo y las estructuras persistentes del proyecto.','Se implementaron postulaciones con expediente, habilitación, evaluaciones, cupos por cargo, ponderaciones, ranking, desempates y auditoría.','La integración continua del 17 de noviembre aprobó 122 pruebas sin fallos, errores ni omisiones. La consulta OSV de 23 dependencias no encontró coincidencias conocidas en esa ejecución; ese resultado no equivale a ausencia absoluta de vulnerabilidades.','Se prepararon el código, el JAR, los scripts Linux y la documentación para la defensa. La disponibilidad institucional y el ensayo nativo de Fedora requieren comprobación final; no se sustituyen con los resultados de una prueba local.'],1):p(f'OE{i} {s}')
    page();h('9 Recomendación')
    for i,s in enumerate(['Ensayar en Fedora el recorrido completo con las cuentas reales de Decano o Vicedecano, Evaluador y Postulante, comprobando respuestas, PDF y ranking antes de la defensa.','Confirmar la resolución DNS y disponibilidad de correo y PostgreSQL con el responsable de infraestructura. Si el servicio institucional no responde, conservar el diagnóstico y repetir la comprobación sin presentar el resultado histórico como actual.','Mantener las credenciales en archivos privados fuera del repositorio. Revisar permisos, copias de seguridad y recuperación de la base.','Verificar el cifrado de cada conexión según las posibilidades del servidor. HTTPS de correo y TLS de PostgreSQL protegen conexiones distintas; habilitar una no cambia automáticamente la otra.','Mantener la supervisión systemd, los límites de entrada, las bitácoras y las pruebas de regresión al introducir cambios. Las guías de instalación, operación y seguridad se conservan como documentos técnicos acompañantes.'],1):p(f'{i}. {s}')
    h('10 Bibliografías')
    for s in ['UAGRM. INF513 SA Entrega Proyecto No 1 Sistemas vía Mail. Actividad de Presencial consultada el 14 de noviembre de 2026. Casos del Grupo 09 aprobados el 5 de octubre de 2026.','Grupo 05 SA. Gestión de Reservas de Citas para la Barbería Barber Seven. Documento de defensa, gestión 1 2026, 69 páginas. Referencia de estructura y presentación documental; no es fuente de las reglas del dominio de auxiliaturas.','Grupo 09 SA. Código y documentación del proyecto. https://github.com/BrayanJoelRamosVargas1/inf513-proyecto1','IETF. RFC 5321 Simple Mail Transfer Protocol. https://www.rfc-editor.org/rfc/rfc5321','IETF. RFC 1939 Post Office Protocol Version 3. https://www.rfc-editor.org/rfc/rfc1939','IETF. RFC 2045 Multipurpose Internet Mail Extensions. https://www.rfc-editor.org/rfc/rfc2045','Oracle. Java Platform Standard Edition 17 y JDBC. https://docs.oracle.com/en/java/javase/17/','PostgreSQL Global Development Group. PostgreSQL Documentation. https://www.postgresql.org/docs/','Apache Software Foundation. Maven Documentation. https://maven.apache.org/guides/','OWASP Foundation. SQL Injection Prevention Cheat Sheet. https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html']:p(s,align=WD_ALIGN_PARAGRAPH.LEFT)
    d.save(ROOT/'entrega/PUDS_INF513_P1_Grupo09SA.docx')
    print('PUDS reconstruido con los diez capítulos de la referencia y ocho CU')

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('--base',required=True);build(ap.parse_args().base)
