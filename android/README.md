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

## v2.2 — Mobil ölçek + sprite hattı
- **Kamera**: HTML kadrajı varsayılan (kısa kenara ~10.5 karo). Mola menüsünden
  **Yakınlık: Yakın/Orta/Uzak** — kalıcı ayar.
- **UI ölçeği**: tüm arayüz yoğunluk-duyarlı büyür (420dp sanal tuval) —
  telefonda okunur barlar, büyük **SALDIR/KULLAN/İNŞA**, ikonlu 6'lı hotbar,
  sağ üstte **minimap** (yapılar/kalp/gece gölgeleri işaretli).
- **Yüzme**: yan profil **kurbağalama** — kol süpürmesi, makas tekme, köpük.
- **Sprite hattı**: `app/src/main/assets/sprites/` içine
  `docs/SPRITE-PROMPTS.md` şartnamesiyle PNG at → otomatik kullanılır,
  yoksa prosedürel çizim sürer.
- **v2.3**: Yapı + ağaç/kaya/çalı sprite kancaları (`build_*`, `tree_*`, `rock_*`, `bush`); prosedürel yapılar kara-fantezi sayfa kompozisyonuna çekildi (tuğla/mazgal/sancak/kemer). Stil-kilitli prompt eki: `docs/SPRITE-PROMPTS.md` v2 bölümü.
- **v2.4**: Blender→render→boya hattı kanıtlandı; ilk üretim varlığı build_stonewall.png assets klasöründe (tools/blender/).

## v3.0
- **Bina yükseltme**: İnşa modunda E → öndeki yapı karşılanabilir bir üst
  katmana çıkar (Palisat→Taş Duvar→Kale Taşı, hedefin tam maliyeti; can yeni
  tavana yenilenir). Karşılanamıyorsa eski davranış (söküm / tip döngüsü).
- **Ayarlar paneli** (Mola → Ayarlar): Yakınlık · Ses Aç/Kapa · Joystick boyu
  (Küçük/Orta/Büyük) · Butonlar Sağda/Solda (solak modu) · çevrim içi durum.
- **Çok oyunculu-lite**: Supabase REST üstünden küresel sohbet + dünya
  hayaletleri (yakındaki gerçek oyuncular yarı saydam görünür). Kurulum:
  1) Supabase projesinde tools/supabase/schema.sql çalıştır,
  2) app/src/main/assets/net.txt oluştur (1. satır URL, 2. satır anon key —
     örnek: tools/supabase/net.txt.example), 3) push → APK. Dosya yoksa oyun
  tamamen offline. NOT: senkron paylaşılan dünya DEĞİL — hayalet + sohbet.
- **Blender seti**: 7 yeni boyalı-3D varlık assets/sprites içinde
  (build_wall, build_gate, build_gate_open, build_keep, build_ballista,
  tree_1, rock_1) — hat: tools/blender/bl_set.py.

## v3.1 — Görsel revizyon (kullanıcı geri bildirimi)
- **3/4 izometrik kamera** (34°/31°, track-to): ön+yan+üst aynı karede → 3B hissi.
- **Pürüzsüz yüzeyler**: displace güçleri yarıdan aza indirildi, shade_smooth
  eklendi; ağaç/kaya/çalı artık temiz silüetli. Boya kuantalaması yumuşatıldı
  (24→28 renk, hafif keskinleştirme).
- **Tam set: 14 varlık** — yapılar (5+balista) + Kalp Taşı (20 yüzlü altın
  kristal) + tree_1/2/3 + rock_1/2/3 + bush. Hepsi assets/sprites içinde,
  kancalar hazır olduğundan oyun otomatik kullanır.

## v3.2 — Doğa revizyonu (zoom geri bildirimi)
- Kayalar: noise-bump tamamen kaldırıldı → FACETED geometri (güçlü displace +
  düzlem birleştirme + kenar bevel, düz gölgeleme) = kırık taş düzlemleri.
- Ağaç/çalı: yaprak materyalinden bump söküldü, renk aralığı daraltıldı →
  beneksiz temiz gradyan.
- Boya geçişi: 32 renk + hafif keskinlik (kuantalama beneği bitti).

