from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from urllib.parse import urlsplit
import os,json
from readiness import live
os.chdir(os.path.dirname(os.path.abspath(__file__)))
class Handler(SimpleHTTPRequestHandler):
 def log_message(self,*args): pass
 def do_GET(self):
  # Localhost-only, exact routes, no arbitrary proxy or directory/private-file access.
  if self.headers.get('Host') not in ('127.0.0.1:57820','localhost:57820'):
   self.send_error(403);return
  path=urlsplit(self.path).path
  if path=='/api/fixtures':
   body=json.dumps(live()).encode();self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.end_headers();self.wfile.write(body)
  elif path in ('/','/index.html','/source-adapter.js','/iteration4.js','/SOURCE-READINESS.md','/inventory.json'):
   super().do_GET()
  else: self.send_error(404)
server=ThreadingHTTPServer(('127.0.0.1',57820),Handler)
print('Min TV design preview: http://127.0.0.1:57820/',flush=True)
server.serve_forever()
