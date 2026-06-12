# Görsel Kanon — İlk HTML'in Piksel Dili
**Düzeltme notu:** v2'de "kapsül/yuvarlak" diye icat edilen stil REDDEDİLDİ ve geri
alındı. Bu projenin görsel kanonu **kayip-orman.html'in fillRect grammarı**dır;
her yeni varlık bu dile çevrilerek eklenir, dil değiştirilmez.

## Grammar
- Birim: keskin `fillRect` blokları + seçilmiş yerlerde daire (yaprak kümeleri, tavşan).
- İnsan: 12×11 ten kafa, 14×13 gömlek, 5×9 bacaklar; yürüyüş = bacak X-kayması
  (`sin(walkT·11)·3`), gövde bob'u, gözler bakış yönüne 2.5px kayar; arkadan saç dolu.
- Oyuncu paleti: gömlek `#3e7fd0`, saç `#503217`, ten `#f2c08c`, bacak `#27405e`.
- Silah: omuz çapasında `atan2(fy,fx)` + savurma `-86°→+57°` (balta mekaniği; sopa gövdesi).
- Gölge: `wob = sin(t·6)·0.14+1` ile nefes alan mürekkep elipsi `#221440`,
  üstte mor taç yayı `#3a2766`, iki dik beyaz göz şeridi.
- Kalp Taşı: iki katlı gri kaide + 45° döndürülmüş, `sin(t·2.4)·3` ile süzülen
  altın elmas `#e8b73d` ve `#fff1a8` çekirdek. Süs eklenmez.
- Fauna: HTML birebir; domuz bu dile çevrilerek eklendi (blok gövde, beyaz diş
  dikdörtgenleri, şarjda kor göz + toz).
- YÜZME (HTML'de yoktu): aynı dilde tasarlandı — su üstünde yarım gövde,
  dönüşümlü 5×3 kulaç blokları, köpük şeridi, eliptik halkalar.

## Kural
Yeni görsel = önce HTML'de karşılığı var mı bak; varsa **porta sadık kal**,
yoksa yukarıdaki grammar içinde tasarla. "Kendi standardını koymak" yasak.

---
## Kanon güncellemesi (v2.3)
Kullanıcının ürettiği **kara-fantezi boyalı sayfa** sprite kanonudur.
Kod tarafı gerçeği: Canvas vektör çizimi bu raster boyama dokusunu BİREBİR
üretemez — prosedürel çizim artık aynı kompozisyonun **düz-gölgeli
yaklaşımıdır** (tuğla sıraları, mazgal, mavi-altın sancak, kemerli kapı,
demir kuşaklı palisat). Birebirlik yalnız sprite hattından gelir;
prosedürel katman sprite olmayan her varlıkta yedek olarak yaşar.
