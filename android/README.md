# Kayıp Krallık — Saf Kotlin Android Native

Motor yok. Harici kütüphane yok. XML layout yok. **Yalnız Android SDK + Kotlin**:
SurfaceView üstünde tek render ipliği, Canvas çizimi, AudioTrack ses sentezi,
SharedPreferences kayıt. 10 gece süren Kalp Taşı kuşatması — tamamı elle yazılmış.

## Mimari (Hexagonal / Ports & Adapters)

```
app/src/main/java/com/mk/kayipkrallik/
├── core/        SAF KOTLIN — Android'i hiç görmez
│   ├── World.kt   sabitler (K), deterministik RNG, dünya üretimi (hash/fbm)
│   ├── State.kt   varlıklar, GameState, Snapshot (kayıt veri modeli)
│   └── Game.kt    TÜM kural: oyuncu/gölge/fauna/Ayla/yapı/dalga/saat
└── android/     KABUK — yalnız çizer, çalar, dokunuşu iletir
    ├── GameView.kt   SurfaceView + iplik + chunk önbelleği + gece ışığı + HUD + dokunmatik
    ├── Sound.kt      AudioTrack STATIC — 19 efekt dosyasız sentezlenir
    ├── SaveStore.kt  Snapshot ↔ JSON (org.json) ↔ SharedPreferences
    └── MainActivity.kt
```

Köprü sözleşmesi: kabuk her karede `s.sfx / s.toasts / s.bigText / s.flash /
s.shakeTiles` kuyruklarını çekip TEMİZLER; girdi yalnız `setDir / tapAttack /
tapInteract / tapBuild` üzerinden akar. Çekirdek tek başına derlenir ve test edilir.

## Oynanış
Kalp Taşı (300 HP) kampta. Geceleri gölgeler **kalbe ve sana** yürür: dalga
2+gün, **her 5. gece ×2 BÜYÜK kuşatma**, yol kesen yapıyı kemirirler. 5 yapı
(Palisat→Taş→Kale→Kapı→Balista), tamir (E+1 malzeme=%30), gece oto-kilit,
balista mühimmatı (8 başlar, E+odun=+5, tavan 20). Ölünce kaynaklar düşer —
son vuran gölge ganimeti **sırtlanır**, avlarsan geri alırsın. Kafesteki
Ayla'yı kurtar: gündüz odun taşır, gece kalbe sığınır. Fauna: tavşan/geyik
ürkek, **kurt kışkırtılır**. 10. şafak = ZAFER.

## Kontroller (dokunmatik)
Sol yarı: dinamik joystick · **VUR** (basılı tut = seri) · **E** etkileşim
(konuş/topla/kapı/cıvata/tamir/ye) · **B** inşa modu (modda E=tip, VUR=yerleştir)
· sağ-üst **II** mola. Geri tuşu: mola ↔ devam.

## v2'de gelenler
Modüler `:core`/`:app` Gradle yapısı · **YÜZME** (su yalnız oyuncuya açık; kulaç
animasyonu + halkalar) · **SOPA** (envanterden üret, 3 odun, 32 hasar) · **YABAN
DOMUZU** (yaralanınca şarj eder) · irkilme + zikzak kaçış · **SÖKÜM** (%50 iade) ·
**SOHBET** (hızlı mesaj + Ayla yanıtı; global chat portu hazır) · **ŞAFAK KARNESİ** ·
yeni görsel dil: kapsül insansılar, telli gölgeler, rünlü kalp, su parıltısı,
parçacıklar · dikey-öncelik (yatay da açık) · sohbet/envanter/inşa panelleri ·
`docs/` altında ARCHITECTURE · STANDARDS · GDD · TEST-STRATEGY · ROADMAP · ART-DIRECTION.

## Telefondan APK (PC gerekmez)

APK'yı **GitHub Actions** derler (`.github/workflows/android.yml` hazır):

**Yol A — Termux (önerilen):**
```
pkg install git
cd KayipKrallikNative
git init && git add -A && git commit -m "ilk"
# GitHub'da boş repo aç (web), sonra:
git remote add origin https://github.com/KULLANICI/kayip-krallik.git
git push -u origin main        # şifre yerine Personal Access Token
```
**Yol B — GitHub web:** yeni repo → *Add file → Upload files* ile bu klasördeki
dosyaları yükle (mobil tarayıcıda klasörler tek tek açılır, zahmetli ama olur).

Sonra **en kolayı:** repo → **Releases** → en üstteki *build N* → `app-debug.apk`
indir → dokun → "bilinmeyen kaynak" iznini ver → kur. (Actions → Artifacts da çalışır.)
Her push'ta CI önce 59 senaryoluk duman testini koşar; **geçmezse APK çıkmaz.** Cihazda yerel
derleme (Termux+Gradle) desteklenmiyor — dürüst sınır: AAPT2 telefonda koşmaz.

## Doğrulama — ne fiilen test edildi, ne edilmedi

| Katman | Durum |
|---|---|
| core | **kotlinc ile derlendi + 59/59 duman senaryosu KOŞTU** (yerleşim, karo-katılık, tamir, cıvata, oto-kilit, kemirme, kalp hasarı, balista atışı, şafak töreni, yağma sırtlanma+geri alma, kurt aggro, Ayla kurtar+çalış+sığın, Snapshot tur dönüşü, 5. gece ×2, Gün 10 zaferi) |
| android kabuk | **tools/android-stub.jar'a karşı tam tip denetiminden geçti** (sıfır hata) — stub yalnız imza, davranış içermez |
| piksel çıktısı, gerçek dokunuş, AudioTrack sesi, Gradle/AGP | yalnız **CI + cihazda** doğrulanır — ilk koşuda sorun olursa logcat/ekran görüntüsü at |

Duman testini kendin koşmak için (JDK + kotlinc olan herhangi bir yerde):
```
kotlinc app/src/main/java/com/mk/kayipkrallik/core/*.kt tools/Smoke.kt -include-runtime -d smoke.jar
java -jar smoke.jar
```

## Bilinen kapsam (v2 listesi)
Minimap, yağmur ve üç-NPC görev zinciri web sürümünde var, native v1'de bilinçli
dışarıda — çekirdek krallık döngüsü (kuşatma/inşa/yağma/Ayla/fauna/zafer) tam.
