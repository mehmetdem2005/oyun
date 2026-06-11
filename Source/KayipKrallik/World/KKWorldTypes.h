// KKWorldTypes.h — Karo/kaynak tipleri + KO paleti (SANAT-YONU.md ile senkron). [TKT-F0-007]
#pragma once

#include "CoreMinimal.h"
#include "KKWorldTypes.generated.h"

UENUM(BlueprintType)
enum class EKKTile : uint8
{
	Deep, Water, Sand, Grass, DarkGrass, Path, Camp
};

UENUM(BlueprintType)
enum class EKKResource : uint8
{
	None, Tree, Rock, Bush
};

namespace KKPalette
{
	// Kayıp Orman'ın HEX paleti birebir (paintCell tablosu).
	inline FLinearColor Tile(EKKTile T)
	{
		switch (T)
		{
		case EKKTile::Deep:      return FLinearColor::FromSRGBColor(FColor::FromHex(TEXT("1d568f")));
		case EKKTile::Water:     return FLinearColor::FromSRGBColor(FColor::FromHex(TEXT("2e7cc4")));
		case EKKTile::Sand:      return FLinearColor::FromSRGBColor(FColor::FromHex(TEXT("e6d28a")));
		case EKKTile::Grass:     return FLinearColor::FromSRGBColor(FColor::FromHex(TEXT("4fae60")));
		case EKKTile::DarkGrass: return FLinearColor::FromSRGBColor(FColor::FromHex(TEXT("3d8c4e")));
		case EKKTile::Path:      return FLinearColor::FromSRGBColor(FColor::FromHex(TEXT("b08a58")));
		default:                 return FLinearColor::FromSRGBColor(FColor::FromHex(TEXT("c8a06d"))); // Camp
		}
	}

	inline FLinearColor Hex(const TCHAR* H) { return FLinearColor::FromSRGBColor(FColor::FromHex(H)); }
}
