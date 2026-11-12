#!/usr/bin/env python3
"""Drop the first committed response for a single local write-recovery fixture."""

import argparse
import http.client
import json
import socket
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from uuid import UUID


def run(fixture_id: UUID) -> None:
    fixture_notes = f"Catatan QA {fixture_id}"
    first_receipt = None
    receipt_lock = threading.Lock()
    hop_headers = {"connection", "content-length", "keep-alive", "transfer-encoding"}

    class Proxy(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def forward(self) -> None:
            nonlocal first_receipt
            body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
            headers = {
                key: value
                for key, value in self.headers.items()
                if key.lower() not in hop_headers | {"host"}
            }
            headers["Accept-Encoding"] = "identity"
            upstream = http.client.HTTPConnection("127.0.0.1", 18089, timeout=12)
            try:
                upstream.request(self.command, self.path, body or None, headers)
                response = upstream.getresponse()
                data = response.read()
                response_headers = response.getheaders()
                status = response.status
            finally:
                upstream.close()

            if self.command == "POST" and self.path == "/v1/waste-logs":
                submitted = json.loads(body)
                if submitted.get("notes") == fixture_notes and status in (200, 201):
                    receipt = json.loads(data)["data"]
                    with receipt_lock:
                        drop = status == 201 and first_receipt is None
                        if drop:
                            first_receipt = (submitted, receipt["id"])
                        print(json.dumps({
                            "upstream_status": status,
                            "response_dropped": drop,
                            "same_request_and_record": first_receipt == (submitted, receipt["id"]),
                            "request_id": submitted["client_request_id"],
                            "record_id": receipt["id"],
                            "xp": receipt["progress"]["xp"],
                        }), flush=True)
                    if drop:
                        self.close_connection = True
                        self.connection.shutdown(socket.SHUT_RDWR)
                        return

            self.send_response(status)
            for key, value in response_headers:
                if key.lower() not in hop_headers:
                    self.send_header(key, value)
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(data)

        do_GET = do_POST = forward

        def log_message(self, *_args: object) -> None:
            pass

    with ThreadingHTTPServer(("127.0.0.1", 18091), Proxy) as server:
        print("Local write-recovery proxy listening on 127.0.0.1:18091", flush=True)
        server.serve_forever()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fixture_id", type=UUID)
    run(parser.parse_args().fixture_id)
