# Oyun Tasarım Belgesi — Kayıp Krallık
**Fantezi:** Issız adada son Kalp Taşı'nın bekçisisin. Gündüz hazırlan, gece kuşatmayı kır,
10. şafakta krallık ayakta kalsın.

## Çekirdek Döngü (1 gün ≈ 4 dk gerçek zaman)
TOPLA (odun/taş/yemek, av) → KUR (duvar hattı, kapı, balista; sök-yeniden-kur) →
SAVUN (dalga + damla; kemirme; balista cıvataları) → **ŞAFAK KARNESİ** (gece özeti) → tekrar.

## "Bağımlılık" Mecazının Mühendisliği (etik retention)
- **Bir gece daha:** gün kısa; karne tostu bir sonraki hedefi fısıldar.
- **Zirve ritmi:** her 5. gece ×2 BÜYÜK kuşatma — gerilim testeresi.
- **Ucuz atlatma anları:** kalp %20'nin altına düşüp kurtulduğun gece hafızaya kazınır.
- **Bağ:** Ayla isimlidir ve ölürse GERİ GELMEZ — koruma içgüdüsü gerçek bedelle.
- **Görünür ilerleme:** çıplak yumruk → SOPA → taş hat → kale + balista ağı.
- Karanlık desen YOK: enerji duvarı yok, bekleme zamanlayıcısı yok, satın alma yok.

## Sistem Sayıları (denge tek bakışta)
| Şey | Değer |
|---|---|
| Oyuncu | 100 HP · 135 px/s · yüzerken 70 px/s, −6 enerji/s |
| Vuruş | 20 · sopayla 32 (3 odun) |
| Gölge | 30 HP · 77 px/s · dokunuş 10 · kemirme 8/0.8s |
| Kurt | kışkırtılınca 160 px/s, ısırık 15 |
| Yaban domuzu | 25 HP · yaralanınca 1.5 s ŞARJ (190 px/s, 10 hasar) · 3 et + 1 post |
| Kalp | 300 HP · düşerse oyun ruhen kaybedilir (devam serbest) |
| Balista | 8 cıvata başlar · E+odun=+5 (tavan 20) · 12 hasar / 2.5 s |

## Yüzme Tasarımı
Su yalnız oyuncuya açık: **kaçış yolu** ama bedeli var (yavaş + enerji erir + savaşamazsın).
Gölgeler suya giremez → kıyı hattı taktiksel sınırdır. Boğulma baskısı suyu "ikinci üs" olmaktan çıkarır.

## Sohbet
Hızlı mesaj çarkı (4) + Ayla'nın bağlamsal yanıtı bugün; **global chat** ChatPort'a
takılacak Supabase Realtime adaptörüyle gelir (ROADMAP) — çekirdek değişmeden.
