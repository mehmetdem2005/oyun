package com.mk.kayipkrallik.android

import android.app.Activity
import android.os.Bundle

/**
 * GİRİŞ — tek etkinlik, tek görünüm. Tam ekran/yan yatış manifest'te
 * (Theme.Black.NoTitleBar.Fullscreen + sensorLandscape); burada yalnız
 * yaşam döngüsü köprülenir: arka plana inerken KAYDET + ipliği durdur.
 */
class MainActivity : Activity() {
    private var view: GameView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val v = GameView(this)
        view = v
        setContentView(v)
    }

    override fun onPause() { view?.onHostPause(); super.onPause() }
    override fun onResume() { super.onResume(); view?.onHostResume() }

    override fun onBackPressed() {
        if (view?.onBack() != true) {
            view?.onHostDestroy()
            super.onBackPressed()
        }
    }
}
