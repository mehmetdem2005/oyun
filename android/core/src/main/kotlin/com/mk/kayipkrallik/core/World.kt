package com.mk.kayipkrallik.core

/* ════════════════════════════════════════════════════════════════════════
   KAYIP KRALLIK — ÇEKİRDEK (saf Kotlin; Android'e dokunmaz)
   Bu katman tek başına derlenir ve test edilir; kabuk yalnız çizer/çalar.
   Dünya matematiği KO/HTML/Godot/UE ile BİREBİR: aynı tohum = aynı ada.
   ════════════════════════════════════════════════════════════════════════ */

object K {
    const val TS = 32f               // karo boyutu (dünya px)
    const val DAY_LEN = 240f         // tam gün (sn)
    const val SAVE_VERSION = 1

    // Yapı türleri (kayıt uyumu için sabit sayılar)
    const val WALL = 1; const val DOOR = 2; const val BALLISTA = 3
    const val WALL_STONE = 4; const val WALL_KEEP = 5
    val B_ORDER = intArrayOf(WALL, WALL_STONE, WALL_KEEP, DOOR, BALLISTA)
    val B_HP = mapOf(WALL to 100f, DOOR to 60f, BALLISTA to 80f,
        WALL_STONE to 400f, WALL_KEEP to 1200f)                  // plan 22 birebir
    val B_NAME = mapOf(WALL to "Ahşap Palisat", DOOR to "Ahşap Kapı",
        BALLISTA to "Balista", WALL_STONE to "Taş Duvar", WALL_KEEP to "Kale Taşı")
    val B_COST: Map<Int, List<Pair<String, Int>>> = mapOf(
        WALL to listOf("wood" to 4),
        DOOR to listOf("wood" to 6, "stone" to 1),
        BALLISTA to listOf("wood" to 8, "stone" to 4),
        WALL_STONE to listOf("stone" to 6),
        WALL_KEEP to listOf("stone" to 12, "wood" to 4))
    val B_REPAIR = mapOf(WALL to "wood", BALLISTA to "wood",
        WALL_STONE to "stone", WALL_KEEP to "stone")             // kapı hariç: E = aç/kapa
    const val AMMO_START = 8; const val AMMO_CAP = 20; const val AMMO_PER_WOOD = 5

    // Karolar
    const val T_DEEP = 0; const val T_WATER = 1; const val T_SAND = 2
    const val T_GRASS = 3; const val T_CAMP = 4

    // Kaynaklar
    const val R_TREE = 1; const val R_ROCK = 2; const val R_BUSH = 3
    val R_HITS = mapOf(R_TREE to 3, R_ROCK to 4, R_BUSH to 1)
    val R_RESPAWN = mapOf(R_TREE to 90f, R_ROCK to 120f, R_BUSH to 45f)

    // Fauna
    const val CK_RABBIT = 0; const val CK_DEER = 1; const val CK_WOLF = 2
    const val CK_BOAR = 3                               // yaban domuzu: yaralanınca şarj eder

    // Hızlı sohbet (UI sırası = indeks; çekirdek metnin sahibi — kabukta sabit yok)
    val QCHAT = listOf("Yard\u0131m edin!", "Kalbi koruyun!", "Buraya gelin!", "Harika i\u015f!")

    // Ayla durumları
    const val VS_CAGED = 0; const val VS_GO_TREE = 1; const val VS_CHOP = 2
    const val VS_DELIVER = 3; const val VS_HIDE = 4

    val ITEM_TR = mapOf("club" to "sopa", "hide" to "post",
        "wood" to "odun", "stone" to "taş",
        "berry" to "böğürtlen", "meat" to "et", "hide" to "post")
}

/** Deterministik LCG — spawner açıları dahil her şey tohuma bağlı (test edilebilirlik). */
class Rng(seed: Long) {
    private var s = seed
    fun next(): Float { s = s * 6364136223846793005L + 1442695040888963407L
        return (((s ushr 33).toInt() and 0x7fffffff).toFloat()) / 2147483647f }
    fun range(a: Float, b: Float) = a + next() * (b - a)
    fun angle() = next() * 6.2831853f
}

