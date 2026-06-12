# Kod Standartları — Kayıp Krallık
*Kotlin resmî stil kılavuzu temel; aşağıdakiler projeye özgü zorunluluklardır.*

## Dil ve Biçim
- 4 boşluk; satır ≤ 110; fonksiyon hedefi ≤ ~45 satır (çizim fonksiyonları hariç, onlar tür başına bölünür).
- Adlandırma: sabitler `K` nesnesinde SCREAMING_CASE; alanlar kısa ama Türkçe yorumlu.
- **Her dosya ve her blok Türkçe yorum taşır** — niyet, sayı seçimi, sözleşme.

## Oyun-Özel Kurallar (konsol stüdyo pratikleri)
1. **Sıcak döngüde tahsis yasak:** `update`/çizim içinde `new`, boxing, string birleştirme yok
   (toast metinleri olay anında bir kez kurulur). Parçacıklar havuzdan döner.
2. **Tek otorite:** durum yalnız `Game.update` içinde değişir; çizim katmanı SALT OKUR.
3. **Sabitler sözleşmedir:** dengeler (`B_HP`, hızlar, menziller) yalnız `K`'da yaşar;
   kod içine gömülü sihirli sayı = inceleme reddi.
4. **Kuyruk drenajı:** kabuk kuyrukları okuyup temizlemekle yükümlüdür; sızıntı = bellek hatası.
5. **Determinizm:** rastgelelik yalnız `s.rng` (LCG, seed'li); `Math.random` SADECE çizim süslerinde.
6. **Girdi kapıları:** kabuk çekirdeğe yalnız `tap*/setDir/quickChat/craftClub` ile dokunur;
   alan ezme (örn. `buildSel`) yalnız belgelenmiş UI istisnalarında.

## Hata ve Kayıt
- `SaveStore.load` bozuk veride `null` döner — sessiz çökme yok, yeni oyun teklif edilir.
- Snapshot sürümlüdür (`v`); eski sürüm okunur, yeni alan varsayılanla doldurulur.

## Kalite Kapısı
- `gradle :core:smoke` (59 senaryo) geçmeden APK üretilmez — CI bunu zorlar.
- Yeni mekanik = en az 1 smoke maddesi; düzeltilen hata = onu yakalayan madde.
