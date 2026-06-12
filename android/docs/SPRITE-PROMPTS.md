# SPRITE ÜRETİM PROMPTLARI — Kayıp Krallık v2.2

Bu doküman, **senin yüksek çözünürlüklü üreteceğin** sprite'ların tam şartnamesi.
Motor hazır: `app/src/main/assets/sprites/` içine doğru adla PNG at → oyun
**otomatik kullanır**; dosya yoksa prosedürel (HTML-sadık) çizim devam eder.
Yani yarım set bile atsan oyun kırılmaz.

---

## 1) Motor tarafı — nasıl tüketiliyor

- Dosya adı şeması: **`<anahtar>_f<KARE>.png`** → yatay şerit.
  Örnek: `player_walk_f6.png` = 6 kare yan yana (6×256 = 1536×256 px).
- Her kare **kare** (256×256 önerilir; 512 de olur, APK büyür).
- Karakter **alt-orta hizalı**: ayak/taban alt kenardan ~10 px yukarıda,
  yatayda ortalanmış. Motor tabanı (x,y)'ye oturtur, genişliği dünya
  ölçüsüne kendisi ölçekler.
- Varsayılan bakış **SAĞA** (sola dönüşü motor aynalar).
- **Gölge çizme** — oyun zemin elipsini kendisi çizer.
- **Kafes çizme** (Ayla) — kafes çubuklarını oyun üstüne çizer.
- Şerit üretemeyen araçta: kareleri **tek tek** üret (`player_walk_1.png`…),
  bana yükle → PIL ile şeride dizip repo'ya koyarım.

| Anahtar | Kare | Oyun fps | Görünüm |
|---|---|---|---|
| player_idle | 4 | 6 | ¾ önden |
| player_walk | 6 | 10 | ¾ önden |
| player_attack | 4 | vuruş süresine bağlı | ¾ önden |
| player_swim | 6 | 8 | **YAN profil** |
| ayla_idle / ayla_walk / ayla_carry | 4 / 6 / 4 | 5 / 10 / 10 | ¾ önden |
| ayla_caged | 1 | — | ¾ önden |
| shadow_float | 6 | 8 | önden |
| wolf_walk / wolf_run | 6 / 6 | 6 / 10 | YAN |
| deer_idle / deer_run | 6 / 6 | 6 / 10 | YAN |
| boar_walk / boar_charge | 6 / 4 | 6 / 10 | YAN |
| rabbit_idle / rabbit_hop | 2 / 4 | 6 / 10 | YAN |
| heart_pulse | 6 | 6 | önden (nesne) |

---

## 2) Ortak şartname — HER promptun başına yapıştır

```
chunky pixel-art game sprite, crisp hard square pixels (32x32 logic rendered
at 256x256), flat colors, no anti-aliasing, transparent background (PNG alpha),
full body visible, feet anchored near bottom edge with 10px margin, centered
horizontally, consistent character scale across all frames
```

**Ortak NEGATİF prompt:**
```
no background, no ground plane, no drop shadow, no glow, no soft gradient,
no outline stroke, no text, no watermark, no blur, not 3D render, no isometric
```

**Palet (sapma yok):** gömlek `#3e7fd0` · ten `#f2c08c` · saç `#503217` ·
pantolon `#27405e` · Ayla gömleği `#d76fa3` · gölge mürekkebi `#221440` ·
gölge tacı `#3a2766` · altın `#e8b73d` · altın parıltı `#fff1a8` ·
kurt `#7e8694` · geyik `#8a5a32` · domuz `#5c4230` · tavşan `#a8b0bd`

**Tutarlılık taktiği:** Önce 1 adet "referans kare" üret (player_idle 1. kare).
Sonraki tüm karelerde aynı seed + karakter referansı (img2img / character
reference) kullan. Kareler arası ölçek kaymasın — en sık bozulan şey bu.

---

## 3) OYUNCU (insan, mavi gömlek)

### player_idle_f4.png — nefes döngüsü
**Prompt:** `[ortak] + young villager hero, blue shirt #3e7fd0, dark brown hair
#503217, tan skin #f2c08c, navy pants #27405e, 3/4 top-down RPG view facing
camera (south), relaxed standing pose, idle breathing animation frame`
**Kareler:** 1 nötr · 2 göğüs+omuz 1px yukarı · 3 nötr · 4 göğüs 1px aşağı + göz kırpma.

### player_walk_f6.png — yürüyüş döngüsü
**Prompt:** `[ortak] + same hero, walking cycle facing camera, arms counter-swing`
**Kareler:** 1 sol ayak temas · 2 çökme (gövde 2px aşağı) · 3 geçiş (bacaklar
dik, gövde 1px yukarı) · 4 sağ ayak temas · 5 çökme · 6 geçiş.

