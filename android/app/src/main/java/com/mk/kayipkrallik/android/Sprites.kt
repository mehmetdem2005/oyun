package com.mk.kayipkrallik.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/* ════ SPRITE HATTI ════
   assets/sprites altındaki .png dosyaları otomatik yüklenir. Adlandırma: <ad>_f<KARE>.png
   (örn. player_walk_f6.png = 6 kare yatay şerit). Sprite yoksa oyun
   prosedürel çizime düşer — sıfır kırılma. Prompt'lar: docs/SPRITE-PROMPTS.md */
object Sprites {
    private val bmps = HashMap<String, Bitmap>()
    private val frs = HashMap<String, Int>()
    private var loaded = false
    private val src = Rect(); private val dst = RectF(0f, 0f, 0f, 0f)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            val am = ctx.getAssets()
            val files = am.list("sprites") ?: return
            var i = 0
            while (i < files.size) {
                val f = files[i]; i++
                if (!f.endsWith(".png")) continue
                var key = f.substring(0, f.length - 4)
                var fr = 1
                val k = key.lastIndexOf("_f")
                if (k > 0) {
                    val n = key.substring(k + 2).toIntOrNull()
                    if (n != null && n > 0) { fr = n; key = key.substring(0, k) }
                }
                val st = am.open("sprites/" + f)
                val bm = BitmapFactory.decodeStream(st)
                st.close()
                if (bm != null) { bmps[key] = bm; frs[key] = fr }
            }
        } catch (e: Exception) { }
    }

    fun has(key: String) = bmps.containsKey(key)

    /* t*fps ile dönen kare; w = dünya genişliği (yükseklik orandan), taban (x,y). */
    fun draw(c: Canvas, key: String, t: Float, fps: Float,
             x: Float, y: Float, w: Float, flip: Boolean, p: Paint) {
        val bm = bmps[key] ?: return
        val fr = frs[key] ?: 1
        val fw = bm.getWidth() / Math.max(1, fr)
        val fh = bm.getHeight()
        if (fw <= 0 || fh <= 0) return
        val idx = if (fr <= 1) 0 else (((t * fps).toInt() % fr) + fr) % fr
        val h = w * fh / fw
        src.left = idx * fw; src.top = 0; src.right = idx * fw + fw; src.bottom = fh
        dst.left = x - w / 2f; dst.top = y - h; dst.right = x + w / 2f; dst.bottom = y
        c.save()
        if (flip) { c.translate(x, 0f); c.scale(-1f, 1f); c.translate(-x, 0f) }
        c.drawBitmap(bm, src, dst, p)
        c.restore()
    }
}
