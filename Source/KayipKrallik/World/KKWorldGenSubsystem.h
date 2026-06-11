// KKWorldGenSubsystem.h — Deterministik dünya üretimi: KO hash2i/vnoise/fbm/campD BİREBİR portu. [TKT-F0-008]
// Determinizm = plan 102: arazi ağdan TAŞINMAZ, seed paylaşılır, herkes aynı dünyayı üretir.
// Bu yüzden FMath::PerlinNoise yerine kendi tamsayı-hash gürültümüz: platformlar arası bit-eş sonuç.
#pragma once

#include "CoreMinimal.h"
#include "Subsystems/WorldSubsystem.h"
#include "World/KKWorldTypes.h"
#include "KKWorldGenSubsystem.generated.h"

USTRUCT()
struct FKKResourceSpec
{
	GENERATED_BODY()
	EKKResource Type = EKKResource::None;
	int32 Variant = 0;
	FVector2D JitterUU = FVector2D::ZeroVector; // karo içi sapma (dünya birimi)
};

UCLASS()
class KAYIPKRALLIK_API UKKWorldGenSubsystem : public UWorldSubsystem
{
	GENERATED_BODY()

public:
	static constexpr float TileSize = 100.f;     // 1 karo = 1 m (KO TS=32px'in dünya eşleniği)
	static constexpr int32 ChunkTiles = 16;      // KO CSZ = 16

	void InitSeed(int32 InSeed);
	int32 GetSeed() const { return Seed; }

	// ---- KO portu (aritmetik birebir) ----
	double Hash01(int32 X, int32 Y, int32 Salt) const;          // hash2i
	double VNoise(double X, double Y, int32 Salt) const;        // vnoise
	double FBM(double X, double Y, int32 Salt, int32 Oct) const;// fbm

	/** 28x28 bölge kampı; yoksa false. Merkez karo koordinatı döner. */
	bool  CampInfo(int32 RegionX, int32 RegionY, FIntPoint& OutCenter) const;
	double CampDist(int32 TX, int32 TY) const;                  // campD (kamp yoksa 99)

	EKKTile GetTile(int32 TX, int32 TY) const;                  // genTile
	FKKResourceSpec GetResourceAt(int32 TX, int32 TY) const;    // build() obje kuralları

	bool IsBlockedTile(EKKTile T) const { return T == EKKTile::Deep || T == EKKTile::Water; }

	/** Başlangıç kampı (0,0 bölgesi her zaman var) merkez karosu. */
	FIntPoint FindStartCampTile() const;

	// ---- Hasat kalıcılığı (KO World.harvest eşleniği) ----
	static int64 MakeNodeKey(int32 TX, int32 TY) { return (int64(TY) << 32) | int64(uint32(TX)); }
	void   MarkHarvested(int64 Key, float RespawnAtWorldTime);
	bool   IsHarvested(int64 Key, float NowWorldTime) const;
	void   ExportHarvested(TArray<int64>& OutKeys, TArray<float>& OutTimes) const;
	void   ImportHarvested(const TArray<int64>& Keys, const TArray<float>& Times);

	// ---- Yardımcılar ----
	FVector TileToWorld(int32 TX, int32 TY, float Z = 0.f) const
	{
		return FVector((TX + 0.5f) * TileSize, (TY + 0.5f) * TileSize, Z);
	}
	FIntPoint WorldToTile(const FVector& W) const
	{
		return FIntPoint(FMath::FloorToInt32(W.X / TileSize), FMath::FloorToInt32(W.Y / TileSize));
	}

private:
	int32 Seed = 12345;
	mutable TMap<FIntPoint, FIntPoint> CampCache;   // bölge -> merkez
	mutable TSet<FIntPoint> CampNone;               // bölge kampsız
	TMap<int64, float> Harvested;                   // key -> respawn world time
};