### player_attack_f4.png — SOPA savurma
**Prompt:** `[ortak] + same hero swinging a short wooden club (brown handle
#7a4e2a, round dark knob), melee attack animation, 3/4 front view`
**Kareler:** 1 kurulum (sopa omuz arkasında, gövde hafif döner) · 2 savuruş
ortası (sopa yatay, hareket yayı hissi) · 3 darbe (sopa ön-alt tam uzanım,
gövde öne) · 4 toparlanma.
Motor kareyi vuruş süresine (0.18 sn) kendisi eşler.

### player_swim_f6.png — KURBAĞALAMA, **yan profil** ⭐
**Prompt:** `[ortak] + same hero swimming breaststroke, STRICT SIDE VIEW facing
right, body horizontal, head above water at front, lower body slightly
submerged suggested by a thin white foam line crossing the torso, small splash
at feet, do NOT paint water area`
**Kareler:** 1 süzülme (kollar ileride bitişik, bacaklar düz) · 2 kollar dışa
açılmaya başlar · 3 kollar geniş süpürme + bacaklar kurbağa gibi katlanıp açılır ·
4 güç tekmesi: bacaklar çırpıp birleşir, ayakta sıçrama, kollar göğüs altına
toplanır · 5 kollar ileri uzanır · 6 tam süzülme + kafa önünde burun dalgası.

---

## 4) AYLA (köylü, pembe gömlek)

### ayla_idle_f4.png
**Prompt:** `[ortak] + villager woman, pink shirt #d76fa3, dark hair #3a2614,
3/4 front view, gentle idle sway`
**Kareler:** nefes + saç hafif salınım (oyuncu idle ile aynı mantık).

### ayla_walk_f6.png — oyuncu yürüyüşüyle aynı 6 kare düzeni.

### ayla_carry_f4.png — omuzda kütük
**Prompt:** `[ayla] + walking while carrying a small brown log on right
shoulder, left arm balancing`
**Kareler:** 4 karelik kısa yürüyüş döngüsü, kütük omuzda sabit.

### ayla_caged_f1.png — tek kare
**Prompt:** `[ayla] + standing sad, hands clasped in front, head slightly down`
**NOT:** Kafes ÇİZME — çubukları oyun üstüne bindiriyor.

---

## 5) GÖLGE (gece düşmanı)

### shadow_float_f6.png — wob döngüsü
**Prompt:** `[ortak] + ghostly ink-blob creature, deep purple-black body
#221440, faint purple crown arc #3a2766 on top, two vertical white eye slits,
wispy smoky tendrils at bottom, floating menacing spirit, front view`
**Kareler:** sıkış-gerin salınımı: 1 uzun-ince · 2 normal · 3 basık-geniş ·
4 normal · 5 uzun-ince (tüyler sola savrulur) · 6 normal (tüyler sağa).
Alt kenarda tüy uçları HAFİF değişsin — hayalet hissi buradan gelir.

---

## 6) FAUNA (hepsi YAN görünüm, sağa bakar)

### wolf_walk_f6.png / wolf_run_f6.png
**Prompt:** `[ortak] + grey wolf #7e8694 side view facing right, darker legs
#4a5158, bushy tail` — run: `+ aggressive gallop, ears back, glowing red eye`
**Walk kareleri:** 4 zamanlı bacak döngüsü (çapraz bacaklar), kuyruk salınır.
**Run kareleri:** dörtnal — 1 toplanma · 2 ileri fırlama (gövde uzar) ·
3 havada uzanım · 4 ön ayak iniş · 5 arka toplanır · 6 itiş.

### deer_idle_f6.png (otlama) / deer_run_f6.png
**Prompt:** `[ortak] + brown deer #8a5a32 side view facing right, small pale
antlers #e6d28a, white tail dot`
**Idle:** kafa 1-2-3'te yere iner (otlar), 4-5-6'da kalkar + kulak seğirmesi.
**Run:** kurt dörtnalı ile aynı 6-kare şablon, boyun ileri uzanır.

### boar_walk_f6.png / boar_charge_f4.png
**Prompt:** `[ortak] + wild boar, dark brown body #5c4230, bristly mohawk
ridge on back, pale snout, two small white tusks, side view facing right`
— charge: `+ head lowered, tusks forward, furious sprint, small dust puffs
behind hooves`
**Charge kareleri:** 1 kafa iner + kazma duruşu · 2 fırlama · 3 tam sprint
(toz) · 4 ikinci adım (toz).

### rabbit_idle_f2.png / rabbit_hop_f4.png
**Prompt:** `[ortak] + small grey-blue rabbit #a8b0bd, long ears, pale tail
#fff1a8, side view facing right`
**Idle:** 1 oturuş · 2 kulak seğirir. **Hop:** 1 çömelme · 2 havada uzanım ·
3 iniş · 4 toparlanma.

