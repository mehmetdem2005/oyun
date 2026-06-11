// KKCritterSpawner.cpp — Doğanın nüfus müdürü. [TKT-F1-019]
#include "World/KKCritterSpawner.h"
#include "World/KKCritter.h"
#include "World/KKWorldGenSubsystem.h"
#include "Core/KKMessageBusSubsystem.h"
#include "Core/KKGameplayTags.h"
#include "Core/KKTimeOfDaySubsystem.h"
#include "KayipKrallik.h"
#include "EngineUtils.h"
#include "Kismet/GameplayStatics.h"
#include "TimerManager.h"
#include "Engine/World.h"

AKKCritterSpawner::AKKCritterSpawner()
{
	PrimaryActorTick.bCanEverTick = false;
	bReplicates = false; // yalnız sunucu mantığı; hayvanlar kendileri replike
}

void AKKCritterSpawner::BeginPlay()
{
	Super::BeginPlay();
	if (!HasAuthority()) return;

	if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(this))
	{
		TWeakObjectPtr<AKKCritterSpawner> Weak = this;
		Bus->Subscribe(KKTags::Time_DawnStarted, [Weak](const FKKMessage&)
		{
			if (AKKCritterSpawner* S = Weak.Get()) S->OnDawn();
		});
		Bus->Subscribe(KKTags::Time_NightStarted, [Weak](const FKKMessage&)
		{
			if (AKKCritterSpawner* S = Weak.Get()) S->OnNight();
		});
		Bus->Subscribe(KKTags::Critter_Killed, [Weak](const FKKMessage&)
		{
			if (AKKCritterSpawner* S = Weak.Get()) S->HuntPressure++; // her av iz bırakır
		});
	}

	// Oyun gündüz başlar: ilk nüfusu hemen kur.
	GetWorldTimerManager().SetTimer(SustainTimer, this, &AKKCritterSpawner::SustainTick, 5.f, true, 1.f);
}

void AKKCritterSpawner::OnDawn()
{
	HuntPressure = FMath::Max(0, HuntPressure - 2); // doğa toparlar (155: "zaman toparlar")
	GetWorldTimerManager().SetTimer(SustainTimer, this, &AKKCritterSpawner::SustainTick, 5.f, true, 1.f);
}

void AKKCritterSpawner::OnNight()
{
	GetWorldTimerManager().ClearTimer(SustainTimer);
	for (TActorIterator<AKKCritter> It(GetWorld()); It; ++It)
	{
		It->Destroy(); // ürkekler ine çekilir — gece sahnesi gölgelere ait
	}
}

int32 AKKCritterSpawner::CountAliveNear() const
{
	APawn* P = UGameplayStatics::GetPlayerPawn(GetWorld(), 0);
	if (!P) return 0;
	int32 N = 0;
	for (TActorIterator<AKKCritter> It(GetWorld()); It; ++It)
	{
		if (It->IsKKAlive() &&
			FVector::DistSquared2D(P->GetActorLocation(), It->GetActorLocation()) < NearRadius * NearRadius) ++N;
	}
	return N;
}

void AKKCritterSpawner::SustainTick()
{
	APawn* P = UGameplayStatics::GetPlayerPawn(GetWorld(), 0);
	const UKKWorldGenSubsystem* Gen = GetWorld()->GetSubsystem<UKKWorldGenSubsystem>();
	if (!P || !Gen) return;

	// Av baskısı tavanı yer: 6 sağlıklı -> yoğun avda 1'e kadar düşer. Bölge "boşalmış" HİSSEDİLİR.
	const int32 Target = FMath::Clamp(BasePop - HuntPressure / 2, 1, BasePop);
	if (CountAliveNear() >= Target) return;

	// Geçerli karo bulana dek birkaç deneme (su/derine hayvan doğmaz).
	for (int32 Try = 0; Try < 5; ++Try)
	{
		const float A = FMath::FRandRange(0.f, 2.f * PI);
		const float D = FMath::FRandRange(SpawnMin, SpawnMax);
		const FVector Loc = P->GetActorLocation() + FVector(FMath::Cos(A) * D, FMath::Sin(A) * D, 90.f);
		const FIntPoint T = Gen->WorldToTile(Loc);
		if (Gen->IsBlockedTile(Gen->GetTile(T.X, T.Y))) continue;

		FActorSpawnParameters SP;
		SP.SpawnCollisionHandlingOverride = ESpawnActorCollisionHandlingMethod::AdjustIfPossibleButAlwaysSpawn;
		if (AKKCritter* C = GetWorld()->SpawnActor<AKKCritter>(AKKCritter::StaticClass(), Loc, FRotator(0, FMath::FRandRange(0.f, 360.f), 0), SP))
		{
			// %70 tavşan / %30 geyik — küçük av bol, büyük av ödül.
			C->InitKind(FMath::FRand() < 0.7f ? EKKCritterKind::Rabbit : EKKCritterKind::Deer);
		}
		return; // tik başına 1 doğum: nüfus damla damla dolar, ekran aniden kalabalıklaşmaz
	}
}
