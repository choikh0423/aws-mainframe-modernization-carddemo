#!/usr/bin/env python3
"""
Devin Live Trace Server
=======================
A lightweight Python server that:
1. Serves the live-trace-demo.html frontend
2. Creates a Devin session using the "Live COBOL Flow Trace" playbook
3. Polls session messages for structured JSON trace_step blocks
4. Streams trace events to the frontend via SSE in real-time

Usage:
    export DEVIN_API_KEY="cog_your_key_here"  # org-scoped service user key
    python trace-server.py

    Then open http://localhost:8765 in your browser.

The server auto-detects the org ID from the API key via GET /v3/self.
"""

import json
import os
import re
import time
import urllib.request
import urllib.error
from http.server import HTTPServer, SimpleHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlparse, parse_qs

API_BASE = "https://api.devin.ai/v3"
REPO = "choikh0423/aws-mainframe-modernization-carddemo"

# Playbook ID for "Live COBOL Flow Trace"
PLAYBOOK_ID = "playbook-9e1bfe742b43446eae8c9be94e4a044a"

# Structured output schema -- Devin extracts trace_steps matching this schema
# and populates them in the session's structured_output field automatically.
STRUCTURED_OUTPUT_SCHEMA = {
    "type": "object",
    "properties": {
        "trace_steps": {
            "type": "array",
            "description": "Each step discovered during the live trace",
            "items": {
                "type": "object",
                "properties": {
                    "step_index": {
                        "type": "integer",
                        "description": "0-6 for trace steps, -1 for completion"
                    },
                    "title": {
                        "type": "string",
                        "description": "Short title for this trace step"
                    },
                    "file": {
                        "type": "string",
                        "description": "Source file(s) read in this step"
                    },
                    "finding": {
                        "type": "string",
                        "description": "What was discovered in this step"
                    },
                    "code_snippet": {
                        "type": "string",
                        "description": "Key COBOL source lines"
                    }
                },
                "required": ["step_index", "title", "finding"]
            }
        }
    },
    "required": ["trace_steps"]
}


# Resolved at startup by calling /v3/self
_ORG_ID = None


def resolve_org_id(api_key):
    """Auto-detect org_id from an org-scoped service user key via /v3/self."""
    resp = devin_api_request("GET", "/self", api_key)
    org_id = resp.get("org_id")
    if not org_id:
        raise RuntimeError(
            "API key is not org-scoped (org_id is null). "
            "Please use an org-scoped service user key."
        )
    return org_id


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


def create_trace_session(api_key, org_id, user_question):
    """Create a Devin session with the live trace playbook and structured output."""
    data = {
        "prompt": user_question,
        "playbook_id": PLAYBOOK_ID,
        "structured_output_schema": STRUCTURED_OUTPUT_SCHEMA,
    }
    return devin_api_request("POST", f"/organizations/{org_id}/sessions", api_key, data)


def get_session_status(api_key, org_id, session_id):
    """Get the current status of a session, including structured_output."""
    return devin_api_request("GET", f"/organizations/{org_id}/sessions/{session_id}", api_key)


def get_session_messages(api_key, org_id, session_id, after=None):
    """Get messages from a session."""
    path = f"/organizations/{org_id}/sessions/{session_id}/messages"
    if after:
        path += f"?after={after}"
    return devin_api_request("GET", path, api_key)


def extract_trace_steps_from_message(text):
    """Extract any {"trace_step": {...}} JSON blocks from a message."""
    steps = []
    for match in re.finditer(r'\{["\s]*trace_step["\s]*:', text):
        start = match.start()
        depth = 0
        i = start
        while i < len(text):
            if text[i] == '{':
                depth += 1
            elif text[i] == '}':
                depth -= 1
                if depth == 0:
                    try:
                        parsed = json.loads(text[start:i + 1])
                        if "trace_step" in parsed:
                            steps.append(parsed["trace_step"])
                    except json.JSONDecodeError:
                        pass
                    break
            i += 1
    return steps


