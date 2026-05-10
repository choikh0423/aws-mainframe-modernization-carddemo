#!/usr/bin/env python3
"""
Devin Live Trace Server (SQLite-Backed)
========================================
A lightweight Python server that:
1. Serves the live-trace-demo.html frontend (SQLite-backed live demo)
2. Creates a Devin session using the "Live COBOL Flow Trace" playbook
3. Polls session messages for structured JSON trace_step blocks
4. Streams trace events to the frontend via SSE in real-time
5. Provides REST API endpoints for SQLite database operations
6. Acts as intermediary: detects requires_input/db_query in trace steps,
   relays DB state to Devin session, and sends SSE events to frontend

Usage:
    export DEVIN_API_KEY="cog_your_key_here"  # optional, for /live Devin trace
    python trace-server.py

    Then open http://localhost:8765 in your browser.

The server auto-detects the org ID from the API key via GET /v3/self.
The SQLite database is initialized on startup from app/data/ASCII/ files.
"""

import json
import os
import re
import threading
import time
import urllib.request
import urllib.error
from http.server import HTTPServer, SimpleHTTPRequestHandler
from socketserver import ThreadingMixIn
from pathlib import Path
from urllib.parse import urlparse, parse_qs

import sys
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "db"))
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
                    "phase": {
                        "type": "string",
                        "enum": ["online", "batch", "complete"],
                        "description": "Execution phase: online (CICS interactive), batch (batch processing), complete (summary)"
                    },
                    "title": {
                        "type": "string",
                        "description": "Short title for this trace step"
                    },
                    "programs": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "Program/file names touched in this step (drives diagram highlighting)"
                    },
                    "file": {
                        "type": "string",
                        "description": "Source file(s) read in this step"
                    },
                    "finding": {
                        "type": "string",
                        "description": "What was discovered — use real DB values when available"
                    },
                    "code_snippet": {
                        "type": "string",
                        "description": "Key COBOL source lines (3-8 lines, from actual source)"
                    },
                    "requires_input": {
                        "type": "string",
                        "description": "Set to 'transaction_amount' when the program expects user input (EXEC CICS RECEIVE MAP). The server will pause and open a terminal overlay."
                    },
                    "db_query": {
                        "type": "string",
                        "description": "Set to 'check_overlimit' at the overlimit decision point. The server will query SQLite and return real account values."
                    }
                },
                "required": ["phase", "title", "programs", "finding"]
            }
        }
    },
    "required": ["trace_steps"]
}


# Resolved at startup by calling /v3/self
_ORG_ID = None

# Threading event for input pause: polling loop waits on this when requires_input is detected.
# /api/input handler sets it to resume polling.
_input_event = threading.Event()
_input_event.set()  # Start in "ready" state (not waiting)
_waiting_for_input = False

# Pending transaction: stored when user submits, committed to DB when Devin traces WRITE TRANSACT
_pending_transaction = None  # {"amount": float, "merchant": str} or None

