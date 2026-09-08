#!/usr/bin/env python3
"""
WebRanger Mobile MCP Bridge for Termux & External AI Agents (omp, Claude Code, Cursor)
Zero-dependency Python 3 script connecting external command-line agents to the WebRanger
browser running in the background on Android.

Usage:
  # 1. Start as stdio MCP bridge for Claude Code:
  # In ~/.claude/mcp.json:
  # { "mcpServers": { "webranger": { "command": "python3", "args": ["/path/to/webranger_mcp_bridge.py"] } } }
  # Or: claude mcp add webranger python3 /path/to/webranger_mcp_bridge.py

  # 2. Check browser status from Termux:
  python3 webranger_mcp_bridge.py --status

  # 3. Direct CLI navigation from Termux:
  python3 webranger_mcp_bridge.py --navigate "https://en.wikipedia.org"

  # 4. Extract current page DOM snapshot:
  python3 webranger_mcp_bridge.py --dom

  # 5. Capture screenshot to file:
  python3 webranger_mcp_bridge.py --screenshot page.png

  # 6. Execute direct MCP toolcall:
  python3 webranger_mcp_bridge.py --call chrome_click_element '{"element_id": 2}'
"""

import sys
import os
import json
import urllib.request
import urllib.error
import argparse

DEFAULT_HOST = os.environ.get("WEBRANGER_HOST", "127.0.0.1")
DEFAULT_PORT = int(os.environ.get("WEBRANGER_PORT", "8765"))
BASE_URL = f"http://{DEFAULT_HOST}:{DEFAULT_PORT}"

def post_mcp_request(payload: dict) -> dict:
    url = f"{BASE_URL}/mcp"
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json; charset=utf-8"}
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            res_data = response.read().decode("utf-8")
            return json.loads(res_data)
    except urllib.error.URLError as e:
        sys.stderr.write(f"[WebRanger Bridge Error] Could not connect to WebRanger at {BASE_URL}: {e}\n")
        sys.stderr.write("Make sure WebRanger is running on your device with 'Embedded MCP Server' enabled in Settings.\n")
        return {
            "jsonrpc": "2.0",
            "id": payload.get("id", 1),
            "error": {"code": -32000, "message": f"Connection failed to {BASE_URL}: {e}"}
        }

def get_status() -> dict:
    url = f"{BASE_URL}/status"
    req = urllib.request.Request(url)
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            return json.loads(response.read().decode("utf-8"))
    except Exception as e:
        return {"error": str(e), "server": "unreachable", "url": BASE_URL}

def download_screenshot(filepath: str) -> bool:
    url = f"{BASE_URL}/screenshot"
    try:
        urllib.request.urlretrieve(url, filepath)
        return True
    except Exception as e:
        sys.stderr.write(f"Failed to capture screenshot: {e}\n")
        return False

def run_stdio_mcp_bridge():
    """
    Standard input/output JSON-RPC loop for Claude Code and MCP clients.
    """
    sys.stderr.write(f"[WebRanger Bridge] Starting stdio MCP loop connected to {BASE_URL}\n")
    sys.stderr.flush()

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            req_json = json.loads(line)
            resp_json = post_mcp_request(req_json)
            sys.stdout.write(json.dumps(resp_json) + "\n")
            sys.stdout.flush()
        except json.JSONDecodeError:
            err = {"jsonrpc": "2.0", "id": None, "error": {"code": -32700, "message": "Parse error"}}
            sys.stdout.write(json.dumps(err) + "\n")
            sys.stdout.flush()
        except Exception as e:
            err = {"jsonrpc": "2.0", "id": None, "error": {"code": -32603, "message": str(e)}}
            sys.stdout.write(json.dumps(err) + "\n")
            sys.stdout.flush()

def main():
    parser = argparse.ArgumentParser(description="WebRanger MCP Bridge for Termux & External AI")
    parser.add_argument("--status", action="store_true", help="Print WebRanger background status")
    parser.add_argument("--navigate", type=str, help="Navigate active browser to URL")
    parser.add_argument("--dom", action="store_true", help="Print latest DOM snapshot and interactive elements")
    parser.add_argument("--screenshot", type=str, nargs="?", const="webranger_screen.png", help="Capture screenshot to file")
    parser.add_argument("--tabs", action="store_true", help="List open browser tabs")
    parser.add_argument("--call", nargs=2, metavar=("TOOL", "ARGS_JSON"), help="Call MCP tool with JSON arguments")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help="Server port (default: 8765)")

    args = parser.parse_args()

    global BASE_URL
    BASE_URL = f"http://{DEFAULT_HOST}:{args.port}"

    if args.status:
        st = get_status()
        print(json.dumps(st, indent=2))
        return

    if args.tabs:
        resp = post_mcp_request({"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {"name": "chrome_list_tabs", "arguments": {}}})
        print(json.dumps(resp, indent=2))
        return

    if args.navigate:
        resp = post_mcp_request({"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {"name": "chrome_navigate", "arguments": {"url": args.navigate}}})
        print(json.dumps(resp, indent=2))
        return

    if args.dom:
        resp = post_mcp_request({"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {"name": "chrome_get_dom_snapshot", "arguments": {"viewport_only": False}}})
        try:
            content = resp.get("result", {}).get("content", [{}])[0].get("text", "")
            print(content)
        except Exception:
            print(json.dumps(resp, indent=2))
        return

    if args.screenshot:
        out_file = args.screenshot
        if download_screenshot(out_file):
            print(f"Screenshot saved to {out_file}")
        return

    if args.call:
        tool_name, raw_args = args.call
        try:
            parsed_args = json.loads(raw_args)
        except Exception as e:
            sys.stderr.write(f"Invalid JSON args: {e}\n")
            sys.exit(1)
        resp = post_mcp_request({"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {"name": tool_name, "arguments": parsed_args}})
        print(json.dumps(resp, indent=2))
        return

    # Default mode: stdio bridge
    run_stdio_mcp_bridge()

if __name__ == "__main__":
    main()
