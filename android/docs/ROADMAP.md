# Yol Haritası
## v2.1 — Global Sohbet (sunucu)
`ChatPort` hazır. Adaptör: Supabase Realtime kanalı `chat:global`.
Tablo: `messages(id, room, who, body, created_at)` + RLS: herkes okur, yazma auth.
Kabukta `SupabaseChat : ChatPort` → `send` insert, kanal dinleyici → `s.pushChat(.., 2)`.
Çekirdek dosyaları DEĞİŞMEZ — mimarinin sınavı budur.

## Sonrası
- Minimap (chunk önbelleğinden küçültme) · Yağmur/hava (görsel + av davranışı)
- Görev NPC üçlüsü (web sürümündeki zincirin taşınması) · Gamepad desteği
- Ses miksi (gece ambiyans katmanı) · Başarımlar (karne verisinden)