/** Dünya üretimi — hash zinciri imul 374761393 / 668265263 / 974711 (KO imzası). */
class WorldGen(val seed: Int) {
    private fun h32(x: Int, y: Int, s: Int): Int {
        var h = x * 374761393 + y * 668265263 + (s + seed) * 974711
        h = h xor (h ushr 13); h *= 1274126177
        return h xor (h ushr 16)
    }
    fun hash01(x: Int, y: Int, s: Int): Float =
        ((h32(x, y, s).toLong() and 0xffffffffL).toFloat()) / 4294967296f

    private fun vnoise(x: Float, y: Float, s: Int): Float {
        val xi = floorI(x); val yi = floorI(y)
        val xf = x - xi; val yf = y - yi
        val u = xf * xf * (3 - 2 * xf); val v = yf * yf * (3 - 2 * yf)
        val a = hash01(xi, yi, s); val b = hash01(xi + 1, yi, s)
        val c = hash01(xi, yi + 1, s); val d = hash01(xi + 1, yi + 1, s)
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
    }
    fun fbm(x: Float, y: Float, s: Int): Float {
        var acc = 0f; var amp = 0.5f; var f = 1f
        for (i in 0 until 4) { acc += amp * vnoise(x * f, y * f, s + i * 131); amp *= 0.5f; f *= 2f }
        return acc
    }

    fun tileAt(tx: Int, ty: Int): Int {
        val e = fbm(tx * 0.05f, ty * 0.05f, 1)
        if (e < 0.30f) return K.T_DEEP
        if (e < 0.345f) return K.T_WATER
        if (e < 0.375f) return K.T_SAND
        return if (fbm(tx * 0.11f + 99f, ty * 0.11f - 7f, 2) < 0.06f) K.T_CAMP else K.T_GRASS
    }
    fun blocked(t: Int) = t <= K.T_WATER

    private var campX = Int.MIN_VALUE; private var campY = 0
    /** Tohum başına bir kez: spiral arama — KK'nin doğduğu açıklık. */
    fun campTile(): Pair<Int, Int> {
        if (campX != Int.MIN_VALUE) return Pair(campX, campY)
        for (r in 0 until 400) for (i in -r..r) {
            val cands = arrayOf(intArrayOf(i, -r), intArrayOf(i, r), intArrayOf(-r, i), intArrayOf(r, i))
            for (c in cands) {
                val tx = c[0]; val ty = c[1]
                if (tileAt(tx, ty) == K.T_CAMP &&
                    !blocked(tileAt(tx + 1, ty)) && !blocked(tileAt(tx - 1, ty))) {
                    campX = tx; campY = ty; return Pair(tx, ty)
                }
            }
        }
        campX = 0; campY = 0; return Pair(0, 0)
    }

    /** Kaynak özeti: tür/varyant/sapma — yoğunluklar KO birebir (0.085/0.115/0.14). */
    fun resourceAt(tx: Int, ty: Int): Res? {
        if (tileAt(tx, ty) != K.T_GRASS) return null
        val r = hash01(tx, ty, 7)
        val kind = when {
            r < 0.085f -> K.R_TREE
            r < 0.115f -> K.R_ROCK
            r < 0.14f -> K.R_BUSH
            else -> return null
        }
        return Res(kind, (hash01(tx, ty, 8) * 3).toInt(),
            (hash01(tx, ty, 9) - 0.5f) * 12f, (hash01(tx, ty, 10) - 0.5f) * 8f)
    }

    companion object { fun floorI(v: Float): Int { val i = v.toInt(); return if (v < i) i - 1 else i } }
}

data class Res(val kind: Int, val variant: Int, val ox: Float, val oy: Float)

fun floorI(v: Float) = WorldGen.floorI(v)
fun key(tx: Int, ty: Int): Long = (tx.toLong() shl 32) or (ty.toLong() and 0xffffffffL)
fun keyX(k: Long): Int = (k shr 32).toInt()
fun keyY(k: Long): Int = k.toInt()
