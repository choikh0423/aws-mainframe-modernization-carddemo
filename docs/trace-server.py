#!/usr/bin/env python3
"""
Devin Live Trace Server
=======================
A lightweight Python server that:
1. Serves the live-trace-demo.html frontend
2. Creates a Devin child session to trace COBOL dynamic calls
3. Polls session messages and streams events to the frontend via SSE

Usage:
    export DEVIN_API_KEY="cog_your_key_here"
    export DEVIN_ORG_ID="org-your_org_id"
    python trace-server.py

    Then open http://localhost:8765 in your browser.

Alternatively, pass credentials via the frontend UI (they'll be sent as query params).
"""

import json
import os
import re
import sys
import time
import threading
import urllib.request
import urllib.error
from http.server import HTTPServer, SimpleHTTPRequestHandler
from pathlib import Path

API_BASE = "https://api.devin.ai/v3"
REPO = "choikh0423/aws-mainframe-modernization-carddemo"

# The prompt that the child Devin session will execute
TRACE_PROMPT = """You are performing a live dynamic call resolution trace of the "Add Transaction" flow in the AWS CardDemo COBOL application (repo: choikh0423/aws-mainframe-modernization-carddemo).

Trace through the code step by step. For each step, read the actual source file and explain what you found. Be concise.

**Step 1 - MENU DISPATCH**: Read app/cpy/COMEN02Y.cpy. Find menu option 8 and show which program name it maps to. Explain the REDEFINES overlay. Then read the XCTL in app/cbl/COMEN01C.cbl lines 184-187.

**Step 2 - XCTL TRANSFER**: Explain how COMEN01C transfers control to COTRN02C via CICS XCTL with COMMAREA.

**Step 3 - XREF LOOKUP**: In app/cbl/COTRN02C.cbl, find where it reads CCXREF and CXACAIX files. Show the variable dataset names and explain CICS FCT resolution.

**Step 4 - DATE VALIDATION CALL**: Find CALL 'CSUTLDTC' in COTRN02C.cbl. Then read app/cbl/CSUTLDTC.cbl and map the positional parameters from the caller's USING clause to the callee's LINKAGE SECTION.

**Step 5 - CEEDAYS CHAIN**: In CSUTLDTC.cbl, find the CALL "CEEDAYS" and explain the IBM LE runtime call with its parameter types.

**Step 6 - DATA MUTATION**: Find the WRITE to TRANSACT file in COTRN02C.cbl. Show how the transaction ID is auto-generated (STARTBR, READPREV, ADD 1).

**Step 7 - BATCH CONNECTION**: Read app/cbl/CBTRN02C.cbl. Show how the batch program reads from the same TRANSACT file and updates ACCOUNT and TCATBAL files.

Output each step clearly with the step number. This is a live trace demonstration of semantic COBOL code reading."""

# Keywords that map session messages to trace steps
STEP_KEYWORDS = [
    # Step 0: Menu dispatch
    {"keywords": ["COMEN02Y", "option 8", "menu", "REDEFINES"], "step_index": 0},
    # Step 1: XCTL transfer
    {"keywords": ["XCTL", "COMMAREA", "COTRN02C", "transfer"], "step_index": 1},
    # Step 2: XREF lookup
    {"keywords": ["CCXREF", "CXACAIX", "cross-ref", "FCT"], "step_index": 2},
    # Step 3: Date validation
    {"keywords": ["CSUTLDTC", "LINKAGE", "date valid", "positional"], "step_index": 3},
    # Step 4: CEEDAYS
    {"keywords": ["CEEDAYS", "Lilian", "LE runtime", "Language Environment"], "step_index": 4},
    # Step 5: Data mutation
    {"keywords": ["TRANSACT", "WRITE", "STARTBR", "READPREV", "mutation"], "step_index": 5},
    # Step 6: Batch
    {"keywords": ["CBTRN02C", "batch", "ACCOUNT", "TCATBAL", "ACCT-CURR-BAL"], "step_index": 6},
]


def devin_api_request(method, path, api_key, data=None):
    """Make a request to the Devin API."""
    url = f"{API_BASE}{path}"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    body = json.dumps(data).encode() if data else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        error_body = e.read().decode() if e.fp else ""
        print(f"API Error {e.code}: {error_body}")
        raise
    except Exception as e:
        print(f"Request error: {e}")
        raise


def create_trace_session(api_key, org_id):
    """Create a child Devin session that traces through the COBOL code."""
    data = {
        "prompt": TRACE_PROMPT,
    }
    result = devin_api_request("POST", f"/organizations/{org_id}/sessions", api_key, data)
    return result


def get_session_status(api_key, org_id, session_id):
    """Get the current status of a session."""
    return devin_api_request("GET", f"/organizations/{org_id}/sessions/{session_id}", api_key)


def get_session_messages(api_key, org_id, session_id, after=None):
    """Get messages from a session."""
    path = f"/organizations/{org_id}/sessions/{session_id}/messages"
    if after:
        path += f"?after={after}"
    return devin_api_request("GET", path, api_key)


