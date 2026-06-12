// MainActivity.kt — Tam ekran oyun girişi. Kayıt SharedPreferences'ta JSON (versiyonlu v4).
package com.kayipkrallik.oyun

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var game: Game
    private lateinit var view: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val prefs = getSharedPreferences("kayipkrallik", MODE_PRIVATE)
        val seed = prefs.getInt("seed", 12345)
        game = Game(seed)
        game.start()
        // önceki kaydı yükle (varsa)
        prefs.getString("save", null)?.let { s ->
            try { game.loadJson(JSONObject(s)) } catch (_: Exception) {}
        }

        view = GameView(this, game) { save() }
        setContentView(view)
    }

    private fun save() {
        try {
            getSharedPreferences("kayipkrallik", MODE_PRIVATE).edit()
                .putInt("seed", game.seed)
                .putString("save", game.toJson().toString())
                .apply()
        } catch (_: Exception) {}
    }

    override fun onPause() { super.onPause(); save() }

    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
}
