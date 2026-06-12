// World.kt — Kayıp Orman prosedürel dünya üretiminin BİREBİR portu.
// KKWorldGenSubsystem.cpp ile aynı sabitler: hash/fbm çarpanları, eşikler
// (0.30/0.345/0.375, 0.06 kamp şansı...) determinizm sözleşmesidir — DOKUNMA.
package com.kayipkrallik.oyun

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

enum class Tile { GRASS, DARK, SAND, WATER, DEEP, PATH, CAMP }
enum class Res { NONE, TREE, ROCK, BUSH }

class World(val seed: Int) {
    // null = bu bölgede kamp yok (negatif sonuç da önbelleğe alınır, KO CampNone gibi)
    private val campCache = HashMap<Long, Pair<Int, Int>?>()
    private val tileCache = HashMap<Long, Tile>()
    private val resCache = HashMap<Long, Spec>()

    // nodeKey -> yeniden doğma zamanı (simTime sn). Tembel temizlik: KO alive() ile aynı.
    val harvested = HashMap<Long, Double>()

    data class Spec(val type: Res, val variant: Int, val jx: Double, val jy: Double)

    companion object {
        fun key(tx: Int, ty: Int): Long = (tx.toLong() shl 32) or (ty.toLong() and 0xFFFFFFFFL)
    }

    // JS: imul(x,374761393)+imul(y,668265263)+imul(seed,974711); h=imul(h^(h>>>13),1274126177); h^=h>>>16
    fun hash01(x: Int, y: Int, salt: Int): Double {
        val s = seed + salt
        var h = x * 374761393 + y * 668265263 + s * 974711
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h.toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }

    private fun smooth(t: Double) = t * t * (3.0 - 2.0 * t)
    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    private fun vnoise(x: Double, y: Double, salt: Int): Double {
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        val xf = x - xi; val yf = y - yi
        val a = hash01(xi, yi, salt); val b = hash01(xi + 1, yi, salt)
        val c = hash01(xi, yi + 1, salt); val d = hash01(xi + 1, yi + 1, salt)
        val sx = smooth(xf); val sy = smooth(yf)
        return lerp(lerp(a, b, sx), lerp(c, d, sx), sy)
    }

    private fun fbm(x: Double, y: Double, salt: Int, oct: Int): Double {
        var v = 0.0; var amp = 0.5; var f = 1.0; var tot = 0.0
        for (i in 0 until oct) {
            v += vnoise(x * f, y * f, salt + i * 101) * amp
            tot += amp; amp *= 0.5; f *= 2.0
        }
        return v / tot
    }

    // 28 karoluk bölgelerde kamp; (0,0) bölgesi HER ZAMAN kamp içerir (KO kuralı).
    fun campInfo(rx: Int, ry: Int): Pair<Int, Int>? {
        val k = key(rx, ry)
        if (campCache.containsKey(k)) return campCache[k]
        val has = (rx == 0 && ry == 0) || hash01(rx, ry, 700) < 0.06
        val c = if (!has) null else Pair(
            rx * 28 + 8 + floor(hash01(rx, ry, 701) * 12.0).toInt(),
            ry * 28 + 8 + floor(hash01(rx, ry, 702) * 12.0).toInt())
        campCache[k] = c
        return c
    }

    private fun campDist(tx: Int, ty: Int): Double {
        val c = campInfo(Math.floorDiv(tx, 28), Math.floorDiv(ty, 28)) ?: return 99.0
        val dx = (tx - c.first).toDouble(); val dy = (ty - c.second).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    fun tileAt(tx: Int, ty: Int): Tile {
        val k = key(tx, ty)
        tileCache[k]?.let { return it }
        val t = computeTile(tx, ty)
        if (tileCache.size > 120000) tileCache.clear()
        tileCache[k] = t
        return t
    }

    private fun computeTile(tx: Int, ty: Int): Tile {
        val cd = campDist(tx, ty)
        if (cd <= 3.3) return Tile.CAMP
        if (cd <= 4.6) return Tile.PATH
        val e = fbm(tx * 0.045, ty * 0.045, 0, 4)
        if (e < 0.30) return Tile.DEEP
        if (e < 0.345) return Tile.WATER
        if (e < 0.375) return Tile.SAND
        val p = fbm(tx * 0.018 + 40.0, ty * 0.018 - 40.0, 9, 2)
        if (abs(p - 0.5) < 0.011) return Tile.PATH
        val m = fbm(tx * 0.07 + 100.0, ty * 0.07 + 100.0, 5, 3)
        return if (m > 0.60) Tile.DARK else Tile.GRASS
    }

    fun resourceAt(tx: Int, ty: Int): Spec {
        val k = key(tx, ty)
        resCache[k]?.let { return it }
        val s = computeResource(tx, ty)
        if (resCache.size > 120000) resCache.clear()
        resCache[k] = s
        return s
    }

    private fun computeResource(tx: Int, ty: Int): Spec {
        val t = tileAt(tx, ty)
        val hasCamp = campInfo(Math.floorDiv(tx, 28), Math.floorDiv(ty, 28)) != null
        val nearCamp = hasCamp && campDist(tx, ty) < 7.0
        // KO jitter: (h-0.5)*10px / 32px karo -> oransal sapma
        val jx = (hash01(tx, ty, 41) - 0.5) * (10.0 / 32.0)
        val jy = (hash01(tx, ty, 42) - 0.5) * (8.0 / 32.0)
        if ((t == Tile.GRASS || t == Tile.DARK) && !nearCamp) {
            val r = hash01(tx, ty, 31)
            val f = fbm(tx * 0.06 + 50.0, ty * 0.06 + 50.0, 13, 3)
            if (f > 0.55 && r < 0.16) return Spec(Res.TREE, floor(hash01(tx, ty, 32) * 3.0).toInt(), jx, jy)
            if (r > 0.974) return Spec(Res.ROCK, floor(hash01(tx, ty, 33) * 3.0).toInt(), jx, jy)
            if (t == Tile.GRASS && r > 0.948) return Spec(Res.BUSH, 0, jx, jy)
        } else if (t == Tile.SAND && !nearCamp && hash01(tx, ty, 31) < 0.035) {
            return Spec(Res.ROCK, 2, jx, jy)
        }
        return Spec(Res.NONE, 0, jx, jy)
    }

    fun startCamp(): Pair<Int, Int> = campInfo(0, 0) ?: Pair(8, 8)

    fun isBlocked(t: Tile) = t == Tile.WATER || t == Tile.DEEP

    fun isHarvested(tx: Int, ty: Int, now: Double): Boolean {
        val t = harvested[key(tx, ty)] ?: return false
        return now < t
    }

    fun markHarvested(tx: Int, ty: Int, respawnAt: Double) { harvested[key(tx, ty)] = respawnAt }
}