---

## 7) KALP TAŞI (nesne)

### heart_pulse_f6.png
**Prompt:** `[ortak] + sacred shrine object: two-tier grey stone pedestal
(#7e8694 base, #a8b0bd top) at bottom, a golden diamond crystal #e8b73d with
bright core #fff1a8 floating above it rotated 45 degrees, front view`
**Kareler:** elmas 1→3'te 4px yükselir, 4→6'da iner; 3. karede çapraz parıltı
(✦) patlaması. Kaide tüm karelerde SABİT.

---

## 8) Üretim akışı (önerilen)

1. `player_idle` 1. kareyi üret → bana göster → palet/oran onayı.
2. Onaydan sonra aynı referansla setleri sırayla üret:
   öncelik **player (4 set) → shadow → wolf → boar → heart**, kalanlar sonra.
3. Kareleri tek tek attıysan ben şeride dizerim; şerit attıysan adlandır:
   `<anahtar>_f<N>.png` → `app/src/main/assets/sprites/` → push →
   Actions yeşilse Releases'tan yeni APK.
4. Telefonda dene → ekran görüntüsü at → ölçek/fps ince ayarını koddan yaparım.

---
---

# v2 EKİ — STİL KİLİDİ: SENİN SAYFAN (kara-fantezi, boyalı)

Ürettiğin sayfa yeni **görsel kanon**. Bölüm 2'deki "chunky pixel-art" şartnamesi
bu stil için **İPTAL** — aşağıdaki blok geçerli. Kare sayıları, dosya adları ve
kare-kare poz dökümleri (Bölüm 3-7) AYNEN geçerli; sadece stil bloğu değişti.

## A) Sayfan neden doğrudan oyuna giremiyor (4 şart)

1. **Her varlık AYRI dosya** olmalı — tek kompozit sayfa kesilemez (sen de
   kesme dedin; doğrusu da bu).
2. **ŞEFFAF arka plan** — sayfandaki koyu fon oyunda kutu gibi görünür.
   PNG alpha şart.
3. **Eşit kare ızgarası** — şeritte her kare aynı genişlikte; karakter her
   karede AYNI ölçekte ve AYNI taban hizasında (sayfanda kareler kayıyor).
4. **Etiket/yazı/gölge yok** — adlar dosya adında, gölgeyi oyun çizer.

## B) Yeni ortak stil bloğu (her promptun başına)

```
hand-painted dark fantasy game asset, AoE2-style detailed miniature,
painterly shading with strong silhouette, weathered steel and stone,
royal blue cloth #2c52a0 with gold trim #d6aa4c heraldry, subtle rim light,
single asset only, transparent background (PNG alpha), no text, no shadow,
no ground plane, consistent scale and baseline across all frames
```
Negatif blok aynı (Bölüm 2) + `no labels, no sprite sheet grid, single subject`.

## C) Sayfandaki adlar → oyunun beklediği dosyalar

| Senin sayfan | Oyun dosyası | Not |
|---|---|---|
| WALL_SEGMENT | `build_stonewall.png` | tek kare, mazgallı taş duvar + mavi sancak |
| GATE | `build_gate.png` + `build_gate_open.png` | kapalı/açık iki dosya |
| WATCH_TOWER / ARCHER_TOWER | `build_keep.png` | burç (tek kule yeter) |
| BALLISTA_TOWER | `build_ballista.png` | cıvata/şarjörü oyun üstüne çizer |
| — (yeni) | `build_wall.png` | ahşap palisat: sivri kütükler + demir kuşak |
| TREES_AND_ROCKS | `tree_1/2/3.png`, `rock_1/2/3.png`, `bush.png` | her biri AYRI dosya |
| SOLDIER/ARCHER/SPEARMAN/CAVALRY/ELEPHANT | **üretme** | oyunda karşılığı yok — boşa emek; ordu mekaniği istersen önce onu konuşalım |

Karakterler (player/ayla/shadow/kurt/geyik/domuz/tavşan/kalp): Bölüm 3-7'deki
kare dökümleri geçerli, stil bloğu B ile. Oyuncu bu stilde:
`young kingdom hero, royal blue tunic with gold trim, leather bracers, short
wooden club` — zırhlı asker DEĞİL (oyunun kahramanı köylü-kahraman).

## D) Önerilen ilk adım (10 dk)

1. `build_stonewall.png` üret (tek kare, şeffaf, B bloğuyla).
2. Bana at ya da `app/src/main/assets/sprites/` içine koyup push'la.
3. Releases'tan APK → duvarı oyunda gör → onaydan sonra seri üretim:
   **build seti → player 4 set → shadow → fauna → heart**.
