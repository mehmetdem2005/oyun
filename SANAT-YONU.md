# KAYIP KRALLIK — Sanat Yönü (Faz 0 Mührü)

> Tek cümle: **"Yumuşatılmamış bloklar + tek renk yüzeyler = minyatür diorama; gece korkutur, ateş ısıtır."**
> Kayıp Orman'ın piksel sahnesi, 3B'de aynı ruhla yaşar. Her renk aşağıdaki tablodan gelir — göz kararı renk YASAK.

## 1) Palet (Kayıp Orman birebir)
| Kullanım | HEX |
|---|---|
| Derin su | `#1d568f` |
| Su | `#2e7cc4` |
| Kum | `#e6d28a` |
| Çim | `#4fae60` |
| Koyu çim (orman) | `#3d8c4e` |
| Patika | `#b08a58` |
| Kamp zemini | `#c8a06d` |
| Ağaç gövdesi | `#6e4a2a` |
| Çam yeşilleri | `#1f7a44` `#2a955a` `#14532e` `#1b6b3c` |
| Yuvarlak yaprak | `#2f9e4f` `#23753a` `#49bd66` |
| Kaya | `#7e8694` `#a8b0bd` (yosun `#4f9b58`) |
| Çalı | `#2c7a3d` `#3c9b51` · Böğürtlen `#e23d4f` |
| Oyuncu tuniği | `#3e7fd0` · Ten `#f2c08c` · Çelik `#cfd6e0` |
| Gölge düşman | `#221440` (vuruş moru `#6a4fb8`, göz `#f2f0ff`) |
| Ateş | `#ff8a23` `#ffc23d` `#fff1a8` · Meşale ışığı `#ffb45e` |
| HUD: CAN `#e23d4f` · ENERJİ `#e8b73d` · AÇLIK `#e07b2f` · Vurgu `#ffd76a` |
| Gece sis/karanlık | `#070a1e` |

## 2) Kamera & Form
- **2.1 Kamera:** SpringArm mutlak rotasyon, pitch **-55°**, kol **1400uu**, FOV **50** (sıkışık perspektif → izometrik his), lag 9.
- **2.2 Form dili:** Yalnız motor temel şekilleri (Cube/Sphere/Cone/Cylinder). Kenar yumuşatma yok. Detay = renk bloğu, doku DEĞİL.
- **2.3 Ölçek:** 1 karo = 100uu. Ağaç ~2.6 karo boy, oyuncu ~1.8 karo. Hafif deterministik yaw/ölçek sapması (hash) = organiklik.

## 3) Işık & Atmosfer (TimeOfDay eğrileri)
- **3.1 Güneş:** Gündüz `(1.0,0.95,0.84)` × 5.5 lüks · Gece `(0.22,0.30,0.58)` × 0.35 · Alacakaranlıkta `(1.0,0.52,0.28)` karışımı. Pitch -58° → -16°. **Dinamik gölge KAPALI** (mobil bütçe).
- **3.2 Sis:** Gündüz `(0.74,0.81,0.90)` yoğunluk 0.012 → Gece `#070a1e` yoğunluk 0.028. Gece sisi tehdidin ta kendisi.
- **3.3 Nokta ışıklar:** Yalnız ateş (#ffb45e, 820uu, 4200cd, çift-sinüs titreme) ve oyuncu meşalesi (620uu, karanlık×3800). Ekranda ≤ 12 ışık.
- **3.4 Post:** Vignette 0.35 · Bloom 0.55 · FilmGrain 0.12 · Saturation 1.06 · AutoExposure KAPALI (sahne pozu sabit, gece gerçekten karanlık).

## 4) Hareket Dili
- Vuruş = sarsıntı (0.28s sinüs), kılıç savuruşu 0.18s (-80°→+80°), gölge ölümü = büzülme (savaşta 0.6s, şafakta törensel 1.2s), ateş = çift-sinüs nabız.
- Kural: her oyuncu eylemi 0.3 sn içinde görsel + işitsel yankı bulur (KO "tık hissi").

## 5) Ses Kimliği
- Chiptune sentez (32kHz, kare/üçgen/testere + LP gürültü). Reçeteler `KKAudioSubsystem.cpp` tablosunda — KO SFX nesnesi birebir.
- Mix kuralı: Combat ≤6, World ≤6, UI ≤4 eşzamanlı; taşan atılır.

## 6) Yasaklar
- Gradient, gerçekçi doku, lens flare, motion blur YOK. Palet dışı renk YOK. "Şimdilik gri küp" yerleştirip sonra boyamak YOK — şekil + palet birlikte gelir.
