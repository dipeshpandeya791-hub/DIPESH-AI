package com.dipesh.ai

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Plain-HTTP client for devices on your own home network (Roku, Tasmota, Shelly, WLED, Home Assistant,
 * IP cameras). The app keeps cleartext traffic off for the internet, so local devices go through this
 * raw-socket client, which refuses any address that is not private/local.
 */
object LanHttp {
    class Response(val code: Int, val body: ByteArray) {
        val text get() = String(body, Charsets.UTF_8)
    }

    fun resolveLocal(host: String): InetAddress {
        val a = InetAddress.getByName(host.trim())
        if (!(a.isSiteLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress))
            throw IllegalArgumentException("$host is not on your local network (only private addresses like 192.168.x.x are allowed here).")
        return a
    }

    fun request(
        method: String, host: String, port: Int, path: String,
        headers: Map<String, String> = emptyMap(), body: String? = null, timeoutMs: Int = 6000
    ): Response {
        val addr = resolveLocal(host)
        Socket().use { s ->
            s.connect(InetSocketAddress(addr, port), timeoutMs)
            s.soTimeout = timeoutMs
            val payload = body?.toByteArray(Charsets.UTF_8)
            val req = StringBuilder()
            req.append("$method ${if (path.startsWith("/")) path else "/$path"} HTTP/1.1\r\n")
            req.append("Host: ${host.trim()}:$port\r\nConnection: close\r\nUser-Agent: DipeshAI\r\nAccept: */*\r\n")
            headers.forEach { (k, v) -> req.append("$k: $v\r\n") }
            if (payload != null) {
                if (headers.keys.none { it.equals("Content-Type", true) }) req.append("Content-Type: application/json\r\n")
                req.append("Content-Length: ${payload.size}\r\n")
            } else if (method != "GET") req.append("Content-Length: 0\r\n")
            req.append("\r\n")
            val out = s.getOutputStream()
            out.write(req.toString().toByteArray(Charsets.ISO_8859_1))
            if (payload != null) out.write(payload)
            out.flush()
            val buf = ByteArrayOutputStream()
            val tmp = ByteArray(8192)
            val input = s.getInputStream()
            try {
                while (buf.size() < 4_000_000) {
                    val n = input.read(tmp); if (n < 0) break
                    buf.write(tmp, 0, n)
                }
            } catch (_: java.net.SocketTimeoutException) { if (buf.size() == 0) throw java.io.IOException("The device did not answer in time.") }
            return parse(buf.toByteArray())
        }
    }

    private fun parse(raw: ByteArray): Response {
        var split = -1
        for (i in 0 until raw.size - 3) if (raw[i] == 13.toByte() && raw[i + 1] == 10.toByte() && raw[i + 2] == 13.toByte() && raw[i + 3] == 10.toByte()) { split = i; break }
        if (split < 0) throw java.io.IOException("The device sent an invalid reply.")
        val head = String(raw, 0, split, Charsets.ISO_8859_1)
        val code = head.lineSequence().first().split(' ').getOrNull(1)?.toIntOrNull() ?: 0
        var body = raw.copyOfRange(split + 4, raw.size)
        if (head.contains("transfer-encoding: chunked", true)) body = dechunk(body)
        return Response(code, body)
    }

    private fun dechunk(b: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < b.size) {
            var j = i
            while (j < b.size - 1 && !(b[j] == 13.toByte() && b[j + 1] == 10.toByte())) j++
            if (j >= b.size - 1) break
            val size = String(b, i, j - i, Charsets.ISO_8859_1).trim().substringBefore(';').toIntOrNull(16) ?: break
            if (size == 0) break
            val start = j + 2
            if (start + size > b.size) { out.write(b, start, b.size - start); break }
            out.write(b, start, size)
            i = start + size + 2
        }
        return out.toByteArray()
    }
}
