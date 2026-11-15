"""Diagnóstico de DNS sin credenciales ni cambios de configuración (Python 3)."""
import concurrent.futures
import datetime
import json
import socket
import urllib.request

HOSTS = ("db.tecnoweb.org.bo", "mail.tecnoweb.org.bo", "github.com")


def local(host):
    try:
        return {"host": host, "direcciones": sorted({r[4][0] for r in
                socket.getaddrinfo(host, None, type=socket.SOCK_STREAM)})}
    except OSError as exc:
        return {"host": host, "error": str(exc)}


def doh(provider, endpoint, host, record):
    result = {"proveedor": provider, "host": host, "tipo": record}
    try:
        request = urllib.request.Request(
            endpoint + "?name=" + host + "&type=" + record,
            headers={"Accept": "application/dns-json"})
        with urllib.request.urlopen(request, timeout=12) as response:
            data = json.load(response)
        result.update({key: data[key] for key in
                       ("Status", "Answer", "Authority", "Comment") if key in data})
    except (OSError, ValueError) as exc:
        result["error"] = str(exc)
    return result


def main():
    # Las consultas institucionales no transmiten contraseñas ni usan IP supuestas.
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        jobs = [pool.submit(local, host) for host in HOSTS]
        for provider, endpoint in (
                ("Google", "https://dns.google/resolve"),
                ("Cloudflare", "https://cloudflare-dns.com/dns-query")):
            for host, record in ((HOSTS[0], "A"), (HOSTS[1], "A"),
                                 ("tecnoweb.org.bo", "NS"), (HOSTS[2], "A")):
                jobs.append(pool.submit(doh, provider, endpoint, host, record))
        results = [job.result() for job in jobs]
    print(json.dumps({"fecha_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                      "consultas": results}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
