#!/usr/bin/env python3
"""Run a loopback worker fixture and inject TCP faults at the Android gateway."""

import argparse
import http.client
import json
import re
import socket
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit


def run(examples: Path, candidate_review: bool) -> None:
    model = {
        "model_version": "mock-four-class-v1",
        "sha256": "a" * 64,
        "class_names": ["pet_bottle", "glass_bottle", "metal_can", "hdpe_bottle"],
    }
    detection = json.loads((examples / "detection-response.json").read_text())["data"]
    detection.pop("detection_id")
    detection.pop("expires_at")
    detection["model"] = model
    if candidate_review:
        detection["objects"].append({
            **detection["objects"][0],
            "object_id": "object-2",
            "bbox": [0.02, 0.03, 0.4, 0.5],
            "confidence": 0.8,
        })
    catalog = json.loads((examples / "catalog-response.json").read_text())["data"]
    headers_to_skip = {"connection", "content-length", "host", "keep-alive", "transfer-encoding"}
    request_pattern = re.compile(rb'name="client_request_id"[^\r\n]*\r\n(?:[^\r\n]+\r\n)*\r\n([0-9a-f-]{36})')
    detection_count = 0
    lock = threading.Lock()

    class QuietHandler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def reply(self, status: int, body: bytes, headers=()) -> None:
            self.send_response(status)
            for key, value in headers:
                if key.lower() not in headers_to_skip:
                    self.send_header(key, value)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *_args: object) -> None:
            pass

    class Worker(QuietHandler):
        def respond(self) -> None:
            self.rfile.read(int(self.headers.get("Content-Length", "0")))
            value = {
                "/internal/v1/objects": catalog,
                "/internal/v1/capabilities": {
                    "model": model,
                    "mapping_version": detection["mapping_version"],
                    "thresholds": detection["thresholds"],
                    "supported_visual_classes": 4,
                    "supported_object_labels": {"glass_bottle": "Botol kaca"},
                },
                "/internal/v1/detect": detection,
            }.get(self.path)
            if value is None:
                self.reply(404, b"")
                return
            payload = {"api_version": "1.0.0", "request_id": self.headers.get("X-Request-ID"), "data": value}
            self.reply(200, json.dumps(payload).encode(), [("Content-Type", "application/json")])

        do_GET = do_POST = respond

    class Proxy(QuietHandler):
        def forward(self) -> None:
            nonlocal detection_count
            body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
            headers = {k: v for k, v in self.headers.items() if k.lower() not in headers_to_skip}
            headers["Accept-Encoding"] = "identity"
            upstream = http.client.HTTPConnection("127.0.0.1", 18093, timeout=12)
            try:
                upstream.request(self.command, self.path, body or None, headers)
                response = upstream.getresponse()
                status, response_headers, data = response.status, response.getheaders(), response.read()
            finally:
                upstream.close()
            if self.command == "POST" and self.path == "/v1/ai/detections":
                with lock:
                    detection_count += 1
                    number = detection_count
                match = request_pattern.search(body)
                print(json.dumps({"upload": number, "status": status,
                                  "client_request_id": match[1].decode() if match else None}), flush=True)
                if not candidate_review and status == 201 and number == 1:
                    self.close_connection = True
                    self.connection.shutdown(socket.SHUT_RDWR)
                    print("Dropped the first committed detection response.", flush=True)
                    return
                if not candidate_review and status == 201 and number == 3:
                    time.sleep(8)
                    print("Released the held detection response.", flush=True)
            url = urlsplit(self.path)
            if self.command == "GET" and url.path == "/v1/waste-logs":
                query = parse_qs(url.query)
                if query.get("category") == ["PLASTIC"] and not query.get("method"):
                    print(f"Held older Plastik filter response ({status}).", flush=True)
                    time.sleep(7)
                    print("Released the older Plastik filter response.", flush=True)
                elif query.get("category") == ["PAPER"] and query.get("method") == ["RECYCLE"]:
                    print(f"Returned newer Kertas/Recycle filter response ({status}).", flush=True)
            try:
                self.reply(status, data, response_headers)
            except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                print("Client closed the socket before the response.", flush=True)

        do_GET = do_POST = do_DELETE = forward

    with ThreadingHTTPServer(("127.0.0.1", 18092), Worker) as worker:
        thread = threading.Thread(target=worker.serve_forever, daemon=True)
        thread.start()
        try:
            with ThreadingHTTPServer(("127.0.0.1", 18094), Proxy) as proxy:
                print("Loopback worker 18092 and gateway fault proxy 18094 ready.", flush=True)
                proxy.serve_forever()
        finally:
            worker.shutdown()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("examples", type=Path)
    parser.add_argument("--candidate-review", action="store_true")
    arguments = parser.parse_args()
    run(arguments.examples, arguments.candidate_review)
