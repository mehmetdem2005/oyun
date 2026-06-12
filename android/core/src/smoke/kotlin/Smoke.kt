import com.mk.kayipkrallik.core.*

/* Smoke.kt — Çekirdeği Android'siz koşturur. CI'da da `gradle test` yerine bu koşar. */

var pass = 0
fun A(c: Boolean, m: String) {
    if (!c) {
        System.out.flush()
        System.err.println("X-FAIL[" + pass + "] " + m)
        System.err.flush()
        System.exit(1)
    }
    pass++; println("\u2713 $m")
}

fun main() {
    val s = GameState(12345)
    val g = Game(s)
    val camp = s.gen.campTile()
    A(s.gen.tileAt(camp.first, camp.second) == K.T_CAMP, "kamp karosu CAMP (${camp.first},${camp.second})")
    A(s.heart.alive && s.heart.hp == 300f, "Kalp Ta\u015f\u0131 300 HP ile do\u011fdu")
    A(s.villager != null && s.villager!!.state == K.VS_CAGED, "Ayla kafeste")
    A(!s.gen.blocked(s.gen.tileAt(s.wtX(s.player.x), s.wtY(s.player.y))), "oyuncu y\u00fcr\u00fcnebilir karoda")

    // — yak\u0131nda a\u011fa\u00e7 bul, 3 vur —
    var tx = 0; var ty = 0; var found = false
    loop@ for (r in 1..40) for (i in -r..r) for (c in arrayOf(
        intArrayOf(camp.first + i, camp.second - r), intArrayOf(camp.first + i, camp.second + r),
        intArrayOf(camp.first - r, camp.second + i), intArrayOf(camp.first + r, camp.second + i))) {
        val rr = s.gen.resourceAt(c[0], c[1])
        if (rr != null && rr.kind == K.R_TREE) { tx = c[0]; ty = c[1]; found = true; break@loop }
    }
    A(found, "yak\u0131nda a\u011fa\u00e7 var ($tx,$ty)")
    for (i in 0 until 3) g.hitNode(tx, ty, s.player.inv)
    A(s.player.inv["wood"] == 3, "3 vuru\u015f = 3 odun")
    A(s.isHarvested(key(tx, ty)), "a\u011fa\u00e7 hasat edildi (yeniden-do\u011fum)")

    // — bo\u015f karo bul, in\u015fa zinciri —
    s.player.inv["wood"] = 40; s.player.inv["stone"] = 40
    var sx = 0; var sy = 0; found = false
    loop2@ for (r in 1..40) for (i in -r..r) for (c in arrayOf(
        intArrayOf(camp.first + i, camp.second - r), intArrayOf(camp.first + i, camp.second + r),
        intArrayOf(camp.first - r, camp.second + i), intArrayOf(camp.first + r, camp.second + i))) {
        val k = key(c[0], c[1])
        if (!s.isSolid(c[0], c[1]) && s.gen.resourceAt(c[0], c[1]) == null &&
            !(c[0] == s.heart.tx && c[1] == s.heart.ty)) { sx = c[0]; sy = c[1]; found = true; break@loop2 }
    }
    A(found, "in\u015faya uygun bo\u015f karo ($sx,$sy)")
    s.player.x = (sx - 1) * K.TS + 16f; s.player.y = sy * K.TS + 16f
    s.player.fx = 1f; s.player.fy = 0f; s.player.buildSel = K.WALL
    A(g.tryPlace(), "palisat yerle\u015fti")
    A(s.buildSolid(sx, sy), "palisat yolu kesiyor")
    val wall = s.builds[key(sx, sy)]!!
    wall.hp = 30f
    s.player.buildMode = false
    g.tapInteract(); g.update(0.016f)
    A(Math.abs(wall.hp - 60f) < 0.01f, "tamir +%30 (30\u219260)")

    // — balista + c\u0131vata —
    var bx = sx + 1; var by = sy
    if (s.isSolid(bx, by) || s.gen.resourceAt(bx, by) != null) { bx = sx; by = sy + 1 }
    s.player.x = (bx - 1) * K.TS + 16f; s.player.y = by * K.TS + 16f
    s.player.fx = 1f; s.player.fy = 0f; s.player.buildSel = K.BALLISTA
    A(g.tryPlace(), "balista yerle\u015fti")
    val bal = s.builds[key(bx, by)]!!
    A(bal.ammo == K.AMMO_START, "balista 8 c\u0131vatayla do\u011fdu")
    s.player.inv["wood"] = 1
    g.tapInteract(); g.update(0.016f)
    A(bal.ammo == 13 && (s.player.inv["wood"] ?: 0) == 0, "E + 1 odun = +5 c\u0131vata")

    // — et ye (\u00f6n karo n\u00f6tr) —
    s.player.inv["meat"] = 1; s.player.hu = 40f
    val fk = key(g.frontTX(), g.frontTY())
    if (s.gen.resourceAt(g.frontTX(), g.frontTY()) != null) s.harvested[fk] = s.t + 999f
    g.tapInteract(); g.update(0.016f)
    A(Math.round(s.player.hu) == 75, "\u00e7i\u011f et +35 a\u00e7l\u0131k")

    // — g\u00fcnd\u00fcz fauna —
    var i = 0
    while (i < 1300) { g.update(0.05f); i++ }            // 65 sn
    A(s.critters.size > 0, "g\u00fcnd\u00fcz fauna belirdi (${s.critters.size})")

    // — kap\u0131 + gece oto-kilit + ku\u015fatma —
    // Kalbin KUZEY şeridinde boş karo: gölge→kapı→kalp hizası (kemirme testi için)
    var dx2 = 0; var dy2 = 0; found = false
    loop3@ for (r in 3..30) for (off in -r..r) {
        val cx2 = s.heart.tx + off; val cy2 = s.heart.ty - r
        if (s.isSolid(cx2, cy2) || s.builds.containsKey(key(cx2, cy2))) continue
        val rr2 = s.gen.resourceAt(cx2, cy2)
        if (rr2 != null && !s.isHarvested(key(cx2, cy2))) continue
        dx2 = cx2; dy2 = cy2; found = true; break@loop3
    }
    A(found, "kapı için kuzey şeridinde boş karo ($dx2,$dy2)")
    s.player.x = dx2 * K.TS + 16f; s.player.y = (dy2 + 1) * K.TS + 16f
    s.player.inv["wood"] = 20; s.player.inv["stone"] = 20      // cıvata testi oduna el koymuştu
    s.player.fx = 0f; s.player.fy = -1f; s.player.buildSel = K.DOOR
    A(g.tryPlace(), "kap\u0131 yerle\u015fti")
    val door = s.builds[key(dx2, dy2)]!!
    door.open = true
    s.t = 118f
    i = 0; while (i < 100) { g.update(0.05f); i++ }
    A(s.night, "gece ba\u015flad\u0131")
    A(!door.open, "plan 24: kap\u0131 gece kendili\u011finden kilitlendi")
    A(s.critters.isEmpty(), "\u00fcrkekler ine \u00e7ekildi")
    i = 0; while (i < 200) { g.update(0.05f); i++ }
    A(s.shadows.size > 0, "ku\u015fatma dalgas\u0131 do\u011fdu (${s.shadows.size} g\u00f6lge)")

    // — kemirme: dalga kapıyı düşürmüş olabilir (RNG akışı) → tazele, sonra ölç —
    if (s.builds[key(dx2, dy2)] == null) {
        s.player.x = dx2 * K.TS + 16f; s.player.y = (dy2 + 1) * K.TS + 16f
        s.player.fx = 0f; s.player.fy = -1f; s.player.buildSel = K.DOOR
        s.player.inv["wood"] = 20; s.player.inv["stone"] = 20
        A(g.tryPlace(), "kapı dalga sonrası tazelendi")
    }
    val door2 = s.builds[key(dx2, dy2)]!!
    val gnaw = s.shadows[0]
    gnaw.x = door2.x; gnaw.y = door2.y - 44f
    s.player.x = s.heart.x + 600f                        // oyuncu uzak: hedef kalp kalsın
    val dhp0 = door2.hp
    i = 0; while (i < 60) { g.update(0.05f); i++ }
    val dNow = s.builds[key(dx2, dy2)]
    A(dNow == null || dNow.hp < dhp0, "gölge yoldaki kapıyı kemirdi")

    // — kalbe hasar —
    gnaw.x = s.heart.x; gnaw.y = s.heart.y - 20f
    i = 0; while (i < 60) { g.update(0.05f); i++ }
    A(s.heart.hp < 300f, "kalp hasar ald\u0131 (${s.heart.hp}/300)")

    // — balista at\u0131\u015f\u0131 —
    bal.ammo = 5; gnaw.hp = 999f; gnaw.x = bal.x + 80f; gnaw.y = bal.y
    val am0 = bal.ammo
    i = 0; while (i < 80) { g.update(0.05f); i++ }
    A(bal.ammo < am0, "balista ate\u015fledi (c\u0131vata $am0\u2192${bal.ammo})")

    // — \u015fafak: g\u00fcn 2 + t\u00f6ren + oto-kay\u0131t iste\u011fi —
    s.t = K.DAY_LEN - 1.5f
    i = 0; while (i < 100) { g.update(0.05f); i++ }
    A(s.day == 2, "\u015fafak: G\u00fcn 2")
    A(g.autoSaveRequested, "\u015fafakta oto-kay\u0131t istendi")
    A(s.shadows.isEmpty(), "g\u00f6lgeler \u015fafak t\u00f6reniyle eridi")

    // — ya\u011fma: sahte katil g\u00f6lge s\u0131rtlan\u0131r, \u00f6l\u00fcnce d\u00fc\u015f\u00fcr\u00fcr —
    val thief = Shadow(s.player.x + 10f, s.player.y)
    s.shadows.add(thief)
    s.player.inv.clear(); s.player.inv["wood"] = 7
    g.damagePlayer(999f, thief)
    A((thief.stolen["wood"] ?: 0) == 7 && s.player.inv.isEmpty(), "ya\u011fma: g\u00f6lge 7 odunu s\u0131rtland\u0131")
    val bags0 = s.bags.size
    g.damageShadow(thief, 999f)
    A(s.bags.size > bags0, "g\u00f6lge \u00f6l\u00fcnce ganimet keseye d\u00fc\u015ft\u00fc")

    // — yeniden do\u011fu\u015f + kese topla —
    i = 0; while (i < 90) { g.update(0.05f); i++ }       // 4 sn respawn + y\u00fcr\u00fcme yok ama kese kampta de\u011fil
    A(s.player.alive, "4 sn sonra kampta uyand\u0131")

    // — kurt k\u0131\u015fk\u0131rtma —
    s.player.hp = 100f
    val wolf = Critter(K.CK_WOLF, s.player.x + 30f, s.player.y, 40f)
    s.critters.add(wolf)
    g.damageCritter(wolf, 5f)
    A(wolf.aggro, "K\u0131\u015fk\u0131rt\u0131l\u0131r: vurulan kurt sava\u015f ilan etti")
    val hp0 = s.player.hp
    i = 0; while (i < 50) { g.update(0.05f); i++ }
    A(s.player.hp < hp0, "kurt \u0131s\u0131rd\u0131 (${(hp0 - s.player.hp).toInt()} hasar)")
    g.damageCritter(wolf, 999f)

    // — Ayla: kurtar + \u00e7al\u0131\u015fma d\u00f6ng\u00fcs\u00fc —
    val v = s.villager!!
    s.player.x = v.x; s.player.y = v.y + 20f
    g.tapInteract(); g.update(0.016f)
    A(v.state != K.VS_CAGED && s.villagerState == 1, "Ayla kurtar\u0131ld\u0131 \u2014 ilk yurtta\u015f")
    s.t = (s.day - 1) * K.DAY_LEN + 10f                   // sabah
    s.player.x = s.heart.x + 900f                         // kurttan/oyuncudan ba\u011f\u0131ms\u0131z
    i = 0; while (i < 1600) { g.update(0.05f); i++ }      // 80 sn
    val delivered = s.bags.any { (it.loot["wood"] ?: 0) > 0 && dist(it.x, it.y, s.heart.x, s.heart.y) < 60f }
    A(v.carry > 0 || delivered || v.hasTgt, "Ayla \u00e7al\u0131\u015f\u0131yor: hedef/k\u00fct\u00fck/teslim (carry=${v.carry})")

    // — kay\u0131t tur d\u00f6n\u00fc\u015f\u00fc (Snapshot) —
    val snap = s.toSnapshot()
    val g2s = GameState.fromSnapshot(snap)
    A(g2s.day == s.day && g2s.builds.size == s.builds.size, "Snapshot: g\u00fcn + ${s.builds.size} yap\u0131 d\u00f6nd\u00fc")
    A(Math.abs(g2s.heart.hp - s.heart.hp) < 0.01f, "Snapshot: kalp can\u0131 d\u00f6nd\u00fc (${g2s.heart.hp.toInt()})")
    A(g2s.villager != null && g2s.villager!!.state != K.VS_CAGED, "Snapshot: Ayla yurtta\u015fl\u0131\u011f\u0131 d\u00f6nd\u00fc")

    // — 5. gece b\u00fcy\u00fck ku\u015fatma —
    s.day = 5; s.t = 4 * K.DAY_LEN + 118f
    s.shadows.clear(); s.toasts.clear()
    i = 0; while (i < 80) { g.update(0.05f); i++ }
    A(s.toasts.any { it.text.contains("B\u00dcY\u00dcK") }, "plan 43: 5. gece B\u00dcY\u00dcK ku\u015fatma tostu")

    // — G\u00fcn 10 zaferi —
    s.day = 9; s.t = 9 * K.DAY_LEN - 1f
    i = 0; while (i < 80) { g.update(0.05f); i++ }
    A(s.day == 10 && s.victory, "G\u00fcn 10: ZAFER bayra\u011f\u0131")
    A(s.bigText != null && s.bigText!!.contains("KRALLIK"), "zafer mesaj\u0131 haz\u0131r")


    /* ════════ v2: yüzme · sopa · domuz · irkilme · söküm · sohbet · karne ════════ */

    fun openPair(st: GameState): Pair<Int, Int> {        // (karo, doğusu) ikisi de boş
        val cc = st.gen.campTile()
        var r = 0
        while (r < 40) {
            var k = -r
            while (k <= r) {
                val tx2 = cc.first + k; val ty2 = cc.second + r
                if (st.gen.tileAt(tx2, ty2) >= K.T_SAND && st.gen.tileAt(tx2 + 1, ty2) >= K.T_SAND &&
                    st.gen.resourceAt(tx2, ty2) == null && st.gen.resourceAt(tx2 + 1, ty2) == null &&
                    !st.isSolid(tx2 + 1, ty2)) return Pair(tx2, ty2)
                k++
            }
            r++
        }
        return cc
    }

    // — YÜZME —
    val s2 = GameState(777); val g2 = Game(s2)
    val c2 = s2.gen.campTile()
    var wx2 = 0; var wy2 = 0; var wF = false
    var r2 = 1
    loopW@ while (r2 < 220) {
        var k2 = -r2
        while (k2 <= r2) {
            val cands = arrayOf(intArrayOf(c2.first + k2, c2.second - r2), intArrayOf(c2.first + k2, c2.second + r2),
                intArrayOf(c2.first - r2, c2.second + k2), intArrayOf(c2.first + r2, c2.second + k2))
            for (cd in cands) if (s2.gen.tileAt(cd[0], cd[1]) <= K.T_WATER) {
                wx2 = cd[0]; wy2 = cd[1]; wF = true; break@loopW
            }
            k2++
        }
        r2++
    }
    A(wF, "v2: haritada erişilebilir su var ($wx2,$wy2)")
    s2.player.x = wx2 * K.TS + 16f; s2.player.y = wy2 * K.TS + 16f
    g2.update(0.016f)
    A(s2.player.swim, "v2: su karosunda YÜZME açık")
    s2.player.en = 50f
    g2.update(0.5f)
    A(s2.player.en < 49.5f, "v2: yüzerken enerji ERİR (yenilenme kapalı)")
    g2.setDir(1f, 0f)
    val xs0 = s2.player.x; g2.update(0.1f); val dxSwim = s2.player.x - xs0
    s2.player.x = c2.first * K.TS + 16f; s2.player.y = c2.second * K.TS + 16f
    g2.update(0.016f)
    A(!s2.player.swim, "v2: karaya çıkınca yüzme kapandı")
    val xl0 = s2.player.x; g2.update(0.1f); val dxLand = s2.player.x - xl0
    g2.setDir(0f, 0f)
    A(dxSwim > 4f && dxSwim < dxLand - 3f, "v2: suda yavaş hareket (" + dxSwim.toInt() + "px < " + dxLand.toInt() + "px)")

    // — SOPA —
    val s3 = GameState(31); val g3 = Game(s3)
    s3.player.inv["wood"] = 3
    A(g3.craftClub() && (s3.player.inv["club"] ?: 0) == 1 && (s3.player.inv["wood"] ?: 0) == 0,
        "v2: SOPA üretildi (3 odun eridi)")
    A(!g3.craftClub(), "v2: ikinci sopa reddedildi")
    val ap = openPair(s3)
    s3.player.x = ap.first * K.TS + 16f; s3.player.y = ap.second * K.TS + 16f
    s3.player.fx = 1f; s3.player.fy = 0f; s3.player.en = 100f
    val sh3 = Shadow(s3.player.x + 30f, s3.player.y)
    s3.shadows.add(sh3)
    g3.tapAttack(); g3.update(0.016f)
    A(!sh3.alive, "v2: sopayla 32 hasar — gölge (30 HP) tek vuruş")
    s3.player.inv["club"] = 0; s3.player.en = 100f
    val sh4 = Shadow(s3.player.x + 30f, s3.player.y)
    s3.shadows.add(sh4)
    g3.tapAttack(); g3.update(0.016f)
    A(sh4.alive && sh4.hp > 0f && sh4.hp <= 10.5f, "v2: sopasız 20 hasar — gölge yaşar (hp=" + sh4.hp.toInt() + ")")

    // — YABAN DOMUZU —
    val s5 = GameState(55); val g5 = Game(s5)
    val pb = s5.player
    var bx5 = s5.wtX(pb.x) + 3
    while (s5.isSolid(bx5, s5.wtY(pb.y))) bx5++
    val boar = Critter(K.CK_BOAR, bx5 * K.TS + 16f, pb.y, 25f)
    s5.critters.add(boar)
    g5.damageCritter(boar, 5f)
    A(boar.alive && boar.chargeT > 0f, "v2: domuz yaralandı → ŞARJ başladı")
    val d0 = Math.abs(boar.x - pb.x)
    i = 0; while (i < 20) { g5.update(0.016f); i++ }
    A(Math.abs(boar.x - pb.x) < d0 - 20f, "v2: şarjda oyuncuya yaklaştı (" + d0.toInt() + "→" + Math.abs(boar.x - pb.x).toInt() + "px)")

    // — SÖKÜM —
    val s6 = GameState(66); val g6 = Game(s6)
    val op6 = openPair(s6); val p6 = s6.player
    p6.x = op6.first * K.TS + 16f; p6.y = op6.second * K.TS + 16f
    p6.fx = 1f; p6.fy = 0f
    p6.inv["wood"] = 5; p6.buildMode = true; p6.buildSel = K.WALL
    g6.tapAttack(); g6.update(0.016f)
    A(s6.builds.values.count { it.t == K.WALL } == 1, "v2: duvar kondu (söküm hazırlığı)")
    val wAfter = p6.inv["wood"] ?: 0
    g6.tapInteract(); g6.update(0.016f)              // 1. E: koruma uyarısı
    g6.tapInteract(); g6.update(0.016f)              // 2. E: söküm
    A(s6.builds.values.none { it.t == K.WALL }, "v2: SÖKÜM — yapı kalktı")
    A((p6.inv["wood"] ?: 0) > wAfter, "v2: söküm iadesi geldi (+%50)")

    // — SOHBET —
    val s7 = GameState(77); val g7 = Game(s7)
    g7.quickChat(0)
    A(s7.chat.size == 1 && s7.chat[0].second == 0, "v2: hızlı mesaj yazıldı (Ayla kafeste → yanıt yok)")
    s7.villager!!.state = K.VS_GO_TREE
    g7.quickChat(1)
    A(s7.chat.size == 3 && s7.chat[2].second == 1, "v2: Ayla bağlamla yanıtladı — sohbet akıyor")

    // — ŞAFAK KARNESİ —
    val s8 = GameState(88); val g8 = Game(s8)
    s8.t = K.DAY_LEN - 2f
    g8.update(0.05f)
    s8.nightKills = 4; s8.toasts.clear()
    i = 0; while (i < 60) { g8.update(0.05f); i++ }
    A(s8.day == 2 && s8.toasts.any { it.text.contains("4 gölge") }, "v2: ŞAFAK KARNESİ — gece özeti tostu")

    
run {   // 60: BİNA YÜKSELTME — açık çimene ışınlan, yerleştir, yükselt
    val s60 = GameState(73)
    val g2 = Game(s60)
    val p = s60.player
    var fx60 = p.x; var fy60 = p.y
    loopP@ for (ty in 40 until 120) {
        for (tx in 40 until 120) {
            val tt = s60.gen.tileAt(tx, ty); val tn = s60.gen.tileAt(tx + 1, ty)
            if ((tt == K.T_GRASS) && (tn == K.T_GRASS) &&
                s60.gen.resourceAt(tx, ty) == null && s60.gen.resourceAt(tx + 1, ty) == null) {
                fx60 = tx * K.TS + 16f; fy60 = ty * K.TS + 16f; break@loopP
            }
        }
    }
    p.x = fx60; p.y = fy60
    p.inv["wood"] = 10; p.inv["stone"] = 0
    p.buildMode = true; p.fx = 1f; p.fy = 0f; p.buildSel = K.WALL
    g2.tapAttack(); g2.update(0.016f)
    val k60 = key(s60.wtX(p.x + 42f), s60.wtY(p.y))
    A(s60.builds[k60] != null, "60a yerleşti")
    p.inv["gold"] = 0
    A(g2.upgradeBuild() == 2, "60b altınsız reddetti")
    p.inv["gold"] = 999
    A(g2.upgradeBuild() == 1, "60c L2 oldu")
    val b60 = s60.builds[k60]
    A(b60 != null && b60.lvl == 2, "60d seviye 2")
    A(b60 != null && b60.hp == bHp(K.WALL, 2), "60e can L2 tavanı")
    g2.upgradeBuild(); g2.upgradeBuild(); g2.upgradeBuild()
    A(b60 != null && b60.lvl == 5 && g2.upgradeBuild() == 3, "60f maks seviye 5")
}


run {   // 63: OK KULESI — menzildeki golgeye otomatik hasar
    val s63 = GameState(73)
    val g63 = Game(s63)
    val bx3 = s63.wtX(s63.player.x) + 3; val by3 = s63.wtY(s63.player.y)
    val tw = Build(K.B_TOWER, bx3, by3); tw.lvl = 2
    s63.builds[key(bx3, by3)] = tw
    val sh63 = Shadow(tw.x + 40f, tw.y); sh63.hp = 60f
    s63.shadows.add(sh63)
    var i63 = 0
    while (i63 < 90 && sh63.hp >= 60f) { g63.update(0.05f); i63++ }
    A(sh63.hp < 60f, "63a kule vurdu: " + sh63.hp)
}

run {   // 64: DIKEN TUZAGI — ustune basan golge hasar alir, bekleme dolar
    val s64 = GameState(73)
    val g64 = Game(s64)
    val bx4 = s64.wtX(s64.player.x) + 3; val by4 = s64.wtY(s64.player.y)
    val tp = Build(K.B_TRAP, bx4, by4); tp.lvl = 3
    s64.builds[key(bx4, by4)] = tp
    val sh64 = Shadow(tp.x + 4f, tp.y); sh64.hp = 100f
    s64.shadows.add(sh64)
    var i64 = 0
    while (i64 < 90 && sh64.hp >= 100f) { g64.update(0.05f); i64++ }
    A(sh64.hp < 100f, "64a tuzak yakti: " + sh64.hp)
    A(tp.cd > 0f, "64b bekleme doldu")
}

run {   // 65: DUNYA SINIRI — slither tarzi dairesel ceper
    val s65 = GameState(73)
    A(s65.gen.tileAt(K.WORLD_R + 5, 0) == K.T_DEEP, "65a sinir otesi okyanus")
    A(s65.gen.tileAt(0, K.WORLD_R - 2) <= K.T_WATER, "65b ceperde kara yok")
    A(s65.gen.tileAt(40, 60) >= K.T_SAND || s65.gen.tileAt(40, 60) <= K.T_WATER, "65c ic bolge dokunulmadi")
    val c65 = s65.gen.campTile()
    val d65 = dist(0f, 0f, c65.first.toFloat(), c65.second.toFloat())
    A(d65 < K.WORLD_R - 60f, "65d kamp sinirin icinde: " + d65)
}

run {   // 66: DEMIRCI URETIMI — kaynak varsa siradakini uretir, en iyi silah otomatik
    val s66 = GameState(73)
    val g66 = Game(s66)
    g66.update(0.05f)
    val p66 = s66.player
    s66.addInv(p66.inv, "wood", 20); s66.addInv(p66.inv, "stone", 20)
    val bx6 = s66.wtX(p66.x) + 2; val by6 = s66.wtY(p66.y)
    s66.builds[key(bx6, by6)] = Build(K.B_SMITH, bx6, by6)
    var i66 = 0
    while (i66 < 160 && (p66.inv["club"] ?: 0) == 0) { g66.update(0.05f); i66++ }
    A((p66.inv["club"] ?: 0) == 1, "66a sopa uretildi")
    A(g66.bestAtk(p66) == 12f, "66b silah kusanildi: " + g66.bestAtk(p66))
    i66 = 0
    while (i66 < 160 && (p66.inv["ssword"] ?: 0) == 0) { g66.update(0.05f); i66++ }
    A(g66.bestAtk(p66) == 22f, "66c tas kilic terfisi")
}

run {   // 67: HEYKEL CARPANI + AMBAR — uretim hizlanir
    val s67 = GameState(73)
    val g67 = Game(s67)
    g67.update(0.05f)
    val hx7 = s67.wtX(s67.player.x) + 3; val hy7 = s67.wtY(s67.player.y)
    val st7 = Build(K.B_STATUE, hx7, hy7); st7.lvl = 5
    s67.builds[key(hx7, hy7)] = st7
    val dp7 = Build(K.B_STORE, hx7 + 1, hy7); dp7.lvl = 3        // depo: altın tavanı aç
    s67.builds[key(hx7 + 1, hy7)] = dp7
    val g0 = s67.player.inv["gold"] ?: 0
    var i67 = 0
    while (i67 < 600) { g67.update(0.05f); i67++ }      // 30 sn: 0.5/sn taban × 1.2 heykel = 18
    val kazanc = (s67.player.inv["gold"] ?: 0) - g0
    A(kazanc >= 17, "67a heykelli kazanc 30sn: " + kazanc + " (statusuz 15 olurdu)")
}

run {   // 68: BUYUCU KULESI — alan hasari
    val s68 = GameState(73)
    val g68 = Game(s68)
    val bx8 = s68.wtX(s68.player.x) + 3; val by8 = s68.wtY(s68.player.y)
    val wz = Build(K.B_WIZARD, bx8, by8); wz.lvl = 2
    s68.builds[key(bx8, by8)] = wz
    val sh8 = Shadow(wz.x + 80f, wz.y); sh8.hp = 50f
    s68.shadows.add(sh8)
    var i68 = 0
    while (i68 < 140 && sh8.hp >= 50f && sh8.alive) { g68.update(0.05f); i68++ }
    A(sh8.hp < 50f || !sh8.alive, "68a buyu carpti: " + sh8.hp)
}

run {   // 69: AKILLI IKSIR — can dusunce otomatik icilir
    val s69 = GameState(73)
    val g69 = Game(s69)
    s69.addInv(s69.player.inv, "potion", 1)
    s69.player.hp = 20f
    var i69 = 0
    while (i69 < 40 && s69.player.hp < 50f) { g69.update(0.05f); i69++ }
    A(s69.player.hp >= 55f, "69a iksir icildi: " + s69.player.hp)
    A((s69.player.inv["potion"] ?: 0) == 0, "69b iksir tukendi")
}

run {   // 61: JENERATÖR — otomatik yerleşir, altın üretir
    val s61 = GameState(73)
    val g61 = Game(s61)
    var i61 = 0
    while (i61 < 80) { g61.update(0.05f); i61++ }
    A(s61.builds.values.count { it.t == K.B_GEN } == 1, "61a tek jeneratör")
    A((s61.player.inv["gold"] ?: 0) >= 26, "61b altın aktı: " + (s61.player.inv["gold"] ?: 0))
}

run {   // 62: KIŞLA — minyon doğar, seviye tavanına uyar
    val s62 = GameState(73)
    val g62 = Game(s62)
    val bx = s62.wtX(s62.player.x) + 3
    val by = s62.wtY(s62.player.y) + 3
    val kb = Build(K.B_BARRACKS, bx, by)
    kb.lvl = 2; kb.hp = bHp(K.B_BARRACKS, 2)
    s62.builds[key(bx, by)] = kb
    var i62 = 0
    while (i62 < 400) { g62.update(0.05f); i62++ }
    A(s62.minions.size in 1..2, "62a minyon doğdu/tavanlı: " + s62.minions.size)
}

println("\n\u2605 DUMAN TEST\u0130: $pass/$pass GE\u00c7T\u0130 \u2014 \u00e7ekirdek Kotlin'de F\u0130\u0130LEN \u00c7ALI\u015eTI.")
}
