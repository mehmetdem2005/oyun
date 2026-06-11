// KKGameInstance.h — Oturum kökü: ses ana seviyesi + başlangıç günlüğü. [TKT-F0-001]
#pragma once

#include "CoreMinimal.h"
#include "Engine/GameInstance.h"
#include "KKGameInstance.generated.h"

UCLASS()
class KAYIPKRALLIK_API UKKGameInstance : public UGameInstance
{
	GENERATED_BODY()

public:
	virtual void Init() override;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category="KK|Audio", meta=(ClampMin="0.0", ClampMax="1.0"))
	float MasterVolume = 0.5f;
};
