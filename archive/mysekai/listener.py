import os
import re
from datetime import datetime
from fastapi import FastAPI, Request, HTTPException, Header
from fastapi.responses import PlainTextResponse, Response
from config import IN_PORT, OUT_PORT, LOCAL_IP, MYSEKAI_RAW_DIR, GAME_SERVER_MAP

def extract_api_type(url: str) -> str:
    if re.search(r'/mysekai(\?|$)', url):
        return 'mysekai'
    if re.search(r'/suite/', url):
        return 'suite'
    return 'unknown'

def extract_game_server(url: str) -> str:
    domain_match = re.search(r'https?://([^/]+)', url)
    if not domain_match:
        return "unknown"
    
    domain = domain_match.group(1)
    
    for server_domain, server_code in GAME_SERVER_MAP.items():
        if domain == server_domain or domain.endswith('.' + server_domain):
            return server_code
    
    return "unknown"

def generate_filename(api_type: str, original_url: str) -> str:
    # timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    user_id_match = re.search(r'/user/(\d+)', original_url)
    user_id = user_id_match.group(1) if user_id_match else "unknown"
    game_server = extract_game_server(original_url)
    return f"{game_server}_{api_type}_{user_id}.bin"

def create_app(queue=None):
    app = FastAPI(
        title="MySekai Listener",
        description="Asynchronous server for receiving and saving API data",
        version="1.0.0"
    )

    @app.get("/upload.js")
    async def get_upload_js():
        uurl = f"https://{LOCAL_IP}:{OUT_PORT}/upload"
        js_content = r"""
(function () {
    const TARGET = “enenenen”; // 将由 Python 注入
    const CHUNK_SIZE = 1 * 1024 * 1024; // 1MB
    const MAX_RETRY = 3;
    function now() { return new Date().toISOString(); }
    function log(msg){ try { console.log(now() + " [upload] " + msg); } catch(e){} }
    // 取响应体
    let body = $response && $response.body ? $response.body : "";
    if (body == null) body = "";
    // 判断是不是看起来像Base64（注意只是启发式；若你100%确定binary-body-mode=1返回的就是Base64，可直接 isB64 = true）
    const base64Like = /^[A-Za-z0-9+/=\r\n]+$/.test(body) && (body.length % 4 === 0);
    const isB64 = base64Like; // 若你确定就是Base64，可改成 `true`
    // 拆分（无论是不是Base64，都按字符串长度切分；后端按 X-Body-Format 判断是否需要 Base64 解码）
    const total = Math.ceil(body.length / CHUNK_SIZE);
    let index = 0;
    log(`start url=${$request && $request.url} len=${body.length} chunks=${total} isB64=${isB64}`);
    function postChunk(i, attempt) {
        const start = i * CHUNK_SIZE;
        const end = Math.min(start + CHUNK_SIZE, body.length);
        const chunk = body.slice(start, end);
        const headers = {
            "Content-Type": "application/octet-stream",
            "User-Agent": "shadowrocket-upload",
            "X-Original-Url": ($request && $request.url) ? String($request.url) : "",
            "X-Request-Path": ($request && $request.path) ? String($request.path) : "",
            "X-Chunk-Index": String(i),
            "X-Total-Chunks": String(total),
            "X-Body-Format": isB64 ? "base64" : "plain"
        };
        $httpClient.post(
            { url: TARGET, headers, body: chunk },
            function (error, response, data) {
                const code = response && (response.status || response.statusCode);
                if (error) {
                    log(`chunk ${i} attempt ${attempt} error=${(error.message || JSON.stringify(error))}`);
                    if (attempt < MAX_RETRY) {
                        return postChunk(i, attempt + 1);
                    }
                    log(`chunk ${i} failed after ${MAX_RETRY} attempts. abort`);
                    return $done({});
                }
                if (!(code >= 200 && code < 300)) {
                    log(`chunk ${i} attempt ${attempt} resp=${code}, body_len=${data ? data.length : 0}`);
                    if (attempt < MAX_RETRY) {
                        return postChunk(i, attempt + 1);
                    }
                    log(`chunk ${i} non-2xx after ${MAX_RETRY} attempts. abort`);
                    return $done({});
                }
                // 成功
                if (i + 1 < total) {
                    postChunk(i + 1, 1);
                } else {
                    log(`all ${total} chunks uploaded successfully`);
                    $done({});
                }
            }
        );
    }
    if (total === 0) {
        log("empty body, nothing to upload");
        return $done({});
    }
    postChunk(0, 1);
})();
        """.replace("enenenen", uurl)
        
        return Response(
            content=js_content,
            media_type="application/javascript; charset=utf-8",
            headers={
                "Cache-Control": "no-store, no-cache, must-revalidate",
                "Pragma": "no-cache",
                "Expires": "0"
            }
        )

    @app.post("/upload")
    async def upload_data(request: Request, x_original_url: str = Header(None, alias="X-Original-Url")):
        if not x_original_url:
            raise HTTPException(status_code=400, detail="Missing X-Original-Url header")
        
        api_type = extract_api_type(x_original_url)
        filename = generate_filename(api_type, x_original_url)
        os.makedirs(MYSEKAI_RAW_DIR, exist_ok=True)
        filepath = os.path.join(MYSEKAI_RAW_DIR, filename)

        data = await request.body()
        with open(filepath, "wb") as f:
            f.write(data)

        print(f"Saved [{api_type.upper()}]: {filepath}")
        print(f"Source URL: {x_original_url[:100]}{'...' if len(x_original_url) > 100 else ''}")
        print(f"File Size: {len(data)/1024:.2f} KB\n")
        print(f'Current Time: {datetime.now().strftime("%Y:%m:%d %H:%M:%S")}')

        if queue:
            await queue.put(filepath)

        return PlainTextResponse("OK")

    @app.exception_handler(404)
    async def not_found_exception_handler(request: Request, exc: HTTPException):
        return PlainTextResponse("Not Found", status_code=404)
    
    return app


if __name__ == "__main__":
    import uvicorn
    
    print(f"Universal Data Receiver running at http://0.0.0.0:{IN_PORT}")
    print("File naming format: [api_type]_[user].bin\n")
    
    try:
        uvicorn.run(
            app, 
            host="0.0.0.0", 
            port=IN_PORT,
            log_level="info"
        )
    except KeyboardInterrupt:
        print("\nServer stopped by user")