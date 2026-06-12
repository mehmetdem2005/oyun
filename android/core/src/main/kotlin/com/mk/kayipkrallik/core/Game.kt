package com.mk.kayipkrallik.core

/* ════════════════════════════════════════════════════════════════════════
   SİSTEMLER — Game: kabuğun konuştuğu tek yüzey.
   Girdi: setDir / tapAttack / tapInteract / tapBuild
   Çıktı: durum alanları + sfx/toast/big/flash/dirtyChunks olay kuyrukları
   ════════════════════════════════════════════════════════════════════════ */

class Game(val s: GameState) {

    private var inDx = 0f; private var inDy = 0f
    private var qAtk = false; private var qInt = false; private var qBld = false

    fun setDir(dx: Float, dy: Float) { inDx = dx; inDy = dy }
    fun tapAttack() { qAtk = true }
    fun tapInteract() { qInt = true }
    fun tapBuild() { qBld = true }

    /* ── Sohbet: çekirdek mesajı kaydeder, port dışarı taşır, Ayla bağlamla yanıtlar ── */
    private var econT = 0f
    private var genOk = false
    private var wDisc = 0
    private var demK = 0L
    private var demT = 0f
    var chatPort: ChatPort? = null
    fun quickChat(i: Int) {
        if (i < 0 || i >= K.QCHAT.size) return
        val msg = K.QCHAT[i]
        s.pushChat(msg, 0); s.emit("click", s.player.x, s.player.y)
        chatPort?.send(msg, 0)
        val v = s.villager
        if (v != null && v.state != K.VS_CAGED && v.hp > 0f) {
            s.pushChat(when (i) {
                0 -> "Ayla: Geliyorum!"
                1 -> "Ayla: Kalbe ko\u015fuyorum!"
                2 -> "Ayla: Yan\u0131nday\u0131m."
                else -> "Ayla: Sen de \u00f6yle!"
            }, 1)
        }
    }