class TraceHandler(SimpleHTTPRequestHandler):
    """HTTP handler that serves the frontend and handles SSE events."""

    def do_GET(self):
        if self.path == "/":
            self.serve_landing()
        elif self.path == "/live" or self.path == "/live/":
            self.serve_file("live-trace-demo.html")
        elif self.path == "/static" or self.path == "/static/":
            self.serve_file("data-flow-demo.html")
        elif self.path == "/dataflow" or self.path == "/dataflow/":
            self.serve_file("data-flow-demo.html")
        elif self.path.startswith("/events"):
            self.handle_sse()
        elif self.path == "/health":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok"}).encode())
        else:
            super().do_GET()

    def serve_landing(self):
        """Serve a landing page with links to /live and /static."""
        html = LANDING_HTML
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(html.encode())

    def serve_file(self, filename):
        """Serve an HTML file from the docs directory."""
        html_path = Path(__file__).parent / filename
        if html_path.exists():
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            self.wfile.write(html_path.read_bytes())
        else:
            self.send_error(404, f"{filename} not found")

    def handle_sse(self):
        """Handle SSE connection for live trace events."""
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)

        api_key = os.environ.get("DEVIN_API_KEY", "")
        org_id = _ORG_ID
        option_num = params.get("option", ["8"])[0]
        option_name = params.get("option_name", ["Add Transaction"])[0]

        if not api_key or not org_id:
            self.send_error(500, "Server not configured — restart with DEVIN_API_KEY set")
            return

        # Build the user question based on the selected menu option
        user_question = (
            f"I'm looking at the CardDemo mainframe application. "
            f"I selected menu option {option_num} ({option_name}). "
            f"What does this flow do? Trace through the COBOL source code "
            f"and show me each program call, data access, and how the pieces connect."
        )

        # Set up SSE headers
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "keep-alive")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()

        try:
            self.send_sse_event({
                "type": "status",
                "message": f"Creating Devin session to trace option {option_num}..."
            })

            # Create session with playbook + structured output
            session = create_trace_session(api_key, org_id, user_question)
            session_id = session.get("session_id", "")
            session_url = session.get("url", "")

            self.send_sse_event({
                "type": "session_created",
                "session_id": session_id,
                "session_url": session_url,
            })

            # Poll for messages and extract trace_step JSON blocks
            triggered_steps = set()
            cursor = None
            prev_structured_count = 0
            max_polls = 180  # 15 minutes max
            poll_count = 0

            while poll_count < max_polls:
                time.sleep(5)
                poll_count += 1

                try:
                    # Check session status + structured output
                    status_resp = get_session_status(api_key, org_id, session_id)
                    status = status_resp.get("status", "unknown")

                    # Check structured_output for trace steps
                    structured = status_resp.get("structured_output", {})
                    if structured and isinstance(structured, dict):
                        steps_list = structured.get("trace_steps", [])
                        if len(steps_list) > prev_structured_count:
                            for step_data in steps_list[prev_structured_count:]:
                                idx = step_data.get("step_index", -99)
                                if idx not in triggered_steps:
                                    triggered_steps.add(idx)
                                    self.send_sse_event({
                                        "type": "step",
                                        "step_index": idx,
                                        "title": step_data.get("title", ""),
                                        "file": step_data.get("file", ""),
                                        "finding": step_data.get("finding", ""),
                                        "code_snippet": step_data.get("code_snippet", ""),
                                    })
                                    if idx == -1:
                                        break
                            prev_structured_count = len(steps_list)

                    # Also scan messages for trace_step JSON blocks (fallback)
                    msg_resp = get_session_messages(
                        api_key, org_id, session_id, after=cursor
                    )
                    items = msg_resp.get("items", [])
                    for msg in items:
                        content = msg.get("content", "") or msg.get("message", "")
                        if not content:
                            continue
                        for step_data in extract_trace_steps_from_message(content):
                            idx = step_data.get("step_index", -99)
                            if idx not in triggered_steps:
                                triggered_steps.add(idx)
                                self.send_sse_event({
                                    "type": "step",
                                    "step_index": idx,
                                    "title": step_data.get("title", ""),
                                    "file": step_data.get("file", ""),
                                    "finding": step_data.get("finding", ""),
                                    "code_snippet": step_data.get("code_snippet", ""),
                                })

                    new_cursor = msg_resp.get("end_cursor")
                    if new_cursor:
                        cursor = new_cursor

                    # Check if complete
                    if -1 in triggered_steps:
                        break
                    if status in ("exit", "error"):
                        break

                except Exception as e:
                    print(f"Poll error: {e}")
                    continue

            # Send completion
            self.send_sse_event({
                "type": "complete",
                "steps_resolved": len([s for s in triggered_steps if s >= 0]),
                "session_url": session_url,
            })

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


