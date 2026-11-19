package com.redmesastudios.trivianight

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread
import kotlin.random.Random

private const val Tag = "TriviaNight.Server"
private const val PreferredPort = 8725

// A four-digit code in the QR link, so only phones that can see the TV can join.
fun newPin(random: Random = Random.Default): String = random.nextInt(0, 10_000).toString().padStart(4, '0')

data class HttpRequest(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>)

fun parseRequest(requestLine: String, headerLines: List<String>): HttpRequest? {
    val parts = requestLine.split(" ")
    if (parts.size < 2) return null
    val target = parts[1]
    val path = URLDecoder.decode(target.substringBefore('?'), "UTF-8")
    val query = target.substringAfter('?', "").split('&').filter { '=' in it }.associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }
    val headers = headerLines.filter { ':' in it }.associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
    return HttpRequest(parts[0].uppercase(), path, query, headers)
}

// The small web server phones play through, on the home network, running while a group
// game is open. Phones load one page (PhonePage.kt) and then talk JSON: join, ask what to
// show (about twice a second), answer.
class GroupServer(private val context: Context, private val room: GroupRoom) {
    private var server: ServerSocket? = null

    // The address phones should open, or null if the stick isn't on a network.
    fun start(): String? {
        val socket = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(PreferredPort)) } }
            .getOrElse { ServerSocket(0) }
        server = socket
        thread(name = "group-game", isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { client.use { serve(it) } }
            }
        }
        val address = localAddress(context) ?: return null
        return "http://$address:${socket.localPort}/?pin=${room.pin}"
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun serve(client: Socket) {
        runCatching {
            client.soTimeout = 15_000
            val input = BufferedInputStream(client.getInputStream())
            val requestLine = readHttpLine(input) ?: return
            val headerLines = generateSequence { readHttpLine(input)?.takeIf { it.isNotEmpty() } }.toList()
            val request = parseRequest(requestLine, headerLines) ?: return
            val length = request.headers["content-length"]?.toIntOrNull()?.coerceIn(0, 16_384) ?: 0
            val body = if (length > 0) String(input.readNBytes(length)) else ""
            route(request, body, client.getOutputStream())
        }.onFailure { Log.w(Tag, "Request failed", it) }
    }

    private fun route(request: HttpRequest, body: String, out: OutputStream) {
        if (request.method == "GET" && request.path == "/") {
            return writeHttpResponse(out, 200, "text/html; charset=utf-8", phonePage().toByteArray())
        }
        val json = runCatching { JSONObject(body.ifEmpty { "{}" }) }.getOrElse { JSONObject() }
        val pin = request.query["pin"] ?: json.optString("pin")
        if (pin != room.pin) return writeJson(out, 403, JSONObject().put("error", "wrong code"))
        when {
            request.method == "GET" && request.path == "/api/state" ->
                writeJson(out, 200, room.phoneView(request.query["id"]))
            request.method == "GET" && request.path == "/api/players" -> {
                val seated = room.seatedProfiles()
                val players = Records.state.value.players.map {
                    JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("playing", it.id in seated)
                }
                writeJson(out, 200, JSONObject().put("players", JSONArray(players)))
            }
            request.method == "POST" && request.path == "/api/join" -> {
                // A saved player joins as themselves; a new name becomes a saved player.
                val saved = Records.state.value.player(json.optString("profile").ifEmpty { null })
                    ?: json.optString("name").trim().takeIf { it.isNotEmpty() }?.let { Records.addPlayer(it, json.optInt("color")) }
                val player = if (saved == null) null else room.join(saved.name, saved.color, json.optString("id").ifEmpty { null }, saved.id)
                if (player == null) writeJson(out, 409, JSONObject().put("error", "taken"))
                else writeJson(out, 200, JSONObject().put("id", player.id).put("profile", saved!!.id))
            }
            request.method == "POST" && request.path == "/api/answer" -> {
                val taken = room.answer(json.optString("id"), json.optInt("round"), json.optInt("choice", -1))
                writeJson(out, 200, JSONObject().put("ok", taken))
            }
            else -> writeHttpResponse(out, 404, "text/plain", "Not found".toByteArray())
        }
    }

    private fun writeJson(out: OutputStream, status: Int, json: JSONObject) =
        writeHttpResponse(out, status, "application/json", json.toString().toByteArray())
}

internal fun readHttpLine(input: InputStream): String? {
    val line = StringBuilder()
    while (true) {
        val b = input.read()
        if (b == -1) return if (line.isEmpty()) null else line.toString()
        if (b == '\n'.code) return line.toString().trimEnd('\r')
        line.append(b.toChar())
        if (line.length > 8192) return null
    }
}

internal fun writeHttpResponse(out: OutputStream, status: Int, type: String, body: ByteArray) {
    val reason = mapOf(200 to "OK", 403 to "Forbidden", 404 to "Not Found", 409 to "Conflict")[status] ?: "OK"
    val head = "HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
    out.write(head.toByteArray())
    out.write(body)
    out.flush()
}

// The stick's own address on the home network: its Wi-Fi's (or wired) address, never a
// VPN's. With Tailscale running, the active network is the VPN, whose 100.x address phones
// on the Wi-Fi can't reach.
fun localAddress(context: Context): String? {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    @Suppress("DEPRECATION")
    return connectivity.allNetworks
        .filter { network ->
            connectivity.getNetworkCapabilities(network)?.let {
                !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    (it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            } == true
        }
        .firstNotNullOfOrNull { network ->
            connectivity.getLinkProperties(network)?.linkAddresses?.map { it.address }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }
}
