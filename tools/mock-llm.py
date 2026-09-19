"""Mock OpenAI-compatible SSE server for manual E2E testing of Quine.

第一轮返回一个 fs_read 工具调用，第二轮（收到 tool 结果后）流式吐文本。
绑定 0.0.0.0，模拟器通过 10.0.2.2:8123 访问。
"""

import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOOL_PATH = "notes/todo.md"
PORT = 8123


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_GET(self):
        if self.path.endswith("/models"):
            body = json.dumps({"object": "list", "data": [{"id": "mock-model"}]}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_error(404)

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            payload = json.loads(raw or b"{}")
        except Exception:
            payload = {}
        messages = payload.get("messages", [])
        has_tool_result = any(m.get("role") == "tool" for m in messages)

        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()

        def sse(obj):
            self.wfile.write(
                ("data: " + json.dumps(obj, ensure_ascii=False) + "\n\n").encode("utf-8")
            )
            self.wfile.flush()

        if not has_tool_result:
            sse({"choices": [{"index": 0, "delta": {"role": "assistant",
                 "content": "我先看一眼这个文件。\n\n"}, "finish_reason": None}]})
            time.sleep(0.2)
            sse({"choices": [{"index": 0, "delta": {"tool_calls": [{"index": 0, "id": "call_mock_1",
                 "type": "function", "function": {"name": "fs_read", "arguments": ""}}]},
                 "finish_reason": None}]})
            sse({"choices": [{"index": 0, "delta": {"tool_calls": [{"index": 0, "function": {
                 "arguments": json.dumps({"path": TOOL_PATH}, ensure_ascii=False)}}]},
                 "finish_reason": None}]})
            sse({"choices": [{"index": 0, "delta": {}, "finish_reason": "tool_calls"}]})
        else:
            for chunk in ["读到了。", "内容如下：\n\n", "- 第一行\n", "- 第二行\n\n", "要我改点什么吗？"]:
                sse({"choices": [{"index": 0, "delta": {"content": chunk}, "finish_reason": None}]})
                time.sleep(0.2)
            sse({"choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}]})

        sse({"choices": [], "usage": {"prompt_tokens": 128, "completion_tokens": 42,
             "total_tokens": 170}})
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()


if __name__ == "__main__":
    print(f"mock llm listening on 0.0.0.0:{PORT}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
