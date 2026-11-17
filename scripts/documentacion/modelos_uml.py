"""Genera fuentes SVG de secuencia y DOT de comunicación y clases, CU1–CU8.

Las llamadas se contrastan con los identificadores del código; los cuadros
SQL describen operaciones, no clases Java ficticias. No utiliza credenciales.
"""
from pathlib import Path
import json
from html import escape
import textwrap

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/diagramas/uml'
OUT.mkdir(parents=True, exist_ok=True)
CASES = [
    dict(n=1, title='Gestión de usuarios', actor='Decano o Vicedecano',
         command='INSUSU / LISUSU', example='INSUSU', method='insertarUsuario',
         methods=['insertarUsuario', 'listarUsuarios'],
         sql='INSERT / SELECT p1_usuario',
         tables='p1_usuario; p1_auditoria',
         rule='Correo y registro únicos; roles y estado activo. El alta inicial exige el remitente de arranque configurado.',
         exception='Registro duplicado o remitente sin rol: error sin alta parcial.',
         test='ProcesadorOrdenTest; SeguridadPostgresTest; CicloCorreoPostgresTest'),
    dict(n=2, title='Parametrizar convocatorias', actor='Decano o Vicedecano',
         command='INSCON / INSCAR / ASIOFE / CFGARE / LISCFG', example='ASIOFE', method='oferta',
         methods=['insertarConvocatoria', 'insertarCargo'], categories=['oferta', 'area', 'sqlConfiguracion'],
         sql='Oferta y cupos por cargo', tables='p1_convocatoria; p1_cargo; p1_oferta; p1_area_examen',
         rule='Fechas y estados válidos. La suma de cupos por cargo no excede las plazas de la convocatoria.',
         exception='Cupos concurrentes que exceden el total: rechazar la operación.',
         test='SeguridadPostgresTest; CicloCorreoPostgresTest'),
    dict(n=3, title='Postulación y expediente', actor='Postulante',
         command='INSPOST / LISPOST', example='INSPOST', method='insertarPostulacion',
         methods=['insertarPostulacion', 'listarPostulaciones'],
         sql='Postulación y PDF atómicos', tables='p1_postulacion; p1_expediente; p1_adjunto_correo',
         rule='PDF original asociado por mensaje_id; convocatoria vigente, cargo ofertado y postulante autorizado.',
         exception='PDF ausente o rechazado, oferta sin plazas o duplicado: no crear una postulación parcial.',
         test='LectorAdjuntosTest; SeguridadPostgresTest; CicloCorreoPostgresTest'),
    dict(n=4, title='Requisitos y habilitación', actor='Decano o Vicedecano',
         command='EVAREQ / HABPOS', example='HABPOS', method='habilitar',
         methods=['evaluarRequisito', 'habilitar'],
         sql='Requisitos y estado vigente', tables='p1_evaluacion_requisito; p1_postulacion; p1_asignacion_evaluador',
         rule='EVAREQ registra revisión; HABPOS expresa la decisión de la autoridad. No son la misma operación.',
         exception='Evaluador no asignado o requisitos no cumplidos: impedir habilitación o examen.',
         test='SeguridadPostgresTest; CicloCorreoPostgresTest'),
    dict(n=5, title='Méritos y examen', actor='Evaluador',
         command='ASIEVA / REGMER / REGEXA / REGAREA', example='REGAREA', method='registrarArea',
         methods=['asignarEvaluador', 'registrarMerito', 'registrarExamen', 'recalcularPuntaje'],
         categories=['registrarArea', 'notaExamen'], sql='Nota por área y recálculo',
         tables='p1_asignacion_evaluador; p1_merito; p1_examen; p1_area_examen',
         rule='Nota entre 0 y 100, evaluador asignado y áreas con pesos completos. Final = 40 % méritos + 60 % examen.',
         exception='REGEXA no elude áreas configuradas; impedir cambios si ya hay plazas asignadas.',
         test='SeguridadPostgresTest; SeguridadPostgresTest; CicloCorreoPostgresTest'),
    dict(n=6, title='Ranking y plazas', actor='Decano o Vicedecano',
         command='GENRAN / DESEMP / ASIPLA', example='ASIPLA', method='asignarPlaza',
         methods=['generarRanking', 'asignarPlaza'], categories=['sqlRanking', 'resolver', 'asignar'],
         sql='Bloqueo y adjudicación por cargo', tables='p1_postulacion; p1_oferta; p1_desempate',
         rule='Orden por cargo, cupo y requisitos completos. Un empate en el corte requiere resolución motivada.',
         exception='El ID no decide ganadores; sin resolución vigente del empate no se adjudica.',
         test='SeguridadPostgresTest; CicloCorreoPostgresTest'),
    dict(n=7, title='Proceso y auditoría', actor='Decano o Vicedecano',
         command='AUDCAL y escrituras auditadas', example='AUDCAL', method='auditoriaProyecto',
         methods=['registrarAuditoria', 'estadoAuditable', 'auditoriaProyecto'],
         sql='SELECT historial por entidad e ID', tables='p1_auditoria; p1_postulacion',
         rule='Las escrituras exitosas registran actor y acción en su transacción; AUDCAL consulta el historial autorizado.',
         exception='Una consulta AUDCAL no se representa como cambio de calificación. Un fallo revierte negocio y auditoría.',
         test='SeguridadPostgresTest; CicloCorreoPostgresTest'),
    dict(n=8, title='Reportes y estadísticas', actor='Decano o Vicedecano',
         command='REPEST', example='REPEST', method='estadisticas',
         methods=['estadisticas', 'consultar'], categories=['sqlRanking'],
         sql='SELECT agregados y ranking', tables='p1_postulacion; p1_convocatoria; p1_oferta; p1_desempate',
         rule='Informe por convocatoria y cargo, estado, notas y vacantes. La consulta no modifica calificaciones.',
         exception='Sin postulaciones: respuesta sin resultados; no fabricar cantidades ni ganadores.',
         test='SeguridadPostgresTest; CicloCorreoPostgresTest'),
]


