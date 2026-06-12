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
    A(s6.builds.size == 1, "v2: duvar kondu (söküm hazırlığı)")
    val wAfter = p6.inv["wood"] ?: 0
    g6.tapInteract(); g6.update(0.016f)
    A(s6.builds.isEmpty(), "v2: SÖKÜM — yapı kalktı")
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

    println("\n\u2605 DUMAN TEST\u0130: $pass/$pass GE\u00c7T\u0130 \u2014 \u00e7ekirdek Kotlin'de F\u0130\u0130LEN \u00c7ALI\u015eTI.")
}
