# KAYIP KRALLIK
## Unreal Engine 5.7 Oyun Tasarım & Mimari Planı — v0.5
*(Kayıp Orman'ın evrimi: şövalye · kale inşa · gece kuşatması · ordu komutası · 50 oyunculu diyar · Anadolu bestiyarisi)*

> **ADR-001 — Motor Kararı (v0.5):** Godot 4.6 → **Unreal Engine 5.7** (12 Kasım 2025 kararlı sürüm). Gerekçe: ürün sahibi kararı + PC temini taahhüdü; UE'nin 3D görsel tavanı, Fab pazarı, MetaSounds ve kariyer değeri. Bilinen bedeller (kabul edildi): donanım gereksinimi, APK boyutu, C++/BP öğrenme eğrisi, sunucu kaynak derlemesi → riskler tablosuna işlendi. Sonuçlar: görsel stil 2.5D'ye evrildi (6.0), **Faz -1 (Donanım & Pipeline)** eklendi, faz süreleri +%30-50 revize. UE6 duyuruldu ancak tarihsiz — v1 UE 5.7'de biter, UE6 değerlendirmesi v2 konusudur.

---

## 1. VİZYON

**Tek cümle:** Gündüz krallığının kalıntılarını keşfedip kaynak topladığın, kalen taş taş yükselttiğin; gece ise gölge ordularının kuşatmasına taretler, botlar ve kılıcınla karşı koyduğun, mobil öncelikli 2D açık dünya hayatta kalma oyunu.

| | |
|---|---|
| **Tür** | Açık dünya hayatta kalma × kale inşa × gece kuşatması × RTS ordu komutası |
| **Çok oyunculu** | Krallık kipi: 1–4 co-op (telefon host) · **Diyar kipi: 50 oyuncuya kadar** (adanmış sunucu) · Faz 6'da async PvP |
| **Harita** | Diyar: 8192×8192 karo (~67M karo) tek parça dev dünya — slither.io ölçeğinde gezinme alanı, tımar sistemiyle dağılım |
| **Platform** | Android öncelikli + PC/Steam eş zamanlı (UE 5.7); mobil 60 FPS anayasası korunur, masaüstü Lumen alır |
| **Perspektif** | Top-down **2.5D**: 3D dünya + sabit kuş bakışı kamera, stilize el boyaması görünüm (piksel ruhu palet ve oranlarla korunur — bkz. 6.0) |
| **Oturum** | 5–15 dk gün döngüleri (mobil cep oturumu) |
| **Referanslar** | Kayıp Orman çekirdeği + Kingdom Two Crowns (gece ritmi) + Necesse (inşa) + They Are Billions (kuşatma gerilimi) |

---

## 2. ÇEKİRDEK DÖNGÜ

```
ŞAFAK ──► Kader Kartı seç (günlük modifikatör)
  │
GÜNDÜZ ──► Keşfet · Topla · İnşa et · Görev yap · Köylü yönet
  │
ALACAKARANLIK ──► Hazırlık: taret mühimmatı, duvar tamiri, gözcü raporu
  │
GECE ──► KUŞATMA: dalgalar Kalp Taşı'na saldırır
  │
ŞAFAK ──► Ganimet · Kayıp analizi · Otomatik kayıt ──► başa dön
```

Kaybetme durumu: **Kalp Taşı** yok edilirse kale düşer → prestij sistemi devreye girer (kaybetmek bile meta ilerleme sağlar).

---

## 3. TASARIM SÜTUNLARI

1. **"Gündüz senin, gece onların."** Her sistem bu ritme hizmet eder; gündüz özgürlük, gece gerilim.
2. **"Her duvar bir karar."** İnşa estetik değil taktiktir — düşman AI zayıf noktayı bulur.
3. **"Kaybetmek bile ilerletir."** Kale düşse de Kral Mührü + kart koleksiyonu kalıcıdır.
4. **"Tek elle, telefonda, stabil 60 FPS."** Mobil performans bir özellik değil, anayasadır.

---

## 4. KAYIP ORMAN'DAN KORUNANLAR (sevdiğin çekirdek aynen evrilir)

| Kayıp Orman (HTML) | Kayıp Krallık (UE 5.7) |
|---|---|
| Chunk tabanlı sonsuz dünya | **World Partition** streaming + PCG ile prosedürel doldurma |
| Gün/gece döngüsü + karanlık | Kuşatma ritminin kalbi; dönen Directional Light + Sky Atmosphere (mobilde sade profil) |
| Gölge düşmanları | Kuşatma ordusunun temel piyadesi olarak geri döner |
| Ağaç/taş/meyve toplama | Tam üretim zincirlerinin ilk halkası |
| Kamp ateşi ışığı + güvenli bölge | Point light meşale ağı + moral sistemine evrilir |
| Ayla, Bora, Deniz | Köyün ilk üç kurtarılan sakini (görev zinciri başlangıcı) |
| Balta/kamp ateşi/barınak craft | 60+ tarifli üretim ağacına genişler |
| Görev sayaç sistemi (lifetime+base) | Aynı mimari; zincirli görevlere ölçeklenir |
| LocalStorage kayıt v1 | Versiyonlu + migrasyonlu **USaveGame** v2 |
| Sanal joystick + bağlamsal butonlar | Enhanced Input dokunmatik şema + düzen editörü (bkz. 80–87, 146) |

---

## 5. 185 ÖZELLİK

### A · Dünya & Keşif (1–10)
1. Sonsuz prosedürel dünya — thread'li chunk streaming, görünür alan + tampon üretimi
2. 6 biyom: Kayıp Orman, Karabataklık, Kayalık Yayla, Kül Ovası, Donmuş Sınır, Yıkık Krallık harabeleri
3. Biyoma özel kaynaklar: meşe→sert ahşap, granit, demir damarı, obsidyen, buz kristali
4. Paylaşılabilir dünya tohumu (seed) — aynı haritayı arkadaşına gönder
5. POI üretimi: yıkık kuleler, terk edilmiş madenler, eski sur kalıntıları (ganimet + lore)
6. Fog-of-war keşif haritası: gezilen bölgeler kalıcı açılır
7. Hava sistemi: yağmur/fırtına/sis/kar — okçu menzili, hareket hızı ve görüşe etki
8. Mevsim döngüsü (30 günlük): mahsul verimi ve düşman kompozisyonu değişir
9. Nehirler + köprü inşası: su, doğal savunma hattı olarak kullanılabilir
10. Dinamik dünya olayları: göçmen kervanı, meteor düşüşü (kristal), gezgin ozan

### B · Şövalye & Karakter (11–20)
11. Sınıf seçimi: Muhafız (savunma), Akıncı (hız/keşif), Mühendis (inşa+taret bonusu)
12. 6 ekipman slotu: kask, zırh, eldiven, çizme, kalkan, silah
13. Silah arketipleri: kılıç (dengeli), balta (kesim+ağaç bonusu), mızrak (menzil), savaş çekici (alan), yay, arbalet
14. Malzeme kademesi: bakır → demir → çelik → gümüş → ejder çeliği
15. Stamina dövüşü: hafif/ağır vuruş, blok, yuvarlanma — Kayıp Orman'ın tek tuş saldırısının derinleşmiş hali
16. Mükemmel parry penceresi: zamanlı blok → düşmanı sersemletir
17. 3 dallı yetenek ağacı: Savaş / İnşa / Liderlik (toplam 36 düğüm)
18. Unvan ilerlemesi: Silahtar → Şövalye → Sancak Beyi → Lord (her unvan kale limiti açar)
19. At bineği: hızlı keşif + mızraklı şarj saldırısı
20. Arma editörü: kendi sancağını tasarla (renk + sembol), kalende dalgalanır

### C · Kale İnşa (21–32)
21. Grid tabanlı inşa modu: hayalet önizleme, snap, döndürme, alan boyama
22. Duvar kademeleri: ahşap palisat → taş duvar → kale taşı (HP: 100/400/1200)
23. Duvar hasar görselleştirme (çatlak aşamaları) + çekiçle tamir mekaniği
24. Kale kapısı: manuel aç/kapa + "gece otomatik kilitle" kuralı
25. Kule tipleri: okçu kulesi, gözcü kulesi (haritada görüş+istihbarat), mancınık platformu
26. Pasif savunmalar: hendek, dikenli çukur, sur üstü kaynar yağ döküşü
27. Blueprint sistemi: kale planını şablon olarak kaydet, yeni oyunda tek tuşla diz
28. Üretim binaları: demirci, marangoz, fırın, simyahane, revir
29. Depo ağı: sandıklar tek envanter gibi davranır (menzil içinde otomatik çekme)
30. Çiftlik zinciri: tarla + kuyu + ağıl (tavuk/inek) → sürdürülebilir yiyecek
31. **Kalp Taşı**: kalenin çekirdeği; seviyesi inşa limitini belirler, düşerse kuşatma kaybedilir
32. Dekorasyon + moral: bayrak, meşale, heykel → köylü verimine küçük bonus

### D · Taretler & Botlar (33–42)
33. Balista tareti: tek hedef, zırh delici, uzun menzil
34. Mancınık tareti: alan hasarı, sur arkasından dolaylı atış
35. Yağ kazanı: kapı önü koni alan, yanma DoT
36. Ok yağmuru kulesi: çok sayıda zayıf hedefe karşı
37. Kristal tareti (geç oyun): zincir yıldırım, kristal cevheri ister
38. Mühimmat ekonomisi: taretler sonsuz değil — ok/taş/yağ üretip stoklaman gerekir
39. Hedefleme modları: en yakın / en güçlü / kuşatma aracı önceliği (taret başına ayarlanır)
40. **Tamirci bot**: gece hasar alan duvarları otomatik yamar (malzeme stoğundan)
41. **Hamal bot**: yerdeki kaynakları ve üretim çıktısını depoya taşır
42. **Nöbetçi şövalye botları**: waypoint ile devriye rotası çiz, gece otomatik savaşır

### E · Kuşatma & Savaş (43–54)
43. Gece dalgaları: her gece küçük baskın, her 5. gece büyük kuşatma
44. İstihbarat: gözcü kulesi varsa yarınki dalga kompozisyonu önceden görünür
45. 8 düşman tipi: gölge piyade, zırhlı ork, koçbaşı ekibi, merdivenci, sapancı, şaman (buff), gargoyle (duvar aşar), bombacı
46. Kuşatma araçları: koçbaşı (kapı), kuşatma kulesi (sur), düşman mancınığı (uzaktan döver)
47. Boss geceleri: Gölge Lordu (10. gece), Taş Devi (20.), Kül Ejderi (30.)
48. Akıllı kuşatma AI: ordu, surun en zayıf segmentini maliyet analiziyle seçer
49. Moral mekaniği: dalga lideri ölürse zayıf birimler kaçar
50. Dalga sonu ganimet sandığı + kelle başı altın
51. Savaş alanı kalıntısı: sabah cesetlerden ok/metal geri kazanımı
52. Yaralanma: %20 altı canla debuff; revir binasında tedavi
53. Aktif liderlik becerileri: Savaş Narası (AoE buff), Kalkan Duvarı, Alev Yağmuru (cooldown'lu)
54. Dinamik zorluk: dalga gücü kale değerine + oyuncu performansına ölçeklenir

### F · NPC, Köy & Görevler (55–64)
55. Kurtarılabilir köylüler: harabe ve kafeslerde bulunur, kaleye katılır
56. Meslek atama: oduncu, madenci, çiftçi, okçu, demirci çırağı (otomasyon katmanı)
57. Köylü ihtiyaç sistemi: yatak + yemek + güvenlik → mutluluk → üretim verimi
58. İsimli karakterler + sadakat puanı (Ayla, Bora, Deniz ilk üç köylü)
59. Görev mimarisi: ana hikaye zinciri (Yıkık Krallık'ın sırrı) + günlük yan görevler
60. Gezgin tüccar: nadir mallar, arz-talebe göre dalgalı fiyat
61. Kervan eskort görevleri: hareketli savunma mini senaryosu
62. AI lordlar: komşu kaleler — ittifak, haraç veya yağma diplomasisi
63. Tavern: altınla geçici paralı asker kirala
64. Seçimli diyalog: cevaplar sadakati ve görev dallarını etkiler

### G · Ekonomi & Üretim (65–72)
65. Üretim zincirleri: buğday→un→ekmek · cevher→külçe→silah · keten→kumaş→zırh astarı
66. Bina üretim kuyruğu: sıraya iş ekle, süre/maliyet görünür
67. Çift para: Altın (oyun içi) + Kral Mührü (prestij/meta para)
68. Pazar binası: "X kaynağı Y üstündeyse sat" otomasyon kuralları
69. Gün sonu raporu: üretim/tüketim/kayıp özeti tek ekranda
70. Kaynak yenilenme dengesi: maden damarı tükenir, orman zamanla geri büyür (KO'daki respawn'ın evrimi)
71. Simya: can, stamina, gece görüşü, ateş direnci iksirleri
72. Mutfak: tarifli yemekler kalıcı olmayan buff verir (meyve sisteminin evrimi)

### H · Kader Kartları & Meta (73–79)
73. **Kader Kartları (tarot destesi)**: her şafak 3 karttan 1'ini seç — o günün modifikatörü ("Kule: taret hasarı +%20, ahşap maliyeti +%10")
74. Lanet kartları: bilerek zorlaştır → gece ödülü 2×
75. Kart koleksiyonu + deste kurma: run'lar arası kalıcı meta ilerleme
76. Prestij döngüsü: kale düştüğünde Kral Mührü kazan → kalıcı bonus ağacında harca
77. 60+ başarım (gizliler dahil)
78. Günlük meydan okuma: herkes aynı seed, yerel skor tablosu
79. İstatistik sayfası: kesilen ağaç, püskürtülen dalga, tamir edilen duvar metrekareleri…

### I · Mobil UX & Kontrol (80–87)
80. Sanal joystick + bağlama duyarlı aksiyon butonu (KO şemasının geliştirilmişi)
81. İnşa kamerası: pinch-zoom, iki parmak pan, grid snap, uzun bas = yık
82. Nişan affı: mobil otomatik hedef yapışması (yakın+ön koni)
83. Tek el "seyir modu": rota çiz, şövalye otomatik toplayarak yürüsün
84. Haptik geri bildirim: vuruş, duvar çöküşü, kart çekme
85. UI ölçeği + solak düzeni ayarı
86. 30/60 FPS seçici + pil dostu mod (parçacık/ışık bütçesini düşürür)
87. SaveService v2: 3 slot, gün dönümü otokayıt, bulut yedeği (Faz 4)

### J · Görsel & Ses (88–93)
88. Dinamik ışık: point light meşale ağı + gün/gece directional light eğrisi (KO'nun ışık delme efektinin gerçek motoru); masaüstünde opsiyonel Lumen
89. Katmanlı dinamik müzik: kuşatma yoğunluğu arttıkça enstrüman eklenir
90. Niagara parçacıkları: duvar tozu, kıvılcım, yağmur, ateş közleri (mobil LOD'lu emitter profilleri)
91. Oyun hissi paketi: ekran sarsıntısı + hit-stop (CustomTimeDilation mikro duraklatma)
92. Gölge düşmanları için malzeme efekti: duman/distorsiyon kimliği (translucent material + Niagara)
93. Konumsal ses: spatialization + mesafe atenuasyonu — uzaktan gelen kuşatma uğultusu

### K · Teknik Altyapı (94–100)
94. Dünya akışı: World Partition streaming + PCG prosedürel doldurma + HISM (binlerce duvar/dekor tek draw call ailesinde)
95. Save v2: versiyonlu USaveGame şeması + migrasyon zinciri (v1→v2→…) — eski kayıt asla kırılmaz
96. Obje havuzlama: ok, Niagara emitter, düşman, hasar yazısı (mobilde spawn/GC baskısı sıfır)
97. AI LOD: ekran dışı düşman seyrek tick (4 Hz) — Mass LOD/significance yönetimi; ekranda tam simülasyon
98. Veri odaklı tasarım: tüm item/düşman/taret/kart tanımları **DataAsset + DataTable** — kod değişmeden denge yaması
99. Foto & tekrar modu: deterministik seed ile kuşatma tekrarını izle
100. Mod temeli: kullanıcı tanımlı kart/düşman/tarif DataTable/JSON yükleme

### L · Çok Oyunculu (101–112)
101. **Krallık kipi:** 1–4 oyuncu co-op, aynı krallık — UE listen server (host = otorite)
102. Sıfır harita senkronu: dünya seed'den deterministik — her istemci PCG ile araziyi kendi üretir (prosedürel mimarinin bedava MP kazancı)
103. Varlık replikasyonu: UE Actor replikasyonu + NetDormancy; ilgi yönetimi NetCullDistance / Replication Graph (oyuncuya ~2 ekran dışı varlıkların sync'i durur)
104. Rol bazlı co-op: biri surda savunurken diğeri orduyla karşı saldırıda (sınıf sistemiyle sinerji, 11)
105. Ortak inşa: eşzamanlı yerleştirme, grid rezervasyonu host'ta çözülür (çakışma yok)
106. Paylaşılan kale deposu + kişisel envanter ayrımı; ganimet paylaşım kuralları
107. Dalga gücü oyuncu sayısına ölçeklenir (54'ün MP uzantısı)
108. Yeniden bağlanma: düşen oyuncunun durumu host'ta bekler, kaldığı yerden döner
109. **EOS (Epic Online Services):** ücretsiz lobi + NAT punch/relay + oda koduyla katılım — kendi relay sunucusu işletmeye gerek yok
110. Ping çarkı iletişimi: Saldır / Savun / Yardım / Kaynak — mobilde yazışmasız koordinasyon
111. Async PvP baskını (Faz 6): çevrimdışı oyuncunun kale kopyasına saldır; savunmayı taretler + nöbetçi AI oynar
112. Güven modeli: hasar, envanter, üretim kararları yalnız otoritede (host/sunucu) doğrulanır; istemci girdi + görsel taşır

### M · Ordu & Komuta (113–124)
113. Asker üretim binaları: kışla (piyade), atış menzili (okçu), ahır (atlı), mühendishane (kuşatma ekibi)
114. 6 birim tipi: kılıçlı, mızraklı (anti-süvari), okçu, arbaletçi, atlı akıncı, kuşatma mühendisi (koçbaşı/merdiven taşır)
115. Nüfus tavanı: Kalp Taşı seviyesi + ev sayısı → ordu kapasitesi
116. Takip modu: ordun çevrende formasyonla dolanır (çember / kama / hat)
117. **İşaretle-Saldır:** görüş alanındaki düşman üssünü/yapısını işaretle → birlikler sen görmesen bile oraya yürür, savaşır, sonucu raporlar
118. Komut seti: Takip Et · Burayı Savun · Saldır (hedef) · Toplanma Noktasına Çekil
119. 3 ordu grubu (I/II/III): HUD rozetine dokun → hedefe dokun (mobil RTS şeması)
120. Ekran dışı savaş: komutalı birlikler kamera dışında da gerçek simülasyonda (LOD tick, deterministik çözüm — bkz. 6.5)
121. Savaş raporu bildirimi: uzaktaki çatışma bitince kayıp/kazanım/ganimet özeti
122. Birim bakımı: askerler yemek + maaş tüketir (çiftlik ve hazineyle döngüsel bağ, 65–70)
123. Kıdem: hayatta kalan birim XP kazanır — Acemi → Gazi → Muhafız (+stat)
124. Moral & ricat: ağır kayıpta birlik toplanma noktasına çekilir; liderlik becerileri (53) orduya da işler

### N · Ses Mimarisi (125–130)
125. 300+ ses varlığı kataloğu: malzeme × eylem matrisi (tam döküm 6.6'da)
126. Veri odaklı ses tanımı: olay başına varyasyon seti + pitch/volume aralığı **MetaSounds** rastgele düğümleriyle (3 kayıt 12 gibi duyulur)
127. Polifoni yönetimi: **Sound Concurrency** kuralları — kategori başına eşzamanlılık limiti + öncelik/voice-stealing motorun yerleşik özelliği (100 askerlik savaşta ses çorbası yok)
128. Etkileşimli müzik: MetaSounds + **Quartz** senkron saatiyle keşif → alacakaranlık → kuşatma → boss katman geçişleri
129. Ses sahneleri: Audio Volume ile iç mekân/mağara reverb submix'i; gece global lowpass tonu
130. Bark sistemi: asker/köylü kısa nidaları — komut onayı ("Emredersin!"), panik, zafer, selam; 60+ Türkçe varyasyon

### O · Dev Harita & Diyar (50 Oyuncu) (131–140)
131. **Diyar kipi:** tek dünyada 50 oyuncuya kadar — adanmış headless **UE dedicated server** (Linux VPS'te self-host)
132. Dev sınırlı harita: 8192×8192 karo ölçeğinde tek parça dünya — World Partition streaming taşır, sınır dışı "Sis Denizi"
133. **Tımar sistemi:** her oyuncuya otomatik 96×96 karo arazi tapusu; Kalp Taşı seviyesiyle genişler
134. Akıllı doğuş dağıtımı: Poisson-disk yerleşim, tımarlar arası min ~1.000 karo — **kimse kimsenin dibinde başlamaz**
135. Tımar koruması: PvE diyarında yabancı arazide inşa/yıkım/yağma kapalı; PvP isteyen ayrı şarda oynar
136. Dünya haritası UI: tımar sınırları, müttefik işaretleri, keşfedilen POI'ler, dünya olayı pinleri
137. Kervan yolları & hızlı seyahat: inşa edilen yol ağı hız bonusu + ahırlar arası at değişim noktaları (dev haritada ulaşım çözümü)
138. Bölgesel dünya olayları: "Kuzey sınırında Buz Golemi istilası!" — sunucu duyurusu, katılan herkese ödül havuzu
139. Şard tarayıcı: diyar listesi (doluluk, PvE/PvP, dünya yaşı) + arkadaşının diyarına kodla katıl
140. Çevrimdışı kalıcılık: sen yokken tımarın yaşamaya devam eder — taretler + nöbetçi botların savunur (40–42 ile uyum)

### P · Menü, Ayarlar & Kontroller (141–152)
141. Ana menü: Tek Oyunculu / Krallık / Diyar / Bestiyari / Ayarlar — canlı kale arka planı (gece kuşatması silüeti)
142. Ayarlar sekmeleri: Grafik · Ses · Kontroller · Oynanış · Erişilebilirlik · Dil (TR/EN)
143. Grafik: kalite ön ayarı (Düşük/Orta/Yüksek), FPS limiti (30/60), parçacık yoğunluğu, ışık sayısı, sarsıntı şiddeti
144. Ses: bus başına slider (Müzik/SFX/Ambiyans/UI/Bark) + ana ses + titreşim aç/kapa
145. Tuş yeniden atama: klavye/gamepad rebind, çakışma uyarısı, varsayılana dön (**Enhanced Input** runtime mapping)
146. **Dokunmatik düzen editörü:** her butonu/joystick'i sürükle-taşı, boyutlandır, opaklık ayarla — 3 kayıtlı profil
147. Oynanış ayarları: otomatik nişan şiddeti, kamera yumuşaklığı, hasar yazıları, otomatik toplama aç/kapa
148. Erişilebilirlik: renk körü paletleri, yazı boyutu, flaş/sarsıntı azaltma, bekletmeli tek-tuş modu
149. Kayıt yöneticisi: 3 slot kartı + bulut durumu + kopyala/sil (iki adımlı onay — KO geleneği)
150. Codex: öğreticiyi tekrar oynat, mekanik ansiklopedisi, Bestiyari sayfaları
151. Tam gamepad desteği: menü navigasyonu dahil (mobil controller + PC)
152. Canlı önizleme: ayarlar oyunu kapatmadan anında uygulanır (yan panel)

### Q · Bestiyari Sistemi (153–160)
153. **40+ yaratık türü, 6 davranış sınıfı:** Ürkek · Tarafsız · Kışkırtılır · Mistik · Yoldaş · Gece/Kuşatma — **hepsi düşman değil**
154. Canlı ekosistem: yaratıklar birbirini avlar (kurt geyiği kovalar, çakal leşe gelir); spawn biyom + saat + mevsime bağlı
155. Avcılık: et/post/boynuz/tüy düşer; aşırı avlanma bölge popülasyonunu düşürür, zaman toparlar (70 ile aynı denge)
156. Evcilleştirme: yem + sabır döngüsüyle 6 tür yoldaş olur (aşağıdaki tabloda)
157. Mistik karşılaşmalar: savaşsız ödül yolları — pazarlık, dilek, rehberlik (tablodaki Mistik sınıf)
158. Bestiyari codex'i: her tür ilk karşılaşmada açılır; davranış ipucu + Anadolu folklor notu
159. Efsanevi nadir spawnlar: Ak Geyik, Altın Sazan — Diyar'da sunucu duyurulu, herkes koşar
160. Yaratık AI mimarisi: tek ortak **StateTree** + `CreatureDef` DataAsset davranış profili — yeni tür eklemek = veri, kod değil (98 disiplini)

**BESTİYARİ — ilk 40 tür** *(Anadolu folkloru + yaban hayatı; ★ = mitolojik)*

| # | Yaratık | Sınıf | Nerede / Ne zaman | Davranış & oyun rolü |
|---|---|---|---|---|
| 1 | Geyik | Ürkek | Orman, gündüz | Kaçar; et + post; Ak Geyik'in sıradan akrabası |
| 2 | Tavşan | Ürkek | Çayır | Çok hızlı; tuzakla yakalanır, ufak et |
| 3 | Keklik | Ürkek | Yayla | Kısa uçar; tüy → ok üretim malzemesi |
| 4 | Dağ Keçisi | Ürkek | Kayalık Yayla | Kayalara tırmanır; peşinden giden düşme riski alır; süt+post |
| 5 | Yaban Ördeği | Ürkek | Nehir | Su kenarı avı; yumurta toplama |
| 6 | Kelaynak | Ürkek | Kül Ovası, nadir | Yuvası nadir ganimet; avlamak yerine korursan moral bonusu |
| 7 | Ateşböceği Sürüsü | Ürkek | Orman, gece | Kavanozla yakala → taşınabilir ışık (meşale alternatifi) |
| 8 | Yaban Domuzu | Kışkırtılır | Orman | Yaklaşana şarj eder; bol et; çukur tuzağıyla avlanır |
| 9 | Boz Ayı | Kışkırtılır | Mağara civarı | Bal stoğunu çalarsan kaleye dadanır; postu en iyi zırh astarı |
| 10 | Kurt Sürüsü | Kışkırtılır | Çayır, gece | Alfa ölürse sürü dağılır (moral sistemi 49); yavrusu evcilleşir → #25 |
| 11 | Engerek | Kışkırtılır | Bataklık | Zehir DoT; zehir bezi → panzehir ve zehirli ok |
| 12 | Dev Sazan | Tarafsız | Nehir derin | Olta mücadelesi mini-oyunu; Altın Sazan efsanevi varyant (159) |
| 13 | Arı Kovanı | Kışkırtılır | Orman | Vurursan sürü saldırır; meşale dumanıyla sakinleştir → bal |
| 14 | Yaban Atı | Tarafsız | Yayla | Evcilleştirilebilir binek (19); ürkütürsen sürü kaçar |
| 15 | Çakal | Tarafsız | Her yer, alacakaranlık | Leşçi: savaş sonrası ganimetini kapıp kaçar — sabah erken topla! |
| 16 | ★ Şahmeran | Mistik | Mağara derinliği | Yılanlar kraliçesi; saldırmaz — nadir malzeme takası + yarınki dalga kehaneti |
| 17 | ★ Peri | Mistik | Kutsal koru, şafak | Kaynak sun → rastgele kalıcı olmayan buff dileği |
| 18 | ★ Hızır Yolcusu | Mistik | Yollar, çok nadir | Aç gezgine yemek ver → gizli hazine haritası bırakır |
| 19 | ★ Ak Geyik | Mistik | Efsanevi | Kovalamadan takip edersen gizli vadiye götürür (Türk mitinin yol göstericisi) |
| 20 | ★ Erbüke | Mistik | Yıkık Krallık | Yarı insan yarı yılan bilge; simya tarifleri öğretir |
| 21 | ★ Yayla Dedesi | Mistik | Kayalık Yayla | Taş yığını canlanır; taş hediye et → gizli maden damarı gösterir; toprağına izinsiz inşa edersen küser (üretim -%10) |
| 22 | ★ Su Perisi | Mistik | Nehir, gece | Şarkısı stamina yeniler; balık hediye beklentisi vardır |
| 23 | ★ Kayra Kuşu | Mistik | Fırtına bulutu | Görene yıldırım düşmeden önce uyarı verir (hava sistemi 7) |
| 24 | ★ Nazar Böceği | Mistik | Gece, mavi parıltı | Yakalarsan 1 gece lanet kartı koruması (74 ile bağ) |
| 25 | Savaş Kurdu | Yoldaş | Evcil (kurt yavrusundan) | Yanında savaşır, komutlara uyar (118) |
| 26 | Şahin | Yoldaş | Evcil | Keşif uçuşu: işaretlediğin yöne uçar, haritayı açar (117 sinerjisi) |
| 27 | Baykuş | Yoldaş | Evcil, gece | Erken uyarı: düşman yaklaşınca öter — canlı gözcü kulesi |
| 28 | Kara Kedi | Yoldaş | Evcil | Kale maskotu; depodaki yiyecek bozulmasını yavaşlatır |
| 29 | Yük Domuzu | Yoldaş | Evcil | Seninle gezer, +20 slot taşır (hamal botun canlısı) |
| 30 | Keçi | Yoldaş | Evcil (ağıl) | Süt → peynir zinciri (65); her şeyi yer, çöp öğütücü |
| 31 | Gölge Piyade | Gece | Her gece | KO'nun gölgesi — kuşatma ordusunun eri (45) |
| 32 | ★ Karakoncolos | Gece/Kış | Kış geceleri | Kapıya vurur, tanıdık **ses taklidi** yapar — kapıyı elinle açarsan içeri dalar (folklor birebir!) |
| 33 | ★ Gulyabani | Gece | Bataklık | Sis içinde görünmez yaklaşır; meşale ışığı siluetini gösterir |
| 34 | ★ Hortlak | Gece | Harabe POI | Gündüz uyur; mezarını soyarsan uyanır — POI bekçisi |
| 35 | ★ Al Karısı | Gece | Revir | Korumasız revirdeki yaralılara musallat olur, iyileşmeyi durdurur; demir tılsım kovar |
| 36 | ★ Karabasan | Gece | Uyuyan köylüler | Gece nöbetçisi yoksa köylülere çöker → sabah verim düşer |
| 37 | ★ Tepegöz | Mini-boss | 15. gece | Tek gözü zayıf nokta — mükemmel parry (16) penceresiyle kör edilir |
| 38 | ★ Çarşamba Cadısı | Gece | Her 7. gece | O gecenin kader kartını laneter (74'ü zorla dayatır) — yüksek risk/ödül |
| 39 | Kül İfriti | Gece | Kül Ovası | Ölünce patlar — duvar dibinde öldürme, uzaktan vur |
| 40 | Buz Golemi | Gece | Donmuş Sınır | Yavaş ama duvar döver; ateş hasarına ×2 zayıf (yağ kazanı 35) |

### R · Araştırma Destekli İyileştirmeler (161–185) — v0.4

**Saha bulguları → tasarım cevabımız:**

| Bulgu (kaynak) | Cevabımız |
|---|---|
| Türün #1 şikayeti: toplama grind'i — "butona basılı tut, sayı artsın" zaman saygısızlığı sayılıyor (AV Club, oyuncu denemeleri) | 161 aktif toplama + 162 delegasyon eğrisi |
| "PvE'de üsler asla saldırılmıyor → duvarlar kozmetik, inşa içi boş" (tür eleştirisi; Palworld/7DtD istisna övgüsü) | Çekirdek döngümüz zaten panzehir; 184 ile garanti altına alındı |
| Bellwright (en yakın rakip, %79 olumlu): survival→köy→bölge geçişinin pürüzsüzlüğü en övülen yanı; Renown ile köylü kazanımı güçlü ama açıklanmıyor; erken oyun tedium + dar envanter en eleştirilen | 165–167 |
| Palworld (22M/ay): yaratıkların üste **çalışması** kanıtlanmış kanca — toplama otomasyonu + yaratık sevgisi tek sistemde | 168–169 |
| Whiteout Survival ($2,2 Mlr): net "sıradaki görev" göstergesi D1–7 tutundurmayı çözdü; battle pass/kozmetik ekonomisi loot box'a göre ~%25 daha iyi tutundurma; ittifak/sosyal özellikler D30–90'ın motoru; QoL otomasyonu burnout'u düşürüyor | 170–175 |
| Hibrit-casual 2026: D7 %16–22 benchmark (hyper-casual %6–9); basit çekirdek + meta katman formülü; EMEA %50 YoY büyüme — TR/MENA zamanlaması ideal | 176–177, 181 |
| UE 5.7 (Kas 2025): MetaSounds + Sound Concurrency yerleşik, Mass/StateTree olgun, IoStore/pak yamaları + Play Asset Delivery | 178–180 |
| Epic'in Lyra örnek projesi + Replication Graph: GAS, CommonUI ve büyük oyunculu replikasyonun resmi referansı | 183 |

161. **Aktif toplama:** kesme/kazma asla "basılı tut" değil — ritim halkası: doğru anda vur → kritik (+%50 verim, ekstra parçacık+ses). Toplamak bir beceri
162. **Delegasyon eğrisi:** ilkel işler ilerledikçe köylü/bot/yaratığa devredilir; oyuncu işçiden lorda evrilir — "tek kulübe → köy → bölge" geçişi tek pürüzsüz akış
163. Dünya ayar kaydırıcıları: dalga zorluğu, kaynak çarpanı, açlık hızı, gece süresi (hardcore ve rahat oyuncuyu aynı oyunda tut)
164. Envanter QoL paketi: depodan üretim (craft-from-storage), tek tuş "depoya boşalt", akıllı istifleme — mikroyönetim sıfır
165. **İtibar sistemi:** köylüler kafesten değil, görev+yardımla kazanılan İtibar puanıyla ikna edilir; kazanım her an ekranda şeffaf (Bellwright'ın belirsizlik hatasına düşme)
166. Büyük dava dakika 1'den: "Yıkık Krallığı yeniden kur" hedefi açılış sinematiğinde — hayatta kalmak yetmez, dava tutundurur
167. Ölçek geçişi pürüzsüzlük kuralı: kulübe→köy→bölge aşamalarında UI ve döngü kırılması yasak (tasarım QA maddesi)
168. **Yoldaşlar kalede çalışır:** Savaş Kurdu devriye atar, Keçi otu biçer, Şahin tımarlar arası posta taşır, Yük Domuzu hasat çeker — evcilleştirme (156) üretim otomasyonuna bağlanır
169. `CreatureDef` DataAsset'ine `work_skill` alanı: her yaratığın üs görevi + verimlilik statı (yeni tür = yine sadece veri)
170. **"Sırada ne var" pusulası:** ekranda her an tek net sonraki hedef (ana görev zincirinden); karmaşa = D1 ölümü
171. Sezonluk LiveOps: 6–8 haftalık temalı sezonlar — yeni kader kartları, kozmetik, dünya olayı; Patch PCK delta ile KB boyutunda güncelleme
172. **İttifak/Lonca (Diyar):** ortak dünya olayı hedefleri, inşa yardımı gönderme, ittifak sohbet pingleri — sosyal bağ D30/D90'ın kanıtlanmış motoru
173. **Adil monetizasyon anayasası:** yalnız kozmetik (arma, sancak, kale teması, yapı görünümü, yaratık kostümü) + sezon kartı; **güç satışı yasak** — premium hissi koru
174. Kademeli QoL otomasyonu: mühimmat üretimi, hasat, tamir kuyruğu geç oyunda tek tuşa iner — sıkıcı iş azalır, strateji kalır
175. İsteğe bağlı ödüllü reklam (yalnız tek oyunculu): "kervana ekstra mal" — asla zorunlu, asla kuşatma ortasında
176. **İlk 10 dakika altın akışı:** tutorial ekranı yok, yönlendirilmiş gerçek oyun — 8. dakikada ilk mini gece baskını = "aha" anı; hedef D1 ≥ %45
177. Geri dönüş karşılaması: 3+ gün ara verene "Kale Raporu" (neler oldu) + telafi sandığı — reaktivasyon döngüsü
178. Pak/IoStore yama güncellemeleri + Android **Play Asset Delivery**: sezon içeriği tam APK değil, ince yama olarak iner — mobil veri dostu LiveOps
179. Kayıt yedeği dışa/içe aktarma: platform dosya erişim katmanı (Android SAF plugin'i) — kayıt taşınabilirliği Faz 0'dan vatandaş
180. Denge testi akışı: `slomo` konsol komutu + CustomTimeDilation ile kuşatmalar hızlandırılmış oturumda doğrulanır
181. **Kültürel kimlik vitrini:** Anadolu bestiyarisi + Türkçe barklar pazarlamanın merkezine — EMEA/TR pazarı yükselişte, kültürel içerik ayrıştırıcı
182. Yapı seti derinliği: duvar parçalarının çeyrek-grid varyantları (köşe, kemer, mazgal, payanda) — inşacı kitlenin yaratıcı ifade talebi (Enshrouded dersi)
183. Faz -1'de **Lyra Starter Game** incelemesi: GAS, CommonUI, Enhanced Input ve modüler oyun mimarisinin resmi örneği; Faz 5 girişinde Replication Graph örnekleri — tekerleği yeniden icat etme
184. **"Duvarlar asla kozmetik değil" garantisi:** PvE'de dahi baskınsız gece yok; her yapının savunma değeri test matrisinde ölçülür
185. Zorluk ön ayarları + **Demir İrade** modu: tek can, kalıcı ölüm, özel başarım seti — hardcore kitle de evinde

**Faz dağılımı:** 161, 164, 166, 170, 176 → Faz 0–1 · 162, 165, 167–169, 174, 182, 184–185 → Faz 2–3 · 163, 171–173, 175, 177 → Faz 3–6 (LiveOps) · 178–180 sürekli · 183 → Faz 5 girişi.

---

## 6. UNREAL ENGINE 5.7 MİMARİSİ

### 6.0 Görsel stil kararı — 2.5D
- UE'de saf 2D (Paper2D) yıllardır bakımsız; motorla savaşmak yerine motoru kullanıyoruz: **3D dünya + sabit kuş bakışı kamera (top-down 2.5D)**, stilize el boyaması/low-poly görünüm.
- Kayıp Orman'ın kimliği palet, oranlar ve "gece korkusu" ile korunur; piksel-art'ta ısrar edilirse alternatif Paper2D+PaperZD yolu var ama önerilmez (bakımsız araç seti riski).
- **Mobilde Nanite ve Lumen kapalı** (mobil hedefte kullanılmaz) — geleneksel LOD + sade dinamik ışık; masaüstü aynı içerikle Lumen açar.

### 6.1 Proje iskeleti (C++ çekirdek + Blueprint içerik)
```
Source/KayipKrallik/
├── Core/        # GameInstance, KKGameMode, TimeOfDaySubsystem, SaveSubsystem
├── Combat/      # GAS: AttributeSet'ler, Ability'ler (saldırı/blok/parry/liderlik)
├── World/       # PCG graph sürücüleri, POISpawner, BiomeRules
├── Build/       # BuildGridSubsystem, StructureRegistry, BlueprintIO (şablon)
├── Siege/       # WaveDirector, SiegeTargetScorer (EQS), ThreatScaler
├── Army/        # Mass Entity fragment/processor'ları, SquadCommandComponent
├── Economy/     # ProductionQueue, StorageNetwork, MarketRules
└── Creatures/   # StateTree görevleri, CreatureDef tipleri
Content/
├── Data/        # DataAsset & DataTable: item/düşman/taret/kart/tarif/ses
├── UI/          # UMG + CommonUI widget'ları (gamepad+dokunmatik tek koddan)
└── Audio/       # MetaSounds kaynakları, Concurrency/Attenuation varlıkları
```

### 6.2 Temel kararlar
- **C++ / Blueprint ayrımı:** Sistemler ve performans-kritik döngüler C++; içerik, denge ve UI akışı Blueprint — solo geliştirici için hızlı iterasyon, Live Coding açık.
- **Olay omurgası:** Gameplay Message Subsystem (Lyra deseni) — sistemler birbirini tanımaz; `Wall.Destroyed`, `Wave.Started`, `Card.Drawn` GameplayTag mesajlarıyla konuşur (temiz mimari değişmezi korunur).
- **Savaş & yetenekler:** **GAS (Gameplay Ability System)** — can/stamina AttributeSet, saldırı/blok/parry Ability, liderlik becerileri (53) GameplayEffect; MP replikasyonu motorla birlikte gelir.
- **AI:** Birim/yaratık davranışı **StateTree**; karmaşık NPC mantığı Behavior Tree; hedef seçimi **EQS**.
- **Kuşatma yolu:** Dinamik NavMesh + duvar segmentlerine HP-ağırlıklı NavArea maliyeti → ordu "en ucuz gediği" kendiliğinden bulur (özellik 48); EQS skoru kapı/zayıf duvar önceliğini verir.
- **Dünya:** World Partition (streaming grid) + **PCG** ile seed'li deterministik doldurma + HISM duvar/dekor örneklemesi.
- **Veri odaklı denge:** ItemDef/EnemyDef/TurretDef/CardDef → PrimaryDataAsset; tablolar DataTable. Denge yaması = veri düzenle, derleme yok.
- **Kayıt:** USaveGame + `SaveVersion` alanı; migrasyon fonksiyon zinciri — KO'daki `v:1` disiplininin devamı.

### 6.3 Mobil performans bütçesi (anayasa, mad. 4)
| Bütçe | Hedef |
|---|---|
| Frame süresi | ≤ 16.6 ms (60 FPS), pil modunda 33 ms — Device Profiles ile katman |
| Ekranda tam aktör birim | ≤ 60 (üstü Mass LOD'a düşer) |
| Hareketli ışık | ≤ 12 (meşaleler mesafeyle söner); mobilde dinamik gölge yok |
| Draw call (Vulkan) | ≤ 150 — HISM + atlas + malzeme birleştirme zorunlu |
| Spawn/GC baskısı (savaş anı) | ~0 — aktör ve Niagara havuzdan |
| Paket boyutu | Taban APK ≤ 400 MB + içerik Play Asset Delivery ile |
| Streaming | World Partition hücre yükü hitch'siz (async, ≤ 2 ms/frame) |

### 6.4 Çok oyunculu mimarisi (Krallık kipi — host otoriteli)
- **Topoloji:** UE listen server. Host simüle eder, istemciler girdi gönderir + replikasyon alır. Tek oyunculu mod = "host'u tek kişi oynamak"; ayrı kod yolu yok.
- **UE katmanı:** Aktör replikasyonu + RPC'ler; sık değişen durum (pozisyon/HP) replicated property + NetUpdateFrequency ayarı; uyuyan yapılar NetDormancy ile sıfır maliyete iner.
- **Determinizm kazancı:** Arazi senkronize edilmez — seed paylaşılır, PCG her uçta aynı dünyayı üretir. Ağ yükü yalnız dinamik varlıklar + olaylar.
- **Olay-bazlı inşa:** `Server_PlaceStructure(GridPos, DefId)` RPC → doğrulama otoritede, her uç yapıyı yerelde kurar; bant genişliği hedefi ≤ 12 KB/s/istemci.
- **İlgi yönetimi (AOI):** NetCullDistance + (Diyar'da) Replication Graph — uzak varlıkların replikasyonu kapanır; ordular için kritik.
- **Otorite disiplini:** Tüm sistemler Faz 0'dan itibaren `HasAuthority()` farkındalığıyla yazılır → Faz 4'te netcode "açılır", yeniden yazılmaz.
- **Çevrimiçi servisler:** **EOS (Epic Online Services), ücretsiz:** lobi + oda kodu, NAT punch/relay, istatistik — relay sunucusu işletme derdi yok.
- **Kayıt:** Dünya kaydı host cihazında; misafir oyuncunun karakter profili (ekipman/XP) kendi cihazında — çift katmanlı USaveGame.
- **Saat & dalgalar:** TimeOfDaySubsystem ve WaveDirector yalnız otoritede karar verir; istemciler interpolasyon yapar.

### 6.5 Ordu simülasyonu — iki katmanlı savaş (Mass Entity)
- **Katman 1 (görünür):** Oyuncu çevresindeki birimler tam aktör — animasyon, hitbox, GAS yetenekleri.
- **Katman 2 (ekran dışı):** Aynı birimler **Mass Entity** kaydına iner: 4 Hz processor tick, hitbox yok; vuruş çözümü stat tabanlı (saldırı vs zırh + seed'li deterministik RNG), konum NavMesh yolu üzerinde parametrik ilerler. 1.000+ ekran dışı birim ≈ ihmal edilebilir CPU (CitySample kalabalıklarının kanıtladığı desen).
- **Şeffaf geçiş:** Kamera yaklaşınca Mass kaydından tam aktör örneklenir (pozisyon/HP/hedef korunur) — oyuncu dikiş görmez.
- **Komut zinciri (özellik 117):** İşaretle → hedef ID + yol önbelleği → squad durumu `MarchToTarget` → varışta çatışma → `Battle.Resolved` mesajı → savaş raporu UI.
- **MP'de:** Ordu simülasyonu yalnız otoritede koşar; istemciler görünür birimleri replikasyonla alır, ekran dışı için yalnız rapor olayını.

### 6.6 Ses mimarisi — 300+ varlık, MetaSounds yönetmeni
```
Master (Submix hiyerarşisi)
├── Music      → MetaSounds + Quartz (keşif/alacakaranlık/kuşatma/boss katmanları)
│                + sidechain duck (Combat çalarken müzik kısılır)
├── SFX
│   ├── Combat   (Concurrency: limit 10, öncelik kuralı)
│   ├── Build/World
│   └── Ambience (biyom + hava katmanları, crossfade)
├── UI
└── Voice/Barks  (Concurrency: limit 3)
Global: gece lowpass filtresi · iç mekân Audio Volume → Reverb submix
```
- **Veri odaklı ses:** SoundDef DataAsset `{varyasyon seti, pitch/vol aralığı, concurrency grubu, öncelik, cooldown}` — yeni ses eklemek = veri, kod yok (98 ile aynı disiplin).
- **Varyasyon:** MetaSounds rastgele seçici + pitch düğümleri → 3 kayıt 12 gibi duyulur.
- **Polifoni/voice-stealing:** Sound Concurrency kuralları motorun yerleşiği — limit aşımında en düşük öncelikli + en uzak ses kesilir. Ordu savaşının ses bütçesi sabittir.
- **Konum:** Attenuation varlıkları (mesafe eğrisi + spatialization); kuşatma uğultusu mesafeyle şekillenir.
- **Mobil:** SFX bellekte, uzun ambiyans loop'ları stream; pil modunda ambiyans katman sayısı düşer.

**Ses kataloğu (hedef ≈ 345 varlık):**

| Kategori | Örnek | Adet |
|---|---|---|
| Dövüş vuruşları — 5 silah × 5 malzeme (et/ahşap/taş/metal/kalkan) × 3 varyasyon | kılıç-metal-2 | 75 |
| Adım sesleri — 6 zemin × yürüme/koşu × 4 varyasyon | çim-koşu-3 | 48 |
| Barklar — asker/köylü × onay/panik/zafer/selam (Türkçe) | "Emredersin!" | 64 |
| Yaratıklar — 8 düşman × spawn/saldırı/ölüm/idle | gargoyle-çığlık | 32 |
| İnşa & yıkım — yerleştirme, çekiç, çatlama, çöküş × malzeme | taş-çöküş | 24 |
| UI — menü, kart çekme, başarım, bildirim, inşa onayı | kart-flip | 20 |
| Toplama — kesme, kazma, hışırtı, devrilme, cevher kırılma | ağaç-devrilme | 18 |
| Mekanik & doğa loop'ları — kapı, ateş, su, mancınık gerilimi, at | kale-kapısı | 18 |
| Kuşatma araçları — koçbaşı, mancınık fırlatma/çarpma, kule gıcırtısı | koçbaşı-darbe | 16 |
| Ambiyans katmanları — 6 biyom × gündüz/gece + 4 hava | bataklık-gece | 16 |
| Müzik stem'leri — 4 durum × 3–4 katman | kuşatma-perküsyon | 14 |
| **Toplam** | | **≈ 345** |

### 6.7 Diyar Sunucusu — 50 oyuncu mimarisi (UE dedicated)
- **Build:** UE **dedicated server** hedefi — motor kaynak kodundan derleme gerekir (Faz -1'de pipeline kurulur); Linux x86_64 headless çıktı.
- **Barındırma:** x86_64 Linux VPS (Hetzner sınıfı, ilk şard ~€15-25/ay) — Oracle ARM ücretsiz katmanı UE sunucusu için uygun değil; şard başına maliyet Faz 5 çıkış raporuna girer.
- **Tick modeli:** Sunucu 20 Hz sabit simülasyon; istemcide interpolasyon + yalnız kendi karakterinde girdi tahmini (CharacterMovement yerleşik).
- **İlgi yönetimi:** **Replication Graph** (Fortnite'ta kanıtlı) — grid node'larıyla zone AOI; oyuncu başına sync seti çevresindeki hücreler. 50 oyuncu dağınıkken ağ yükü oyuncu sayısıyla değil **yoğunlukla** ölçeklenir — tımar mesafesi (134) bunu garanti eder.
- **Varlık bütçesi:** Diyar'da oyuncu başı ordu tavanı 24 birim (sunucuda max ~1.200 komutalı birim) — Mass katman-2 (6.5) sayesinde ucuz; tam aktör yalnız oyuncuların çevresinde örneklenir.
- **Tam sunucu otoritesi:** Hareket doğrulama + eylem oran limitleri; envanter/hasar yalnız sunucuda — 50 yabancıyla hile direnci (112'nin güçlendirilmiş hali).
- **Kalıcılık:** 5 dakikada bir dünya snapshot'ı (binary) + arada olay günlüğü; çökmede snapshot + journal replay. Oyuncu profilleri sunucu tarafında.
- **Bant genişliği hedefi:** AOI ile oyuncu başı ≤ 20 KB/s (mobil veri dostu).
- **Tek kod tabanı:** Krallık kipinde otorite host'ta, Diyar'da VPS'te koşar — Faz 0'ın `HasAuthority()` disiplini ikisini aynı koddan üretir.
- **Çıkış ölçümü:** 50 bot istemciyle yük testi → sunucu frame ≤ 20 ms, istemci 60 FPS.

### 6.8 Geliştirme ortamı — Faz -1'in alışveriş listesi
- **PC hedef spek:** 8+ çekirdek CPU (7800X3D / 14700K sınıfı), **64 GB RAM** (32 GB asgari), RTX 4070 12 GB sınıfı GPU, **2 TB NVMe** (motor kaynağı + proje + DerivedDataCache rahatça 600 GB+ ister).
- **Sürüm kontrolü:** Git + **Git LFS** (binary asset'ler) ya da Perforce ücretsiz katman; `Saved/`, `Intermediate/`, `DerivedDataCache/` ignore'da.
- **Android hattı:** Android Studio + UE'nin desteklediği SDK/NDK sürümleri, Vulkan, Device Profiles ile katmanlı kalite; telefon artık birincil **test cihazı** — her sprint sonu gerçek cihazda 60 FPS doğrulaması.
- **Derleme gerçeği:** Motor kaynak derlemesi saatler sürer (bir kez + sürüm güncellemelerinde); oyun C++ artımlı derlemesi dakikalar, Blueprint iterasyonu anlık — Live Coding açık tutulur.

---

## 7. YOL HARİTASI

| Faz | Ad | Kapsam (özellik no) | Çıkış kriteri |
|---|---|---|---|
| **-1** | **Donanım & Pipeline** | PC temini (6.8 spek), UE 5.7 + kaynak derleme (sunucu hedefi), Git LFS, Android SDK/NDK, Lyra incelemesi (183), 2.5D stil testi: tek sahne görsel hedef kanıtı | UE editör akıcı; boş proje telefona Vulkan build alıyor; stil sahnesi onaylı |
| **0** | **Çekirdek Port** | KO sistemlerinin UE 5.7'ye taşınması: hareket, World Partition+PCG dünyası, toplama, gün/gece, USaveGame v2, mobil kontrol, `HasAuthority()` disiplini, ses omurgası, temel menü (1, 80, 87, 94–96, 141–142) | Telefonda 60 FPS açık dünya yürüyüşü + toplama + otokayıt |
| **1** | **İlk Kale (MVP)** | İnşa grid'i, 4 yapı, Kalp Taşı, balista, basit dalga, 1 köylü, ilk 8 fauna (21–25, 31, 33, 38, 43, 55, 153–155) | "10 gece hayatta kal" dikey dilimi oynanabilir |
| **2** | **Kuşatma & Ordu** | Düşman çeşitliliği, kuşatma araçları, botlar, akıllı AI, ekonomi zinciri + asker üretimi, komuta, Mass ekran dışı savaş (34–37, 39–54, 65–70, 113–124) | Orduya işaretle-saldır emri ver, ekranı kapat, savaş raporunu al; 5. gece kuşatması gerilim üretiyor |
| **3** | **Krallık, Ses & Bestiyari** | Kader kartları, köy/meslekler, görevler, bosslar, prestij + 300+ ses kataloğu (MetaSounds) + tam ayar paketi & dokunmatik editör + 40 yaratık & mistikler & evcilleştirme (11–20, 56–64, 71–79, 88–93, 125–130, 143–152, 156–160) | Tam oyun döngüsü + meta + işitsel kimlik + canlı ekosistem |
| **4** | **Krallık Kipi (Co-op)** | 1–4 oyuncu netcode, aktör replikasyonu, EOS lobi/NAT, yeniden bağlanma (101–110, 112) | 4 oyuncu mobil veride aynı kuşatmayı 60 FPS savunuyor |
| **5** | **Diyar (50 Oyuncu)** | UE dedicated server, Replication Graph AOI, dev harita, tımar + doğuş dağıtımı, dünya haritası, hızlı seyahat, dünya olayları, şard tarayıcı, çevrimdışı kalıcılık (131–140) | 50 bot yük testinde sunucu frame ≤ 20 ms, istemci 60 FPS; gerçek oyuncular dibinde başlamıyor |
| **6** | **Cila & Yayın** | Bulut kayıt, foto/tekrar modu, mod temeli, async PvP, Play Asset Delivery paketleme, mağaza hazırlığı (4, 78, 97–100, 111) | Kapalı beta → Play Store + Steam |

*ADR-001 gereği faz süreleri Godot tahminlerine göre **+%30-50** revize edilmiştir (C++/BP öğrenme eğrisi + derleme/asset hattı).*

---

## 8. RİSKLER & ÖNLEMLER

| Risk | Etki | Önlem |
|---|---|---|
| Duvar değişiminde NavMesh yeniden inşa maliyeti | Gece FPS düşüşü | Dinamik NavMesh'i tile bazlı sınırlı yeniden inşa + duvar maliyeti NavArea ile (tam rebuild yok) |
| Mobil fill-rate (ışık + Niagara) | Eski cihazlarda kasma | Işık limiti 12, dinamik gölge yok, Device Profiles + pil modu, emitter LOD |
| Save şeması sürekli değişecek | Kayıt kırılması = oyuncu kaybı | Versiyonlu USaveGame migrasyon zinciri Faz 0'dan itibaren zorunlu |
| Kapsam patlaması (185 özellik!) | Hiç bitmeyen proje | Faz kapıları: bir faz çıkış kriterini geçmeden sonrakine kod yazılmaz |
| Denge ayarı kod gerektirirse yavaşlar | İterasyon hızı düşer | Tüm sayılar DataAsset/DataTable'da; denge = veri düzenleme |
| Mobil NAT / bağlantı kopması | Co-op kurulamıyor | EOS lobi + NAT punch/relay (ücretsiz) + oda kodu + yeniden bağlanma state'i (108–109) |
| Host-istemci durum kayması | Hayalet duvar/asker | Olay-bazlı yapı RPC'si + periyodik hafif checksum + otorite düzeltmesi |
| Ordu savaşında ses kakofonisi | Mix çamurlaşır, CPU yanar | Sound Concurrency limitleri + voice-stealing (127) |
| Ekran dışı savaşa güvensizlik | "Hile var" hissi | Deterministik çözüm + şeffaf savaş raporu (120–121) |
| 50 oyuncuda sunucu CPU/ağ patlaması | Diyar oynanamaz | Replication Graph AOI + ordu tavanı (24) + Mass katman-2; çıkış kriteri yük testiyle ölçülür (frame ≤ 20 ms) |
| Sunucu işletme maliyeti | Sürdürülemez şardlar | x86_64 VPS (~€15-25/ay/şard); Faz 5'te şard başına maliyet raporu zorunlu |
| Oyuncular yine de kümelenir | Tımar bölgesi tıkanır | Doğuş Poisson-disk + dolu bölge ağırlık cezası; taşınma mekaniği (Kalp Taşı söküp yeniden kurma) |
| **UE öğrenme eğrisi (solo)** | İlk fazlar yavaşlar | Faz -1 Lyra etüdü; C++ çekirdeği dar tut, içerik Blueprint'te; süreler +%30-50 revize (ADR-001) |
| **APK/paket boyutu şişmesi** | Mobil indirme bariyeri | Taban ≤ 400 MB hedefi; Play Asset Delivery + pak yamaları (178); kullanılmayan plugin'ler kapalı |
| **Derleme/iterasyon süreleri** | Günlük hız kaybı | Live Coding açık, içerik Blueprint-ağırlıklı, motor derlemesi yalnız sürüm güncellemesinde |
| **UE6 geçişi ortada** | Proje ortasında motor nesli değişimi | v1 UE 5.7'de tamamlanır; UE6 değerlendirmesi v2 konusu (ADR-001) — bekleyerek zaman kaybı yok |

---

## 9. İLK SPRINTLER

**Faz -1 — Donanım & Pipeline:**
1. PC temini (6.8 spek listesi) + UE 5.7 kurulumu; motor kaynak derlemesi (dedicated server hedefi için bir kez)
2. Git + LFS deposu, `.gitignore` (Saved/Intermediate/DDC), ilk commit disiplini
3. Android Studio + SDK/NDK kurulumu; boş projeyi telefona Vulkan build alarak hattı doğrula
4. Lyra etüdü (183): GAS, CommonUI, Enhanced Input, Gameplay Message desenlerini çıkar
5. 2.5D stil kanıtı: tek sahne — orman + meşale gecesi + 1 şövalye; görsel hedef onayı (6.0)

**Faz 0 — Çekirdek Port:**
1. Proje iskeleti (6.1): Core subsystem'ler — GameInstance, TimeOfDaySubsystem, SaveSubsystem (USaveGame v2, versiyonlu), Gameplay Message omurgası
2. Dünya: World Partition + PCG seed'li biyom doldurma (KO'nun gürültü kurallarının portu), HISM dekor
3. Oyuncu: Character + Enhanced Input (sanal joystick + bağlamsal buton), GAS iskeleti (can/stamina AttributeSet)
4. Toplama döngüsü: ağaç/taş/çalı + envanter bileşeni + CommonUI hotbar
5. Gece/ışık: Directional Light gün eğrisi + meşale point light'ları + ilk gölge düşmanı (havuzdan, StateTree)
6. Ses omurgası: Submix hiyerarşisi + ilk MetaSounds varyasyonlu SFX seti + Concurrency kuralları
7. Otorite disiplini: tüm sistemler `HasAuthority()` farkındalığıyla (tek oyunculu = host) — Faz 4 netcode'una sıfır-refactor zemini
8. Cihaz testi: orta seviye Android'de 60 FPS doğrulaması + Unreal Insights profil raporu

---

*Onay sonrası Faz -1, stüdyo disipliniyle (ticket, kalite kapıları, API doğrulama — UE 5.7 resmi dokümantasyonuna karşı) açılır.*
