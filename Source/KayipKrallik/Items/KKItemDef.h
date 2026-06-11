// KKItemDef.h — Eşya tanımı (PrimaryDataAsset): veri editörden, kod tipten bağımsız. [TKT-F0-013]
#pragma once

#include "CoreMinimal.h"
#include "Engine/DataAsset.h"
#include "KKItemDef.generated.h"

UCLASS(BlueprintType)
class KAYIPKRALLIK_API UKKItemDef : public UPrimaryDataAsset
{
	GENERATED_BODY()

public:
	UPROPERTY(EditDefaultsOnly, BlueprintReadOnly, Category="KK") FName ItemId;
	UPROPERTY(EditDefaultsOnly, BlueprintReadOnly, Category="KK") FText DisplayName;
	UPROPERTY(EditDefaultsOnly, BlueprintReadOnly, Category="KK") FLinearColor Color = FLinearColor::White;
	UPROPERTY(EditDefaultsOnly, BlueprintReadOnly, Category="KK") int32 MaxStack = 99;

	virtual FPrimaryAssetId GetPrimaryAssetId() const override
	{
		return FPrimaryAssetId(TEXT("KKItem"), GetFName());
	}
};
