package com.mk.kayipkrallik.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.mk.kayipkrallik.core.ChatPort
import com.mk.kayipkrallik.core.Build
import com.mk.kayipkrallik.core.Critter
import com.mk.kayipkrallik.core.Game
import com.mk.kayipkrallik.core.GameState
import com.mk.kayipkrallik.core.K
import com.mk.kayipkrallik.core.Player
import com.mk.kayipkrallik.core.Shadow
import com.mk.kayipkrallik.core.key

/**
 * KABUK — çekirdek (core) hiçbir Android sınıfı görmez; bu dosya tam tersi:
 * yalnız ÇİZER, DUYURUR, DOKUNUŞU İLETİR. Karar veren tek yer Game.update().
 *
 * Mimari sözleşme (Ports & Adapters):
 *   girdi  → setDir / tapAttack / tapInteract / tapBuild
 *   çıktı  ← s.sfx, s.toasts, s.bigText, s.flash, s.shakeTiles (her kare boşaltılır)
 *   kayıt  ← autoSaveRequested bayrağı (şafak); kabuk JSON'lar, bayrağı söndürür.
 *
 * Render: tek iplik, lockCanvas/unlockCanvasAndPost; dünya katmanı ZOOM ölçekli,
 * zemin 512px chunk bitmap önbelleğinde (LruCache 96), gece karartması yarı
 * çözünürlük bitmap + DST_OUT delikleriyle (CLEAR modu stub'ta yok: opak beyazı
 * DST_OUT ile basmak alfayı sıfırlar — temizleme hilesi).
 */
class GameView(ctx: Context) : SurfaceView(ctx), SurfaceHolder.Callback, Runnable {

    private val appCtx: Context = ctx

    /* ── kipler ── */
    private val M_MENU = 0; private val M_PLAY = 1; private val M_PAUSE = 2
    private var mode = M_MENU
    private var game: Game? = null

    /* ── iplik / yüzey ── */
    @Volatile private var running = false
    @Volatile private var surfaceReady = false
    private var thread: Thread? = null
    private var lastNs = 0L
    private var vw = 1f; private var vh = 1f

    /* ── kamera / dünya görünümü ── */
    private var Z = 1.6f                               // dünya yakınlığı (dikeyde daha geniş kadraj)
    private var camX = 0f; private var camY = 0f

    /* ── boyalar (tahsis her karede DEĞİL, bir kez) ── */
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clearP = Paint()                       // DST_OUT temizleyici
    private val holeP = Paint()                        // ışık deliği (gradyan + DST_OUT)
    private val glowP = Paint()                        // altın nefes (ADD)

    /* ── zemin önbelleği: chunk = 16 karo × 32 px = 512 px ── */
    private val chunkPx = 512
    private val chunks = LruCache<Long, Bitmap>(96)

    /* ── gece katmanı (yarı çözünürlük) ── */
    private var nightBmp: Bitmap? = null
    private var nightCv: Canvas? = null

    /* ── girdi ── */
    private var joyId = -1; private var joyOx = 0f; private var joyOy = 0f
    private var joyVx = 0f; private var joyVy = 0f
    private var atkId = -1; private var atkHeld = false
    private val btnRects = ArrayList<Pair<RectF, Int>>()   // menü/duraklatma vuruş alanları

    /* ── HUD olay yerelleri ── */
    private class Tst(val text: String, val color: Int) { var t = 2.7f }
    private val toastQ = ArrayList<Tst>()
    private var bigTxt: String? = null; private var bigCol = 0; private var bigT = 0f
    private var flash = 0f; private var shakeT = 0f
    private var uiT = 0f                               // menü animasyon saati

    private val snd = Sound()

    init {
        holder.addCallback(this)
        setKeepScreenOn(true)
        clearP.setXfermode(PorterDuffXfermode(PorterDuff.Mode.DST_OUT))
        holeP.setXfermode(PorterDuffXfermode(PorterDuff.Mode.DST_OUT))
        glowP.setXfermode(PorterDuffXfermode(PorterDuff.Mode.ADD))
    }

    /* ════════════ YAŞAM DÖNGÜSÜ ════════════ */
    override fun surfaceCreated(h: SurfaceHolder) { surfaceReady = true; startLoop() }
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {
        vw = w.toFloat(); vh = hh.toFloat()
        us = clampF(Math.min(vw, vh) / 420f, 1.4f, 3.6f) // UI: ~420dp sanal tuval
        uw = vw / us; uh = vh / us
        applyZoom()
        nightBmp = Bitmap.createBitmap(Math.max(1, w / 2), Math.max(1, hh / 2),
            Bitmap.Config.ARGB_8888)
        nightCv = Canvas(nightBmp!!)
    }
    override fun surfaceDestroyed(h: SurfaceHolder) { surfaceReady = false; stopLoop() }

    fun onHostPause() {                                 // arka plan: kaydet, ipliği durdur
        if (mode == M_PLAY) mode = M_PAUSE
        saveNow(); stopLoop()
    }
    fun onHostResume() { if (surfaceReady) startLoop() }
    fun onHostDestroy() { snd.release() }
    fun onBack(): Boolean {                             // geri tuşu: oyun→duraklat→menü
        if (mode == M_PLAY) { mode = M_PAUSE; return true }
        if (mode == M_PAUSE) { mode = M_PLAY; return true }
        return false
    }

    /* HTML kadrajı: kısa kenara ~N karo sığar (ayar: yakın 8.5 / orta 10.5 / uzak 13) */
    private fun applyZoom() {
        if (vw <= 1f) return
        val tiles = floatArrayOf(8.5f, 10.5f, 13f)[zoomMode]
        Z = clampF(Math.min(vw, vh) / (tiles * K.TS), 2.0f, 5.0f)
    }
    private fun startLoop() {
        if (running) return
        running = true; lastNs = System.nanoTime()
        val t = Thread(this, "kk-render"); thread = t; t.start()
    }
    private fun stopLoop() {
        running = false
        try { thread?.join(400) } catch (e: Exception) { }
        thread = null
    }

    /* ════════════ ANA DÖNGÜ ════════════ */
    override fun run() {
        while (running) {
            val now = System.nanoTime()
            var dt = (now - lastNs) / 1.0e9f; lastNs = now
            if (dt > 0.06f) dt = 0.06f; if (dt < 0f) dt = 0f
            uiT += dt
            if (mode == M_PLAY) step(dt) else decayHud(dt)
            if (surfaceReady) {
                val c = holder.lockCanvas()
                if (c != null) {
                    try { drawAll(c) } finally { holder.unlockCanvasAndPost(c) }
                } else sleep8()
            } else sleep8()
        }
    }
    private fun sleep8() { try { Thread.sleep(8) } catch (e: Exception) { } }

    /* ── v2 görsel/etkileşim durumu (sıcak döngüde sıfır tahsis ilkesi) ── */
    private class Pcl { var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
        var l = 0f; var ml = 1f; var col = 0; var sz = 2f; var on = false }
    private val pcl = Array(96) { Pcl() }               // önceden tahsisli parçacık havuzu
    private val ripples = ArrayList<FloatArray>()        // yüzme halkaları: x,y,doğumZamanı
    private var ripT = 0f
    private var chatOpen = false; private var invOpen = false
    private val msgRects = ArrayList<Pair<RectF, Int>>()
    private var hotbarRect = RectF(0f, 0f, 0f, 0f)
    private val buildRects = ArrayList<Pair<RectF, Int>>()
    private var wt = 0f                                  // yürüyüş fazı (yalnız çizim)
    private var sprInit = false
    private var us = 2.4f; private var uw = 1f; private var uh = 1f   // UI ölçeği + sanal tuval
    private var zoomMode = 1                             // 0 yakın · 1 orta · 2 uzak (ayarlardan)
    private val spp = Paint()                            // sprite boyası
    private var mmBmp: Bitmap? = null; private var mmCv: Canvas? = null
    private var mmT = 0f; private val mmP = Paint()      // minimap tamponu
    private fun spr(c: Canvas, names: Array<String>, t: Float, fps: Float,
                    x: Float, y: Float, w: Float, flip: Boolean, alpha: Int): Boolean {
        var k = 0
        while (k < names.size) {
            if (Sprites.has(names[k])) {
                spp.setAlpha(alpha)
                Sprites.draw(c, names[k], t, fps, x, y, w, flip, spp)
                return true
            }
            k++
        }
        return false
    }
    private val p2c = Paint()
    private fun p2(w: Float, col: Int): Paint {
        p2c.setStyle(Paint.Style.STROKE); p2c.setStrokeCap(Paint.Cap.ROUND)
        p2c.setStrokeWidth(w); p2c.setColor(col); return p2c
    }

    private fun decayHud(dt: Float) {
        flash = Math.max(0f, flash - dt * 1.4f)
        shakeT = Math.max(0f, shakeT - dt)
        bigT = Math.max(0f, bigT - dt)
        var i = toastQ.size - 1
        while (i >= 0) { toastQ[i].t -= dt; if (toastQ[i].t <= 0f) toastQ.removeAt(i); i-- }
    }

    /** Bir simülasyon adımı + çekirdek→kabuk olay kuyruklarının boşaltılması. */
    private fun step(dt: Float) {
        val g = game ?: return
        if (!sprInit) {
            sprInit = true
            Sprites.load(appCtx)
            zoomMode = appCtx.getSharedPreferences("kk_ui", Context.MODE_PRIVATE).getInt("zoom", 1)
            applyZoom()
        }
        if (g.chatPort == null)                          // yerel sohbet adaptörü (port bağlama)
            g.chatPort = object : ChatPort { override fun send(text: String, who: Int) { } }
        if (atkHeld) g.tapAttack()                      // basılı tut = seri vuruş (çekirdek cd'li)
        g.setDir(joyVx, joyVy)
        if (Math.abs(joyVx) + Math.abs(joyVy) > 0.05f) wt += dt * 11f
        if (!chatOpen && !invOpen) g.update(dt)          // paneller oyunu dondurur
        val s = g.s
        var i = 0
        while (i < s.sfx.size) {
            snd.play(s.sfx[i].name)
            spawnFx(s.sfx[i].name, s.sfx[i].x, s.sfx[i].y)
            i++
        }
        s.sfx.clear()
        updateFx(dt, s)
        i = 0
        while (i < s.toasts.size) {
            toastQ.add(Tst(s.toasts[i].text, s.toasts[i].color))
            if (toastQ.size > 4) toastQ.removeAt(0)
            i++
        }
        s.toasts.clear()
        val bt = s.bigText
        if (bt != null) { bigTxt = bt; bigCol = s.bigColor; bigT = 6f; s.bigText = null }
        if (s.flash > flash) flash = s.flash
        s.flash = 0f
        if (s.shakeTiles.isNotEmpty()) { shakeT = 0.22f; s.shakeTiles.clear() }
        s.dirtyChunks.clear()                           // zemin değişmez; kaynak canlı çizilir
        mmT -= dt
        if (mmT <= 0f) { mmT = 0.6f; updateMinimap(s) }
        if (g.autoSaveRequested) { g.autoSaveRequested = false; saveNow() }
        decayHud(dt)
        // kamera: oyuncuyu merkezde tutan yumuşak takip
        val txc = s.player.x - vw / (2f * Z)
        val tyc = s.player.y - vh / (2f * Z)
        val k = Math.min(1f, 8f * dt)
        camX += (txc - camX) * k; camY += (tyc - camY) * k
    }

