from __future__ import annotations

import json
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import cast

from .contracts import EstimateRequest
from .estimator import estimate_price

MAX_REQUEST_BYTES = 64 * 1024


class PriceRequestHandler(BaseHTTPRequestHandler):
    server_version = "HengjiPrice/0.1"

    def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        if self.path == "/health":
            self._write_json(HTTPStatus.OK, {"status": "ok"})
            return
        self._write_error(HTTPStatus.NOT_FOUND, "NOT_FOUND", "Route not found")

    def do_POST(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        if self.path != "/v1/estimates":
            self._write_error(HTTPStatus.NOT_FOUND, "NOT_FOUND", "Route not found")
            return
        try:
            body = self._read_json()
            request = EstimateRequest.from_dict(body)
            self._write_json(HTTPStatus.OK, estimate_price(request).to_dict())
        except RequestError as error:
            self._write_error(error.status, error.code, str(error))
        except ValueError as error:
            self._write_error(HTTPStatus.BAD_REQUEST, "INVALID_REQUEST", str(error))
        except Exception:
            self._write_error(HTTPStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Request could not be completed")

    def log_message(self, format: str, *args: object) -> None:
        # Do not write raw URLs, quote payloads, or financial values to logs.
        return

    def _read_json(self) -> dict[str, object]:
        if self.headers.get_content_type() != "application/json":
            raise RequestError(HTTPStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json")
        raw_length = self.headers.get("Content-Length")
        if raw_length is None:
            raise RequestError(HTTPStatus.LENGTH_REQUIRED, "LENGTH_REQUIRED", "Content-Length is required")
        try:
            length = int(raw_length)
        except ValueError as error:
            raise RequestError(HTTPStatus.BAD_REQUEST, "INVALID_CONTENT_LENGTH", "Content-Length is invalid") from error
        if length < 0 or length > MAX_REQUEST_BYTES:
            raise RequestError(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, "BODY_TOO_LARGE", "Request exceeds 65536 bytes")
        try:
            value: object = json.loads(self.rfile.read(length).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise RequestError(HTTPStatus.BAD_REQUEST, "INVALID_JSON", "Request body is not valid UTF-8 JSON") from error
        if not isinstance(value, dict):
            raise RequestError(HTTPStatus.BAD_REQUEST, "INVALID_JSON_OBJECT", "Request body must be a JSON object")
        untyped_value = cast(dict[object, object], value)
        if not all(isinstance(key, str) for key in untyped_value):
            raise RequestError(HTTPStatus.BAD_REQUEST, "INVALID_JSON_OBJECT", "Request object keys must be strings")
        return cast(dict[str, object], untyped_value)

    def _write_error(self, status: HTTPStatus, code: str, message: str) -> None:
        self._write_json(status, {"error": {"code": code, "message": message}})

    def _write_json(self, status: HTTPStatus, value: object) -> None:
        payload = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(payload)


class RequestError(Exception):
    def __init__(self, status: HTTPStatus, code: str, message: str) -> None:
        super().__init__(message)
        self.status = status
        self.code = code


def main() -> None:
    server = ThreadingHTTPServer(("127.0.0.1", 8790), PriceRequestHandler)
    print("HENGJI price intelligence listening on 127.0.0.1:8790")
    server.serve_forever()


if __name__ == "__main__":
    main()
