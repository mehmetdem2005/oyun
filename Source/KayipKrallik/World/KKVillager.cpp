// KKVillager.cpp — Ayla: krallığın ilk kalbi atan yurttaşı. [TKT-F1-016]
#include "World/KKVillager.h"
#include "World/KKWorldTypes.h"
#include "World/KKResourceNode.h"
#include "World/KKHeartStone.h"
#include "World/KKLootBag.h"
#include "Items/KKInventoryComponent.h"
#include "Player/KKPlayerCharacter.h"
#include "Core/KKMessageBusSubsystem.h"
#include "Core/KKGameplayTags.h"
#include "Core/KKTimeOfDaySubsystem.h"
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
#include "EngineUtils.h"
#include "Net/UnrealNetwork.h"

namespace
{
	const TCHAR* CubeM    = TEXT("/Engine/BasicShapes/Cube.Cube");
	const TCHAR* SphereM  = TEXT("/Engine/BasicShapes/Sphere.Sphere");
	const TCHAR* BaseMat  = TEXT("/Engine/BasicShapes/BasicShapeMaterial.BasicShapeMaterial");

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
		C->SetCollisionEnabled(ECollisionEnabled::NoCollision); // kapsül yeter
		C->SetCastShadow(false);
		O->AddInstanceComponent(C);
		return C;
	}
}

AKKVillager::AKKVillager()
{
	PrimaryActorTick.bCanEverTick = true;
	bReplicates = true;

	GetCapsuleComponent()->SetCapsuleSize(26.f, 56.f);
	GetCharacterMovement()->MaxWalkSpeed = 300.f;            // oyuncudan yavaş: koruman gereken biri
	GetCharacterMovement()->bOrientRotationToMovement = true;
	GetCharacterMovement()->RotationRate = FRotator(0, 540, 0);
	bUseControllerRotationYaw = false;

	Inventory = CreateDefaultSubobject<UKKInventoryComponent>(TEXT("Inventory"));
}

void AKKVillager::GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const
{
	Super::GetLifetimeReplicatedProps(OutLifetimeProps);
	DOREPLIFETIME(AKKVillager, VState);
	DOREPLIFETIME(AKKVillager, bCarrying);
}

void AKKVillager::BeginPlay()
{
	Super::BeginPlay();
	BuildVisual();
	ApplyCageVisual();
}

void AKKVillager::InitFromSave(bool bAlreadyRescued)
{
	check(HasAuthority());
	if (bAlreadyRescued)
	{
		VState = EKKVillagerState::GoTree;
		ApplyCageVisual();
	}
}

void AKKVillager::BuildVisual()
{
	const FLinearColor Tunic = KKPalette::Hex(TEXT("b08a58")); // Path kahvesi = köylü yünü (palet-içi)
	const FLinearColor Skin  = KKPalette::Hex(TEXT("f2c08c"));
	const FLinearColor Hair  = KKPalette::Hex(TEXT("6e4a2a"));
	const FLinearColor Wood  = KKPalette::Hex(TEXT("6e4a2a"));

	Part(this, GetCapsuleComponent(), CubeM,   Tunic, FVector(0, 0, -14), FVector(0.34f, 0.30f, 0.52f));
	Part(this, GetCapsuleComponent(), SphereM, Skin,  FVector(0, 0, 30),  FVector(0.30f));
	Part(this, GetCapsuleComponent(), SphereM, Hair,  FVector(-3, 0, 38), FVector(0.30f, 0.30f, 0.18f)); // saç kepi

	// Taşıma görseli: omuzda kütük — bCarrying ile görünür.
	CarryLog = Part(this, GetCapsuleComponent(), CubeM, Wood, FVector(6, -16, 22), FVector(0.42f, 0.10f, 0.10f), FRotator(0, 0, 18));
	CarryLog->SetVisibility(false);

	// Kafes: 4 köşe dikme + 4 üst kiriş — Ayla içinde doğar, E kırar.
	const float R = 42.f;
	for (int32 i = 0; i < 4; ++i)
	{
		const float SX = (i & 1) ? R : -R;
		const float SY = (i & 2) ? R : -R;
		CageParts.Add(Part(this, GetCapsuleComponent(), CubeM, Wood, FVector(SX, SY, 0), FVector(0.08f, 0.08f, 1.30f)));
	}
	CageParts.Add(Part(this, GetCapsuleComponent(), CubeM, Wood, FVector(0,  R, 58), FVector(0.92f, 0.08f, 0.08f)));
	CageParts.Add(Part(this, GetCapsuleComponent(), CubeM, Wood, FVector(0, -R, 58), FVector(0.92f, 0.08f, 0.08f)));
	CageParts.Add(Part(this, GetCapsuleComponent(), CubeM, Wood, FVector( R, 0, 58), FVector(0.08f, 0.92f, 0.08f)));
	CageParts.Add(Part(this, GetCapsuleComponent(), CubeM, Wood, FVector(-R, 0, 58), FVector(0.08f, 0.92f, 0.08f)));
}

