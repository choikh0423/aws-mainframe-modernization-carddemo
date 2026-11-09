"""HTTP front end of the demo region (default port 8090).

  /                      side-by-side comparison page
  /app/                  the migrated React build (proxied API calls go to Java)
  /api/legacy/...        the CICS region: terminal I/O, data, reset
  /api/parity/run        replay all scenarios on both sides
  /api/bill-payment, /api/demo/...  proxied to the Spring Boot service
"""
import argparse
import json
import mimetypes
import os
import sys
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from . import parity
from .cics import Region

HERE = os.path.dirname(os.path.abspath(__file__))
RUNTIME = os.path.dirname(HERE)
REPO = os.path.abspath(os.path.join(RUNTIME, '..', '..', '..'))


def make_handler(region, java_url, frontend_build):
    web = os.path.join(RUNTIME, 'web')

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, fmt, *args):
            sys.stderr.write('[region] ' + fmt % args + '\n')

        def _json(self, obj, code=200):
            body = json.dumps(obj).encode()
            self.send_response(code)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def _file(self, path):
            if not os.path.isfile(path):
                self.send_error(404)
                return
            with open(path, 'rb') as f:
                body = f.read()
            self.send_response(200)
            self.send_header('Content-Type', mimetypes.guess_type(path)[0] or 'application/octet-stream')
            self.send_header('Content-Length', str(len(body)))
            self.send_header('Cache-Control', 'no-store')
            self.end_headers()
            self.wfile.write(body)

        def _body(self):
            n = int(self.headers.get('Content-Length') or 0)
            return json.loads(self.rfile.read(n) or b'{}') if n else {}

        def _proxy(self, method):
            data = None
            if method == 'POST':
                data = self.rfile.read(int(self.headers.get('Content-Length') or 0))
            req = urllib.request.Request(java_url + self.path, data=data, method=method,
                                         headers={'Content-Type': 'application/json'})
            try:
                with urllib.request.urlopen(req, timeout=15) as r:
                    body, code = r.read(), r.status
            except urllib.error.HTTPError as e:
                body, code = e.read(), e.code
            except urllib.error.URLError as e:
                return self._json({'error': 'Java service unreachable at %s: %s' % (java_url, e.reason)}, 502)
            self.send_response(code)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            path = self.path.split('?')[0]
            if path in ('/', '/index.html'):
                return self._file(os.path.join(web, 'compare.html'))
            if path.startswith('/web/'):
                return self._file(os.path.join(web, os.path.basename(path)))
            if path == '/app' or path.startswith('/app/'):
                rel = path[len('/app/'):] or 'index.html'
                target = os.path.normpath(os.path.join(frontend_build, rel))
                if not target.startswith(frontend_build) or not os.path.isfile(target):
                    target = os.path.join(frontend_build, 'index.html')
                return self._file(target)
            if path.startswith('/api/bill-payment') or path.startswith('/api/demo/'):
                return self._proxy('GET')
            if path == '/api/legacy/screen':
                termid = (self.path.split('termid=')[1] if 'termid=' in self.path else 'T001')[:4]
                return self._json(region.state(region.terminal(termid)))
            if path.startswith('/api/legacy/accounts/'):
                a = parity.legacy_account(region, path.rsplit('/', 1)[1])
                return self._json(a) if a else self._json({'error': 'NOTFND'}, 404)
            if path == '/api/legacy/transactions/latest':
                n = int(self.path.split('limit=')[1]) if 'limit=' in self.path else 5
                return self._json(parity.legacy_latest(region, n))
            self.send_error(404)

        def do_POST(self):
            path = self.path.split('?')[0]
            if path.startswith('/api/bill-payment') or path.startswith('/api/demo/'):
                return self._proxy('POST')
            body = self._body()
            termid = (body.get('termid') or 'T001')[:4]
            try:
                if path == '/api/legacy/start':
                    return self._json(region.start_from_menu(termid))
                if path == '/api/legacy/input':
                    return self._json(region.enter(termid, body.get('aid', 'ENTER'),
                                                   body.get('fields'), body.get('cursor')))
                if path == '/api/legacy/reset':
                    region.reseed()
                    return self._json({'ok': True})
                if path == '/api/parity/run':
                    with region.lock:
                        return self._json(parity.run(region, java_url))
            except ValueError as e:
                return self._json({'error': str(e)}, 400)
            self.send_error(404)

    return Handler


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--port', type=int, default=int(os.environ.get('REGION_PORT', 8090)))
    ap.add_argument('--java', default=os.environ.get('JAVA_URL', 'http://localhost:8080'))
    ap.add_argument('--db', default=os.path.join(RUNTIME, 'build', 'vsam.db'))
    ap.add_argument('--frontend', default=os.path.join(RUNTIME, '..', 'frontend', 'build'))
    args = ap.parse_args()
    region = Region(os.path.join(RUNTIME, 'build'), os.path.join(REPO, 'app', 'bms'),
                    os.path.join(REPO, 'app', 'data', 'ASCII'), args.db)
    region.reseed()
    handler = make_handler(region, args.java.rstrip('/'), os.path.abspath(args.frontend))
    print('CICS demo region on http://localhost:%d (Java at %s)' % (args.port, args.java), flush=True)
    ThreadingHTTPServer(('0.0.0.0', args.port), handler).serve_forever()


if __name__ == '__main__':
    main()
