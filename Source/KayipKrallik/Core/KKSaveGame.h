// KKSaveGame.h — Versiyonlu kayıt şeması v2 (plan 95: eski kayıt asla kırılmaz). [TKT-F0-005]
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/SaveGame.h"
#include "KKSaveGame.generated.h"

UCLASS()
class KAYIPKRALLIK_API UKKSaveGame : public USaveGame
{
	GENERATED_BODY()

public:
	/** Şema sürümü. v1 = KO atası; v2 = UE portu; v3 = inşa; v4 = yağma keseleri + köylü. */
	UPROPERTY() int32 SaveVersion = 4;

	UPROPERTY() int32  WorldSeed = 12345;
	UPROPERTY() int32  Day = 1;
	UPROPERTY() float  TimeSec = 0.f;

	UPROPERTY() FVector PlayerLocation = FVector::ZeroVector;
	UPROPERTY() float Health  = 100.f;
	UPROPERTY() float Stamina = 100.f;
	UPROPERTY() float Hunger  = 100.f;

	UPROPERTY() TMap<FName, int32> Inventory;

	/** Hasat edilen düğümler: anahtar + yeniden doğma zamanı (paralel diziler — TMap<int64,float> UPROPERTY kısıtı yüzünden). */
	UPROPERTY() TArray<int64> HarvestedKeys;
	UPROPERTY() TArray<float> HarvestedRespawnAt;

	/** v3 — Yerleştirilmiş yapılar (paralel diziler): anahtar, tip, bayraklar (bit0 = kapı açık). */
	UPROPERTY() TArray<int64> BuildKeys;
	UPROPERTY() TArray<uint8> BuildTypes;
	UPROPERTY() TArray<uint8> BuildFlags;

	/** v4 — Yağma keseleri: "eşya asla buharlaşmaz" sözünün kayıt ayağı.
	 *  Düz paralel diziler (UPROPERTY iç içe konteyner sevmez): kese i'nin eşyaları
	 *  LootIds/LootCounts içinde [başlangıç..başlangıç+LootBagItemNum[i]) aralığındadır. */
	UPROPERTY() TArray<FVector> LootBagLocations;
	UPROPERTY() TArray<int32>   LootBagItemNum;
	UPROPERTY() TArray<FName>   LootIds;
	UPROPERTY() TArray<int32>   LootCounts;

	/** v4 — Köylü (Faz 1: Ayla). 0=kafeste bekliyor · 1=kurtarıldı · 2=öldü (isimli karakter geri gelmez). */
	UPROPERTY() uint8 VillagerState = 0;
};
