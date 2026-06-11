// KKShadowEnemy.cpp
#include "Enemy/KKShadowEnemy.h"
#include "Core/KKTimeOfDaySubsystem.h"
#include "World/KKBuildGridSubsystem.h"
#include "World/KKBuildable.h"
#include "World/KKWorldGenSubsystem.h"
#include "World/KKHeartStone.h"
#include "World/KKLootBag.h"
#include "Net/UnrealNetwork.h"
#include "EngineUtils.h"
#include "Core/KKMessageBusSubsystem.h"
#include "Core/KKGameplayTags.h"
#include "Audio/KKAudioSubsystem.h"
#include "KayipKrallik.h"
#include "GameFramework/CharacterMovementComponent.h"
#include "GameFramework/GameStateBase.h"
#include "GameFramework/PlayerState.h"
#include "Components/CapsuleComponent.h"
#include "Components/StaticMeshComponent.h"
#include "Materials/MaterialInstanceDynamic.h"
#include "Engine/StaticMesh.h"

AKKShadowEnemy::AKKShadowEnemy()
{
	PrimaryActorTick.bCanEverTick = true;
	bReplicates = true;
	AutoPossessAI = EAutoPossessAI::PlacedInWorldOrSpawned; // AAIController possess -> AddMovementInput çalışır

	GetCapsuleComponent()->SetCapsuleSize(30.f, 60.f);
	GetCharacterMovement()->MaxWalkSpeed = 240.f;            // oyuncudan yavaş: gece gerilimi
	GetCharacterMovement()->bOrientRotationToMovement = true;
	GetCharacterMovement()->RotationRate = FRotator(0.f, 540.f, 0.f);
	bUseControllerRotationYaw = false;
}

void AKKShadowEnemy::BeginPlay()
{
	Super::BeginPlay();
	BuildVisual();
	Phase = FMath::FRandRange(0.f, 6.28f);
}

void AKKShadowEnemy::BuildVisual()
{
	if (Body) return;
	auto Add = [this](const FLinearColor& C, FVector L, FVector S) -> UStaticMeshComponent*
	{
		UStaticMeshComponent* P = NewObject<UStaticMeshComponent>(this);
		P->SetupAttachment(RootComponent);
		P->RegisterComponent();
		if (UStaticMesh* M = LoadObject<UStaticMesh>(nullptr, TEXT("/Engine/BasicShapes/Sphere.Sphere"))) P->SetStaticMesh(M);
		if (UMaterialInterface* B = LoadObject<UMaterialInterface>(nullptr, TEXT("/Engine/BasicShapes/BasicShapeMaterial.BasicShapeMaterial")))
		{
			UMaterialInstanceDynamic* MID = UMaterialInstanceDynamic::Create(B, this);
			MID->SetVectorParameterValue(TEXT("Color"), C);
			P->SetMaterial(0, MID);
		}
		P->SetRelativeLocation(L);
		P->SetRelativeScale3D(S);
		P->SetCollisionEnabled(ECollisionEnabled::NoCollision);
		P->SetCanEverAffectNavigation(false);
		AddInstanceComponent(P);
		return P;
	};

	Body = Add(FLinearColor::FromSRGBColor(FColor::FromHex(TEXT("221440"))), FVector(0, 0, 0), BaseBodyScale);
	BodyMID = Cast<UMaterialInstanceDynamic>(Body->GetMaterial(0));
	const FLinearColor EyeC = FLinearColor::FromSRGBColor(FColor::FromHex(TEXT("f2f0ff")));
	EyeL = Add(EyeC, FVector(24, -10, 16), FVector(0.09f));
	EyeR = Add(EyeC, FVector(24,  10, 16), FVector(0.09f));
}

AActor* AKKShadowEnemy::FindTarget() const
{
	// Kuşatma zihni (Faz 1 basit hali): en yakın CANLI hedef — oyuncular VEYA Kalp Taşı.
	// Sonuç: oyuncu uzaktayken sürü doğal olarak kalbe yürür; yaklaşan oyuncu agroyu çeker.
	AActor* Best = nullptr;
	float BestD2 = FLT_MAX;
	if (const AGameStateBase* GS = GetWorld()->GetGameState())
	{
		for (const APlayerState* PS : GS->PlayerArray)
		{
			APawn* P = PS ? PS->GetPawn() : nullptr;
			if (!P) continue;
			if (const IKKDamageable* D = Cast<IKKDamageable>(P)) if (!D->IsKKAlive()) continue;
			const float D2 = FVector::DistSquared2D(GetActorLocation(), P->GetActorLocation());
			if (D2 < BestD2) { BestD2 = D2; Best = P; }
		}
	}
	for (TActorIterator<AKKHeartStone> It(GetWorld()); It; ++It) // tek aktör; iterasyon bedava
	{
		if (!It->IsKKAlive()) continue;
		const float D2 = FVector::DistSquared2D(GetActorLocation(), It->GetActorLocation());
		if (D2 < BestD2) { BestD2 = D2; Best = *It; }
	}
	return Best;
}

