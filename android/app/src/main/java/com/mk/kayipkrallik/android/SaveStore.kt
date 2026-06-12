package com.mk.kayipkrallik.android

import android.content.Context
import com.mk.kayipkrallik.core.Snapshot
import org.json.JSONArray
import org.json.JSONObject

/**
 * KAYIT DEPOSU — çekirdek formattan habersizdir (Snapshot sınıfı saf veri);
 * JSON'a çevirme işi tümüyle kabuğun sorumluluğu (Hexagonal: adaptör katmanı).
 *
 * Anahtar: SharedPreferences "kk_save" / "snap". Tek oyuncu, tek slot.
 * Bozuk/eksik kayıt → null (oyun "Devam Et"i gizler, çökmez).
 */
object SaveStore {
    private const val PREF = "kk_save"
    private const val KEY = "snap"

    fun has(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null) != null

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun save(ctx: Context, s: Snapshot) {
        try {
            val o = JSONObject()
            o.put("v", s.version)
            o.put("seed", s.seed); o.put("t", s.t.toDouble()); o.put("day", s.day)
            o.put("vic", s.victory); o.put("hunt", s.hunt); o.put("vs", s.villagerState)
            o.put("px", s.px.toDouble()); o.put("py", s.py.toDouble())
            o.put("php", s.php.toDouble()); o.put("pen", s.pen.toDouble()); o.put("phu", s.phu.toDouble())
            o.put("hh", s.heartHp.toDouble()); o.put("ha", s.heartAlive)
            val inv = JSONObject()
            for (e in s.inv) inv.put(e.key, e.value)
            o.put("inv", inv)
            val hv = JSONArray()
            for (h in s.harvested) {                       // [karoAnahtarı(Long), kalan*100]
                val a = JSONArray(); a.put(h[0]); a.put(h[1]); hv.put(a)
            }
            o.put("harv", hv)
            val bl = JSONArray()
            for (b in s.builds) {                          // [t,tx,ty,hp,open,ammo]
                val a = JSONArray()
                for (i in b.indices) a.put(b[i])
                bl.put(a)
            }
            o.put("bld", bl)
            val bg = JSONArray()
            for (p in s.bags) {                            // ölüm keseleri de kalıcı
                val a = JSONObject()
                a.put("x", p.first[0].toDouble()); a.put("y", p.first[1].toDouble())
                val l = JSONObject()
                for (e in p.second) l.put(e.key, e.value)
                a.put("l", l)
                bg.put(a)
            }
            o.put("bags", bg)
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, o.toString()).apply()
        } catch (e: Exception) { /* kayıt asla oyunu düşürmez */ }
    }

    fun load(ctx: Context): Snapshot? {
        try {
            val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY, null) ?: return null
            val o = JSONObject(raw)
            val s = Snapshot()
            if (o.getInt("v") != s.version) return null    // sürüm uyuşmazlığı: temiz başla
            s.seed = o.getInt("seed"); s.t = o.getDouble("t").toFloat(); s.day = o.getInt("day")
            s.victory = o.getBoolean("vic"); s.hunt = o.getInt("hunt")
            s.villagerState = o.getInt("vs")
            s.px = o.getDouble("px").toFloat(); s.py = o.getDouble("py").toFloat()
            s.php = o.getDouble("php").toFloat(); s.pen = o.getDouble("pen").toFloat()
            s.phu = o.getDouble("phu").toFloat()
            s.heartHp = o.getDouble("hh").toFloat(); s.heartAlive = o.getBoolean("ha")
            val inv = o.getJSONObject("inv")
            val ik = inv.keys()
            while (ik.hasNext()) { val k = ik.next(); s.inv[k] = inv.getInt(k) }
            val hv = o.getJSONArray("harv")
            for (i in 0 until hv.length()) {
                val a = hv.getJSONArray(i)
                s.harvested.add(longArrayOf(a.getLong(0), a.getLong(1)))
            }
            val bl = o.getJSONArray("bld")
            for (i in 0 until bl.length()) {
                val a = bl.getJSONArray(i)
                s.builds.add(intArrayOf(a.getInt(0), a.getInt(1), a.getInt(2),
                    a.getInt(3), a.getInt(4), a.getInt(5)))
            }
            val bg = o.getJSONArray("bags")
            for (i in 0 until bg.length()) {
                val a = bg.getJSONObject(i)
                val loot = HashMap<String, Int>()
                val l = a.getJSONObject("l")
                val lk = l.keys()
                while (lk.hasNext()) { val k = lk.next(); loot[k] = l.getInt(k) }
                s.bags.add(Pair(floatArrayOf(
                    a.getDouble("x").toFloat(), a.getDouble("y").toFloat()), loot))
            }
            return s
        } catch (e: Exception) { return null }
    }
}