    /* ── ana tik ── */
    fun update(dt: Float) {
        if (demT > 0f) demT -= dt
        if (!genOk) {                                    // v4: varsayılan Jeneratör
            genOk = true
            if (s.builds.values.none { it.t == K.B_GEN }) {
                val gx = s.wtX(s.player.x) + 2; val gy = s.wtY(s.player.y) - 2
                val gb = Build(K.B_GEN, gx, gy); gb.hp = bHp(K.B_GEN, 1)
                s.builds[key(gx, gy)] = gb
                if ((s.player.inv["gold"] ?: 0) == 0) s.player.inv["gold"] = 25
            }
        }
        econT += dt                                      // ── EKONOMİ TİKİ (1 sn) ──
        while (econT >= 1f) {
            econT -= 1f
            var cap = 300; var disc = 0
            for (b in s.builds.values) {
                if (b.t == K.B_STORE) cap += bRate(K.B_STORE, b.lvl).toInt()
                if (b.t == K.B_WORKSHOP) disc += bRate(K.B_WORKSHOP, b.lvl).toInt()
            }
            wDisc = minOf(20, disc)
            if (s.player.alive && s.player.hp < 35f && s.takeInv(s.player.inv, "potion", 1)) {
                s.player.hp = minOf(100f, s.player.hp + 40f)        // akıllı iksir
                s.toast("⚗ Can iksiri içildi (+40).", 0)
                s.emit("eat", s.player.x, s.player.y)
            }
            var statM = 1f; var gran = 0
            for (b in s.builds.values) {
                if (b.t == K.B_STATUE) statM += 0.04f * b.lvl       // heykel: küresel üretim
                if (b.t == K.B_GRANARY) gran += b.lvl               // ambar: çiftlik bonusu
            }
            var gold = s.player.inv["gold"] ?: 0
            for (b in s.builds.values) {
                when (b.t) {
                    K.B_GEN, K.B_MINE -> {
                        b.cd += bRate(b.t, b.lvl) * statM
                        if (b.cd >= 1f) {
                            val a = b.cd.toInt(); b.cd -= a.toFloat()
                            if (gold < cap) gold = minOf(cap, gold + a)
                        }
                    }
                    K.B_FARM -> {
                        b.cd += 1f
                        if (b.cd >= 12f) {
                            b.cd = 0f
                            s.addInv(s.player.inv, "berry", bRate(K.B_FARM, b.lvl).toInt() + gran)
                        }
                    }
                    K.B_TEMPLE -> if (dist(b.x, b.y, s.player.x, s.player.y) < 140f)
                        s.player.hp = minOf(100f, s.player.hp + bRate(K.B_TEMPLE, b.lvl))
                    K.B_TOWER -> {                  // otomatik ok: menzildeki golgeye
                        b.cd += 1f
                        if (b.cd >= 2f) {
                            var tg0: Shadow? = null
                            var bd0 = 96f + 18f * b.lvl
                            for (sh in s.shadows) {
                                val d0 = dist(sh.x, sh.y, b.x, b.y)
                                if (d0 < bd0) { bd0 = d0; tg0 = sh }
                            }
                            if (tg0 != null) {
                                b.cd = 0f
                                tg0.hp -= bRate(K.B_TOWER, b.lvl)
                                s.emit("hit", tg0.x, tg0.y)
                                if (tg0.hp <= 0f) s.shadows.remove(tg0)
                            }
                        }
                    }
                    K.B_TRAP -> {                       // diken: ustune basan yanar
                        if (b.cd > 0f) b.cd -= 1f
                        if (b.cd <= 0f) {
                            var hit0: Shadow? = null
                            for (sh in s.shadows) {
                                if (dist(sh.x, sh.y, b.x, b.y) < 26f) { hit0 = sh; break }
                            }
                            if (hit0 != null) {
                                hit0.hp -= bRate(K.B_TRAP, b.lvl)
                                s.emit("hit", hit0.x, hit0.y)
                                b.cd = 3f
                                if (hit0.hp <= 0f) s.shadows.remove(hit0)
                            }
                        }
                    }
                    K.B_ALCHEMY -> {
                        if (dist(b.x, b.y, s.player.x, s.player.y) < 110f)
                            s.player.hp = minOf(100f, s.player.hp + bRate(K.B_ALCHEMY, b.lvl))
                        craftTick(b)
                    }
                    K.B_SMITH, K.B_WORKSHOP -> craftTick(b)
                    K.B_LUMBER -> {
                        b.cd += 1f
                        if (b.cd >= 10f) {
                            b.cd = 0f
                            s.addInv(s.player.inv, "wood", bRate(K.B_LUMBER, b.lvl).toInt())
                        }
                    }
                    K.B_WIZARD -> {
                        b.cd += 1f
                        if (b.cd >= 4f) {                            // alan büyüsü
                            b.cd = 0f
                            val rr = 140f + 20f * b.lvl
                            var iw = 0
                            while (iw < s.shadows.size) {
                                val shw = s.shadows[iw]
                                if (shw.alive && dist(shw.x, shw.y, b.x, b.y) < rr) {
                                    s.emit("hit", shw.x, shw.y)
                                    damageShadow(shw, bRate(K.B_WIZARD, b.lvl))
                                }
                                iw++
                            }
                        }
                    }
                    K.B_HEAL -> for (mh in s.minions)
                        if (dist(mh.x, mh.y, b.x, b.y) < 130f)
                            mh.hp = minOf(60f, mh.hp + bRate(K.B_HEAL, b.lvl))
                    K.B_BARRACKS -> {
                        val hk = key(b.tx, b.ty)
                        val have = s.minions.count { it.home == hk }
                        if (have < b.lvl && s.minions.size < 12) {
                            b.cd += 1f
                            if (b.cd >= 6f) {
                                b.cd = 0f
                                s.minions.add(Minion(b.x + 20f, b.y + 14f,
                                    30f + 10f * b.lvl, b.lvl, hk))
                            }
                        }
                    }
                }
            }
            s.player.inv["gold"] = gold
        }
        run {                                            // ── MİNYON YZ ──
            var smithB = 0f
            for (b in s.builds.values)
                if (b.t == K.B_SMITH && bRate(K.B_SMITH, b.lvl) > smithB)
                    smithB = bRate(K.B_SMITH, b.lvl)
            var mi = 0
            while (mi < s.minions.size) {
                val m = s.minions[mi]
                if (m.hp <= 0f) { s.minions.removeAt(mi); continue }
                var tg: Shadow? = null; var bd = 280f
                for (sh in s.shadows) {
                    val d2 = dist(sh.x, sh.y, m.x, m.y)
                    if (d2 < bd) { bd = d2; tg = sh }
                }
                if (tg != null) {
                    val dx = tg.x - m.x; val dy = tg.y - m.y
                    val dn = maxOf(1f, dist(0f, 0f, dx, dy))
                    if (bd > 24f) { m.x += dx / dn * 55f * dt; m.y += dy / dn * 55f * dt }
                    m.ac -= dt
                    if (bd <= 26f && m.ac <= 0f) {
                        m.ac = 0.8f
                        tg.hp -= 5f + 2f * m.lvl + smithB
                        s.emit("hit", tg.x, tg.y)
                        if (tg.hp <= 0f) s.shadows.remove(tg)
                    }
                } else {
                    val hb = s.builds[m.home]
                    if (hb != null) {
                        val dx = hb.x - m.x; val dy = hb.y - m.y
                        val dn = dist(0f, 0f, dx, dy)
                        if (dn > 40f) { m.x += dx / dn * 40f * dt; m.y += dy / dn * 40f * dt }
                    }
                }
                mi++
            }
        }
        val p0 = s.phase()
        s.t += dt
        val p1 = s.phase()
        if (p0 < 0.5f && p1 >= 0.5f) onNight()
        if (p1 < p0) onDawn()

        updatePlayer(dt)
        updateShadows(dt)
        updateCritters(dt)
        updateVillager(dt)
        updateBuilds(dt)
        updateBags()
        updateSpawners(dt)
        s.flash = maxOf(0f, s.flash - dt * 1.4f)
    }

    fun darkness(): Float {
        val f = s.phase()
        return when {
            f < 0.45f -> 0f
            f < 0.58f -> (f - 0.45f) / 0.13f
            f < 0.92f -> 1f
            else -> 1f - (f - 0.92f) / 0.08f
        }
    }

    fun clockText(): String {
        val f = s.phase()
        val hm = (6f + f * 24f) % 24f
        val h = hm.toInt(); val m = ((hm - h) * 60).toInt()
        val pre = if (s.night) "\u263E" else "\u2600"
        return pre + " G\u00fcn " + s.day + " \u00b7 " +
                h.toString().padStart(2, '0') + ":" + m.toString().padStart(2, '0')
    }

