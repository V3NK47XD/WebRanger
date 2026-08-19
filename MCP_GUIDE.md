# Mobile Chrome MCP (Model Context Protocol) Guide

This document provides a human-readable specification of the **Mobile Chrome MCP Server** tools and functions integrated into the mobile browser. It enables any AI model (Anthropic Claude, OpenAI GPT-4o, Google Gemini, Local Ollama, DeepSeek) to interact with and control the mobile Chromium browser via standardized Model Context Protocol (MCP) JSON-RPC 2.0 tool calls.

---

## 🛠️ MCP Tools Overview

| MCP Tool Name | Description | Key Parameters |
| :--- | :--- | :--- |
| `chrome_get_dom_snapshot` | Extracts the semantic DOM tree and interactive element index `[N]`. | `viewport_only` (bool) |
| `chrome_click_element` | Taps/clicks an interactive element with 3-layer event + native hardware touch fallback. | `element_id` (int) |
| `chrome_type_text` | Types text into input fields, dispatches React setters, and presses Enter/Submits. | `element_id` (int), `text` (string), `press_enter` (bool) |
| `chrome_scroll` | Scrolls the mobile viewport up, down, to the top, or to the bottom. | `direction` (string), `amount` (int) |
| `chrome_navigate` | Navigates the browser to a destination URL. | `url` (string) |
| `chrome_evaluate_script` | Executes arbitrary JavaScript inside the webpage context and returns the result. | `script` (string) |
| `chrome_wait` | Waits for asynchronous DOM mutations, route changes, or dynamic content to settle. | `timeout_ms` (int), `debounce_ms` (int) |
| `chrome_take_screenshot` | Captures a high-resolution visual screenshot image of the active mobile viewport. | *(none)* |
| `chrome_go_back` | Navigates back to the previous page in history. | *(none)* |
| `chrome_finish_task` | Concludes the autonomous ReAct loop and reports the final answer to the user. | `answer` (string), `success` (bool) |

---

## 📖 Detailed Tool Specifications & JSON Schemas

### 1. `chrome_get_dom_snapshot`
Extracts a compressed, semantic accessibility tree of the current page. Each visible interactive element is assigned a unique numeric identifier `[N]` (e.g. `[1]`, `[2]`).

#### Parameters:
- `viewport_only` *(boolean, optional, default: `true`)*: If `true`, only extracts elements currently visible in the active mobile viewport to minimize token usage.

#### JSON Tool Call Request:
```json
{
  "name": "chrome_get_dom_snapshot",
  "arguments": {
    "viewport_only": true
  }
}
```

#### JSON Response Structure:
```json
{
  "content": [
    {
      "type": "text",
      "text": "Page Title: \"Wikipedia, the free encyclopedia\"\nURL: https://en.wikipedia.org\nInteractive & Visible Elements:\n[1] <input type=\"text\"> \"Search Wikipedia\" placeholder=\"Search Wikipedia\" (pos: 20,50 100x35)\n[2] <button type=\"submit\"> \"Search\" (pos: 130,50 70x35)\n[3] <a> \"Featured Article: Chromium\" (pos: 20,120 200x25)"
    }
  ],
  "isError": false
}
```

---

### 2. `chrome_click_element`
Performs an intelligent 3-layer click and touch tap on an element by its numeric badge ID:
1. **Ancestor resolution:** Bubbles up to the nearest clickable container (`<a>`, `<button>`, `[role="button"]`) if a child text span or icon was targeted.
2. **Synthetic Event Pipeline:** Dispatches `PointerEvent('pointerdown')` $\rightarrow$ `TouchEvent` $\rightarrow$ `PointerEvent('pointerup')` $\rightarrow$ `focus()` $\rightarrow$ `MouseEvent('click')` $\rightarrow$ native `.click()`.
3. **Anchor / Form Fallback:** Directly triggers `window.location.href = href` for links and `form.requestSubmit()` for forms.
4. **Native Hardware Tap:** Dispatches real Android `MotionEvent.ACTION_DOWN` / `ACTION_UP` onto the WebView surface at the element's mapped pixel coordinates.

#### Parameters:
- `element_id` *(integer, required)*: The numeric ID of the element from the DOM snapshot to tap (e.g. `2`).

#### JSON Tool Call Request:
```json
{
  "name": "chrome_click_element",
  "arguments": {
    "element_id": 2
  }
}
```

#### JSON Response Structure:
```json
{
  "content": [
    {
      "type": "text",
      "text": "Tapped element #2. Result: {\"success\":true,\"elementId\":2,\"action\":\"click\",\"coords\":{\"x\":165,\"y\":67.5}}"
    }
  ],
  "isError": false,
  "highlightedElementId": 2
}
```

---

### 3. `chrome_type_text`
Enters text into an input field or textarea identified by its element ID. Supports React/Vue controlled component prototype setters and automatic form submission on Enter.

#### Parameters:
- `element_id` *(integer, required)*: The numeric ID of the input field.
- `text` *(string, required)*: The text string to type.
- `clear_first` *(boolean, optional, default: `false`)*: Whether to clear existing content in the input before typing.
- `press_enter` *(boolean, optional, default: `true`)*: Whether to dispatch `Enter` keyboard events and trigger form submission.

