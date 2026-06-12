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
    const val B_GEN = 10; const val B_MINE = 11; const val B_BARRACKS = 12
    const val B_FARM = 13; const val B_STORE = 15; const val B_TEMPLE = 16
    const val B_WORKSHOP = 17; const val B_MARKET = 18
    const val WORLD_R = 700                            // dünya yarıçapı (karo); çap 5,6 km
    const val B_TOWER = 14; const val B_SMITH = 19
    const val B_ALCHEMY = 20; const val B_TRAP = 21
    const val B_LUMBER = 22; const val B_WIZARD = 23; const val B_HEAL = 24
    const val B_STATUE = 25; const val B_GRANARY = 26
    val B_ORDER = intArrayOf(WALL, WALL_STONE, WALL_KEEP, DOOR, BALLISTA,
        B_MINE, B_BARRACKS, B_FARM, B_STORE, B_TEMPLE, B_WORKSHOP, B_MARKET,
        B_TOWER, B_SMITH, B_ALCHEMY, B_TRAP,
        B_LUMBER, B_WIZARD, B_HEAL, B_STATUE, B_GRANARY)
    // harita/fallback renkleri (RRGGBB) — miniharıta pikselleri de bunu kullanacak
    val B_MAPCOL = mapOf(WALL to 0x8a6a42, WALL_STONE to 0x8f939c, WALL_KEEP to 0xb9bec9,
        DOOR to 0xa07c4e, BALLISTA to 0xc8a050, B_GEN to 0xe6c24a, B_MINE to 0xcfa83a,
        B_BARRACKS to 0xc05848, B_FARM to 0x6faa52, B_STORE to 0x9a7748,
        B_TEMPLE to 0xbcd0e8, B_WORKSHOP to 0xd08a3e, B_MARKET to 0xa46ac0,
        B_TOWER to 0xb4bac4, B_SMITH to 0xb06a2a, B_ALCHEMY to 0x52b8b0, B_TRAP to 0x9c3a3a,
        B_LUMBER to 0x7a5a36, B_WIZARD to 0x7e58c8, B_HEAL to 0xea9ab0,
        B_STATUE to 0xd8c890, B_GRANARY to 0xb08a4e)
    /* ── EŞYALAR: envanterde yaşar; en iyisi otomatik kuşanılır ── */
    val IT_ORDER = arrayOf("club", "ssword", "isword", "shield", "plate", "axe", "pick", "potion")
    val IT_NAME = mapOf("club" to "Sopa", "ssword" to "Taş Kılıç", "isword" to "Altın Kılıç",
        "shield" to "Ahşap Kalkan", "plate" to "Taş Zırh",
        "axe" to "Balta", "pick" to "Kazma", "potion" to "Can İksiri")
    val IT_BLD = mapOf("club" to B_SMITH, "ssword" to B_SMITH, "isword" to B_SMITH,
        "shield" to B_SMITH, "plate" to B_SMITH,
        "axe" to B_WORKSHOP, "pick" to B_WORKSHOP, "potion" to B_ALCHEMY)
    val IT_COST = mapOf(
        "club" to listOf("wood" to 5),
        "ssword" to listOf("wood" to 4, "stone" to 8),
        "isword" to listOf("stone" to 10, "gold" to 25),
        "shield" to listOf("wood" to 8),
        "plate" to listOf("stone" to 12, "gold" to 15),
        "axe" to listOf("wood" to 6, "stone" to 3),
        "pick" to listOf("wood" to 3, "stone" to 6),
        "potion" to listOf("berry" to 6, "gold" to 8))
    val IT_ATK = mapOf("club" to 12f, "ssword" to 22f, "isword" to 36f)
    val IT_DEF = mapOf("shield" to 0.15f, "plate" to 0.30f)
    val B_HP = mapOf(WALL to 100f, DOOR to 60f, BALLISTA to 80f,
        WALL_STONE to 400f, WALL_KEEP to 1200f,
        B_GEN to 600f, B_MINE to 220f, B_BARRACKS to 320f, B_FARM to 150f,
        B_STORE to 260f, B_TEMPLE to 280f, B_WORKSHOP to 300f, B_MARKET to 260f,
        B_TOWER to 90f, B_SMITH to 80f, B_ALCHEMY to 70f, B_TRAP to 40f,
        B_LUMBER to 120f, B_WIZARD to 110f, B_HEAL to 90f, B_STATUE to 60f, B_GRANARY to 100f)                  // plan 22 birebir
    val B_NAME = mapOf(WALL to "Ahşap Palisat", DOOR to "Ahşap Kapı",
        BALLISTA to "Balista", WALL_STONE to "Taş Duvar", WALL_KEEP to "Kale Taşı",
        B_GEN to "Jeneratör", B_MINE to "Altın Madeni", B_BARRACKS to "Kışla",
        B_FARM to "Çiftlik", B_STORE to "Depo", B_TEMPLE to "Tapınak",
        B_WORKSHOP to "Atölye", B_MARKET to "Pazar",
        B_TOWER to "Ok Kulesi", B_SMITH to "Demirci",
        B_ALCHEMY to "Simyahane", B_TRAP to "Diken Tuzağı",
        B_LUMBER to "Kereste Kampı", B_WIZARD to "Büyücü Kulesi", B_HEAL to "Şifahane",
        B_STATUE to "Zafer Heykeli", B_GRANARY to "Ambar")
    val B_COST: Map<Int, List<Pair<String, Int>>> = mapOf(
        WALL to listOf("wood" to 4),
        DOOR to listOf("wood" to 6, "stone" to 1),
        BALLISTA to listOf("wood" to 8, "stone" to 4),
        WALL_STONE to listOf("stone" to 6),
        WALL_KEEP to listOf("stone" to 12, "wood" to 4),
        B_GEN to listOf<Pair<String, Int>>(),
        B_MINE to listOf("wood" to 8, "stone" to 6),
        B_BARRACKS to listOf("wood" to 10, "stone" to 6),
        B_FARM to listOf("wood" to 6),
        B_STORE to listOf("wood" to 8, "stone" to 4),
        B_TEMPLE to listOf("stone" to 8, "wood" to 4),
        B_WORKSHOP to listOf("wood" to 10, "stone" to 8),
        B_MARKET to listOf("wood" to 8, "stone" to 8),
        B_TOWER to listOf("wood" to 6, "stone" to 4),
        B_SMITH to listOf("wood" to 4, "stone" to 6),
        B_ALCHEMY to listOf("wood" to 3, "stone" to 3),
        B_TRAP to listOf("wood" to 3),
        B_LUMBER to listOf("wood" to 8),
        B_WIZARD to listOf("stone" to 10, "gold" to 20),
        B_HEAL to listOf("wood" to 6, "stone" to 4),
        B_STATUE to listOf("stone" to 8, "gold" to 30),
        B_GRANARY to listOf("wood" to 6))
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
    val QCHAT = listOf("Yard\u0131m edin!", "Kalbi koruyun!", "Buraya gelin!", "Harika i\u015f!", "\uD83D\uDCB0 Ticaret?")

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
        val dC = dist(0f, 0f, tx.toFloat(), ty.toFloat())
        if (dC >= K.WORLD_R) return K.T_DEEP             // sınır ötesi: açık okyanus
        var e = fbm(tx * 0.05f, ty * 0.05f, 1)
        if (dC > K.WORLD_R - 48f)                        // kıyıya düşen kara: dünya çeperi
            e -= (dC - (K.WORLD_R - 48f)) / 48f * 0.9f
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