    /* ── zaman olayları ── */
    private fun onNight() {
        s.night = true
        val big = s.day % 5 == 0                       // plan 43: 5. gece büyük kuşatma
        s.waveLeft = minOf((2 + s.day) * (if (big) 2 else 1), 24)
        s.burstGap = if (big) 0.8f else 1.2f; s.burstT = 1f
        if (big) s.toast("B\u00dcY\u00dcK KU\u015eATMA \u2014 " + s.day + ". gece, g\u00f6lgeler s\u00fcr\u00fcyle geliyor!", 1)
        var n = 0
        for (b in s.builds.values) if (b.t == K.DOOR && b.open) {       // plan 24: oto-kilit
            b.open = false; s.emit("door", b.x, b.y); n++
        }
        if (n > 0) s.toast("Kap\u0131lar gece i\u00e7in s\u00fcrg\u00fclendi.", 0)
        s.critters.clear()                              // ürkekler ine çekilir
    }

    private fun onDawn() {
        s.night = false; s.day++
        s.huntPressure = maxOf(0, s.huntPressure - 2)   // doğa toparlar
        s.toast("\u2600 Gece atlat\u0131ld\u0131: " + s.nightKills + " g\u00f6lge avland\u0131 \u00b7 Kalp %" +
            (s.heart.hp / 3f).toInt(), 0)               // ŞAFAK KARNESİ — oturum ritmi
        s.nightKills = 0
        for (sh in s.shadows) if (sh.alive && sh.dieT < 0) sh.dieT = 0f  // şafak töreni
        if (s.day >= 10 && !s.victory) {
            s.victory = true
            s.big("G\u00dcN 10 \u2014 KRALLIK AYAKTA!\nKu\u015fatma k\u0131r\u0131ld\u0131; bundan sonras\u0131 senin hik\u00e2yen.", 0)
        }
        autoSaveRequested = true
    }
    var autoSaveRequested = false                       // kabuk okur, kaydeder, sıfırlar

    /* ── kaynak vurma: tek hasat yolu ── */
    fun hitNode(tx: Int, ty: Int, inv: HashMap<String, Int>): Boolean {
        val k = key(tx, ty)
        val r = s.gen.resourceAt(tx, ty) ?: return false
        if (s.isHarvested(k)) return false
        val left = (s.hitsLeft[k] ?: K.R_HITS.getValue(r.kind)) - 1
        s.shake(tx, ty); s.markDirty(tx, ty)
        s.emit(if (r.kind == K.R_ROCK) "mine" else if (r.kind == K.R_TREE) "chop" else "rustle",
            tx * K.TS, ty * K.TS)
        when (r.kind) {                                  // vuruş başına ödül (KO birebir)
            K.R_TREE -> s.addInv(inv, "wood", if ((s.player.inv["axe"] ?: 0) > 0) 2 else 1)
            K.R_ROCK -> s.addInv(inv, "stone", if ((s.player.inv["pick"] ?: 0) > 0) 2 else 1)
            else -> s.addInv(inv, "berry", 2)
        }
        if (left <= 0) { s.hitsLeft.remove(k); s.harvested[k] = s.t + K.R_RESPAWN.getValue(r.kind) }
        else s.hitsLeft[k] = left
        return true
    }

    /* ── OYUNCU ── */
    private fun updatePlayer(dt: Float) {
        val p = s.player
        p.attackT = maxOf(0f, p.attackT - dt)
        if (!p.alive) {
            p.respawnT -= dt
            if (p.respawnT <= 0f) {
                val c = s.gen.campTile()
                p.x = c.first * K.TS + 16f; p.y = c.second * K.TS + 16f
                p.alive = true; p.hp = 60f; p.hu = 50f; p.en = 100f
            }
            qAtk = false; qInt = false; qBld = false
            return
        }
        // Açlık saati: 300 sn'de 100; sıfırda can erir. Enerji yenilenir.
        p.hu = maxOf(0f, p.hu - dt * (100f / 300f))
        if (p.hu <= 0f) p.hp -= dt * 2f
        p.swim = s.gen.tileAt(s.wtX(p.x), s.wtY(p.y)) <= K.T_WATER
        if (!p.swim) p.en = minOf(100f, p.en + dt * 10f) // suda yenilenme YOK
        if (p.hp <= 0f) { playerDie(); return }

        // YÜZME: hız düşer, enerji erir, biterse can erir (swim yukarıda hesaplandı).
        if (p.swim) {
            p.en -= 6f * dt
            if (p.en <= 0f) { p.en = 0f; p.hp -= 4f * dt }
        }
        var dx = inDx; var dy = inDy
        val l = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (l > 0.01f) {
            if (l > 1f) { dx /= l; dy /= l }
            val nl = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            p.fx = dx / nl; p.fy = dy / nl
            val spd = if (p.swim) 70f else 135f
            val nx = p.x + dx * spd * dt; val ny = p.y + dy * spd * dt
            if (!solidForPlayer(nx, ny)) { p.x = nx; p.y = ny }
            else if (!solidForPlayer(nx, p.y)) p.x = nx              // duvara sürtün: eksen kaydır
            else if (!solidForPlayer(p.x, ny)) p.y = ny
        }
        if (qAtk) { qAtk = false; doAttack() }
        if (qInt) { qInt = false; doInteract() }
        if (qBld) { qBld = false; p.buildMode = !p.buildMode; s.emit("click", p.x, p.y) }
    }

    /* Oyuncu için katılık: su ENGEL DEĞİL (yüzer); yalnız yapılar engeller. */
    private fun solidForPlayer(x: Float, y: Float) = s.buildSolid(s.wtX(x), s.wtY(y))

    fun frontTX() = s.wtX(s.player.x + s.player.fx * 34f)
    fun frontTY() = s.wtY(s.player.y + s.player.fy * 34f)

