package com.mk.kayipkrallik.core

/* ── Varlıklar: kabuk yalnız okur-çizer; tüm karar çekirdekte ── */

class Player {
    var swim = false                                    // su karosunda mı (çekirdek karar verir)
    var x = 0f; var y = 0f
    var hp = 100f; var en = 100f; var hu = 100f
    var alive = true; var respawnT = -1f
    var fx = 1f; var fy = 0f                       // bakış
    var attackT = 0f
    var buildMode = false; var buildSel = K.WALL
    val inv = HashMap<String, Int>()
    var lastByShadow: Shadow? = null; var lastByT = -99f
}

class Shadow(var x: Float, var y: Float) {
    var hp = 30f; var alive = true
    var atkCd = 0f; var scale = 0f; var dieT = -1f
    var ph = 0f
    val stolen = HashMap<String, Int>()            // sırtlanan ganimet (plan: yağma kuralı)
}

class Critter(var kind: Int, var x: Float, var y: Float, var hp: Float) {
    var alive = true; var moving = false
    var wx = 0f; var wy = 0f; var wt = 0f
    var aggro = false; var biteCd = 0f; var ph = 0f
    var chargeT = 0f                                    // domuz şarj sayacı
    var alertT = 0f                                     // ürkek irkilme donması
}

class Villager(var x: Float, var y: Float) {
    var state = K.VS_CAGED
    var hp = 40f
    var carry = 0; var chopCd = 0f; var rescan = 0f
    var tgtX = 0; var tgtY = 0; var hasTgt = false
}

class Minion(var x: Float, var y: Float, var hp: Float, var lvl: Int, val home: Long) {
    var ac = 0f                                          // saldırı bekleme
}

class Build(val t: Int, val tx: Int, val ty: Int) {
    var lvl = 1                                          // v4: 1..5 seviye
    var cd = 0f                                          // üretim sayacı
    val x = tx * K.TS + K.TS / 2
    val y = ty * K.TS + K.TS / 2
    var hp = K.B_HP.getValue(t)
    var open = false
    var ammo = if (t == K.BALLISTA) K.AMMO_START else 0
    var fireCd = 0f; var scan = 0f
    // Kabuğun çizeceği uçan cıvata (isabet uçuş sonunda kesinleşir — UE felsefesi)
    var boltT = -1f; var boltTgt: Shadow? = null
}

class Heart(val tx: Int, val ty: Int) {
    val x = tx * K.TS + K.TS / 2
    val y = ty * K.TS + K.TS / 2
    var hp = 300f; var alive = true
}

class LootBag(var x: Float, var y: Float) {
    val loot = HashMap<String, Int>()
    var ph = 0f
}

/* ── Kabuk köprüleri: ses/yazı/flaş olayları çekirdekten dışarı akar ── */
class Sfx(val name: String, val x: Float, val y: Float)
class Toast(val text: String, val color: Int)     // 0=altın 1=kırmızı 2=beyaz

/* ── Kayıt anlık görüntüsü: kabuk JSON'a çevirir; çekirdek formattan habersiz ── */
class Snapshot {
    var version = K.SAVE_VERSION
    var seed = 0; var t = 0f; var day = 1
    var victory = false; var hunt = 0; var villagerState = 0
    var px = 0f; var py = 0f; var php = 100f; var pen = 100f; var phu = 100f
    var heartHp = 300f; var heartAlive = true
    val inv = HashMap<String, Int>()
    val harvested = ArrayList<LongArray>()         // [key, kalanSaniye*100]
    val builds = ArrayList<IntArray>()             // [t,tx,ty,hp,open,ammo,lvl]
    val bags = ArrayList<Pair<FloatArray, HashMap<String, Int>>>()
}

/* ── Oyun durumu ── */
class GameState(val seed: Int) {
    val gen = WorldGen(seed)
    val rng = Rng(seed.toLong() * 7919 + 17)

    var t = 0f; var day = 1
    var night = false; var victory = false; var heartFellShown = false
    var huntPressure = 0; var villagerState = 0

    val player = Player()
    var heart: Heart
    var villager: Villager? = null
    val shadows = ArrayList<Shadow>()
    val critters = ArrayList<Critter>()
    val bags = ArrayList<LootBag>()
    val builds = HashMap<Long, Build>()
    val minions = ArrayList<Minion>()            // kışla birlikleri (kaydedilmez)
    val harvested = HashMap<Long, Float>()         // key -> yeniden doğum zamanı
    val hitsLeft = HashMap<Long, Int>()            // key -> kalan vuruş

    // sayaçlar
    var waveLeft = 0; var burstT = 0f; var burstGap = 1.2f
    var trickleT = 0f; var faunaT = 3f

    // kabuk olayları
    val sfx = ArrayList<Sfx>()
    val toasts = ArrayList<Toast>()
    var bigText: String? = null; var bigColor = 0
    var flash = 0f
    val dirtyChunks = HashSet<Long>()              // kaynak değişti: kabuk önbelleği tazeler
    val shakeTiles = ArrayList<Long>()             // vuruş sarsıntısı isteği

    init {
        val c = gen.campTile()
        heart = Heart(c.first + 2, c.second)       // kampın iki karo doğusu
        player.x = c.first * K.TS + K.TS / 2
        player.y = c.second * K.TS + K.TS / 2
        spawnVillagerCage(c.first, c.second)
    }

