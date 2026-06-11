// KKGameState.h — Replike dünya gerçekleri: seed + gün + saat (plan 102). [TKT-F0-016]
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/GameStateBase.h"
#include "KKGameState.generated.h"

UCLASS()
class KAYIPKRALLIK_API AKKGameState : public AGameStateBase
{
	GENERATED_BODY()

public:
	UPROPERTY(Replicated, BlueprintReadOnly, Category="KK") int32 WorldSeed = 12345;
	UPROPERTY(Replicated, BlueprintReadOnly, Category="KK") int32 Day = 1;
	UPROPERTY(Replicated, BlueprintReadOnly, Category="KK") float TimeSec = 0.f;

	virtual void GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const override;
};
