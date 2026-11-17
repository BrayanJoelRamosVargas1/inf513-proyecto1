"""Actualiza una copia del PUDS existente con las vistas UML y el SQL legible.

Uso: python actualizar_puds.py --base ruta/al/PUDS.docx
No ejecuta SQL ni modifica el código Java. Requiere python-docx y Pillow.
"""
from pathlib import Path
import argparse
import re
import json
from copy import deepcopy
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from PIL import Image
from modelos_uml import CASES, ROOT, OUT


def split_columns(body):
    parts=[]; start=0; depth=0; quoted=False; i=0
    while i<len(body):
        c=body[i]
        if c=="'":
            if quoted and i+1<len(body) and body[i+1]=="'": i+=2; continue
            quoted=not quoted
        elif not quoted:
            if c=='(': depth+=1
            elif c==')': depth-=1
            elif c==',' and depth==0: parts.append(body[start:i].strip()); start=i+1
        i+=1
    return parts+[body[start:].strip()]


def update(base):
    d=Document(base)
    assert not any(p.text == '7 4 Realización UML de los ocho casos' for p in d.paragraphs), 'Use el PUDS anterior, sin la ampliación UML'
    # Construir piezas con los estilos del documento y moverlas antes del ancla.
    anchor=None
    def place(node):
        if anchor is not None: anchor.addprevious(node)
        return node
    def p(s='',style=None):
        x=d.add_paragraph(s,style);place(x._p)
        x.paragraph_format.space_after=Pt(6)
        if style is None:x.paragraph_format.line_spacing=1.12
        if style=='Caption':x.paragraph_format.line_spacing=1.0
        return x
    def h(s,level=2):return p(s,'Heading '+str(level))
    def page():
        x=p();x.paragraph_format.space_after=Pt(0);x.paragraph_format.space_before=Pt(0);x.paragraph_format.line_spacing=Pt(1)
        run=x.add_run();run.font.size=Pt(1);run.add_break(__import__('docx').enum.text.WD_BREAK.PAGE)
    def figure(name,caption,max_h=6.9):
        path=OUT/(name+'.png');im=Image.open(path)
        width=min(6.55,max_h*im.width/im.height)
        x=p();x.alignment=WD_ALIGN_PARAGRAPH.CENTER
        x.paragraph_format.keep_with_next=True
        x.add_run().add_picture(str(path),width=Inches(width))
        for el in x._p.xpath('.//wp:docPr'):el.set('descr',caption)
        c=p('Figura nueva. '+caption,'Caption');c.alignment=WD_ALIGN_PARAGRAPH.CENTER
    def table(headers,rows,widths=(2.15,4.4)):
        t=d.add_table(rows=1,cols=len(headers));place(t._tbl);t.autofit=False
        for cell,s in zip(t.rows[0].cells,headers):cell.text=s
        for vals in rows:
            for cell,s in zip(t.add_row().cells,vals):cell.text=s
        for row_n,row in enumerate(t.rows):
            for i,cell in enumerate(row.cells):
                cell.width=Inches(widths[i]);cell.vertical_alignment=WD_CELL_VERTICAL_ALIGNMENT.CENTER
                pr=cell._tc.get_or_add_tcPr()
                margins=OxmlElement('w:tcMar')
                for side in ('top','bottom','left','right'):
                    e=OxmlElement('w:'+side);e.set(qn('w:w'),'85');e.set(qn('w:type'),'dxa');margins.append(e)
                pr.append(margins)
                if row_n==0:
                    sh=OxmlElement('w:shd');sh.set(qn('w:fill'),'E8EEF5');pr.append(sh)
                for para in cell.paragraphs:
                    para.paragraph_format.space_after=Pt(3)
                    para.paragraph_format.keep_with_next=(row_n==0)
                    for r in para.runs:r.font.name='Times New Roman';r.font.size=Pt(10);r.bold=row_n==0
            row._tr.get_or_add_trPr().append(OxmlElement('w:cantSplit'))
        t.rows[0]._tr.get_or_add_trPr().append(OxmlElement('w:tblHeader'))
        borders=OxmlElement('w:tblBorders')
        for side in ('top','left','bottom','right','insideH','insideV'):
            e=OxmlElement('w:'+side);e.set(qn('w:val'),'single');e.set(qn('w:sz'),'4');e.set(qn('w:color'),'D9D9D9');borders.append(e)
        t._tbl.tblPr.append(borders)
        return t

    # Actualizar afirmaciones generales sin convertir ensayos históricos en actuales.
    replacements={
      'La verificación local ejecutó 111 pruebas': 'La verificación local del 15 de noviembre ejecutó 122 pruebas',
      'La ejecución finalizó con 111 pruebas': 'La ejecución registrada el 15 de noviembre finalizó con 122 pruebas',
      'Las 111 pruebas aprobadas sustentan': 'Las 122 pruebas registradas sustentan',
      'La suite completa aprobó 111 pruebas sin fallos, errores ni omisiones.': 'La revisión de categorías aprobó 111 pruebas; la suite posterior registrada alcanzó 122 sin fallos, errores ni omisiones.',
      'Historial técnico de actor, acción, entidad y referencia.': 'Auditoría transversal del negocio de CU7 con actor, acción, entidad y referencia.',
    }
    for para in d.paragraphs:
        for a,b in replacements.items():
            if a in para.text:para.text=para.text.replace(a,b)
        if para.text.startswith('Los requisitos se relacionan con los casos de uso'):
            para.text='Los requisitos se relacionan con los ocho casos de uso, comandos, tablas y pruebas. Las fichas conservan sus flujos; la sección 7 incorpora secuencia, comunicación y clases por CU. El modelo distingue negocio, auditoría e infraestructura y el diccionario cubre las veinte tablas del esquema. El SQL completo se entrega legible en el documento y como archivo independiente.'

    for t in d.tables:
        for row in t.rows:
            if row.cells[0].text=='Versión documental':row.cells[1].text='Revisión documental del 17 de noviembre de 2026'
    for para in d.paragraphs:
        if 'El servicio está activo en Windows' in para.text:para.text=para.text.replace('El servicio está activo en Windows','La comprobación institucional del 14 registró el servicio activo en Windows')
    # Sincronizar tabla de suites con evidencia existente, sin afirmar otra ejecución.
    ev=json.loads((ROOT/'evidencias/pruebas-locales.json').read_text(encoding='utf-8'))
    suites={x['nombre'].rsplit('.',1)[-1]:str(x['pruebas']) for x in ev['suites']}
    for t in d.tables:
        if t.rows[0].cells[0].text=='Suite':
            for row in t.rows[1:]:
                if row.cells[0].text in suites:row.cells[1].text=suites[row.cells[0].text]
            for row in t.rows:
                for cell in row.cells:
                    for para in cell.paragraphs:
                        para.paragraph_format.line_spacing=1.0
                        para.paragraph_format.space_after=Pt(3)
    anchor=next(x._p for x in d.paragraphs if x.text=='Cómo revisar este documento')
    p('Actualización documental del 17 de noviembre de 2026. Las últimas comprobaciones institucionales posteriores al despliegue del 14 fallaron por resolución DNS. No se afirma conexión actual ni ensayo nativo completo en Fedora. El ejecutable conserva su SHA 256 d707df9c1c36c6859456d4d8da2d00418efacf85001fd64eaa2e8252b9d194ca. Esta revisión amplía documentación y formato SQL, sin nuevas reglas de negocio.')

    # Vistas UML integradas en diseño, no como otro proyecto o nuevos CU.
    anchor=next(x._p for x in d.paragraphs if x.text=='8 Analizador y validación')
    page();h('7 4 Realización UML de los ocho casos',2)
    p('Las vistas siguientes desarrollan el diseño del código existente. El orden temporal se representa mediante líneas de vida, mensajes y alternativas. La colaboración numera los intercambios y la vista de clases identifica los componentes que realizan el caso. Las flechas SQL son interacciones con PostgreSQL; no son clases Java adicionales.')
    p('Notación: líneas continuas indican solicitudes; líneas discontinuas, retorno. El fragmento alt separa aceptación y rechazo del negocio. Main y PasarelaCorreo se agrupan como fachada de recepción y envío para conservar legibilidad; AnalizadorOrden participa mediante una llamada de análisis. La confirmación llega como otro correo y conserva el ID de la orden original.')
    p('La secuencia selecciona una operación representativa por CU. Las consultas públicas LISCON y LISCAR no solicitan confirmación. Las operaciones privadas, incluidas consultas de ranking, auditoría y reportes, sí la requieren. La cola se guarda antes de marcar el correo; el resultado se conserva antes del envío. Si este falla, registrarFallo programa un reintento sin repetir la operación de negocio.')
    table(['Componente real','Responsabilidad en las secuencias'],[
      ('Main y PasarelaCorreo','Recibir, persistir la entrada, analizar, ejecutar pendientes, guardar respuesta y enviarla en otro ciclo.'),
      ('AnalizadorOrden y ProcesadorOrden','Analizar el asunto, validar catálogo, tipos y permisos; solicitar confirmación y volver a comprobar el rol.'),
      ('RepositorioPostgres','Confirmar de forma transaccional, bloquear reintentos, ejecutar SQL parametrizado y conservar resultado y auditoría.'),
      ('GestionCategorias','Cupos, áreas, ponderaciones, ranking, desempates y asignación por cargo, con la conexión de la transacción.'),
      ('InspectorPdf y BitacoraArchivos','Validación antivirus previa a almacenar el adjunto; bitácoras locales sin claves ni códigos de confirmación.')])
    for c in CASES:
        page();h(f"7 4 {c['n']} CU{c['n']} {c['title']}",3)
        p('Operaciones: '+c['command']+'. La secuencia ilustra '+c['example']+'.')
        figure('secuencia_cu'+str(c['n']),f"Secuencia de CU{c['n']} con confirmación y resultado durable",7.0)
        # La regla se desarrolla en la página de colaboración, sin repetirse aquí.
        page();h(f"CU{c['n']} Colaboración y clases participantes",3)
        figure('comunicacion_cu'+str(c['n']),f"Colaboración de CU{c['n']} y orden de mensajes",4.2)
        p('Mensajes: 1 correo inicial; 2 análisis y procesamiento; 3 permisos y desafío; 4 persistencia de cola y confirmación; 5 correo CONFIRMAR; 6 ejecución idempotente; 7 operación SQL; 8 resultado o rechazo; 9 resultado durable al coordinador; 10 respuesta al remitente.')
        p('Regla del negocio: '+c['rule'])
        p('Alternativa comprobable: '+c['exception'])
        page();h(f"CU{c['n']} Diseño de clases y persistencia",3)
        figure('clases_cu'+str(c['n']),f"Clases reales que realizan CU{c['n']}",5.0)
        p('Persistencia: '+c['tables']+'.')
        p('Alternativa comprobable: '+c['exception'])
        p('Métodos internos del repositorio: '+', '.join(c['methods'])+'.'+(' Delegación a GestionCategorias: '+', '.join(c['categories'])+'.' if c.get('categories') else ''))
        p('Evidencia automatizada: '+c['test']+'. Las suites se ejecutan en base aislada; no acreditan por sí solas la conectividad institucional actual.')
    page()

    # Reemplazar el diccionario incompleto por uno derivado de todo el esquema.
    start=next(x._p for x in d.paragraphs if x.text=='9 3 Diccionario de datos')
    end=next(x._p for x in d.paragraphs if x.text=='9 4 Integridad transacciones y migración')
    node=start.getnext()
    while node is not end:
        nxt=node.getnext();node.getparent().remove(node);node=nxt
    anchor=end
    sql=(ROOT/'database/schema.sql').read_text(encoding='utf-8')
    tables=re.findall(r'create table if not exists (\w+)\s*\((.*?)\);',sql,re.S|re.I)
    business={'p1_usuario','p1_convocatoria','p1_cargo','p1_oferta','p1_area_examen','p1_postulacion','p1_expediente','p1_evaluacion_requisito','p1_merito','p1_examen','p1_desempate','p1_asignacion_evaluador'}
    p('El diccionario siguiente representa veinte tablas: doce del dominio, una auditoría transversal de CU7, seis de soporte del correo y persona, ejemplo independiente de la consigna. Los campos añadidos por migración se incluyen en su tabla; las restricciones generales se resumen al final. No se ocultan las tablas de infraestructura ni se confunden con nuevos casos de uso.')
    for name,body in tables:
        h(name.replace('_',' '),3)
        purpose=('Negocio dentro de CU1 a CU8.' if name in business else 'Auditoría transversal del negocio y de calificaciones, CU7.' if name=='p1_auditoria' else 'Ejemplo literal de INSPER y LISPER, independiente de los roles de auxiliaturas.' if name=='persona' else 'Infraestructura del correo y de confirmaciones; no constituye un CU adicional.')
        p(purpose).paragraph_format.keep_with_next=True
        definitions=split_columns(body)
        for m in re.finditer(r'alter table '+name+r' add column if not exists ([^;]+);',sql,re.I):definitions.append(m.group(1))
        rows=[]
        for definition in definitions:
            definition=' '.join(definition.split())
            parts=definition.split(' ',1)
            if parts[0].lower() in ('primary','foreign','unique','check'):rows.append(('Restricción',definition))
            else:rows.append((parts[0],parts[1] if len(parts)>1 else ''))
        table(['Campo o restricción','Tipo y regla del esquema'],rows)
    h('Restricciones y cardinalidades del modelo',3)
    p('Cada postulación pertenece a una convocatoria, un postulante y un cargo. Cada uno puede tener cero o muchas postulaciones. Un expediente, mérito, examen, revisión o asignación pertenece a una postulación; esta puede reunir cero o muchos de ellos. El desempate es cero o uno por postulación. La pareja convocatoria y cargo identifica la oferta y es referenciada por las áreas de examen. Se conserva UNIQUE de convocatoria, postulante y cargo para evitar duplicados.')
    p('Los CHECK validados exigen fechas ordenadas, notas entre 0 y 100, estados permitidos y plaza asignada únicamente en estado GANADOR. El índice único sobre lower(correo) impide duplicar identidad por mayúsculas. Las claves foráneas del SQL son las únicas relaciones físicas: las líneas discontinuas del soporte indican correlación, no FK.')
    p('p1_auditoria identifica su objeto mediante entidad y entidad_id. Es una referencia transversal; no se agrega una FK ficticia hacia todas las tablas. La escritura auditada y su resultado comparten la transacción. La bitácora local access.log o error.log tiene una función operativa distinta y no reemplaza CU7.')

    # SQL completo como parte de anexos de entrega, conservando el archivo ejecutable.
    anchor=next(x._p for x in d.paragraphs if x.text=='Ampliación de los ocho casos aprobados')
    page();h('17 1 Script SQL completo',2)
    p('Contenido de database/schema.sql. Se presenta con una columna por línea y conserva las migraciones no destructivas. El orden importa: primero se crean las tablas y después se agregan columnas, índices y restricciones. Para una instalación nueva usar una base propia; para una base existente, realizar respaldo y prueba de restauración antes de aplicar cambios. No ejecutar este anexo contra la base compartida durante la defensa.')
    for statement in re.split(r'(?<=;)\s*',sql.strip()):
        if not statement.strip():continue
        x=p(statement.strip());x.alignment=WD_ALIGN_PARAGRAPH.LEFT
        x.paragraph_format.line_spacing=1.03
        x.paragraph_format.space_after=Pt(9)
        x.paragraph_format.keep_together=len(statement.splitlines())<=17
        for run in x.runs:run.font.name='Consolas';run.font.size=Pt(9)
    page();h('17 2 Trazabilidad de la actualización documental',2)
    p('La referencia de organización es el proyecto de barbería del Grupo 05SA, gestión 2026 1, aportado por el equipo. Se adoptó la separación de análisis, secuencia, clases y datos; no se trasladaron sus actores, operaciones ni clases al dominio de auxiliaturas. Los diagramas nuevos describen el código real y se entregan con fuentes SVG o DOT editables en docs/diagramas/uml. No se atribuye una calificación esperada ni una aprobación docente a esta revisión.')
    table(['Control','Resultado y límite'],[
      ('Alcance','Ocho CU aprobados; 24 vistas UML nuevas: 8 secuencias, 8 colaboraciones y 8 vistas de clases.'),
      ('Base de datos','Diccionario de veinte tablas, incluidas las tres de categorías; las definiciones SQL solo cambian de formato.'),
      ('Ejecutable','Java y JAR sin cambios de comportamiento en esta actualización documental.'),
      ('Evidencia de pruebas','122 pruebas registradas el 15 de noviembre. Los resultados de red del 15 son posteriores al despliegue exitoso del 14.'),
      ('Presentación','Se requiere ensayo real en Fedora con los cuatro roles, confirmaciones, PDF y reinicio.'),
      ('Disponibilidad y TLS','No se acredita conexión institucional actual; el último fallo de DNS y la falta de TLS de PostgreSQL permanecen documentados.')])
    page()

    # Enumeración de figuras en orden del documento.
    number=0
    for para in d.paragraphs:
        if para.style.name=='Caption' and re.match(r'Figura (?:\d+|nueva)\.',para.text):
            number+=1;para.text=re.sub(r'^Figura (?:\d+|nueva)\.',f'Figura {number}.',para.text)
            para.alignment=WD_ALIGN_PARAGRAPH.CENTER
    # Anchos de tabla, repetición de cabeceras y párrafos sin saltos artificiales.
    for t in d.tables:
        for row in t.rows:
            for cell in row.cells:
                for para in cell.paragraphs:para.paragraph_format.page_break_before=False
    target=ROOT/'entrega/PUDS_INF513_P1_Grupo09SA.docx';d.save(target)
    (ROOT/'docs/PUDS_ESTRUCTURA.md').write_text('\n'.join(x.text for x in d.paragraphs if x.style.name.startswith('Heading')),encoding='utf-8')
    print(json.dumps({'figuras':number,'tablas_diccionario':len(tables),'vistas_uml':24,'salida':str(target)},ensure_ascii=False))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--base',required=True)
    update(parser.parse_args().base)
