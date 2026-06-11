// KKGameMode.h — Dünya kurulum orkestratörü: ışık/sis/post + streamer + kayıt akışı. [TKT-F0-018]
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/GameModeBase.h"
#include "KKGameMode.generated.h"

class AKKWorldStreamer;
class UKKSaveGame;

UCLASS()
class KAYIPKRALLIK_API AKKGameMode : public AGameModeBase
{
	GENERATED_BODY()

public:
	AKKGameMode();

	virtual void StartPlay() override;
	virtual void HandleStartingNewPlayer_Implementation(APlayerController* NewPlayer) override;

protected:
	void SetupWorld();
	void PlacePlayer(APlayerController* PC);

	UPROPERTY() TObjectPtr<AKKWorldStreamer> Streamer;
	UPROPERTY() TObjectPtr<UKKSaveGame> PendingSave; // GC güvenli referans
	FVector StartCampWorld = FVector::ZeroVector;
};
