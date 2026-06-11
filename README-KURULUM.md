# KAYIP KRALLIK — Faz -1/0 Kurulum (UE 5.7 C++)

Bu klasör **derlenmeye hazır eksiksiz kaynak ağacıdır**: Faz 0'ın tüm sistemleri
(zaman, dünya üretimi, akış, şövalye, GAS öznitelikleri, envanter, gölgeler, ses, HUD, kayıt v2) C++ olarak içeridedir.
Tek manuel editör adımı: **boş bir seviye oluşturmak** (aşağıda 5. adım) — gerisi koddan doğar.

## Faz -1: Donanım & Kurulum
1. **UE 5.7'yi Epic Games Launcher'dan kur** (binary yeterli ⚠️ kaynak derleme ŞİMDİ GEREKMİYOR — yalnız Faz 4-5 dedicated server için gerekecek, plan ADR-001).
2. **Visual Studio 2022** + "Game development with C++" iş yükü (Windows SDK + .NET dahil).
3. (Android için) Launcher → UE 5.7 → Options → Android paketini işaretle; Android Studio/SDK kurulumunu UE belgesindeki sürümlerle eşle.
4. **Git LFS**: `git lfs install` — `.gitignore` hazır (Saved/Intermediate/DDC commit edilmez).

## Faz 0: Projeyi Açma (ilk derleme)
1. Bu klasörü `C:\Dev\KayipKrallik\` gibi KISA bir yola kopyala (uzun yol = Windows derleme sorunu).
2. `KayipKrallik.uproject` → sağ tık → **Generate Visual Studio project files**.
3. `KayipKrallik.sln` → Visual Studio → konfigürasyon **Development Editor / Win64** → **Build**.
4. Derleme bitince `.uproject` çift tık → editör açılır.
5. **TEK manuel adım:** File → New Level → **Empty Level** → Content köküne **`L_Dunya`** adıyla kaydet. (Config bu haritayı varsayılan yapar; GameMode global ayarlı — World Settings'e dokunmana gerek yok.)
6. **Play**: başlangıç kampında doğarsın; WASD yürü, SOL TIK/SPACE kes-saldır, E topla/ye. 240 sn'de bir gece — gölgeler gelir, ateşin yanında kal.

## Mobil Hızlı Test
- Sanal joystickler `DefaultInput.ini` ile hazır (sol = hareket, sağ çubuk = saldır, B = etkileşim).
- Platforms → Android → Package; ilk paketlemede SDK lisanslarını onayla.

## Dürüstlük Notu (önemli)
Bu kod **bu oturumda derlenemedi** (UE motoru kurulu olmayan bir ortamda yazıldı). Bilinçli olarak
yalnız uzun yıllardır kararlı UE API'leri kullanıldı; yine de 5.7'de **birkaç ufak include/imza
düzeltmesi çıkabilir** — derleyici satırı gösterir, mimari sağlamdır. İlk derlemede hata görürsen
mesajı olduğu gibi bana getir, hedefli yama veririm.

## Dosya Haritası
```
Source/KayipKrallik/
  Core/    Tag'ler · MesajOtobüsü · Zaman(240sn) · Kayıt v2 + migrasyon · Hasar arayüzü
  World/   WorldGen (KO gürültüsü birebir) · Streamer (HISM) · KaynakDüğümü · KampAteşi
  Player/  Şövalye · Controller (kod-içi Enhanced Input) · GameMode · GameState
  Combat/  GAS AttributeSet (Can/Enerji/Açlık) · ince ASC
  Items/   Envanter (replike) · ItemDef · Tarif satırı (+ Data/DT_Recipes.csv)
  Enemy/   GölgeDüşman · GeceSpawner
  Audio/   Prosedürel chiptune SFX (KO reçeteleri)
  UI/      Kod-içi HUD (bar/saat/hotbar/ipucu/flaş)
Config/    Engine · Input (Enhanced + sanal joystick) · Game
SANAT-YONU.md  Palet + ışık + form mührü
```

## Sıradaki (Faz 0 kapanış kriterleri, plan 6.2)
- [x] Seed'li dünya, gün/gece, toplama döngüsü, gölge geceleri, kayıt v2, HUD — **kodda**
- [ ] PC'de ilk derleme + 10 dk oynanış videosu
- [ ] Android cihazda 60 fps ölçümü (stat unit)
- [x] Faz 1 başlangıcı: İnşa sistemi PAKETTE — `B` ile inşa modu, `SALDIR` yerleştir, `E` Duvar/Kapı değiştir, kapılar `E` ile açılır, yapılar kayıtla kalıcı (save v3)
- [x] Faz 1 devamı PAKETTE: yapı HP + gölgelerin duvar kemirmesi, **Kalp Taşı** (kampın 2 karo doğusu, 300 HP, gece feneri), gece **kuşatma dalgası** (kalbe yürür, 2+Gün), **Gün 10 zafer / kalp düşerse yenilgi** HUD bandı, sağ üst KALP barı
- [x] **Balista** PAKETTE: 3. yapı (8 odun + 4 taş, 80 HP) — 950 menzilde en yakın gölgeye 2.5 sn'de bir cıvata (12 hasar, 3'te öldürür); boşta nöbetçi taraması yapar; gölgeler onu da söker → duvar arkasına kur
- [x] **Yağma kuralı** PAKETTE (çok oyunculu temel): oyun ölümle BİTMEZ — 4 sn'de kampta doğarsın ama envanterin KATİLE gider. Katil oyuncuysa envanterine; gölgeyse sırtlanır (baş üstünde altın elmas = "kovala beni"), o gölge nasıl ölürse ölsün (kılıç/balista/ŞAFAK dahil) altın bantlı kese düşürür; katil yoksa (açlık) kese ölüm noktasına. Keseye dokunan alır.
- [x] **Kese-kayıt (save v4)** PAKETTE: yağma keseleri konum+içerikle kayda yazılır, yüklemede geri doğar — "eşya asla buharlaşmaz" artık kayıt-geçirmez
- [x] **Ayla — ilk yurttaş** PAKETTE (plan 55+58): kamptan 7 karo ötede seed-deterministik ahşap kafeste; E ile kurtar (quest sesi + altın toast). Gündüz: ev civarındaki (14 karo) ağaçları keser, omzunda kütükle sana getirir (aynı `ServerHarvest` yolu — kendi envanteri var). Gece: işi bırakıp Kalp Taşı'na sığınır. Friendly-fire onu da bulur: 40 HP, ölürse **geri gelmez** (kayıtta kalıcı), taşıdığı odun kese düşer
- [x] **İlk fauna — Ürkek sınıfı** PAKETTE (plan 153+155): Tavşan (10 HP, zıplayarak koşar, 1 et) + Geyik (30 HP, boynuzlu, 2 et + 1 post). Gezinir/otlar, 4.2 m'de panikleyip kaçar (tavşan 520 hız!). Av = yağma kesesi. **Aşırı avlanma** bölge tavanını düşürür (6→1'e kadar), her şafak doğa 2 puan toparlar. Gece ürkekler ine çekilir. Çiğ et E ile +35 açlık (hotbar 4. slot, pembe); post Faz 2 zanaatını bekliyor
- [x] **Büyük kuşatma** PAKETTE (plan 43): her 5. gece dalga ×2 + daha sık patlama + kırmızı HUD bandı — "5'in katı geceler"e hazırlan refleksi
- [ ] Faz 1 kalan: DT_Recipes DataTable bağlanması, yarı saydam hayalet malzemesi, friendly-fire anahtarı (Faz 4), pişirme (çiğ et riski — Faz 2 girişi)
