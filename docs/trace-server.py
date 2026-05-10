#!/usr/bin/env python3
"""
Devin Live Trace Server — SQLite-Backed
=========================================
A lightweight Python server that:
1. Serves the live-trace-demo.html and data-flow-demo.html (SQLite-backed) frontends
2. Manages a SQLite database mirroring VSAM datasets (ACCTDAT, TRANSACT, etc.)
3. Exposes REST API endpoints for DB operations
4. Creates Devin sessions using the "Live COBOL Flow Trace" playbook
5. Acts as intermediary: detects when Devin needs terminal input,
   relays DB query results back to the Devin session
6. Streams trace events + SQL query log to the frontend via SSE

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

import carddemo_db as db

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
                    },
                    "requires_input": {
                        "type": "string",
                        "description": "Set to 'transaction_amount' when user input is needed"
                    },
                    "db_query": {
                        "type": "string",
                        "description": "Set to 'check_overlimit' when Devin needs DB state"
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


def send_session_message(api_key, org_id, session_id, message):
    """Send a message to a Devin session (to resume after input pause)."""
    data = {"message": message}
    return devin_api_request(
        "POST", f"/organizations/{org_id}/sessions/{session_id}/messages",
        api_key, data
    )


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
    """HTTP handler that serves the frontend and handles API + SSE."""

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path

        if path == "/":
            self.serve_landing()
        elif path in ("/live", "/live/"):
            self.serve_file("live-trace-demo.html")
        elif path in ("/static", "/static/", "/dataflow", "/dataflow/"):
            self.serve_file("data-flow-demo.html")
        elif path in ("/api/db/state",):
            self.handle_db_state()
        elif path in ("/api/db/querylog",):
            self.handle_db_querylog()
        elif path.startswith("/events"):
            self.handle_sse()
        elif path == "/health":
            self.send_json({"status": "ok"})
        else:
            super().do_GET()

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path

        if path in ("/api/db/reset",):
            self.handle_db_reset()
        elif path in ("/api/db/transaction",):
            self.handle_db_transaction()
        elif path in ("/api/db/batch",):
            self.handle_db_batch()
        else:
            self.send_error(404, "Not found")

    def read_body(self):
        length = int(self.headers.get("Content-Length", 0))
        if length == 0:
            return {}
        return json.loads(self.rfile.read(length).decode())

    def send_json(self, data, status=200):
        body = json.dumps(data, indent=2).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    # ── DB API endpoints ────────────────────────────────────────

    def handle_db_state(self):
        """GET /api/db/state — return full database state."""
        state = db.get_state()
        self.send_json(state)

    def handle_db_querylog(self):
        """GET /api/db/querylog?since=<timestamp> — return query log."""
        params = parse_qs(urlparse(self.path).query)
        since = float(params["since"][0]) if "since" in params else None
        logs = db.get_query_log(since)
        self.send_json({"query_log": logs})

    def handle_db_reset(self):
        """POST /api/db/reset — reset database to initial state."""
        result = db.reset_db()
        self.send_json(result)

    def handle_db_transaction(self):
        """POST /api/db/transaction — add a pending transaction."""
        body = self.read_body()
        amount = body.get("amount", 0)
        merchant = body.get("merchant", "UNSPECIFIED")
        card_num = body.get("card_num", "9680294154603697")

        if not amount or amount <= 0:
            self.send_json({"error": "Invalid amount"}, 400)
            return

        result = db.add_transaction(amount, merchant, card_num)
        self.send_json(result)

    def handle_db_batch(self):
        """POST /api/db/batch — run batch processing (overlimit check)."""
        result = db.run_batch()
        self.send_json(result)

    # ── File serving ────────────────────────────────────────────

    def serve_landing(self):
        html = LANDING_HTML
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(html.encode())

    def serve_file(self, filename):
        html_path = Path(__file__).parent / filename
        if html_path.exists():
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            self.wfile.write(html_path.read_bytes())
        else:
            self.send_error(404, f"{filename} not found")

    # ── SSE for live Devin trace ────────────────────────────────

    def handle_sse(self):
        """Handle SSE connection for live trace events.

        When a trace_step contains requires_input='transaction_amount',
        the server pauses polling, sends an 'input_required' SSE event,
        and waits for the frontend to POST the transaction to /api/db/transaction.
        Then the server sends the DB state back to the Devin session and resumes.

        When a trace_step contains db_query='check_overlimit', the server
        queries the DB, sends the result to the Devin session, and forwards
        the response as an SSE event.
        """
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)

        api_key = os.environ.get("DEVIN_API_KEY", "")
        org_id = _ORG_ID
        option_num = params.get("option", ["8"])[0]
        option_name = params.get("option_name", ["Add Transaction"])[0]

        if not api_key or not org_id:
            self.send_error(500, "Server not configured — restart with DEVIN_API_KEY set")
            return

        user_question = (
            f"I'm looking at the CardDemo mainframe application. "
            f"I selected menu option {option_num} ({option_name}). "
            f"What does this flow do? Trace through the COBOL source code "
            f"and show me each program call, data access, and how the pieces connect. "
            f"The server has a SQLite database mirroring VSAM datasets. "
            f"When you reach the transaction entry step, output a trace_step with "
            f'requires_input="transaction_amount" so the user can enter the amount. '
            f"When you reach the overlimit check, output a trace_step with "
            f'db_query="check_overlimit" so the server can query the database '
            f"and send you the actual account state for your decision."
        )

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

            session = create_trace_session(api_key, org_id, user_question)
            session_id = session.get("session_id", "")
            session_url = session.get("url", "")

            self.send_sse_event({
                "type": "session_created",
                "session_id": session_id,
                "session_url": session_url,
            })

            triggered_steps = set()
            cursor = None
            prev_structured_count = 0
            max_polls = 180
            poll_count = 0

            while poll_count < max_polls:
                time.sleep(5)
                poll_count += 1

                try:
                    status_resp = get_session_status(api_key, org_id, session_id)
                    status = status_resp.get("status", "unknown")

                    structured = status_resp.get("structured_output", {})
                    if structured and isinstance(structured, dict):
                        steps_list = structured.get("trace_steps", [])
                        if len(steps_list) > prev_structured_count:
                            for step_data in steps_list[prev_structured_count:]:
                                idx = step_data.get("step_index", -99)

                                # Check for special directives
                                requires_input = step_data.get("requires_input")
                                db_query = step_data.get("db_query")

                                if requires_input == "transaction_amount":
                                    self.send_sse_event({
                                        "type": "input_required",
                                        "input_type": "transaction_amount",
                                        "step_index": idx,
                                        "title": step_data.get("title", ""),
                                        "finding": step_data.get("finding", ""),
                                        "prompt": "Enter the transaction amount in the terminal",
                                    })
                                    # Wait for frontend to POST /api/db/transaction
                                    # The frontend will send a message to resume
                                    input_poll = 0
                                    while input_poll < 120:  # 10 min max wait
                                        time.sleep(5)
                                        input_poll += 1
                                        # Check if a new pending transaction exists
                                        state = db.get_state()
                                        pending = [t for t in state["transactions"]
                                                   if t["status"] == "Pending"]
                                        if pending:
                                            latest = pending[-1]
                                            # Send DB state to Devin
                                            msg = (
                                                f"The user entered a transaction: "
                                                f"amount=${latest['amount']:.2f}, "
                                                f"merchant={latest['merchant']}, "
                                                f"card={latest['card_num']}. "
                                                f"Transaction ID: {latest['tran_id']}. "
                                                f"Status: Pending. "
                                                f"The transaction has been written to the "
                                                f"SQLite database. Continue tracing the "
                                                f"batch processing flow."
                                            )
                                            try:
                                                send_session_message(
                                                    api_key, org_id, session_id, msg
                                                )
                                            except Exception as e:
                                                print(f"Failed to send message to Devin: {e}")
                                            self.send_sse_event({
                                                "type": "input_received",
                                                "transaction": latest,
                                            })
                                            break
                                    continue

                                if db_query == "check_overlimit":
                                    # Server queries DB and sends result to Devin
                                    state = db.get_state()
                                    acct = state["account"]
                                    msg = (
                                        f"Database query result — current account state:\n"
                                        f"  acct_id: {acct['acct_id']}\n"
                                        f"  balance: ${acct['balance']:.2f}\n"
                                        f"  credit_limit: ${acct['credit_limit']:.2f}\n"
                                        f"  cyc_credit: ${acct['cyc_credit']:.2f}\n"
                                        f"  cyc_debit: ${acct['cyc_debit']:.2f}\n\n"
                                        f"Use these values to compute WS-TEMP-BAL and "
                                        f"determine if the transaction should be accepted "
                                        f"or rejected. Then call the batch processing "
                                        f"endpoint to execute the decision."
                                    )
                                    try:
                                        send_session_message(
                                            api_key, org_id, session_id, msg
                                        )
                                    except Exception as e:
                                        print(f"Failed to send DB state to Devin: {e}")
                                    self.send_sse_event({
                                        "type": "db_query_result",
                                        "step_index": idx,
                                        "account_state": acct,
                                    })
                                    continue

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

                    if -1 in triggered_steps:
                        break
                    if status in ("exit", "error"):
                        break

                except Exception as e:
                    print(f"Poll error: {e}")
                    continue

            self.send_sse_event({
                "type": "complete",
                "steps_resolved": len([s for s in triggered_steps if s >= 0]),
                "session_url": session_url,
            })

        except Exception as e:
            self.send_sse_event({"type": "error", "message": str(e)})

    def send_sse_event(self, data):
        try:
            msg = f"data: {json.dumps(data)}\n\n"
            self.wfile.write(msg.encode())
            self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            pass

    def log_message(self, format, *args):
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
    flex-wrap: wrap;
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
  .card.db h2 { color: #4da6ff; }
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
  .card.db .badge {
    background: rgba(77,166,255,0.1);
    color: #4da6ff;
    border: 1px solid rgba(77,166,255,0.3);
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
    <a href="/dataflow" class="card db">
      <div class="badge">SQLITE-BACKED &mdash; NO API KEY NEEDED</div>
      <h2>/dataflow</h2>
      <p>
        Live database-driven demo. Transactions write to SQLite,
        batch processing queries real DB state for overlimit decisions.
        Run it twice to see data alteration change the routing.
        Shows actual SQL queries in real-time.
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

    # Initialize SQLite database
    print("Initializing SQLite database...")
    db.init_db()
    print(f"  Database: {db.DB_PATH}")

    api_key = os.environ.get("DEVIN_API_KEY", "")
    if api_key:
        print("Resolving org ID from API key...")
        try:
            _ORG_ID = resolve_org_id(api_key)
            print(f"  Org ID: {_ORG_ID}")
        except Exception as e:
            print(f"WARNING: Could not resolve org ID: {e}")
            print("  /live route will not work, but /dataflow will.")
    else:
        print("NOTE: DEVIN_API_KEY not set. /live route disabled.")
        print("      /dataflow route works without an API key.")

    server = HTTPServer(("0.0.0.0", port), TraceHandler)
    print(f"""
+==============================================================+
|  Devin Live COBOL Flow Trace Server (SQLite-Backed)          |
|  ----------------------------------------------------------- |
|                                                              |
|  Routes:                                                     |
|    http://localhost:{port}          Landing page               |
|    http://localhost:{port}/dataflow SQLite-backed live demo    |
|    http://localhost:{port}/live     Live trace (asks Devin)    |
|                                                              |
|  API Endpoints:                                              |
|    GET  /api/db/state       Full database state              |
|    POST /api/db/reset       Reset DB to initial state        |
|    POST /api/db/transaction Add a pending transaction        |
|    POST /api/db/batch       Run batch processing             |
|    GET  /api/db/querylog    SQL query audit log              |
|                                                              |
|  Database: {str(db.DB_PATH):<45}|
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
