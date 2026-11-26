package com.example.engine

import android.webkit.MimeTypeMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HttpServerState(
    val isRunning: Boolean = false,
    val port: Int = 8080,
    val rootPath: String = "/sdcard",
    val readOnly: Boolean = false,
    val url: String = "",
    val logs: List<String> = emptyList(),
    val error: String? = null
)

/**
 * Pure-Kotlin embedded HTTP/1.1 wireless file server for local Wi-Fi PC file sharing.
 * Supports directory browsing, file downloads, and browser file uploads.
 */
class LanHttpServer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val _state = MutableStateFlow(HttpServerState())
    val state: StateFlow<HttpServerState> = _state.asStateFlow()

    fun start(rootPath: String, port: Int = 8080, readOnly: Boolean = false) {
        stop()
        val safePort = port.coerceIn(1024, 65535)
        val rootDir = File(rootPath)
        if (!rootDir.exists() || !rootDir.isDirectory) {
            _state.update { it.copy(error = "Root folder does not exist: $rootPath") }
            return
        }

        acceptJob = scope.launch {
            try {
                val socket = ServerSocket(safePort)
                serverSocket = socket
                val ip = getLocalIpAddress() ?: "127.0.0.1"
                val url = "http://$ip:$safePort"
                _state.value = HttpServerState(
                    isRunning = true,
                    port = safePort,
                    rootPath = rootDir.canonicalPath,
                    readOnly = readOnly,
                    url = url,
                    logs = listOf("${timestamp()} Server started at $url serving ${rootDir.canonicalPath}"),
                    error = null
                )

                while (isActive && !socket.isClosed) {
                    val client = try {
                        socket.accept()
                    } catch (_: Exception) {
                        break
                    }
                    launch { handleClient(client, rootDir.canonicalFile, readOnly) }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isRunning = false,
                        error = "Server error: ${e.message ?: "Port $safePort may be in use"}"
                    )
                }
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        _state.update {
            if (it.isRunning) {
                it.copy(
                    isRunning = false,
                    logs = (listOf("${timestamp()} Server stopped") + it.logs).take(60)
                )
            } else {
                it.copy(isRunning = false)
            }
        }
    }

    private fun handleClient(socket: Socket, rootDir: File, readOnly: Boolean) {
        socket.use { client ->
            client.soTimeout = 15_000
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())

            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val rawTarget = parts[1]

            // Parse headers
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase(Locale.US)] =
                        line.substring(idx + 1).trim()
                }
            }

            val remoteIp = client.inetAddress.hostAddress ?: "unknown"
            val pathPart = rawTarget.substringBefore('?')
            val queryPart = rawTarget.substringAfter('?', "")
            val decodedPath = runCatching { URLDecoder.decode(pathPart, "UTF-8") }.getOrDefault("/")

            addLog("$remoteIp $method $decodedPath")

            if (method == "POST" && decodedPath == "/upload") {
                if (readOnly) {
                    sendText(output, 403, "Forbidden", "Server is in Read-Only mode")
                    return
                }
                val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
                val dirParam = extractQueryParam(queryPart, "dir") ?: "/"
                val fileNameParam = extractQueryParam(queryPart, "name") ?: "upload_${System.currentTimeMillis()}"
                val safeName = fileNameParam.substringAfterLast('/').substringAfterLast('\\')
                val targetDir = resolveSafeFile(rootDir, dirParam)
                if (targetDir == null || !targetDir.isDirectory) {
                    sendText(output, 400, "Bad Request", "Invalid target directory")
                    return
                }
                val destFile = FileSystemEngine.resolveUniqueTarget(targetDir, safeName)
                FileOutputStream(destFile).use { fos ->
                    val buf = ByteArray(16 * 1024)
                    var remaining = contentLength
                    while (remaining > 0) {
                        val toRead = minOf(buf.size.toLong(), remaining).toInt()
                        val read = input.read(buf, 0, toRead)
                        if (read == -1) break
                        fos.write(buf, 0, read)
                        remaining -= read
                    }
                }
                sendText(output, 200, "OK", "Uploaded ${destFile.name}")
                return
            }

            if (method != "GET" && method != "HEAD") {
                sendText(output, 405, "Method Not Allowed", "Only GET/POST supported")
                return
            }

            val targetFile = resolveSafeFile(rootDir, decodedPath)
            if (targetFile == null || !targetFile.exists()) {
                sendText(output, 404, "Not Found", "Path not found: $decodedPath")
                return
            }

            if (targetFile.isDirectory) {
                val html = buildDirectoryHtml(rootDir, targetFile, decodedPath, readOnly)
                val bytes = html.toByteArray(Charsets.UTF_8)
                writeHeaders(output, 200, "OK", "text/html; charset=utf-8", bytes.size.toLong())
                if (method == "GET") output.write(bytes)
                output.flush()
            } else {
                val mime = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(targetFile.extension.lowercase(Locale.ROOT))
                    ?: "application/octet-stream"
                val extra = "Content-Disposition: inline; filename=\"${targetFile.name.replace("\"", "")}\"\r\n"
                writeHeaders(output, 200, "OK", mime, targetFile.length(), extra)
                if (method == "GET") {
                    FileInputStream(targetFile).use { fis ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        while (fis.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                        }
                    }
                }
                output.flush()
            }
        }
    }

    private fun resolveSafeFile(rootDir: File, relativePath: String): File? {
        val cleaned = relativePath.trimStart('/')
        val candidate = if (cleaned.isEmpty()) rootDir else File(rootDir, cleaned)
        val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        val rootCanonical = rootDir.canonicalPath
        if (canonical.path != rootCanonical && !canonical.path.startsWith(rootCanonical + File.separator)) {
            return null
        }
        return canonical
    }

    private fun buildDirectoryHtml(rootDir: File, dir: File, relPath: String, readOnly: Boolean): String {
        val normalized = if (relPath.startsWith("/")) relPath else "/$relPath"
        val items = (dir.listFiles() ?: emptyArray())
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })

        val rows = buildString {
            if (dir.canonicalPath != rootDir.canonicalPath) {
                val parentRel = normalized.trimEnd('/').substringBeforeLast('/', "")
                val parentHref = if (parentRel.isEmpty()) "/" else parentRel
                append("<tr><td><a href=\"$parentHref\">📁 .. (Parent)</a></td><td>—</td><td>—</td></tr>")
            }
            for (f in items) {
                val base = normalized.trimEnd('/')
                val encodedName = URLEncoder.encode(f.name, "UTF-8").replace("+", "%20")
                val href = "$base/$encodedName"
                val icon = if (f.isDirectory) "📁" else "📄"
                val sizeStr = if (f.isDirectory) "Folder" else FileSystemEngine.formatSize(f.length())
                val dateStr = FileSystemEngine.formatDate(f.lastModified())
                append("<tr><td><a href=\"$href\">$icon ${escapeHtml(f.name)}</a></td><td>$sizeStr</td><td>$dateStr</td></tr>")
            }
        }

        val uploadSection = if (!readOnly) {
            """
            <div class="upload-box">
              <input type="file" id="fileInput" />
              <button onclick="uploadFile()">Upload to this folder</button>
              <span id="status"></span>
            </div>
            <script>
              async function uploadFile() {
                const input = document.getElementById('fileInput');
                if (!input.files.length) return;
                const file = input.files[0];
                document.getElementById('status').textContent = 'Uploading...';
                const res = await fetch('/upload?dir=' + encodeURIComponent('${escapeHtml(normalized)}') + '&name=' + encodeURIComponent(file.name), {
                  method: 'POST',
                  body: file
                });
                if (res.ok) { location.reload(); } else { document.getElementById('status').textContent = 'Upload failed'; }
              }
            </script>
            """.trimIndent()
        } else "<p class=\"ro\">Read-Only Mode Enabled</p>"

        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width,initial-scale=1" />
              <title>Tabbed File Explorer — ${escapeHtml(normalized)}</title>
              <style>
                body { font-family: system-ui, -apple-system, sans-serif; background: #0B0F17; color: #E3E8F2; margin: 0; padding: 24px; }
                .wrap { max-width: 960px; margin: 0 auto; }
                h1 { font-size: 20px; color: #7BB1FF; margin-bottom: 4px; }
                .path { font-family: monospace; color: #94A3B8; margin-bottom: 16px; }
                .upload-box { background: #161E2C; padding: 12px 16px; border-radius: 10px; margin-bottom: 16px; display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
                button { background: #0B57D0; color: #fff; border: none; padding: 8px 14px; border-radius: 8px; cursor: pointer; font-weight: 600; }
                table { width: 100%; border-collapse: collapse; background: #111722; border-radius: 12px; overflow: hidden; }
                th, td { text-align: left; padding: 10px 14px; border-bottom: 1px solid #1F2735; }
                th { background: #161E2C; color: #94A3B8; font-size: 12px; text-transform: uppercase; }
                a { color: #5AD5F4; text-decoration: none; }
                a:hover { text-decoration: underline; }
                .ro { color: #F59E0B; font-size: 13px; }
              </style>
            </head>
            <body>
              <div class="wrap">
                <h1>Tabbed File Explorer — Wireless LAN Server</h1>
                <div class="path">${escapeHtml(normalized)}</div>
                $uploadSection
                <table>
                  <thead><tr><th>Name</th><th>Size</th><th>Modified</th></tr></thead>
                  <tbody>$rows</tbody>
                </table>
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun extractQueryParam(query: String, key: String): String? {
        return query.split('&')
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) pair.substring(0, idx) to pair.substring(idx + 1) else null
            }
            .firstOrNull { it.first == key }
            ?.second
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) {
                if (sb.isNotEmpty() && sb[sb.length - 1] == '\r') sb.deleteCharAt(sb.length - 1)
                return sb.toString()
            }
            sb.append(b.toChar())
            if (sb.length > 8192) return sb.toString()
        }
    }

    private fun writeHeaders(
        out: OutputStream,
        code: Int,
        status: String,
        contentType: String,
        contentLength: Long,
        extraHeaders: String = ""
    ) {
        val header = buildString {
            append("HTTP/1.1 $code $status\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: $contentLength\r\n")
            append("Connection: close\r\n")
            append(extraHeaders)
            append("\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
    }

    private fun sendText(out: OutputStream, code: Int, status: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        writeHeaders(out, code, status, "text/plain; charset=utf-8", bytes.size.toLong())
        out.write(bytes)
        out.flush()
    }

    private fun addLog(message: String) {
        _state.update {
            it.copy(logs = (listOf("${timestamp()} $message") + it.logs).take(60))
        }
    }

    private fun timestamp(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun getLocalIpAddress(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress
                        if (ip != null && !ip.startsWith("127.")) return ip
                    }
                }
            }
            null
        }.getOrNull()
    }
}
