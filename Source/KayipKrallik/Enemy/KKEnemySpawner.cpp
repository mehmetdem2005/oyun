// KKEnemySpawner.cpp
#include "Enemy/KKEnemySpawner.h"
#include "Enemy/KKShadowEnemy.h"
#include "World/KKHeartStone.h"
#include "Core/KKMessageBusSubsystem.h"
#include "Core/KKGameplayTags.h"
#include "KayipKrallik.h"
#include "EngineUtils.h"
#include "Kismet/GameplayStatics.h"
#include "TimerManager.h"

AKKEnemySpawner::AKKEnemySpawner()
{
	PrimaryActorTick.bCanEverTick = false;
	bReplicates = false; // yalnız sunucu mantığı; gölgeler kendileri replike
}

void AKKEnemySpawner::BeginPlay()
{
	Super::BeginPlay();
	if (!HasAuthority()) return;

	if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(this))
	{
		TWeakObjectPtr<AKKEnemySpawner> Weak = this;
		Bus->Subscribe(KKTags::Time_NightStarted, [Weak](const FKKMessage& M)
		{
			if (AKKEnemySpawner* S = Weak.Get()) S->OnNight(M.IntValue);
		});
		Bus->Subscribe(KKTags::Time_DawnStarted, [Weak](const FKKMessage&)
		{
			if (AKKEnemySpawner* S = Weak.Get()) S->OnDawn();
		});
	}
}

void AKKEnemySpawner::OnNight(int32 Day)
{
	CurrentDay = FMath::Max(1, Day);
	// İki katman (plan "basit dalga"):
	//  1) KUŞATMA PATLAMASI — gece başında Kalp Taşı çevresine 2+Gün gölge (1.2 sn arayla, halka düzeni)
	//  2) DAMLA BASKI — mevcut 6 sn'lik akış oyuncu çevresinden sızar (kanat baskısı)
	// Plan 43: her gece küçük baskın, HER 5. GECE BÜYÜK KUŞATMA (dalga x2, daha sık patlama).
	const bool bBigSiege = (CurrentDay % 5 == 0);
	WaveLeft = FMath::Min((2 + CurrentDay) * (bBigSiege ? 2 : 1), 24);
	GetWorldTimerManager().SetTimer(BurstTimer, this, &AKKEnemySpawner::SpawnBurstOne, bBigSiege ? 0.8f : 1.2f, true, 1.0f);
	GetWorldTimerManager().SetTimer(WaveTimer,  this, &AKKEnemySpawner::SpawnBatch,    6.f,  true, 4.f);
	if (bBigSiege)
	{
		if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(this))
		{
			Bus->BroadcastTag(KKTags::Night_BigSiege, CurrentDay); // HUD kırmızı bant basar
		}
	}
	UE_LOG(LogKK, Log, TEXT("[Enemy] Gece %d: %s — dalga %d golge."), CurrentDay,
		bBigSiege ? TEXT("BUYUK KUSATMA") : TEXT("kusatma"), WaveLeft);
}

void AKKEnemySpawner::SpawnBurstOne()
{
	if (WaveLeft <= 0) { GetWorldTimerManager().ClearTimer(BurstTimer); return; }

	// Dalga kalbe yürür: doğuş noktası kalp çevresinde halka — kuşatma OKUNUR olsun.
	FVector Center = FVector::ZeroVector;
	for (TActorIterator<AKKHeartStone> It(GetWorld()); It; ++It) { Center = It->GetActorLocation(); break; }
	if (Center.IsNearlyZero()) // kalp yoksa (test haritası) oyuncuya düş
	{
		if (APawn* P = UGameplayStatics::GetPlayerPawn(GetWorld(), 0)) Center = P->GetActorLocation();
		else return;
	}

	const float Ang  = FMath::FRandRange(0.f, 2.f * PI);
	const float Dist = FMath::FRandRange(1600.f, 2200.f);
	const FVector Loc = Center + FVector(FMath::Cos(Ang) * Dist, FMath::Sin(Ang) * Dist, 80.f);

	FActorSpawnParameters P;
	P.SpawnCollisionHandlingOverride = ESpawnActorCollisionHandlingMethod::AdjustIfPossibleButAlwaysSpawn;
	if (GetWorld()->SpawnActor<AKKShadowEnemy>(AKKShadowEnemy::StaticClass(), Loc, FRotator::ZeroRotator, P))
	{
		--WaveLeft;
	}
}

void AKKEnemySpawner::OnDawn()
{
	GetWorldTimerManager().ClearTimer(WaveTimer);
	GetWorldTimerManager().ClearTimer(BurstTimer);
	WaveLeft = 0;
	for (TActorIterator<AKKShadowEnemy> It(GetWorld()); It; ++It)
	{
		It->ForceDawnDeath();
	}
}

int32 AKKEnemySpawner::CountAlive() const
{
	int32 N = 0;
	for (TActorIterator<AKKShadowEnemy> It(GetWorld()); It; ++It)
	{
		if (It->IsKKAlive()) ++N;
	}
	return N;
}

void AKKEnemySpawner::SpawnBatch()
{
	APawn* Player = UGameplayStatics::GetPlayerPawn(GetWorld(), 0);
	if (!Player) return;

	const int32 MaxAlive = FMath::Min(4 + CurrentDay * 2, 18); // gün ilerledikçe baskı (KO eğrisi)
	const int32 ToSpawn = FMath::Min(FMath::RandRange(1, 2), MaxAlive - CountAlive());

	for (int32 i = 0; i < ToSpawn; ++i)
	{
		const float Ang = FMath::FRandRange(0.f, 2.f * PI);
		const float Dist = FMath::FRandRange(1400.f, 2400.f); // ekran dışından sızar
		const FVector Loc = Player->GetActorLocation()
			+ FVector(FMath::Cos(Ang) * Dist, FMath::Sin(Ang) * Dist, 80.f);

		FActorSpawnParameters P;
		P.SpawnCollisionHandlingOverride = ESpawnActorCollisionHandlingMethod::AdjustIfPossibleButAlwaysSpawn;
		GetWorld()->SpawnActor<AKKShadowEnemy>(AKKShadowEnemy::StaticClass(), Loc, FRotator::ZeroRotator, P);
	}
}
