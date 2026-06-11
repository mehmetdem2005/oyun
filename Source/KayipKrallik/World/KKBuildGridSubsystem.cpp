// KKBuildGridSubsystem.cpp — İnşa ızgarası: tek doğruluk kaynağı sunucu, görüş her makinede. [TKT-F1-003]
#include "World/KKBuildGridSubsystem.h"
#include "World/KKBuildable.h"
#include "World/KKBallista.h"
#include "World/KKWorldGenSubsystem.h"
#include "Items/KKInventoryComponent.h"
#include "Core/KKMessageBusSubsystem.h"
#include "Core/KKGameplayTags.h"
#include "KayipKrallik.h"
#include "Engine/World.h"

bool UKKBuildGridSubsystem::CanPlaceAt(int32 TX, int32 TY) const
{
	const UWorld* W = GetWorld();
	const UKKWorldGenSubsystem* Gen = W ? W->GetSubsystem<UKKWorldGenSubsystem>() : nullptr;
	if (!Gen) return false;

	// 1) Zemin uygun mu? (su/derin yasak — KO solid karo mantığının inşa yüzü)
	if (Gen->IsBlockedTile(Gen->GetTile(TX, TY))) return false;

	// 2) Karo dolu ya da kutsal mı? (Kalp Taşı, kamp ateşi)
	const int64 Key = KKBuild::MakeKey(TX, TY);
	if (Placed.Contains(Key) || Reserved.Contains(Key)) return false;

	// 3) Üzerinde DURAN kaynak düğümü var mı? (hasat edilmişse karo serbest)
	const FKKResourceSpec Spec = Gen->GetResourceAt(TX, TY);
	if (Spec.Type != EKKResource::None)
	{
		const float Now = W->GetTimeSeconds();
		if (!Gen->IsHarvested(UKKWorldGenSubsystem::MakeNodeKey(TX, TY), Now)) return false;
	}

	// 4) Kamp ateşinin karosu kutsaldır — başlangıç sığınağı kapatılamaz.
	if (Gen->FindStartCampTile() == FIntPoint(TX, TY)) return false;

	return true;
}

AKKBuildable* UKKBuildGridSubsystem::ServerPlace(EKKBuildType Type, int32 TX, int32 TY, UKKInventoryComponent* PayFrom)
{
	UWorld* W = GetWorld();
	if (!W || W->GetNetMode() == NM_Client) return nullptr; // otorite disiplini
	if (Type == EKKBuildType::None || !CanPlaceAt(TX, TY)) return nullptr;

	// Maliyet: ÖNCE karşılanabilirlik, SONRA düşüm — yarım ödeme asla olmaz.
	const FKKBuildCost Cost = KKBuild::GetCost(Type);
	if (PayFrom)
	{
		if (Cost.Count1 > 0 && PayFrom->GetCount(Cost.Item1) < Cost.Count1) return nullptr;
		if (Cost.Count2 > 0 && PayFrom->GetCount(Cost.Item2) < Cost.Count2) return nullptr;
		if (Cost.Count1 > 0) PayFrom->ConsumeItem(Cost.Item1, Cost.Count1);
		if (Cost.Count2 > 0) PayFrom->ConsumeItem(Cost.Item2, Cost.Count2);
	}

	const UKKWorldGenSubsystem* Gen = W->GetSubsystem<UKKWorldGenSubsystem>();
	FActorSpawnParameters P;
	P.SpawnCollisionHandlingOverride = ESpawnActorCollisionHandlingMethod::AlwaysSpawn;
	// Tip -> sınıf: davranışlı yapılar (Balista) kendi alt sınıfında yaşar; kayıt formatı değişmez.
	UClass* Cls = (Type == EKKBuildType::Ballista) ? AKKBallista::StaticClass() : AKKBuildable::StaticClass();
	AKKBuildable* B = W->SpawnActor<AKKBuildable>(Cls, FTransform::Identity, P);
	if (!B) return nullptr;

	B->InitBuild(Type, FIntPoint(TX, TY), Gen->TileToWorld(TX, TY, 0.f));
	// RegisterLocal artık InitBuild içinde koşar (Tile atandıktan sonra); harita güncel.

	if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(W))
	{
		FKKMessage M; M.Tag = KKTags::Build_Placed; M.IntValue = int32(Type); M.Location = B->GetActorLocation(); M.Source = B;
		Bus->Broadcast(M);
	}
	UE_LOG(LogKK, Log, TEXT("[Build] %s yerlestirildi (%d,%d)."), *KKBuild::DisplayName(Type).ToString(), TX, TY);
	return B;
}

