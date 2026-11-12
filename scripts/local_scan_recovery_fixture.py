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


def run(examples: Path, mode: str | None) -> None:
    model = {
        "model_version": "mock-four-class-v1",
        "sha256": "a" * 64,
        "class_names": ["pet_bottle", "glass_bottle", "metal_can", "hdpe_bottle"],
    }
    detection = json.loads((examples / "detection-response.json").read_text())["data"]
    detection.pop("detection_id")
    detection.pop("expires_at")
    detection["model"] = model
    if mode == "candidate":
        detection["objects"].append({
            **detection["objects"][0],
            "object_id": "object-2",
            "bbox": [0.02, 0.03, 0.4, 0.5],
            "confidence": 0.8,
        })
    if mode == "confidence":
        model["class_names"].append("unknown")
        template = detection["objects"][0]
        detection["objects"] = [
            {
                **template,
                "object_id": f"object-{index}",
                "class_id": class_id,
                "raw_class": model["class_names"][class_id],
                "bbox": [0.05 * index, 0.05, 0.05 * index + 0.1, 0.2],
                "confidence": score,
                "confidence_band": band,
                "supported_for_review": band != "MANUAL_REQUIRED",
                "requires_material_confirmation": band != "MANUAL_REQUIRED",
                "suggested_object_keys": ["glass_bottle"] if band != "MANUAL_REQUIRED" else [],
            }
            for index, (score, band, class_id) in enumerate(
                [(0.49, "MANUAL_REQUIRED", 1), (0.50, "UNCERTAIN", 1), (0.699, "UNCERTAIN", 1),
                 (0.70, "OFFERED", 1), (0.95, "MANUAL_REQUIRED", 4)], start=1
            )
        ]
    catalog = json.loads((examples / "catalog-response.json").read_text())["data"]
    recommendation = json.loads((examples / "internal-recommendation-response.json").read_text())["data"]
    if mode == "policy":
        special = json.loads((examples / "provider-unavailable-response.json").read_text())["data"]
        for field in ("category", "confirmed_object_key", "context_revision", "detection_id", "expires_at", "object_id", "recommendation_id"):
            special.pop(field)
        special.update(special_handling=True, prototype_only=False)
        policy_cases = [
            ("malformed JSON", None),
            ("invalid method", {**recommendation, "recommended_method": "INVALID_METHOD"}),
            ("invalid evidence", {**recommendation, "evidence_ids": [{"id": "invalid"}]}),
            ("missing sources", {**recommendation, "source_urls": []}),
            ("special handling", special),
        ]
    if mode in ("recommendation", "double-save"):
        # Exercise the UI path reserved for actionable recommendations, on the loopback worker only.
        recommendation["prototype_only"] = False
    headers_to_skip = {"connection", "content-length", "host", "keep-alive", "transfer-encoding"}
    request_pattern = re.compile(rb'name="client_request_id"[^\r\n]*\r\n(?:[^\r\n]+\r\n)*\r\n([0-9a-f-]{36})')
    detection_count = 0
    recommendation_count = 0
    worker_recommendations = 0
    lock = threading.Lock()

    class QuietHandler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def reply(self, status: int, body: bytes, headers=()) -> None:
            self.send_response(status)
            for key, value in headers:
                if key.lower() not in headers_to_skip:
                    self.send_header(key, value)
            self.send_header("Content-Length", str(len(body)))
            if self.close_connection:
                self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *_args: object) -> None:
            pass

    class Worker(QuietHandler):
        def respond(self) -> None:
            nonlocal worker_recommendations
            body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
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
                "/internal/v1/recommendations": recommendation,
            }.get(self.path)
            if value is None:
                self.reply(404, b"")
                return
            if mode == "policy" and self.path == "/internal/v1/recommendations":
                with lock:
                    case, value = policy_cases[min(worker_recommendations, len(policy_cases) - 1)]
                    worker_recommendations += 1
                if case == "special handling" and not json.loads(body)["context"]["risk_flag"]:
                    self.reply(400, b"")
                    return
                print(f"Worker returned {case}.", flush=True)
                if value is None:
                    self.reply(200, b"{", [("Content-Type", "application/json")])
                    return
            payload = {"api_version": "1.0.0", "request_id": self.headers.get("X-Request-ID"), "data": value}
            self.reply(200, json.dumps(payload).encode(), [("Content-Type", "application/json")])

        do_GET = do_POST = respond

    class Proxy(QuietHandler):
        def forward(self) -> None:
            nonlocal detection_count, recommendation_count
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
                if mode is None and status == 201 and number == 1:
                    self.close_connection = True
                    self.connection.shutdown(socket.SHUT_RDWR)
                    print("Dropped the first committed detection response.", flush=True)
                    return
                if mode is None and status == 201 and number == 3:
                    time.sleep(8)
                    print("Released the held detection response.", flush=True)
            url = urlsplit(self.path)
            if mode == "policy" and self.command == "POST" and url.path == "/v1/ai/recommendations":
                payload = json.loads(data)
                print(json.dumps({"status": status, "code": payload.get("error", {}).get("code"),
                                  "method": payload.get("data", {}).get("recommended_method")}), flush=True)
            if mode == "recommendation" and self.command == "POST" and url.path == "/v1/ai/recommendations":
                with lock:
                    recommendation_count += 1
                    number = recommendation_count
                request = json.loads(body)
                print(json.dumps({"recommendation": number, "status": status,
                                  "revision": request["context_revision"],
                                  "condition": request["condition"]["cleanliness"]}), flush=True)
                if status == 201 and number == 1:
                    self.close_connection = True
                    self.connection.shutdown(socket.SHUT_RDWR)
                    print("Dropped the first committed recommendation response.", flush=True)
                    return
                if status == 201 and number == 2:
                    time.sleep(5)
                    print("Released the superseded recommendation response.", flush=True)
            if mode in ("recommendation", "double-save") and self.command == "POST" and url.path == "/v1/waste-logs":
                request = json.loads(body)
                print(json.dumps({"saved": status, "method": request["method"],
                                  "has_ai_provenance": request.get("ai_provenance") is not None}), flush=True)
                if mode == "double-save" and status == 201:
                    time.sleep(2)
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
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--candidate-review", dest="mode", action="store_const", const="candidate")
    modes.add_argument("--recommendation-recovery", dest="mode", action="store_const", const="recommendation")
    modes.add_argument("--confidence-review", dest="mode", action="store_const", const="confidence")
    modes.add_argument("--double-save", dest="mode", action="store_const", const="double-save")
    modes.add_argument("--policy-review", dest="mode", action="store_const", const="policy")
    arguments = parser.parse_args()
    run(arguments.examples, arguments.mode)
