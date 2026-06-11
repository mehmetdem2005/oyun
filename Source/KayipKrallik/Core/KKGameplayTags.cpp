// KKGameplayTags.cpp
#include "Core/KKGameplayTags.h"

namespace KKTags
{
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Time_DayChanged,   "KK.Time.DayChanged",   "Yeni gün başladı (IntValue = gün no)");
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Time_NightStarted, "KK.Time.NightStarted", "Karanlık eşiği aşıldı, gölgeler uyanıyor");
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Time_DawnStarted,  "KK.Time.DawnStarted",  "Şafak söktü, gölgeler ölür");
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Player_Damaged,    "KK.Player.Damaged",    "Oyuncu hasar aldı (FloatValue = miktar)");
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Player_Died,       "KK.Player.Died",       "Oyuncu öldü");
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Player_Respawned,  "KK.Player.Respawned",  "Oyuncu yeniden doğdu");
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Resource_Harvested,"KK.Resource.Harvested","Kaynak toplandı (StringValue = item id)");
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Inventory_Changed, "KK.Inventory.Changed", "Envanter değişti (HUD tazelenir)");
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Enemy_Killed,      "KK.Enemy.Killed",      "Gölge öldürüldü");
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Save_Completed,    "KK.Save.Completed",    "Kayıt tamamlandı");
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Build_Placed,      "KK.Build.Placed",      "Yapı yerleştirildi (IntValue=tip)");
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Build_Destroyed,   "KK.Build.Destroyed",   "Yapı yıkıldı (IntValue=tip)");
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Game_HeartDestroyed,"KK.Game.HeartDestroyed","Kalp Taşı düştü — yenilgi");
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Game_Victory,       "KK.Game.Victory",       "Gün 10'a ulaşıldı — dikey dilim zaferi");
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Villager_Rescued,   "KK.Villager.Rescued",   "Yurttaş katıldı (StringValue=isim)");
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Villager_Died,      "KK.Villager.Died",      "Yurttaş öldü — kalıcı (StringValue=isim)");
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Critter_Killed,     "KK.Critter.Killed",     "Av düştü (IntValue=tür) — av baskısı sayacı");
	UE_DEFINE_GAMEPLAY_TAG_COMMENT(Night_BigSiege,     "KK.Night.BigSiege",     "5. gece büyük kuşatma duyurusu (IntValue=gün)");
}