    private fun doAttack() {
        val p = s.player
        if (p.swim) return                               // suda silah çekilmez
        if (p.buildMode) { tryPlace(); return }
        val ftx = frontTX(); val fty = frontTY()
        val r = s.gen.resourceAt(ftx, fty)
        if (r != null && !s.isHarvested(key(ftx, fty))) {
            val cx = ftx * K.TS + 16f; val cy = fty * K.TS + 16f
            if (dist(cx, cy, p.x, p.y) < 54f) { hitNode(ftx, fty, p.inv); return }
        }
        if (p.en < 8f) return
        p.en -= 8f; p.attackT = 0.18f; s.emit("swing", p.x, p.y)
        // Yay isabeti: gölgeler > fauna > Ayla (dost ateşi mümkün — sorumluluk oyuncuda)
        val dmg = 20f + bestAtk(p)                          // silah: en iyisi otomatik
        for (sh in s.shadows) if (sh.alive && hitArc(sh.x, sh.y)) { damageShadow(sh, dmg); return }
        for (c in s.critters) if (c.alive && hitArc(c.x, c.y)) { damageCritter(c, dmg); return }
        val v = s.villager
        if (v != null && v.state != K.VS_CAGED && hitArc(v.x, v.y)) damageVillager(20f)
    }

    private fun hitArc(ex: Float, ey: Float): Boolean {
        val p = s.player
        val dx = ex - p.x; val dy = ey - p.y
        val d = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        return d < 40f && (dx * p.fx + dy * p.fy) / (if (d == 0f) 1f else d) > 0.35f
    }

    private fun doInteract() {
        val p = s.player
        if (p.buildMode) {
            val ur = upgradeBuild()
            if (ur == 1) { s.emit("craft", p.x, p.y); return }
            if (ur == 2 || ur == 3) {                    // söküm kazara olmasın: çift-E
                val fkD = key(frontTX(), frontTY())
                if (!(demK == fkD && demT > 0f)) {
                    demK = fkD; demT = 2.5f
                    s.toast(if (ur == 3) "Maks seviye \u2014 tekrar E: S\u00d6K" else "Tekrar E: S\u00d6K", 0)
                    return
                }
                demK = 0L
            }
            val ftx0 = frontTX(); val fty0 = frontTY(); val fk0 = key(ftx0, fty0)
            val own = s.builds[fk0]
            if (own != null) {                           // SÖKÜM: %50 malzeme iadesi
                for (pr in K.B_COST.getValue(own.t))
                    s.addInv(p.inv, pr.first, maxOf(1, pr.second / 2))
                s.builds.remove(fk0); s.markDirty(ftx0, fty0)
                s.emit("mine", p.x, p.y)
                s.toast("Yap\u0131 s\u00f6k\u00fcld\u00fc (+%50 iade).", 0); return
            }
            val i = K.B_ORDER.indexOf(p.buildSel)        // boş karo: E = tip döngüsü
            p.buildSel = K.B_ORDER[(i + 1) % K.B_ORDER.size]
            s.emit("click", p.x, p.y); return
        }
        val ftx = frontTX(); val fty = frontTY()
        val fk = key(ftx, fty)
        val r = s.gen.resourceAt(ftx, fty)
        if (r != null && r.kind == K.R_BUSH && !s.isHarvested(fk)) { hitNode(ftx, fty, p.inv); return }
        val v = s.villager                               // kafes kurtarması
        if (v != null && v.state == K.VS_CAGED && dist(v.x, v.y, p.x, p.y) < 54f) {
            v.state = K.VS_GO_TREE; s.villagerState = 1
            s.emit("quest", v.x, v.y)
            s.toast("Ayla kat\u0131ld\u0131 \u2014 krall\u0131\u011f\u0131n ilk yurtta\u015f\u0131!", 0)
            return
        }
        var b = s.builds[fk]
        if (b == null) b = s.builds[key(s.wtX(p.x), s.wtY(p.y))]
        if (b != null) {
            if (b.t == K.DOOR) { b.open = !b.open; s.emit("door", b.x, b.y); return }
            if (b.t == K.BALLISTA && b.ammo < K.AMMO_CAP && s.takeInv(p.inv, "wood", 1)) {
                b.ammo = minOf(K.AMMO_CAP, b.ammo + K.AMMO_PER_WOOD)
                s.emit("craft", b.x, b.y); return
            }
            val mat = K.B_REPAIR[b.t]
            if (mat != null && b.hp < bHp(b.t, b.lvl) && s.takeInv(p.inv, mat, 1)) {
                b.hp = minOf(bHp(b.t, b.lvl), b.hp + bHp(b.t, b.lvl) * 0.3f)
                s.emit("craft", b.x, b.y); return
            }
        }
        if (s.takeInv(p.inv, "meat", 1)) { p.hu = minOf(100f, p.hu + 35f); s.emit("eat", p.x, p.y); return }
        if (s.takeInv(p.inv, "berry", 1)) { p.hu = minOf(100f, p.hu + 22f); s.emit("eat", p.x, p.y) }
    }

    /* SOPA: ilk silah. Envanter panelinden üretilir (3 odun). */
    fun craftClub(): Boolean {
        val p = s.player
        if ((p.inv["club"] ?: 0) > 0) { s.toast("Zaten bir sopan var.", 2); return false }
        if (!s.takeInv(p.inv, "wood", 3)) { s.toast("3 odun gerek.", 1); return false }
        s.addInv(p.inv, "club", 1)
        s.emit("craft", p.x, p.y)
        s.toast("Sopa haz\u0131r \u2014 vuru\u015flar art\u0131k daha sert!", 0)
        return true
    }

