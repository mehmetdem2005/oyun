// KKWorldStreamer.h — Chunk akışı: zemin/dekor HISM + kaynak düğümü yaşam döngüsü. [TKT-F0-011]
// Mimari not (plan 94/132): Faz 0'da kendi hafif streamer'ımız; Faz 1'de World Partition'a
// devredilecek arazi-dışı görevler buradan ayrışır. Bütçe: tick başına en çok 2 chunk inşası
// (6.3 "hitch'siz streaming" maddesi).
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "World/KKWorldTypes.h"
#include "KKWorldStreamer.generated.h"

class UHierarchicalInstancedStaticMeshComponent;
class UMaterialInstanceDynamic;
class AKKResourceNode;
class AKKCampfire;

USTRUCT()
struct FKKChunkVisual
{
	GENERATED_BODY()
	UPROPERTY() TArray<TObjectPtr<UHierarchicalInstancedStaticMeshComponent>> Comps;
	UPROPERTY() TArray<TObjectPtr<AActor>> OwnedActors; // sunucu: düğümler + kamp ateşleri
};

UCLASS()
class KAYIPKRALLIK_API AKKWorldStreamer : public AActor
{
	GENERATED_BODY()

public:
	AKKWorldStreamer();

	/** Başlangıç bölgesini senkron kur (oyuncu boşluğa düşmesin). */
	void ForceBuildAround(const FVector& WorldLoc, int32 Radius = 1);

	virtual void Tick(float Dt) override;

protected:
	virtual void BeginPlay() override;

	UPROPERTY(EditAnywhere, Category="KK|Stream") int32 LoadRadius = 2;   // 5x5 chunk
	UPROPERTY(EditAnywhere, Category="KK|Stream") int32 UnloadRadius = 3;
	UPROPERTY(EditAnywhere, Category="KK|Stream") int32 MaxBuildsPerTick = 2;

	UPROPERTY() TMap<FIntPoint, FKKChunkVisual> Loaded;
	TArray<FIntPoint> BuildQueue;
	float ScanAcc = 0.f;

	UPROPERTY() TObjectPtr<UStaticMesh> CubeMesh;
	UPROPERTY() TMap<uint8, TObjectPtr<UMaterialInstanceDynamic>> TileMIDs;
	UPROPERTY() TObjectPtr<UMaterialInstanceDynamic> TuftMID;
	UPROPERTY() TObjectPtr<UMaterialInstanceDynamic> FlowerGoldMID;
	UPROPERTY() TObjectPtr<UMaterialInstanceDynamic> FlowerPinkMID;

	void EnsureAssets();
	UMaterialInstanceDynamic* MakeMID(const FLinearColor& C);
	UHierarchicalInstancedStaticMeshComponent* MakeHISM(UMaterialInstanceDynamic* MID, bool bCollide);

	void ScanAndQueue();
	void BuildChunk(const FIntPoint& Chunk);
	void UnloadChunk(const FIntPoint& Chunk);
	FIntPoint PlayerChunk() const;
};
