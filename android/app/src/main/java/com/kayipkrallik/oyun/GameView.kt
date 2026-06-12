// GameView.kt — Kod-içi render (SurfaceView + Canvas) ve dokunmatik kontrol.
// 2.5D his: dünya düz çizilir, gölge düşmanlar #221440, gece zemin #070a1e'ye karartılır.
// Sol alt sanal çubuk = hareket; sağ butonlar = vur/E/inşa/ye; üstte barlar+saat+hotbar.
package com.kayipkrallik.oyun

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class GameView(ctx: Context, val game: Game, val onSave: () -> Unit) :
    SurfaceView(ctx), SurfaceHolder.Callback, Runnable {

    private var thread: Thread? = null
    @Volatile private var running = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f; isFakeBoldText = true }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 80f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }

    private var camX = 0f; private var camY = 0f
    private val scale = 0.85f   // izometrik sıkışıklık hissi için hafif uzaklaşma
    private var lastFrame = System.nanoTime()
    private var fps = 0f

    // dokunmatik durum
    private var moveId = -1; private var moveBaseX = 0f; private var moveBaseY = 0f; private var moveCurX = 0f; private var moveCurY = 0f
    private var mdx = 0f; private var mdy = 0f
    private var buildMode = false

    // sağ taraf buton dikdörtgenleri (surfaceChanged'da hesaplanır)
    private var btnAttack = RectF(); private var btnE = RectF(); private var btnBuild = RectF()
    private var btnEat = RectF(); private var btnDoor = RectF(); private var btnSwap = RectF(); private var btnSave = RectF()
    private var buildSel = Build.WALL

    init { holder.addCallback(this) }

    override fun surfaceCreated(h: SurfaceHolder) { running = true; thread = Thread(this).also { it.start() } }
    override fun surfaceDestroyed(h: SurfaceHolder) { running = false; try { thread?.join() } catch (_: Exception) {} ; onSave() }
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {
        val bs = 150f; val pad = 30f
        val rx = w - pad; val by = ht - pad
        btnAttack = RectF(rx - bs, by - bs, rx, by)
        btnE = RectF(rx - bs * 2 - pad, by - bs, rx - bs - pad, by)
        btnEat = RectF(rx - bs, by - bs * 2 - pad, rx, by - bs - pad)
        btnBuild = RectF(rx - bs * 2 - pad, by - bs * 2 - pad, rx - bs - pad, by - bs - pad)
        btnDoor = RectF(rx - bs * 3 - pad * 2, by - bs, rx - bs * 2 - pad * 2, by)
        btnSwap = RectF(rx - bs * 3 - pad * 2, by - bs * 2 - pad, rx - bs * 2 - pad * 2, by - bs - pad)
        btnSave = RectF(pad, pad + 60f, pad + 160f, pad + 130f)
    }

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            var dt = (now - lastFrame) / 1_000_000_000f
            lastFrame = now
            if (dt > 0.1f) dt = 0.1f
            fps = if (dt > 0f) (fps * 0.9f + (1f / dt) * 0.1f) else fps

            // hareket uygula
            if (moveId != -1) game.movePlayer(mdx, mdy, dt) else game.movePlayer(0f, 0f, dt)
            game.pickupBags()
            game.update(dt)

            val c = holder.lockCanvas() ?: continue
            try { renderFrame(c) } finally { holder.unlockCanvasAndPost(c) }
        }
    }

    private fun w2sx(x: Float, cw: Int) = (x - camX) * scale + cw / 2
    private fun w2sy(y: Float, ch: Int) = (y - camY) * scale + ch / 2

    private fun renderFrame(c: Canvas) {
        val cw = c.width; val ch = c.height
        // SpringArm lag 9 yaklaşımı: kamerayı oyuncuya yumuşak takip
        camX += (game.px - camX) * 0.12f; camY += (game.py - camY) * 0.12f
        val dk = game.darkness

        // --- zemin karoları ---
        val half = (max(cw, ch) / scale / TILE / 2).toInt() + 2
        val ptx = (game.px / TILE).toInt(); val pty = (game.py / TILE).toInt()
        for (ty in (pty - half)..(pty + half)) for (tx in (ptx - half)..(ptx + half)) {
            val t = game.world.tileAt(tx, ty)
            val col = when (t) {
                Tile.DEEP -> Pal.DEEP; Tile.WATER -> Pal.WATER; Tile.SAND -> Pal.SAND
                Tile.GRASS -> Pal.GRASS; Tile.DARK -> Pal.DARK; Tile.PATH -> Pal.PATH; Tile.CAMP -> Pal.CAMP
            }
            paint.color = Pal.darken(col, dk * 0.82f)
            val sx = w2sx(tx * TILE, cw); val sy = w2sy(ty * TILE, ch)
            val s = TILE * scale + 1f
            c.drawRect(sx, sy, sx + s, sy + s, paint)
        }

        // --- kamp ateşi (başlangıç kampında) ---
        val camp = game.world.startCamp()
        drawFire(c, camp.first * TILE + TILE / 2, camp.second * TILE + TILE / 2, cw, ch)

        // --- ağaç/kaya/çalı ---
        for (tr in game.trees) {
            if (tr.depleted) continue
            val sx = w2sx(tr.x, cw); val sy = w2sy(tr.y, ch)
            val shk = if (tr.shake > 0f) sin(tr.shake * 30f) * 4f else 0f
            when (tr.res) {
                Res.TREE -> {
                    paint.color = Pal.darken(Pal.TRUNK, dk * 0.7f)
                    c.drawRect(sx - 7 + shk, sy - 6, sx + 7 + shk, sy + 26, paint)
                    paint.color = Pal.darken(Pal.PINE[tr.variant % Pal.PINE.size], dk * 0.7f)
                    c.drawCircle(sx + shk, sy - 24, 34f * scale + 16f, paint)
                    paint.color = Pal.darken(Pal.LEAF[tr.variant % Pal.LEAF.size], dk * 0.7f)
                    c.drawCircle(sx + shk - 10, sy - 36, 18f, paint)
                }
                Res.ROCK -> {
                    paint.color = Pal.darken(if (tr.variant == 2) Pal.ROCK2 else Pal.ROCK, dk * 0.7f)
                    c.drawCircle(sx + shk, sy, 26f, paint)
                    paint.color = Pal.darken(Pal.MOSS, dk * 0.7f); c.drawCircle(sx + shk - 8, sy - 8, 7f, paint)
                }
                Res.BUSH -> {
                    paint.color = Pal.darken(Pal.BUSH, dk * 0.7f); c.drawCircle(sx + shk, sy, 22f, paint)
                    paint.color = Pal.BERRY; c.drawCircle(sx + shk + 6, sy - 4, 5f, paint); c.drawCircle(sx + shk - 6, sy + 4, 5f, paint)
                }
                else -> {}
            }
        }

        // --- yapılar ---
        for (wl in game.walls) {
            val sx = w2sx(wl.x, cw); val sy = w2sy(wl.y, ch)
            val shk = if (wl.shake > 0f) sin(wl.shake * 40f) * 5f else 0f
            val s = TILE * scale * 0.5f
            when (wl.type) {
                Build.WALL -> { paint.color = Pal.darken(Pal.TRUNK, dk * 0.6f); c.drawRect(sx - s + shk, sy - s, sx + s + shk, sy + s, paint) }
                Build.DOOR -> {
                    paint.color = Pal.darken(Pal.PATH, dk * 0.6f)
                    if (wl.open) c.drawRect(sx - s + shk, sy - s, sx - s + 12 + shk, sy + s, paint)
                    else c.drawRect(sx - s + shk, sy - s, sx + s + shk, sy + s, paint)
                }
                Build.BALLISTA -> {
                    paint.color = Pal.darken(Pal.TRUNK, dk * 0.5f); c.drawCircle(sx + shk, sy, 28f, paint)
                    paint.color = Pal.STEEL
                    c.drawRect(sx + shk, sy - 4, sx + shk + cos(wl.aimAng) * 40f, sy + 4 + sin(wl.aimAng) * 40f, paint)
                }
            }
            // HP çubuğu (hasarlıysa)
            if (wl.hp < wl.maxHp) {
                paint.color = Color.DKGRAY; c.drawRect(sx - 24, sy - s - 12, sx + 24, sy - s - 6, paint)
                paint.color = Pal.HP; c.drawRect(sx - 24, sy - s - 12, sx - 24 + 48f * (wl.hp / wl.maxHp), sy - s - 6, paint)
            }
        }

        // --- kalp taşı ---
        if (!game.heartDown) {
            val sx = w2sx(game.heartX, cw); val sy = w2sy(game.heartY, ch)
            val float = sin(game.heartPhase * 1.5f) * 8f
            paint.color = if (game.heartFlash > 0f) Pal.SHADOW_HIT else Color.parseColor("#c83b6b")
            c.drawCircle(sx, sy - 30 + float, 26f, paint)
            paint.color = Pal.darken(Pal.GOLD, 0f); c.drawCircle(sx, sy - 30 + float, 10f, paint)
        } else {
            val sx = w2sx(game.heartX, cw); val sy = w2sy(game.heartY, ch)
            paint.color = Color.parseColor("#2a2030"); c.drawCircle(sx, sy, 20f, paint)
        }

        // --- keseler ---
        for (b in game.bags) {
            val sx = w2sx(b.x, cw); val sy = w2sy(b.y, ch)
            paint.color = Pal.GOLD; c.drawCircle(sx, sy, 12f, paint)
            paint.color = Color.parseColor("#8a6a20"); c.drawCircle(sx, sy, 5f, paint)
        }

        // --- hayvanlar ---
        for (cr in game.critters) {
            val sx = w2sx(cr.x, cw); val sy = w2sy(cr.y, ch)
            val hop = if (cr.kind == CritterKind.RABBIT) kotlin.math.abs(sin(cr.hopPhase)) * 6f else 0f
            paint.color = if (cr.kind == CritterKind.RABBIT) Color.parseColor("#d8cdbd") else Color.parseColor("#9a6a3c")
            val r = if (cr.kind == CritterKind.RABBIT) 10f else 16f
            c.drawCircle(sx, sy - hop, r, paint)
        }

        // --- yurttaş Ayla ---
        game.villager?.let { v ->
            if (!v.dead && (game.villagerFreed || true)) {
                val sx = w2sx(v.x, cw); val sy = w2sy(v.y, ch)
                if (!game.villagerFreed) { paint.color = Pal.darken(Pal.TRUNK, dk * 0.6f)
                    c.drawRect(sx - 22, sy - 22, sx + 22, sy + 22, paint) } // kafes
                paint.color = Color.parseColor("#c86fae"); c.drawCircle(sx, sy, 14f, paint)
                paint.color = Pal.SKIN; c.drawCircle(sx, sy - 14, 8f, paint)
                if (v.carrying) { paint.color = Pal.TRUNK; c.drawRect(sx + 8, sy - 18, sx + 20, sy - 6, paint) }
            }
        }

        // --- gölgeler ---
        for (s in game.shadows) {
            val sx = w2sx(s.x, cw); val sy = w2sy(s.y, ch)
            val scl = if (s.dying) (1f - s.deathT / 0.6f) else 1f
            paint.color = if (s.flash > 0f) Pal.SHADOW_HIT else Pal.SHADOW
            c.drawCircle(sx, sy, 22f * scl, paint)
            if (!s.dying) { paint.color = Pal.SHADOW_EYE; c.drawCircle(sx - 6, sy - 4, 3f, paint); c.drawCircle(sx + 6, sy - 4, 3f, paint) }
        }

        // --- cıvatalar ---
        for (b in game.bolts) {
            val f = (b.t / b.dur).coerceIn(0f, 1f)
            val bx = b.x + (b.tx - b.x) * f; val by = b.y + (b.ty - b.y) * f
            paint.color = Pal.STEEL; c.drawCircle(w2sx(bx, cw), w2sy(by, ch), 5f, paint)
        }

        // --- oyuncu ---
        if (!game.dead) drawPlayer(c, cw, ch)

        // --- gece meşale ışığı (basit vinyet boşluğu) ---
        if (dk > 0.3f && !game.dead) {
            paint.color = Color.argb((dk * 70).toInt(), 255, 180, 94)
            c.drawCircle(w2sx(game.px, cw), w2sy(game.py, ch), 220f, paint)
        }

        drawHUD(c, cw, ch)
        drawControls(c)
    }

    private fun drawPlayer(c: Canvas, cw: Int, ch: Int) {
        val sx = w2sx(game.px, cw); val sy = w2sy(game.py, ch)
        paint.color = if (game.flash > 0f) Color.parseColor("#ff5a4d") else Pal.TUNIC
        c.drawRect(sx - 12, sy - 8, sx + 12, sy + 20, paint)
        paint.color = Pal.SKIN; c.drawCircle(sx, sy - 16, 11f, paint)
        // kılıç savuruşu
        if (game.attackT > 0f) {
            val a = game.facing + (game.attackT / 0.18f - 0.5f) * 2.8f
            paint.color = Pal.STEEL
            c.drawRect(sx, sy - 4, sx + cos(a) * 40f, sy + 4 + sin(a) * 40f, paint)
        }
    }

    private fun drawFire(c: Canvas, wx: Float, wy: Float, cw: Int, ch: Int) {
        val sx = w2sx(wx, cw); val sy = w2sy(wy, ch)
        val p = System.nanoTime() / 1e9
        val flick = (sin(p * 7) * 0.5 + sin(p * 13) * 0.5).toFloat() * 4f
        paint.color = Pal.FIRE1; c.drawCircle(sx, sy, 18f + flick, paint)
        paint.color = Pal.FIRE2; c.drawCircle(sx, sy - 4, 12f + flick, paint)
        paint.color = Pal.FIRE3; c.drawCircle(sx, sy - 8, 6f, paint)
    }

    private fun drawHUD(c: Canvas, cw: Int, ch: Int) {
        val bx = 30f; var by = 30f; val bw = 320f; val bh = 26f
        fun bar(label: String, v: Float, mx: Float, col: Int, yy: Float) {
            paint.color = Color.argb(150, 0, 0, 0); c.drawRect(bx, yy, bx + bw, yy + bh, paint)
            paint.color = col; c.drawRect(bx, yy, bx + bw * (v / mx).coerceIn(0f, 1f), yy + bh, paint)
            c.drawText(label, bx + bw + 12, yy + bh - 4, text)
        }
        bar("CAN", game.health, game.maxHealth, Pal.HP, by); by += bh + 6
        bar("ENERJİ", game.stamina, game.maxStamina, Pal.ENERGY, by); by += bh + 6
        bar("AÇLIK", game.hunger, game.maxHunger, Pal.HUNGER, by)

        // saat + gün (sağ üst)
        val icon = if (game.darkness > 0.5f) "☾" else "☀"
        text.textAlign = Paint.Align.RIGHT
        c.drawText("Gün ${game.day}  $icon  ${(game.timeSec / CYCLE * 24).toInt()}:00", cw - 30f, 50f, text)
        c.drawText("FPS ${fps.toInt()}", cw - 30f, 92f, text)
        text.textAlign = Paint.Align.LEFT

        // KALP barı (sağ üst, saatin altı)
        if (!game.heartDown) {
            val hx = cw - 290f; val hy = 110f
            paint.color = Color.argb(150, 0, 0, 0); c.drawRect(hx, hy, hx + 260f, hy + 22f, paint)
            paint.color = Color.parseColor("#c83b6b"); c.drawRect(hx, hy, hx + 260f * (game.heartHp / game.heartMaxHp), hy + 22f, paint)
            text.textAlign = Paint.Align.RIGHT; c.drawText("KALP", cw - 30f, hy + 18f, text); text.textAlign = Paint.Align.LEFT
        }

        // hotbar (alt orta)
        var hxx = cw / 2f - (game.inv.size * 70f) / 2
        for ((k, v) in game.inv) {
            paint.color = Color.argb(160, 20, 20, 30); c.drawRect(hxx, ch - 90f, hxx + 64f, ch - 26f, paint)
            paint.color = when (k) { "wood" -> Pal.TRUNK; "stone" -> Pal.ROCK; "berry" -> Pal.BERRY; "meat" -> Color.parseColor("#c0504a"); "hide" -> Color.parseColor("#9a6a3c"); else -> Color.GRAY }
            c.drawCircle(hxx + 32f, ch - 68f, 16f, paint)
            c.drawText("$v", hxx + 44f, ch - 32f, text)
            hxx += 70f
        }

        // toast'lar
        var tyv = ch / 2f - 120f
        for (z in game.toasts) {
            big.textSize = 44f; big.color = if (z.gold) Pal.GOLD else Pal.HP
            c.drawText(z.text, cw / 2f, tyv, big); tyv += 54f
        }
        // büyük bant
        if (game.bannerT > 0f && game.banner != null) {
            paint.color = Color.argb(180, 0, 0, 0); c.drawRect(0f, ch / 2f - 70f, cw.toFloat(), ch / 2f + 30f, paint)
            big.textSize = 80f; big.color = if (game.bannerGold) Pal.GOLD else Pal.HP
            c.drawText(game.banner!!, cw / 2f, ch / 2f, big)
        }
        if (game.dead) {
            big.textSize = 60f; big.color = Pal.HP
            c.drawText("ÖLDÜN — ${game.respawnT.toInt() + 1}", cw / 2f, ch / 2f - 100f, big)
        }
    }

    private fun drawControls(c: Canvas) {
        // hareket çubuğu
        if (moveId != -1) {
            paint.color = Color.argb(60, 255, 255, 255); c.drawCircle(moveBaseX, moveBaseY, 110f, paint)
            paint.color = Color.argb(120, 255, 255, 255); c.drawCircle(moveCurX, moveCurY, 50f, paint)
        }
        fun btn(r: RectF, label: String, on: Boolean = false) {
            paint.color = if (on) Color.argb(200, 80, 120, 200) else Color.argb(120, 40, 40, 60)
            c.drawRoundRect(r, 20f, 20f, paint)
            text.textAlign = Paint.Align.CENTER; text.textSize = 30f
            c.drawText(label, r.centerX(), r.centerY() + 10, text)
            text.textAlign = Paint.Align.LEFT; text.textSize = 34f
        }
        btn(btnAttack, "VUR")
        btn(btnE, if (buildMode) "YAP" else "E")
        btn(btnBuild, "İNŞA", buildMode)
        btn(btnEat, "YE")
        btn(btnDoor, "KAPI")
        btn(btnSwap, if (buildSel == Build.WALL) "DUVAR" else if (buildSel == Build.DOOR) "KAPI+" else "BALİS")
        btn(btnSave, "KAYDET")
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val act = e.actionMasked
        when (act) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex; val x = e.getX(i); val y = e.getY(i)
                if (handleButtons(x, y)) return true
                if (x < width / 2 && moveId == -1) {
                    moveId = e.getPointerId(i); moveBaseX = x; moveBaseY = y; moveCurX = x; moveCurY = y
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (moveId != -1) {
                    val idx = e.findPointerIndex(moveId)
                    if (idx >= 0) {
                        moveCurX = e.getX(idx); moveCurY = e.getY(idx)
                        var dx = moveCurX - moveBaseX; var dy = moveCurY - moveBaseY
                        val d = hypot(dx, dy)
                        if (d > 110f) { dx = dx / d * 110f; dy = dy / d * 110f; moveCurX = moveBaseX + dx; moveCurY = moveBaseY + dy }
                        mdx = if (d > 12f) dx / 110f else 0f; mdy = if (d > 12f) dy / 110f else 0f
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == moveId) { moveId = -1; mdx = 0f; mdy = 0f }
            }
        }
        return true
    }

    private fun handleButtons(x: Float, y: Float): Boolean {
        when {
            btnAttack.contains(x, y) -> { game.attack(); return true }
            btnEat.contains(x, y) -> { game.tryEat(); return true }
            btnBuild.contains(x, y) -> { buildMode = !buildMode; return true }
            btnSwap.contains(x, y) -> {
                buildSel = when (buildSel) { Build.WALL -> Build.DOOR; Build.DOOR -> Build.BALLISTA; Build.BALLISTA -> Build.WALL }
                return true
            }
            btnDoor.contains(x, y) -> { game.toggleDoor(); return true }
            btnSave.contains(x, y) -> { onSave(); game.toast("Kaydedildi", true); return true }
            btnE.contains(x, y) -> {
                if (buildMode) game.place(buildSel)
                else { if (!game.tryFreeVillager() && !game.tryHarvest()) game.tryEat() }
                return true
            }
        }
        return false
    }
}
