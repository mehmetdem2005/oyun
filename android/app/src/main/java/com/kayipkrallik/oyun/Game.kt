// Game.kt — Kayıp Krallık simülasyon çekirdeği (motorsuz, saf Android port).
// Tüm sayısal sabitler UE kaynağından BİREBİR: can/açlık/enerji, yapı HP, gölge hasarı,
// kalp 300HP, balista menzil/hasar, kuşatma eğrisi, 5. gece büyük kuşatma, av ekolojisi.
// 1 karo = 100 dünya birimi (uu). Otorite tek süreçte (tek oyunculu çekirdek).
package com.kayipkrallik.oyun

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

const val TILE = 100f                 // 1 karo = 100uu (SANAT-YONU 2.3)
const val CYCLE = 240f                // KKTimeOfDaySubsystem: CYCLE = 240 sn
const val WIN_DAY = 10                 // Gün-10 zafer

enum class Build { WALL, DOOR, BALLISTA }
enum class CritterKind { RABBIT, DEER }

open class Entity(var x: Float, var y: Float)

class Tree(x: Float, y: Float, val tx: Int, val ty: Int, val res: Res, val variant: Int) : Entity(x, y) {
    var hits = 3
    var depleted = false
    var shake = 0f
}

class Wall(x: Float, y: Float, val tx: Int, val ty: Int, val type: Build) : Entity(x, y) {
    var hp = when (type) { Build.WALL -> 120f; Build.BALLISTA -> 80f; Build.DOOR -> 60f }
    val maxHp = hp
    var open = false           // yalnız kapı
    var shake = 0f
    // balista durumu
    var fireCd = 0f
    var idleScan = 0f
    var aimAng = 0f
}

class Shadow(x: Float, y: Float, val bornDay: Int) : Entity(x, y) {
    var hp = 30f
    var dying = false
    var deathT = 0f
    var flash = 0f
    var attackCd = 0f
    var carryLoot: HashMap<String, Int>? = null  // yağma: öldürdüğü oyuncunun envanteri
}

class Critter(x: Float, y: Float, val kind: CritterKind) : Entity(x, y) {
    var hp = 10f
    var wanderT = 0f
    var vx = 0f; var vy = 0f
    var hopPhase = 0f
}

class LootBag(x: Float, y: Float, val items: HashMap<String, Int>) : Entity(x, y)

class Bolt(x: Float, y: Float, val tx: Float, val ty: Float) : Entity(x, y) {
    var t = 0f
    val dur = 0.22f
}

class Villager(x: Float, y: Float) : Entity(x, y) {
    var hp = 40f
    var chopCd = 0f
    var carrying = false
    var target: Tree? = null
    var atNight = false
    var dead = false
}

class Game(seed: Int) {
    val world = World(seed)
    val seed = seed

    // --- oyuncu ---
    var px = 0f; var py = 0f
    var health = 100f; var maxHealth = 100f
    var stamina = 100f; var maxStamina = 100f
    var hunger = 100f; var maxHunger = 100f
    var facing = 0f
    val inv = LinkedHashMap<String, Int>()   // item -> adet (hotbar sırası)
    var dead = false
    var respawnT = 0f
    var attackT = 0f
    var flash = 0f

    // --- zaman ---
    var timeSec = 0f
    var day = 1
    var wasNight = false
    var darkness = 0f

    // --- dünya nesneleri ---
    val trees = ArrayList<Tree>()
    val walls = ArrayList<Wall>()
    val shadows = ArrayList<Shadow>()
    val critters = ArrayList<Critter>()
    val bags = ArrayList<LootBag>()
    val bolts = ArrayList<Bolt>()
    var villager: Villager? = null
    var villagerFreed = false
    var villagerCageX = 0f; var villagerCageY = 0f

    // --- kalp taşı ---
    var heartX = 0f; var heartY = 0f
    var heartHp = 300f; var heartMaxHp = 300f
    var heartPhase = 0f; var heartFlash = 0f
    var heartDown = false
    var kingdomWon = false