## v4.0-alpha — Ekonomi çekirdeği tamam
- 4 yeni yapı: Ok Kulesi, Demirci, Simyahane, Diken Tuzağı (SPEC 5'er seviye).
- Kayıt artık seviyeleri koruyor (SaveStore alan-sayısı bağımsız okuma).
- B_MAPCOL: yapı-tipi renk paleti (harita pikselleri + sprite'sız fallback kutuları).
- Jeneratör kabukta "heart" kristali ile çiziliyor; ölü B_NEXT zinciri silindi.
- Duman: 71/71 (yeni: 63 kule, 64 tuzak). Yol haritası: docs/GOREV-PANOSU.md

## v4.1 — Tam harita ekranı
- HUD'da "M" butonu / mola menüsünde HARİTA: kalp merkezli 128×128 karo, ekran
  ortalı kare (yamuksuz). Karo önbelleği + satır-koşusu birleştirme (akıcı çizim).
- Yapılar B_MAPCOL pikselleriyle, kalp/oyuncu/minyon/gölge işaretli, altta lejant.
- Dokunuş veya Geri: kapanır. Ana menü zaten mevcuttu (M_MENU açılış).

## v4.1.1 — AAA harita cilası (geri bildirim turu)
- Pencere 128→80 karo: üs okunaklı kale ölçeğine geldi.
- Kaynak benekleri kaldırıldı (stratejik harita dili): ormanlar hafif koyu yama,
  kayalar haritada yok; çimende algı-eşiği altı ton varyasyonu.
- İşaret hiyerarşisi: surlar hücre-çizgi, binalar konturlu 2.2 karo blok,
  kalp ışıması ve oyuncu/minyon/gölge noktaları küçültülüp ayrıştırıldı.
- Kıyı çizgisi (karaya komşu su açık tonda), çift altın çerçeve + köşe
  işaretleri + pusula + lejant şeridi.

## v4.2 — Gerçek ölçek tepeden harita
- 1 karo = 4 m, bina = 1 karo (gerçek taban izi); pencere 64 karo = 256 m × 256 m.
- Su: karaya-uzaklık (chamfer) tabanlı 5 derinlik bandı + kıyı kabartması.
- Çimen: value-noise organik ton; ağaçlar ışıklı taç küreleri (orman kümeleri),
  çalılar küçük tutam, kayalar seyrek çakıl (R_TREE/R_ROCK/R_BUSH doğru eşlendi).
- Binalar minik ekstrüde blok (çatı ışığı + güney gölgesi), kalp yumuşak ışıma,
  vinyet + altın çift çerçeve + ölçek altyazısı.

## v4.3 — Slither ölçeğinde dünya + iki kademeli harita
- Dünya artık sınırlı: K.WORLD_R = 700 karo → çap 1.400 karo = 5,6 km, dairesel
  okyanus çeperi (slither.io referansı: yarıçap 21.600 birim ≈ 1.450 avatar çapı;
  bizim avatar 1 karo). 50 oyuncuya kişi başı ~31 bin karo alan.
- Harita iki kademe: Bölge (256 m gerçek ölçek, oyuncu merkezli, hareketle
  yenilenir) ↔ Dünya (tamamı, 280 örnekleme ızgarası) — alt ortadaki çiple geçiş.
- Çevrimiçi hayaletler iki görünümde de nokta olarak çizilir.
- Duman 75/75 (yeni 65a-d dünya sınırı testleri).

## v4.4 — Eşya sistemi + 5 yeni işlevli bina (katalog 21)
- Eşyalar: Sopa→Taş Kılıç→Altın Kılıç (+12/22/36 hasar, en iyisi otomatik),
  Kalkan/Taş Zırh (-%15/-%30 gölge hasarı), Balta/Kazma (odun/taş ×2),
  Can İksiri (×3 stok, can <%35 olunca akıllı otomatik içilir).
- Üretim akışı UI'sız: Demirci/Atölye/Simyahane yanında dur → 6 sn'de kaynağın
  yetiyorsa sıradaki eşya üretilir (toast). Eşyalar envanterde → kayıt değişmedi.
- Yeni binalar: Kereste Kampı (odun), Büyücü Kulesi (alan büyüsü), Şifahane
  (minyon iyileştirme), Zafer Heykeli (üretim ×1+0.04·lvl), Ambar (çiftlik +lvl).
- Duman 82/82 (66 üretim/kuşanma · 67 heykel+depo · 68 büyücü · 69 iksir).