void AKKShadowEnemy::Tick(float Dt)
{
	Super::Tick(Dt);
	Phase += Dt;

	// --- Kozmetik (her makinede): KO wobble = scale nabzı ---
	if (Body)
	{
		const float W = 1.f + 0.06f * FMath::Sin(Phase * 6.f);
		Body->SetRelativeScale3D(FVector(BaseBodyScale.X * W, BaseBodyScale.Y * W, BaseBodyScale.Z / W));
	}
	if (FlashT > 0.f && BodyMID)
	{
		FlashT = FMath::Max(0.f, FlashT - Dt * 5.f);
		const FLinearColor Base = FLinearColor::FromSRGBColor(FColor::FromHex(TEXT("221440")));
		const FLinearColor Hit  = FLinearColor::FromSRGBColor(FColor::FromHex(TEXT("6a4fb8"))); // KO hitT moru
		BodyMID->SetVectorParameterValue(TEXT("Color"), FMath::Lerp(Base, Hit, FlashT));
	}
	if (bDying)
	{
		DeathT += Dt;
		const float S = FMath::Max(0.02f, 1.f - DeathT / DeathDur);
		SetActorScale3D(FVector(S));
		return;
	}

	if (!HasAuthority()) return;

	// --- Şafak ölümü (KO: gündüz gölge yaşayamaz) ---
	if (const UKKTimeOfDaySubsystem* Time = GetWorld()->GetSubsystem<UKKTimeOfDaySubsystem>())
	{
		if (Time->GetDarkness01() < 0.45f) { ForceDawnDeath(); return; }
	}

	// --- Kovala + dokunma hasarı ---
	AttackCd = FMath::Max(0.f, AttackCd - Dt);
	if (AActor* Target = FindTarget())
	{
		const FVector To = Target->GetActorLocation() - GetActorLocation();
		AddMovementInput(To.GetSafeNormal2D(), 1.f);
		if (To.SizeSquared2D() < TouchRange * TouchRange && AttackCd <= 0.f)
		{
			if (IKKDamageable* D = Cast<IKKDamageable>(Target))
			{
				if (D->IsKKAlive())
				{
					D->ReceiveKKDamage(TouchDamage, this);
					AttackCd = 1.1f;
				}
			}
		}

		// --- Kuşatma önizlemesi (Faz 1): oyuncuya gidemiyorsa öndeki yapıyı kemir ---
		// Mantık KO ruhuyla basit: hareket yönünde 1 karo ilerisi dolu duvar/kapalı kapı ise vur.
		// (Çarpışma zaten gölgeyi durdurmuş olur; bu yüzden "duvara dayanmış" anlar doğal yakalanır.)
		if (AttackCd <= 0.f)
		{
			UKKBuildGridSubsystem*       Grid = GetWorld()->GetSubsystem<UKKBuildGridSubsystem>();
			const UKKWorldGenSubsystem*  Gen  = GetWorld()->GetSubsystem<UKKWorldGenSubsystem>();
			if (Grid && Gen)
			{
				const FVector Probe = GetActorLocation() + To.GetSafeNormal2D() * 90.f;
				const FIntPoint T = Gen->WorldToTile(Probe);
				if (AKKBuildable* B = Grid->FindAt(T.X, T.Y))
				{
					const bool bBlocksPath =
						B->GetBuildType() == EKKBuildType::Wall ||
						B->GetBuildType() == EKKBuildType::Ballista || // savunmayı sökmek de kuşatmadır
						(B->GetBuildType() == EKKBuildType::Door && !B->IsDoorOpen());
					if (bBlocksPath && B->IsKKAlive())
					{
						B->ReceiveKKDamage(StructDamage, this);
						AttackCd = 1.1f; // oyuncu ve yapı aynı ritmi paylaşır — tek tehdit saati
					}
				}
			}
		}
	}
}

void AKKShadowEnemy::GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const
{
	Super::GetLifetimeReplicatedProps(OutLifetimeProps);
	DOREPLIFETIME(AKKShadowEnemy, bHasLoot);
}

