# Mimari Tanımı — Kayıp Krallık Native
*ISO/IEC/IEEE 42010 düzenine göre; TOGAF ADM'nin Vizyon→Teknoloji hattıyla eşlenmiştir.*

## 1. Paydaşlar ve Kaygılar
| Paydaş | Kaygı |
|---|---|
| Oyuncu (telefon, dikey) | 60 fps, dokunmatik erişilebilirlik, veri kaybı olmaması |
| Geliştirici (tek kişi, PC'siz) | Telefon-yalnız akış; çekirdeğin cihazsız test edilebilmesi |
| CI hattı | Deterministik derleme; smoke geçmeden APK üretmeme |
| Gelecek sunucu (global chat) | Çekirdeğe dokunmadan ağ adaptörü takılabilmesi |

## 2. Görünümler
### 2.1 Modül Görünümü
```
:core   saf Kotlin/JVM  — kural, durum, dünya üretimi, sohbet PORTU
:app    Android kabuk   — çizim, ses, dokunuş, kayıt; :core'a TEK YÖNLÜ bağımlı
```
**Bağımlılık kuralı:** `:core` hiçbir Android sınıfı görmez (derleyici zorlar —
modülün classpath'inde Android yoktur). Kabuk, çekirdeğe yalnız şu kapılardan girer:
`setDir / tapAttack / tapInteract / tapBuild / quickChat / craftClub / update`.

### 2.2 Çalışma-Zamanı Görünümü (kare akışı)
```
dokunuş → girdi bayrakları ──┐
                             ├─ g.update(dt)  [tek otorite: çekirdek]
                             └─ kuyruk drenajı: sfx/toast/big/flash/shake → kabuk
çizim: chunk önbelleği → su parıltısı → y-sıralı varlıklar → parçacık → gece → HUD
```
**Kuyruk sözleşmesi:** kabuk her karede kuyrukları okur ve **temizler**; çekirdek
asla kabuğu çağırmaz (Blackboard'ın tek-yönlü hâli).

### 2.3 Dağıtım Görünümü
Telefon → git push → GitHub Actions → `:core:smoke` (kapı) → `:app:assembleDebug`
→ **Releases'a APK**. Gelecek: `ChatPort` → Supabase Realtime adaptörü (ROADMAP).

## 3. Mimari Kararlar (ADR özetleri)
- **AD-1 Hexagonal:** kural tek yerde; kabuk değişse de oyun değişmez. Bedel: köprü disiplinli olmalı.
- **AD-2 Sıfır bağımlılık:** AGP dışında kütüphane yok → APK küçük, tedarik zinciri riski sıfır.
- **AD-3 Deterministik çekirdek:** LCG + seed → smoke senaryoları tekrarlanabilir.
- **AD-4 Önceden tahsis:** parçacık havuzu, chunk LRU → sıcak döngüde GC duraksaması yok.
- **AD-5 Sohbet portu:** arayüz çekirdekte, adaptör kabukta; ağ sürümü çekirdeği değiştirmez.

## 4. TOGAF Eşlemesi (özet)
Vizyon: tek-cihaz hayatta kalma + topluluk tohumu (chat) · İş: oturum başı 1 gün döngüsü ·
Veri/Uygulama: Snapshot v2 sözleşmesi, modül sınırları · Teknoloji: Kotlin 2.0 / minSdk 24 / CI-Release.