    private fun spawnVillagerCage(cx: Int, cy: Int) {
        if (villagerState == 2) return             // isimli yurttaşlar geri gelmez
        val a = gen.hash01(0, 0, 900) * 6.2831853f
        var tx = cx + Math.round(Math.cos(a.toDouble()) * 7).toInt()
        var ty = cy + Math.round(Math.sin(a.toDouble()) * 7).toInt()
        var i = 0
        while (i < 6 && gen.blocked(gen.tileAt(tx, ty))) {
            tx += Integer.signum(cx - tx); ty += Integer.signum(cy - ty); i++
        }
        val v = Villager(tx * K.TS + K.TS / 2, ty * K.TS + K.TS / 2)
        if (villagerState == 1) {
            v.state = K.VS_GO_TREE
            v.x = (cx + 1) * K.TS + K.TS / 2; v.y = (cy - 1) * K.TS + K.TS / 2
        }
        villager = v
    }

    /* — yardımcılar — */
    fun isHarvested(k: Long): Boolean { val h = harvested[k] ?: return false; return t < h }
    fun buildSolid(tx: Int, ty: Int): Boolean {
        val b = builds[key(tx, ty)] ?: return false
        return b.t != K.DOOR || !b.open
    }
    fun isSolid(tx: Int, ty: Int) = gen.blocked(gen.tileAt(tx, ty)) || buildSolid(tx, ty)
    fun wtX(x: Float) = floorI(x / K.TS)
    fun wtY(y: Float) = floorI(y / K.TS)

    fun emit(name: String, x: Float, y: Float) { sfx.add(Sfx(name, x, y)) }
    val chat = ArrayList<Triple<String, Int, Float>>() // (metin, kim 0=sen/1=Ayla/2=sistem, zaman)
    var nightKills = 0                                  // şafak karnesi: bu gece avlanan gölge
    fun pushChat(text: String, who: Int) {
        chat.add(Triple(text, who, t)); while (chat.size > 12) chat.removeAt(0)
    }
    fun toast(text: String, color: Int) { toasts.add(Toast(text, color)) }
    fun big(text: String, color: Int) { bigText = text; bigColor = color }
    fun markDirty(tx: Int, ty: Int) { dirtyChunks.add(key(floorI(tx / 16f), floorI(ty / 16f))) }
    fun shake(tx: Int, ty: Int) { shakeTiles.add(key(tx, ty)) }

    fun addInv(inv: HashMap<String, Int>, item: String, n: Int) {
        inv[item] = (inv[item] ?: 0) + n
    }
    fun takeInv(inv: HashMap<String, Int>, item: String, n: Int): Boolean {
        val have = inv[item] ?: 0
        if (have < n) return false
        if (have == n) inv.remove(item) else inv[item] = have - n
        return true
    }
    fun afford(t: Int): Boolean {
        for (c in K.B_COST.getValue(t)) if ((player.inv[c.first] ?: 0) < c.second) return false
        return true
    }

    fun spawnBag(x: Float, y: Float, loot: Map<String, Int>) {
        if (loot.isEmpty()) return
        for (b in bags) if (dist(b.x, b.y, x, y) < 40f) {
            for (e in loot) addInv(b.loot, e.key, e.value); return
        }
        val bag = LootBag(x, y); bag.ph = rng.angle()
        for (e in loot) addInv(bag.loot, e.key, e.value)
        bags.add(bag)
    }

    /* — kayıt — */
    fun toSnapshot(): Snapshot {
        val s = Snapshot()
        s.seed = seed; s.t = t; s.day = day
        s.victory = victory; s.hunt = huntPressure; s.villagerState = villagerState
        s.px = player.x; s.py = player.y
        s.php = player.hp; s.pen = player.en; s.phu = player.hu
        s.inv.putAll(player.inv)
        s.heartHp = heart.hp; s.heartAlive = heart.alive
        for (e in harvested) if (t < e.value)
            s.harvested.add(longArrayOf(e.key, ((e.value - t) * 100).toLong()))
        for (b in builds.values)
            s.builds.add(intArrayOf(b.t, b.tx, b.ty, b.hp.toInt(), if (b.open) 1 else 0, b.ammo, b.lvl))
        for (b in bags) s.bags.add(Pair(floatArrayOf(b.x, b.y), HashMap(b.loot)))
        return s
    }

    companion object {
        fun fromSnapshot(s: Snapshot): GameState {
            val g = GameState(s.seed)
            g.t = s.t; g.day = s.day
            g.victory = s.victory; g.huntPressure = s.hunt
            g.villagerState = s.villagerState
            g.player.x = s.px; g.player.y = s.py
            g.player.hp = s.php; g.player.en = s.pen; g.player.hu = s.phu
            g.player.inv.putAll(s.inv)
            g.heart.hp = s.heartHp; g.heart.alive = s.heartAlive
            for (h in s.harvested) g.harvested[h[0]] = g.t + h[1] / 100f
            for (a in s.builds) {
                val b = Build(a[0], a[1], a[2])
                b.hp = a[3].toFloat(); b.open = a[4] == 1; b.ammo = a[5]
                b.lvl = if (a.size > 6) a[6] else 1
                g.builds[key(a[1], a[2])] = b
            }
            for (p in s.bags) g.spawnBag(p.first[0], p.first[1], p.second)
            // Ayla durumunu kayda göre yeniden kur
            g.villager = null
            g.respawnVillagerFromState()
            g.night = g.phase() >= 0.5f
            return g
        }
    }
    internal fun respawnVillagerFromState() {
        val c = gen.campTile(); spawnVillagerCage(c.first, c.second)
    }
    fun phase() = (t % K.DAY_LEN) / K.DAY_LEN
}

fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = ax - bx; val dy = ay - by
    return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
}

/* Sohbet portu (Hexagonal): kabuk yerel adaptör bağlar.
   Global chat = ağ adaptörü (Supabase Realtime) — docs/ROADMAP.md. Çekirdek değişmez. */
interface ChatPort { fun send(text: String, who: Int) }