    /* ════════════ OYUN AKIŞI ════════════ */
    private fun newGame() {
        val seed = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        game = Game(GameState(seed))
        snapCam(); mode = M_PLAY
        toastQ.clear(); bigTxt = null; flash = 0f
        chunks.evictAll()
    }
    private fun continueGame() {
        val snap = SaveStore.load(appCtx) ?: return
        game = Game(GameState.fromSnapshot(snap))
        snapCam(); mode = M_PLAY
        toastQ.clear(); bigTxt = null; flash = 0f
        chunks.evictAll()
        toastQ.add(Tst("Kayıt yüklendi — hoş geldin!", 0))
    }
    private fun snapCam() {
        val s = game!!.s
        camX = s.player.x - vw / (2f * Z); camY = s.player.y - vh / (2f * Z)
    }
    private fun saveNow() {
        val g = game ?: return
        SaveStore.save(appCtx, g.s.toSnapshot())
    }

    /* ════════════ ÇİZİM KÖKÜ ════════════ */
    private fun drawAll(c: Canvas) {
        c.drawColor(rgb(10, 14, 20))
        if (mode == M_MENU || game == null) { drawMenu(c); return }
        val s = game!!.s
        // ekran sarsıntısı: deterministik sinüs (rastgele tahsisi yok)
        val shx = if (shakeT > 0f) sinF(s.t * 91f) * 9f * shakeT else 0f
        val shy = if (shakeT > 0f) sinF(s.t * 113f + 2f) * 9f * shakeT else 0f
        c.save(); c.translate(shx, shy)
        drawWorld(c, s)
        drawWater(c, s)
        drawFx(c, s)
        drawNight(c, s)
        c.restore()
        drawHud(c, s)
        if (mode == M_PAUSE) drawPause(c)
    }

    /* ── dünya: zemin chunk'ları + y-sıralı varlıklar ── */
    private class Dr(val y: Float, val f: (Canvas) -> Unit)

