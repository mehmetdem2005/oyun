# Blender → Sprite Hattı (3D model → render → boya)
Kanıtlanmış akış (sandbox içinde Blender 4.0.2 headless ile üretildi):
1. blender -b -noaudio --factory-startup -P bl_stonewall.py → şeffaf 540² render
2. Boya geçişi (PIL): karart 0.80 · kontrast 1.22 · soğuk gölge karışımı ·
   24 renge kuantala · unsharp(2,140) → build_stonewall.png
3. Dosyayı app/src/main/assets/sprites/ içine koy → oyun otomatik kullanır.
Yeni varlık = bu scripti kopyala, geometri/materyal bölümünü değiştir
(kapı: kemer + ahşap kanat; burç: dik gövde; balista: platform + kollar).
Işık/kamera/render blokları SABİT kalır — set tutarlılığı buradan gelir.

## v3.0 — bl_set.py (7 varlıklı set)
Tek koşu: palisat, kapı (kapalı/açık), burç, balista, çam, kaya.
Işık/kamera rigi stonewall ile birebir → set tutarlılığı garantili.
Varsa-atla korumalı: yarıda kesilirse aynı komutla kaldığı yerden sürer.