def label(s):
    return json.dumps(s, ensure_ascii=False)


def dot(body):
    return ('digraph G {graph[rankdir=LR,bgcolor="white",pad="0.2",nodesep=".35",ranksep=".65"];'
            'node[shape=box,fontname="Arial",fontsize=17,margin=".18,.12",style=filled,fillcolor="#f3f6fa"];'
            'edge[fontname="Arial",fontsize=14,color="#34465d",arrowsize=.7];' + body + '}')


def text(svg, x, y, s, size=21, anchor='middle', width=27):
    lines = textwrap.wrap(s, width=width, break_long_words=False) or ['']
    svg.append(f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-family="Arial" font-size="{size}" fill="#172331">')
    for i, line in enumerate(lines):
        svg.append(f'<tspan x="{x}" dy="{0 if i == 0 else size + 3}">{escape(line)}</tspan>')
    svg.append('</text>')


def sequence(c):
    # Cinco líneas de vida; el parser se representa por una llamada local de Main.
    xs = [90, 295, 500, 705, 910]
    s = ['<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="1210" viewBox="0 0 1000 1210">',
         '<rect width="1000" height="1210" fill="white"/>',
         '<defs><marker id="arr" markerWidth="9" markerHeight="9" refX="8" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8" fill="none" stroke="#34465d"/></marker></defs>']
    names = [c['actor'], 'Main / PasarelaCorreo', 'Procesador Orden', 'Repositorio Postgres', 'PostgreSQL']
    for x, name in zip(xs, names):
        s.append(f'<rect x="{x-84}" y="15" width="168" height="86" fill="#edf2f7" stroke="#34465d"/>')
        text(s, x, 42, name, size=21, width=15)
        s.append(f'<line x1="{x}" y1="101" x2="{x}" y2="1155" stroke="#8996a5" stroke-dasharray="7 6"/>')
    rows = [
        (0, 1, '1. Correo con ' + c['example'], False),
        (1, 3, '2. registrarEntrada()', False),
        (3, 4, '3. Cola; PDF solo en CU3', False),
        (1, 1, '4. analizar()', False),
        (1, 2, '5. procesar()', False),
        (2, 3, '6. Permiso y desafío', False),
        (1, 0, '7. Respuesta: CONFIRMAR', True),
        (0, 1, '8. Nuevo correo CONFIRMAR', False),
        (1, 2, '9. procesar() nueva orden', False),
        (2, 3, '10. confirmarOrden()', False),
        (3, 4, '11. ' + c['sql'], False),
        (4, 3, '12. Resultado aceptado', True),
        (3, 2, '13. Resultado durable', True),
        (2, 1, '14. Resultado', True),
        (1, 0, '15. Respuesta final', True),
    ]
    for i, (a, b, msg, ret) in enumerate(rows):
        y = (151 + i * 61) if i < 10 else (790 if i == 10 else 850 if i == 11 else 995 + (i-12)*66)
        x1, x2 = xs[a], xs[b]
        dashed = 'stroke-dasharray="6 4"' if ret else ''
        if a == b:
            s.append(f'<path d="M{x1},{y} h70 v22 h-70" stroke="#34465d" fill="none" marker-end="url(#arr)"/>')
            text(s, x1 + 78, y-12, msg, size=19, anchor='start', width=29)
        else:
            s.append(f'<line x1="{x1}" y1="{y}" x2="{x2}" y2="{y}" stroke="#34465d" {dashed} marker-end="url(#arr)"/>')
            text(s, (x1+x2)/2, y-29, msg, size=20, width=max(23,int(abs(x2-x1)/10)))
    # Fragmento UML alt sobre la ejecución: resultado aceptado o rechazo.
    s.append('<rect x="602" y="710" width="390" height="240" fill="none" stroke="#52667d"/>')
    s.append('<rect x="602" y="710" width="43" height="25" fill="#edf2f7" stroke="#52667d"/>')
    text(s, 624, 729, 'alt', size=17)
    s.append('<line x1="602" y1="865" x2="992" y2="865" stroke="#52667d" stroke-dasharray="6 4"/>')
    text(s, 791, 745, '[datos y permisos válidos]', size=17, width=40)
    text(s, 791, 897, '[rechazo: sin cambio parcial]', size=17, width=40)
    s.append('<line x1="910" y1="935" x2="705" y2="935" stroke="#34465d" stroke-dasharray="6 4" marker-end="url(#arr)"/>')
    text(s, 812, 922, 'Resultado de rechazo', size=17, width=28)
    text(s, 500, 1190, 'Orden confirmada: revalidar permisos y ejecutar una sola vez.', size=21, width=85)
    s.append('</svg>')
    return ''.join(s)


def generate():
    repo_source = (ROOT/'src/main/java/bo/edu/uagrm/inf513/grupo09sa/persistencia/RepositorioPostgres.java').read_text(encoding='utf-8')
    cat_source = (ROOT/'src/main/java/bo/edu/uagrm/inf513/grupo09sa/persistencia/GestionCategorias.java').read_text(encoding='utf-8')
    test_names = {p.stem for p in (ROOT/'src/test').rglob('*Test.java')}
    for c in CASES:
        c['test']='; '.join(dict.fromkeys(c['test'].split('; ')))
        for method in c['methods']: assert method+'(' in repo_source, method
        for method in c.get('categories', []): assert method+'(' in cat_source, method
        for test in c['test'].split('; '): assert test in test_names, test
        n = c['n']
        (OUT/f'secuencia_cu{n}.svg').write_text(sequence(c), encoding='utf-8')
        body = ('a[label='+label(c['actor'])+'];m[label="Main / PasarelaCorreo"];'
                'p[label="ProcesadorOrden"];r[label="RepositorioPostgres"];d[label="PostgreSQL",shape=cylinder];'
                'a->m[label="1: correo / 5: CONFIRMAR"];'
                'm->p[label="2: analizar y procesar"];'
                'p->r[label="3: permiso y confirmación\n6: ejecutar una vez"];'
                'r->d[label='+label('4: cola y desafío\n7: '+c['sql'])+'];'
                'd->r[style=dashed,label="8: resultado / rechazo"];'
                'r->m[style=dashed,label="9: resultado durable"];'
                'm->a[style=dashed,label="10: respuesta y cierre"];')
        for old,new in [('1: correo / 5: CONFIRMAR','1, 5'),('2: analizar y procesar','2'),('3: permiso y confirmación\n6: ejecutar una vez','3, 6'),('8: resultado / rechazo','8'),('9: resultado durable','9'),('10: respuesta y cierre','10')]:
            body=body.replace(old,new)
        body=body.replace(label('4: cola y desafío\n7: '+c['sql']),label('4, 7'))
        body=body.replace('Main / PasarelaCorreo','Main /\\nPasarelaCorreo').replace('RepositorioPostgres','Repositorio\\nPostgres').replace('ProcesadorOrden','Procesador\\nOrden')
        body += '{rank=same;a;m;p}{rank=same;r;d}'
        (OUT/f'comunicacion_cu{n}.dot').write_text(dot(body).replace('rankdir=LR','rankdir=TB'), encoding='utf-8')
        def klass(title, fields, operations):
            def cell(lines, bold=False):
                content='<BR ALIGN="LEFT"/>'.join(escape(line) for line in lines)
                return '<TR><TD ALIGN="LEFT" BALIGN="LEFT">'+('<B>'+content+'</B>' if bold else content)+'</TD></TR>'
            return '<<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="9" BGCOLOR="#f3f6fa">'+cell([title],True)+cell(fields)+cell(operations)+'</TABLE>>'
        body='p[label='+klass('ProcesadorOrden',['- catalogo: CatalogoComandos','- repo: RepositorioProyecto'],['+ procesar(...) : Resultado','- validar(Orden) : String'])+'];'
        body+='r[label='+klass('RepositorioPostgres',['- ds: DataSource'],['- '+m+'(...)' for m in c['methods']])+'];'
        body+='i[label='+klass('«interface» RepositorioProyecto',[],['+ ejecutarIdempotente(...)','+ confirmarOrden(...)'])+'];'
        body+='o[label='+klass('«record» Orden',['comando: String','parametros: List'],['+ comando()','+ parametros()'])+'];'
        body+='z[label='+klass('«record» Resultado',['exito: boolean','asunto / cuerpo: String'],['+ ok(...)','+ error(...)'])+'];'
        body+='p->i[label="depende de"];r->i[style=dashed,arrowhead=empty,label="implementa"];p->o[label="lee"];p->z[label="devuelve"];'
        if c.get('categories'):
            body+='g[label='+klass('GestionCategorias',['- conexiones: Conexion'],[m+'(...)' for m in c['categories']])+'];r->g[label="delega"];'
        (OUT/f'clases_cu{n}.dot').write_text(dot(body).replace('node[shape=box','node[shape=plain'),encoding='utf-8')
    (OUT/'casos.json').write_text(json.dumps(CASES,ensure_ascii=False,indent=2),encoding='utf-8')


if __name__ == '__main__':
    generate()
