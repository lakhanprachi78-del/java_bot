"""Compare the Python and Java LOS service contracts.

HTTP mode uses only the Python standard library. WebSocket mode requires the
optional ``websocket-client`` package because the running Angular client uses
the ``/ws`` endpoint.
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from typing import Any, Callable
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class Case:
    name: str
    method: str
    path: str
    body: dict[str, Any] | None = None


def cases(session_id: str, application_id: str, status: str) -> list[Case]:
    return [
        Case("health", "GET", "/health"),
        Case("whoami", "GET", "/whoami?" + urlencode({"session_id": session_id})),
        Case("greet", "GET", "/greet?" + urlencode({"session_id": session_id})),
        Case("reset", "POST", "/chat/reset?" + urlencode({"session_id": session_id})),
        Case("direct status", "POST", "/chat/direct", {
            "session_id": session_id, "field": "status", "value": status, "offset": 0,
        }),
        Case("direct status second page", "POST", "/chat/direct", {
            "session_id": session_id, "field": "status", "value": status, "offset": 25,
        }),
        Case("direct application", "POST", "/chat/direct", {
            "session_id": session_id, "field": "application_id", "value": application_id, "offset": 0,
        }),
        Case("chat", "POST", "/chat", {"session_id": session_id, "message": "Show my applications"}),
        Case("out of scope", "POST", "/chat", {"session_id": session_id, "message": "What is the weather today?"}),
        Case("feedback", "POST", "/api/feedback", {"query_id": 1, "positive": True}),
        Case("feedback detail", "POST", "/api/feedback/detail", {
            "query_id": 1, "query_text": "contract test", "answer_text": "contract test",
            "positive": True, "tags": ["contract-test"], "comment": "automated comparison",
        }),
    ]


def http_request(base_url: str, case: Case, timeout: float) -> tuple[int, Any]:
    data = None if case.body is None else json.dumps(case.body).encode("utf-8")
    request = Request(base_url.rstrip("/") + case.path, data=data, method=case.method)
    if data is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with urlopen(request, timeout=timeout) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except HTTPError as error:
        return error.code, read_json_error(error)
    except (URLError, TimeoutError) as error:
        return 0, {"error": f"connection failed: {error}"}


def read_json_error(error: HTTPError) -> Any:
    try:
        return json.loads(error.read().decode("utf-8"))
    except (ValueError, UnicodeDecodeError):
        return {"error": error.reason}


def websocket_request(base_url: str, case: Case, timeout: float) -> tuple[int, Any]:
    try:
        import websocket
    except ImportError as error:
        raise RuntimeError("WebSocket mode requires: python -m pip install websocket-client") from error

    payload = case.body or {}
    ws_url = base_url.rstrip("/") + "/ws"
    try:
        socket = websocket.create_connection(ws_url, timeout=timeout)
        try:
            socket.send(json.dumps({"type": websocket_type(case), "payload": payload}))
            response = json.loads(socket.recv())
            return 200, response.get("payload", response)
        finally:
            socket.close()
    except Exception as error:  # websocket-client exposes several connection exception types
        return 0, {"error": f"connection failed: {error}"}


def websocket_type(case: Case) -> str:
    return {
        "health": "health",
        "whoami": "whoami",
        "greet": "greet",
        "reset": "chat.reset",
        "direct status": "chat.direct",
        "direct status second page": "chat.direct",
        "direct application": "chat.direct",
        "chat": "chat",
        "feedback": "feedback",
        "feedback detail": "feedback.detail",
    }[case.name]


def compare(case_list: list[Case], request: Callable[[str, Case, float], tuple[int, Any]],
            python_url: str, java_url: str, timeout: float) -> int:
    failures = 0
    for case in case_list:
        python_result = request(python_url, case, timeout)
        java_result = request(java_url, case, timeout)
        if python_result[0] != 0 and java_result[0] != 0 and python_result == java_result:
            print(f"PASS  {case.name}")
            continue
        failures += 1
        print(f"FAIL  {case.name}")
        print(f"      python: {json.dumps(python_result, sort_keys=True)}")
        print(f"      java:   {json.dumps(java_result, sort_keys=True)}")
    print(f"\n{len(case_list) - failures} passed, {failures} failed")
    return 1 if failures else 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("http", "websocket"), default="http")
    parser.add_argument("--python-url", default="http://127.0.0.1:5000")
    parser.add_argument("--java-url", default="http://127.0.0.1:8080")
    parser.add_argument("--session-id", action="append", dest="session_ids",
                        help="Session to test; repeat to select multiple (default: chandan, prachi, admin)")
    parser.add_argument("--application-id", default="APP-0001")
    parser.add_argument("--status", default="approved")
    parser.add_argument("--timeout", type=float, default=10.0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    request = http_request if args.mode == "http" else websocket_request
    if args.mode == "websocket":
        args.python_url = args.python_url.replace("http://", "ws://").replace("https://", "wss://")
        args.java_url = args.java_url.replace("http://", "ws://").replace("https://", "wss://")
    session_ids = args.session_ids or ["session-chandan", "session-prachi", "session-admin"]
    selected_cases = [case for session_id in session_ids
                      for case in cases(session_id, args.application_id, args.status)]
    if args.mode == "websocket":
        selected_cases = [case for case in selected_cases if case.name != "health"]
    return compare(selected_cases, request,
                   args.python_url, args.java_url, args.timeout)


if __name__ == "__main__":
    sys.exit(main())