def detect_step(message_text, triggered_steps):
    """Detect which trace step a message corresponds to."""
    text_lower = message_text.lower()
    for step_def in STEP_KEYWORDS:
        idx = step_def["step_index"]
        if idx in triggered_steps:
            continue
        matches = sum(1 for kw in step_def["keywords"] if kw.lower() in text_lower)
        if matches >= 2:  # require at least 2 keyword matches
            return idx
    return None


class TraceHandler(SimpleHTTPRequestHandler):
    """HTTP handler that serves the frontend and handles SSE events."""

    def do_GET(self):
        if self.path == "/" or self.path == "/index.html":
            self.serve_html()
        elif self.path.startswith("/events"):
            self.handle_sse()
        elif self.path == "/health":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok"}).encode())
        else:
            super().do_GET()

    def serve_html(self):
        """Serve the live-trace-demo.html file."""
        html_path = Path(__file__).parent / "live-trace-demo.html"
        if html_path.exists():
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            self.wfile.write(html_path.read_bytes())
        else:
            self.send_error(404, "live-trace-demo.html not found")

    def handle_sse(self):
        """Handle SSE connection for live trace events."""
        # Parse query params
        from urllib.parse import urlparse, parse_qs
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)

        api_key = params.get("api_key", [os.environ.get("DEVIN_API_KEY", "")])[0]
        org_id = params.get("org_id", [os.environ.get("DEVIN_ORG_ID", "")])[0]

        if not api_key or not org_id:
            self.send_error(400, "Missing api_key or org_id")
            return

        # Set up SSE headers
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "keep-alive")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()

        try:
            # Create child session
            self.send_sse_event({"type": "status", "message": "Creating Devin trace session..."})
            session = create_trace_session(api_key, org_id)
            session_id = session.get("session_id", "")
            session_url = session.get("url", "")

            self.send_sse_event({
                "type": "session_created",
                "session_id": session_id,
                "session_url": session_url,
            })

            # Poll for messages and detect trace steps
            triggered_steps = set()
            cursor = None
            max_polls = 120  # 10 minutes max
            poll_count = 0

            while poll_count < max_polls and len(triggered_steps) < 7:
                time.sleep(5)
                poll_count += 1

                try:
                    # Check session status
                    status_resp = get_session_status(api_key, org_id, session_id)
                    status = status_resp.get("status", "unknown")

                    if status in ("exit", "error"):
                        break

                    # Get new messages
                    msg_resp = get_session_messages(api_key, org_id, session_id, after=cursor)
                    items = msg_resp.get("items", [])

                    for msg in items:
                        content = msg.get("content", "") or msg.get("message", "")
                        if not content:
                            continue

                        # Detect which step this message relates to
                        step_idx = detect_step(content, triggered_steps)
                        if step_idx is not None:
                            triggered_steps.add(step_idx)
                            self.send_sse_event({
                                "type": "step",
                                "step_index": step_idx,
                                "message": content[:200],
                            })

                        # Also send raw file reading events
                        file_match = re.search(r'(?:reading|opened?|app/(?:cbl|cpy)/\w+\.\w+)', content, re.I)
                        if file_match:
                            self.send_sse_event({
                                "type": "reading_file",
                                "file": file_match.group(0),
                                "message": content[:150],
                            })

                    # Update cursor for pagination
                    new_cursor = msg_resp.get("end_cursor")
                    if new_cursor:
                        cursor = new_cursor

                except Exception as e:
                    print(f"Poll error: {e}")
                    continue

            # Send completion
            self.send_sse_event({"type": "complete", "steps_resolved": len(triggered_steps)})

        except Exception as e:
            self.send_sse_event({"type": "error", "message": str(e)})

    def send_sse_event(self, data):
        """Send a Server-Sent Event."""
        try:
            msg = f"data: {json.dumps(data)}\n\n"
            self.wfile.write(msg.encode())
            self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            pass

    def log_message(self, format, *args):
        """Suppress default request logging for SSE polling."""
        if "/events" not in (args[0] if args else ""):
            super().log_message(format, *args)


def main():
    port = int(os.environ.get("PORT", 8765))
    server = HTTPServer(("0.0.0.0", port), TraceHandler)
    print(f"""
╔══════════════════════════════════════════════════════════════╗
║  Devin Live Trace Server                                     ║
║  ─────────────────────────────────────────────────────────── ║
║  Open: http://localhost:{port}                                ║
║                                                              ║
║  Configuration (optional — can also set via UI):             ║
║    DEVIN_API_KEY  = {os.environ.get('DEVIN_API_KEY', '(not set)')[:20]}...  ║
║    DEVIN_ORG_ID   = {os.environ.get('DEVIN_ORG_ID', '(not set)')[:20]}...  ║
║                                                              ║
║  Press Ctrl+C to stop                                        ║
╚══════════════════════════════════════════════════════════════╝
""")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down...")
        server.shutdown()


if __name__ == "__main__":
    main()
