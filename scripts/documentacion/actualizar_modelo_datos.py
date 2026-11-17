"""Sustituye las dos vistas de datos del PUDS, conservando sus demás capítulos.

Uso: python actualizar_modelo_datos.py --base copia-previa.docx
Las láminas A3 evitan comprimir el diagrama completo al ancho de una hoja carta.
"""
from pathlib import Path
from copy import deepcopy
import argparse, json
from docx import Document
from docx.shared import Inches, Pt
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from PIL import Image
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A2

ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'docs/diagramas/datos'

def update(base):
    d=Document(base)
    assert not any(p.text=='9 3 Mapeo de clases a tablas' for p in d.paragraphs), 'Use la copia anterior a esta revisión'
    ps=d.paragraphs
    start=next(i for i,p in enumerate(ps) if p.text=='9 Modelo de base de datos')
    support=next(i for i,p in enumerate(ps) if p.text=='9 2 Modelo de soporte del correo')
    dictionary=next(i for i,p in enumerate(ps) if p.text=='9 3 Diccionario de datos')
    portrait=deepcopy(d.sections[0]._sectPr)
    # Cierre de la sección carta anterior y comienzo de las láminas A3.
    def section_after(paragraph, large):
        sp=deepcopy(portrait)
        for typ in sp.findall(qn('w:type')):sp.remove(typ)
        typ=OxmlElement('w:type');typ.set(qn('w:val'),'nextPage');sp.insert(0,typ)
        if large:
            for el in sp.findall(qn('w:titlePg')):sp.remove(el)
            size=sp.find(qn('w:pgSz'));size.set(qn('w:w'),'16838');size.set(qn('w:h'),'23811')
            margins=sp.find(qn('w:pgMar'))
            for side in ('top','bottom','left','right'):margins.set(qn('w:'+side),'850')
        paragraph._p.get_or_add_pPr().append(sp)
    section_after(ps[start-1],False)
    ps[start+1].text='9 1 Diseño conceptual del negocio'
    ps[support].text='9 2 Auditoría y persistencia del correo'
    ps[dictionary].text='9 4 Diccionario de datos'
    for p in ps:
        if p.text=='9 4 Integridad transacciones y migración':p.text='9 5 Integridad transacciones y migración'
    for idx in (start,start+1,support):
        ps[idx].paragraph_format.page_break_before=(idx==support)
    for index,stem in ((start+2,'clases_datos'),(support+1,'clases_soporte')):
        p=ps[index];p.clear();p.alignment=WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_before=Pt(0);p.paragraph_format.space_after=Pt(2)
        p.paragraph_format.keep_with_next=True
        im=Image.open(ASSETS/(stem+'.png'))
        height=min(13.0,10.2*im.height/im.width)
        p.add_run().add_picture(str(ASSETS/(stem+'.png')),height=Inches(height))
    ps[start+3].text='Figura 42. Clases de datos del dominio con atributos tipos y multiplicidades'
    ps[start+4].text=('Las doce clases describen los datos persistentes de los ocho casos aprobados. Cada atributo conserva el nombre y tipo PostgreSQL del esquema, incluidas las migraciones. Las asociaciones continuas representan claves foráneas reales; 0..* permite que todavía no existan registros dependientes y 0..1 identifica el desempate opcional. Los nombres de clase son conceptuales; su correspondencia física figura en el mapeo siguiente. Lámina A3; versión independiente ampliada en docs/diagramas/datos.')
    ps[support+2].text='Figura 43. Clases de auditoría y soporte con sus atributos completos'
    ps[support+3].text=('Auditoria pertenece a CU7. Las seis clases de correo sostienen recepción, confirmación, reintentos y respuesta; Persona conserva el ejemplo independiente INSPER y LISPER. Se dibuja una asociación física únicamente entre ControlCorreo y AdjuntoCorreo: las demás tablas se correlacionan por valores o por la operación ejecutada, sin claves foráneas declaradas. Su independencia física queda visible y no equivale a nuevos casos de uso. Los diagramas no agregan tablas al sistema.')
    section_after(ps[support+3],True)
    for el in d._element.body.sectPr.findall(qn('w:titlePg')):d._element.body.sectPr.remove(el)
    for index in (start+4,support+3):
        ps[index].paragraph_format.line_spacing=1.08
        ps[index].paragraph_format.space_after=Pt(0)
        for r in ps[index].runs:r.font.size=Pt(10)
    # Nueva sección carta con el mapeo explícito de los nombres conceptuales.
    anchor=ps[dictionary]._p
    def insert_p(text,style=None):
        p=d.add_paragraph(text,style);anchor.addprevious(p._p);return p
    insert_p('9 3 Mapeo de clases a tablas','Heading 2')
    p=insert_p('El diseño lógico se materializa en las veinte tablas del diccionario. Los atributos de las láminas se corresponden uno a uno con sus columnas. PK identifica la clave primaria, FK una referencia física y UQ una restricción de unicidad. Los índices y las restricciones compuestas se detallan junto al SQL del anexo.')
    data=json.loads((ASSETS/'mapeo.json').read_text(encoding='utf-8'))
    t=d.add_table(rows=1, cols=3);anchor.addprevious(t._tbl);t.style='Table Grid'
    for c,txt in zip(t.rows[0].cells,['Clase conceptual','Tabla PostgreSQL','Ámbito']):c.text=txt
    business={x['tabla'] for x in data if x['tabla'] in ['p1_usuario','p1_convocatoria','p1_cargo','p1_oferta','p1_area_examen','p1_postulacion','p1_expediente','p1_evaluacion_requisito','p1_merito','p1_examen','p1_desempate','p1_asignacion_evaluador']}
    for x in data:
        scope='Negocio CU1 a CU8' if x['tabla'] in business else ('Auditoría CU7' if x['tabla']=='p1_auditoria' else 'Ejemplo INSPER LISPER' if x['tabla']=='persona' else 'Soporte del correo')
        for c,txt in zip(t.add_row().cells,[x['clase'],x['tabla'],scope]):c.text=txt
    t.rows[0]._tr.get_or_add_trPr().append(OxmlElement('w:tblHeader'))
    for ri,row in enumerate(t.rows):
        row._tr.get_or_add_trPr().append(OxmlElement('w:cantSplit'))
        for cell in row.cells:
            for p in cell.paragraphs:
                p.paragraph_format.space_after=Pt(2);p.paragraph_format.space_before=Pt(2)
                p.paragraph_format.line_spacing=1.0
                for r in p.runs:r.font.size=Pt(9);r.bold=ri==0
    insert_p('La clave de Oferta es (convocatoria_id, cargo_id). AreaExamen referencia ese par y agrega area a su clave. Desempate usa postulacion_id como PK y FK, de modo que una postulación admite como máximo un desempate vigente. La asignación enlaza al evaluador con su postulación; los correos de quienes califican se conservan como valores de auditoría, sin dibujar una FK inexistente a Usuario.')
    for i in (start+3,support+2):
        ps[i].style='Caption';ps[i].alignment=WD_ALIGN_PARAGRAPH.CENTER
    d.save(ROOT/'entrega/PUDS_INF513_P1_Grupo09SA.docx')
    # Láminas ampliadas A2 para lectura e impresión independiente.
    c=canvas.Canvas(str(ASSETS/'MODELO_DATOS_AMPLIADO.pdf'),pagesize=A2)
    c.setTitle('Grupo09SA Diseño de datos y correspondencia con PostgreSQL')
    w,h=A2
    for stem,title in [('clases_datos','Clases de datos del negocio'),('clases_soporte','Auditoría y soporte del correo')]:
        c.setFont('Times-Bold',20);c.drawString(38,h-40,title)
        im=Image.open(ASSETS/(stem+'.png'));scale=min((w-76)/im.width,(h-120)/im.height)
        iw,ih=im.width*scale,im.height*scale
        c.drawImage(str(ASSETS/(stem+'.png')),(w-iw)/2,h-64-ih,width=iw,height=ih)
        c.setFont('Times-Roman',11);c.drawString(38,28,'Grupo09SA | A2 | Asociaciones continuas = FK reales | Tipos PostgreSQL | Fuente: database/schema.sql')
        c.showPage()
    c.save()
    print('PUDS actualizado y láminas ampliadas generadas')

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('--base',required=True);update(ap.parse_args().base)
