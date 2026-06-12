# KAYIP KRALLIK — v4 GÖREV PANOSU
Ekonomi eğrileri: docs/V4-PLAN.md · Bu pano: durum + kalan işler + protokoller.

## DURUM — ÇEKİRDEKTE BİTTİ (71/71 duman testi)
- [x] 5 seviye sistemi: Build.lvl, SPEC kayıt defteri (can ×1.8 / üretim ×1.45 / maliyet ×1.7)
- [x] Varsayılan ALTIN JENERATÖRÜ (oyun başında oyuncu yanına, 25 altın başlangıç)
- [x] Üretim: Jeneratör+Maden→altın (Depo kapasite, Atölye %indirim), Çiftlik→meyve,
      Tapınak→can aurası, KIŞLA→MİNYON (seviye=birlik tavanı, maks 12)
- [x] upgradeBuild: E ile, altınla, atölye indirimli, maks L5; çift-E söküm koruması
- [x] YENİ 4 YAPI: Ok Kulesi (otomatik atış, menzil 96+18·lvl), Demirci (minyon
      hasarı +3·lvl), Simyahane (can aurası), Diken Tuzağı (temas hasarı, 3 sn bekleme)
- [x] Kayıt: lvl alanı uçtan uca (Snapshot 7 alan + SaveStore alan-sayısı bağımsız okuma)
- [x] B_MAPCOL: 16 yapıya özel harita/piksel renkleri (kabuk fallback kutuları da kullanıyor)
- [x] Ölü B_NEXT tip-zinciri silindi

## F1 — ÇEKİRDEK KALANLAR
- [x] Katalog 21 tipe çıktı — yeni 5: Kereste Kampı (odun üretimi), Büyücü Kulesi
      (4 sn'de alan büyüsü), Şifahane (minyon iyileştirme aurası), Zafer Heykeli
      (küresel üretim +%4·lvl), Ambar (çiftlik verimi +lvl)
- [x] EŞYA SİSTEMİ: 8 eşya (Sopa/Taş Kılıç/Altın Kılıç · Kalkan/Taş Zırh ·
      Balta/Kazma ×2 toplama · Can İksiri — akıllı otomatik içilir <%35 can).
      Üretim: Demirci/Atölye/Simyahane yanında dur → 6 sn'de sıradaki üretilir;
      en iyi silah/zırh otomatik kuşanılır; envanterde yaşar → kayıt bedava
- [ ] Kalan adaylar: Gözcü Feneri, Mancınık, Liman, Sur Kapısı L2+ görselleri
- [ ] Ticaret v1 (protokol aşağıda) — çekirdekte tradeOffer/tradeAccept fonksiyonları
- [ ] Minyonların kayda yazılması (şu an kışla yeniden üretiyor — bilinçli eksik)

## F2 — KABUK UI
- [ ] Kafa üstü: isim + CAN BARI; hızlı mesaj BALONCUĞU (5 sn)
- [ ] Hızlı mesaj paneline "Ticaret?" düğmesi
- [ ] Sol kenar SOHBET ÇEKMECESİ: ok ile kayar, 2 sekme (Global | Oda)
- [x] M_MAP iki kademe: BÖLGE (oyuncu merkezli 64 karo = 256 m, gerçek ölçek,
      hareketle yenilenir) ↔ DÜNYA (1.400 karo = 5,6 km, 280 örnekleme) — geçiş çipi,
      çevrimiçi hayalet noktaları iki görünümde, HUD "M" + mola menüsü
- [x] ANA MENÜ: zaten vardı (M_MENU başlangıç modu; Yeni Dünya + Devam Et) —
      Çok Oyunculu sekmesi F3'te eklenecek
- [ ] Seviye görselleri: L1-L5 sprite varyantı (bl_set.py parametrik: sancak sayısı,
      altın şerit, yükseklik) — 5 seviye × ana yapılar
- [ ] Yeni 4 yapının Blender sprite'ları (şimdilik tip-renkli kutu fallback)

## F1-EK — DÜNYA SINIRI [x]
- [x] K.WORLD_R = 700 karo (çap 5,6 km ≈ slither.io: yarıçap 21.600 birim, dairesel sınır)
- [x] tileAt: sınır ötesi okyanus + 48 karoluk kıyı alçalması (doğal çeper)
- [x] Duman 65a-d: sınır/çeper/iç-bölge/kamp testleri (75/75)

## F3 — ÇOK OYUNCULU LOBİ (Supabase)
- [ ] kk_rooms tablosu:
      id uuid pk · name text · cap int (varsayılan 50) · pub bool · owner text · created timestamptz
- [ ] kk_presence'a room uuid alanı; oda-içi sohbet = kk_chat.room süzgeci
- [ ] Lobi UI: 2 sekme — Rastgele Eşleş | Odalar (kur: ad+kapasite+özel/açık;
      listede doluluk "3/50")
- [ ] Rastgele eşleşme: pub=true ve doluluk<cap olan ilk oda, yoksa otomatik kur

## F4 — ÇOKLU DİL + TİCARET v2
- [ ] Lang.kt: TR/EN tablo (anahtar→metin), Ayarlar'da dil seçimi, tüm UI metinleri tablodan
- [ ] Ticaret v2: eşya↔eşya takası, onay penceresi, 30 sn zaman aşımı

## TİCARET v1 PROTOKOLÜ (chat kanalı üstünden)
- İstek:  `[TRADE?]`                       → karşıda baloncuk "Ticaret?"
- Teklif: `[TRADE:gold:50:wood:20]`        → "50 altın ↔ 20 odun"
- Kabul:  `[TRADE-OK:<teklifId>]` · Red: `[TRADE-NO]`
- Uygulama: Net.sendChat taşır; çekirdek tradeApply(env) envanter takasını yapar.
