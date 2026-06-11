// KKSaveSubsystem.cpp
#include "Core/KKSaveSubsystem.h"
#include "Core/KKSaveGame.h"
#include "Core/KKMessageBusSubsystem.h"
#include "Core/KKGameplayTags.h"
#include "Core/KKTimeOfDaySubsystem.h"
#include "World/KKWorldGenSubsystem.h"
#include "World/KKBuildGridSubsystem.h"
#include "World/KKLootBag.h"
#include "World/KKVillager.h"
#include "EngineUtils.h"
#include "Player/KKGameState.h"
#include "Player/KKPlayerCharacter.h"
#include "Items/KKInventoryComponent.h"
#include "Combat/KKAttributeSet.h"
#include "KayipKrallik.h"
#include "Kismet/GameplayStatics.h"

bool UKKSaveSubsystem::HasSave() const
{
	return UGameplayStatics::DoesSaveGameExist(SlotName(), 0);
}

UKKSaveGame* UKKSaveSubsystem::LoadMigrated()
{
	if (!HasSave()) return nullptr;
	UKKSaveGame* Save = Cast<UKKSaveGame>(UGameplayStatics::LoadGameFromSlot(SlotName(), 0));
	if (!Save)
	{
		UE_LOG(LogKK, Warning, TEXT("[Save] Slot okunamadi/bozuk; yeni oyun baslatilacak."));
		return nullptr;
	}
	MigrateToCurrent(Save);
	return Save;
}

void UKKSaveSubsystem::MigrateToCurrent(UKKSaveGame* Save) const
{
	if (!Save) return;
	while (Save->SaveVersion < CurrentVersion)
	{
		switch (Save->SaveVersion)
		{
		case 1:
			// v1 -> v2: v1'de Stamina alanı yoktu varsayalım; güvenli varsayılan bas.
			Save->Stamina = 100.f;
			Save->SaveVersion = 2;
			UE_LOG(LogKK, Log, TEXT("[Save] Migrasyon v1 -> v2 tamam."));
			break;
		case 2:
			// v2 -> v3: inşa dizileri eklendi; eski kayıtta yapı yok, boş dizi doğru durumdur.
			Save->SaveVersion = 3;
			UE_LOG(LogKK, Log, TEXT("[Save] Migrasyon v2 -> v3 tamam (insa sistemi)."));
			break;
		case 3:
			// v3 -> v4: kese dizileri + köylü bayrağı; boş/false doğru başlangıçtır.
			Save->SaveVersion = 4;
			UE_LOG(LogKK, Log, TEXT("[Save] Migrasyon v3 -> v4 tamam (yagma keseleri + koylu)."));
			break;
		default:
			// Bilinmeyen eski sürüm: ileri taşıyamayız; sürümü mühürle ve devam et.
			UE_LOG(LogKK, Warning, TEXT("[Save] Bilinmeyen surum %d; oldugu gibi kullaniliyor."), Save->SaveVersion);
			Save->SaveVersion = CurrentVersion;
			break;
		}
	}
}

bool UKKSaveSubsystem::SaveWorld(UWorld* World)
{
	if (!World || World->GetNetMode() == NM_Client) return false; // otorite disiplini

	UKKSaveGame* Save = Cast<UKKSaveGame>(UGameplayStatics::CreateSaveGameObject(UKKSaveGame::StaticClass()));
	if (!Save) return false;

	if (const AKKGameState* GS = World->GetGameState<AKKGameState>())
	{
		Save->WorldSeed = GS->WorldSeed;
		Save->Day       = GS->Day;
	}
	if (const UKKTimeOfDaySubsystem* Time = World->GetSubsystem<UKKTimeOfDaySubsystem>())
	{
		Save->TimeSec = Time->GetTimeSec();
	}
	if (const UKKWorldGenSubsystem* Gen = World->GetSubsystem<UKKWorldGenSubsystem>())
	{
		Gen->ExportHarvested(Save->HarvestedKeys, Save->HarvestedRespawnAt);
	}
	if (const UKKBuildGridSubsystem* Grid = World->GetSubsystem<UKKBuildGridSubsystem>())
	{
		Grid->ExportBuilds(Save->BuildKeys, Save->BuildTypes, Save->BuildFlags);
	}
	// Keseler: dünyadaki her açık kese düz dizilere yazılır (sıra önemsiz).
	for (TActorIterator<AKKLootBag> It(World); It; ++It)
	{
		const TMap<FName, int32>& L = It->GetLoot();
		if (L.Num() == 0) continue;
		Save->LootBagLocations.Add(It->GetActorLocation());
		Save->LootBagItemNum.Add(L.Num());
		for (const auto& P : L) { Save->LootIds.Add(P.Key); Save->LootCounts.Add(P.Value); }
	}
	// Köylü: dünyada yoksa ölmüştür (SaveWorld yalnız oyun içinde çağrılır; Ayla ya sahnede ya toprakta).
	Save->VillagerState = 2;
	for (TActorIterator<AKKVillager> It(World); It; ++It)
	{
		Save->VillagerState = It->IsRescued() ? 1 : 0;
		break; // Faz 1: tek yurttaş
	}
	if (const AKKPlayerCharacter* PC = Cast<AKKPlayerCharacter>(UGameplayStatics::GetPlayerPawn(World, 0)))
	{
		Save->PlayerLocation = PC->GetActorLocation();
		Save->Health  = PC->GetAttrValue(EKKAttr::Health);
		Save->Stamina = PC->GetAttrValue(EKKAttr::Stamina);
		Save->Hunger  = PC->GetAttrValue(EKKAttr::Hunger);
		if (const UKKInventoryComponent* Inv = PC->FindComponentByClass<UKKInventoryComponent>())
		{
			Inv->ExportTo(Save->Inventory);
		}
	}

	const bool bOk = UGameplayStatics::SaveGameToSlot(Save, SlotName(), 0);
	if (bOk)
	{
		if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(World))
		{
			Bus->BroadcastTag(KKTags::Save_Completed, Save->Day);
		}
		UE_LOG(LogKK, Log, TEXT("[Save] Gun %d kaydedildi."), Save->Day);
	}
	return bOk;
}

void UKKSaveSubsystem::DeleteSave()
{
	if (HasSave()) UGameplayStatics::DeleteGameInSlot(SlotName(), 0);
}