LANDING_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Devin Dynamic Call Resolution Demo</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    background: #0a0a1a;
    color: #e0e0e0;
    font-family: 'SF Mono', 'Cascadia Code', 'Fira Code', monospace;
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 100vh;
  }
  .container {
    text-align: center;
    max-width: 700px;
    padding: 40px;
  }
  h1 {
    font-size: 24px;
    color: #4da6ff;
    margin-bottom: 8px;
  }
  h1 span { color: #00ff88; }
  .subtitle {
    color: #888;
    font-size: 13px;
    margin-bottom: 40px;
    line-height: 1.6;
  }
  .cards {
    display: flex;
    gap: 24px;
    justify-content: center;
  }
  .card {
    background: #0d1117;
    border: 1px solid #1e3a5f;
    border-radius: 12px;
    padding: 32px 28px;
    width: 300px;
    text-decoration: none;
    color: inherit;
    transition: all 0.3s;
  }
  .card:hover {
    border-color: #4da6ff;
    transform: translateY(-4px);
    box-shadow: 0 8px 24px rgba(77,166,255,0.15);
  }
  .card h2 {
    font-size: 16px;
    margin-bottom: 12px;
  }
  .card.static h2 { color: #ffa94d; }
  .card.live h2 { color: #00ff88; }
  .card p {
    color: #888;
    font-size: 11px;
    line-height: 1.6;
  }
  .card .badge {
    display: inline-block;
    padding: 3px 10px;
    border-radius: 12px;
    font-size: 9px;
    font-weight: 700;
    margin-bottom: 12px;
    letter-spacing: 0.5px;
  }
  .card.static .badge {
    background: rgba(255,169,77,0.15);
    color: #ffa94d;
    border: 1px solid rgba(255,169,77,0.3);
  }
  .card.live .badge {
    background: rgba(0,255,136,0.1);
    color: #00ff88;
    border: 1px solid rgba(0,255,136,0.3);
  }
  .footer {
    margin-top: 40px;
    color: #555;
    font-size: 10px;
  }
  .footer a { color: #4da6ff; text-decoration: none; }
</style>
</head>
<body>
<div class="container">
  <h1><span>Devin</span> &mdash; Dynamic Call Resolution</h1>
  <p class="subtitle">
    Interactive demo tracing COBOL dynamic calls through the<br>
    CardDemo application. Select a menu option and ask Devin:<br>
    &ldquo;What does this flow do?&rdquo;
  </p>
  <div class="cards">
    <a href="/static" class="card static">
      <div class="badge">NO API KEY NEEDED</div>
      <h2>/static</h2>
      <p>
        Combined demo: Option 3 (COCRDLIC read-only trace) &amp; Option 8
        (full 3-phase data flow with batch processing, overlimit detection,
        and live VSAM updates). Devin analysis + interactive simulation.
      </p>
    </a>
    <a href="/live" class="card live">
      <div class="badge">REQUIRES SERVER ENV VARS</div>
      <h2>/live</h2>
      <p>
        Ask Devin &ldquo;what does this flow do?&rdquo; and watch the call
        tree light up in real-time as Devin reads through the COBOL source
        and discovers each dynamic call. Powered by the Live COBOL Flow
        Trace playbook.
      </p>
    </a>
    <a href="/dataflow" class="card static" style="border-color:#ff6b9d;">
      <div class="badge" style="background:rgba(255,107,157,0.15);color:#ff6b9d;border-color:rgba(255,107,157,0.3);">NO API KEY NEEDED</div>
      <h2 style="color:#ff6b9d;">/dataflow</h2>
      <p>
        End-to-end data flow demo: enter a $500 transaction (accepted),
        watch batch processing update the account, then enter $1,000
        (rejected &mdash; overlimit). Shows real VSAM data changing live.
      </p>
    </a>
  </div>
  <div class="footer">
    Repo: <a href="https://github.com/choikh0423/aws-mainframe-modernization-carddemo">choikh0423/aws-mainframe-modernization-carddemo</a>
    &nbsp;&bull;&nbsp;
    <a href="https://app.devin.ai/settings/playbooks/9e1bfe742b43446eae8c9be94e4a044a">Playbook</a>
  </div>
</div>
</body>
</html>
"""


def main():
    global _ORG_ID
    port = int(os.environ.get("PORT", 8765))

    api_key = os.environ.get("DEVIN_API_KEY", "")
    if not api_key:
        print("ERROR: DEVIN_API_KEY environment variable is not set.")
        print("       Use an org-scoped service user key (starts with cog_).")
        return

    print("Resolving org ID from API key...")
    try:
        _ORG_ID = resolve_org_id(api_key)
        print(f"  Org ID: {_ORG_ID}")
    except Exception as e:
        print(f"ERROR: Could not resolve org ID: {e}")
        return

    server = HTTPServer(("0.0.0.0", port), TraceHandler)
    print(f"""
+==============================================================+
|  Devin Live COBOL Flow Trace Server                          |
|  ----------------------------------------------------------- |
|                                                              |
|  Routes:                                                     |
|    http://localhost:{port}          Landing page               |
|    http://localhost:{port}/static   Pre-recorded replay        |
|    http://localhost:{port}/live     Live trace (asks Devin)    |
|                                                              |
|  Playbook: Live COBOL Flow Trace                             |
|    {PLAYBOOK_ID}                                             |
|                                                              |
|  Org ID (auto-detected): {_ORG_ID}                           |
|                                                              |
|  Press Ctrl+C to stop                                        |
+==============================================================+
""")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down...")
        server.shutdown()


if __name__ == "__main__":
    main()
