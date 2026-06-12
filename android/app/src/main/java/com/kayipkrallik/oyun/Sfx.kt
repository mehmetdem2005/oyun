// Sfx.kt — Chiptune sentez. Reçeteler KKAudioSubsystem.cpp / Kayıp Orman SFX tablosu BİREBİR.
// 32 kHz, kare/üçgen/testere + alçak geçirenli gürültü; üstel sönüm -> 0.001.
package com.kayipkrallik.oyun

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

object Sfx {
    const val SR = 32000
    private const val SINE = 0; private const val SQUARE = 1
    private const val TRIANGLE = 2; private const val SAW = 3

    private val cache = HashMap<String, ShortArray>()
    private val active = ArrayList<Pair<Long, String>>() // bitiş zamanı(ms), kategori
    private val reaper = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "sfx").apply { isDaemon = true } }
    var master = 0.5f

    private class Mixer {
        var buf = FloatArray(0)
        private val rnd = java.util.Random()

        private fun ensure(n: Int) { if (buf.size < n) buf = buf.copyOf(n) }

        // KO tone(): frekans üstel rampa (f1>0 ise), kazanç üstel sönüm -> 0.001
        fun tone(f0in: Float, f1in: Float, dur: Float, wave: Int, vol: Float, at: Float = 0f) {
            val n = (dur * SR).toInt(); val off = (at * SR).toInt()
            ensure(off + n)
            val f0 = max(1f, f0in); val ramp = f1in > 0f; val f1 = max(1f, f1in)
            var ph = 0.0
            for (i in 0 until n) {
                val t = i.toFloat() / n
                val f = if (ramp) f0 * (f1 / f0).pow(t) else f0
                ph += 2.0 * PI * f / SR
                val s = when (wave) {
                    SQUARE -> if (sin(ph) >= 0.0) 1f else -1f
                    TRIANGLE -> (2.0 / PI * asin(sin(ph))).toFloat()
                    SAW -> (2.0 * ((ph / (2.0 * PI)) % 1.0) - 1.0).toFloat()
                    else -> sin(ph).toFloat()
                }
                val g = vol * (0.001f / max(0.002f, vol)).pow(t)
                buf[off + i] += s * g
            }
        }

        // KO noiseHit(): rampalı beyaz gürültü + tek kutuplu alçak geçiren
        fun noise(dur: Float, vol: Float, lowpassHz: Float, at: Float = 0f) {
            val n = (dur * SR).toInt(); val off = (at * SR).toInt()
            ensure(off + n)
            val alpha = 1f - exp(-2.0 * PI * lowpassHz / SR).toFloat()
            var y = 0f
            for (i in 0 until n) {
                val x = (rnd.nextFloat() * 2f - 1f) * (1f - i.toFloat() / n)
                y += alpha * (x - y)
                buf[off + i] += y * vol
            }
        }

        fun toPcm(): ShortArray {
            val out = ShortArray(buf.size)
            for (i in buf.indices) out[i] = (buf[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            return out
        }
    }

    private fun render(id: String): ShortArray {
        val m = Mixer()
        when (id) {
            "pickup" -> m.tone(620f, 990f, 0.09f, SINE, 0.12f)
            "swing" -> m.tone(260f, 110f, 0.08f, TRIANGLE, 0.09f)
            "chop" -> { m.noise(0.08f, 0.22f, 1100f); m.tone(170f, 90f, 0.07f, SQUARE, 0.07f) }
            "mine" -> { m.noise(0.06f, 0.26f, 2600f); m.tone(820f, 420f, 0.06f, SQUARE, 0.07f) }
            "hitE" -> m.tone(900f, 300f, 0.07f, SQUARE, 0.10f)
            "hurt" -> { m.tone(160f, 70f, 0.22f, SAW, 0.18f); m.noise(0.12f, 0.18f, 700f) }
            "craft" -> { m.tone(523f, 0f, 0.09f, SQUARE, 0.11f); m.tone(784f, 0f, 0.13f, SQUARE, 0.11f, 0.10f) }
            "eat" -> { m.tone(300f, 380f, 0.06f, SINE, 0.12f); m.tone(340f, 430f, 0.06f, SINE, 0.12f, 0.08f) }
            "place" -> { m.tone(120f, 60f, 0.13f, SINE, 0.20f); m.noise(0.07f, 0.16f, 500f) }
            "enemyDie" -> m.tone(440f, 70f, 0.26f, SAW, 0.12f)
            "playerDie" -> { m.tone(220f, 40f, 0.50f, SAW, 0.16f); m.noise(0.30f, 0.10f, 380f, 0.08f) }
            "quest" -> { m.tone(523f, 0f, 0.10f, SQUARE, 0.10f); m.tone(659f, 0f, 0.10f, SQUARE, 0.10f, 0.10f)
                         m.tone(784f, 0f, 0.10f, SQUARE, 0.10f, 0.20f); m.tone(1047f, 0f, 0.16f, SQUARE, 0.10f, 0.30f) }
            "rustle" -> m.noise(0.10f, 0.14f, 900f)
            "critterDie" -> { m.tone(220f, 120f, 0.12f, SINE, 0.12f); m.noise(0.08f, 0.10f, 600f) }
            "bolt" -> { m.noise(0.08f, 0.16f, 1800f); m.tone(320f, 180f, 0.07f, TRIANGLE, 0.09f) }
            "heartHit" -> { m.tone(95f, 70f, 0.12f, SINE, 0.22f); m.noise(0.05f, 0.20f, 500f) }
            "heartDie" -> { m.tone(160f, 28f, 0.90f, SAW, 0.20f); m.noise(0.50f, 0.15f, 300f, 0.10f) }
            "door" -> { m.noise(0.05f, 0.14f, 800f); m.tone(140f, 90f, 0.10f, SQUARE, 0.08f, 0.02f) }
            else -> m.tone(800f, 0f, 0.04f, SQUARE, 0.06f) // click + bilinmeyen
        }
        return m.toPcm()
    }

    private fun categoryOf(id: String) = when (id) {
        "swing", "chop", "mine", "hitE", "hurt", "enemyDie", "playerDie" -> "Combat"
        "click", "quest", "craft" -> "UI"
        else -> "World"
    }

    // Mix kuralı: Combat<=6, World<=6, UI<=4 eşzamanlı; taşan atılır (KO yaklaşımı).
    @Synchronized
    private fun passesConcurrency(cat: String, durMs: Long): Boolean {
        val now = System.currentTimeMillis()
        active.removeAll { it.first <= now }
        val cap = if (cat == "UI") 4 else 6
        if (active.count { it.second == cat } >= cap) return false
        active.add(Pair(now + durMs, cat))
        return true
    }

    fun play(id: String) {
        try {
            val pcm = synchronized(cache) { cache.getOrPut(id) { render(id) } }
            if (pcm.isEmpty()) return
            val durMs = pcm.size * 1000L / SR
            if (!passesConcurrency(categoryOf(id), durMs)) return

            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SR)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setVolume(master)
            track.play()
            // Kuyruk tek kullanımlık: süre + pay sonra serbest bırak (erken kesilme payı +50ms).
            reaper.schedule({ try { track.release() } catch (_: Exception) {} }, durMs + 120, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            // Ses cihazı yoksa oyun sessiz devam eder — çökmek yok.
        }
    }
}
