// KKEnemySpawner.h — Gece dalga yöneticisi: NightStarted -> damla damla gölge. [TKT-F0-020]
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "KKEnemySpawner.generated.h"

UCLASS()
class KAYIPKRALLIK_API AKKEnemySpawner : public AActor
{
	GENERATED_BODY()

public:
	AKKEnemySpawner();

protected:
	virtual void BeginPlay() override;

	void OnNight(int32 Day);
	void OnDawn();
	void SpawnBatch();
	int32 CountAlive() const;

	void SpawnBurstOne(); // kuşatma patlaması: kalp çevresinden, 1.2 sn arayla

	FTimerHandle WaveTimer;
	FTimerHandle BurstTimer;
	int32 CurrentDay = 1;
	int32 WaveLeft = 0;
};