    fun tryPlace(): Boolean {
        val p = s.player
        val t = p.buildSel
        val tx = frontTX(); val ty = frontTY()
        val k = key(tx, ty)
        if (s.isSolid(tx, ty) || s.builds.containsKey(k)) return false
        val r = s.gen.resourceAt(tx, ty)
        if (r != null && !s.isHarvested(k)) return false
        if (tx == s.heart.tx && ty == s.heart.ty) return false
        if (!s.afford(t)) { s.emit("click", p.x, p.y); return false }
        for (c in K.B_COST.getValue(t)) s.takeInv(p.inv, c.first, c.second)
        val b = Build(t, tx, ty); b.scan = s.rng.angle()
        s.builds[k] = b
        s.emit("place", b.x, b.y)
        return true
    }

    fun placeOk(): Boolean {                             // kabuk hayaleti için
        val tx = frontTX(); val ty = frontTY(); val k = key(tx, ty)
        if (s.isSolid(tx, ty) || s.builds.containsKey(k)) return false
        val r = s.gen.resourceAt(tx, ty)
        if (r != null && !s.isHarvested(k)) return false
        if (tx == s.heart.tx && ty == s.heart.ty) return false
        return s.afford(s.player.buildSel)
    }

    private fun playerDie() {
        val p = s.player
        p.alive = false; p.respawnT = 4f
        s.emit("playerDie", p.x, p.y); s.flash = 0.6f
        // YAĞMA KURALI (solo dili): 10 sn içinde vuran gölge sırtlanır; yoksa yere kese.
        if (p.inv.isNotEmpty()) {
            val drop = HashMap(p.inv); p.inv.clear()
            val killer = p.lastByShadow
            if (killer != null && killer.alive && s.t - p.lastByT < 10f) {
                for (e in drop) s.addInv(killer.stolen, e.key, e.value)
                s.toast("Bir g\u00f6lge ganimetini s\u0131rtland\u0131 \u2014 onu avla!", 1)
            } else s.spawnBag(p.x, p.y, drop)
        }
    }

    fun damagePlayer(amt: Float, by: Shadow?) {
        val p = s.player
        if (!p.alive) return
        p.hp -= amt; p.lastByShadow = by; p.lastByT = s.t
        s.flash = 0.45f; s.emit("hurt", p.x, p.y)
        if (p.hp <= 0f) playerDie()
    }

    /* ── GÖLGELER ── */
    fun spawnShadow() {
        val h = s.heart
        val a = s.rng.angle(); val d = s.rng.range(520f, 700f)
        val x = h.x + Math.cos(a.toDouble()).toFloat() * d
        val y = h.y + Math.sin(a.toDouble()).toFloat() * d
        if (s.gen.blocked(s.gen.tileAt(s.wtX(x), s.wtY(y)))) return   // suya gölge doğmaz
        val sh = Shadow(x, y); sh.ph = s.rng.angle()
        s.shadows.add(sh)
    }

    fun bestAtk(p: Player): Float {
        var a = 0f
        for (e in K.IT_ATK) if ((p.inv[e.key] ?: 0) > 0 && e.value > a) a = e.value
        return a
    }
    fun bestDef(p: Player): Float {
        var d = 0f
        for (e in K.IT_DEF) if ((p.inv[e.key] ?: 0) > 0 && e.value > d) d = e.value
        return d
    }
    private fun craftTick(b: Build) {                    // oyuncu yakınken sıradakini üret
        if (dist(b.x, b.y, s.player.x, s.player.y) > 96f) { b.cd = 0f; return }
        b.cd += 1f
        if (b.cd < 6f) return
        b.cd = 0f
        val p = s.player
        for (id in K.IT_ORDER) {
            if (K.IT_BLD[id] != b.t) continue
            val have = p.inv[id] ?: 0
            if (id == "potion") { if (have >= 3) continue } else if (have > 0) continue
            var ok = true
            for (c in K.IT_COST.getValue(id))
                if ((p.inv[c.first] ?: 0) < c.second) ok = false
            if (!ok) continue
            for (c in K.IT_COST.getValue(id)) s.takeInv(p.inv, c.first, c.second)
            s.addInv(p.inv, id, 1)
            s.toast("⚒ " + (K.IT_NAME[id] ?: id) + " üretildi!", 0)
            s.emit("craft", b.x, b.y)
            return
        }
    }

    fun damageShadow(sh: Shadow, amt: Float) {
        if (!sh.alive) return
        sh.hp -= amt; s.emit("hitE", sh.x, sh.y)
        if (sh.hp <= 0f) killShadow(sh)
    }

    private fun killShadow(sh: Shadow) {
        sh.alive = false
        s.emit("enemyDie", sh.x, sh.y)
        if (s.night) s.nightKills++                      // şafak karnesine işlenir
        if (sh.stolen.isNotEmpty()) {                    // çalıntı HER ölümde düşer
            s.spawnBag(sh.x, sh.y, sh.stolen); sh.stolen.clear()
        }
    }

