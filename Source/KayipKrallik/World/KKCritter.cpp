// KKCritter.cpp — Tavşan zıplar, geyik süzülür; ikisi de senden kaçar. [TKT-F1-018]
#include "World/KKCritter.h"
#include "World/KKWorldTypes.h"
#include "World/KKLootBag.h"
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
#include "Engine/World.h"
#include "Net/UnrealNetwork.h"

namespace
{
	const TCHAR* CubeM   = TEXT("/Engine/BasicShapes/Cube.Cube");
	const TCHAR* SphereM = TEXT("/Engine/BasicShapes/Sphere.Sphere");
	const TCHAR* BaseMat = TEXT("/Engine/BasicShapes/BasicShapeMaterial.BasicShapeMaterial");

	UStaticMeshComponent* Part(AActor* O, USceneComponent* P, const TCHAR* Mesh, const FLinearColor& Col,
	                           const FVector& Loc, const FVector& Scl, const FRotator& Rot = FRotator::ZeroRotator)
	{
		UStaticMeshComponent* C = NewObject<UStaticMeshComponent>(O);
		C->SetupAttachment(P);
		C->RegisterComponent();
		if (UStaticMesh* M = LoadObject<UStaticMesh>(nullptr, Mesh)) C->SetStaticMesh(M);
		if (UMaterialInterface* B = LoadObject<UMaterialInterface>(nullptr, BaseMat))
		{
			UMaterialInstanceDynamic* MID = UMaterialInstanceDynamic::Create(B, O);
			MID->SetVectorParameterValue(TEXT("Color"), Col);
			C->SetMaterial(0, MID);
		}
		C->SetRelativeLocation(Loc); C->SetRelativeScale3D(Scl); C->SetRelativeRotation(Rot);
		C->SetCollisionEnabled(ECollisionEnabled::NoCollision);
		C->SetCastShadow(false);
		O->AddInstanceComponent(C);
		return C;
	}
}

AKKCritter::AKKCritter()
{
	PrimaryActorTick.bCanEverTick = true;
	bReplicates = true;
	GetCharacterMovement()->bOrientRotationToMovement = true;
	GetCharacterMovement()->RotationRate = FRotator(0, 720, 0);
	bUseControllerRotationYaw = false;
}

void AKKCritter::GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const
{
	Super::GetLifetimeReplicatedProps(OutLifetimeProps);
	DOREPLIFETIME(AKKCritter, Kind);
}

void AKKCritter::InitKind(EKKCritterKind InKind)
{
	check(HasAuthority());
	Kind = InKind;
	if (Kind == EKKCritterKind::Rabbit)
	{
		HP = 10.f;
		GetCapsuleComponent()->SetCapsuleSize(16.f, 18.f);
		GetCharacterMovement()->MaxWalkSpeed = 180.f;
	}
	else
	{
		HP = 30.f;
		GetCapsuleComponent()->SetCapsuleSize(30.f, 46.f);
		GetCharacterMovement()->MaxWalkSpeed = 160.f;
	}
	BuildVisual();
}

void AKKCritter::BeginPlay()
{
	Super::BeginPlay();
	if (!bVisualBuilt && !HasAuthority()) BuildVisual(); // istemci: replike Kind ile kur
}

void AKKCritter::OnRep_Kind()
{
	if (!bVisualBuilt) BuildVisual();
}

void AKKCritter::BuildVisual()
{
	bVisualBuilt = true;
	if (Kind == EKKCritterKind::Rabbit)
	{
		const FLinearColor Fur  = KKPalette::Hex(TEXT("a8b0bd")); // taş grisi kürk
		const FLinearColor Tail = KKPalette::Hex(TEXT("fff1a8")); // alev kremi = pamuk kuyruk
		Body = Part(this, GetCapsuleComponent(), SphereM, Fur, FVector(0, 0, -2), FVector(0.22f, 0.18f, 0.16f));
		Part(this, Body, SphereM, Fur, FVector(38, 0, 22), FVector(0.55f));                       // kafa (göreli)
		Part(this, Body, CubeM,  Fur, FVector(34, -10, 58), FVector(0.10f, 0.16f, 0.55f), FRotator(12, 0, -8));  // kulak L
		Part(this, Body, CubeM,  Fur, FVector(34,  10, 58), FVector(0.10f, 0.16f, 0.55f), FRotator(12, 0,  8));  // kulak R
		Part(this, Body, SphereM, Tail, FVector(-46, 0, 6), FVector(0.30f));                      // kuyruk
	}
	else
	{
		const FLinearColor Hide = KKPalette::Hex(TEXT("8a5a32")); // kapı paneli kahvesi = geyik postu
		const FLinearColor Leg  = KKPalette::Hex(TEXT("6e4a2a"));
		const FLinearColor Horn = KKPalette::Hex(TEXT("e6d28a")); // kum sarısı = kemik boynuz
		Body = Part(this, GetCapsuleComponent(), CubeM, Hide, FVector(0, 0, 6), FVector(0.52f, 0.26f, 0.30f));
		Part(this, GetCapsuleComponent(), CubeM, Hide, FVector(30, 0, 30), FVector(0.16f, 0.14f, 0.34f), FRotator(-24, 0, 0)); // boyun
		Part(this, GetCapsuleComponent(), CubeM, Hide, FVector(42, 0, 48), FVector(0.20f, 0.13f, 0.13f));                       // kafa
		Part(this, GetCapsuleComponent(), CubeM, Horn, FVector(40, -8, 64), FVector(0.05f, 0.05f, 0.30f), FRotator(0, 0, -28)); // boynuz L
		Part(this, GetCapsuleComponent(), CubeM, Horn, FVector(40,  8, 64), FVector(0.05f, 0.05f, 0.30f), FRotator(0, 0,  28)); // boynuz R
		for (int32 i = 0; i < 4; ++i) // bacaklar
		{
			const float X = (i < 2) ? 20.f : -20.f;
			const float Y = (i & 1) ? 10.f : -10.f;
			Part(this, GetCapsuleComponent(), CubeM, Leg, FVector(X, Y, -28), FVector(0.06f, 0.06f, 0.42f));
		}
	}
}

