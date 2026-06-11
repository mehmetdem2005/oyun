// KKBuildTypes.h — İnşa edilebilir yapı sözlüğü (plan 7.2 Faz 1: İlk Kale MVP). [TKT-F1-001]
// Anahtar paketleme WorldGen MakeNodeKey ile AYNI düzen: (int64(TY)<<32)|uint32(TX).
#pragma once

#include "CoreMinimal.h"
#include "KKBuildTypes.generated.h"

UENUM(BlueprintType)
enum class EKKBuildType : uint8
{
	None UMETA(Hidden),
	Wall,   // Taş duvar — kale çevresinin ana hücresi
	Door,     // Ahşap kapı — oyuncu geçer, gölge geçemez (kapalıyken)
	Ballista, // Otomatik savunma — geceleri en yakın gölgeye cıvata atar
};

/** Tek kalemlik inşa maliyeti. */
struct FKKBuildCost
{
	FName Item1;  int32 Count1 = 0;
	FName Item2;  int32 Count2 = 0;
};

namespace KKBuild
{
	/** WorldGen düğüm anahtarı ile birebir aynı paketleme — kayıt formatı ortak dili. */
	FORCEINLINE int64 MakeKey(int32 TX, int32 TY)
	{
		return (int64(TY) << 32) | int64(uint32(TX));
	}
	FORCEINLINE FIntPoint KeyToTile(int64 Key)
	{
		return FIntPoint(int32(uint32(Key & 0xFFFFFFFF)), int32(Key >> 32));
	}

	/** Maliyet tablosu. DÜRÜST NOT: Faz 1 sonunda DT_Recipes DataTable'a taşınacak;
	 *  başlangıç diliminde tek kaynak burası ki sunucu doğrulaması ve UI aynı sayıyı görsün. */
	FORCEINLINE FKKBuildCost GetCost(EKKBuildType T)
	{
		FKKBuildCost C;
		switch (T)
		{
		case EKKBuildType::Wall: C.Item1 = FName("wood");  C.Count1 = 4; break;
		case EKKBuildType::Door:     C.Item1 = FName("wood");  C.Count1 = 6;
		                             C.Item2 = FName("stone"); C.Count2 = 1; break;
		case EKKBuildType::Ballista: C.Item1 = FName("wood");  C.Count1 = 8;
		                             C.Item2 = FName("stone"); C.Count2 = 4; break;
		default: break;
		}
		return C;
	}

	FORCEINLINE FText DisplayName(EKKBuildType T)
	{
		switch (T)
		{
		case EKKBuildType::Wall: return NSLOCTEXT("KK", "BuildWall", "Taş Duvar");
		case EKKBuildType::Door:     return NSLOCTEXT("KK", "BuildDoor", "Ahşap Kapı");
		case EKKBuildType::Ballista: return NSLOCTEXT("KK", "BuildBallista", "Balista");
		default:                 return FText::GetEmpty();
		}
	}
}