    private fun updateShadows(dt: Float) {
        val p = s.player; val h = s.heart
        var i = 0
        while (i < s.shadows.size) {
            val sh = s.shadows[i]
            if (!sh.alive) { s.shadows.removeAt(i); continue }
            if (sh.dieT >= 0f) {                         // şafak töreni 1.2 sn
                sh.dieT += dt; sh.scale = maxOf(0f, 1f - sh.dieT / 1.2f)
                if (sh.dieT >= 1.2f) killShadow(sh)
                i++; continue
            }
            sh.scale = minOf(1f, sh.scale + dt * 2f)
            sh.atkCd = maxOf(0f, sh.atkCd - dt)
            // Hedef: en yakın (oyuncu ∪ kalp)
            var isHeart = h.alive
            var tx = if (isHeart) h.x else p.x
            var ty = if (isHeart) h.y else p.y
            if (p.alive && h.alive &&
                dist(p.x, p.y, sh.x, sh.y) < dist(h.x, h.y, sh.x, sh.y)) {
                isHeart = false; tx = p.x; ty = p.y
            }
            if (!h.alive && !p.alive) { i++; continue }
            val dx = tx - sh.x; val dy = ty - sh.y
            val l = maxOf(1f, dist(tx, ty, sh.x, sh.y))
            val nx = dx / l; val ny = dy / l
            if (l < 22f) {
                if (sh.atkCd <= 0f) {
                    sh.atkCd = 0.8f
                    if (isHeart) damageHeart(10f) else if (p.alive) damagePlayer(10f * (1f - bestDef(p)), sh)
                }
                i++; continue
            }
            // Önündeki yapıyı kemir (kapı/duvar yolu keser, gölge söker)
            val ptx = s.wtX(sh.x + nx * 28f); val pty = s.wtY(sh.y + ny * 28f)
            val fb = s.builds[key(ptx, pty)]
            if (fb != null && (fb.t != K.DOOR || !fb.open)) {
                if (sh.atkCd <= 0f) { sh.atkCd = 0.8f; damageBuild(fb, 8f) }
                i++; continue
            }
            val nxp = sh.x + nx * 77f * dt; val nyp = sh.y + ny * 77f * dt
            if (!s.isSolid(s.wtX(nxp), s.wtY(nyp))) { sh.x = nxp; sh.y = nyp }
            i++
        }
    }

    fun damageHeart(amt: Float) {
        val h = s.heart
        if (!h.alive) return
        h.hp -= amt; s.emit("heartHit", h.x, h.y)
        s.flash = maxOf(s.flash, 0.25f); s.shake(h.tx, h.ty)
        if (h.hp <= 0f) {
            h.hp = 0f; h.alive = false
            s.emit("heartDie", h.x, h.y)
            s.big("KALP TA\u015eI D\u00dc\u015eT\u00dc\nKrall\u0131k karanl\u0131\u011fa g\u00f6m\u00fcld\u00fc\u2026", 1)
        }
    }

    fun damageBuild(b: Build, amt: Float) {
        b.hp -= amt; s.shake(b.tx, b.ty)
        s.emit(if (b.t == K.DOOR || b.t == K.WALL) "chop" else "mine", b.x, b.y)
        if (b.hp <= 0f) s.builds.remove(key(b.tx, b.ty))
    }

    /* ── FAUNA: Ürkek + Kışkırtılır (plan 153/155) ── */
    fun spawnCritter() {
        val p = s.player
        val a = s.rng.angle(); val d = s.rng.range(560f, 820f)
        val x = p.x + Math.cos(a.toDouble()).toFloat() * d
        val y = p.y + Math.sin(a.toDouble()).toFloat() * d
        if (s.gen.blocked(s.gen.tileAt(s.wtX(x), s.wtY(y)))) return
        val r = s.rng.next()
        val kind = if (r < 0.6f) K.CK_RABBIT else if (r < 0.85f) K.CK_DEER else K.CK_WOLF
        val hp = floatArrayOf(10f, 30f, 40f)[kind]
        val c = Critter(kind, x, y, hp); c.ph = s.rng.angle()
        s.critters.add(c)
    }

    fun damageCritter(c: Critter, amt: Float) {
        if (!c.alive) return
        c.hp -= amt; s.emit("hitE", c.x, c.y)
        if (c.kind == K.CK_WOLF) c.aggro = true          // Kışkırtılır: ilk darbe savaş ilanı
        if (c.kind == K.CK_BOAR && c.hp > 0f) c.chargeT = 1.5f  // domuz yaralanınca ŞARJ eder
        if (c.hp <= 0f) {
            c.alive = false; s.emit("critterDie", c.x, c.y)
            val drop = when (c.kind) {
                K.CK_RABBIT -> mapOf("meat" to 1)
                K.CK_BOAR -> mapOf("meat" to 3, "hide" to 1)
                else -> mapOf("meat" to 2, "hide" to 1)
            }
            s.spawnBag(c.x, c.y, drop)
            s.huntPressure++
        }
    }

    private fun critStep(c: Critter, nx: Float, ny: Float, sp: Float, dt: Float) {
        val xx = c.x + nx * sp * dt; val yy = c.y + ny * sp * dt
        if (!s.isSolid(s.wtX(xx), s.wtY(yy))) { c.x = xx; c.y = yy }
    }

