package com.cineflix.tv.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder

/**
 * Lightweight LAN HTTP Companion server (port 8765) allowing users to paste their TMDB API key
 * or direct MP4/HLS stream URLs from a phone/PC browser directly onto their Google TV.
 */
class LocalCompanionPairServer(
    private val onCredentialReceived: (String) -> Unit,
    private val onCustomStreamReceived: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    fun getLocalIpAddress(): String {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { nif ->
                if (!nif.isUp || nif.isLoopback) return@forEach
                nif.inetAddresses?.toList()?.forEach { addr ->
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
            "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }

    fun start(scope: CoroutineScope, port: Int = 8765) {
        if (serverSocket != null) return
        acceptJob = scope.launch(Dispatchers.IO) {
            runCatching {
                val ss = ServerSocket(port)
                serverSocket = ss
                while (isActive && !ss.isClosed) {
                    val client = ss.accept()
                    launch {
                        runCatching {
                            client.use { sock ->
                                val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                                val requestLine = reader.readLine() ?: return@use
                                var contentLength = 0
                                while (true) {
                                    val line = reader.readLine() ?: break
                                    if (line.isEmpty()) break
                                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                                    }
                                }

                                val body = if (contentLength > 0) {
                                    val buf = CharArray(contentLength)
                                    var read = 0
                                    while (read < contentLength) {
                                        val r = reader.read(buf, read, contentLength - read)
                                        if (r == -1) break
                                        read += r
                                    }
                                    String(buf, 0, read)
                                } else ""

                                var statusMessage = ""
                                if (requestLine.startsWith("POST")) {
                                    val params = parseFormBody(body)
                                    val tmdbKey = params["tmdbKey"].orEmpty().trim()
                                    val streamUrl = params["streamUrl"].orEmpty().trim()
                                    if (tmdbKey.isNotEmpty()) {
                                        onCredentialReceived(tmdbKey)
                                        statusMessage = "TMDB Credential sent to Google TV!"
                                    }
                                    if (streamUrl.isNotEmpty()) {
                                        onCustomStreamReceived(streamUrl)
                                        statusMessage = "Direct Stream URL sent to Google TV!"
                                    }
                                }

                                val html = buildCompanionHtml(statusMessage)
                                val writer = OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8)
                                writer.write("HTTP/1.1 200 OK\r\n")
                                writer.write("Content-Type: text/html; charset=utf-8\r\n")
                                writer.write("Content-Length: ${html.toByteArray(Charsets.UTF_8).size}\r\n")
                                writer.write("Connection: close\r\n\r\n")
                                writer.write(html)
                                writer.flush()
                            }
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun parseFormBody(body: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        body.split("&").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[k] = v
            }
        }
        return map
    }

    private fun buildCompanionHtml(status: String): String = """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8"/>
          <meta name="viewport" content="width=device-width,initial-scale=1"/>
          <title>CineFlix by MJ — TV Companion</title>
          <style>
            body{margin:0;background:#080808;color:#f4f4f5;font-family:system-ui,-apple-system,sans-serif;display:flex;justify-content:center;padding:32px 16px;}
            .card{max-width:460px;width:100%;background:#111116;border:1px solid rgba(255,255,255,0.12);border-radius:16px;padding:24px;}
            h1{margin:0 0 8px;font-size:20px;color:#fff;}
            p{margin:0 0 20px;font-size:13px;color:#a1a1aa;line-height:1.5;}
            label{display:block;font-size:12px;font-weight:600;color:#d4d4d8;margin-bottom:6px;}
            input{width:100%;box-sizing:border-box;background:#08080b;border:1px solid rgba(255,255,255,0.16);border-radius:10px;padding:12px;color:#fff;font-size:13px;margin-bottom:16px;}
            button{width:100%;background:#e50914;color:#fff;border:0;border-radius:10px;padding:12px;font-weight:700;font-size:14px;cursor:pointer;}
            .ok{background:rgba(16,185,129,0.15);border:1px solid rgba(16,185,129,0.4);color:#6ee7b7;padding:10px 12px;border-radius:10px;font-size:13px;margin-bottom:16px;}
          </style>
        </head>
        <body>
          <div class="card">
            <h1>CineFlix by MJ · TV Remote Input</h1>
            <p>Send your TMDB API Key (v3) / Read Access Token (v4) or a direct .mp4 / .m3u8 stream URL straight to your Google TV screen.</p>
            ${if (status.isNotEmpty()) "<div class=\"ok\">$status</div>" else ""}
            <form method="POST">
              <label for="tmdbKey">TMDB API Key or Read Access Token</label>
              <input id="tmdbKey" name="tmdbKey" placeholder="Paste 32-char API Key or eyJ... token"/>
              <label for="streamUrl">Direct Video Stream URL (Optional .mp4 / .m3u8)</label>
              <input id="streamUrl" name="streamUrl" placeholder="https://.../stream.m3u8"/>
              <button type="submit">Send to Google TV</button>
            </form>
          </div>
        </body>
        </html>
    """.trimIndent()
}
