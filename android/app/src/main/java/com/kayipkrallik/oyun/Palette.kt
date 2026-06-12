// Palette.kt — SANAT-YONU.md'deki HEX paleti BİREBİR. Palet dışı renk YASAK.
package com.kayipkrallik.oyun

import android.graphics.Color

object Pal {
    val DEEP = Color.parseColor("#1d568f")
    val WATER = Color.parseColor("#2e7cc4")
    val SAND = Color.parseColor("#e6d28a")
    val GRASS = Color.parseColor("#4fae60")
    val DARK = Color.parseColor("#3d8c4e")
    val PATH = Color.parseColor("#b08a58")
    val CAMP = Color.parseColor("#c8a06d")
    val TRUNK = Color.parseColor("#6e4a2a")
    val PINE = intArrayOf(Color.parseColor("#1f7a44"), Color.parseColor("#2a955a"),
        Color.parseColor("#14532e"), Color.parseColor("#1b6b3c"))
    val LEAF = intArrayOf(Color.parseColor("#2f9e4f"), Color.parseColor("#23753a"), Color.parseColor("#49bd66"))
    val ROCK = Color.parseColor("#7e8694")
    val ROCK2 = Color.parseColor("#a8b0bd")
    val MOSS = Color.parseColor("#4f9b58")
    val BUSH = Color.parseColor("#2c7a3d")
    val BERRY = Color.parseColor("#e23d4f")
    val TUNIC = Color.parseColor("#3e7fd0")
    val SKIN = Color.parseColor("#f2c08c")
    val STEEL = Color.parseColor("#cfd6e0")
    val SHADOW = Color.parseColor("#221440")
    val SHADOW_HIT = Color.parseColor("#6a4fb8")
    val SHADOW_EYE = Color.parseColor("#f2f0ff")
    val FIRE1 = Color.parseColor("#ff8a23")
    val FIRE2 = Color.parseColor("#ffc23d")
    val FIRE3 = Color.parseColor("#fff1a8")
    val TORCH = Color.parseColor("#ffb45e")
    val HP = Color.parseColor("#e23d4f")
    val ENERGY = Color.parseColor("#e8b73d")
    val HUNGER = Color.parseColor("#e07b2f")
    val ACCENT = Color.parseColor("#ffd76a")
    val NIGHT = Color.parseColor("#070a1e")
    val GOLD = Color.parseColor("#ffd76a")

    // Gece karanlığı: zemini #070a1e'ye doğru karart (Darkness 0..1).
    fun darken(c: Int, d: Float): Int {
        if (d <= 0f) return c
        val r = (Color.red(c) * (1 - d) + Color.red(NIGHT) * d).toInt()
        val g = (Color.green(c) * (1 - d) + Color.green(NIGHT) * d).toInt()
        val b = (Color.blue(c) * (1 - d) + Color.blue(NIGHT) * d).toInt()
        return Color.rgb(r, g, b)
    }
}