void AKKVillager::ApplyCageVisual()
{
	const bool bCaged = IsCaged();
	for (UStaticMeshComponent* P : CageParts) if (P) P->SetVisibility(bCaged);
	if (HasAuthority())
	{
		if (bCaged) GetCharacterMovement()->DisableMovement();
		else        GetCharacterMovement()->SetMovementMode(MOVE_Walking);
	}
}

void AKKVillager::Rescue(AActor* /*By*/)
{
	if (!HasAuthority() || !IsCaged()) return;
	VState = EKKVillagerState::GoTree;
	ApplyCageVisual(); // dinleyen sunucu anında; istemciler OnRep_VState ile
	if (GetNetMode() != NM_DedicatedServer)
	{
		if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFXAt(this, FName("quest"), GetActorLocation());
	}
	if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(this))
	{
		Bus->BroadcastTag(KKTags::Villager_Rescued, 0, 0.f, GetActorLocation(), TEXT("Ayla"));
	}
	UE_LOG(LogKK, Log, TEXT("[Villager] Ayla kurtarildi — krallik bir yurttas kazandi."));
}

void AKKVillager::OnRep_VState() { ApplyCageVisual(); }
void AKKVillager::OnRep_Carry()  { if (CarryLog) CarryLog->SetVisibility(bCarrying); }

void AKKVillager::Multicast_ChopFX_Implementation()
{
	// Vuruş ritmi: küçük öne eğilme yerine ses yeter (Faz 1 sade dili) — node zaten sallanıyor.
	if (GetNetMode() != NM_DedicatedServer)
	{
		if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFXAt(this, FName("chop"), GetActorLocation());
	}
}

AKKResourceNode* AKKVillager::FindNearbyTree() const
{
	// Çapa: Kalp Taşı (ev) — yoksa kendi konumu. Köylü kampı terk etmez.
	FVector Home = GetActorLocation();
	for (TActorIterator<AKKHeartStone> It(GetWorld()); It; ++It) { Home = It->GetActorLocation(); break; }

	AKKResourceNode* Best = nullptr;
	float BestD2 = WorkRadius * WorkRadius;
	for (TActorIterator<AKKResourceNode> It(GetWorld()); It; ++It)
	{
		if (It->GetResourceType() != EKKResource::Tree || It->IsDepleted()) continue;
		const float D2 = FVector::DistSquared2D(Home, It->GetActorLocation());
		if (D2 < BestD2) { BestD2 = D2; Best = *It; }
	}
	return Best;
}

AActor* AKKVillager::FindNearestPlayer() const
{
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
	return Best;
}

