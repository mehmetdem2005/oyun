// KKGameMode.cpp
#include "Player/KKGameMode.h"
#include "Player/KKPlayerCharacter.h"
#include "Player/KKPlayerController.h"
#include "Player/KKGameState.h"
#include "World/KKWorldGenSubsystem.h"
#include "World/KKWorldStreamer.h"
#include "World/KKBuildGridSubsystem.h"
#include "World/KKHeartStone.h"
#include "World/KKVillager.h"
#include "World/KKLootBag.h"
#include "Enemy/KKEnemySpawner.h"
#include "World/KKCritterSpawner.h"
#include "Core/KKSaveSubsystem.h"
#include "Core/KKSaveGame.h"
#include "Core/KKTimeOfDaySubsystem.h"
#include "Core/KKMessageBusSubsystem.h"
#include "Core/KKGameplayTags.h"
#include "Core/KKGameInstance.h"
#include "Items/KKInventoryComponent.h"
#include "UI/KKHUD.h"
#include "KayipKrallik.h"

#include "Engine/DirectionalLight.h"
#include "Engine/ExponentialHeightFog.h"
#include "Engine/PostProcessVolume.h"
#include "Components/DirectionalLightComponent.h"
#include "TimerManager.h"

AKKGameMode::AKKGameMode()
{
	DefaultPawnClass      = AKKPlayerCharacter::StaticClass();
	PlayerControllerClass = AKKPlayerController::StaticClass();
	GameStateClass        = AKKGameState::StaticClass();
	HUDClass              = AKKHUD::StaticClass();
}

void AKKGameMode::StartPlay()
{
	SetupWorld();       // ÖNCE dünya: oyuncu doğmadan zemin hazır olmalı
	Super::StartPlay();
}

