// KKHUD.h — HUD aktörü: yerel oyuncuya widget bağlar. [TKT-F0-022]
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/HUD.h"
#include "KKHUD.generated.h"

class UKKHUDWidget;

UCLASS()
class KAYIPKRALLIK_API AKKHUD : public AHUD
{
	GENERATED_BODY()

protected:
	virtual void BeginPlay() override;

	UPROPERTY() TObjectPtr<UKKHUDWidget> Widget;
};