    // --- gece dalga durumu ---
    var waveLeft = 0
    var burstAcc = 0f; var batchAcc = 0f
    var bigSiege = false

    // --- av baskısı ---
    var huntPressure = 0
    var critterRespawnAcc = 0f

    // --- UI bildirimleri (toast) ---
    data class Toast(val text: String, val gold: Boolean, var t: Float)
    val toasts = ArrayList<Toast>()
    var banner: String? = null      // büyük bant (KALP DÜŞTÜ / KRALLIK AYAKTA / BÜYÜK KUŞATMA)
    var bannerGold = false
    var bannerT = 0f

    fun toast(s: String, gold: Boolean) { toasts.add(Toast(s, gold, 3f)) }
    fun showBanner(s: String, gold: Boolean, dur: Float) { banner = s; bannerGold = gold; bannerT = dur }

    fun start() {
        val c = world.startCamp()
        px = c.first * TILE + TILE / 2; py = c.second * TILE + TILE / 2
        heartX = px + 6 * TILE; heartY = py
        // kafes ~7 karo ötede (kuzeydoğu)
        villagerCageX = px + 5 * TILE; villagerCageY = py - 5 * TILE
        villager = Villager(villagerCageX, villagerCageY)
        inv["wood"] = 0
        ensureChunk()
    }

    // --- dünya nesnelerini oyuncu çevresinde tembel doğur (chunk akışı yerine yarıçap) ---
    private var lastChunkTx = Int.MIN_VALUE
    private var lastChunkTy = Int.MIN_VALUE
    fun ensureChunk() {
        val ptx = (px / TILE).toInt(); val pty = (py / TILE).toInt()
        if (ptx == lastChunkTx && pty == lastChunkTy) return
        lastChunkTx = ptx; lastChunkTy = pty
        val R = 16
        val now = timeSec.toDouble()
        // mevcut görünür ağaçları sıfırla, yarıçaptakileri tazele
        trees.removeAll { hypot(it.x - px, it.y - py) > (R + 4) * TILE }
        val present = HashSet<Long>()
        for (t in trees) present.add(World.key(t.tx, t.ty))
        for (ty in (pty - R)..(pty + R)) for (tx in (ptx - R)..(ptx + R)) {
            val k = World.key(tx, ty)
            if (present.contains(k)) continue
            if (world.isHarvested(tx, ty, now)) continue
            val s = world.resourceAt(tx, ty)
            if (s.type == Res.NONE) continue
            val wx = tx * TILE + TILE / 2 + (s.jx * TILE).toFloat()
            val wy = ty * TILE + TILE / 2 + (s.jy * TILE).toFloat()
            trees.add(Tree(wx, wy, tx, ty, s.type, s.variant))
        }
    }

    fun movePlayer(dx: Float, dy: Float, dt: Float) {
        if (dead) return
        val sp = 420f  // KKPlayerCharacter MaxWalkSpeed
        val nx = px + dx * sp * dt
        val ny = py + dy * sp * dt
        // su engeli: yumuşak itme (bloklanan karoya girme)
        val ttx = (nx / TILE).toInt(); val tty = (ny / TILE).toInt()
        if (!world.isBlocked(world.tileAt(ttx, tty)) && !blockedByWall(nx, ny)) {
            px = nx; py = ny
        }
        if (dx != 0f || dy != 0f) facing = atan2(dy, dx)
        ensureChunk()
    }

    private fun blockedByWall(nx: Float, ny: Float): Boolean {
        for (w in walls) {
            if (w.type == Build.DOOR && w.open) continue
            if (hypot(w.x - nx, w.y - ny) < TILE * 0.5f) return true
        }
        return false
    }