void AKKGameMode::SetupWorld()
{
	UWorld* W = GetWorld();
	if (!W) return;

	// 1) Kayıt + seed
	UKKSaveSubsystem* SaveSub = GetGameInstance()->GetSubsystem<UKKSaveSubsystem>();
	PendingSave = SaveSub ? SaveSub->LoadMigrated() : nullptr;
	const int32 Seed = PendingSave ? PendingSave->WorldSeed : int32(FPlatformTime::Cycles() & 0x7fffffff);

	if (AKKGameState* GS = GetGameState<AKKGameState>()) GS->WorldSeed = Seed;

	UKKWorldGenSubsystem* Gen = W->GetSubsystem<UKKWorldGenSubsystem>();
	Gen->InitSeed(Seed);
	if (PendingSave) Gen->ImportHarvested(PendingSave->HarvestedKeys, PendingSave->HarvestedRespawnAt);
	if (PendingSave)
	{
		if (UKKBuildGridSubsystem* Grid = W->GetSubsystem<UKKBuildGridSubsystem>())
		{
			Grid->ImportBuilds(W, PendingSave->BuildKeys, PendingSave->BuildTypes, PendingSave->BuildFlags);
		}
	}

	UKKTimeOfDaySubsystem* Time = W->GetSubsystem<UKKTimeOfDaySubsystem>();
	Time->SetTime(PendingSave ? PendingSave->TimeSec : 0.f, PendingSave ? PendingSave->Day : 1);

	// 2) Atmosfer: güneş + sis + post (SANAT-YONU 3.x değerleri)
	ADirectionalLight* Sun = W->SpawnActor<ADirectionalLight>(FVector::ZeroVector, FRotator(-58.f, 35.f, 0.f));
	if (Sun)
	{
		if (UDirectionalLightComponent* L = Sun->FindComponentByClass<UDirectionalLightComponent>())
		{
			L->SetMobility(EComponentMobility::Movable);
			L->SetCastShadows(false); // mobil bütçe 6.3
			L->SetIntensity(5.5f);
		}
		Time->RegisterSun(Sun);
	}
	if (AExponentialHeightFog* Fog = W->SpawnActor<AExponentialHeightFog>(FVector(0, 0, -200.f), FRotator::ZeroRotator))
	{
		Time->RegisterFog(Fog);
	}
	if (APostProcessVolume* PP = W->SpawnActor<APostProcessVolume>())
	{
		PP->bUnbound = true;
		PP->Settings.bOverride_VignetteIntensity = true;  PP->Settings.VignetteIntensity = 0.35f;
		PP->Settings.bOverride_BloomIntensity = true;     PP->Settings.BloomIntensity = 0.55f;
		PP->Settings.bOverride_FilmGrainIntensity = true; PP->Settings.FilmGrainIntensity = 0.12f;
		PP->Settings.bOverride_ColorSaturation = true;    PP->Settings.ColorSaturation = FVector4(1.06f, 1.06f, 1.06f, 1.f);
	}

	// 3) Dünya akışı: başlangıç kampı çevresi SENKRON kurulur (boşluğa düşme yok)
	Streamer = W->SpawnActor<AKKWorldStreamer>();
	const FIntPoint Camp = Gen->FindStartCampTile();
	StartCampWorld = Gen->TileToWorld(Camp.X, Camp.Y, 0.f);
	if (Streamer) Streamer->ForceBuildAround(StartCampWorld, 1);

	// 3.5) Kalp Taşı: kampın iki karo doğusu — kamp düzlüğü garantili (campD<=3.3 bölgesi).
	// Korunacak ŞEY olmadan kuşatmanın anlamı yok; dikey dilimin kalbi kelimenin tam anlamıyla budur.
	{
		const FIntPoint HeartTile(Camp.X + 2, Camp.Y);
		FActorSpawnParameters HP_; HP_.SpawnCollisionHandlingOverride = ESpawnActorCollisionHandlingMethod::AlwaysSpawn;
		W->SpawnActor<AKKHeartStone>(AKKHeartStone::StaticClass(),
			Gen->TileToWorld(HeartTile.X, HeartTile.Y, 0.f), FRotator::ZeroRotator, HP_);
		if (UKKBuildGridSubsystem* Grid = W->GetSubsystem<UKKBuildGridSubsystem>())
		{
			Grid->AddReservedTile(HeartTile.X, HeartTile.Y); // kalbe duvar örülmez
			Grid->AddReservedTile(Camp.X, Camp.Y);           // kamp ateşi karosu da kutsal
		}
	}

	// 3.6) Ayla (Faz 1 tek yurttaş): kafes konumu seed'den DETERMİNİSTİK — herkes aynı dünyayı görür.
	// VillagerState: 0 kafes kur · 1 kurtarılmış başlat (kalp yanı) · 2 doğurma (isimli ölüm kalıcı).
	{
		const uint8 VS = PendingSave ? PendingSave->VillagerState : 0;
		if (VS != 2)
		{
			FVector SpawnLoc;
			if (VS == 1)
			{
				SpawnLoc = Gen->TileToWorld(Camp.X + 1, Camp.Y - 1, 100.f); // kalbin dibinde, işe hazır
			}
			else
			{
				// Kafes: kamptan 7 karo, yönü Hash01(0,0,900); bloklu karoya denk gelirse içeri çek (6 deneme).
				const double A = Gen->Hash01(0, 0, 900) * 2.0 * PI;
				FIntPoint T(Camp.X + FMath::RoundToInt(FMath::Cos(A) * 7.0), Camp.Y + FMath::RoundToInt(FMath::Sin(A) * 7.0));
				for (int32 Try = 0; Try < 6 && Gen->IsBlockedTile(Gen->GetTile(T.X, T.Y)); ++Try)
				{
					T.X += (Camp.X > T.X) ? 1 : -1; // kampa doğru bir adım
					T.Y += (Camp.Y > T.Y) ? 1 : -1;
				}
				SpawnLoc = Gen->TileToWorld(T.X, T.Y, 100.f);
				if (Streamer) Streamer->ForceBuildAround(SpawnLoc, 1); // kafes boşlukta süzülmesin
			}
			FActorSpawnParameters VP; VP.SpawnCollisionHandlingOverride = ESpawnActorCollisionHandlingMethod::AdjustIfPossibleButAlwaysSpawn;
			if (AKKVillager* V = W->SpawnActor<AKKVillager>(AKKVillager::StaticClass(), SpawnLoc, FRotator::ZeroRotator, VP))
			{
				V->InitFromSave(VS == 1);
			}
		}
	}

	// 3.7) Yağma keseleri: "eşya asla buharlaşmaz" sözünün kayıt ayağı.
	if (PendingSave)
	{
		int32 Cursor = 0;
		const int32 NBags = FMath::Min(PendingSave->LootBagLocations.Num(), PendingSave->LootBagItemNum.Num());
		for (int32 i = 0; i < NBags; ++i)
		{
			TMap<FName, int32> L;
			const int32 N = PendingSave->LootBagItemNum[i];
			for (int32 k = 0; k < N && Cursor < PendingSave->LootIds.Num() && Cursor < PendingSave->LootCounts.Num(); ++k, ++Cursor)
			{
				L.Add(PendingSave->LootIds[Cursor], PendingSave->LootCounts[Cursor]);
			}
			FActorSpawnParameters BP; BP.SpawnCollisionHandlingOverride = ESpawnActorCollisionHandlingMethod::AdjustIfPossibleButAlwaysSpawn;
			if (AKKLootBag* Bag = W->SpawnActor<AKKLootBag>(AKKLootBag::StaticClass(), PendingSave->LootBagLocations[i], FRotator::ZeroRotator, BP))
			{
				Bag->InitLoot(L);
			}
		}
	}

	// 4) Gölge yöneticisi + fauna nüfus müdürü
	W->SpawnActor<AKKEnemySpawner>();
	W->SpawnActor<AKKCritterSpawner>();

	// 5) Gün dönümünde otokayıt (mesaj otobüsü — sistemler birbirini tanımaz)
	if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(W))
	{
		TWeakObjectPtr<UWorld> WeakW = W;
		Bus->Subscribe(KKTags::Time_DayChanged, [WeakW](const FKKMessage& M)
		{
			if (UWorld* World = WeakW.Get())
			{
				if (UKKSaveSubsystem* S = World->GetGameInstance()->GetSubsystem<UKKSaveSubsystem>())
				{
					S->SaveWorld(World);
				}
				// Dikey dilim zaferi: Gün 10'a ulaş (plan Faz 1 çıkış kriteri).
				// Tek seferlik: HUD mesajı + tag; oyun durmaz — "10'dan sonrası senin krallığın".
				if (M.IntValue == 10)
				{
					if (UKKMessageBusSubsystem* B = UKKMessageBusSubsystem::Get(World))
					{
						B->BroadcastTag(KKTags::Game_Victory, M.IntValue);
					}
				}
			}
		});
	}

	UE_LOG(LogKK, Log, TEXT("[GameMode] Dunya kuruldu. Seed=%d, Kamp=(%d,%d), Kayit=%s"),
		Seed, Camp.X, Camp.Y, PendingSave ? TEXT("YUKLENDI") : TEXT("YENI"));
}

