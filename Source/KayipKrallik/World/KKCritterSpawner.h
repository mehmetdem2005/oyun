// KKCritterSpawner.h — Gündüz fauna nüfusu: doğur, sürdür, av baskısını say. [TKT-F1-019]
// Plan 155 dilimi: aşırı avlanma bölge popülasyonunu DÜŞÜRÜR (gün içi tavan iner),
// her şafak doğa 2 puan TOPARLAR. Gece: ürkekler ortadan kaybolur (Faz 3 ekosistemde
// gece türleri ayrışacak — şimdilik gölgelerin tersi simetri, dünya nefes alıyor hissi).
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "KKCritterSpawner.generated.h"

UCLASS()
class KAYIPKRALLIK_API AKKCritterSpawner : public AActor
{
	GENERATED_BODY()

public:
	AKKCritterSpawner();

protected:
	virtual void BeginPlay() override;

	void OnDawn();
	void OnNight();
	void SustainTick();          // 5 sn'de bir: oyuncu çevresi nüfusu hedefe tamamla
	int32 CountAliveNear() const;

	FTimerHandle SustainTimer;
	int32 HuntPressure = 0;      // bugünkü av sayısı (Critter_Killed dinler)

	static constexpr int32 BasePop      = 6;     // sağlıklı bölge tavanı
	static constexpr float NearRadius   = 4500.f;
	static constexpr float SpawnMin     = 1800.f; // ekran dışından belirir
	static constexpr float SpawnMax     = 2600.f;
};