#### JSON Tool Call Request:
```json
{
  "name": "chrome_type_text",
  "arguments": {
    "element_id": 1,
    "text": "Chromium mobile browser",
    "clear_first": true,
    "press_enter": true
  }
}
```

#### JSON Response Structure:
```json
{
  "content": [
    {
      "type": "text",
      "text": "Typed into element #1. Result: {\"success\":true,\"elementId\":1,\"action\":\"type\",\"length\":23}"
    }
  ],
  "isError": false,
  "highlightedElementId": 1
}
```

---

### 4. `chrome_scroll`
Scrolls the mobile viewport up, down, to the top, or to the bottom with smooth scrolling.

#### Parameters:
- `direction` *(string, required)*: Must be one of `"up"`, `"down"`, `"top"`, or `"bottom"`.
- `amount` *(integer, optional, default: `600`)*: The number of pixels to scroll.

#### JSON Tool Call Request:
```json
{
  "name": "chrome_scroll",
  "arguments": {
    "direction": "down",
    "amount": 500
  }
}
```

---

### 5. `chrome_navigate`
Navigates the browser directly to a destination web URL.

#### Parameters:
- `url` *(string, required)*: Target web address (e.g. `https://en.wikipedia.org`).

#### JSON Tool Call Request:
```json
{
  "name": "chrome_navigate",
  "arguments": {
    "url": "https://en.wikipedia.org"
  }
}
```

---

### 6. `chrome_evaluate_script`
Executes custom JavaScript inside the active webpage context and returns the serialized evaluation result or exception.

#### Parameters:
- `script` *(string, required)*: JavaScript code snippet to evaluate.

#### JSON Tool Call Request:
```json
{
  "name": "chrome_evaluate_script",
  "arguments": {
    "script": "document.title"
  }
}
```

#### JSON Response Structure:
```json
{
  "content": [
    {
      "type": "text",
      "text": "Script output: \"Wikipedia, the free encyclopedia\""
    }
  ],
  "isError": false
}
```

---

### 7. `chrome_wait`
Waits for asynchronous DOM mutations, single-page application (SPA) routing, or AJAX network requests to settle.

#### Parameters:
- `timeout_ms` *(integer, optional, default: `2500`)*: Maximum time to wait.
- `debounce_ms` *(integer, optional, default: `300`)*: Period of silence with no DOM mutations to declare the page stable.

#### JSON Tool Call Request:
```json
{
  "name": "chrome_wait",
  "arguments": {
    "timeout_ms": 2000,
    "debounce_ms": 300
  }
}
```

---

### 8. `chrome_take_screenshot`
Captures a hardware-accelerated screenshot of the active mobile viewport via Android `PixelCopy`.

#### JSON Tool Call Request:
```json
{
  "name": "chrome_take_screenshot",
  "arguments": {}
}
```

#### JSON Response Structure:
```json
{
  "content": [
    {
      "type": "image",
      "data": "<BASE64_JPEG_DATA>",
      "mimeType": "image/jpeg"
    },
    {
      "type": "text",
      "text": "Screenshot captured (1080x2400px)"
    }
  ],
  "isError": false
}
```

---

### 9. `chrome_go_back`
Navigates back to the previous page in browser history.

#### JSON Tool Call Request:
```json
{
  "name": "chrome_go_back",
  "arguments": {}
}
```

---

### 10. `chrome_finish_task`
Concludes the multi-turn autonomous ReAct loop and presents the final extracted answer or summary to the user.

#### Parameters:
- `answer` *(string, required)*: The final extracted answer, summary, or confirmation message.
- `success` *(boolean, optional, default: `true`)*: Whether the task objective was completed successfully.

#### JSON Tool Call Request:
```json
{
  "name": "chrome_finish_task",
  "arguments": {
    "answer": "Chromium is a free and open-source web browser project, mainly developed and maintained by Google.",
    "success": true
  }
}
```

---

## 🔄 Autonomous Multi-Turn ReAct Flow

```
                  ┌──────────────────────┐
                  │ User Enters Goal     │
                  │ ("Search Wikipedia") │
                  └──────────┬───────────┘
                             │
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │ 1. OBSERVE                                             │
 │    MCP: chrome_get_dom_snapshot                        │
 │    Output: [1] <input> "Search", [2] <button> "Search" │
 └───────────────────────────┬────────────────────────────┘
                             │
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │ 2. REASON (LLM Reasoning Stream)                       │
 │    "I will type 'Chromium' into search input #1"       │
 └───────────────────────────┬────────────────────────────┘
                             │
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │ 3. ACT (MCP Tool Execution)                            │
 │    MCP: chrome_type_text(id=1, text="Chromium")        │
 │    (Executes synthetic typing + native tap + submit)   │
 └───────────────────────────┬────────────────────────────┘
                             │
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │ 4. AUTO-CONTINUE / VERIFY                              │
 │    MCP: chrome_get_dom_snapshot (Results Page)         │
 │    Model inspects new article paragraph #3             │
 └───────────────────────────┬────────────────────────────┘
                             │
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │ 5. FINISH TASK                                         │
 │    MCP: chrome_finish_task(answer="...")               │
 │    Status: COMPLETED (Displays final summary card)     │
 └────────────────────────────────────────────────────────┘
```
