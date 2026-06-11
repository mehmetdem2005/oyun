// KKRecipeRow.h — Üretim tarifi DataTable satırı (Data/DT_Recipes.csv ile eşleşir). [TKT-F0-013]
#pragma once

#include "CoreMinimal.h"
#include "Engine/DataTable.h"
#include "KKRecipeRow.generated.h"

USTRUCT(BlueprintType)
struct FKKRecipeRow : public FTableRowBase
{
	GENERATED_BODY()

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category="KK") FName ResultItem;
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category="KK") int32 ResultCount = 1;
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category="KK") FName Cost1Item;
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category="KK") int32 Cost1Count = 0;
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category="KK") FName Cost2Item;
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category="KK") int32 Cost2Count = 0;
};
