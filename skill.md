# WebRanger Browser Control & MCP Skill

This document instructs AI agents on how to directly connect to, control, and automate the **WebRanger** mobile Chromium browser running on Android from within Termux (or PRoot). **Do not inspect the codebase**; everything required to interact with the browser is documented below.

---

## 1. Quick Overview

- **App Package**: `com.chromemobile.browser.debug` (or `com.chromemobile.browser`)
- **Main Activity**: `com.chromemobile.browser.MainActivity`
- **MCP Host**: `127.0.0.1` (loopback is shared between Android and Termux)
- **Default MCP Port**: `8765`
- **Supported Transports**:
  - **HTTP JSON-RPC 2.0**: `POST http://127.0.0.1:8765/mcp` (also accepts `/` or `/rpc`)
  - **Server-Sent Events (SSE)**: `GET http://127.0.0.1:8765/sse`
  - **Direct Status Check**: `GET http://127.0.0.1:8765/status`
  - **Direct Android Intent Fallback**: `am start` via Termux

---

## 2. Interaction Modes

### Mode A: WebRanger MCP Server (Primary & Recommended)

When the WebRanger app is running and its embedded MCP server is active, you can perform full browser automation using pre-built project bridge scripts or direct HTTP JSON-RPC calls.

#### Using Pre-built Bridge Scripts (`scripts/`)

The repository includes ready-to-use bridge scripts in Python and Node.js:

```bash
# Check server status & connection
python3 scripts/webranger_mcp_bridge.py --status

# Navigate active tab to any URL
python3 scripts/webranger_mcp_bridge.py --navigate "https://en.wikipedia.org"

# Extract semantic accessibility DOM snapshot (returns numbered elements like [1], [2])
python3 scripts/webranger_mcp_bridge.py --dom

# Capture current viewport screenshot to image file
python3 scripts/webranger_mcp_bridge.py --screenshot page.png

# List all open tabs
python3 scripts/webranger_mcp_bridge.py --tabs

# Execute any arbitrary MCP tool call with JSON arguments
python3 scripts/webranger_mcp_bridge.py --call chrome_click_element '{"element_id": 2}'
python3 scripts/webranger_mcp_bridge.py --call chrome_type_text '{"element_id": 1, "text": "Chromium", "press_enter": true}'
```

Node.js Stdio Bridge (for Claude Code, Cursor, or MCP clients):
```bash
node scripts/webranger_mcp_bridge.js
```

Connecting Claude Code directly via SSE:
```bash
claude mcp add --transport sse webranger http://127.0.0.1:8765/sse
```

---

#### Direct HTTP JSON-RPC 2.0 API

If you prefer sending raw JSON-RPC requests via `curl` or language-level HTTP clients:

**1. Navigate URL (`chrome_navigate`)**
```bash
curl -s -X POST http://127.0.0.1:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "chrome_navigate",
      "arguments": { "url": "https://en.wikipedia.org" }
    }
  }'
```

**2. Extract Semantic DOM Snapshot (`chrome_get_dom_snapshot`)**
Returns the visible interactive elements with assigned numeric IDs `[N]`:
```bash
curl -s -X POST http://127.0.0.1:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "chrome_get_dom_snapshot",
      "arguments": { "viewport_only": true }
    }
  }'
```

**3. Click / Tap an Element (`chrome_click_element`)**
Performs 3-layer touch/click (synthetic event dispatch + native hardware `MotionEvent` fallback):
```bash
curl -s -X POST http://127.0.0.1:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "chrome_click_element",
      "arguments": { "element_id": 1 }
    }
  }'
```

**4. Type Text into an Input Field (`chrome_type_text`)**
```bash
curl -s -X POST http://127.0.0.1:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "tools/call",
    "params": {
      "name": "chrome_type_text",
      "arguments": {
        "element_id": 1,
        "text": "Artificial Intelligence",
        "press_enter": true
      }
    }
  }'
```

