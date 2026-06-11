# CLAUDE.md — Kayıp Krallık · Claude Code Görev Tanımı

> Bu dosya Claude Code için yazıldı. Projeyi derleyecek, hataları düzeltecek ve
> aşağıdaki doğrulama listesini İŞLETEREK imzalayacaksın. İnsan (Mehmet) telefondan
> takip ediyor; raporların kısa ve Türkçe olsun.

## 1) Proje nedir

UE 5.7 / C++ — "Kayıp Krallık": 2.5D top-down hayatta kalma + kale inşası.
HTML prototipi "Kayıp Orman"ın birebir portu üstüne kale katmanı geliyor.
Tam tasarım: `Docs/kayip-krallik-plan.md` (v0.5, 185 özellik, 8 faz).
Sanat dili: `SANAT-YONU.md` — palet ve form kuralları BAĞLAYICIDIR.

**Mevcut durum (bu pakette):**
- **Faz 0 TAM**: prosedürel dünya (KO gürültü aritmetiği birebir), chunk akışı,
  gece/gündüz, hasat, açlık/enerji (GAS), gece gölgeleri, chiptune sentez sesler,
  kod-içi HUD, versiyonlu kayıt (v3), Enhanced Input (tamamen kodda).
- **Faz 1 BAŞLANGICI**: inşa modu (B), Duvar/Kapı yerleştirme + maliyet,
  kapı aç/kapat (E), yapı HP + gölgelerin duvar kemirmesi, **Balista** (oto-savunma),
  **Kalp Taşı** + Gün-10 zafer / yenilgi döngüsü, gece kuşatma dalgası, yapılar kayıtta kalıcı,
  **çok oyunculu yağma kuralı** (ölüm bitirmez; envanter katile — gölge hırsız + yağma kesesi),
  **save v4** (keseler kayıtlı), **Ayla** (ilk yurttaş), **Ürkek fauna** (tavşan+geyik, av baskısı
  ekolojisi) ve **5. gece büyük kuşatması**.

## 2) Görevin (sıra önemli)

1. **Motoru hazırla** (aşağıdaki A veya B yolu).
2. **Derle** → hata varsa düzelt → tekrar derle, 0 hata / 0 uyarıya in.
3. **Editörde tek manuel adım**: boş Level oluştur, `Content/` köküne `L_Dunya`
   adıyla kaydet (DefaultEngine.ini bu haritayı bekliyor). Komutla da olur:
   `UnrealEditor-Cmd <proje> -run=...` gerekmiyor — boş map yeterli; istersen
   editör Python'u ile: `unreal.EditorLevelLibrary.new_level('/Game/L_Dunya')`.
4. **PIE doğrulaması**: §5 listesini tek tek işaretle.
5. **Rapor**: değiştirdiğin her dosya için 1 satır gerekçe.

## 3) Motor kurulumu

### Yol A — Windows PC (önerilen, hızlı)
- Epic Games Launcher → UE **5.7** binary kur. Visual Studio 2022
  (Desktop C++ + Game development with C++ iş yükleri).
- `KayipKrallik.uproject` sağ tık → *Generate Visual Studio project files*.
- Derleme (CLI):
  ```
  "<UE>/Engine/Build/BatchFiles/Build.bat" KayipKrallikEditor Win64 Development -project="<tam_yol>/KayipKrallik.uproject" -waitmutex
  ```

### Yol B — Linux VM (kaynak derleme)
- **İNSAN ADIMI GEREKİR**: Epic hesabı ↔ GitHub bağlantısı ve EULA onayı olmadan
  `EpicGames/UnrealEngine` reposuna erişemezsin. Mehmet'ten erişimli token iste;
  yoksa bu yolu BLOKE olarak raporla, uydurma çözüm deneme.
- Sonrası: `Setup.sh` → `GenerateProjectFiles.sh` → `make UnrealEditor` (saatler sürer,
  ~200 GB disk). Proje: `GenerateProjectFiles.sh <uproject>` → `make KayipKrallikEditor`.
