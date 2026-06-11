// KKCampfire.h — Kamp merkezi: taş çember + ateş + sıcak ışık (gecenin güvenli çapası). [TKT-F0-010]
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "KKCampfire.generated.h"

class UPointLightComponent;
class UStaticMeshComponent;

UCLASS()
class KAYIPKRALLIK_API AKKCampfire : public AActor
{
	GENERATED_BODY()

public:
	AKKCampfire();
	virtual void Tick(float Dt) override;

protected:
	virtual void BeginPlay() override;

	UPROPERTY() TObjectPtr<USceneComponent> Root;
	UPROPERTY() TObjectPtr<UPointLightComponent> Light;
	UPROPERTY() TObjectPtr<UStaticMeshComponent> Flame;
	UPROPERTY() TObjectPtr<UStaticMeshComponent> FlameInner;

	void BuildVisual();
	float Phase = 0.f;
};