    // ---------------- ana simülasyon ----------------
    fun update(dt: Float) {
        // zaman
        if (!kingdomWon && !heartDown) timeSec += dt
        if (timeSec >= CYCLE) { timeSec -= CYCLE; day++; onNewDay() }
        darkness = computeDarkness(timeSec / CYCLE)
        val night = darkness > 0.5f
        if (night != wasNight) { if (night) onNight() else onDawn(); wasNight = night }

        // oyuncu hayatta kalma ekonomisi (KKPlayerCharacter 4Hz mantığı, burada her kare ölçekli)
        if (!dead) {
            hunger = (hunger - (100f / 300f) * dt).coerceAtLeast(0f)
            if (hunger <= 0f) {
                health -= 2f * dt
                if (health <= 0f) { die(null); }
            }
            stamina = (stamina + 10f * dt).coerceAtMost(maxStamina)
            if (attackT > 0f) attackT -= dt
            if (flash > 0f) flash -= dt * 3f
        } else {
            respawnT -= dt
            if (respawnT <= 0f) respawn()
        }

        updateTrees(dt)
        updateShadows(dt)
        updateBalistas(dt)
        updateBolts(dt)
        updateCritters(dt)
        updateVillager(dt)
        updateWave(dt)
        updateHeart(dt)

        // toast/banner zamanlayıcı
        val it = toasts.iterator()
        while (it.hasNext()) { val z = it.next(); z.t -= dt; if (z.t <= 0f) it.remove() }
        if (bannerT > 0f) bannerT -= dt

        // gün-10 zafer
        if (day >= WIN_DAY && !kingdomWon && !heartDown) {
            kingdomWon = true
            showBanner("KRALLIK AYAKTA", true, 999f)
            Sfx.play("quest")
        }
    }