    private fun drawWorld(c: Canvas, s: GameState) {
        c.save(); c.scale(Z, Z); c.translate(-camX, -camY)
        val viewW = vw / Z; val viewH = vh / Z
        val cx0 = floorI(camX / chunkPx) - 1
        val cx1 = floorI((camX + viewW) / chunkPx) + 1
        val cy0 = floorI(camY / chunkPx) - 1
        val cy1 = floorI((camY + viewH) / chunkPx) + 1
        var cy = cy0
        while (cy <= cy1) {
            var cx = cx0
            while (cx <= cx1) {
                var b = chunks.get(key(cx, cy))
                if (b == null) { b = buildChunk(s, cx, cy); chunks.put(key(cx, cy), b) }
                c.drawBitmap(b, cx * chunkPx.toFloat(), cy * chunkPx.toFloat(), null)
                cx++
            }
            cy++
        }
        // — varlık toplama (görünür pencere + pay) —
        val list = ArrayList<Dr>(64)
        val wx0 = camX - 80f; val wx1 = camX + viewW + 80f
        val wy0 = camY - 96f; val wy1 = camY + viewH + 96f
        val tx0 = floorI(wx0 / K.TS); val tx1 = floorI(wx1 / K.TS)
        val ty0 = floorI(wy0 / K.TS); val ty1 = floorI(wy1 / K.TS)
        var ty = ty0
        while (ty <= ty1) {                             // prosedürel kaynaklar (ağaç/kaya/çalı)
            var tx = tx0
            while (tx <= tx1) {
                val r = s.gen.resourceAt(tx, ty)
                if (r != null && !s.isHarvested(key(tx, ty))) {
                    val bx = tx * K.TS + K.TS / 2 + r.ox
                    val by = ty * K.TS + K.TS / 2 + r.oy
                    val hl = s.hitsLeft[key(tx, ty)]
                    val fx = tx; val fy = ty
                    list.add(Dr(by) { cv -> drawRes(cv, s, r.kind, r.variant, bx, by, hl, fx, fy) })
                }
                tx++
            }
            ty++
        }
        val h = s.heart
        if (h.x > wx0 && h.x < wx1) list.add(Dr(h.y + 6f) { cv -> drawHeart(cv, s) })
        for (b in s.builds.values)
            if (b.x > wx0 && b.x < wx1 && b.y > wy0 && b.y < wy1)
                list.add(Dr(b.y + 10f) { cv -> drawBuild(cv, s, b) })
        for (bg in s.bags)
            list.add(Dr(bg.y) { cv -> drawBag(cv, s, bg.x, bg.y) })
        val v = s.villager
        if (v != null && v.hp > 0f)
            list.add(Dr(v.y) { cv -> drawVillager(cv, s) })
        for (cr in s.critters) if (cr.alive)
            list.add(Dr(cr.y) { cv -> drawCritter(cv, s, cr) })
        for (sh in s.shadows)
            list.add(Dr(sh.y) { cv -> drawShadow(cv, s, sh) })
        list.add(Dr(s.player.y) { cv -> drawPlayer(cv, s) })
        list.sortBy { it.y }
        var i = 0
        while (i < list.size) { list[i].f(c); i++ }
        // — inşa hayaleti: çekirdeğin placeOk() kararıyla yeşil/kırmızı —
        if (s.player.buildMode && s.player.alive) {
            val g = game!!
            val gx = g.frontTX() * K.TS; val gy = g.frontTY() * K.TS
            val ok = g.placeOk()
            val a = (76 + sinF(s.t * 8f) * 30).toInt()
            p.setStyle(Paint.Style.FILL)
            p.setColor(if (ok) argb(a, 110, 230, 140) else argb(a, 255, 90, 90))
            c.drawRect(gx + 2f, gy + 2f, gx + K.TS - 2f, gy + K.TS - 2f, p)
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f)
            p.setColor(if (ok) argb(210, 110, 230, 140) else argb(210, 255, 90, 90))
            c.drawRect(gx + 2f, gy + 2f, gx + K.TS - 2f, gy + K.TS - 2f, p)
            p.setStyle(Paint.Style.FILL)
        }
        // — saldırı süpürmesi: yay boyunca solan noktalar —
        val pl = s.player
        if (pl.attackT > 0f) {
            val ang = Math.atan2(pl.fy.toDouble(), pl.fx.toDouble()).toFloat()
            val pr = 1f - pl.attackT / 0.35f
            p.setColor(argb((140 * (1f - pr)).toInt(), 255, 255, 255))
            var k2 = 0
            while (k2 < 5) {
                val a2 = ang - 0.8f + k2 * 0.4f + pr * 0.5f
                c.drawCircle(pl.x + cosF(a2) * 26f, pl.y - 8f + sinF(a2) * 26f, 2.4f, p)
                k2++
            }
        }
        c.restore()
    }

    /** 16×16 karoluk zemin parçasını bir kez boya, önbelleğe koy (KO benek dili). */
    private fun buildChunk(s: GameState, cx: Int, cy: Int): Bitmap {
        val bmp = Bitmap.createBitmap(chunkPx, chunkPx, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val pp = Paint()
        var ly = 0
        while (ly < 16) {
            var lx = 0
            while (lx < 16) {
                val tx = cx * 16 + lx; val ty = cy * 16 + ly
                val t = s.gen.tileAt(tx, ty)
                pp.setColor(when (t) {
                    K.T_DEEP -> rgb(29, 86, 143)
                    K.T_WATER -> rgb(46, 124, 196)
                    K.T_SAND -> rgb(230, 210, 138)
                    K.T_CAMP -> rgb(200, 160, 109)
                    else -> rgb(79, 174, 96)
                })
                val px = lx * 32f; val py = ly * 32f
                cv.drawRect(px, py, px + 32f, py + 32f, pp)
                val r1 = s.gen.hash01(tx, ty, 91); val r2 = s.gen.hash01(tx, ty, 92)
                val r3 = s.gen.hash01(tx, ty, 93)
                if (t == K.T_GRASS) {                   // çimen benekleri + nadir çiçek
                    pp.setColor(rgb(68, 154, 84))
                    cv.drawRect(px + (r1 * 6).toInt() * 4f, py + (r2 * 6).toInt() * 4f,
                        px + (r1 * 6).toInt() * 4f + 4f, py + (r2 * 6).toInt() * 4f + 4f, pp)
                    pp.setColor(rgb(102, 197, 120))
                    cv.drawRect(px + (r3 * 7).toInt() * 4f, py + (r1 * 7).toInt() * 4f,
                        px + (r3 * 7).toInt() * 4f + 4f, py + (r1 * 7).toInt() * 4f + 6f, pp)
                    if (r1 > 0.94f) {
                        pp.setColor(if (r2 > 0.5f) rgb(233, 217, 106) else rgb(232, 160, 192))
                        cv.drawRect(px + 14f, py + 14f, px + 18f, py + 18f, pp)
                    }
                } else if (t == K.T_SAND) {
                    pp.setColor(rgb(205, 181, 111))
                    cv.drawRect(px + (r1 * 7).toInt() * 4f, py + (r2 * 7).toInt() * 4f,
                        px + (r1 * 7).toInt() * 4f + 4f, py + (r2 * 7).toInt() * 4f + 4f, pp)
                } else if (t <= K.T_WATER) {
                    pp.setColor(if (t == K.T_WATER) rgb(42, 112, 178) else rgb(26, 77, 128))
                    cv.drawRect(px + (r1 * 6).toInt() * 4f, py + (r2 * 6).toInt() * 4f,
                        px + (r1 * 6).toInt() * 4f + 8f, py + (r2 * 6).toInt() * 4f + 4f, pp)
                } else {                                // kamp toprağı dokusu
                    pp.setColor(rgb(148, 113, 74))
                    cv.drawRect(px + (r1 * 6).toInt() * 4f, py + (r2 * 6).toInt() * 4f,
                        px + (r1 * 6).toInt() * 4f + 6f, py + (r2 * 6).toInt() * 4f + 4f, pp)
                }
                lx++
            }
            ly++
        }
        return bmp
    }

    /* ════════════ SPRITE'LAR (KO sanat dili, Canvas'a çevrildi) ════════════ */
    private fun shadowE(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        p.setColor(argb(64, 0, 0, 0))
        c.save(); c.translate(x, y); c.scale(1f, h / w)
        c.drawCircle(0f, 0f, w, p); c.restore()
    }
    private fun tri(c: Canvas, x: Float, yBase: Float, w: Float, h: Float, col: Int) {
        // Path stub'ta yok: üçgeni 6 yatay şeritle yaklaşıkla (pikselli his ✓)
        p.setColor(col)
        var i = 0
        while (i < 6) {
            val f = i / 6f
            val ww = w * (1f - f) * 0.5f
            c.drawRect(x - ww, yBase - h * (f + 1f / 6f), x + ww, yBase - h * f, p)
            i++
        }
    }
    private fun drawRes(c: Canvas, s: GameState, kind: Int, variant: Int,
                        x: Float, y: Float, hitsLeft: Int?, tx: Int, ty: Int) {
        val rk = if (kind == K.R_TREE) (if (variant == 1) "tree_1" else if (variant == 2) "tree_2" else "tree_3")
            else if (kind == K.R_ROCK) (if (variant == 1) "rock_2" else if (variant == 2) "rock_3" else "rock_1")
            else "bush"
        val sprR = spr(c, arrayOf(rk), 0f, 1f, x, y + 1f,
            if (kind == K.R_TREE) 46f else 30f, false, 255)
        if (!sprR) {
        if (kind == K.R_TREE) {
            shadowE(c, x, y + 2f, 15f, 6f)
            p.setColor(rgb(110, 74, 42))
            c.drawRect(x - 3f, y - 12f, x + 3f, y + 1f, p)
            if (variant == 1) {
                p.setColor(rgb(35, 117, 58)); c.drawCircle(x, y - 18f, 14f, p)
                p.setColor(rgb(47, 158, 79))
                c.drawCircle(x - 6f, y - 22f, 10f, p); c.drawCircle(x + 6f, y - 21f, 10f, p)
                c.drawCircle(x, y - 29f, 9f, p)
                p.setColor(rgb(73, 189, 102)); c.drawCircle(x - 4f, y - 27f, 4.5f, p)
            } else {
                val d = variant == 2
                tri(c, x, y - 5f, 30f, 16f, if (d) rgb(20, 83, 46) else rgb(31, 122, 68))
                tri(c, x, y - 15f, 24f, 14f, if (d) rgb(27, 107, 60) else rgb(42, 149, 90))
                tri(c, x, y - 24f, 16f, 12f, if (d) rgb(20, 83, 46) else rgb(31, 122, 68))
            }
        } else if (kind == K.R_ROCK) {
            val sc = if (variant == 1) 1.25f else if (variant == 2) 0.82f else 1f
            shadowE(c, x, y + 1f, 12f * sc, 5f)
            p.setColor(rgb(126, 134, 148))
            c.drawRect(x - 11f * sc, y - 10f * sc, x + 11f * sc, y + 1f, p)
            p.setColor(rgb(168, 176, 189))
            c.drawRect(x - 7f * sc, y - 15f * sc, x + 5f * sc, y - 8f * sc, p)
            p.setColor(rgb(90, 97, 109))
            c.drawRect(x - 11f * sc, y - 2f, x + 11f * sc, y + 1f, p)
            p.setColor(rgb(205, 212, 222))
            c.drawRect(x - 5f * sc, y - 14f * sc, x - 2f * sc, y - 11f * sc, p)
        } else {                                        // ÇALI — böğürtlenli
            shadowE(c, x, y + 1f, 10f, 4f)
            p.setColor(rgb(44, 122, 61))
            c.drawCircle(x, y - 6f, 9f, p)
            c.drawCircle(x - 7f, y - 4f, 6f, p); c.drawCircle(x + 7f, y - 4f, 6f, p)
            p.setColor(rgb(60, 155, 81)); c.drawCircle(x - 3f, y - 9f, 4.5f, p)
            p.setColor(rgb(226, 61, 79))
            c.drawCircle(x - 5f, y - 6f, 2.3f, p); c.drawCircle(x + 4f, y - 8f, 2.3f, p)
            c.drawCircle(x + 1f, y - 3f, 2.3f, p)
        }
        }
        if (hitsLeft != null) {                         // hasar çentiği (KO beyaz şerit)
            val mx = K.R_HITS.getValue(kind).toFloat()
            p.setColor(argb(150, 255, 255, 255))
            c.drawRect(x - 8f, y - 36f, x - 8f + 16f * (hitsLeft / mx), y - 34f, p)
        }
    }
    /* KALP TAŞI — ilk HTML birebir: iki katlı gri kaide + süzülen altın elmas */
    private fun drawHeart(c: Canvas, s: GameState) {
        val h = s.heart
        if (h.alive && spr(c, arrayOf("heart_pulse"), s.t, 6f, h.x, h.y + 4f, 52f, false, 255)) return
        shadowE(c, h.x, h.y + 2f, 16f, 6f)
        p.setColor(if (h.alive) rgb(126, 134, 148) else rgb(74, 81, 88))
        c.drawRect(h.x - 14f, h.y - 8f, h.x + 14f, h.y + 4f, p)
        p.setColor(if (h.alive) rgb(168, 176, 189) else rgb(90, 97, 109))
        c.drawRect(h.x - 10f, h.y - 16f, h.x + 10f, h.y - 7f, p)
        val fl = if (h.alive) sinF(s.t * 2.4f) * 3f else 0f
        c.save(); c.translate(h.x, h.y - 26f + fl); c.rotate(45f)
        p.setColor(if (h.alive) rgb(232, 183, 61) else rgb(107, 90, 38))
        c.drawRect(-6f, -6f, 6f, 6f, p)
        if (h.alive) { p.setColor(rgb(255, 241, 168)); c.drawRect(-2f, -2f, 2f, 2f, p) }
        c.restore()
    }

    private fun drawBuild(c: Canvas, s: GameState, b: Build) {
        val x = b.x; val y = b.y
        shadowE(c, x, y + 2f, 15f, 5f)
        val bk = if (b.t == K.WALL) "build_wall"
            else if (b.t == K.WALL_STONE) "build_stonewall"
            else if (b.t == K.WALL_KEEP) "build_keep"
            else if (b.t == K.DOOR) (if (b.open) "build_gate_open" else "build_gate")
            else "build_ballista"
        val sprB = spr(c, arrayOf(bk), s.t, 4f, x, y + 2f,
            if (b.t == K.WALL_KEEP || b.t == K.BALLISTA) 42f else 36f, false, 255)
        if (!sprB) {
        if (b.t == K.WALL) {
            var i = 0
            while (i < 4) {                              // sivri kütükler (iki ton)
                val px = x - 14f + i * 8f
                p.setColor(rgb(86, 58, 34)); c.drawRect(px, y - 26f, px + 6f, y + 2f, p)
                p.setColor(rgb(118, 82, 48)); c.drawRect(px, y - 26f, px + 2.5f, y + 2f, p)
                p.setColor(rgb(140, 100, 58)); c.drawRect(px + 1f, y - 30f, px + 5f, y - 25f, p)
                i++
            }
            p.setColor(rgb(120, 126, 136))               // demir kuşak + perçinler
            c.drawRect(x - 15f, y - 13f, x + 15f, y - 9f, p)
            p.setColor(rgb(214, 170, 76))
            i = 0
            while (i < 4) { c.drawRect(x - 12f + i * 8f, y - 12f, x - 10.6f + i * 8f, y - 10.6f, p); i++ }
        } else if (b.t == K.WALL_STONE) {                // SAYFA: WALL_SEGMENT
            bricks(c, x - 15f, y - 26f, x + 15f, y + 2f)
            merlons(c, x, y - 26f, 30f)
            p.setColor(rgb(46, 50, 58)); c.drawRect(x - 15f, y - 1f, x + 15f, y + 2f, p)
            banner(c, x, y - 22f, 7f, 13f)
        } else if (b.t == K.WALL_KEEP) {                 // SAYFA: WATCH_TOWER
            bricks(c, x - 14f, y - 38f, x + 14f, y + 2f)
            merlons(c, x, y - 38f, 28f)
            p.setColor(rgb(34, 38, 46))                  // ok deliği
            c.drawRect(x - 1.5f, y - 30f, x + 1.5f, y - 20f, p)
            p.setColor(rgb(46, 50, 58)); c.drawRect(x - 14f, y - 1f, x + 14f, y + 2f, p)
            banner(c, x, y - 16f, 9f, 15f)
        } else if (b.t == K.DOOR) {                      // SAYFA: GATE
            bricks(c, x - 15f, y - 26f, x - 10f, y + 2f)
            bricks(c, x + 10f, y - 26f, x + 15f, y + 2f)
            bricks(c, x - 15f, y - 32f, x + 15f, y - 24f)
            merlons(c, x, y - 32f, 30f)
            if (b.open) {
                p.setColor(rgb(22, 26, 34)); c.drawRect(x - 10f, y - 24f, x + 10f, y + 2f, p)
                p.setColor(rgb(70, 48, 30)); c.drawRect(x - 10f, y - 22f, x - 6f, y + 2f, p)
            } else {
                p.setColor(rgb(70, 48, 30))              // kemerli kanat
                c.drawRect(x - 10f, y - 22f, x + 10f, y + 2f, p)
                c.drawRect(x - 7f, y - 25f, x + 7f, y - 21f, p)
                p.setColor(rgb(120, 126, 136))           // demir bantlar
                c.drawRect(x - 10f, y - 16f, x + 10f, y - 14f, p)
                c.drawRect(x - 10f, y - 6f, x + 10f, y - 4f, p)
                p.setColor(rgb(214, 170, 76)); c.drawRect(x + 4f, y - 12f, x + 7f, y - 9f, p)
            }
        } else {                                        // BALİSTA
            bricks(c, x - 14f, y - 16f, x + 14f, y + 2f) // SAYFA: BALLISTA_TOWER tabanı
            p.setColor(rgb(114, 78, 46)); c.drawCircle(x, y - 10f, 8f, p)
            val dx = cosF(b.scan); val dy = sinF(b.scan)
            p.setColor(rgb(110, 74, 42)); p.setStrokeWidth(4f)
            c.drawLine(x, y - 10f, x + dx * 15f, y - 10f + dy * 15f, p)
            p.setColor(if (b.ammo > 0) rgb(138, 90, 50) else rgb(126, 134, 148))
            p.setStrokeWidth(3f)                          // boş stok = gri yay (plan 38)
            c.drawLine(x - dy * 9f + dx * 8f, y - 10f + dx * 9f + dy * 8f,
                       x + dy * 9f + dx * 8f, y - 10f - dx * 9f + dy * 8f, p)
            p.setColor(rgb(230, 210, 138))                // taban çentikleri (≤5)
            var i = 0
            while (i < Math.min(b.ammo, 5)) { c.drawRect(x - 12f + i * 5f, y + 3f, x - 9f + i * 5f, y + 7f, p); i++ }
            val tg = b.boltTgt                          // uçan cıvata
            if (b.boltT >= 0f && tg != null) {
                val pr = Math.min(1f, b.boltT / 0.22f)
                val bx = x + (tg.x - x) * pr; val by = (y - 10f) + (tg.y - 8f - (y - 10f)) * pr
                p.setColor(rgb(205, 212, 222)); p.setStrokeWidth(2f)
                c.drawLine(bx, by, bx - dx * 8f, by - dy * 8f, p)
            }
        }
        }
        if (sprB && b.t == K.BALLISTA) {                 // sprite olsa da oynanış geri bildirimi
            p.setColor(rgb(230, 210, 138))
            var i = 0
            while (i < Math.min(b.ammo, 5)) { c.drawRect(x - 12f + i * 5f, y + 3f, x - 9f + i * 5f, y + 7f, p); i++ }
            val tg = b.boltTgt
            if (b.boltT >= 0f && tg != null) {
                val pr = Math.min(1f, b.boltT / 0.22f)
                val dx = cosF(b.scan); val dy = sinF(b.scan)
                val bx = x + (tg.x - x) * pr; val by = (y - 10f) + (tg.y - 8f - (y - 10f)) * pr
                p.setColor(rgb(205, 212, 222)); p.setStrokeWidth(2f)
                c.drawLine(bx, by, bx - dx * 8f, by - dy * 8f, p)
            }
        }
        val mx = K.B_HP.getValue(b.t)                   // çatlaklar: %60 / %30
        p.setStrokeWidth(1.5f); p.setColor(argb(130, 0, 0, 0))
        if (b.hp < mx * 0.6f) c.drawLine(x - 8f, y - 16f, x + 6f, y - 4f, p)
        if (b.hp < mx * 0.3f) c.drawLine(x + 8f, y - 20f, x - 4f, y - 8f, p)
    }
    /* ════ İNSANSI — İLK HTML'İN BİREBİR PORTU (fillRect piksel dili) ════
       Kaynak: kayip-orman drawHuman/drawPlayer. Sopa, baltanın savurma mekaniğini taşır;
       YÜZME bu dilde yeni tasarlandı (HTML'de su engeldi). */
    private fun drawHumanRect(c: Canvas, s: GameState, x: Float, y: Float,
                              fx: Float, fy: Float, moving: Boolean,
                              shirt: Int, hair: Int, npcIdle: Boolean, alpha: Int) {
        shadowE(c, x, y + 1f, 10f, 4f)
        val lw = if (moving) sinF(wt) * 3f else 0f
        val bobY = if (moving) -Math.abs(sinF(wt)) * 1.6f
                   else if (npcIdle) sinF(s.t * 2f + x) * 0.8f else 0f
        c.save(); c.translate(x, y + bobY)
        p.setColor(withA(rgb(39, 64, 94), alpha))                    // bacaklar (yürüyüş kayması)
        c.drawRect(-6f + clampF(lw, -3f, 3f), -9f, -1f + clampF(lw, -3f, 3f), 0f, p)
        c.drawRect(1f - clampF(lw, -3f, 3f), -9f, 6f - clampF(lw, -3f, 3f), 0f, p)
        p.setColor(withA(shirt, alpha)); c.drawRect(-7f, -21f, 7f, -8f, p)
        p.setColor(argb((64 * alpha) / 255, 0, 0, 0)); c.drawRect(-7f, -10f, 7f, -8f, p)
        p.setColor(withA(rgb(242, 192, 140), alpha)); c.drawRect(-6f, -32f, 6f, -21f, p)
        p.setColor(withA(hair, alpha))
        if (fy < -0.6f) c.drawRect(-6f, -32f, 6f, -23f, p)           // arkadan bakış: saç dolu
        else {
            c.drawRect(-6f, -32f, 6f, -28f, p)                       // saç kapağı + favoriler
            c.drawRect(-6f, -28f, -4f, -24f, p); c.drawRect(4f, -28f, 6f, -24f, p)
            val ex = clampF(fx * 2.5f, -2.5f, 2.5f)
            p.setColor(withA(rgb(29, 37, 48), alpha))                // gözler bakış yönüne kayar
            c.drawRect(-3f + ex, -26f, -1f + ex, -23.5f, p)
            c.drawRect(2f + ex, -26f, 4f + ex, -23.5f, p)
        }
        c.restore()
    }

    private fun drawPlayer(c: Canvas, s: GameState) {
        val pl = s.player
        if (!pl.alive) return
        if (pl.swim) { drawSwimmer(c, s, pl); return }
        val mv = Math.abs(joyVx) + Math.abs(joyVy) > 0.05f
        val flipP = pl.fx < -0.3f
        val pk = if (pl.attackT > 0f) "player_attack" else if (mv) "player_walk" else "player_idle"
        if (spr(c, arrayOf(pk, "player_idle"), s.t + (if (pl.attackT > 0f) (0.18f - pl.attackT) * 30f else 0f),
                if (pl.attackT > 0f) 1f else if (mv) 10f else 6f, pl.x, pl.y, 44f, flipP, 255)) {
            if (pl.buildMode) { p.setColor(argb(230, 232, 183, 61)); c.drawCircle(pl.x, pl.y - 44f, 3f, p) }
            return
        }
        drawHumanRect(c, s, pl.x, pl.y, pl.fx, pl.fy, mv,
            rgb(62, 127, 208), rgb(80, 50, 23), false, 255)          // HTML mavisi + kahve saç
        if ((pl.inv["club"] ?: 0) > 0) {                             // SOPA: balta savurma mekaniği
            val rot = if (pl.attackT > 0f) (-86f + (1f - pl.attackT / 0.18f) * 143f) else -37f
            val ang = Math.atan2(pl.fy.toDouble(), pl.fx.toDouble()).toFloat() * 57.296f
            c.save(); c.translate(pl.x + pl.fx * 7f, pl.y - 15f + pl.fy * 3f)
            c.rotate(ang + rot)
            p.setColor(rgb(122, 78, 42)); c.drawRect(0f, -1.5f, 13f, 1.5f, p)
            p.setColor(rgb(92, 60, 32)); c.drawCircle(13.5f, 0f, 3.4f, p)
            c.restore()
        }
        if (pl.buildMode) { p.setColor(argb(230, 232, 183, 61)); c.drawCircle(pl.x, pl.y - 38f, 3f, p) }
    }

    /* YÜZME — yan profil KURBAĞALAMA: vücut hareket yönüne yatar,
       kol süpürür, bacaklar makas-tekme atar, burunda dalga köpürür. */
    private fun drawSwimmer(c: Canvas, s: GameState, pl: Player) {
        if (spr(c, arrayOf("player_swim"), s.t, 8f, pl.x, pl.y, 46f,
                pl.fx < 0f && Math.abs(pl.fx) >= Math.abs(pl.fy), 255)) return
        val ang = Math.atan2(pl.fy.toDouble(), pl.fx.toDouble()).toFloat() * 57.296f
        val ph = sinF(s.t * 4.5f)                        // kurbağalama döngüsü
        c.save(); c.translate(pl.x, pl.y); c.rotate(ang)
        val kick = ph * 0.5f + 0.5f                      // 0 kapalı → 1 açık makas
        p.setColor(argb(120, 16, 48, 84))                // su altı bacaklar
        c.drawRect(-17f, -1.2f - kick * 4.5f, -6f, 1.4f - kick * 4.5f, p)
        c.drawRect(-17f, -1.2f + kick * 4.5f, -6f, 1.4f + kick * 4.5f, p)
        p.setColor(rgb(62, 127, 208))                    // gövde: yatay yan profil
        c.drawRect(-8f, -7f, 8f, 0f, p)
        p.setColor(argb(64, 0, 0, 0)); c.drawRect(-8f, -2f, 8f, 0f, p)
        val arm = -12f + kick * 96f                      // kol süpürmesi: ileri→yana→topla
        c.save(); c.translate(4f, -5f); c.rotate(arm)
        p.setColor(rgb(242, 192, 140)); c.drawRect(0f, -1.6f, 11f, 1.6f, p); c.restore()
        p.setColor(rgb(242, 192, 140)); c.drawRect(8f, -10f, 16f, -1f, p)   // kafa önde
        p.setColor(rgb(80, 50, 23)); c.drawRect(8f, -10f, 16f, -6.5f, p)
        p.setColor(rgb(29, 37, 48)); c.drawRect(13.2f, -6f, 15.2f, -4f, p)
        p.setColor(argb(200, 230, 246, 255))             // su çizgisi köpüğü
        c.drawRect(-13f, -1.2f, 17f, 1.2f, p)
        p.setColor(argb(170, 235, 248, 255))             // burun dalgası
        c.drawRect(16f, -3.2f - Math.abs(ph) * 2f, 20f, -1f, p)
        c.restore()
    }

    private fun drawVillager(c: Canvas, s: GameState) {
        val v = s.villager ?: return
        if (v.state == K.VS_CAGED) {
            if (!spr(c, arrayOf("ayla_caged"), s.t, 4f, v.x, v.y + 4f, 44f, false, 255))
                drawHumanRect(c, s, v.x, v.y, 0f, 1f, false, rgb(215, 111, 163), rgb(58, 38, 20), true, 235)
            p.setColor(rgb(108, 82, 50)); var k = -2                 // kafes çubukları
            while (k <= 2) { c.drawRect(v.x + k * 7f - 1.4f, v.y - 38f, v.x + k * 7f + 1.4f, v.y + 4f, p); k++ }
            c.drawRect(v.x - 16f, v.y - 40f, v.x + 16f, v.y - 36f, p)
            c.drawRect(v.x - 16f, v.y, v.x + 16f, v.y + 4f, p)
            drawTag(c, v.x, v.y - 50f, "E: Kurtar")
            return
        }
        val vk = if (v.carry > 0) "ayla_carry" else if (v.hasTgt) "ayla_walk" else "ayla_idle"
        if (!spr(c, arrayOf(vk, "ayla_idle"), s.t, if (v.hasTgt) 10f else 5f,
                v.x, v.y, 42f, v.tgtX < v.x, 255))
            drawHumanRect(c, s, v.x, v.y, if (v.tgtX < v.x) -1f else 1f, 0.4f, v.hasTgt,
                rgb(215, 111, 163), rgb(58, 38, 20), true, 255)          // Ayla: HTML pembesi
        if (v.carry > 0) {
            c.save(); c.translate(v.x, v.y - 30f); c.rotate(-18f)
            p.setColor(rgb(110, 74, 42)); c.drawRect(-11f, -3f, 11f, 3f, p)
            p.setColor(rgb(190, 152, 104)); c.drawCircle(11f, 0f, 2.8f, p)
            c.restore()
        }
        drawTag(c, v.x, v.y - 46f, "Ayla")
    }

    /* GÖLGE — HTML drawEnemy birebir: wob'lu mürekkep elipsi + mor taç + göz şeritleri */
    private fun drawShadow(c: Canvas, s: GameState, e: Shadow) {
        val a = (255f * Math.min(1f, e.scale)).toInt()
        if (spr(c, arrayOf("shadow_float"), s.t + e.ph, 8f, e.x, e.y, 40f * e.scale, false, a)) return
        val wob = sinF(s.t * 6f + e.ph) * 0.14f + 1f
        p.setColor(argb((64 * a) / 255, 90, 50, 160))
        c.save(); c.translate(e.x, e.y + 1f); c.scale(1f, 5f / 12f)
        c.drawCircle(0f, 0f, 12f, p); c.restore()
        c.save(); c.translate(e.x, e.y - 9f); c.scale(1f, (13f / wob) / (11f * wob))
        p.setColor(withA(rgb(34, 20, 64), a)); c.drawCircle(0f, 0f, 11f * wob, p)
        c.restore()
        var sg = 0; var qx0 = 0f; var qy0 = 0f                       // taç yayı: 6 doğru parçası
        while (sg <= 6) {
            val th = (198f + sg * 24f) * 0.017453f
            val qx = e.x + Math.cos(th.toDouble()).toFloat() * 9f * wob
            val qy = e.y - 11f + Math.sin(th.toDouble()).toFloat() * 9f * wob
            if (sg > 0) c.drawLine(qx0, qy0, qx, qy, p2(2f, withA(rgb(58, 39, 102), a)))
            qx0 = qx; qy0 = qy; sg++
        }
        p.setColor(withA(rgb(242, 240, 255), a))                     // dik göz şeritleri
        c.drawRect(e.x - 6f, e.y - 13f, e.x - 2f, e.y - 10.6f, p)
        c.drawRect(e.x + 2f, e.y - 13f, e.x + 6f, e.y - 10.6f, p)
        if (e.stolen.isNotEmpty()) {                                 // sırtlanan ganimet
            c.save(); c.translate(e.x, e.y - 28f + sinF(s.t * 5f) * 2f); c.rotate(45f)
            p.setColor(withA(rgb(232, 183, 61), a)); c.drawRect(-4f, -4f, 4f, 4f, p)
            c.restore()
        }
    }

    /* FAUNA — HTML drawCritter birebir; domuz aynı dilde eklendi */
    private fun drawCritter(c: Canvas, s: GameState, cr: Critter) {
        val sk = if (cr.kind == K.CK_RABBIT) (if (cr.moving) "rabbit_hop" else "rabbit_idle")
            else if (cr.kind == K.CK_DEER) (if (cr.moving) "deer_run" else "deer_idle")
            else if (cr.kind == K.CK_BOAR) (if (cr.chargeT > 0f) "boar_charge" else "boar_walk")
            else (if (cr.aggro) "wolf_run" else "wolf_walk")
        if (spr(c, arrayOf(sk, sk.substring(0, sk.indexOf('_')) + "_idle"),
                s.t + cr.ph, if (cr.moving) 10f else 6f, cr.x, cr.y, 38f, cr.wx < 0f, 255)) return
        val x = cr.x; val y = cr.y
        c.save()
        if (cr.wx < 0f) { c.translate(x, y); c.scale(-1f, 1f); c.translate(-x, -y) }
        if (cr.kind == K.CK_RABBIT) {
            val hop = if (cr.moving) Math.abs(sinF((s.t + cr.ph) * 10f)) * 4f else 0f
            shadowE(c, x, y + 1f, 7f, 3f)
            p.setColor(rgb(168, 176, 189))
            c.drawCircle(x, y - 5f - hop, 5f, p); c.drawCircle(x + 4f, y - 8f - hop, 3f, p)
            c.drawRect(x + 2f, y - 14f - hop, x + 4f, y - 9f - hop, p)
            c.drawRect(x + 5f, y - 14f - hop, x + 7f, y - 9f - hop, p)
            p.setColor(rgb(255, 241, 168)); c.drawCircle(x - 5f, y - 5f - hop, 2f, p)
        } else if (cr.kind == K.CK_DEER) {
            shadowE(c, x, y + 1f, 10f, 4f)
            p.setColor(rgb(138, 90, 50))
            c.drawRect(x - 8f, y - 12f, x + 8f, y - 3f, p)
            c.drawRect(x + 6f, y - 17f, x + 11f, y - 11f, p)
            c.drawLine(x + 7f, y - 17f, x + 5f, y - 22f, p2(1.5f, rgb(230, 210, 138)))
            c.drawLine(x + 10f, y - 17f, x + 12f, y - 22f, p2(1.5f, rgb(230, 210, 138)))
            p.setColor(rgb(110, 74, 42))
            val dl = floatArrayOf(-6f, -2f, 3f, 7f); var k = 0
            while (k < 4) { c.drawRect(x + dl[k], y - 4f, x + dl[k] + 2f, y + 1f, p); k++ }
        } else if (cr.kind == K.CK_BOAR) {
            shadowE(c, x, y + 1f, 10f, 4f)
            p.setColor(rgb(92, 66, 48)); c.drawRect(x - 9f, y - 13f, x + 9f, y - 4f, p)
            p.setColor(rgb(70, 50, 36))                              // sırt kılları
            c.drawRect(x - 5f, y - 15f, x - 3f, y - 12f, p)
            c.drawRect(x - 1f, y - 15f, x + 1f, y - 12f, p)
            c.drawRect(x + 3f, y - 15f, x + 5f, y - 12f, p)
            p.setColor(rgb(120, 88, 64)); c.drawRect(x + 7f, y - 11f, x + 12f, y - 6f, p)
            p.setColor(rgb(238, 232, 220))                           // dişler
            c.drawRect(x + 8f, y - 5f, x + 9.6f, y - 2f, p)
            c.drawRect(x + 10.4f, y - 5f, x + 12f, y - 2f, p)
            p.setColor(rgb(58, 42, 32))
            val bl = floatArrayOf(-6f, -2f, 3f, 7f); var k = 0
            while (k < 4) { c.drawRect(x + bl[k], y - 4f, x + bl[k] + 2f, y + 1f, p); k++ }
            p.setColor(if (cr.chargeT > 0f) rgb(255, 90, 90) else rgb(30, 26, 26))
            c.drawRect(x + 8f, y - 10f, x + 10f, y - 8f, p)
            if (cr.chargeT > 0f) burst(x - 12f, y, rgb(150, 130, 104), 1, 30f, 2f, 0.3f)
        } else {                                                     // KURT
            shadowE(c, x, y + 1f, 10f, 4f)
            p.setColor(rgb(126, 134, 148))
            c.drawRect(x - 8f, y - 11f, x + 8f, y - 3f, p)
            c.drawRect(x + 6f, y - 15f, x + 12f, y - 10f, p)
            c.drawRect(x + 7f, y - 18f, x + 9f, y - 15f, p)
            c.drawRect(x + 10f, y - 18f, x + 12f, y - 15f, p)
            c.drawRect(x - 12f, y - 13f, x - 8f, y - 10f, p)         // kuyruk
            p.setColor(rgb(74, 81, 88))
            val wl = floatArrayOf(-6f, -2f, 3f, 7f); var k = 0
            while (k < 4) { c.drawRect(x + wl[k], y - 4f, x + wl[k] + 2f, y + 1f, p); k++ }
            if (cr.aggro) { p.setColor(rgb(255, 90, 90)); c.drawRect(x + 9f, y - 14f, x + 11f, y - 12f, p) }
        }
        c.restore()
    }

    private fun drawBag(c: Canvas, s: GameState, x: Float, y: Float) {
        val bob = sinF(s.t * 4f + x * 0.1f) * 2f
        shadowE(c, x, y + 1f, 7f, 3f)
        p.setColor(rgb(138, 90, 50))
        c.drawRoundRect(RectF(x - 7f, y - 12f + bob, x + 7f, y - 1f + bob), 4f, 4f, p)
        p.setColor(rgb(90, 60, 30)); c.drawRect(x - 7f, y - 8f + bob, x + 7f, y - 6f + bob, p)
        p.setColor(rgb(255, 215, 106))
        c.drawCircle(x + 5f, y - 14f + bob + sinF(s.t * 7f) * 1.5f, 1.6f, p)
        drawTag(c, x, y - 22f + bob, "E")
    }
    private fun drawTag(c: Canvas, x: Float, y: Float, txt: String) {
        tp.setTextSize(9f); tp.setTextAlign(Paint.Align.CENTER)
        val w = tp.measureText(txt) + 10f
        p.setColor(argb(225, 255, 255, 255))
        c.drawRoundRect(RectF(x - w / 2, y - 8f, x + w / 2, y + 5f), 6f, 6f, p)
        tp.setColor(rgb(29, 37, 48))
        c.drawText(txt, x, y + 1.5f, tp)
    }

    /* ── gece karartması: yarı çözünürlük + DST_OUT delikleri ── */
    private fun drawNight(c: Canvas, s: GameState) {
        val d = game!!.darkness()
        if (d < 0.03f) return
        val nb = nightBmp ?: return
        val nc = nightCv ?: return
        nc.drawColor(Color.WHITE, PorterDuff.Mode.DST_OUT)   // tam temizlik hilesi
        nc.drawColor(argb((Math.min(0.92f, d) * 255).toInt(), 7, 10, 30))
        punch(nc, (s.player.x - camX) * Z / 2f, (s.player.y - 10f - camY) * Z / 2f,
            (118f + sinF(s.t * 9f) * 7f) * Z / 2f)
        if (s.heart.alive)
            punch(nc, (s.heart.x - camX) * Z / 2f, (s.heart.y - 16f - camY) * Z / 2f, 86f * Z / 2f)
        c.save(); c.scale(2f, 2f)
        c.drawBitmap(nb, 0f, 0f, null)
        c.restore()
        if (s.heart.alive) {                            // kalbin altın nefesi (ADD)
            val gx = (s.heart.x - camX) * Z; val gy = (s.heart.y - 18f - camY) * Z
            glowP.setShader(RadialGradient(gx, gy, 110f * Z,
                argb((70 * d).toInt(), 255, 200, 70), argb(0, 255, 190, 60),
                Shader.TileMode.CLAMP))
            c.drawCircle(gx, gy, 110f * Z, glowP)
            glowP.setShader(null)
        }
    }
    private fun punch(nc: Canvas, x: Float, y: Float, r: Float) {
        holeP.setShader(RadialGradient(x, y, Math.max(4f, r),
            argb(235, 255, 255, 255), argb(0, 255, 255, 255), Shader.TileMode.CLAMP))
        nc.drawCircle(x, y, r, holeP)
        holeP.setShader(null)
    }

    /* ── SU: parıltı dikdörtgenleri (canlı su hissi, dünya uzayında) ── */
    private fun drawWater(c: Canvas, s: GameState) {
        val x0 = floorI(camX / K.TS) - 1; val y0 = floorI(camY / K.TS) - 1
        val x1 = x0 + (vw / (K.TS * Z)).toInt() + 3; val y1 = y0 + (vh / (K.TS * Z)).toInt() + 3
        p.setStyle(Paint.Style.FILL)
        var ty = y0
        while (ty <= y1) {
            var tx = x0
            while (tx <= x1) {
                if (s.gen.tileAt(tx, ty) <= K.T_WATER && s.gen.hash01(tx, ty, 7) > 0.62f) {
                    val ph2 = s.gen.hash01(tx, ty, 8) * 6.28f
                    val gx = tx * K.TS + 4f + (sinF(s.t * 0.9f + ph2) * 0.5f + 0.5f) * 22f
                    val gy = ty * K.TS + 6f + s.gen.hash01(tx, ty, 9) * 20f
                    p.setColor(argb((60 + sinF(s.t * 2f + ph2) * 35f).toInt(), 235, 248, 255))
                    c.drawRect(gx, gy, gx + 6f, gy + 1.6f, p)
                }
                tx++
            }
            ty++
        }
    }
    /* ── PARÇACIK havuzu: önceden tahsis; sıcak döngüde new yok ── */
    private fun burst(x: Float, y: Float, col: Int, n: Int, spd: Float, sz: Float, life: Float) {
        var made = 0; var k = 0
        while (k < pcl.size && made < n) {
            val q = pcl[k]
            if (!q.on) {
                q.on = true; q.x = x; q.y = y
                val a = (Math.random() * 6.283).toFloat()
                val sp = spd * (0.4f + Math.random().toFloat() * 0.8f)
                q.vx = Math.cos(a.toDouble()).toFloat() * sp
                q.vy = Math.sin(a.toDouble()).toFloat() * sp - spd * 0.4f
                q.ml = life; q.l = life; q.col = col; q.sz = sz
                made++
            }
            k++
        }
    }
    private fun spawnFx(name: String, x: Float, y: Float) {
        if (name == "chop") burst(x + 16f, y + 16f, rgb(167, 116, 60), 6, 70f, 2.4f, 0.5f)
        else if (name == "mine") burst(x + 16f, y + 16f, rgb(176, 182, 195), 6, 80f, 2.2f, 0.5f)
        else if (name == "hitE") burst(x, y, rgb(196, 130, 255), 5, 90f, 2.4f, 0.4f)
        else if (name == "place") burst(x, y, rgb(214, 188, 130), 8, 60f, 2.6f, 0.5f)
        else if (name == "enemyDie") burst(x, y, rgb(150, 80, 230), 14, 120f, 3f, 0.7f)
        else if (name == "critterDie") burst(x, y, rgb(220, 90, 90), 8, 90f, 2.4f, 0.55f)
        else if (name == "heartHit") burst(x, y, rgb(255, 215, 106), 8, 110f, 2.6f, 0.6f)
    }
    private fun updateFx(dt: Float, s: GameState) {
        var k = 0
        while (k < pcl.size) {
            val q = pcl[k]
            if (q.on) {
                q.l -= dt
                if (q.l <= 0f) q.on = false
                else { q.x += q.vx * dt; q.y += q.vy * dt; q.vy += 160f * dt }
            }
            k++
        }
        if (s.player.swim && s.player.alive) {           // kulaç halkaları
            ripT -= dt
            if (ripT <= 0f) { ripT = 0.4f; ripples.add(floatArrayOf(s.player.x, s.player.y + 6f, s.t)) }
        }
        var r = ripples.size - 1
        while (r >= 0) { if (s.t - ripples[r][2] > 1.1f) ripples.removeAt(r); r-- }
    }
    private fun drawFx(c: Canvas, s: GameState) {
        var r = 0
        while (r < ripples.size) {                       // büyüyen su halkaları
            val rp = ripples[r]
            val age = s.t - rp[2]
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f)
            p.setColor(argb(Math.max(0, (120 * (1f - age / 1.1f)).toInt()), 220, 240, 255))
            c.save(); c.translate(rp[0], rp[1]); c.scale(1f, 0.5f)
            c.drawCircle(0f, 0f, 8f + age * 30f, p); c.restore()
            r++
        }
        p.setStyle(Paint.Style.FILL)
        var k = 0
        while (k < pcl.size) {
            val q = pcl[k]
            if (q.on) {
                p.setColor(withA(q.col, (220 * (q.l / q.ml)).toInt()))
                c.drawCircle(q.x, q.y, q.sz * (0.5f + q.l / q.ml * 0.5f), p)
            }
            k++
        }
    }
    /* MİNİMAP: 56×56 karo, 2px hücre — 0.6 sn'de bir tazelenir */
    private fun updateMinimap(s: GameState) {
        var bm = mmBmp
        if (bm == null) {
            bm = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
            mmBmp = bm; mmCv = Canvas(bm)
        }
        val cv = mmCv ?: return
        val ptx = s.wtX(s.player.x); val pty = s.wtY(s.player.y)
        var ty = 0
        while (ty < 56) {
            var tx = 0
            while (tx < 56) {
                val wx2 = ptx - 28 + tx; val wy2 = pty - 28 + ty
                val t = s.gen.tileAt(wx2, wy2)
                mmP.setColor(
                    if (t == K.T_DEEP) rgb(29, 86, 143)
                    else if (t == K.T_WATER) rgb(46, 124, 196)
                    else if (t == K.T_SAND) rgb(230, 210, 138)
                    else if (t == K.T_CAMP) rgb(200, 160, 109)
                    else if (s.gen.resourceAt(wx2, wy2) != null) rgb(38, 110, 58)
                    else rgb(79, 174, 96))
                cv.drawRect(tx * 2f, ty * 2f, tx * 2f + 2f, ty * 2f + 2f, mmP)
                tx++
            }
            ty++
        }
        for (b in s.builds.values) {                      // yapılar: açık gri
            val dx = s.wtX(b.x) - ptx + 28; val dy = s.wtY(b.y) - pty + 28
            if (dx in 0..55 && dy in 0..55) {
                mmP.setColor(rgb(220, 224, 232)); cv.drawRect(dx * 2f, dy * 2f, dx * 2f + 2f, dy * 2f + 2f, mmP)
            }
        }
        val hx = s.heart.x / K.TS - ptx + 28; val hy2 = s.heart.y / K.TS - pty + 28
        if (hx > 0f && hx < 55f) { mmP.setColor(rgb(232, 183, 61)); cv.drawRect(hx * 2f - 1f, hy2 * 2f - 1f, hx * 2f + 3f, hy2 * 2f + 3f, mmP) }
        for (e in s.shadows) if (e.alive) {               // gece tehdidi: kırmızı
            val ex = e.x / K.TS - ptx + 28; val ey = e.y / K.TS - pty + 28
            if (ex > 0f && ex < 56f && ey > 0f && ey < 56f) {
                mmP.setColor(rgb(255, 90, 90)); cv.drawRect(ex * 2f, ey * 2f, ex * 2f + 2f, ey * 2f + 2f, mmP)
            }
        }
        mmP.setColor(rgb(255, 255, 255))                  // oyuncu: merkez
        cv.drawRect(54f, 54f, 58f, 58f, mmP)
    }
    /* ── Kara-fantezi yapı dili: tuğla sırası + mazgal + mavi-altın sancak ── */
    private fun bricks(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float) {
        p.setColor(rgb(56, 60, 68)); c.drawRect(x0, y0, x1, y1, p)   // harç zemini
        var row = 0; var yy = y0 + 1f
        while (yy < y1 - 1f) {
            var xx = x0 + 1f + (if (row % 2 == 1) 4.5f else 0f) - 9f
            while (xx < x1 - 1f) {
                val l = Math.max(xx, x0 + 1f); val r = Math.min(xx + 8f, x1 - 1f)
                if (r > l + 1f) {
                    p.setColor(if (row == 0) rgb(134, 140, 152) else rgb(98, 102, 112))
                    c.drawRect(l, yy, r, Math.min(yy + 6f, y1 - 1f), p)
                }
                xx += 9f
            }
            yy += 7f; row++
        }
    }
    private fun merlons(c: Canvas, x: Float, topY: Float, span: Float) {
        p.setColor(rgb(142, 148, 160))
        var i = 0
        while (i < 3) {
            val mx2 = x - span / 2f + i * (span - 6f) / 2f
            c.drawRect(mx2, topY - 6f, mx2 + 6f, topY, p); i++
        }
    }
    private fun banner(c: Canvas, x: Float, top: Float, w: Float, h: Float) {
        p.setColor(rgb(30, 58, 118)); c.drawRect(x - w / 2f, top, x + w / 2f, top + h, p)
        p.setColor(rgb(46, 84, 158)); c.drawRect(x - w / 2f + 1f, top, x + w / 2f - 1f, top + h - 3f, p)
        p.setColor(rgb(214, 170, 76))                    // altın arma
        c.save(); c.translate(x, top + h * 0.42f); c.rotate(45f)
        c.drawRect(-2.4f, -2.4f, 2.4f, 2.4f, p); c.restore()
    }
    /* Çip ikonları — HTML drawIconCv ruhunda mini piksel ikonlar */
    private fun itemIcon(c: Canvas, name: String, cx: Float, cy: Float) {
        if (name == "wood") {
            p.setColor(rgb(122, 78, 42)); c.save(); c.translate(cx, cy); c.rotate(-18f)
            c.drawRect(-8f, -3f, 8f, 3f, p)
            p.setColor(rgb(190, 152, 104)); c.drawCircle(8f, 0f, 2.6f, p); c.restore()
        } else if (name == "stone") {
            p.setColor(rgb(126, 134, 148)); c.drawRect(cx - 7f, cy - 3f, cx + 7f, cy + 5f, p)
            p.setColor(rgb(168, 176, 189)); c.drawRect(cx - 4f, cy - 6f, cx + 4f, cy - 1f, p)
        } else if (name == "berry") {
            p.setColor(rgb(226, 61, 79))
            c.drawCircle(cx - 3f, cy, 3.4f, p); c.drawCircle(cx + 3f, cy - 2f, 3.4f, p)
            p.setColor(rgb(60, 155, 81)); c.drawRect(cx - 1f, cy - 7f, cx + 1f, cy - 3f, p)
        } else if (name == "meat") {
            p.setColor(rgb(196, 77, 94)); c.drawCircle(cx - 2f, cy, 4.6f, p)
            p.setColor(rgb(238, 232, 220)); c.drawRect(cx + 2f, cy - 1.4f, cx + 8f, cy + 1.4f, p)
            c.drawCircle(cx + 8f, cy, 2f, p)
        } else if (name == "hide") {
            p.setColor(rgb(168, 116, 63)); c.drawRect(cx - 7f, cy - 5f, cx + 7f, cy + 5f, p)
            p.setColor(rgb(120, 80, 44)); c.drawRect(cx - 7f, cy - 5f, cx - 4f, cy + 5f, p)
        } else {                                          // sopa
            p.setColor(rgb(122, 78, 42)); c.save(); c.translate(cx, cy); c.rotate(40f)
            c.drawRect(-7f, -1.6f, 6f, 1.6f, p)
            p.setColor(rgb(92, 60, 32)); c.drawCircle(7f, 0f, 3.2f, p); c.restore()
        }
    }
    /* ── SOHBET HUD: düğme + akış; paneller modal ── */
    private fun drawChatHud(c: Canvas, s: GameState) {
        val bx = 56f; val by = uh * 0.40f
        p.setColor(argb(180, 24, 40, 64))
        c.drawRoundRect(RectF(bx - 36f, by - 18f, bx + 36f, by + 18f), 12f, 12f, p)
        tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(12f); tp.setColor(Color.WHITE)
        c.drawText("SOHBET", bx, by + 4f, tp)
        var k = s.chat.size - 1; var ly = 150f; var shown = 0
        tp.setTextAlign(Paint.Align.LEFT); tp.setTextSize(11f)
        while (k >= 0 && shown < 4) {
            val m = s.chat[k]
            val age = s.t - m.third
            if (age < 6f) {
                val a = (235 * Math.min(1f, (6f - age))).toInt()
                val txt = (if (m.second == 0) "Sen: " else "") + m.first
                val w = tp.measureText(txt) + 16f
                p.setColor(argb((a * 150) / 235, 10, 16, 26))
                c.drawRoundRect(RectF(16f, ly - 13f, 16f + w, ly + 5f), 8f, 8f, p)
                tp.setColor(withA(if (m.second == 1) rgb(150, 235, 160)
                    else if (m.second == 2) rgb(255, 215, 106) else Color.WHITE, a))
                c.drawText(txt, 24f, ly, tp)
                ly += 24f; shown++
            }
            k--
        }
    }
    private fun drawChatPanel(c: Canvas) {
        msgRects.clear()
        p.setColor(argb(120, 0, 0, 0)); c.drawRect(0f, 0f, uw, uh, p)
        val pw = Math.min(uw - 40f, 360f); val ph = 64f * K.QCHAT.size + 110f
        val l = uw / 2f - pw / 2f; val t0 = uh / 2f - ph / 2f
        p.setColor(argb(242, 16, 24, 38)); c.drawRoundRect(RectF(l, t0, l + pw, t0 + ph), 18f, 18f, p)
        tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(16f); tp.setColor(rgb(255, 215, 106))
        c.drawText("HIZLI MESAJ", uw / 2f, t0 + 32f, tp)
        var k = 0
        while (k < K.QCHAT.size) {
            val r = RectF(l + 18f, t0 + 50f + k * 64f, l + pw - 18f, t0 + 102f + k * 64f)
            p.setColor(argb(235, 38, 64, 104)); c.drawRoundRect(r, 12f, 12f, p)
            tp.setTextSize(14f); tp.setColor(Color.WHITE)
            c.drawText(K.QCHAT[k], uw / 2f, r.top + 33f, tp)
            msgRects.add(Pair(r, k)); k++
        }
        val cr = RectF(l + 18f, t0 + ph - 48f, l + pw - 18f, t0 + ph - 14f)
        p.setColor(argb(220, 90, 40, 40)); c.drawRoundRect(cr, 10f, 10f, p)
        tp.setTextSize(13f); tp.setColor(Color.WHITE); c.drawText("KAPAT", uw / 2f, cr.top + 23f, tp)
        msgRects.add(Pair(cr, -1))
        tp.setTextSize(9.5f); tp.setColor(argb(150, 255, 255, 255))
        c.drawText("Global sohbet yol haritasında (sunucu adaptörü)", uw / 2f, t0 + ph - 58f, tp)
    }
    private fun drawInvPanel(c: Canvas, s: GameState) {
        msgRects.clear()
        p.setColor(argb(120, 0, 0, 0)); c.drawRect(0f, 0f, uw, uh, p)
        val keys = arrayOf("wood", "stone", "berry", "meat", "hide", "club")
        val pw = Math.min(uw - 40f, 380f); val ph = 36f * keys.size + 196f
        val l = uw / 2f - pw / 2f; val t0 = uh / 2f - ph / 2f
        p.setColor(argb(244, 16, 24, 38)); c.drawRoundRect(RectF(l, t0, l + pw, t0 + ph), 18f, 18f, p)
        tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(16f); tp.setColor(rgb(255, 215, 106))
        c.drawText("ENVANTER", uw / 2f, t0 + 30f, tp)
        var k = 0
        while (k < keys.size) {
            val yy = t0 + 58f + k * 36f
            tp.setTextAlign(Paint.Align.LEFT); tp.setTextSize(13f); tp.setColor(Color.WHITE)
            c.drawText(K.ITEM_TR.getValue(keys[k]), l + 26f, yy, tp)
            tp.setTextAlign(Paint.Align.RIGHT); tp.setColor(rgb(255, 233, 176))
            c.drawText("" + (s.player.inv[keys[k]] ?: 0), l + pw - 26f, yy, tp)
            k++
        }
        val cy = t0 + 58f + keys.size * 36f + 6f
        val canClub = (s.player.inv["club"] ?: 0) == 0
        val r1 = RectF(l + 18f, cy, l + pw - 18f, cy + 50f)
        p.setColor(if (canClub) argb(235, 44, 111, 60) else argb(160, 50, 58, 70))
        c.drawRoundRect(r1, 12f, 12f, p)
        tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(14f); tp.setColor(Color.WHITE)
        c.drawText(if (canClub) "SOPA \u00dcRET \u2014 3 odun" else "Sopa ku\u015fan\u0131ld\u0131 \u2713", uw / 2f, cy + 31f, tp)
        msgRects.add(Pair(r1, 100))
        tp.setTextSize(10f); tp.setColor(argb(170, 255, 255, 255))
        c.drawText("\u0130pucu: \u0130n\u015fa modunda E \u2192 \u00f6ndeki yap\u0131y\u0131 S\u00d6K (%50 iade)", uw / 2f, cy + 70f, tp)
        val cr = RectF(l + 18f, t0 + ph - 48f, l + pw - 18f, t0 + ph - 14f)
        p.setColor(argb(220, 90, 40, 40)); c.drawRoundRect(cr, 10f, 10f, p)
        tp.setTextSize(13f); tp.setColor(Color.WHITE); c.drawText("KAPAT", uw / 2f, cr.top + 23f, tp)
        msgRects.add(Pair(cr, -1))
    }

    /* ════════════ HUD ════════════ */
    private fun bar(c: Canvas, x: Float, y: Float, w: Float, v: Float, col: Int, lab: String) {
        p.setColor(argb(150, 0, 0, 0))
        c.drawRoundRect(RectF(x, y, x + w, y + 16f), 8f, 8f, p)
        p.setColor(col)
        if (v > 0.01f) c.drawRoundRect(RectF(x + 2f, y + 2f, x + 2f + (w - 4f) * clampF(v, 0f, 1f), y + 14f), 6f, 6f, p)
        tp.setTextSize(10f); tp.setTextAlign(Paint.Align.LEFT); tp.setColor(argb(230, 255, 255, 255))
        c.drawText(lab, x + 6f, y + 12f, tp)
    }
    private fun drawHud(c: Canvas, s: GameState) { c.save(); c.scale(us, us); drawHudI(c, s); c.restore() }
    private fun drawHudI(c: Canvas, s: GameState) {
        val g = game!!
        bar(c, 20f, 18f, 230f, s.player.hp / 100f, rgb(226, 61, 79), "CAN")
        bar(c, 20f, 40f, 230f, s.player.en / 100f, rgb(232, 183, 61), "ENERJİ")
        bar(c, 20f, 62f, 230f, s.player.hu / 100f, rgb(224, 123, 47), "AÇLIK")
        bar(c, 20f, 84f, 230f, s.heart.hp / 300f, rgb(255, 215, 106), "KALP")
        // saat + hedef
        tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(16f); tp.setColor(Color.WHITE)
        c.drawText(g.clockText(), uw / 2f, 32f, tp)
        tp.setTextSize(11f); tp.setColor(argb(210, 255, 255, 255))
        val goal = if (s.victory) "Hikâye senin — krallık ayakta ★"
            else if (!s.heart.alive) "Kalp düştü… güneşi yine de kovala"
            else "Hedef: 10 gece hayatta kal · Gün " + Math.min(s.day, 10) + "/10"
        c.drawText(goal, uw / 2f, 52f, tp)
        // envanter çipleri (alt-orta)
        val items = arrayOf("wood", "stone", "berry", "meat", "hide", "club")
        val cw = 66f; val totW = cw * items.size
        var x = uw / 2f - totW / 2f
        val hy = uh - 56f
        var i = 0
        while (i < items.size) {
            p.setColor(argb(170, 12, 18, 28))
            c.drawRoundRect(RectF(x + 3f, hy, x + cw - 3f, hy + 40f), 9f, 9f, p)
            itemIcon(c, items[i], x + 18f, hy + 17f)
            tp.setTextAlign(Paint.Align.LEFT); tp.setTextSize(15f); tp.setColor(Color.WHITE)
            c.drawText("" + (s.player.inv[items[i]] ?: 0), x + 32f, hy + 23f, tp)
            tp.setTextSize(8f); tp.setColor(argb(160, 255, 255, 255))
            c.drawText(K.ITEM_TR.getValue(items[i]), x + 10f, hy + 36f, tp)
            x += cw; i++
        }
        hotbarRect = RectF(uw / 2f - totW / 2f - 4f, hy - 6f, uw / 2f + totW / 2f + 4f, hy + 44f)
        // inşa paneli: seçili yapı + maliyet (yetmeyen kalem kırmızı)
        if (s.player.buildMode) {
            tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(13f)
            tp.setColor(rgb(255, 215, 106))
            c.drawText("İNŞA: " + K.B_NAME.getValue(s.player.buildSel) +
                "  (E: tip değiştir · VUR: yerleştir)", uw / 2f, hy - 30f, tp)
            val cost = K.B_COST.getValue(s.player.buildSel)
            val sb = StringBuilder()
            var ok = true
            for (e in cost) {
                if ((s.player.inv[e.first] ?: 0) < e.second) ok = false
                if (sb.length > 0) sb.append("  +  ")
                sb.append(e.second).append(" ").append(K.ITEM_TR.getValue(e.first))
            }
            tp.setTextSize(11f)
            tp.setColor(if (ok) rgb(126, 242, 154) else rgb(255, 110, 110))
            c.drawText(sb.toString(), uw / 2f, hy - 14f, tp)
            buildRects.clear()                            // hızlı tip seçimi: 5 dokunmatik kutu
            val bw = 52f; var bxx = uw / 2f - bw * 2.5f
            var bi = 0
            while (bi < K.B_ORDER.size) {
                val bt2 = K.B_ORDER[bi]
                val rr = RectF(bxx + 3f, hy - 96f, bxx + bw - 3f, hy - 52f)
                p.setColor(if (bt2 == s.player.buildSel) argb(235, 232, 183, 61) else argb(190, 24, 34, 50))
                c.drawRoundRect(rr, 10f, 10f, p)
                tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(10.5f)
                tp.setColor(if (bt2 == s.player.buildSel) rgb(24, 22, 20) else Color.WHITE)
                c.drawText(K.B_NAME.getValue(bt2).take(4), bxx + bw / 2f, hy - 68f, tp)
                buildRects.add(Pair(rr, bt2))
                bi++; bxx += bw
            }
        }
        val mb = mmBmp                                   // minimap (sağ-üst, duraklatın altı)
        if (mb != null) {
            p.setColor(argb(200, 12, 18, 28))
            c.drawRoundRect(RectF(uw - 136f, 76f, uw - 14f, 198f), 10f, 10f, p)
            c.drawBitmap(mb, android.graphics.Rect(0, 0, 112, 112),
                RectF(uw - 131f, 81f, uw - 19f, 193f), spp)
        }
        drawChatHud(c, s)
        drawTouchButtons(c, s)
        if (chatOpen) drawChatPanel(c)
        if (invOpen) drawInvPanel(c, s)
        // tostlar
        var ty2 = uh - 116f
        i = toastQ.size - 1
        while (i >= 0) {
            val t = toastQ[i]
            tp.setTextSize(12f); tp.setTextAlign(Paint.Align.CENTER)
            val w = tp.measureText(t.text) + 26f
            val a = Math.min(1f, t.t / 0.4f)
            p.setColor(argb((215 * a).toInt(), 12, 18, 28))
            c.drawRoundRect(RectF(uw / 2f - w / 2f, ty2 - 17f, uw / 2f + w / 2f, ty2 + 7f), 12f, 12f, p)
            tp.setColor(withA(if (t.color == 1) rgb(255, 150, 150)
                else if (t.color == 2) Color.WHITE else rgb(255, 233, 176), (255 * a).toInt()))
            c.drawText(t.text, uw / 2f, ty2, tp)
            ty2 -= 30f; i--
        }
        // büyük mesaj (zafer / kalp düştü)
        val bt = bigTxt
        if (bt != null && bigT > 0f) {
            val a = Math.min(1f, bigT / 0.8f)
            tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(26f)
            val lines = bt.split("\n")
            var ly = uh * 0.36f
            for (ln in lines) {
                tp.setColor(argb((200 * a).toInt(), 0, 0, 0))
                c.drawText(ln, uw / 2f + 2f, ly + 2f, tp)
                tp.setColor(withA(if (bigCol == 1) rgb(255, 110, 110) else rgb(255, 215, 106),
                    (255 * a).toInt()))
                c.drawText(ln, uw / 2f, ly, tp)
                ly += 34f
            }
        }
        // hasar flaşı + ölüm tülü
        if (flash > 0f) { p.setColor(argb((flash * 110).toInt(), 255, 40, 40)); c.drawRect(0f, 0f, uw, uh, p) }
        if (!s.player.alive) {
            p.setColor(argb(140, 5, 5, 12)); c.drawRect(0f, 0f, uw, uh, p)
            tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(22f); tp.setColor(rgb(255, 110, 110))
            c.drawText("Gölgeler seni yakaladı…", uw / 2f, uh * 0.42f, tp)
            tp.setTextSize(13f); tp.setColor(argb(220, 255, 255, 255))
            c.drawText("Kalp Taşı'nın yanında uyanıyorsun", uw / 2f, uh * 0.42f + 28f, tp)
        }
    }
    private fun drawTouchButtons(c: Canvas, s: GameState) {
        // duraklat (sağ-üst)
        circleBtn(c, uw - 44f, 44f, 28f, argb(170, 12, 18, 28), "II", 13f)
        // VUR (büyük), E, B
        circleBtn(c, uw - 88f, uh - 124f, 58f, argb(205, 201, 47, 63), "SALDIR", 16f)
        circleBtn(c, uw - 206f, uh - 98f, 44f, argb(205, 44, 111, 196), "KULLAN", 12f)
        circleBtn(c, uw - 188f, uh - 212f, 40f,
            if (s.player.buildMode) argb(235, 232, 183, 61) else argb(205, 90, 107, 128), "İNŞA", 13f)
        // joystick
        if (joyId != -1) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3f)
            p.setColor(argb(90, 255, 255, 255))
            c.drawCircle(joyOx, joyOy, 66f, p)
            p.setStyle(Paint.Style.FILL)
            p.setColor(argb(110, 255, 255, 255))
            c.drawCircle(joyOx + joyVx * 48f, joyOy + joyVy * 48f, 24f, p)
        }
    }
    private fun circleBtn(c: Canvas, x: Float, y: Float, r: Float, col: Int, lab: String, ts: Float) {
        p.setColor(col); c.drawCircle(x, y, r, p)
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f)
        p.setColor(argb(70, 255, 255, 255)); c.drawCircle(x, y, r, p)
        p.setStyle(Paint.Style.FILL)
        tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(ts); tp.setColor(Color.WHITE)
        c.drawText(lab, x, y + ts * 0.36f, tp)
    }

    /* ════════════ MENÜ / DURAKLATMA ════════════ */
    private fun panelBtn(c: Canvas, cx: Float, y: Float, w: Float, label: String,
                         id: Int, warn: Boolean) {
        val r = RectF(cx - w / 2f, y, cx + w / 2f, y + 56f)
        p.setColor(if (warn) argb(235, 110, 38, 38) else argb(235, 38, 64, 104))
        c.drawRoundRect(r, 12f, 12f, p)
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f)
        p.setColor(argb(60, 255, 255, 255)); c.drawRoundRect(r, 12f, 12f, p)
        p.setStyle(Paint.Style.FILL)
        tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(17f); tp.setColor(Color.WHITE)
        c.drawText(label, cx, y + 35f, tp)
        btnRects.add(Pair(r, id))
    }
    private fun drawMenu(c: Canvas) { c.save(); c.scale(us, us); drawMenuI(c); c.restore() }
    private fun drawMenuI(c: Canvas) {
        btnRects.clear()
        // arka plan: koyu orman silüeti
        p.setColor(rgb(10, 18, 34)); c.drawRect(0f, 0f, uw, uh * 0.6f, p)
        p.setColor(rgb(16, 46, 32)); c.drawRect(0f, uh * 0.6f, uw, uh, p)
        var i = 0
        while (i < 14) {
            val hx = ((i * 2654435761L) and 0xFFFF) / 65535f
            tri(c, hx * uw, uh, 60f + hx * 70f, 90f + (1f - hx) * 150f, rgb(8, 24, 15))
            i++
        }
        // süzülen ateş böcekleri
        i = 0
        while (i < 18) {
            val ph = uiT * 0.5f + i * 0.7f
            val fx = ((i * 97) % 100) / 100f * uw + sinF(ph) * 30f
            val fy = uh - ((uiT * 16f + i * 67f) % (uh + 40f))
            p.setColor(argb((90 + sinF(ph * 2f) * 70).toInt(), 255, 230, 140))
            c.drawCircle(fx, fy, 2.4f, p)
            i++
        }
        tp.setTextAlign(Paint.Align.CENTER)
        tp.setTextSize(44f); tp.setColor(argb(180, 0, 0, 0))
        c.drawText("KAYIP KRALLIK", uw / 2f + 3f, uh * 0.27f + 3f, tp)
        tp.setColor(rgb(255, 215, 106))
        c.drawText("KAYIP KRALLIK", uw / 2f, uh * 0.27f, tp)
        tp.setTextSize(13f); tp.setColor(argb(190, 255, 255, 255))
        c.drawText("Kalp Taşı'nı 10 gece boyunca kuşatmadan koru", uw / 2f, uh * 0.27f + 30f, tp)
        var y = uh * 0.42f
        panelBtn(c, uw / 2f, y, 300f, "YENİ DÜNYA", 1, false); y += 72f
        if (SaveStore.has(appCtx)) { panelBtn(c, uw / 2f, y, 300f, "DEVAM ET", 2, false); y += 72f }
        tp.setTextSize(11f); tp.setColor(argb(140, 255, 255, 255))
        c.drawText("Tek dosya çekirdek · SurfaceView · motorsuz saf Kotlin", uw / 2f, uh - 28f, tp)
    }
    private fun drawPause(c: Canvas) {
        btnRects.clear()
        p.setColor(argb(170, 5, 8, 14)); c.drawRect(0f, 0f, uw, uh, p)
        tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(30f); tp.setColor(rgb(255, 215, 106))
        c.drawText("MOLA", uw / 2f, uh * 0.3f, tp)
        var y = uh * 0.38f
        panelBtn(c, uw / 2f, y, 300f, "DEVAM ET", 3, false); y += 72f
        panelBtn(c, uw / 2f, y, 300f, "KAYDET", 4, false); y += 72f
        panelBtn(c, uw / 2f, y, 300f, "ANA MENÜ", 5, true)
        panelBtn(c, uw / 2f, y + 66f, 300f, "Yakınlık: " + arrayOf("Yakın", "Orta", "Uzak")[zoomMode], 6, false)
    }

    /* ════════════ DOKUNUŞ ════════════ */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val act = ev.getActionMasked()
        if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
            val idx = ev.getActionIndex()
            val x = ev.getX(idx); val y = ev.getY(idx); val id = ev.getPointerId(idx)
            val ux = x / us; val uy = y / us              // UI sanal tuval koordinatı
            if (mode != M_PLAY) { tapUI(ux, uy); return true }
            val g = game ?: return true
            if (chatOpen || invOpen) {                   // panel modali: dokunuş panele gider
                var k = 0
                while (k < msgRects.size) {
                    val e2 = msgRects[k]
                    if (ux >= e2.first.left && ux <= e2.first.right &&
                        uy >= e2.first.top && uy <= e2.first.bottom) {
                        snd.play("click")
                        if (e2.second == -1) { chatOpen = false; invOpen = false }
                        else if (e2.second == 100) g.craftClub()
                        else { g.quickChat(e2.second); chatOpen = false }
                        return true
                    }
                    k++
                }
                chatOpen = false; invOpen = false; return true
            }
            if (hit(ux, uy, 56f, uh * 0.40f, 42f)) { chatOpen = true; snd.play("click"); return true }
            if (ux >= hotbarRect.left && ux <= hotbarRect.right &&
                uy >= hotbarRect.top && uy <= hotbarRect.bottom) {
                invOpen = true; snd.play("click"); return true
            }
            if (g.s.player.buildMode) {                  // inşa tip barı
                var k = 0
                while (k < buildRects.size) {
                    val e2 = buildRects[k]
                    if (ux >= e2.first.left && ux <= e2.first.right &&
                        uy >= e2.first.top && uy <= e2.first.bottom) {
                        g.s.player.buildSel = e2.second; snd.play("click"); return true
                    }
                    k++
                }
            }
            if (hit(ux, uy, uw - 44f, 44f, 36f)) { mode = M_PAUSE; snd.play("click"); return true }
            if (hit(ux, uy, uw - 88f, uh - 124f, 64f)) { atkHeld = true; atkId = id; g.tapAttack(); return true }
            if (hit(ux, uy, uw - 206f, uh - 98f, 50f)) { g.tapInteract(); return true }
            if (hit(ux, uy, uw - 188f, uh - 212f, 46f)) { g.tapBuild(); snd.play("click"); return true }
            if (x < vw * 0.55f && joyId == -1) {
                joyId = id; joyOx = ux; joyOy = uy; joyVx = 0f; joyVy = 0f
            }
            return true
        }
        if (act == MotionEvent.ACTION_MOVE) {
            if (joyId != -1) {
                val i = ev.findPointerIndex(joyId)
                if (i >= 0) {
                    var dx = ev.getX(i) / us - joyOx; var dy = ev.getY(i) / us - joyOy
                    val d = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    if (d < 66f * 0.18f) { joyVx = 0f; joyVy = 0f }   // ölü bölge
                    else {
                        val m = Math.min(d, 66f)
                        joyVx = dx / d * (m / 66f); joyVy = dy / d * (m / 66f)
                    }
                }
            }
            return true
        }
        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_POINTER_UP ||
            act == MotionEvent.ACTION_CANCEL) {
            if (act == MotionEvent.ACTION_CANCEL) { joyId = -1; joyVx = 0f; joyVy = 0f; atkHeld = false; atkId = -1; return true }
            val idx = ev.getActionIndex()
            val id = ev.getPointerId(idx)
            if (id == joyId) { joyId = -1; joyVx = 0f; joyVy = 0f }
            if (id == atkId) { atkHeld = false; atkId = -1 }
            return true
        }
        return true
    }
    private fun hit(x: Float, y: Float, cx: Float, cy: Float, r: Float): Boolean {
        val dx = x - cx; val dy = y - cy
        return dx * dx + dy * dy <= r * r
    }
    private fun tapUI(x: Float, y: Float) {
        var i = 0
        while (i < btnRects.size) {
            val e = btnRects[i]
            val r = e.first
            if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) {
                snd.play("click")
                when (e.second) {
                    1 -> newGame()
                    2 -> continueGame()
                    3 -> mode = M_PLAY
                    4 -> { saveNow(); toastQ.add(Tst("Oyun kaydedildi ✓", 0)) }
                    5 -> { saveNow(); game = null; mode = M_MENU }
                    6 -> {
                        zoomMode = (zoomMode + 1) % 3
                        appCtx.getSharedPreferences("kk_ui", Context.MODE_PRIVATE)
                            .edit().putInt("zoom", zoomMode).apply()
                        applyZoom()
                    }
                }
                return
            }
            i++
        }
    }

    /* ════════════ küçük yardımcılar ════════════ */
    private fun rgb(r: Int, g: Int, b: Int) = Color.rgb(r, g, b)
    private fun argb(a: Int, r: Int, g: Int, b: Int) =
        Color.argb(clampI(a, 0, 255), r, g, b)
    private fun withA(col: Int, a: Int) =
        (clampI(a, 0, 255) shl 24) or (col and 0x00FFFFFF)
    private fun clampF(v: Float, a: Float, b: Float) = if (v < a) a else if (v > b) b else v
    private fun clampI(v: Int, a: Int, b: Int) = if (v < a) a else if (v > b) b else v
    private fun sinF(v: Float) = Math.sin(v.toDouble()).toFloat()
    private fun cosF(v: Float) = Math.cos(v.toDouble()).toFloat()
    private fun floorI(v: Float): Int { val i = v.toInt(); return if (v < i) i - 1 else i }
}