/* ════ v4 YAPI KAYIT DEFTERİ — 5 seviye: can / üretim / yükseltme altını ════
   Eğri: maliyet ×~1.7 · can ×~1.8 · üretim ×~1.45 (docs/V4-PLAN.md) */
class BSpec(val cat: Int, val hp: FloatArray, val rate: FloatArray, val up: IntArray)

val SPEC: Map<Int, BSpec> = mapOf(
    K.WALL to BSpec(1, floatArrayOf(100f, 180f, 324f, 583f, 1049f),
        floatArrayOf(0f, 0f, 0f, 0f, 0f), intArrayOf(40, 70, 120, 205)),
    K.WALL_STONE to BSpec(1, floatArrayOf(400f, 720f, 1296f, 2333f, 4199f),
        floatArrayOf(0f, 0f, 0f, 0f, 0f), intArrayOf(80, 135, 230, 390)),
    K.WALL_KEEP to BSpec(1, floatArrayOf(1200f, 2160f, 3888f, 6998f, 12597f),
        floatArrayOf(0f, 0f, 0f, 0f, 0f), intArrayOf(150, 255, 435, 740)),
    K.DOOR to BSpec(1, floatArrayOf(60f, 108f, 194f, 350f, 630f),
        floatArrayOf(0f, 0f, 0f, 0f, 0f), intArrayOf(40, 70, 120, 205)),
    K.BALLISTA to BSpec(1, floatArrayOf(80f, 144f, 259f, 467f, 840f),
        floatArrayOf(8f, 11f, 14f, 17f, 20f), intArrayOf(100, 170, 290, 495)),
    K.B_GEN to BSpec(0, floatArrayOf(600f, 1080f, 1944f, 3499f, 6298f),
        floatArrayOf(0.5f, 0.75f, 1.1f, 1.6f, 2.2f), intArrayOf(120, 205, 350, 595)),
    K.B_MINE to BSpec(0, floatArrayOf(220f, 396f, 713f, 1283f, 2309f),
        floatArrayOf(0.35f, 0.5f, 0.75f, 1.1f, 1.5f), intArrayOf(90, 155, 265, 450)),
    K.B_FARM to BSpec(0, floatArrayOf(150f, 270f, 486f, 875f, 1575f),
        floatArrayOf(1f, 2f, 3f, 4f, 5f), intArrayOf(60, 100, 170, 290)),
    K.B_STORE to BSpec(0, floatArrayOf(260f, 468f, 842f, 1516f, 2729f),
        floatArrayOf(250f, 500f, 750f, 1000f, 1250f), intArrayOf(70, 120, 205, 350)),
    K.B_BARRACKS to BSpec(2, floatArrayOf(320f, 576f, 1037f, 1866f, 3359f),
        floatArrayOf(1f, 2f, 3f, 4f, 5f), intArrayOf(110, 185, 315, 535)),
    K.B_TEMPLE to BSpec(3, floatArrayOf(280f, 504f, 907f, 1633f, 2939f),
        floatArrayOf(1f, 2f, 3f, 4f, 5f), intArrayOf(90, 155, 265, 450)),
    K.B_WORKSHOP to BSpec(3, floatArrayOf(300f, 540f, 972f, 1750f, 3149f),
        floatArrayOf(4f, 8f, 12f, 16f, 20f), intArrayOf(120, 205, 350, 595)),
    K.B_MARKET to BSpec(3, floatArrayOf(260f, 468f, 842f, 1516f, 2729f),
        floatArrayOf(1f, 2f, 3f, 4f, 5f), intArrayOf(100, 170, 290, 495)),
    K.B_TOWER to BSpec(1, floatArrayOf(90f, 162f, 292f, 525f, 945f),
        floatArrayOf(9f, 13f, 18f, 24f, 31f), intArrayOf(90, 155, 265, 450)),
    K.B_SMITH to BSpec(3, floatArrayOf(80f, 144f, 259f, 467f, 840f),
        floatArrayOf(3f, 6f, 9f, 12f, 15f), intArrayOf(100, 170, 290, 495)),
    K.B_ALCHEMY to BSpec(3, floatArrayOf(70f, 126f, 227f, 408f, 735f),
        floatArrayOf(0.6f, 1.0f, 1.5f, 2.1f, 2.8f), intArrayOf(90, 155, 265, 450)),
    K.B_TRAP to BSpec(1, floatArrayOf(40f, 72f, 130f, 233f, 420f),
        floatArrayOf(8f, 12f, 17f, 23f, 30f), intArrayOf(50, 85, 145, 245)),
    K.B_LUMBER to BSpec(0, floatArrayOf(120f, 216f, 389f, 700f, 1260f),
        floatArrayOf(2f, 3f, 4f, 5f, 7f), intArrayOf(80, 140, 240, 410)),
    K.B_WIZARD to BSpec(1, floatArrayOf(110f, 198f, 356f, 641f, 1154f),
        floatArrayOf(7f, 10f, 14f, 19f, 26f), intArrayOf(120, 205, 350, 595)),
    K.B_HEAL to BSpec(3, floatArrayOf(90f, 162f, 292f, 525f, 945f),
        floatArrayOf(2f, 3f, 4f, 6f, 8f), intArrayOf(90, 155, 265, 450)),
    K.B_STATUE to BSpec(3, floatArrayOf(60f, 108f, 194f, 350f, 630f),
        floatArrayOf(1f, 2f, 3f, 4f, 5f), intArrayOf(150, 255, 435, 740)),
    K.B_GRANARY to BSpec(0, floatArrayOf(100f, 180f, 324f, 583f, 1050f),
        floatArrayOf(1f, 2f, 3f, 4f, 5f), intArrayOf(70, 120, 205, 350)))

fun bHp(t: Int, l: Int): Float = SPEC[t]?.hp?.get(l - 1) ?: K.B_HP.getValue(t)
fun bRate(t: Int, l: Int): Float = SPEC[t]?.rate?.get(l - 1) ?: 0f
fun upCost(t: Int, l2: Int): Int {
    val sp = SPEC[t] ?: return -1
    if (l2 < 2 || l2 > 5) return -1
    return sp.up[l2 - 2]
}