    private fun computeDarkness(f: Float): Float {
        // KKTimeOfDaySubsystem.ComputeDarkness BİREBİR
        if (f < 0.45f) return 0f
        if (f < 0.58f) return smoothstep((f - 0.45f) / 0.13f)
        if (f < 0.92f) return 1f
        return 1f - smoothstep((f - 0.92f) / 0.08f)
    }
    private fun smoothstep(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3 - 2 * x) }

    private fun onNewDay() {
        huntPressure = 0  // şafak ekoloji toparlanması temposu
    }

    private fun onNight() {
        bigSiege = (day % 5 == 0)
        waveLeft = minOf((2 + day) * (if (bigSiege) 2 else 1), 24)
        burstAcc = 0f; batchAcc = 0f
        if (bigSiege) showBanner("BÜYÜK KUŞATMA", false, 4f)
        // hayvanlar geceleyin kaybolur
        critters.clear()
        villager?.let { it.atNight = true }
    }

    private fun onDawn() {
        // şafak: kalan gölgeler törensel ölür, keseler kalır
        for (s in shadows) if (!s.dying) { s.dying = true; s.deathT = 0f }
        villager?.let { it.atNight = false }
    }

    // ---------------- ağaç/kaynak ----------------
    private fun updateTrees(dt: Float) {
        for (t in trees) if (t.shake > 0f) t.shake -= dt * 4f
    }

    fun tryHarvest(): Boolean {
        if (dead) return false
        var best: Tree? = null; var bd = 180f  // HarvestRange
        for (t in trees) {
            if (t.depleted) continue
            val d = hypot(t.x - px, t.y - py)
            if (d < bd) { bd = d; best = t }
        }
        best ?: return false
        val t = best
        t.hits--
        t.shake = 1f
        Sfx.play(when (t.res) { Res.TREE -> "chop"; Res.ROCK -> "mine"; else -> "rustle" })
        val item = when (t.res) { Res.TREE -> "wood"; Res.ROCK -> "stone"; else -> "berry" }
        val amt = if (t.res == Res.BUSH) 2 else 1
        addItem(item, amt)
        if (t.hits <= 0) {
            t.depleted = true
            val delay = when (t.res) { Res.TREE -> 90.0; Res.ROCK -> 120.0; else -> 45.0 }
            world.markHarvested(t.tx, t.ty, timeSec.toDouble() + delay)
        }
        return true
    }

    // ---------------- yeme ----------------
    fun tryEat(): Boolean {
        // E ile yenebilir: berry (+22) ya da meat (+35)
        if (consume("meat")) { hunger = (hunger + 35f).coerceAtMost(maxHunger); Sfx.play("eat"); return true }
        if (consume("berry")) { hunger = (hunger + 22f).coerceAtMost(maxHunger); Sfx.play("eat"); return true }
        return false
    }

    // ---------------- saldırı (kılıç) ----------------
    fun attack(): Boolean {
        if (dead || stamina < 8f) return false
        stamina -= 8f
        attackT = 0.18f
        Sfx.play("swing")
        val range = 170f
        var hit = false
        // gölgeler
        for (s in shadows) {
            if (s.dying) continue
            val to = atan2(s.y - py, s.x - px)
            if (hypot(s.x - px, s.y - py) < range && angClose(facing, to)) {
                damageShadow(s, 20f); hit = true
            }
        }
        // hayvanlar
        for (c in critters) {
            if (hypot(c.x - px, c.y - py) < range && angClose(facing, atan2(c.y - py, c.x - px))) {
                c.hp -= 20f
                if (c.hp <= 0f) killCritter(c)
                hit = true
            }
        }
        // yurttaş (kazara/kasıtlı öldürme: kalıcı kayıp)
        villager?.let { v ->
            if (!v.dead && hypot(v.x - px, v.y - py) < range && angClose(facing, atan2(v.y - py, v.x - px))) {
                v.hp -= 20f
                if (v.hp <= 0f) killVillager(v)
                hit = true
            }
        }
        if (hit) Sfx.play("hitE")
        return true
    }

    private fun angClose(a: Float, b: Float): Boolean {
        var d = a - b
        while (d > Math.PI) d -= (2 * Math.PI).toFloat()
        while (d < -Math.PI) d += (2 * Math.PI).toFloat()
        return kotlin.math.abs(d) < 1.2f   // ~70° koni
    }

    private fun damageShadow(s: Shadow, amt: Float) {
        s.hp -= amt; s.flash = 1f
        if (s.hp <= 0f && !s.dying) {
            s.dying = true; s.deathT = 0f
            Sfx.play("enemyDie")
            // yağma kesesi: taşıdığı envanteri düşür
            s.carryLoot?.let { loot -> if (loot.isNotEmpty()) bags.add(LootBag(s.x, s.y, HashMap(loot))) ; s.carryLoot = null }
        }
    }

    // ---------------- gölgeler ----------------
    private fun updateShadows(dt: Float) {
        val it = shadows.iterator()
        while (it.hasNext()) {
            val s = it.next()
            if (s.flash > 0f) s.flash -= dt * 4f
            if (s.dying) {
                s.deathT += dt
                if (s.deathT >= 0.6f) it.remove()
                continue
            }
            // hedef: oyuncu ∪ kalp (en yakın)
            val targetHeart = !heartDown && hypot(heartX - s.x, heartY - s.y) < hypot(px - s.x, py - s.y)
            val tx = if (targetHeart) heartX else px
            val ty = if (targetHeart) heartY else py
            val ang = atan2(ty - s.y, tx - s.x)
            // önünde kapalı duvar varsa onu döv
            val blocker = wallInFront(s.x, s.y, ang)
            if (blocker != null && hypot(blocker.x - s.x, blocker.y - s.y) < 130f) {
                s.attackCd -= dt
                if (s.attackCd <= 0f) {
                    s.attackCd = 0.6f
                    blocker.hp -= 8f; blocker.shake = 1f   // StructDamage = 8
                    Sfx.play("place")
                    if (blocker.hp <= 0f) walls.remove(blocker)
                }
                continue
            }
            val sp = 240f  // gölge hızı (oyuncudan yavaş)
            s.x += cos(ang) * sp * dt; s.y += sin(ang) * sp * dt
            // temas hasarı
            if (targetHeart) {
                if (hypot(heartX - s.x, heartY - s.y) < 110f) {
                    s.attackCd -= dt
                    if (s.attackCd <= 0f) { s.attackCd = 0.6f; damageHeart(8f) }
                }
            } else if (!dead && hypot(px - s.x, py - s.y) < 110f) {  // TouchRange
                s.attackCd -= dt
                if (s.attackCd <= 0f) { s.attackCd = 0.6f; hurtPlayer(10f, s) }  // TouchDamage
            }
        }
    }

    private fun wallInFront(x: Float, y: Float, ang: Float): Wall? {
        val fx = x + cos(ang) * 80f; val fy = y + sin(ang) * 80f
        var best: Wall? = null; var bd = 120f
        for (w in walls) {
            if (w.type == Build.DOOR && w.open) continue
            val d = hypot(w.x - fx, w.y - fy)
            if (d < bd) { bd = d; best = w }
        }
        return best
    }

    // ---------------- balista ----------------
    private fun updateBalistas(dt: Float) {
        for (w in walls) {
            if (w.type != Build.BALLISTA) continue
            if (w.fireCd > 0f) w.fireCd -= dt
            var tgt: Shadow? = null; var bd = 950f  // Range
            for (s in shadows) {
                if (s.dying) continue
                val d = hypot(s.x - w.x, s.y - w.y)
                if (d < bd) { bd = d; tgt = s }
            }
            if (tgt != null) {
                w.aimAng = atan2(tgt.y - w.y, tgt.x - w.x)
                if (w.fireCd <= 0f) {
                    w.fireCd = 2.5f  // FireDelay
                    bolts.add(Bolt(w.x, w.y, tgt.x, tgt.y))
                    Sfx.play("bolt")
                }
            } else {
                w.idleScan += dt * 0.6f
                w.aimAng = w.idleScan
            }
        }
    }

    private fun updateBolts(dt: Float) {
        val it = bolts.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.t += dt
            val f = (b.t / b.dur).coerceAtMost(1f)
            b.x = b.x + (b.tx - b.x) * 0.0f  // konum görselde lerp edilir
            if (f >= 1f) {
                // varış: hedefe yakın gölgeye 12 hasar (BoltDamage)
                for (s in shadows) {
                    if (!s.dying && hypot(s.x - b.tx, s.y - b.ty) < 80f) { damageShadow(s, 12f); break }
                }
                it.remove()
            }
        }
    }

    // ---------------- kalp taşı ----------------
    private fun updateHeart(dt: Float) {
        heartPhase += dt
        if (heartFlash > 0f) heartFlash -= dt * 4f
    }
    private fun damageHeart(amt: Float) {
        if (heartDown) return
        heartHp -= amt; heartFlash = 1f
        Sfx.play("heartHit")
        if (heartHp <= 0f) {
            heartHp = 0f; heartDown = true
            showBanner("KALP TAŞI DÜŞTÜ", false, 999f)
            Sfx.play("heartDie")
        }
    }

    // ---------------- gece dalgası ----------------
    private fun updateWave(dt: Float) {
        if (!wasNight || waveLeft <= 0) return
        burstAcc += dt; batchAcc += dt
        val burstEvery = if (bigSiege) 0.8f else 1.2f
        if (burstAcc >= burstEvery) { burstAcc = 0f; spawnBurstOne() }
        if (batchAcc >= 6f) { batchAcc = 0f; spawnBatch() }
    }
    private fun spawnBurstOne() {
        if (waveLeft <= 0) return
        waveLeft--
        spawnShadowRing()
    }
    private fun spawnBatch() {
        val maxAlive = minOf(4 + day * 2, 18)
        val n = minOf(Random.nextInt(1, 3), maxAlive - shadows.count { !it.dying })
        for (i in 0 until n) spawnShadowRing()
    }
    private fun spawnShadowRing() {
        val ang = Random.nextFloat() * 2f * Math.PI.toFloat()
        val dist = 1500f + Random.nextFloat() * 700f
        val cx = heartX; val cy = heartY
        shadows.add(Shadow(cx + cos(ang) * dist, cy + sin(ang) * dist, day))
    }

    // ---------------- hayvanlar (ürkek fauna) ----------------
    private fun updateCritters(dt: Float) {
        if (wasNight) return
        critterRespawnAcc += dt
        val basePop = 6
        val cap = (basePop - huntPressure).coerceAtLeast(0)
        if (critters.size < cap && critterRespawnAcc > 2f) {
            critterRespawnAcc = 0f
            val ang = Random.nextFloat() * 2f * Math.PI.toFloat()
            val d = 1800f + Random.nextFloat() * 800f  // SpawnMin..SpawnMax
            val kind = if (Random.nextFloat() < 0.6f) CritterKind.RABBIT else CritterKind.DEER
            critters.add(Critter(px + cos(ang) * d, py + sin(ang) * d, kind))
        }
        for (c in critters) {
            c.hopPhase += dt * 8f
            val pd = hypot(px - c.x, py - c.y)
            if (pd < 420f) {  // FleeRadius — oyuncudan kaç
                val a = atan2(c.y - py, c.x - px)
                val sp = if (c.kind == CritterKind.RABBIT) 380f else 300f
                c.x += cos(a) * sp * dt; c.y += sin(a) * sp * dt
            } else {
                c.wanderT -= dt
                if (c.wanderT <= 0f) {
                    c.wanderT = 1f + Random.nextFloat() * 2f
                    val a = Random.nextFloat() * 2f * Math.PI.toFloat()
                    val sp = if (c.kind == CritterKind.RABBIT) 120f else 90f
                    c.vx = cos(a) * sp; c.vy = sin(a) * sp
                }
                c.x += c.vx * dt; c.y += c.vy * dt
            }
        }
    }
    private fun killCritter(c: Critter) {
        critters.remove(c)
        huntPressure++
        Sfx.play("critterDie")
        // geyik -> 2 et + 1 post; tavşan -> 1 et
        if (c.kind == CritterKind.DEER) { addItem("meat", 2); addItem("hide", 1) }
        else addItem("meat", 1)
    }

    // ---------------- yurttaş Ayla ----------------
    fun tryFreeVillager(): Boolean {
        val v = villager ?: return false
        if (villagerFreed || v.dead) return false
        if (hypot(v.x - px, v.y - py) < 180f) {
            villagerFreed = true
            toast("Ayla katıldı", true)
            Sfx.play("quest")
            return true
        }
        return false
    }
    private fun updateVillager(dt: Float) {
        val v = villager ?: return
        if (v.dead || !villagerFreed) return
        if (v.atNight) {
            // gece kalbe koş, bekle
            val a = atan2(heartY - v.y, heartX - v.x)
            if (hypot(heartX - v.x, heartY - v.y) > 150f) { v.x += cos(a) * 200f * dt; v.y += sin(a) * 200f * dt }
            return
        }
        // gündüz ağaç kes
        if (v.carrying) {
            val a = atan2(py - v.y, px - v.x)
            if (hypot(px - v.x, py - v.y) > 150f) { v.x += cos(a) * 220f * dt; v.y += sin(a) * 220f * dt }
            else { v.carrying = false; addItem("wood", 1); Sfx.play("pickup") }  // omzundaki kütüğü teslim
            return
        }
        if (v.target == null || v.target!!.depleted) {
            v.target = trees.filter { it.res == Res.TREE && !it.depleted && hypot(it.x - heartX, it.y - heartY) < 1400f }
                .minByOrNull { hypot(it.x - v.x, it.y - v.y) }
        }
        val t = v.target ?: return
        val a = atan2(t.y - v.y, t.x - v.x)
        if (hypot(t.x - v.x, t.y - v.y) > 170f) { v.x += cos(a) * 200f * dt; v.y += sin(a) * 200f * dt }
        else {
            v.chopCd -= dt
            if (v.chopCd <= 0f) {
                v.chopCd = 1.4f  // ChopRhythm
                t.hits--; t.shake = 1f; Sfx.play("chop")
                if (t.hits <= 0) { t.depleted = true; world.markHarvested(t.tx, t.ty, timeSec.toDouble() + 90.0); v.carrying = true; v.target = null }
            }
        }
    }
    private fun killVillager(v: Villager) {
        v.dead = true
        toast("Ayla öldü", false)
        bags.add(LootBag(v.x, v.y, hashMapOf("wood" to 3)))
    }

    // ---------------- yapı kurma ----------------
    fun place(type: Build): Boolean {
        val tx = ((px + cos(facing) * TILE) / TILE).toInt()
        val ty = ((py + sin(facing) * TILE) / TILE).toInt()
        if (world.isBlocked(world.tileAt(tx, ty))) return false
        if (walls.any { it.tx == tx && it.ty == ty }) return false
        val cost = costOf(type)
        for ((k, v) in cost) if ((inv[k] ?: 0) < v) return false
        for ((k, v) in cost) consume(k, v)
        walls.add(Wall(tx * TILE + TILE / 2, ty * TILE + TILE / 2, tx, ty, type))
        Sfx.play("place")
        return true
    }
    fun costOf(type: Build): Map<String, Int> = when (type) {
        Build.WALL -> mapOf("wood" to 4)
        Build.DOOR -> mapOf("wood" to 6, "stone" to 1)
        Build.BALLISTA -> mapOf("wood" to 8, "stone" to 4)
    }

    fun toggleDoor(): Boolean {
        var best: Wall? = null; var bd = 200f
        for (w in walls) if (w.type == Build.DOOR) { val d = hypot(w.x - px, w.y - py); if (d < bd) { bd = d; best = w } }
        best?.let { it.open = !it.open; Sfx.play("door"); return true }
        return false
    }

    fun pickupBags() {
        val it = bags.iterator()
        while (it.hasNext()) {
            val b = it.next()
            if (hypot(b.x - px, b.y - py) < 130f) {
                for ((k, v) in b.items) addItem(k, v)
                Sfx.play("pickup")
                it.remove()
            }
        }
    }

    // ---------------- hasar/ölüm/yağma ----------------
    private fun hurtPlayer(amt: Float, by: Shadow) {
        if (dead) return
        health -= amt; flash = 1f
        Sfx.play("hurt")
        if (health <= 0f) die(by)
    }
    private fun die(killer: Shadow?) {
        if (dead) return
        dead = true
        respawnT = 4f
        Sfx.play("playerDie")
        // yağma kuralı: envanter katile (gölge taşır) ya da ölüm noktasına kese
        val loot = HashMap(inv.filterValues { it > 0 })
        inv.clear(); inv["wood"] = 0
        if (loot.isNotEmpty()) {
            if (killer != null) killer.carryLoot = loot   // gölge hırsız
            else bags.add(LootBag(px, py, loot))           // açlık ölümü: kese yerinde
        }
    }
    private fun respawn() {
        dead = false
        health = maxHealth; stamina = maxStamina; hunger = 50f
        val c = world.startCamp()
        px = c.first * TILE + TILE / 2; py = c.second * TILE + TILE / 2
    }

    // ---------------- envanter yardımcıları ----------------
    fun addItem(id: String, n: Int) { inv[id] = (inv[id] ?: 0) + n }
    fun consume(id: String, n: Int = 1): Boolean {
        val have = inv[id] ?: 0
        if (have < n) return false
        if (have - n <= 0 && id != "wood") inv.remove(id) else inv[id] = have - n
        return true
    }

    // ================= KAYIT (versiyonlu, v4 şeması; eski kayıt KIRILMAZ) =================
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("ver", 4)
        o.put("seed", seed)
        o.put("day", day); o.put("time", timeSec.toDouble())
        o.put("px", px.toDouble()); o.put("py", py.toDouble())
        o.put("hp", health.toDouble()); o.put("st", stamina.toDouble()); o.put("hu", hunger.toDouble())
        o.put("inv", JSONObject(inv as Map<*, *>))
        o.put("heartHp", heartHp.toDouble()); o.put("heartDown", heartDown); o.put("won", kingdomWon)
        // yapılar
        val wa = JSONArray()
        for (w in walls) wa.put(JSONObject().apply {
            put("tx", w.tx); put("ty", w.ty); put("type", w.type.name); put("hp", w.hp.toDouble()); put("open", w.open)
        })
        o.put("walls", wa)
        // hasat edilenler
        val ha = JSONArray()
        for ((k, v) in world.harvested) ha.put(JSONObject().apply { put("k", k); put("t", v) })
        o.put("harvested", ha)
        // keseler (save v4)
        val ba = JSONArray()
        for (b in bags) ba.put(JSONObject().apply {
            put("x", b.x.toDouble()); put("y", b.y.toDouble()); put("items", JSONObject(b.items as Map<*, *>))
        })
        o.put("bags", ba)
        // Ayla durumu (kalıcı)
        o.put("aylaFreed", villagerFreed)
        o.put("aylaDead", villager?.dead ?: false)
        return o
    }

    fun loadJson(o: JSONObject) {
        val ver = o.optInt("ver", 1)
        // MigrateToCurrent zinciri: eksik alanlar makul varsayılana düşer (eski kayıt kırılmaz).
        day = o.optInt("day", 1); timeSec = o.optDouble("time", 0.0).toFloat()
        px = o.optDouble("px", px.toDouble()).toFloat(); py = o.optDouble("py", py.toDouble()).toFloat()
        health = o.optDouble("hp", 100.0).toFloat(); stamina = o.optDouble("st", 100.0).toFloat()
        hunger = o.optDouble("hu", 100.0).toFloat()
        inv.clear()
        o.optJSONObject("inv")?.let { ji -> ji.keys().forEach { inv[it] = ji.getInt(it) } }
        if (!inv.containsKey("wood")) inv["wood"] = 0
        heartHp = o.optDouble("heartHp", 300.0).toFloat()
        heartDown = o.optBoolean("heartDown", false)
        kingdomWon = o.optBoolean("won", false)
        walls.clear()
        o.optJSONArray("walls")?.let { wa ->
            for (i in 0 until wa.length()) {
                val jw = wa.getJSONObject(i)
                val tx = jw.getInt("tx"); val ty = jw.getInt("ty")
                val w = Wall(tx * TILE + TILE / 2, ty * TILE + TILE / 2, tx, ty, Build.valueOf(jw.getString("type")))
                w.hp = jw.optDouble("hp", w.maxHp.toDouble()).toFloat(); w.open = jw.optBoolean("open", false)
                walls.add(w)
            }
        }
        world.harvested.clear()
        o.optJSONArray("harvested")?.let { ha ->
            for (i in 0 until ha.length()) { val j = ha.getJSONObject(i); world.harvested[j.getLong("k")] = j.getDouble("t") }
        }
        bags.clear()
        if (ver >= 4) o.optJSONArray("bags")?.let { ba ->
            for (i in 0 until ba.length()) {
                val j = ba.getJSONObject(i); val items = HashMap<String, Int>()
                j.getJSONObject("items").let { ji -> ji.keys().forEach { items[it] = ji.getInt(it) } }
                bags.add(LootBag(j.getDouble("x").toFloat(), j.getDouble("y").toFloat(), items))
            }
        }
        villagerFreed = o.optBoolean("aylaFreed", false)
        if (o.optBoolean("aylaDead", false)) villager?.dead = true
        lastChunkTx = Int.MIN_VALUE
        ensureChunk()
    }
}
