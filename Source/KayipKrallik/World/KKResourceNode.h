// KKResourceNode.h — Ağaç/Kaya/Çalı: etkileşimli kaynak düğümü (replike). [TKT-F0-009]
// Görsel motor temel şekillerinden koddan kurulur: gün 1'de sıfır asset ile çalışan dünya.
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "World/KKWorldTypes.h"
#include "KKResourceNode.generated.h"

class UStaticMeshComponent;
class AKKPlayerCharacter;

UCLASS()
class KAYIPKRALLIK_API AKKResourceNode : public AActor
{
	GENERATED_BODY()

public:
	AKKResourceNode();

	/** Yalnız sunucuda, streamer tarafından. */
	void InitNode(EKKResource InType, FIntPoint InTile, int32 InVariant, const FVector2D& JitterUU);

	/** Sunucu: bir vuruş/etkileşim. true = kaynak verildi. */
	bool ServerHarvest(AActor* Harvester); // oyuncu VEYA köylü — envanteri olan herkes (tek hasat yolu)

	EKKResource GetResourceType() const { return Type; }
	bool IsDepleted() const { return bDepleted; }

	virtual void Tick(float DeltaSeconds) override;
	virtual void GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const override;

protected:
	virtual void BeginPlay() override;

	UPROPERTY(Replicated) EKKResource Type = EKKResource::Tree;
	UPROPERTY(Replicated) int32 Variant = 0;
	UPROPERTY(Replicated) FIntPoint Tile = FIntPoint::ZeroValue;
	UPROPERTY(Replicated) int32 HitsLeft = 3;
	UPROPERTY(ReplicatedUsing = OnRep_Depleted) bool bDepleted = false;

	UFUNCTION() void OnRep_Depleted();

	UFUNCTION(NetMulticast, Unreliable) void Multicast_HitFX();
	void Multicast_HitFX_Implementation();

	UPROPERTY() TObjectPtr<USceneComponent> Root;
	UPROPERTY() TArray<TObjectPtr<UStaticMeshComponent>> Parts;
	UPROPERTY() TArray<TObjectPtr<UStaticMeshComponent>> BerryParts;

	float ShakeT = 0.f;
	float RespawnCheckAcc = 0.f;

	void BuildVisual();
	UStaticMeshComponent* AddPart(const TCHAR* MeshPath, const FLinearColor& Color,
	                              const FVector& RelLoc, const FVector& Scale,
	                              const FRotator& RelRot = FRotator::ZeroRotator, bool bCollide = true);
	void SetDepletedVisual(bool bHidden);
	float RespawnDelay() const;
	int64 NodeKey() const;
};
