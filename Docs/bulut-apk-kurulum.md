# Bulut APK Kurulumu — Telefondan Yapılacak 3 Adım

PC olmadan APK almak için GitHub Actions kullanıyoruz. Workflow hazır
(`.github/workflows/android-apk.yml`); çalışması için Epic'in UE container
imajına erişim yetkisi gerekiyor. Bunu yalnız SEN verebilirsin (EULA gereği).
Hepsi telefon tarayıcısından yapılır, ~10 dakika.

## Adım 1 — Epic hesabını GitHub'a bağla

1. Tarayıcıdan **epicgames.com** → giriş yap → sağ üst hesap → **Hesap**.
2. **Uygulamalar ve Hesaplar** (Apps & Accounts) → **GitHub** → **Bağlan**.
3. GitHub'a yönlendirir; izin ver. Epik EULA onayını sorarsa kabul et.
4. Birkaç dakika içinde GitHub e-postana **EpicGames organizasyon daveti**
   gelir (github.com/EpicGames). Daveti **kabul et** — bu şart;
   davet kabul edilmeden imaj çekilemez.

## Adım 2 — GitHub erişim anahtarı (PAT) oluştur

1. **github.com** → sağ üst profil → **Settings** → en altta
   **Developer settings** → **Personal access tokens** → **Tokens (classic)**.
2. **Generate new token (classic)**:
   - Note: `epic-container`
   - Expiration: 90 gün (bitince aynı adımla yenilenir)
   - Yetki: yalnız **read:packages** işaretle
3. **Generate** → çıkan `ghp_...` metnini kopyala (sayfadan ayrılınca bir
   daha gösterilmez).

## Adım 3 — Anahtarı depoya secret olarak ekle

1. **github.com/mehmetdem2005/oyun** → **Settings** → **Secrets and
   variables** → **Actions** → **New repository secret**.
2. Name: `EPIC_GHCR_TOKEN` — Secret: az önce kopyaladığın `ghp_...` değeri.
3. **Add secret**.

## Çalıştırma

Depo → **Actions** sekmesi → **Android APK (UE 5.7 bulut derlemesi)** →
**Run workflow**. Bittiğinde APK, çalıştırma sayfasının altında
**Artifacts** bölümünden indirilir.

İlk koşu 2-4 saat sürebilir (imaj indirme + cook + derleme).

## Dürüst risk notu

- GitHub'ın ücretsiz runner diski sınırlı (~85 GB, temizlikle ~140 GB'a
  esnetiyoruz). Epic'in `dev-slim-5.7` imajı sınıra yakın — **ilk
  denemede disk yetmeyebilir**. Yetmezse log bunu açıkça gösterir;
  alternatif (daha küçük imaj etiketi / self-hosted runner) o zaman
  değerlendirilir. Garantili değil, denenebilir en iyi yol bu.
- `dev-slim` imajında Android SDK yoksa betik UE'nin kendi
  `SetupAndroid.sh`'ı ile kurmayı dener; bu da ilk koşuda ek süre demek.
- Adımları bitirince Claude'a "secret ekledim" yaz — workflow'u tetikleyip
  logları takip eder, hata çıkarsa düzeltir.