void AKKVillager::Tick(float Dt)
{
	Super::Tick(Dt);
	if (!HasAuthority() || IsCaged() || !IsKKAlive()) return;

	// --- Gece protokolü: işi bırak, kalbe sığın (Karabasan Faz 3'te gelecek; şimdilik güvende) ---
	float Darkness = 0.f;
	if (const UKKTimeOfDaySubsystem* T = GetWorld()->GetSubsystem<UKKTimeOfDaySubsystem>())
		Darkness = T->GetDarkness01();

	if (Darkness >= 0.45f)
	{
		VState = EKKVillagerState::NightHide;
		FVector Heart = GetActorLocation();
		for (TActorIterator<AKKHeartStone> It(GetWorld()); It; ++It) { Heart = It->GetActorLocation(); break; }
		const FVector To = Heart - GetActorLocation();
		if (To.SizeSquared2D() > 200.f * 200.f) AddMovementInput(To.GetSafeNormal2D(), 1.f);
		return;
	}
	if (VState == EKKVillagerState::NightHide) VState = EKKVillagerState::GoTree; // şafak: işe dön

	ChopCd   = FMath::Max(0.f, ChopCd - Dt);
	RescanCd = FMath::Max(0.f, RescanCd - Dt);

	// --- Teslimat: omuzda kütük varsa en yakın oyuncuya ---
	if (Inventory && !Inventory->IsEmpty())
	{
		VState = EKKVillagerState::Deliver;
		if (AActor* P = FindNearestPlayer())
		{
			const FVector To = P->GetActorLocation() - GetActorLocation();
			if (To.SizeSquared2D() < DeliverRange * DeliverRange)
			{
				if (UKKInventoryComponent* PInv = P->FindComponentByClass<UKKInventoryComponent>())
				{
					TMap<FName, int32> Carry;
					Inventory->TakeAllInto(Carry);
					PInv->AddMap(Carry);
					bCarrying = false; OnRep_Carry();
					if (GetNetMode() != NM_DedicatedServer)
					{
						if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFXAt(this, FName("pickup"), GetActorLocation());
					}
				}
				VState = EKKVillagerState::GoTree;
			}
			else AddMovementInput(To.GetSafeNormal2D(), 1.f);
		}
		return;
	}

	// --- İş döngüsü: ağaç bul -> yürü -> kes ---
	if (!TargetTree.IsValid() || TargetTree->IsDepleted())
	{
		if (RescanCd > 0.f) return; // boşta nefes: her karede dünya taraması yok
		RescanCd = 2.f;
		TargetTree = FindNearbyTree();
		if (!TargetTree.IsValid()) return; // civarda ağaç kalmadı: bekle (respawn 90 sn)
	}

	const FVector To = TargetTree->GetActorLocation() - GetActorLocation();
	if (To.SizeSquared2D() > ChopRange * ChopRange)
	{
		VState = EKKVillagerState::GoTree;
		AddMovementInput(To.GetSafeNormal2D(), 1.f);
	}
	else
	{
		VState = EKKVillagerState::Chop;
		if (ChopCd <= 0.f)
		{
			ChopCd = ChopRhythm;
			Multicast_ChopFX();
			TargetTree->ServerHarvest(this); // ödül kendi envanterine — oyuncuyla AYNI yol
			if (Inventory && !Inventory->IsEmpty()) { bCarrying = true; OnRep_Carry(); }
		}
	}
}

void AKKVillager::ReceiveKKDamage(float Amount, AActor* /*DamageInstigator*/)
{
	if (!HasAuthority() || !IsKKAlive() || IsCaged()) return; // kafesteyken dokunulmaz (kurtar, vurma)
	HP -= Amount;
	if (GetNetMode() != NM_DedicatedServer)
	{
		if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFXAt(this, FName("hurt"), GetActorLocation());
	}
	if (HP <= 0.f) Die();
}

void AKKVillager::Die()
{
	// İsimli karakter kuralı: Ayla geri gelmez. Taşıdığı odun yağma kuralına düşer.
	if (Inventory && !Inventory->IsEmpty())
	{
		TMap<FName, int32> Drop;
		Inventory->TakeAllInto(Drop);
		FActorSpawnParameters P;
		P.SpawnCollisionHandlingOverride = ESpawnActorCollisionHandlingMethod::AdjustIfPossibleButAlwaysSpawn;
		if (AKKLootBag* Bag = GetWorld()->SpawnActor<AKKLootBag>(AKKLootBag::StaticClass(), GetActorLocation(), FRotator::ZeroRotator, P))
		{
			Bag->InitLoot(Drop);
		}
	}
	if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(this))
	{
		Bus->BroadcastTag(KKTags::Villager_Died, 0, 0.f, GetActorLocation(), TEXT("Ayla"));
	}
	UE_LOG(LogKK, Warning, TEXT("[Villager] Ayla dustu. Krallik bir yurttasini kaybetti — kalici."));
	Destroy(); // kayıtta VillagerState=2'ye düşer (SaveWorld dünyada bulamayınca)
}
