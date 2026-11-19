package com.redmesastudios.trivianight

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

private const val SimpleTvPort = 8724

// The Game Bridge: Red Mesa Studios' games on a stick share the household's players (and keep
// their own games and asked questions) through SimpleTV, the home screen, which runs all the
// time and passes these records between the household's TVs. The game works the same without
// it; its records then stay on this TV. Each game hands its records to the SimpleTV on the same
// stick and gets back the household's copy. Requests carry the household's sync key
// (BuildConfig.SYNC_KEY, from ~/.config/simpletv/sync_key, the same file SimpleTV is built with).
object GameBridge {
    val enabled: Boolean get() = BuildConfig.SYNC_KEY.isNotEmpty()

    // Sends every collection and returns what SimpleTV holds after merging it in.
    fun exchange(saved: Saved): Saved {
        if (!enabled) throw IOException("no sync key")
        val mine = savedToJson(saved)
        val merged = JSONObject()
        for (collection in listOf(PlayersCollection, GamesCollection, AskedCollection)) {
            merged.put(collection, JSONArray(String(post("/sync/shared/$collection", mine.getJSONArray(collection).toString().toByteArray()))))
        }
        return savedFromJson(merged)
    }

    // Plain HTTP over a socket to this stick's own SimpleTV; it never leaves the device.
    private fun post(path: String, body: ByteArray): ByteArray {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", SimpleTvPort), 2_000)
            socket.soTimeout = 15_000
            val head = "POST $path HTTP/1.1\r\nHost: 127.0.0.1\r\nX-Sync-Key: ${BuildConfig.SYNC_KEY}\r\n" +
                "Content-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().apply { write(head.toByteArray()); write(body); flush() }
            val input = BufferedInputStream(socket.getInputStream())
            val status = readHttpLine(input)?.split(" ")?.getOrNull(1)?.toIntOrNull()
            val headers = generateSequence { readHttpLine(input)?.takeIf { it.isNotEmpty() } }
                .filter { ':' in it }.associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
            if (status != 200) throw IOException("HTTP $status for $path")
            val length = headers["content-length"]?.toIntOrNull() ?: throw IOException("No length for $path")
            return input.readNBytes(length)
        }
    }
}
