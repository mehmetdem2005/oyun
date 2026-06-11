// KKBuildGridSubsystem.h — Karo-bazlı inşa ızgarası: doluluk + sunucu doğrulamalı yerleştirme. [TKT-F1-003]
// Doluluk haritası HER makinede tutulur: Buildable'lar BeginPlay/EndPlay ile yerel kaydolur,
// böylece istemci hayalet önizlemesi sunucuya sormadan doğru yeşil/kırmızı gösterir.
// Otorite işlemleri (Place/Remove) yalnız sunucuda çalışır.
#pragma once

#include "CoreMinimal.h"
#include "Subsystems/WorldSubsystem.h"
#include "World/KKBuildTypes.h"
#include "KKBuildGridSubsystem.generated.h"

class AKKBuildable;
class UKKInventoryComponent;

UCLASS()
class KAYIPKRALLIK_API UKKBuildGridSubsystem : public UWorldSubsystem
{
	GENERATED_BODY()

public:
	/** Her makine: bu karoya yerleştirilebilir mi? (su/derin değil + dolu değil + kaynak düğümü yok + kamp merkezi değil) */
	bool CanPlaceAt(int32 TX, int32 TY) const;

	/** SUNUCU: maliyeti düş, aktörü doğur, kaydet. Başarısızlıkta envantere dokunmaz. */
	AKKBuildable* ServerPlace(EKKBuildType Type, int32 TX, int32 TY, UKKInventoryComponent* PayFrom);

	/** SUNUCU: karodaki yapıyı kaldır (Faz 2 kuşatma hasarı buraya bağlanacak). */
	bool ServerRemoveAt(int32 TX, int32 TY);

	AKKBuildable* FindAt(int32 TX, int32 TY) const;

	/** GameMode kutsal karoları işaretler (Kalp Taşı vb.) — üzerine inşa edilemez. */
	void AddReservedTile(int32 TX, int32 TY) { Reserved.Add(KKBuild::MakeKey(TX, TY)); }

	// ---- Buildable yaşam döngüsü (aktör kendisi çağırır) ----
	void RegisterLocal(AKKBuildable* B);
	void UnregisterLocal(AKKBuildable* B);

	// ---- Kayıt köprüsü (KKSaveGame v3 paralel dizileri) ----
	void ExportBuilds(TArray<int64>& OutKeys, TArray<uint8>& OutTypes, TArray<uint8>& OutFlags) const;
	void ImportBuilds(UWorld* World, const TArray<int64>& Keys, const TArray<uint8>& Types, const TArray<uint8>& Flags);

private:
	/** Anahtar = KKBuild::MakeKey(TX,TY). Zayıf işaretçi: aktör yok olursa harita kendini temizler. */
	TMap<int64, TWeakObjectPtr<AKKBuildable>> Placed;
	TSet<int64> Reserved; // inşaya kapalı kutsal karolar
};
