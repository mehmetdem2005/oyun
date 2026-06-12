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
        Z = if (vh > vw) 1.45f else 1.6f                 // dikey-öncelik; yatay da desteklenir
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
        if (g.chatPort == null)                          // yerel sohbet adaptörü (port bağlama)
            g.chatPort = object : ChatPort { override fun send(text: String, who: Int) { } }
        if (atkHeld) g.tapAttack()                      // basılı tut = seri vuruş (çekirdek cd'li)
        g.setDir(joyVx, joyVy)
        if (Math.abs(joyVx) + Math.abs(joyVy) > 0.05f) wt += dt * 9f
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
        if (hitsLeft != null) {                         // hasar çentiği (KO beyaz şerit)
            val mx = K.R_HITS.getValue(kind).toFloat()
            p.setColor(argb(150, 255, 255, 255))
            c.drawRect(x - 8f, y - 36f, x - 8f + 16f * (hitsLeft / mx), y - 34f, p)
        }
    }
    private fun drawHeart(c: Canvas, s: GameState) {
        val h = s.heart
        if (!h.alive) { p.setColor(argb(120, 60, 50, 80)); c.drawCircle(h.x, h.y - 8f, 10f, p); return }
        val x = h.x; val y = h.y
        val pul = sinF(s.t * 2.2f) * 0.5f + 0.5f
        shadowE(c, x, y + 4f, 30f, 10f)
        var k = 0                                        // zemin rün halkası: 8 işaret döner
        while (k < 8) {
            val a = s.t * 0.35f + k * 0.785f
            val rx = x + Math.cos(a.toDouble()).toFloat() * 26f
            val ry = y + 4f + Math.sin(a.toDouble()).toFloat() * 12f
            p.setColor(argb((90 + pul * 70f).toInt(), 255, 215, 106))
            c.save(); c.translate(rx, ry); c.rotate(a * 57.3f)
            c.drawRect(-2.6f, -1.1f, 2.6f, 1.1f, p); c.restore()
            k++
        }
        p.setColor(rgb(96, 84, 70)); c.drawRoundRect(RectF(x - 14f, y - 4f, x + 14f, y + 7f), 4f, 4f, p)
        p.setColor(rgb(70, 60, 50)); c.drawRect(x - 10f, y - 2f, x + 10f, y, p)
        c.save(); c.translate(x, y - 16f); c.rotate(45f)  // kristal
        p.setColor(rgb(255, 215, 106)); c.drawRoundRect(RectF(-9f, -9f, 9f, 9f), 3f, 3f, p)
        p.setColor(argb(200, 255, 240, 180)); c.drawRoundRect(RectF(-9f, -9f, 2f, 2f), 3f, 3f, p)
        p.setColor(withA(Color.WHITE, (120 + pul * 100f).toInt())); c.drawRect(-3f, -7f, -1f, 3f, p)
        c.restore()
        var m = 0                                        // 3 yörünge motesi + kuyruk
        while (m < 3) {
            val a = s.t * 1.3f + m * 2.094f
            val mx = x + Math.cos(a.toDouble()).toFloat() * 30f
            val my = y - 16f + Math.sin(a.toDouble()).toFloat() * 14f
            p.setColor(argb(90, 255, 220, 120))
            c.drawCircle(mx - Math.cos(a.toDouble()).toFloat() * 5f, my - Math.sin(a.toDouble()).toFloat() * 2.4f, 1.6f, p)
            p.setColor(argb(220, 255, 235, 170)); c.drawCircle(mx, my, 2.3f, p)
            m++
        }
        val hr = h.hp / 300f                             // can şeridi
        p.setColor(argb(160, 0, 0, 0)); c.drawRect(x - 18f, y + 11f, x + 18f, y + 15f, p)
        p.setColor(rgb(255, 215, 106)); c.drawRect(x - 17f, y + 12f, x - 17f + 34f * Math.max(0f, hr), y + 14f, p)
    }

    private fun drawBuild(c: Canvas, s: GameState, b: Build) {
        val x = b.x; val y = b.y
        shadowE(c, x, y + 2f, 15f, 5f)
        if (b.t == K.WALL) {
            var i = 0
            while (i < 4) {
                val px = x - 14f + i * 8f
                p.setColor(rgb(110, 74, 42)); c.drawRect(px, y - 26f, px + 6f, y + 2f, p)
                p.setColor(rgb(138, 90, 50)); c.drawRect(px + 1f, y - 30f, px + 5f, y - 25f, p)
                i++
            }
            p.setColor(rgb(138, 90, 50)); c.drawRect(x - 15f, y - 12f, x + 15f, y - 8f, p)
        } else if (b.t == K.WALL_STONE) {
            p.setColor(rgb(126, 134, 148)); c.drawRect(x - 15f, y - 24f, x + 15f, y + 2f, p)
            p.setColor(rgb(168, 176, 189)); c.drawRect(x - 12f, y - 31f, x + 12f, y - 21f, p)
            p.setColor(rgb(90, 97, 109)); c.drawRect(x - 15f, y - 2f, x + 15f, y + 1f, p)
        } else if (b.t == K.WALL_KEEP) {
            p.setColor(rgb(74, 81, 88)); c.drawRect(x - 15f, y - 24f, x + 15f, y + 2f, p)
            p.setColor(rgb(126, 134, 148)); c.drawRect(x - 13f, y - 30f, x + 13f, y - 21f, p)
            var i = 0
            while (i < 3) { c.drawRect(x - 13f + i * 10f, y - 36f, x - 7f + i * 10f, y - 30f, p); i++ }
            p.setColor(rgb(46, 51, 58)); c.drawRect(x - 15f, y - 2f, x + 15f, y + 1f, p)
        } else if (b.t == K.DOOR) {
            p.setColor(rgb(110, 74, 42))
            c.drawRect(x - 15f, y - 26f, x - 11f, y + 2f, p)
            c.drawRect(x + 11f, y - 26f, x + 15f, y + 2f, p)
            c.drawRect(x - 15f, y - 30f, x + 15f, y - 25f, p)
            p.setColor(rgb(138, 90, 50))
            if (b.open) c.drawRect(x - 15f, y - 22f, x - 10f, y - 4f, p)
            else {
                c.drawRect(x - 11f, y - 24f, x + 11f, y + 2f, p)
                p.setColor(rgb(230, 210, 138)); c.drawRect(x + 4f, y - 12f, x + 7f, y - 9f, p)
            }
        } else {                                        // BALİSTA
            p.setColor(rgb(110, 74, 42)); c.drawRect(x - 13f, y - 14f, x + 13f, y + 2f, p)
            p.setColor(rgb(138, 90, 50)); c.drawCircle(x, y - 10f, 8f, p)
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
        val mx = K.B_HP.getValue(b.t)                   // çatlaklar: %60 / %30
        p.setStrokeWidth(1.5f); p.setColor(argb(130, 0, 0, 0))
        if (b.hp < mx * 0.6f) c.drawLine(x - 8f, y - 16f, x + 6f, y - 4f, p)
        if (b.hp < mx * 0.3f) c.drawLine(x + 8f, y - 20f, x - 4f, y - 8f, p)
    }
    /* ════ İNSANSI: tek çizici — yön, yürüyüş salınımı, saldırı süpürmesi, YÜZME ════
       Biçim dili: yuvarlatılmış gövde + top kafa + kapsül uzuvlar; küp yok. */
    private fun limb(c: Canvas, x: Float, y: Float, ang: Float, len: Float, w: Float, col: Int) {
        c.save(); c.translate(x, y); c.rotate(ang)
        p.setColor(col); c.drawRoundRect(RectF(-w, 0f, w, len), w, w, p)
        c.restore()
    }
    private fun drawHumanoid(c: Canvas, s: GameState, x: Float, y: Float,
                             fx: Float, fy: Float, moving: Boolean, walk: Float,
                             attackP: Float, swim: Boolean, hasClub: Boolean,
                             skin: Int, hair: Int, shirt: Int, pants: Int, alpha: Int) {
        val side = Math.abs(fx) >= Math.abs(fy)
        val back = !side && fy < 0f
        val sw = if (moving) sinF(walk) else 0f
        val bob = if (moving) -Math.abs(sinF(walk)) * 1.7f else sinF(s.t * 1.6f) * 0.7f
        c.save(); c.translate(x, y)
        if (side && fx < 0f) c.scale(-1f, 1f)
        if (swim) {                                      // ── YÜZME ──
            val str = sinF(s.t * 7f)
            p.setColor(argb((70 * alpha) / 255, 10, 40, 70))     // su altı silueti
            c.save(); c.scale(1f, 0.45f); c.drawCircle(0f, 26f, 13f, p); c.restore()
            p.setColor(withA(shirt, alpha))                       // öne yatık üst gövde
            c.drawRoundRect(RectF(-8f, -10f + bob, 8f, 4f + bob), 6f, 6f, p)
            limb(c, -7f, -8f + bob, -70f + str * 55f, 11f, 2.6f, withA(skin, alpha))   // kulaç
            limb(c, 7f, -8f + bob, -110f - str * 55f, 11f, 2.6f, withA(skin, alpha))
            p.setColor(withA(skin, alpha)); c.drawCircle(0f, -16f + bob, 6f, p)        // kafa
            p.setColor(withA(hair, alpha)); c.drawCircle(0f, -18.5f + bob, 5.4f, p)
            p.setColor(withA(skin, alpha)); c.drawCircle(0f, -15f + bob, 5.2f, p)
            p.setColor(argb((200 * alpha) / 255, 230, 246, 255))                       // su köpüğü
            c.drawRoundRect(RectF(-12f, 2f + bob, 12f, 5.4f + bob), 3f, 3f, p)
            c.restore(); return
        }
        shadowE(c, 0f, 1f, 15f, 5f)
        limb(c, -3.4f, -10f, sw * 24f, 10f, 2.8f, withA(pants, alpha))                 // bacaklar
        limb(c, 3.4f, -10f, -sw * 24f, 10f, 2.8f, withA(pants, alpha))
        limb(c, -6.5f, -19f + bob, if (attackP > 0f) 0f else sw * -28f, 11f, 2.4f, withA(skin, alpha))
        p.setColor(withA(shirt, alpha))                                                // gövde
        c.drawRoundRect(RectF(-7.5f, -23f + bob, 7.5f, -8f + bob), 5f, 5f, p)
        p.setColor(argb((60 * alpha) / 255, 0, 0, 0))
        c.drawRoundRect(RectF(-7.5f, -12f + bob, 7.5f, -8f + bob), 4f, 4f, p)
        p.setColor(argb((220 * alpha) / 255, 70, 52, 38))
        c.drawRect(-7.5f, -11.4f + bob, 7.5f, -9.6f + bob, p)                          // kemer
        if (attackP > 0f) {                              // ön kol + SOPA süpürmesi
            val swing = -95f + (1f - attackP) * 150f
            limb(c, 6.5f, -19f + bob, swing, 11f, 2.4f, withA(skin, alpha))
            if (hasClub) {
                c.save(); c.translate(6.5f, -19f + bob); c.rotate(swing)
                p.setColor(withA(rgb(126, 84, 46), alpha))
                c.drawRoundRect(RectF(-2.2f, 9f, 2.2f, 24f), 2.2f, 2.2f, p)
                p.setColor(withA(rgb(98, 64, 34), alpha)); c.drawCircle(0f, 23f, 3.6f, p)
                c.restore()
            }
        } else limb(c, 6.5f, -19f + bob, sw * 28f, 11f, 2.4f, withA(skin, alpha))
        p.setColor(withA(skin, alpha)); c.drawCircle(0f, -29f + bob, 6.4f, p)          // kafa
        p.setColor(withA(hair, alpha)); c.drawCircle(0f, -31.6f + bob, 5.8f, p)        // saç kapağı
        if (!back) {
            p.setColor(withA(skin, alpha)); c.drawCircle(0f, -28f + bob, 5.6f, p)
            p.setColor(argb(alpha, 34, 30, 30))
            if (side) c.drawCircle(2.6f, -29f + bob, 1.2f, p)
            else { c.drawCircle(-2.2f, -29f + bob, 1.2f, p); c.drawCircle(2.2f, -29f + bob, 1.2f, p) }
        } else { p.setColor(withA(hair, alpha)); c.drawCircle(0f, -28.6f + bob, 5.9f, p) }
        c.restore()
    }

    private fun drawPlayer(c: Canvas, s: GameState) {
        val pl = s.player
        if (!pl.alive) return
        drawHumanoid(c, s, pl.x, pl.y, pl.fx, pl.fy,
            Math.abs(joyVx) + Math.abs(joyVy) > 0.05f, wt,
            if (pl.attackT > 0f) pl.attackT / 0.18f else 0f, pl.swim,
            (pl.inv["club"] ?: 0) > 0,
            rgb(242, 198, 152), rgb(56, 40, 32), rgb(202, 92, 58), rgb(50, 60, 92), 255)
        if (pl.buildMode && !pl.swim) {                  // inşa modunda baş üstü işaret
            p.setColor(argb(230, 232, 183, 61)); c.drawCircle(pl.x, pl.y - 38f, 3.2f, p)
        }
    }

    private fun drawVillager(c: Canvas, s: GameState) {
        val v = s.villager ?: return
        if (v.state == K.VS_CAGED) {
            drawHumanoid(c, s, v.x, v.y, 0f, 1f, false, 0f, 0f, false, false,
                rgb(238, 192, 150), rgb(214, 178, 92), rgb(94, 128, 86), rgb(70, 62, 56), 235)
            p.setColor(rgb(108, 82, 50)); var k = -2     // kafes çubukları
            while (k <= 2) { c.drawRect(v.x + k * 7f - 1.4f, v.y - 38f, v.x + k * 7f + 1.4f, v.y + 4f, p); k++ }
            c.drawRect(v.x - 16f, v.y - 40f, v.x + 16f, v.y - 36f, p)
            c.drawRect(v.x - 16f, v.y, v.x + 16f, v.y + 4f, p)
            drawTag(c, v.x, v.y - 50f, "E: Kurtar")
            return
        }
        drawHumanoid(c, s, v.x, v.y, if (v.tgtX < v.x) -1f else 1f, 0.4f,
            v.hasTgt, s.t * 8f + 1f, 0f, false, false,
            rgb(238, 192, 150), rgb(214, 178, 92), rgb(94, 128, 86), rgb(70, 62, 56), 255)
        if (v.carry > 0) {                               // omuzdaki kütük
            c.save(); c.translate(v.x, v.y - 30f); c.rotate(-18f)
            p.setColor(rgb(138, 92, 52)); c.drawRoundRect(RectF(-11f, -3f, 11f, 3f), 3f, 3f, p)
            p.setColor(rgb(190, 152, 104)); c.drawCircle(11f, 0f, 2.8f, p)
            c.restore()
        }
        drawTag(c, v.x, v.y - 46f, "Ayla")
    }

    /* ── GÖLGE: çekirdek küre + 3 kıvrık duman teli + nabızlı gözler ── */
    private fun drawShadow(c: Canvas, s: GameState, e: Shadow) {
        c.save(); c.translate(e.x, e.y); c.scale(e.scale, e.scale)
        shadowE(c, 0f, 2f, 16f, 5f)
        var tn = 0
        while (tn < 3) {
            var seg = 0
            while (seg < 3) {
                val wob = sinF(s.t * 4f + e.ph + tn * 1.7f + seg * 0.9f)
                val txp = Math.cos((tn * 2.1f + 1f + wob * 0.5f).toDouble()).toFloat() * (6f + seg * 5f)
                p.setColor(argb(150 - seg * 38, 96, 44, 168))
                c.drawCircle(txp, -8f - seg * 7f + wob * 2.5f, 5.5f - seg * 1.3f, p)
                seg++
            }
            tn++
        }
        p.setColor(argb(80, 70, 24, 130)); c.drawCircle(0f, -9f, 13.5f, p)
        p.setColor(argb(205, 96, 44, 168)); c.drawCircle(0f, -9f, 10.5f, p)
        p.setColor(argb(235, 130, 70, 210)); c.drawCircle(-2.5f, -11.5f, 6f, p)
        val eg = (170 + sinF(s.t * 7f + e.ph) * 70f).toInt()
        p.setColor(argb(eg, 244, 240, 255))
        c.drawCircle(-3.4f, -10f, 1.9f, p); c.drawCircle(3.4f, -10f, 1.9f, p)
        if (e.stolen.isNotEmpty()) {
            c.save(); c.translate(0f, -26f + sinF(s.t * 5f) * 2f); c.rotate(45f)
            p.setColor(rgb(255, 215, 106)); c.drawRect(-4f, -4f, 4f, 4f, p)
            c.restore()
        }
        c.restore()
    }

    /* ── FAUNA: dört tür, tür-özel iskelet animasyonu ── */
    private fun drawCritter(c: Canvas, s: GameState, cr: Critter) {
        val trot = sinF((s.t + cr.ph) * 10f)
        c.save(); c.translate(cr.x, cr.y)
        if (cr.wx < 0f) c.scale(-1f, 1f)
        if (cr.kind == K.CK_RABBIT) {
            val hop = if (cr.moving) Math.abs(sinF((s.t + cr.ph) * 9f)) else 0f
            shadowE(c, 0f, 1f, 9f, 3f)
            c.translate(0f, -hop * 6f)
            c.save(); c.scale(1f, 1f - hop * 0.22f)      // squash-stretch
            p.setColor(rgb(196, 186, 172)); c.drawRoundRect(RectF(-7f, -10f, 7f, 0f), 6f, 6f, p)
            c.restore()
            p.setColor(rgb(232, 226, 216)); c.drawCircle(-6.4f, -3f, 2.2f, p)
            limb(c, 3.4f, -12.4f, -14f - hop * 26f, 7.5f, 1.8f, rgb(186, 176, 162))    // kulak gecikmesi
            limb(c, 5.4f, -12f, 4f - hop * 22f, 7f, 1.8f, rgb(186, 176, 162))
            p.setColor(rgb(196, 186, 172)); c.drawCircle(5f, -9.4f, 4f, p)
            p.setColor(rgb(30, 26, 26)); c.drawCircle(6.6f, -10f, 1f, p)
        } else if (cr.kind == K.CK_DEER) {
            shadowE(c, 0f, 1f, 14f, 4f)
            val graze = if (!cr.moving) (sinF(s.t * 1.1f + cr.ph) * 0.5f + 0.5f) else 0f
            var lg = 0
            while (lg < 4) {                              // çapraz tırıs
                limb(c, -8f + lg * 5.2f, -8f, if (cr.moving) (if (lg % 2 == 0) trot else -trot) * 18f else 0f,
                    9f, 1.7f, rgb(126, 92, 58))
                lg++
            }
            p.setColor(rgb(154, 112, 70)); c.drawRoundRect(RectF(-11f, -17f, 9f, -7f), 6f, 6f, p)
            if (cr.moving) { p.setColor(rgb(240, 234, 224)); c.drawCircle(-11.4f, -14f + trot * 1.4f, 2.4f, p) }
            c.save(); c.translate(9f, -15f); c.rotate(-34f + graze * 78f)               // otlayan boyun
            p.setColor(rgb(150, 108, 66)); c.drawRoundRect(RectF(-2.2f, -10f, 2.2f, 2f), 2.2f, 2.2f, p)
            p.setColor(rgb(146, 104, 64)); c.drawRoundRect(RectF(-3f, -15f, 3.4f, -8f), 3f, 3f, p)
            c.drawLine(0f, -14f, -3.4f, -20f, p2(1.6f, rgb(94, 70, 46)))                // boynuz
            c.drawLine(-1.8f, -17f, -5f, -19f, p2(1.4f, rgb(94, 70, 46)))
            c.drawLine(1f, -14f, 4f, -20f, p2(1.6f, rgb(94, 70, 46)))
            c.drawLine(2.4f, -17f, 5.6f, -19f, p2(1.4f, rgb(94, 70, 46)))
            p.setColor(rgb(30, 26, 26)); c.drawCircle(1.6f, -12f, 0.9f, p)
            c.restore()
        } else if (cr.kind == K.CK_BOAR) {
            shadowE(c, 0f, 1f, 14f, 4.4f)
            val low = if (cr.chargeT > 0f) 3f else 0f
            var lg = 0
            while (lg < 4) {
                limb(c, -8f + lg * 5f, -6f, if (cr.moving) (if (lg % 2 == 0) trot else -trot) * 16f else 0f,
                    6.5f, 2f, rgb(70, 52, 40))
                lg++
            }
            p.setColor(rgb(92, 66, 48)); c.drawRoundRect(RectF(-12f, -15f + low * 0.4f, 10f, -4f), 7f, 7f, p)
            p.setColor(rgb(70, 50, 36)); var b2 = 0      // sırt kılları
            while (b2 < 4) { c.drawRect(-8f + b2 * 4.4f, -17f + low * 0.4f, -6.4f + b2 * 4.4f, -13.5f, p); b2++ }
            c.save(); c.translate(10f, -9f + low); c.rotate(low * 5f)
            p.setColor(rgb(84, 60, 44)); c.drawRoundRect(RectF(-4f, -5f, 6f, 4f), 4f, 4f, p)
            p.setColor(rgb(120, 88, 64)); c.drawCircle(6f, 0f, 2.6f, p)
            p.setColor(rgb(238, 232, 220))               // dişler
            c.drawRect(3.2f, 1.4f, 4.6f, 5.2f, p); c.drawRect(5.6f, 1.4f, 7f, 5.2f, p)
            p.setColor(if (cr.chargeT > 0f) rgb(255, 90, 70) else rgb(30, 26, 26))
            c.drawCircle(0.6f, -2.4f, 1.1f, p)
            c.restore()
            if (cr.chargeT > 0f) burst(cr.x, cr.y, rgb(150, 130, 104), 1, 30f, 2f, 0.3f)  // toz
        } else {                                          // KURT
            shadowE(c, 0f, 1f, 15f, 4.4f)
            var lg = 0
            while (lg < 4) {
                limb(c, -9f + lg * 5.4f, -8f, if (cr.moving) (if (lg % 2 == 0) trot else -trot) * 22f else 0f,
                    9f, 1.8f, rgb(96, 102, 114))
                lg++
            }
            p.setColor(rgb(126, 134, 148)); c.drawRoundRect(RectF(-12f, -16f, 9f, -6f), 6f, 6f, p)
            p.setColor(rgb(96, 102, 114)); c.drawRoundRect(RectF(-12f, -16f, 9f, -12f), 5f, 5f, p)
            c.save(); c.translate(-12f, -13f)             // kuyruk: 2 parçalı salınım
            c.rotate((if (cr.aggro) 26f else -8f) + sinF(s.t * (if (cr.aggro) 9f else 4f)) * 14f)
            p.setColor(rgb(96, 102, 114)); c.drawRoundRect(RectF(-9f, -2f, 0f, 2f), 2f, 2f, p)
            p.setColor(rgb(80, 86, 98)); c.drawCircle(-9f, 0f, 2.4f, p)
            c.restore()
            c.save(); c.translate(9f, -14f)
            p.setColor(rgb(126, 134, 148)); c.drawRoundRect(RectF(-3f, -4f, 8f, 4f), 4f, 4f, p)
            limb(c, -1f, -5.5f, -24f, 4.5f, 1.6f, rgb(96, 102, 114))                    // kulaklar
            limb(c, 3f, -5.5f, 18f, 4.5f, 1.6f, rgb(96, 102, 114))
            if (cr.aggro) {
                p.setColor(rgb(255, 80, 70)); c.drawCircle(2f, -1.4f, 1.3f, p)          // kor göz
                p.setColor(rgb(238, 232, 220)); c.drawRect(5.4f, 2.6f, 6.6f, 4.6f, p)   // diş
            } else { p.setColor(rgb(30, 26, 26)); c.drawCircle(2f, -1.4f, 1f, p) }
            c.restore()
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
    /* ── SOHBET HUD: düğme + akış; paneller modal ── */
    private fun drawChatHud(c: Canvas, s: GameState) {
        val bx = 56f; val by = vh * 0.40f
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
        p.setColor(argb(120, 0, 0, 0)); c.drawRect(0f, 0f, vw, vh, p)
        val pw = Math.min(vw - 40f, 360f); val ph = 64f * K.QCHAT.size + 110f
        val l = vw / 2f - pw / 2f; val t0 = vh / 2f - ph / 2f
        p.setColor(argb(242, 16, 24, 38)); c.drawRoundRect(RectF(l, t0, l + pw, t0 + ph), 18f, 18f, p)
        tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(16f); tp.setColor(rgb(255, 215, 106))
        c.drawText("HIZLI MESAJ", vw / 2f, t0 + 32f, tp)
        var k = 0
        while (k < K.QCHAT.size) {
            val r = RectF(l + 18f, t0 + 50f + k * 64f, l + pw - 18f, t0 + 102f + k * 64f)
            p.setColor(argb(235, 38, 64, 104)); c.drawRoundRect(r, 12f, 12f, p)
            tp.setTextSize(14f); tp.setColor(Color.WHITE)
            c.drawText(K.QCHAT[k], vw / 2f, r.top + 33f, tp)
            msgRects.add(Pair(r, k)); k++
        }
        val cr = RectF(l + 18f, t0 + ph - 48f, l + pw - 18f, t0 + ph - 14f)
        p.setColor(argb(220, 90, 40, 40)); c.drawRoundRect(cr, 10f, 10f, p)
        tp.setTextSize(13f); tp.setColor(Color.WHITE); c.drawText("KAPAT", vw / 2f, cr.top + 23f, tp)
        msgRects.add(Pair(cr, -1))
        tp.setTextSize(9.5f); tp.setColor(argb(150, 255, 255, 255))
        c.drawText("Global sohbet yol haritasında (sunucu adaptörü)", vw / 2f, t0 + ph - 58f, tp)
    }
    private fun drawInvPanel(c: Canvas, s: GameState) {
        msgRects.clear()
        p.setColor(argb(120, 0, 0, 0)); c.drawRect(0f, 0f, vw, vh, p)
        val keys = arrayOf("wood", "stone", "berry", "meat", "hide", "club")
        val pw = Math.min(vw - 40f, 380f); val ph = 36f * keys.size + 196f
        val l = vw / 2f - pw / 2f; val t0 = vh / 2f - ph / 2f
        p.setColor(argb(244, 16, 24, 38)); c.drawRoundRect(RectF(l, t0, l + pw, t0 + ph), 18f, 18f, p)
        tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(16f); tp.setColor(rgb(255, 215, 106))
        c.drawText("ENVANTER", vw / 2f, t0 + 30f, tp)
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
        c.drawText(if (canClub) "SOPA \u00dcRET \u2014 3 odun" else "Sopa ku\u015fan\u0131ld\u0131 \u2713", vw / 2f, cy + 31f, tp)
        msgRects.add(Pair(r1, 100))
        tp.setTextSize(10f); tp.setColor(argb(170, 255, 255, 255))
        c.drawText("\u0130pucu: \u0130n\u015fa modunda E \u2192 \u00f6ndeki yap\u0131y\u0131 S\u00d6K (%50 iade)", vw / 2f, cy + 70f, tp)
        val cr = RectF(l + 18f, t0 + ph - 48f, l + pw - 18f, t0 + ph - 14f)
        p.setColor(argb(220, 90, 40, 40)); c.drawRoundRect(cr, 10f, 10f, p)
        tp.setTextSize(13f); tp.setColor(Color.WHITE); c.drawText("KAPAT", vw / 2f, cr.top + 23f, tp)
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
    private fun drawHud(c: Canvas, s: GameState) {
        val g = game!!
        bar(c, 20f, 18f, 230f, s.player.hp / 100f, rgb(226, 61, 79), "CAN")
        bar(c, 20f, 40f, 230f, s.player.en / 100f, rgb(232, 183, 61), "ENERJİ")
        bar(c, 20f, 62f, 230f, s.player.hu / 100f, rgb(224, 123, 47), "AÇLIK")
        bar(c, 20f, 84f, 230f, s.heart.hp / 300f, rgb(255, 215, 106), "KALP")
        // saat + hedef
        tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(16f); tp.setColor(Color.WHITE)
        c.drawText(g.clockText(), vw / 2f, 32f, tp)
        tp.setTextSize(11f); tp.setColor(argb(210, 255, 255, 255))
        val goal = if (s.victory) "Hikâye senin — krallık ayakta ★"
            else if (!s.heart.alive) "Kalp düştü… güneşi yine de kovala"
            else "Hedef: 10 gece hayatta kal · Gün " + Math.min(s.day, 10) + "/10"
        c.drawText(goal, vw / 2f, 52f, tp)
        // envanter çipleri (alt-orta)
        val items = arrayOf("wood", "stone", "berry", "meat", "hide")
        val cols = intArrayOf(rgb(138, 90, 50), rgb(154, 160, 173),
            rgb(226, 61, 79), rgb(196, 77, 94), rgb(168, 116, 63))
        val cw = 66f; val totW = cw * items.size
        var x = vw / 2f - totW / 2f
        val hy = vh - 56f
        var i = 0
        while (i < items.size) {
            p.setColor(argb(170, 12, 18, 28))
            c.drawRoundRect(RectF(x + 3f, hy, x + cw - 3f, hy + 40f), 9f, 9f, p)
            p.setColor(cols[i])
            c.drawRoundRect(RectF(x + 10f, hy + 8f, x + 26f, hy + 24f), 4f, 4f, p)
            tp.setTextAlign(Paint.Align.LEFT); tp.setTextSize(15f); tp.setColor(Color.WHITE)
            c.drawText("" + (s.player.inv[items[i]] ?: 0), x + 32f, hy + 23f, tp)
            tp.setTextSize(8f); tp.setColor(argb(160, 255, 255, 255))
            c.drawText(K.ITEM_TR.getValue(items[i]), x + 10f, hy + 36f, tp)
            x += cw; i++
        }
        hotbarRect = RectF(vw / 2f - totW / 2f - 4f, hy - 6f, vw / 2f + totW / 2f + 4f, hy + 44f)
        // inşa paneli: seçili yapı + maliyet (yetmeyen kalem kırmızı)
        if (s.player.buildMode) {
            tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(13f)
            tp.setColor(rgb(255, 215, 106))
            c.drawText("İNŞA: " + K.B_NAME.getValue(s.player.buildSel) +
                "  (E: tip değiştir · VUR: yerleştir)", vw / 2f, hy - 30f, tp)
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
            c.drawText(sb.toString(), vw / 2f, hy - 14f, tp)
            buildRects.clear()                            // hızlı tip seçimi: 5 dokunmatik kutu
            val bw = 52f; var bxx = vw / 2f - bw * 2.5f
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
        drawChatHud(c, s)
        drawTouchButtons(c, s)
        if (chatOpen) drawChatPanel(c)
        if (invOpen) drawInvPanel(c, s)
        // tostlar
        var ty2 = vh - 116f
        i = toastQ.size - 1
        while (i >= 0) {
            val t = toastQ[i]
            tp.setTextSize(12f); tp.setTextAlign(Paint.Align.CENTER)
            val w = tp.measureText(t.text) + 26f
            val a = Math.min(1f, t.t / 0.4f)
            p.setColor(argb((215 * a).toInt(), 12, 18, 28))
            c.drawRoundRect(RectF(vw / 2f - w / 2f, ty2 - 17f, vw / 2f + w / 2f, ty2 + 7f), 12f, 12f, p)
            tp.setColor(withA(if (t.color == 1) rgb(255, 150, 150)
                else if (t.color == 2) Color.WHITE else rgb(255, 233, 176), (255 * a).toInt()))
            c.drawText(t.text, vw / 2f, ty2, tp)
            ty2 -= 30f; i--
        }
        // büyük mesaj (zafer / kalp düştü)
        val bt = bigTxt
        if (bt != null && bigT > 0f) {
            val a = Math.min(1f, bigT / 0.8f)
            tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(26f)
            val lines = bt.split("\n")
            var ly = vh * 0.36f
            for (ln in lines) {
                tp.setColor(argb((200 * a).toInt(), 0, 0, 0))
                c.drawText(ln, vw / 2f + 2f, ly + 2f, tp)
                tp.setColor(withA(if (bigCol == 1) rgb(255, 110, 110) else rgb(255, 215, 106),
                    (255 * a).toInt()))
                c.drawText(ln, vw / 2f, ly, tp)
                ly += 34f
            }
        }
        // hasar flaşı + ölüm tülü
        if (flash > 0f) { p.setColor(argb((flash * 110).toInt(), 255, 40, 40)); c.drawRect(0f, 0f, vw, vh, p) }
        if (!s.player.alive) {
            p.setColor(argb(140, 5, 5, 12)); c.drawRect(0f, 0f, vw, vh, p)
            tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(22f); tp.setColor(rgb(255, 110, 110))
            c.drawText("Gölgeler seni yakaladı…", vw / 2f, vh * 0.42f, tp)
            tp.setTextSize(13f); tp.setColor(argb(220, 255, 255, 255))
            c.drawText("Kalp Taşı'nın yanında uyanıyorsun", vw / 2f, vh * 0.42f + 28f, tp)
        }
    }
    private fun drawTouchButtons(c: Canvas, s: GameState) {
        // duraklat (sağ-üst)
        circleBtn(c, vw - 44f, 44f, 28f, argb(170, 12, 18, 28), "II", 13f)
        // VUR (büyük), E, B
        circleBtn(c, vw - 86f, vh - 150f, 56f, argb(200, 201, 47, 63), "VUR", 17f)
        circleBtn(c, vw - 196f, vh - 116f, 40f, argb(200, 44, 111, 196), "E", 17f)
        circleBtn(c, vw - 216f, vh - 216f, 36f,
            if (s.player.buildMode) argb(230, 232, 183, 61) else argb(200, 90, 107, 128), "B", 16f)
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
    private fun drawMenu(c: Canvas) {
        btnRects.clear()
        // arka plan: koyu orman silüeti
        p.setColor(rgb(10, 18, 34)); c.drawRect(0f, 0f, vw, vh * 0.6f, p)
        p.setColor(rgb(16, 46, 32)); c.drawRect(0f, vh * 0.6f, vw, vh, p)
        var i = 0
        while (i < 14) {
            val hx = ((i * 2654435761L) and 0xFFFF) / 65535f
            tri(c, hx * vw, vh, 60f + hx * 70f, 90f + (1f - hx) * 150f, rgb(8, 24, 15))
            i++
        }
        // süzülen ateş böcekleri
        i = 0
        while (i < 18) {
            val ph = uiT * 0.5f + i * 0.7f
            val fx = ((i * 97) % 100) / 100f * vw + sinF(ph) * 30f
            val fy = vh - ((uiT * 16f + i * 67f) % (vh + 40f))
            p.setColor(argb((90 + sinF(ph * 2f) * 70).toInt(), 255, 230, 140))
            c.drawCircle(fx, fy, 2.4f, p)
            i++
        }
        tp.setTextAlign(Paint.Align.CENTER)
        tp.setTextSize(44f); tp.setColor(argb(180, 0, 0, 0))
        c.drawText("KAYIP KRALLIK", vw / 2f + 3f, vh * 0.27f + 3f, tp)
        tp.setColor(rgb(255, 215, 106))
        c.drawText("KAYIP KRALLIK", vw / 2f, vh * 0.27f, tp)
        tp.setTextSize(13f); tp.setColor(argb(190, 255, 255, 255))
        c.drawText("Kalp Taşı'nı 10 gece boyunca kuşatmadan koru", vw / 2f, vh * 0.27f + 30f, tp)
        var y = vh * 0.42f
        panelBtn(c, vw / 2f, y, 300f, "YENİ DÜNYA", 1, false); y += 72f
        if (SaveStore.has(appCtx)) { panelBtn(c, vw / 2f, y, 300f, "DEVAM ET", 2, false); y += 72f }
        tp.setTextSize(11f); tp.setColor(argb(140, 255, 255, 255))
        c.drawText("Tek dosya çekirdek · SurfaceView · motorsuz saf Kotlin", vw / 2f, vh - 28f, tp)
    }
    private fun drawPause(c: Canvas) {
        btnRects.clear()
        p.setColor(argb(170, 5, 8, 14)); c.drawRect(0f, 0f, vw, vh, p)
        tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(30f); tp.setColor(rgb(255, 215, 106))
        c.drawText("MOLA", vw / 2f, vh * 0.3f, tp)
        var y = vh * 0.38f
        panelBtn(c, vw / 2f, y, 300f, "DEVAM ET", 3, false); y += 72f
        panelBtn(c, vw / 2f, y, 300f, "KAYDET", 4, false); y += 72f
        panelBtn(c, vw / 2f, y, 300f, "ANA MENÜ", 5, true)
    }

    /* ════════════ DOKUNUŞ ════════════ */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val act = ev.getActionMasked()
        if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
            val idx = ev.getActionIndex()
            val x = ev.getX(idx); val y = ev.getY(idx); val id = ev.getPointerId(idx)
            if (mode != M_PLAY) { tapUI(x, y); return true }
            val g = game ?: return true
            if (chatOpen || invOpen) {                   // panel modali: dokunuş panele gider
                var k = 0
                while (k < msgRects.size) {
                    val e2 = msgRects[k]
                    if (x >= e2.first.left && x <= e2.first.right &&
                        y >= e2.first.top && y <= e2.first.bottom) {
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
            if (hit(x, y, 56f, vh * 0.40f, 42f)) { chatOpen = true; snd.play("click"); return true }
            if (x >= hotbarRect.left && x <= hotbarRect.right &&
                y >= hotbarRect.top && y <= hotbarRect.bottom) {
                invOpen = true; snd.play("click"); return true
            }
            if (g.s.player.buildMode) {                  // inşa tip barı
                var k = 0
                while (k < buildRects.size) {
                    val e2 = buildRects[k]
                    if (x >= e2.first.left && x <= e2.first.right &&
                        y >= e2.first.top && y <= e2.first.bottom) {
                        g.s.player.buildSel = e2.second; snd.play("click"); return true
                    }
                    k++
                }
            }
            if (hit(x, y, vw - 44f, 44f, 34f)) { mode = M_PAUSE; snd.play("click"); return true }
            if (hit(x, y, vw - 86f, vh - 150f, 62f)) { atkHeld = true; atkId = id; g.tapAttack(); return true }
            if (hit(x, y, vw - 196f, vh - 116f, 46f)) { g.tapInteract(); return true }
            if (hit(x, y, vw - 216f, vh - 216f, 42f)) { g.tapBuild(); snd.play("click"); return true }
            if (x < vw * 0.55f && joyId == -1) {
                joyId = id; joyOx = x; joyOy = y; joyVx = 0f; joyVy = 0f
            }
            return true
        }
        if (act == MotionEvent.ACTION_MOVE) {
            if (joyId != -1) {
                val i = ev.findPointerIndex(joyId)
                if (i >= 0) {
                    var dx = ev.getX(i) - joyOx; var dy = ev.getY(i) - joyOy
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