    private fun updateCritters(dt: Float) {
        val p = s.player
        var i = 0
        while (i < s.critters.size) {
            val c = s.critters[i]
            if (!c.alive) { s.critters.removeAt(i); continue }
            c.biteCd = maxOf(0f, c.biteCd - dt)
            val d = dist(p.x, p.y, c.x, c.y)
            if (c.kind == K.CK_BOAR && c.chargeT > 0f) {  // yaralı domuz: tek şarj
                c.chargeT -= dt
                if (p.alive && d > 16f) critStep(c, (p.x - c.x) / d, (p.y - c.y) / d, 190f, dt)
                else if (p.alive && c.biteCd <= 0f) {
                    c.biteCd = 1.2f; damagePlayer(10f, null); c.chargeT = 0f
                }
                c.moving = true; i++; continue
            }
            if (c.kind == K.CK_WOLF) {
                if (c.aggro && (!p.alive || d > 340f)) c.aggro = false
                if (c.aggro) {
                    if (d > 18f) { critStep(c, (p.x - c.x) / d, (p.y - c.y) / d, 160f, dt); c.moving = true }
                    else if (c.biteCd <= 0f) { c.biteCd = 1f; damagePlayer(15f, null) }
                    i++; continue
                }
            } else if (p.alive && d < 135f && c.kind != K.CK_BOAR) {
                if (c.alertT == 0f && !c.moving) c.alertT = 0.35f   // İRKİLME: av donar
                if (c.alertT > 0f) {
                    c.alertT -= dt; c.moving = false
                    if (c.alertT <= 0f) { c.alertT = -1f; c.moving = true }
                    i++; continue
                }
                val sp = if (c.kind == K.CK_RABBIT) 165f else 150f  // ZİKZAK kaçış
                val fx2 = (c.x - p.x) / d; val fy2 = (c.y - p.y) / d
                val zig = Math.sin(((s.t + c.ph) * 8f).toDouble()).toFloat() * 0.45f
                critStep(c, fx2 - fy2 * zig, fy2 + fx2 * zig, sp, dt)
                c.moving = true; i++; continue
            } else if (p.alive && d < 110f && c.kind == K.CK_BOAR) {
                critStep(c, (c.x - p.x) / d, (c.y - p.y) / d, 120f, dt)  // domuz ağır çekilir
                c.moving = true; i++; continue
            }
            if (c.alertT < 0f && d > 150f) c.alertT = 0f // sakinleşti: irkilme yeniden kurulur
            c.wt -= dt                                   // gezinme: yürü <-> otla
            if (c.wt <= 0f) {
                c.moving = !c.moving
                if (c.moving) {
                    val a = s.rng.angle()
                    c.wx = Math.cos(a.toDouble()).toFloat(); c.wy = Math.sin(a.toDouble()).toFloat()
                    c.wt = s.rng.range(1f, 2.5f)
                } else c.wt = s.rng.range(1.5f, 3.5f)
            }
            if (c.moving) critStep(c, c.wx, c.wy, floatArrayOf(56f, 50f, 44f, 48f)[c.kind], dt)
            i++
        }
    }

    /* ── AYLA ── */
    fun damageVillager(amt: Float) {
        val v = s.villager ?: return
        v.hp -= amt; s.emit("hitE", v.x, v.y)
        if (v.hp <= 0f) {
            if (v.carry > 0) s.spawnBag(v.x, v.y, mapOf("wood" to v.carry))
            s.villager = null; s.villagerState = 2       // isimli yurttaşlar geri gelmez
            s.toast("Ayla d\u00fc\u015ft\u00fc\u2026 isimli yurtta\u015flar geri gelmez.", 1)
            s.emit("playerDie", v.x, v.y)
        }
    }

    private fun vStep(v: Villager, tx: Float, ty: Float, dt: Float): Float {
        val l = maxOf(1f, dist(tx, ty, v.x, v.y))
        val nx = v.x + (tx - v.x) / l * 95f * dt
        val ny = v.y + (ty - v.y) / l * 95f * dt
        if (!s.isSolid(s.wtX(nx), s.wtY(ny))) { v.x = nx; v.y = ny }
        return l
    }

    private fun updateVillager(dt: Float) {
        val v = s.villager ?: return
        val h = s.heart
        if (v.state == K.VS_CAGED) return
        if (s.night && v.state != K.VS_HIDE) v.state = K.VS_HIDE
        when (v.state) {
            K.VS_HIDE -> {
                if (!s.night) { v.state = K.VS_GO_TREE; return }
                vStep(v, h.x + 20f, h.y + 14f, dt)
            }
            K.VS_GO_TREE -> {
                v.rescan -= dt
                if (!v.hasTgt && v.rescan <= 0f) { v.rescan = 1.5f; findTree(v) }
                if (!v.hasTgt) return
                if (vStep(v, v.tgtX * K.TS + 16f, v.tgtY * K.TS + 16f, dt) < 26f) v.state = K.VS_CHOP
            }
            K.VS_CHOP -> {
                val k = key(v.tgtX, v.tgtY)
                if (!v.hasTgt || s.isHarvested(k)) { v.hasTgt = false; v.state = K.VS_GO_TREE; return }
                v.chopCd -= dt
                if (v.chopCd <= 0f) {
                    v.chopCd = 1.1f
                    val tmp = HashMap<String, Int>()
                    hitNode(v.tgtX, v.tgtY, tmp)         // odun yere düşmez: omuza
                    v.carry += tmp["wood"] ?: 0
                    if (v.carry >= 2) v.state = K.VS_DELIVER
                }
            }
            K.VS_DELIVER -> {
                if (vStep(v, h.x + 22f, h.y, dt) < 28f) {
                    s.spawnBag(h.x + 24f, h.y + 10f, mapOf("wood" to v.carry))
                    v.carry = 0; v.hasTgt = false; v.state = K.VS_GO_TREE
                    s.emit("place", v.x, v.y)
                }
            }
        }
    }

