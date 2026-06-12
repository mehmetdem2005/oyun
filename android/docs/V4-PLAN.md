# KAYIP KRALLIK v4.0 — "İŞLEVSEL KRALLIK" PLANI

## 1. ÇEKİRDEK DÖNGÜ (tasarım)
Jeneratör altın üretir → altınla yapı kur/YÜKSELT (5 seviye) → yapılar işlev
verir (altın, minyon, savunma, destek) → gece dalgalarına karşı krallık büyür
→ çok oyunculu katmanda oda + sohbet + ticaret isteği.

## 2. YAPI KAYIT DEFTERİ (5 seviye, kategori, işlev)
Eğriler: yükseltme maliyeti ×~1.7 · can ×~1.8 · üretim ×~1.45

| Yapı | Kat. | İşlev (L1 → L5) | Yer maliyeti | Yük. (L2..L5 altın) |
|---|---|---|---|---|
| Jeneratör (merkez, varsayılan) | EKO | 0.5 → 2.2 altın/sn | — (otomatik) | 120·205·350·595 |
| Altın Madeni | EKO | 0.35 → 1.5 altın/sn | 8 odun 6 taş | 90·155·265·450 |
| Çiftlik | EKO | 12 sn'de 1 → 5 yemek | 6 odun | 60·100·170·290 |
| Depo | EKO | +250 → +1250 altın tavanı | 8 odun 4 taş | 70·120·205·350 |
| Ahşap Palisat | SAV | 100 → 1050 can | 4 odun | 40·70·120·205 |
| Taş Duvar | SAV | 400 → 4200 can | 8 taş | 80·135·230·390 |
| Kale Taşı | SAV | 1200 → 12600 can | 16 taş 6 odun | 150·255·435·740 |
| Kapı | SAV | 60 → 630 can | 6 odun 1 taş | 40·70·120·205 |
| Balista Kulesi | SAV | 8 → 20 hasar, 230 → 310 menzil | 8 odun 4 taş | 100·170·290·495 |
| Kışla | ORDU | 1 → 5 minyon kapasitesi | 10 odun 6 taş | 110·185·315·535 |
| Tapınak | DSTK | yakında +1 → +5 can/sn | 8 taş 4 odun | 90·155·265·450 |
| Atölye | DSTK | yükseltmelere %-4 → %-20 | 10 odun 8 taş | 120·205·350·595 |
| Pazar | DSTK | ticaret oranı + istek butonu | 8 odun 8 taş | 100·170·290·495 |

Minyon: can 30+10·L, hasar 5+2·L, kışla başına L adet, 6 sn'de bir doğar,
en yakın Gölge'yi kovalar. Toplam tavan 12 (mobil performans).

## 3. TASK PANOSU
- [T1] ✅ Bu plan dosyası
- [T2] Çekirdek: SPEC kayıt defteri + altın + Build.lvl + jeneratör otomatik yerleşim
- [T3] Çekirdek: ekonomi tiki (altın/yemek üretimi, depo tavanı, tapınak aurası)
- [T4] Çekirdek: minyon varlığı + kışla doğumu + Gölge'ye saldırı YZ
- [T5] Çekirdek: E-yükseltme → seviye sistemi (altınla, atölye indirimi)
- [T6] Smoke: 60 yeniden (seviye) + 61 altın tiki + 62 minyon tavanı → 70+/70+
- [T7] Kabuk: kafa-üstü UI (isim + can barı + sohbet baloncuğu; hayaletlerde de)
- [T8] Kabuk: sol sohbet paneli — 2 sekme (Global/Oda), ok ile aç-kapa
- [T9] Kabuk: tam ekran harita (ortalanmış kare, yamulmasız, yapı-renk pikselleri)
- [T10] Kabuk: yeni yapı çizimleri (kategori-renkli prosedürel + seviye rozetleri)
- [T11] Kabuk: menü akışı (direkt oyun YOK) + Çok Oyunculu ekranı
- [T12] Çok oyunculu: oda sistemi — Hızlı/Odalar sekmeleri, oda kur (kapasite,
       herkese açık/kodlu özel), doluluk 3/50, rastgele konum dağıtma
- [T13] Net.kt v2: kk_rooms tablosu + oda-bazlı sohbet + doluluk sayımı
- [T14] Ticaret İSTEĞİ: hızlı mesajdan [TRADE] etiketi + baloncukta gösterim
- [T15] Çoklu dil: TR/EN sözlük + Ayarlar'da dil seçimi (UI metinleri)
- [T16] schema.sql v2 + README v4
- [T17] ⏳ SONRAKİ OTURUM: 13 yapı × 5 seviye Blender sprite seti (65 render —
       şimdilik seviye rozetleri + renk kademesi; hat hazır: bl_set.py)
- [T18] ⏳ SONRAKİ OTURUM: gerçek ticaret takası (istek/kabul protokolü +
       envanter transferi) ve senkron paylaşılan dünya (sunucu otoritesi ister)

## 4. DÜRÜST KAPSAM NOTLARI
- Odalar = sohbet + hayalet GÖRÜŞ alanı + rastgele dağıtılmış konum. Her
  oyuncunun dünyası kendi tohumundan üretildiği için arazi senkron DEĞİL;
  senkron dünya T18 (sunucu otoritesi gerektirir, REST polling yetmez).
- Özel oda kodu 4 haneli, ekrandaki sayı tuş takımıyla girilir (metin girişi yok).
- 5 seviyenin GÖRSELİ bu sürümde: ölçek + seviye rozetleri + L3 gümüş / L5
  altın çerçeve. Seviye başına ayrı Blender sprite'ı T17.
