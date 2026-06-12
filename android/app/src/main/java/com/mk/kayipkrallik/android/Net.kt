package com.mk.kayipkrallik.android

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue

class Ghost(val name: String, val x: Float, val y: Float)

/* ÇOK OYUNCULU-LITE — Supabase REST: küresel sohbet + paylaşılan dünya
   hayaletleri. Saf Kotlin + HttpURLConnection, sıfır kütüphane.
   Etkinleştirme: app/src/main/assets/net.txt → 1. satır proje URL,
   2. satır anon key. Dosya yoksa oyun tamamen OFFLINE çalışır.
   Şema: tools/supabase/schema.sql */
object Net {
    @Volatile var on = false
    @Volatile var ghosts: Array<Ghost> = arrayOf()
    val chatIn = ConcurrentLinkedQueue<String>()
    private val outChat = ConcurrentLinkedQueue<String>()
    @Volatile private var url = ""
    @Volatile private var key = ""
    @Volatile private var px = 0f
    @Volatile private var py = 0f
    @Volatile private var lastTs = 0L
    private var myId = ""; private var myName = ""
    private var th: Thread? = null

    fun init(ctx: Context) {
        if (th != null) return
        try {
            val br = BufferedReader(InputStreamReader(ctx.getAssets().open("net.txt")))
            url = (br.readLine() ?: "").trim().trimEnd('/')
            key = (br.readLine() ?: "").trim()
            br.close()
        } catch (e: Exception) { return }
        if (url.length < 8 || key.length < 8) return
        val sp = ctx.getSharedPreferences("kk_net", Context.MODE_PRIVATE)
        var id = sp.getString("id", "") ?: ""
        if (id.isEmpty()) {
            id = java.util.UUID.randomUUID().toString()
            sp.edit().putString("id", id).apply()
        }
        myId = id; myName = "Oyuncu-" + id.substring(0, 4)
        on = true; lastTs = System.currentTimeMillis()
        val t = Thread { loop() }; t.isDaemon = true; t.start(); th = t
    }

    fun pos(x: Float, y: Float) { px = x; py = y }
    fun sendChat(text: String) { if (on) outChat.add(text) }

    private fun loop() {
        while (true) {
            try {
                val now = System.currentTimeMillis()
                post("kk_presence", "[{\"id\":\"" + myId + "\",\"name\":\"" + myName +
                    "\",\"x\":" + px.toInt() + ",\"y\":" + py.toInt() + ",\"ts\":" + now + "}]", true)
                var m = outChat.poll()
                while (m != null) {
                    post("kk_chat", "[{\"who\":\"" + myName + "\",\"text\":\"" + esc(m) +
                        "\",\"ts\":" + now + "}]", false)
                    m = outChat.poll()
                }
                parseGhosts(get("kk_presence?select=name,x,y&ts=gt." + (now - 15000) +
                    "&id=neq." + myId + "&limit=12"))
                parseChat(get("kk_chat?select=who,text,ts&ts=gt." + lastTs +
                    "&who=neq." + myName + "&order=ts.asc&limit=10"))
            } catch (e: Exception) { }
            try { Thread.sleep(2500) } catch (e: Exception) { }
        }
    }

    private fun esc(s: String) = s.replace("\\", "").replace("\"", "'")

    private fun post(table: String, body: String, merge: Boolean) {
        val c = URL(url + "/rest/v1/" + table).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; hdr(c)
        c.setRequestProperty("Prefer",
            if (merge) "resolution=merge-duplicates" else "return=minimal")
        c.doOutput = true
        val w = OutputStreamWriter(c.outputStream)
        w.write(body); w.flush(); w.close()
        c.inputStream.close()
        c.disconnect()
    }

    private fun get(q: String): String {
        val c = URL(url + "/rest/v1/" + q).openConnection() as HttpURLConnection
        hdr(c)
        val br = BufferedReader(InputStreamReader(c.inputStream))
        val sb = StringBuilder()
        var l = br.readLine()
        while (l != null) { sb.append(l); l = br.readLine() }
        br.close(); c.disconnect()
        return sb.toString()
    }

    private fun hdr(c: HttpURLConnection) {
        c.setRequestProperty("apikey", key)
        c.setRequestProperty("Authorization", "Bearer " + key)
        c.setRequestProperty("Content-Type", "application/json")
        c.connectTimeout = 4000; c.readTimeout = 4000
    }

    private fun parseGhosts(j: String) {
        val out = ArrayList<Ghost>()
        val rx = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"[^}]*\"x\"\\s*:\\s*(-?\\d+)[^}]*\"y\"\\s*:\\s*(-?\\d+)")
        for (m in rx.findAll(j))
            out.add(Ghost(m.groupValues[1], m.groupValues[2].toFloat(), m.groupValues[3].toFloat()))
        ghosts = out.toTypedArray()
    }

    private fun parseChat(j: String) {
        val rx = Regex("\"who\"\\s*:\\s*\"([^\"]+)\"[^}]*\"text\"\\s*:\\s*\"([^\"]*)\"[^}]*\"ts\"\\s*:\\s*(\\d+)")
        for (m in rx.findAll(j)) {
            chatIn.add("[" + m.groupValues[1] + "] " + m.groupValues[2])
            val t = m.groupValues[3].toLong()
            if (t > lastTs) lastTs = t
        }
    }
}