- Donanım gerçeği: 8+ çekirdek / 64 GB RAM altında kaynak derleme eziyettir
  (plan §6.8). Zayıf VM'de dene-yanıl yapma; Yol A'yı bekle.

## 4) Düzeltme yaparken uyacağın mimari kurallar

- **KO sabitlerine DOKUNMA**: `KKWorldGenSubsystem.cpp` içindeki hash/fbm
  çarpanları, eşikler (0.30/0.345/0.375, 0.06 kamp şansı…) ve
  `KKAudioSubsystem.cpp` sentez reçeteleri prototiple birebir paritedir.
  Derleme hatası bu satırlardaysa İFADEYİ düzelt, SAYIYI değiştirme.
- **Palet**: yeni renk gerekiyorsa `SANAT-YONU.md` listesinden seç.
- **Otorite disiplini**: oyun durumu yalnız sunucuda değişir
  (`HasAuthority()` / `NM_Client` kontrolleri kalıptır, kaldırma).
- **Kayıt**: şema değişikliği = `SaveVersion` artır + `MigrateToCurrent`
  zincirine `case` ekle. Eski kayıt ASLA kırılmaz.
- **Sinyal**: sistemler arası konuşma `KKMessageBusSubsystem` + `KKTags` ile;
  doğrudan çapraz referans ekleme.
- **Yorumlar Türkçe** kalır; yeni kod yazarsan aynı yoğunlukta yorumla.
- API tahmini yasak: emin olmadığın UE çağrısını motor başlıklarından doğrula.

## 5) PIE doğrulama listesi (hepsi ✓ olmadan "bitti" deme)

