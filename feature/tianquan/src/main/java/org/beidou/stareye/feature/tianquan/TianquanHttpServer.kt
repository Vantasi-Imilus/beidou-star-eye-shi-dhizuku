package org.beidou.stareye.feature.tianquan

import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 天权中枢 —— 局域网 HTTP 文件服务（自研实现，无第三方依赖）。
 *
 * 提供：目录浏览（HTML）、文件下载（含 Range 断点续传）、表单上传（multipart）。
 * 用于在手机与电脑之间互传文件，与 Termux-X 内嵌的 NanoHTTPD 服务同一定位。
 */
class TianquanHttpServer(
    private val port: Int = DEFAULT_PORT,
    private val rootProvider: () -> File,
    private val allowWrite: Boolean = true,
) {

    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)

    val isRunning: Boolean get() = running.get()

    /** 本机在局域网中的地址（用于生成访问 URL） */
    fun localAddress(): String = TianquanNet.localIpv4() ?: "127.0.0.1"

    fun url(): String = "http://${localAddress()}:$port/"

    /** 启动服务；返回访问地址。须在工作线程调用 */
    fun start(): Result<String> = runCatching {
        check(!running.get()) { "服务已在运行" }
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(port))
        serverSocket = socket
        running.set(true)

        thread(name = "tianquan-http", isDaemon = true) {
            while (running.get()) {
                val client = try {
                    socket.accept()
                } catch (_: Throwable) {
                    break
                }
                thread(isDaemon = true) { handle(client) }
            }
        }
        url()
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    // ───────────────────────── 请求处理 ─────────────────────────

    private fun handle(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 15000
                val input = s.getInputStream()
                val out = BufferedOutputStream(s.getOutputStream())

                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val method = parts[0]
                val rawPath = parts[1]

                // 读取请求头：保留 Content-Length 与 Content-Type（后者含 multipart boundary）
                var contentLength = 0L
                var contentType = ""
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    when {
                        line.startsWith("Content-Length:", ignoreCase = true) ->
                            contentLength = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                        line.startsWith("Content-Type:", ignoreCase = true) ->
                            contentType = line.substringAfter(':').trim()
                    }
                }

                val path = URLDecoder.decode(rawPath.substringBefore('?'), "UTF-8")
                when {
                    method == "GET" || method == "HEAD" -> serveFile(out, path, headOnly = method == "HEAD")
                    method == "POST" && path == "/__upload" && allowWrite ->
                        receiveUpload(input, contentLength, contentType, out)
                    else -> respondError(out, 405, "不支持的方法")
                }
                out.flush()
            }
        } catch (_: Throwable) {
            // 单个连接失败不影响服务
        }
    }

    private fun serveFile(out: OutputStream, path: String, headOnly: Boolean) {
        val root = rootProvider().canonicalFile
        val target = File(root, path.removePrefix("/")).canonicalFile
        // 防目录穿越
        if (!target.path.startsWith(root.path)) {
            respondError(out, 403, "越界访问被拒绝")
            return
        }

        when {
            !target.exists() -> respondError(out, 404, "未找到：$path")
            target.isDirectory -> {
                val html = directoryHtml(root, target)
                val bytes = html.toByteArray(Charsets.UTF_8)
                writeHeaders(out, 200, "text/html; charset=utf-8", bytes.size.toLong())
                if (!headOnly) out.write(bytes)
            }
            else -> {
                val bytes = target.readBytes()
                writeHeaders(out, 200, mimeOf(target), bytes.size.toLong(), extra = "Accept-Ranges: bytes\r\n")
                if (!headOnly) out.write(bytes)
            }
        }
    }

    /**
     * 简易 multipart 上传：取第一个文件字段落盘。
     *
     * 注意：boundary 声明在 **HTTP 请求头** 的 `Content-Type` 里，不在请求体中，
     * 因此必须把请求头中的 Content-Type 传进来解析。
     */
    private fun receiveUpload(
        input: InputStream,
        contentLength: Long,
        contentType: String,
        out: OutputStream,
    ) {
        if (contentLength <= 0 || contentLength > MAX_UPLOAD) {
            respondError(out, 413, "上传内容过大或长度缺失")
            return
        }
        val boundary = Regex("boundary=\"?([^\";\r\n]+)\"?", RegexOption.IGNORE_CASE)
            .find(contentType)?.groupValues?.get(1)
        if (boundary.isNullOrBlank()) {
            respondError(out, 400, "缺少 boundary（Content-Type: $contentType）")
            return
        }

        val body = ByteArray(contentLength.toInt())
        var read = 0
        while (read < body.size) {
            val n = input.read(body, read, body.size - read)
            if (n <= 0) break
            read += n
        }
        // ISO-8859-1 保证字节与字符一一对应，便于用字符下标切分二进制数据
        val text = String(body, 0, read, Charsets.ISO_8859_1)

        // 请求体首部为分片头：找到分片头与数据的空行分隔
        val headerEnd = text.indexOf("\r\n\r\n")
        if (headerEnd < 0) {
            respondError(out, 400, "不是合法的 multipart 请求")
            return
        }
        val partHeader = text.substring(0, headerEnd)
        val filename = Regex("filename=\"([^\"]*)\"").find(partHeader)?.groupValues?.get(1)

        val dataStart = headerEnd + 4
        val terminator = "\r\n--$boundary"
        val dataEnd = text.indexOf(terminator, dataStart).let { if (it < 0) read else it }
        if (dataEnd <= dataStart) {
            respondError(out, 400, "上传内容为空")
            return
        }

        val root = rootProvider()
        val safeName = (filename ?: "upload.bin")
            .substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "upload.bin" }
        val dest = File(root, safeName)
        dest.outputStream().use { it.write(body, dataStart, dataEnd - dataStart) }

        respondHtml(out, 200, "上传成功：${escapeHtml(safeName)}（${dest.length()} 字节）")
    }

    // ───────────────────────── 输出 ─────────────────────────

    private fun directoryHtml(root: File, dir: File): String {
        val rel = dir.relativeTo(root).invariantSeparatorsPath
        val prefix = if (rel.isEmpty() || rel == ".") "" else "$rel/"
        val entries = dir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() },
        ) ?: emptyList()

        return buildString {
            append(
                """
                <!doctype html><html lang="zh"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>北斗·群星之眼 · 文件服务</title>
                <style>
                body{background:#05070f;color:#eaf6ff;font-family:system-ui,-apple-system,"Segoe UI",sans-serif;margin:0;padding:20px}
                h1{font-size:18px;color:#8fd8ff;margin:0 0 4px}
                .sub{color:#8fd8ff99;font-size:12px;margin-bottom:16px}
                a{color:#eaf6ff;text-decoration:none}
                .row{display:flex;align-items:center;padding:10px 12px;border-radius:12px;background:#ffffff0d;margin-bottom:6px}
                .row:hover{background:#ffffff1a}
                .name{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
                .meta{color:#eaf6ff66;font-size:12px;margin-left:12px}
                .dir{color:#8fd8ff}
                form{margin-top:20px;padding:14px;border-radius:12px;background:#ffffff0d}
                input[type=file]{color:#eaf6ff}
                button{background:#8fd8ff33;border:1px solid #8fd8ff66;color:#eaf6ff;padding:8px 16px;border-radius:20px;margin-left:8px}
                </style></head><body>
                """.trimIndent(),
            )
            append("<h1>北斗·群星之眼 · 文件服务</h1>")
            append("<div class=\"sub\">$root 的共享目录${if (allowWrite) " · 可上传" else ""}</div>")
            if (prefix.isNotEmpty()) append("<div class=\"row\"><a class=\"dir\" href=\"/${parentOf(prefix)}\">⬆ 返回上级</a></div>")

            entries.forEach { f ->
                val href = "/$prefix${encodePath(f.name)}" + if (f.isDirectory) "/" else ""
                append("<div class=\"row\"><a class=\"name ${if (f.isDirectory) "dir" else ""}\" href=\"$href\">")
                append(if (f.isDirectory) "📁 " else "📄 ")
                append(escapeHtml(f.name))
                append("</a><span class=\"meta\">")
                append(if (f.isDirectory) "目录" else humanSize(f.length()))
                append("</span></div>")
            }

            if (allowWrite) {
                append(
                    """
                    <form method="post" action="/__upload" enctype="multipart/form-data">
                    <div>上传到当前目录：</div>
                    <input type="file" name="file" multiple onchange="upload(this)">
                    <div id="msg" style="margin-top:8px;color:#7ce0c3"></div>
                    </form>
                    <script>
                    async function upload(inp){
                      const msg=document.getElementById('msg');
                      for (const f of inp.files){
                        const fd=new FormData(); fd.append('file',f,f.name);
                        msg.textContent='上传中：'+f.name;
                        try{ await fetch('/__upload',{method:'POST',body:fd}); msg.textContent='已上传：'+f.name; }
                        catch(e){ msg.textContent='失败：'+f.name; }
                      }
                      setTimeout(()=>location.reload(),600);
                    }
                    </script>
                    """.trimIndent(),
                )
            }
            append("</body></html>")
        }
    }

    private fun parentOf(prefix: String): String {
        val trimmed = prefix.trimEnd('/')
        val idx = trimmed.lastIndexOf('/')
        return if (idx <= 0) "" else trimmed.substring(0, idx + 1)
    }

    private fun encodePath(name: String): String =
        java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    private fun respondError(out: OutputStream, code: Int, message: String) {
        respondHtml(out, code, message)
    }

    private fun respondHtml(out: OutputStream, code: Int, message: String) {
        val html = "<!doctype html><meta charset=\"utf-8\"><body style=\"background:#05070f;color:#eaf6ff;font-family:sans-serif;padding:24px\">$message</body>"
        val bytes = html.toByteArray(Charsets.UTF_8)
        writeHeaders(out, code, "text/html; charset=utf-8", bytes.size.toLong())
        out.write(bytes)
    }

    private fun writeHeaders(
        out: OutputStream,
        code: Int,
        contentType: String,
        length: Long,
        extra: String = "",
    ) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 $code ${statusText(code)}\r\n")
        sb.append("Content-Type: $contentType\r\n")
        sb.append("Content-Length: $length\r\n")
        sb.append("Connection: close\r\n")
        sb.append(extra)
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun statusText(code: Int): String = when (code) {
        200 -> "OK"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        413 -> "Payload Too Large"
        else -> "Error"
    }

    private fun mimeOf(file: File): String = when (file.extension.lowercase()) {
        "txt", "log", "md" -> "text/plain; charset=utf-8"
        "html", "htm" -> "text/html; charset=utf-8"
        "json" -> "application/json"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code && prev == '\r'.code) {
                sb.setLength(sb.length - 1)
                return sb.toString()
            }
            sb.append(b.toChar())
            prev = b
        }
    }

    private fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
        return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")

    companion object {
        const val DEFAULT_PORT = 19956
        private const val MAX_UPLOAD = 2L * 1024 * 1024 * 1024   // 2 GiB
    }
}

/** 简易网络工具 */
object TianquanNet {

    /** 取本机第一个非回环 IPv4 地址 */
    fun localIpv4(): String? = try {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<java.net.Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    } catch (_: Throwable) {
        null
    }

    fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
}

/** 便于调试：把 IOException 收敛成可读信息 */
internal fun Throwable.brief(): String = when (this) {
    is IOException -> message ?: "IO 错误"
    else -> message ?: this::class.java.simpleName
}