# Guard: prevents batch from running twice for the same overlimit check
_batch_ran_this_cycle = False


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
        parsed = urlparse(self.path)
        path = parsed.path

        if path == "/":
            self.serve_landing()
        elif path in ("/live", "/live/"):
            self.serve_file("live-trace-demo.html")
        elif path in ("/static", "/static/"):
            self.serve_file("data-flow-demo.html")
        elif path in ("/dataflow", "/dataflow/"):
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

        if path in ("/api/db/transaction",):
            self.handle_db_transaction()
        elif path in ("/api/db/batch",):
            self.handle_db_batch()
        elif path in ("/api/db/reset",):
            self.handle_db_reset()
        elif path in ("/api/input",):
            self.handle_user_input()
        else:
            self.send_error(404, "Not found")

    def send_json(self, data, status=200):
        body = json.dumps(data).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def read_json_body(self):
        length = int(self.headers.get("Content-Length", 0))
        if length:
            return json.loads(self.rfile.read(length).decode())
        return {}

    # --- DB API handlers ---

    def handle_db_state(self):
        state = db.get_state()
        self.send_json(state)

    def handle_db_querylog(self):
        logs = db.get_query_log()
        self.send_json({"queries": logs})

    def handle_db_transaction(self):
        body = self.read_json_body()
        amount = body.get("amount", 0)
        merchant = body.get("merchant", "UNKNOWN")
        result = db.add_transaction(float(amount), merchant)
        self.send_json(result)

    def handle_db_batch(self):
        result = db.run_batch()
        self.send_json(result)

    def handle_db_reset(self):
        db.reset_db()
        self.send_json({"status": "reset", "message": "Database reset to initial state"})

    def handle_user_input(self):
        """Handle user input from the terminal (amount + merchant).
        Stores the transaction as pending (NOT written to DB yet).
        The DB write happens later when Devin's trace reaches WRITE TRANSACT."""
        global _waiting_for_input, _pending_transaction, _batch_ran_this_cycle
        body = self.read_json_body()
        amount = body.get("amount", 0)
        merchant = body.get("merchant", "UNKNOWN")
        session_id = body.get("session_id")

        # New input cycle — reset batch guard so next batch can run
        _batch_ran_this_cycle = False

        # Store as pending — don't write to DB yet
        _pending_transaction = {"amount": float(amount), "merchant": merchant}
        print(f"Pending transaction stored: ${amount} @ {merchant} (will commit when Devin traces WRITE TRANSACT)")

        # Tell Devin the user entered the data — Devin should continue tracing
        # the online flow (WRITE TRANSACT) then move to batch
        if session_id and _ORG_ID:
            api_key = os.environ.get("DEVIN_API_KEY", "")
            if api_key:
                try:
                    msg = (
                        f"The user entered a transaction of ${amount:.2f} for merchant {merchant}. "
                        f"Continue tracing COTRN02C — the program will now EXEC CICS WRITE to the TRANSACT dataset. "
                        f"After that, trace the batch processing flow in CBTRN02C. "
                        f"Use db_query to check the account state at the overlimit decision point."
                    )
                    devin_api_request(
                        "POST",
                        f"/organizations/{_ORG_ID}/sessions/{session_id}/messages",
                        api_key,
                        {"message": msg},
                    )
                except Exception as e:
                    print(f"Warning: could not relay input to Devin session: {e}")

        # Resume the polling loop
        _waiting_for_input = False
        _input_event.set()

        self.send_json({"status": "pending", "amount": amount, "merchant": merchant})

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
            max_polls = 450  # 15 minutes max at 2s intervals
            poll_count = 0

            while poll_count < max_polls:
                # If waiting for user input, block here until /api/input resumes us
                if _waiting_for_input:
                    self.send_sse_event({"type": "status", "message": "Waiting for user input..."})
                    _input_event.wait(timeout=300)  # 5 min max wait for input

                time.sleep(2)
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
                                step_key = step_data.get("title", str(len(triggered_steps)))
                                if step_key not in triggered_steps:
                                    triggered_steps.add(step_key)
                                    self._process_trace_step(
                                        step_data, session_id,
                                        api_key, org_id
                                    )
                                    if step_data.get("phase") == "complete":
                                        triggered_steps.add("__complete__")
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
                            step_key = step_data.get("title", str(len(triggered_steps)))
                            if step_key not in triggered_steps:
                                triggered_steps.add(step_key)
                                self._process_trace_step(
                                    step_data, session_id,
                                    api_key, org_id
                                )
                                if step_data.get("phase") == "complete":
                                    triggered_steps.add("__complete__")

                    new_cursor = msg_resp.get("end_cursor")
                    if new_cursor:
                        cursor = new_cursor

                    # Check if complete
                    if "__complete__" in triggered_steps:
                        break
                    if status in ("exit", "error"):
                        break

                except Exception as e:
                    print(f"Poll error: {e}")
                    continue

            # Send completion
            self.send_sse_event({
                "type": "complete",
                "steps_resolved": len(triggered_steps) - (1 if "__complete__" in triggered_steps else 0),
                "session_url": session_url,
            })

        except Exception as e:
            self.send_sse_event({"type": "error", "message": str(e)})

    def _process_trace_step(self, step_data, session_id, api_key, org_id):
        """Process a trace step — send SSE event and handle requires_input/db_query."""
        phase = step_data.get("phase", "online")
        if phase == "complete":
            self.send_sse_event({
                "type": "complete",
                "title": step_data.get("title", "Trace Complete"),
                "finding": step_data.get("finding", ""),
                "programs": step_data.get("programs", []),
                "session_url": "",
            })
            return

        event = {
            "type": "step",
            "phase": phase,
            "title": step_data.get("title", ""),
            "programs": step_data.get("programs", []),
            "file": step_data.get("file", ""),
            "finding": step_data.get("finding", ""),
            "code_snippet": step_data.get("code_snippet", ""),
        }

        # Commit pending transaction when Devin traces WRITE TRANSACT in online phase
        global _pending_transaction
        programs_upper = [p.upper() for p in step_data.get("programs", [])]
        text_upper = (step_data.get("title", "") + " " + step_data.get("finding", "") + " " + step_data.get("code_snippet", "")).upper()
        if _pending_transaction and phase == "online" and (
            "TRANSACT" in programs_upper or
            ("WRITE" in text_upper and "TRANSACT" in text_upper)
        ):
            txn = _pending_transaction
            _pending_transaction = None
            result = db.add_transaction(txn["amount"], txn["merchant"])
            print(f"Transaction committed to DB: ${txn['amount']} @ {txn['merchant']} (tran_id={result.get('tran_id')})")
            event["db_committed"] = True
            event["tran_id"] = result.get("tran_id")

        # Check for requires_input directive (explicit field or fallback: detect RECEIVE MAP in text)
        requires_input = step_data.get("requires_input")
        if not requires_input:
            text = (step_data.get("title", "") + " " + step_data.get("finding", "") + " " + step_data.get("code_snippet", "")).upper()
            if "RECEIVE MAP" in text:
                requires_input = "transaction_amount"
        if requires_input:
            global _waiting_for_input
            event["type"] = "input_required"
            event["input_type"] = requires_input
            event["prompt"] = step_data.get("prompt", "Enter transaction details")
            event["session_id"] = session_id
            # Signal the polling loop to pause until user submits input
            _input_event.clear()
            _waiting_for_input = True

        # Check for db_query directive (explicit field or fallback: detect overlimit check in text)
        db_query = step_data.get("db_query")
        if not db_query:
            text = (step_data.get("title", "") + " " + step_data.get("finding", "") + " " + step_data.get("code_snippet", "")).upper()
            if ("OVERLIMIT" in text or "WS-TEMP-BAL" in text or "CREDIT-LIMIT" in text) and phase == "batch":
                db_query = "check_overlimit"
        if db_query:
            # Server queries the DB on behalf of Devin
            if db_query == "check_overlimit":
                global _batch_ran_this_cycle
                # Guard: only run batch once per input cycle
                # (prevents double-trigger from structured_output + message scan)
                if _batch_ran_this_cycle:
                    print("Skipping duplicate batch run for this cycle")
                    self.send_sse_event(event)
                    return
                # Safety: commit pending transaction before running batch
                if _pending_transaction:
                    txn = _pending_transaction
                    _pending_transaction = None
                    result = db.add_transaction(txn["amount"], txn["merchant"])
                    print(f"Transaction committed before batch: ${txn['amount']} @ {txn['merchant']} (tran_id={result.get('tran_id')})")
                batch_result = db.run_batch()
                _batch_ran_this_cycle = True
                print("Batch run completed for this cycle")
                state = db.get_state()
                event["type"] = "db_result"
                event["db_query"] = db_query
                event["batch_result"] = batch_result
                event["db_state"] = state.get("account", {})
                event["queries_executed"] = batch_result.get("queries_executed", [])

                # Relay results back to Devin session
                if session_id and api_key and org_id:
                    try:
                        results = batch_result.get("results", [])
                        decision = results[0].get("decision", "UNKNOWN") if results else "UNKNOWN"
                        temp_bal = results[0].get("temp_bal", 0) if results else 0
                        acct = state.get("account", {})
                        msg = (
                            f"Database query results:\n"
                            f"- Account balance: ${acct.get('balance', 0):.2f}\n"
                            f"- Credit limit: ${acct.get('credit_limit', 0):.2f}\n"
                            f"- Cycle credit: ${acct.get('cyc_credit', 0):.2f}\n"
                            f"- Cycle debit: ${acct.get('cyc_debit', 0):.2f}\n"
                            f"- Computed temp_bal: ${temp_bal:.2f}\n"
                            f"- Decision: {decision}\n"
                            f"The overlimit check has been performed. "
                            f"{'Transaction was ACCEPTED and posted.' if decision == 'ACCEPTED' else 'Transaction was REJECTED — overlimit.'}"
                        )
                        devin_api_request(
                            "POST",
                            f"/organizations/{org_id}/sessions/{session_id}/messages",
                            api_key,
                            {"message": msg},
                        )
                    except Exception as e:
                        print(f"Warning: could not relay DB result to Devin: {e}")
            else:
                # Generic DB state query
                state = db.get_state()
                event["type"] = "db_result"
                event["db_query"] = db_query
                event["db_state"] = state

        self.send_sse_event(event)

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
  .card.live .badge, .card.db .badge {
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
    <a href="/live" class="card db">
      <div class="badge">SQLITE-BACKED + DEVIN TRACE</div>
      <h2>/live</h2>
      <p>
        Full integrated demo: Devin traces COBOL source, pauses for terminal input,
        queries SQLite for overlimit decisions. Shows SQL query log, VSAM state,
        and Devin&rsquo;s real-time trace in one unified view. Run twice to see
        DB state change the routing.
      </p>
    </a>
    <a href="/static" class="card static">
      <div class="badge">NO API KEY NEEDED</div>
      <h2>/static</h2>
      <p>
        Scripted replay: Option 3 (COCRDLIC read-only trace) &amp; Option 8
        (full 3-phase data flow with batch processing, overlimit detection,
        and live VSAM updates). Devin analysis + interactive simulation.
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

    # Initialize SQLite database from ASCII data files
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
            print("  Devin session trace will not work, but /live DB demo will.")
    else:
        print("NOTE: DEVIN_API_KEY not set. Devin session trace disabled.")
        print("      /live SQLite-backed demo works without an API key.")

    class ThreadingHTTPServer(ThreadingMixIn, HTTPServer):
        daemon_threads = True

    server = ThreadingHTTPServer(("0.0.0.0", port), TraceHandler)
    print(f"""
+==============================================================+
|  Devin Live COBOL Flow Trace Server (SQLite-Backed)          |
|  ----------------------------------------------------------- |
|                                                              |
|  Routes:                                                     |
|    http://localhost:{port}          Landing page               |
|    http://localhost:{port}/live     SQLite-backed live demo    |
|    http://localhost:{port}/static   Pre-recorded replay        |
|                                                              |
|  API Endpoints:                                              |
|    GET  /api/db/state       Full database state              |
|    POST /api/db/transaction Add a transaction                |
|    POST /api/db/batch       Run batch processing             |
|    POST /api/db/reset       Reset DB to initial state        |
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