void AKKShadowEnemy::ReceiveKKDamage(float Amount, AActor* DamageInstigator)
{
	if (!HasAuthority() || bDying) return;
	HP -= Amount;
	Multicast_HitFX();

	// Geri tepme (KO knockback hissi)
	if (DamageInstigator)
	{
		const FVector Dir = (GetActorLocation() - DamageInstigator->GetActorLocation()).GetSafeNormal2D();
		LaunchCharacter(Dir * 420.f + FVector(0, 0, 120.f), true, false);
	}

	if (HP <= 0.f)
	{
		if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(this))
		{
			Bus->BroadcastTag(KKTags::Enemy_Killed, 0, 0.f, GetActorLocation());
		}
		DropStolenLoot(); // hırsızı yakaladın: kese ayaklarının dibinde
		Multicast_StartDeath(0.6f);
		SetLifeSpan(0.7f);
	}
}

void AKKShadowEnemy::AddStolenLoot(const TMap<FName, int32>& In)
{
	check(HasAuthority());
	for (const auto& P : In) if (P.Value > 0) Stolen.FindOrAdd(P.Key) += P.Value;
	if (Stolen.Num() > 0 && !bHasLoot)
	{
		bHasLoot = true;
		ShowLootGem(); // dinleyen sunucu anında; istemciler OnRep ile
	}
}

void AKKShadowEnemy::OnRep_HasLoot()
{
	if (bHasLoot) ShowLootGem();
}

void AKKShadowEnemy::ShowLootGem()
{
	if (LootGem) { LootGem->SetVisibility(true); return; }
	// Baş üstünde 45° altın elmas: "BU gölge senin eşyanı taşıyor — KOVALA" tek bakışlık dil.
	LootGem = NewObject<UStaticMeshComponent>(this);
	LootGem->SetupAttachment(GetRootComponent());
	LootGem->RegisterComponent();
	if (UStaticMesh* M = LoadObject<UStaticMesh>(nullptr, TEXT("/Engine/BasicShapes/Cube.Cube"))) LootGem->SetStaticMesh(M);
	if (UMaterialInterface* B = LoadObject<UMaterialInterface>(nullptr, TEXT("/Engine/BasicShapes/BasicShapeMaterial.BasicShapeMaterial")))
	{
		UMaterialInstanceDynamic* MID = UMaterialInstanceDynamic::Create(B, this);
		MID->SetVectorParameterValue(TEXT("Color"), FLinearColor::FromSRGBColor(FColor::FromHex(TEXT("e8b73d"))));
		LootGem->SetMaterial(0, MID);
	}
	LootGem->SetRelativeLocation(FVector(0, 0, 96));
	LootGem->SetRelativeScale3D(FVector(0.16f));
	LootGem->SetRelativeRotation(FRotator(45.f, 0.f, 45.f));
	LootGem->SetCollisionEnabled(ECollisionEnabled::NoCollision);
	LootGem->SetCastShadow(false);
	AddInstanceComponent(LootGem);
}

void AKKShadowEnemy::DropStolenLoot()
{
	if (!HasAuthority() || Stolen.Num() == 0) return;
	FActorSpawnParameters P;
	P.SpawnCollisionHandlingOverride = ESpawnActorCollisionHandlingMethod::AdjustIfPossibleButAlwaysSpawn;
	if (AKKLootBag* Bag = GetWorld()->SpawnActor<AKKLootBag>(AKKLootBag::StaticClass(),
		GetActorLocation() + FVector(0, 0, -40.f), FRotator::ZeroRotator, P))
	{
		Bag->InitLoot(Stolen);
	}
	Stolen.Reset(); // çift düşürme imkânsız (şafak + hasar yarışı dahil)
}

void AKKShadowEnemy::ForceDawnDeath()
{
	if (bDying || !HasAuthority()) return;
	DropStolenLoot(); // şafak hırsızı eritir ama GANİMETİ YAKMAZ — sabah izini sür, keseni bul
	Multicast_StartDeath(1.2f); // şafakta yavaş büzülme: tören gibi (estetik)
	SetLifeSpan(1.4f);
}

void AKKShadowEnemy::Multicast_HitFX_Implementation()
{
	FlashT = 1.f;
	if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFX(this, FName("hitE"));
}

void AKKShadowEnemy::Multicast_StartDeath_Implementation(float ShrinkDur)
{
	bDying = true;
	DeathDur = ShrinkDur;
	DeathT = 0.f;
	GetCharacterMovement()->DisableMovement();
	SetActorEnableCollision(false);
	if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFX(this, FName("enemyDie"));
}