**5. Scroll Viewport (`chrome_scroll`)**
Directions: `"down"`, `"up"`, `"top"`, `"bottom"`:
```bash
curl -s -X POST http://127.0.0.1:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 5,
    "method": "tools/call",
    "params": {
      "name": "chrome_scroll",
      "arguments": { "direction": "down", "amount": 600 }
    }
  }'
```

**6. Evaluate JavaScript in Page Context (`chrome_evaluate_script`)**
```bash
curl -s -X POST http://127.0.0.1:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 6,
    "method": "tools/call",
    "params": {
      "name": "chrome_evaluate_script",
      "arguments": { "script": "document.title" }
    }
  }'
```

**7. Wait for DOM / Network Settle (`chrome_wait`)**
```bash
curl -s -X POST http://127.0.0.1:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 7,
    "method": "tools/call",
    "params": {
      "name": "chrome_wait",
      "arguments": { "timeout_ms": 3000, "debounce_ms": 300 }
    }
  }'
```

**8. Take Screenshot (`chrome_take_screenshot` or direct GET)**
```bash
# Via direct GET route:
curl -s http://127.0.0.1:8765/screenshot -o current_screen.png

# Or via MCP tool call (returns Base64 image payload):
curl -s -X POST http://127.0.0.1:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 8,
    "method": "tools/call",
    "params": { "name": "chrome_take_screenshot", "arguments": {} }
  }'
```

---

### Mode B: Direct Android Intent Fallback (CLI Fallback)

If the MCP port `8765` is not listening (e.g. older APK installed without `EmbeddedMcpHttpServer`, or MCP toggled off in settings), use Termux's Android Activity Manager commands to launch or navigate:

**Open or Navigate URL Directly in WebRanger**:
```bash
am start --user 0 -a android.intent.action.VIEW \
  -d "https://en.wikipedia.org" \
  -n com.chromemobile.browser.debug/com.chromemobile.browser.MainActivity
```

**Launch App into Foreground**:
```bash
am start --user 0 -n com.chromemobile.browser.debug/com.chromemobile.browser.MainActivity
```

**Using Termux Open Utility**:
```bash
termux-open-url "https://en.wikipedia.org"
```

---

## 3. Autonomous Agent ReAct Loop

When an AI agent executes autonomous web browsing tasks:

```
┌────────────────────────────────────────────────────────┐
│ 1. OBSERVE                                             │
│    Call: chrome_get_dom_snapshot                       │
│    Output: [1] <input> "Search", [2] <button> "Submit" │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 2. REASON                                              │
│    Select target element [1], craft search text        │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 3. ACT                                                 │
│    Call: chrome_type_text(id=1, text="...", enter=true)│
│    (or chrome_click_element(id=2))                     │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 4. WAIT & VERIFY                                       │
│    Call: chrome_wait(timeout_ms=2500)                  │
│    Call: chrome_get_dom_snapshot                       │
│    Verify resulting page contents                      │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 5. COMPLETE                                            │
│    Call: chrome_finish_task(answer="...", success=true)│
└────────────────────────────────────────────────────────┘
```

---

## 4. Diagnostics & Troubleshooting

| Symptom | Cause | Solution |
| :--- | :--- | :--- |
| `[Errno 111] Connection refused` on `8765` | Server not yet started or running older APK build. | 1. Ensure WebRanger app is open on the phone.<br>2. In WebRanger Settings, verify **"Embedded MCP Server for Termux"** is ON and port is `8765`.<br>3. Use **Mode B (Android Intent)** fallback for navigation. |
| `Permission Denial: startActivityAsUser asks to run as user -2` | `am` in Termux defaulted to current user `-2`. | Always supply `--user 0` to `am start`. |
| Background WebView freezing | Android background optimization paused WebView. | Ensure **"Keep Browser Active in Background"** is enabled in WebRanger Settings (runs foreground service with `WakeLock`). |
