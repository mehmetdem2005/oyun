# Test Stratejisi
## Piramit
1. **Davranış sözleşmesi (otomatik, CI kapısı):** `core/src/smoke` — 59 senaryo,
   gerçek `Game.update` üzerinden uçtan uca kural doğrulaması (yerleşim, kuşatma,
   yağma, Ayla yaşam döngüsü, YÜZME, SOPA, DOMUZ, SÖKÜM, SOHBET, ŞAFAK KARNESİ,
   kayıt tur dönüşü, 5. gece, zafer). İlk hata = çıkış 1 = **APK yok**.
2. **Tip sözleşmesi:** kabuk `tools/android-stub.jar`'a karşı tam derlenir —
   imza kayması anında yakalanır (davranış değil).
3. **Manuel çizim listesi (cihazda, her sürümde):** yürüyüş salınımı · yüzme kulacı +
   halkalar · sopa süpürmesi · kurt kuyruk/kor göz · domuz şarj tozu · gölge telleri ·
   kalp rün+mote · su parıltısı · paneller (sohbet/envanter/inşa barı) · dikey↔yatay dönüş.
4. **Cihaz matrisi:** minSdk 24 düşük-uç + güncel amiral; dikey ve yatay.

## Gelecek
Seed taraması (1000 tohumda kamp/su/kafes değişmezleri), kare-süresi bütçe testi (frame ≤ 12 ms).
