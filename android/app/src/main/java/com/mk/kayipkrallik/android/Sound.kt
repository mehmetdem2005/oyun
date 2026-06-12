package com.mk.kayipkrallik.android

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * SES — HTML prototipinin WebAudio reçeteleri, AudioTrack STATIC örneklerine
 * çevrildi. Dosya yok: her efekt ilk çağrıda 22.05 kHz mono PCM16 olarak
 * SENTEZLENİR ve önbelleğe alınır; sonraki çalışlar reloadStaticData+play.
 *
 * Reçete adları çekirdeğin emit() isimleriyle BİREBİR aynıdır — kabuk
 * s.sfx kuyruğundan adı okur, buraya iletir; eşleşme kopmaz.
 */
class Sound {
    private val SR = 22050
    private val cache = HashMap<String, AudioTrack>()
    private var rngS = -0x61c8864680b583ebL    // gürültü için hızlı LCG

    private fun nz(): Float {                            // [-1,1] beyaz gürültü
        rngS = rngS * 6364136223846793005L + 1442695040888963407L
        return ((rngS ushr 33).toInt() and 0xFFFF) / 32768f - 1f
    }

    /** type: 0=sine 1=square 2=saw 3=triangle — f0→f1 üstel kayma, üstel sönüm. */
    private fun tone(buf: FloatArray, type: Int, f0: Float, f1: Float,
                     dur: Float, vol: Float, at: Float) {
        val n = (dur * SR).toInt()
        val o = (at * SR).toInt()
        var ph = 0.0
        val r = if (f1 > 0f) Math.pow((f1 / f0).toDouble(), 1.0 / n) else 1.0
        var f = f0.toDouble()
        var i = 0
        while (i < n && o + i < buf.size) {
            val env = Math.exp(-4.2 * i / n).toFloat()   // WebAudio exponentialRamp hissi
            val t = ph % 1.0
            val s = when (type) {
                0 -> Math.sin(t * 6.283185307179586)
                1 -> if (t < 0.5) 1.0 else -1.0
                2 -> t * 2.0 - 1.0
                else -> if (t < 0.5) t * 4.0 - 1.0 else 3.0 - t * 4.0
            }.toFloat()
            buf[o + i] += s * vol * env
            ph += f / SR; f *= r
            i++
        }
    }

    /** Tek kutuplu alçak geçirenle filtrelenmiş, doğrusal sönümlü gürültü vuruşu. */
    private fun noise(buf: FloatArray, dur: Float, vol: Float, lp: Float, at: Float) {
        val n = (dur * SR).toInt()
        val o = (at * SR).toInt()
        val a = (1.0 - Math.exp(-6.283185307179586 * lp / SR)).toFloat()
        var y = 0f
        var i = 0
        while (i < n && o + i < buf.size) {
            y += a * (nz() - y)
            buf[o + i] += y * vol * (1f - i.toFloat() / n)
            i++
        }
    }

    /** HTML SFX tablosunun birebir karşılığı — toplam süreyi döndürür. */
    private fun recipe(name: String, b: FloatArray): Float = when (name) {
        "pickup"     -> { tone(b, 0, 620f, 990f, 0.09f, 0.24f, 0f); 0.12f }
        "swing"      -> { tone(b, 3, 260f, 110f, 0.08f, 0.18f, 0f); 0.11f }
        "chop"       -> { noise(b, 0.08f, 0.42f, 1100f, 0f); tone(b, 1, 170f, 90f, 0.07f, 0.14f, 0f); 0.12f }
        "mine"       -> { noise(b, 0.06f, 0.50f, 2600f, 0f); tone(b, 1, 820f, 420f, 0.06f, 0.14f, 0f); 0.10f }
        "hitE"       -> { tone(b, 1, 900f, 300f, 0.07f, 0.20f, 0f); 0.10f }
        "hurt"       -> { tone(b, 2, 160f, 70f, 0.22f, 0.34f, 0f); noise(b, 0.12f, 0.34f, 700f, 0f); 0.26f }
        "craft"      -> { tone(b, 1, 523f, 0f, 0.09f, 0.22f, 0f); tone(b, 1, 784f, 0f, 0.13f, 0.22f, 0.10f); 0.26f }
        "eat"        -> { tone(b, 0, 300f, 380f, 0.06f, 0.24f, 0f); tone(b, 0, 340f, 430f, 0.06f, 0.24f, 0.08f); 0.17f }
        "place"      -> { tone(b, 0, 120f, 60f, 0.13f, 0.38f, 0f); noise(b, 0.07f, 0.30f, 500f, 0f); 0.16f }
        "enemyDie"   -> { tone(b, 2, 440f, 70f, 0.26f, 0.24f, 0f); 0.30f }
        "quest"      -> { tone(b, 1, 523f, 0f, 0.10f, 0.20f, 0f); tone(b, 1, 659f, 0f, 0.10f, 0.20f, 0.10f)
                          tone(b, 1, 784f, 0f, 0.10f, 0.20f, 0.20f); tone(b, 1, 1047f, 0f, 0.16f, 0.20f, 0.30f); 0.50f }
        "click"      -> { tone(b, 1, 800f, 0f, 0.04f, 0.12f, 0f); 0.06f }
        "rustle"     -> { noise(b, 0.10f, 0.26f, 900f, 0f); 0.12f }
        "door"       -> { tone(b, 1, 140f, 90f, 0.16f, 0.20f, 0f); 0.19f }
        "bolt"       -> { tone(b, 1, 900f, 300f, 0.07f, 0.18f, 0f); noise(b, 0.04f, 0.14f, 3000f, 0f); 0.10f }
        "heartHit"   -> { tone(b, 0, 120f, 70f, 0.20f, 0.36f, 0f); 0.24f }
        "heartDie"   -> { tone(b, 2, 160f, 30f, 0.90f, 0.34f, 0f); noise(b, 0.50f, 0.22f, 500f, 0f); 0.95f }
        "playerDie"  -> { tone(b, 2, 120f, 40f, 0.60f, 0.32f, 0f); 0.65f }
        "critterDie" -> { tone(b, 0, 220f, 120f, 0.12f, 0.24f, 0f); noise(b, 0.10f, 0.16f, 600f, 0f); 0.15f }
        else         -> { tone(b, 1, 800f, 0f, 0.04f, 0.10f, 0f); 0.06f }
    }

    private fun build(name: String): AudioTrack? {
        try {
            val tmp = FloatArray((SR * 1.1f).toInt())     // en uzun reçete < 1 sn
            val dur = recipe(name, tmp)
            val n = Math.min(tmp.size, ((dur + 0.05f) * SR).toInt())
            val pcm = ShortArray(n)
            for (i in 0 until n) {
                var v = tmp[i]
                if (v > 1f) v = 1f; if (v < -1f) v = -1f  // sert kırpma yerine doygunluk şart değil
                pcm[i] = (v * 30000).toInt().toShort()
            }
            val tr = AudioTrack(AudioManager.STREAM_MUSIC, SR,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                n * 2, AudioTrack.MODE_STATIC)
            tr.write(pcm, 0, n)
            return tr
        } catch (e: Exception) { return null }            // ses yoksa oyun sessiz sürer
    }

    fun play(name: String) {
        try {
            var tr = cache[name]
            if (tr == null) { tr = build(name) ?: return; cache[name] = tr }
            tr.stop(); tr.reloadStaticData(); tr.play()
        } catch (e: Exception) { }
    }

    fun release() {
        for (t in cache.values) try { t.release() } catch (e: Exception) { }
        cache.clear()
    }
}
