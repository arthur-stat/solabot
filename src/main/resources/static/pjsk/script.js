// upload.js
// v2025-11-03T20:19Z fix: replace smart quotes + add error handling
(function () {
    const TARGET = "https://yly.dylancloud.uk:443/upload"; // 将由 Python 注入
    const CHUNK_SIZE = 1 * 1024 * 1024; // 1MB
    const MAX_RETRY = 3;
    function now() { return new Date().toISOString(); }
    function log(msg) {
        try { console.log(now() + " [upload] " + msg); } catch (e) { }
    }
    try {
        // 取响应体
        let body = $response && $response.body ? $response.body : "";
        if (body == null) body = "";
        // 判断是不是看起来像Base64（启发式）
        const base64Like = /^[A-Za-z0-9+/=\r\n]+$/.test(body) && (body.length % 4 === 0);
        const isB64 = base64Like; // 如果你确定就是Base64，可改成 `true`
        // 拆分（无论是不是Base64，都按字符串长度切分；后端按 X-Body-Format 判断是否需要 Base64 解码）
        const total = Math.ceil(body.length / CHUNK_SIZE);
        log(`script-version=v2025-11-03T20:19Z start url=${$request && $request.url} len=${body.length} chunks=${total} isB64=${isB64}`);
        function postChunk(i, attempt) {
            try {
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
                    { url: TARGET, headers: headers, body: chunk },
                    function (error, response, data) {
                        try {
                            const code = response && (response.statusCode || response.status);
                            if (error) {
                                log(`chunk ${i} attempt ${attempt} error=${(error && (error.message || JSON.stringify(error))) || String(error)}`);
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
                            log(`chunk ${i} uploaded (resp=${code})`);
                            if (i + 1 < total) {
                                postChunk(i + 1, 1);
                            } else {
                                log(`all ${total} chunks uploaded successfully`);
                                return $done({});
                            }
                        } catch (innerErr) {
                            log(`chunk ${i} callback exception: ${innerErr && innerErr.message}`);
                            return $done({});
                        }
                    }
                );
            } catch (err) {
                log(`postChunk exception at i=${i} attempt=${attempt} err=${err && err.message}`);
                return $done({});
            }
        }
        if (total === 0) {
            log("empty body, nothing to upload");
            return $done({});
        }
        postChunk(0, 1);
    } catch (e) {
        log("top-level exception: " + (e && (e.message || JSON.stringify(e))));
        try { return $done({}); } catch (_) { }
    }
})();