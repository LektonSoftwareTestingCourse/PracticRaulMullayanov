"""HTTP fault injection for the isolated API test stack; ordinary requests pass through unchanged."""
import http.client
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

RULES = {}
LOCK = threading.Lock()
TARGETS = {"card-management": 8080, "switch": 8081, "transaction-logger": 8082,
           "authorization": 8083, "terminal-simulator": 8084, "merchant-acquirer": 8085}
SERVERS = {}
SERVER_LOCK = threading.Lock()


class Proxy(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def reply(self, status, body=None):
        raw = b"" if body is None else json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def dispatch(self):
        if "chunked" in self.headers.get("Transfer-Encoding", "").lower():
            chunks = []
            while True:
                length = int(self.rfile.readline().split(b";", 1)[0].strip(), 16)
                if length == 0:
                    while self.rfile.readline() not in (b"\r\n", b"\n", b""):
                        pass
                    break
                chunks.append(self.rfile.read(length))
                self.rfile.read(2)
            raw = b"".join(chunks)
        else:
            raw = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        if self.path == "/__health":
            self.reply(200, {"status": "ok"})
            return
        if self.path.startswith("/__availability/") and self.command == "POST":
            name = self.path.rsplit("/", 1)[1]
            if name not in TARGETS:
                self.reply(404, {"error": "unknown service"})
                return
            enabled = json.loads(raw)["enabled"]
            with SERVER_LOCK:
                if not enabled and name in SERVERS:
                    server = SERVERS.pop(name)
                    server.shutdown()
                    server.server_close()
                elif enabled and name not in SERVERS:
                    start_listener(name)
            self.reply(200, {"service": name, "enabled": enabled})
            return
        if self.path.startswith("/__faults/"):
            identifier = self.path.rsplit("/", 1)[1]
            with LOCK:
                if self.command == "POST":
                    RULES[identifier] = json.loads(raw)
                    self.reply(201, {"id": identifier})
                elif self.command == "DELETE":
                    RULES.pop(identifier, None)
                    self.reply(204)
                else:
                    self.reply(405, {"error": "unsupported control method"})
            return
        with LOCK:
            for identifier, rule in list(RULES.items()):
                if rule["method"] == self.command and rule["path"] == self.path:
                    rule["times"] = rule.get("times", 1) - 1
                    if rule["times"] <= 0:
                        del RULES[identifier]
                    self.reply(rule["status"], rule.get("body"))
                    return
        connection = http.client.HTTPConnection(self.server.service_name, 8080, timeout=25)
        try:
            headers = {key: value for key, value in self.headers.items()
                       if key.lower() not in {"host", "connection", "transfer-encoding"}}
            connection.request(self.command, self.path, raw or None, headers)
            response = connection.getresponse()
            body = response.read()
            self.send_response(response.status)
            for key, value in response.getheaders():
                if key.lower() not in {"content-length", "connection", "transfer-encoding"}:
                    self.send_header(key, value)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            # The caller cancelled an in-flight health check during a deliberate outage.
            pass
        except (OSError, http.client.HTTPException) as error:
            self.reply(503, {"error": "SERVICE_UNAVAILABLE", "serviceName": "card-proxy", "message": str(error)})
        finally:
            connection.close()

    do_GET = dispatch
    do_POST = dispatch
    do_PATCH = dispatch
    do_DELETE = dispatch
    do_PUT = dispatch


def start_listener(name):
    server = ThreadingHTTPServer(("0.0.0.0", TARGETS[name]), Proxy)
    server.daemon_threads = True
    server.service_name = name
    SERVERS[name] = server
    threading.Thread(target=lambda: server.serve_forever(poll_interval=0.05), daemon=True).start()


if __name__ == "__main__":
    for service_name in TARGETS:
        start_listener(service_name)
    ThreadingHTTPServer(("0.0.0.0", 8090), Proxy).serve_forever()