- [ ] Derleme: 0 hata (uyarıları da raporla)
- [ ] Oyun `L_Dunya`da açılıyor, kamp ateşi başında doğuyorsun
- [ ] Dünya: çim/koyu çim/kum/su/patika karoları + ağaç/kaya/çalı görünüyor
- [ ] WASD + fare/Space çalışıyor; suya girilemiyor (yumuşak itme)
- [ ] Ağaç kes → odun; kaya kır → taş; çalı E → böğürtlen; E ile ye → açlık artar
- [ ] Saat ilerliyor; gece karanlık + meşale ışığı; şafakta gölgeler ölüyor
- [ ] Gece gölge spawn oluyor, kovalıyor, dokununca can düşüyor; ölünce 4 sn sonra kampta doğuş
- [ ] **İnşa**: B → hayalet; geçersiz karoda kırmızı; E ile Duvar↔Kapı; maliyet düşüyor
- [ ] Kapı E ile açılıp kapanıyor; kapalıyken gölge geçemiyor
- [ ] Gölge duvara vuruyor (sarsıntı+ses), ~15 vuruşta duvar yıkılıyor
- [ ] Kamp doğusunda Kalp Taşı süzülüyor; gece ışığı parlıyor; sağ üstte KALP barı dolu
- [ ] Gece başında dalga kalbe yürüyor (2+Gün gölge); kalp vurulunca bar düşüyor
- [ ] Kalp 0 → kırmızı "KALP TAŞI DÜŞTÜ" bandı + çekirdek kararıp çöküyor (oyun kilitlenmez)
- [ ] Saati hızlandırıp Gün 10 → altın "KRALLIK AYAKTA" bandı (CycleSeconds'ı geçici 10 sn yapabilirsin)
- [ ] B modunda E iki kez → Balista hayaleti (bodur kule); yerleştir → boşta yavaş tarıyor
- [ ] Gece: balista en yakın gölgeye nişan alıp cıvata atıyor (vınlama sesi, 3 isabette ölüm)
- [ ] Gölgeler balistaya da vuruyor; 80 HP'de yıkılıyor — duvar arkası taktiği çalışıyor
- [ ] Envanterle gölgeye öl → 4 sn'de kampta doğ, hotbar SIFIR; katil gölgenin başında altın elmas
- [ ] O gölgeyi öldür (veya şafağı bekle) → altın bantlı kese; üstünden geç → eşyalar geri (pickup sesi)
- [ ] Aç kalarak öl → kese ölüm noktasında bekliyor
- [ ] (2 oyunculu PIE) Oyuncu oyuncuyu öldürünce envanter doğrudan katile geçiyor
- [ ] Kese varken kaydet-çık-gir: kese aynı yerde, içerik tam (save v4)
- [ ] Kamptan ~7 karo ötede ahşap kafes; E → "Ayla katıldı" altın toast + quest arpeji
- [ ] Ayla gündüz ağaç kesiyor (chop ritmi), omzunda kütükle yanına gelip teslim ediyor (pickup sesi)
- [ ] Gece Ayla kalbe koşup bekliyor; şafakta işe dönüyor
- [ ] Ayla'ya vurarak öldür → kırmızı toast, kese düşer; kaydet-gir → kafes YOK, Ayla YOK (kalıcı)
- [ ] Gündüz çevrede tavşan/geyik geziniyor; yaklaşınca kaçıyorlar (tavşan zıplayarak)
- [ ] Geyik avla → kese: 2 et + 1 post; tavşan → 1 et; E ile et ye → açlık +35, hotbar 4. slot işliyor
- [ ] 6-7 hayvan avla → çevrede hayvan görünür şekilde azalıyor; ertesi şafak toparlıyor
- [ ] Gece girince hayvanlar kayboluyor; 5. gece kırmızı "BÜYÜK KUŞATMA" bandı + dalga belirgin kalabalık
- [ ] Kaydet-çık-gir: gün/saat/konum/can/envanter/hasat edilenler/**yapılar** geri geliyor
- [ ] `stat unit` ile FPS notu (hedef: editörde rahat 60)

## 6) Bilinen dürüst notlar (sürpriz yaşama)

- Kod hiç derlenmedi (sandbox'ta UE yok) — ilk derlemede ufak include/imza
  düzeltmeleri NORMALDİR; mimariye dokunmadan gider.
- Hayalet önizleme OPAK (BasicShapeMaterial saydamlık desteklemez) —
  içerlek ölçek + nabız ile okunur; Faz 1 sonunda translucent malzeme varlığı gelecek.
- Dedicated sunucuda yapı YIKIM sesi istemcide çalmaz (aktör kanalı kapanıyor);
  listen-host akışında sorun yok. Faz 2 FX geçişinde çözülecek.
- `USoundWaveProcedural`: bazı platformlarda finite Duration kuyruğu erken
  kesebilir; cihazda sessiz SFX görürsen Duration'a +0.05 pay ver.

## 7) Dosya haritası (hızlı yön bulma)

```
Source/KayipKrallik/
  Core/    tag'ler · mesaj otobüsü · zaman/gün · kayıt (v3+migrasyon) · hasar arayüzü
  World/   dünya üretimi (KO birebir) · chunk akışı · kaynak düğümleri · kamp ateşi
           · inşa: KKBuildTypes / KKBuildable / KKBuildGridSubsystem · KKHeartStone
  Player/  karakter (GAS+inşa modu) · controller (kodda Enhanced Input) · GameMode/State
  Combat/  AttributeSet (Can/Enerji/Açlık)
  Items/   envanter (replike) · tarif satırı
  Enemy/   gölge (kovala+kuşat, hedef: oyuncu∪kalp) · spawner (dalga+damla)
  Audio/   chiptune sentez (KO reçeteleri birebir)
  UI/      kod-içi HUD (bar'lar · saat · hotbar · ipucu)
Config/    L_Dunya varsayılan harita · mobil hedefleme · Enhanced Input sınıfları
Docs/      kayip-krallik-plan.md (tam tasarım v0.5)
Data/      DT_Recipes.csv (editörde DataTable'a aktarılacak — Faz 1 devamı)
```