void AKKGameMode::HandleStartingNewPlayer_Implementation(APlayerController* NewPlayer)
{
	Super::HandleStartingNewPlayer_Implementation(NewPlayer);
	// Pawn bir sonraki tick'te garanti var: o zaman yerleştir.
	TWeakObjectPtr<APlayerController> WeakPC = NewPlayer;
	GetWorldTimerManager().SetTimerForNextTick(FTimerDelegate::CreateWeakLambda(this, [this, WeakPC]()
	{
		if (APlayerController* PC = WeakPC.Get()) PlacePlayer(PC);
	}));
}

void AKKGameMode::PlacePlayer(APlayerController* PC)
{
	AKKPlayerCharacter* Char = PC ? Cast<AKKPlayerCharacter>(PC->GetPawn()) : nullptr;
	if (!Char) return;

	FVector Target = StartCampWorld + FVector(0, 0, 120.f);
	if (PendingSave && !PendingSave->PlayerLocation.IsNearlyZero())
	{
		Target = PendingSave->PlayerLocation + FVector(0, 0, 30.f);
		if (Streamer) Streamer->ForceBuildAround(Target, 1);
		Char->ApplyLoadedState(PendingSave->Health, PendingSave->Stamina, PendingSave->Hunger);
		if (UKKInventoryComponent* Inv = Char->FindComponentByClass<UKKInventoryComponent>())
		{
			Inv->ImportFrom(PendingSave->Inventory);
		}
	}
	Char->SetActorLocation(Target);
}
