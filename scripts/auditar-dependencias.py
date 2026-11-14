"""Consulta OSV con las coordenadas Maven; no transmite código ni configuración privada.

Antes: mvn dependency:tree -DoutputType=json -DoutputFile=target/dependencias-auditoria.json
"""
import datetime
import json
from pathlib import Path
import urllib.request

root = Path(__file__).resolve().parents[1]
tree = json.loads((root / 'target/dependencias-auditoria.json').read_text(encoding='utf-8'))
packages = {}
def walk(node):
    for child in node.get('children', []):
        key = child['groupId'] + ':' + child['artifactId']
        packages[(key, child['version'])] = child.get('scope', '')
        walk(child)
walk(tree)
queries = [{'package': {'ecosystem': 'Maven', 'name': name}, 'version': version}
           for name, version in packages]
request = urllib.request.Request('https://api.osv.dev/v1/querybatch',
    data=json.dumps({'queries': queries}).encode(), headers={'Content-Type': 'application/json'})
with urllib.request.urlopen(request, timeout=60) as response:
    results = json.load(response)['results']
if len(results) != len(queries) or any(item.get('next_page_token') for item in results):
    raise RuntimeError('Respuesta OSV incompleta: no se puede declarar la revisión finalizada')
report = {'fecha_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'fuente': 'https://api.osv.dev/v1/querybatch',
          'alcance': 'Dependencias Maven resueltas de aplicación y pruebas; excluye plugins de compilación',
          'paquetes': [dict(query, scope=scope, vulnerabilidades=result.get('vulns', []))
                       for query, scope, result in zip(queries, packages.values(), results)]}
destination = root / 'evidencias/auditoria-osv.json'
destination.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
count = sum(len(item.get('vulns', [])) for item in results)
print(f'OSV: {len(queries)} dependencias consultadas; {count} coincidencias de vulnerabilidad')
raise SystemExit(1 if count else 0)