    private fun findTree(v: Villager) {                  // kalbin 14 karosu içinde en yakın ağaç
        val h = s.heart
        for (r in 1..14) for (i in -r..r) {
            val cands = arrayOf(intArrayOf(h.tx + i, h.ty - r), intArrayOf(h.tx + i, h.ty + r),
                intArrayOf(h.tx - r, h.ty + i), intArrayOf(h.tx + r, h.ty + i))
            for (c in cands) {
                val rr = s.gen.resourceAt(c[0], c[1]) ?: continue
                if (rr.kind == K.R_TREE && !s.isHarvested(key(c[0], c[1]))) {
                    v.tgtX = c[0]; v.tgtY = c[1]; v.hasTgt = true; return
                }
            }
        }
    }

    /* ── YAPILAR (balista nöbeti) ── */
    private fun updateBuilds(dt: Float) {
        for (b in s.builds.values) {
            if (b.t != K.BALLISTA) continue
            b.scan += dt * 0.4f
            b.fireCd = maxOf(0f, b.fireCd - dt)
            if (b.boltT >= 0f) {
                b.boltT += dt
                if (b.boltT >= 0.22f) {                  // isabet uçuş sonunda kesinleşir
                    val tgt = b.boltTgt
                    if (tgt != null && tgt.alive && dist(tgt.x, tgt.y, b.x, b.y) < 360f)
                        damageShadow(tgt, 12f)
                    b.boltT = -1f; b.boltTgt = null
                }
            }
            if (b.fireCd > 0f || b.ammo <= 0) continue
            var best: Shadow? = null; var bd = 300f * 300f
            for (sh in s.shadows) {
                if (!sh.alive || sh.dieT >= 0f) continue
                val d2 = (sh.x - b.x) * (sh.x - b.x) + (sh.y - b.y) * (sh.y - b.y)
                if (d2 < bd) { bd = d2; best = sh }
            }
            if (best != null) {
                b.fireCd = 2.5f; b.ammo--                // plan 38: her atış stoktan
                b.scan = Math.atan2((best.y - b.y).toDouble(), (best.x - b.x).toDouble()).toFloat()
                b.boltT = 0f; b.boltTgt = best
                s.emit("bolt", b.x, b.y)
            }
        }
    }

    /* ── KESELER ── */
    private fun updateBags() {
        val p = s.player
        var i = 0
        while (i < s.bags.size) {
            val b = s.bags[i]
            if (p.alive && dist(b.x, b.y, p.x, p.y) < 24f) {
                for (e in b.loot) s.addInv(p.inv, e.key, e.value)
                s.emit("pickup", b.x, b.y)
                s.bags.removeAt(i); continue
            }
            i++
        }
    }

    /* ── DOĞUM SAYAÇLARI ── */
    private fun spawnBoar() {
        var tr = 0
        while (tr < 24) {
            val a = s.rng.angle(); val rr = s.rng.range(560f, 820f)
            val x = s.player.x + Math.cos(a.toDouble()).toFloat() * rr
            val y = s.player.y + Math.sin(a.toDouble()).toFloat() * rr
            if (!s.isSolid(s.wtX(x), s.wtY(y))) {
                val c = Critter(K.CK_BOAR, x, y, 25f); c.ph = s.rng.range(0f, 6f)
                s.critters.add(c); return
            }
            tr++
        }
    }

    private fun updateSpawners(dt: Float) {
        if (s.night) {
            if (s.waveLeft > 0) {
                s.burstT -= dt
                if (s.burstT <= 0f) { s.burstT = s.burstGap; s.waveLeft--; spawnShadow() }
            }
            s.trickleT -= dt                              // damla: 6 sn, tavan 4+gün*2≤18
            if (s.trickleT <= 0f) {
                s.trickleT = 6f
                val cap = minOf(4 + s.day * 2, 18)
                var alive = 0
                for (sh in s.shadows) if (sh.alive && sh.dieT < 0f) alive++
                if (alive < cap) spawnShadow()
            }
        } else {
            s.faunaT -= dt                                // sürdürme: tavan 6 − baskı/2
            if (s.faunaT <= 0f) {
                s.faunaT = 5f
                val target = maxOf(1, minOf(6, 6 - s.huntPressure / 2))
                var near = 0
                for (c in s.critters)
                    if (dist(c.x, c.y, s.player.x, s.player.y) < 1400f) near++
                if (near < target) {
                    if (s.rng.range(0f, 1f) < 0.18f) spawnBoar() else spawnCritter()
                }
            }
        }
    }

    /* BİNA YÜKSELTME: palisat → taş duvar → kale taşı. Hedef katmanın TAM
       maliyeti ödenir; can yeni tavana yenilenir (yatırımın ödülü). */
    fun upgradeBuild(): Int {                       // 0 hedef yok · 1 oldu · 2 altın yetmez · 3 maks
        val p = s.player
        var b = s.builds[key(s.wtX(p.x + p.fx * 42f), s.wtY(p.y + p.fy * 42f))]
        if (b == null) b = s.builds[key(s.wtX(p.x), s.wtY(p.y))]
        if (b == null) return 0
        if (SPEC[b.t] == null) return 0
        if (b.lvl >= 5) return 3
        var c = upCost(b.t, b.lvl + 1)
        c -= c * wDisc / 100                             // atölye indirimi
        val gold = p.inv["gold"] ?: 0
        if (gold < c) { s.toast("Yükseltme: " + c + " altın gerek", 0); return 2 }
        p.inv["gold"] = gold - c
        b.lvl += 1
        b.hp = bHp(b.t, b.lvl)
        s.toast(K.B_NAME.getValue(b.t) + " \u2192 Seviye " + b.lvl, 0)
        return 1
    }
}