bool UKKBuildGridSubsystem::ServerRemoveAt(int32 TX, int32 TY)
{
	UWorld* W = GetWorld();
	if (!W || W->GetNetMode() == NM_Client) return false;
	if (AKKBuildable* B = FindAt(TX, TY))
	{
		B->Destroy(); // EndPlay -> UnregisterLocal haritayı temizler
		return true;
	}
	return false;
}

AKKBuildable* UKKBuildGridSubsystem::FindAt(int32 TX, int32 TY) const
{
	if (const TWeakObjectPtr<AKKBuildable>* Found = Placed.Find(KKBuild::MakeKey(TX, TY)))
	{
		return Found->Get();
	}
	return nullptr;
}

void UKKBuildGridSubsystem::RegisterLocal(AKKBuildable* B)
{
	if (B) Placed.Add(KKBuild::MakeKey(B->GetTile().X, B->GetTile().Y), B);
}

void UKKBuildGridSubsystem::UnregisterLocal(AKKBuildable* B)
{
	if (B) Placed.Remove(KKBuild::MakeKey(B->GetTile().X, B->GetTile().Y));
}

void UKKBuildGridSubsystem::ExportBuilds(TArray<int64>& OutKeys, TArray<uint8>& OutTypes, TArray<uint8>& OutFlags) const
{
	OutKeys.Reset(); OutTypes.Reset(); OutFlags.Reset();
	for (const auto& Pair : Placed)
	{
		const AKKBuildable* B = Pair.Value.Get();
		if (!B) continue;
		OutKeys.Add(Pair.Key);
		OutTypes.Add(uint8(B->GetBuildType()));
		OutFlags.Add(B->IsDoorOpen() ? 1 : 0); // bit0 = kapı açık
	}
}

void UKKBuildGridSubsystem::ImportBuilds(UWorld* World, const TArray<int64>& Keys, const TArray<uint8>& Types, const TArray<uint8>& Flags)
{
	if (!World || World->GetNetMode() == NM_Client) return;
	const UKKWorldGenSubsystem* Gen = World->GetSubsystem<UKKWorldGenSubsystem>();
	if (!Gen) return;

	const int32 N = FMath::Min3(Keys.Num(), Types.Num(), Flags.Num());
	for (int32 i = 0; i < N; ++i)
	{
		const FIntPoint T = KKBuild::KeyToTile(Keys[i]);
		const EKKBuildType BT = EKKBuildType(Types[i]);
		if (BT == EKKBuildType::None || Placed.Contains(Keys[i])) continue;

		FActorSpawnParameters P;
		P.SpawnCollisionHandlingOverride = ESpawnActorCollisionHandlingMethod::AlwaysSpawn;
		UClass* Cls = (BT == EKKBuildType::Ballista) ? AKKBallista::StaticClass() : AKKBuildable::StaticClass();
		if (AKKBuildable* B = World->SpawnActor<AKKBuildable>(Cls, FTransform::Identity, P))
		{
			B->InitBuild(BT, T, Gen->TileToWorld(T.X, T.Y, 0.f));
			if (BT == EKKBuildType::Door && (Flags[i] & 1)) B->ServerToggleDoor(); // kayıtlı açık kapı
		}
	}
	UE_LOG(LogKK, Log, TEXT("[Build] %d yapi kayittan yuklendi."), N);
}