AActor* AKKCritter::NearestPlayer(float& OutD2) const
{
	AActor* Best = nullptr; OutD2 = FLT_MAX;
	if (const AGameStateBase* GS = GetWorld()->GetGameState())
	{
		for (const APlayerState* PS : GS->PlayerArray)
		{
			APawn* P = PS ? PS->GetPawn() : nullptr;
			if (!P) continue;
			const float D2 = FVector::DistSquared2D(GetActorLocation(), P->GetActorLocation());
			if (D2 < OutD2) { OutD2 = D2; Best = P; }
		}
	}
	return Best;
}

void AKKCritter::Tick(float Dt)
{
	Super::Tick(Dt);
	Phase += Dt;

	// --- Kozmetik (her makinede): tavşan koşarken zıplar — tek sinüsle "canlı" ---
	if (Body && Kind == EKKCritterKind::Rabbit)
	{
		const float Speed = GetVelocity().Size2D();
		const float Hop = (Speed > 20.f) ? FMath::Abs(FMath::Sin(Phase * 10.f)) * 14.f : 0.f;
		Body->SetRelativeLocation(FVector(0, 0, -2 + Hop));
	}

	if (!HasAuthority() || !IsKKAlive()) return;

	// --- Ürkek sözleşmesi: yakın oyuncu = panik, ters yöne tam gaz ---
	float D2 = 0.f;
	AActor* P = NearestPlayer(D2);
	if (P && D2 < FleeRadius * FleeRadius)
	{
		const FVector Away = (GetActorLocation() - P->GetActorLocation()).GetSafeNormal2D();
		GetCharacterMovement()->MaxWalkSpeed = (Kind == EKKCritterKind::Rabbit) ? 520.f : 470.f;
		AddMovementInput(Away, 1.f);
		bWandering = false; WanderT = 0.f;
		return;
	}
	GetCharacterMovement()->MaxWalkSpeed = (Kind == EKKCritterKind::Rabbit) ? 180.f : 160.f;

	// --- Gezinme: yürü (1-2 sn) <-> otla (1-3 sn) — yön rastgele, dünya umursamaz sakinlik ---
	WanderT -= Dt;
	if (WanderT <= 0.f)
	{
		bWandering = !bWandering;
		if (bWandering)
		{
			const float A = FMath::FRandRange(0.f, 2.f * PI);
			WanderDir = FVector(FMath::Cos(A), FMath::Sin(A), 0.f);
			WanderT = FMath::FRandRange(1.f, 2.f);
		}
		else
		{
			WanderT = FMath::FRandRange(1.f, 3.f);
		}
	}
	if (bWandering) AddMovementInput(WanderDir, 0.6f);
}

void AKKCritter::ReceiveKKDamage(float Amount, AActor* DamageInstigator)
{
	if (!HasAuthority() || !IsKKAlive()) return;
	HP -= Amount;
	if (GetNetMode() != NM_DedicatedServer)
	{
		if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFXAt(this, FName("hitE"), GetActorLocation());
	}
	// Vurulan hayvan paniğe kapanır: bir sonraki tik kaçışı zaten yakalar (P menzilde).
	if (HP <= 0.f) Die(DamageInstigator);
}

void AKKCritter::Die(AActor* /*Killer*/)
{
	// Avcılık (155): ganimet yağma kesesiyle düşer — toplama dili oyunun geri kalanıyla AYNI.
	TMap<FName, int32> Drop;
	if (Kind == EKKCritterKind::Rabbit) { Drop.Add(FName("meat"), 1); }
	else                                { Drop.Add(FName("meat"), 2); Drop.Add(FName("hide"), 1); }

	FActorSpawnParameters P;
	P.SpawnCollisionHandlingOverride = ESpawnActorCollisionHandlingMethod::AdjustIfPossibleButAlwaysSpawn;
	if (AKKLootBag* Bag = GetWorld()->SpawnActor<AKKLootBag>(AKKLootBag::StaticClass(), GetActorLocation(), FRotator::ZeroRotator, P))
	{
		Bag->InitLoot(Drop);
	}
	if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(this))
	{
		Bus->BroadcastTag(KKTags::Critter_Killed, int32(Kind), 0.f, GetActorLocation()); // spawner av baskısını sayar
	}
	if (GetNetMode() != NM_DedicatedServer)
	{
		if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFXAt(this, FName("critterDie"), GetActorLocation());
	}
	Destroy();
}
