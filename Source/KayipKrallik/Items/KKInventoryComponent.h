// KKInventoryComponent.h — Replike envanter (yalnız sunucu yazar). [TKT-F0-014]
#pragma once

#include "CoreMinimal.h"
#include "Components/ActorComponent.h"
#include "KKInventoryComponent.generated.h"

USTRUCT(BlueprintType)
struct FKKItemStack
{
	GENERATED_BODY()
	UPROPERTY(BlueprintReadOnly, Category="KK") FName Id;
	UPROPERTY(BlueprintReadOnly, Category="KK") int32 Count = 0;
};

UCLASS(ClassGroup=(KK), meta=(BlueprintSpawnableComponent))
class KAYIPKRALLIK_API UKKInventoryComponent : public UActorComponent
{
	GENERATED_BODY()

public:
	UKKInventoryComponent();

	/** Sunucu. */
	void AddItem(FName Id, int32 Count);
	/** Sunucu. true = düşüldü. */
	bool ConsumeItem(FName Id, int32 Count);

	UFUNCTION(BlueprintPure, Category="KK")
	int32 GetCount(FName Id) const;

	void ExportTo(TMap<FName, int32>& Out) const;
	/** Sunucu (kayıttan yükleme). */
	void ImportFrom(const TMap<FName, int32>& In);

	// ---- Yağma dili (Faz 1 çok oyunculu kuralı: ölünce eşya katile gider) ----
	/** Sunucu: tüm envanteri Out'a boşaltır (kaynak boşalır, değişim yayınlanır). */
	void TakeAllInto(TMap<FName, int32>& Out);
	/** Sunucu: haritayı mevcut envanterin ÜZERİNE ekler (ImportFrom'un aksine silmez). */
	void AddMap(const TMap<FName, int32>& In);
	bool IsEmpty() const { return Items.Num() == 0; }

	virtual void GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const override;

protected:
	UPROPERTY(ReplicatedUsing = OnRep_Items)
	TArray<FKKItemStack> Items;

	UFUNCTION() void OnRep_Items();
	void NotifyChanged();
};
