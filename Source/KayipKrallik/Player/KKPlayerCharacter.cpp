// KKPlayerCharacter.cpp
#include "Player/KKPlayerCharacter.h"
#include "Combat/KKAbilitySystemComponent.h"
#include "Items/KKInventoryComponent.h"
#include "World/KKResourceNode.h"
#include "World/KKWorldGenSubsystem.h"
#include "World/KKBuildGridSubsystem.h"
#include "World/KKBuildable.h"
#include "World/KKLootBag.h"
#include "Enemy/KKShadowEnemy.h"
#include "World/KKVillager.h"
#include "Core/KKTimeOfDaySubsystem.h"
#include "Core/KKMessageBusSubsystem.h"
#include "Core/KKGameplayTags.h"
#include "Audio/KKAudioSubsystem.h"
#include "KayipKrallik.h"

#include "GameFramework/SpringArmComponent.h"
#include "Camera/CameraComponent.h"
#include "GameFramework/CharacterMovementComponent.h"
#include "Components/CapsuleComponent.h"
#include "Components/StaticMeshComponent.h"
#include "Components/PointLightComponent.h"
#include "Materials/MaterialInstanceDynamic.h"
#include "Engine/StaticMesh.h"
#include "EngineUtils.h"
#include "TimerManager.h"

AKKPlayerCharacter::AKKPlayerCharacter()
{
	PrimaryActorTick.bCanEverTick = true;

	GetCapsuleComponent()->SetCapsuleSize(34.f, 88.f);
	bUseControllerRotationYaw = false;

	UCharacterMovementComponent* Move = GetCharacterMovement();
	Move->bOrientRotationToMovement = true;        // yüz hareket yönüne döner (top-down hissi)
	Move->RotationRate = FRotator(0.f, 720.f, 0.f);
	Move->MaxWalkSpeed = 420.f;                    // KO oyuncu hızı oranı
	Move->BrakingDecelerationWalking = 2400.f;

	// Sabit kuş bakışı: SANAT-YONU 2.1 — pitch -55, FOV 50 (sıkışık perspektif = diorama).
	SpringArm = CreateDefaultSubobject<USpringArmComponent>(TEXT("SpringArm"));
	SpringArm->SetupAttachment(RootComponent);
	SpringArm->SetUsingAbsoluteRotation(true);
	SpringArm->SetRelativeRotation(FRotator(-55.f, 0.f, 0.f));
	SpringArm->TargetArmLength = 1400.f;
	SpringArm->bDoCollisionTest = false;
	SpringArm->bInheritPitch = SpringArm->bInheritYaw = SpringArm->bInheritRoll = false;
	SpringArm->bEnableCameraLag = true;
	SpringArm->CameraLagSpeed = 9.f;

	Camera = CreateDefaultSubobject<UCameraComponent>(TEXT("Camera"));
	Camera->SetupAttachment(SpringArm);
	Camera->SetFieldOfView(50.f);

	// Meşale: gece otomatik yanar (estetik + okunabilirlik), gölge yok (mobil bütçe).
	TorchLight = CreateDefaultSubobject<UPointLightComponent>(TEXT("Torch"));
	TorchLight->SetupAttachment(RootComponent);
	TorchLight->SetRelativeLocation(FVector(0, 0, 95));
	TorchLight->SetLightColor(FLinearColor::FromSRGBColor(FColor::FromHex(TEXT("ffb45e"))));
	TorchLight->SetAttenuationRadius(620.f);
	TorchLight->SetIntensity(0.f);
	TorchLight->SetCastShadows(false);

	ASC = CreateDefaultSubobject<UKKAbilitySystemComponent>(TEXT("ASC"));
	ASC->SetIsReplicated(true);
	ASC->SetReplicationMode(EGameplayEffectReplicationMode::Mixed);
	AttrSet = CreateDefaultSubobject<UKKAttributeSet>(TEXT("AttrSet"));

	Inventory = CreateDefaultSubobject<UKKInventoryComponent>(TEXT("Inventory"));
}

UAbilitySystemComponent* AKKPlayerCharacter::GetAbilitySystemComponent() const { return ASC; }

void AKKPlayerCharacter::PossessedBy(AController* NewController)
{
	Super::PossessedBy(NewController);
	if (ASC) ASC->InitAbilityActorInfo(this, this);
}

void AKKPlayerCharacter::BeginPlay()
{
	Super::BeginPlay();
	BuildVisual();
	LastSafeLoc = GetActorLocation();
}

void AKKPlayerCharacter::BuildVisual()
{
	if (BodyMesh) return;
	auto Add = [this](const TCHAR* Mesh, const FLinearColor& C, FVector L, FVector S, USceneComponent* Parent) -> UStaticMeshComponent*
	{
		UStaticMeshComponent* P = NewObject<UStaticMeshComponent>(this);
		P->SetupAttachment(Parent ? Parent : RootComponent);
		P->RegisterComponent();
		if (UStaticMesh* M = LoadObject<UStaticMesh>(nullptr, Mesh)) P->SetStaticMesh(M);
		UMaterialInstanceDynamic* MID = nullptr;
		if (UMaterialInterface* B = LoadObject<UMaterialInterface>(nullptr, TEXT("/Engine/BasicShapes/BasicShapeMaterial.BasicShapeMaterial")))
		{
			MID = UMaterialInstanceDynamic::Create(B, this);
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

	const FLinearColor Tunic = FLinearColor::FromSRGBColor(FColor::FromHex(TEXT("3e7fd0"))); // KO oyuncu mavisi
	const FLinearColor Skin  = FLinearColor::FromSRGBColor(FColor::FromHex(TEXT("f2c08c")));
	const FLinearColor Steel = FLinearColor::FromSRGBColor(FColor::FromHex(TEXT("cfd6e0")));

	BodyMesh = Add(TEXT("/Engine/BasicShapes/Cube.Cube"),   Tunic, FVector(0, 0, -18), FVector(0.42f, 0.34f, 0.56f), nullptr);
	HeadMesh = Add(TEXT("/Engine/BasicShapes/Sphere.Sphere"), Skin, FVector(0, 0, 42),  FVector(0.32f), nullptr);
	BodyMID  = Cast<UMaterialInstanceDynamic>(BodyMesh->GetMaterial(0));

	SwordPivot = NewObject<USceneComponent>(this);
	SwordPivot->SetupAttachment(RootComponent);
	SwordPivot->RegisterComponent();
	SwordPivot->SetRelativeLocation(FVector(0, 24, 0));
	SwordPivot->SetRelativeRotation(FRotator(0, -35, 0)); // bekleme duruşu
	AddInstanceComponent(SwordPivot);

	SwordMesh = Add(TEXT("/Engine/BasicShapes/Cube.Cube"), Steel, FVector(46, 0, 8), FVector(0.6f, 0.05f, 0.05f), SwordPivot);
}

void AKKPlayerCharacter::Move(const FVector2D& Axis)
{
	if (bDead) return;
	// Sabit kamera: ekran-yukarı = dünya +X, ekran-sağ = dünya +Y.
	AddMovementInput(FVector(0.f, 1.f, 0.f), Axis.X);
	AddMovementInput(FVector(1.f, 0.f, 0.f), Axis.Y);
}

void AKKPlayerCharacter::PrimaryAction()
{
	if (bDead) return;
	if (bBuildMode)
	{
		// Yerleştir: tip sunucuya gider; karoyu sunucu kendi transformundan hesaplar (hile yüzeyi yok).
		Server_PlaceBuild(BuildSel);
		return;
	}
	if (AttackT > 0.f) return;
	Server_Primary();
}

void AKKPlayerCharacter::TryInteract()
{
	if (bDead) return;
	if (bBuildMode)
	{
		BuildSel = (BuildSel + 1) % 3; // Duvar -> Kapı -> Balista
		if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFX(this, FName("click"));
		UpdateBuildGhost();
		UpdateContextHint();
		return;
	}
	Server_Interact();
}

AKKResourceNode* AKKPlayerCharacter::FindNodeInRange(float Range) const
{
	AKKResourceNode* Best = nullptr;
	float BestD2 = Range * Range;
	const FVector L = GetActorLocation();
	for (TActorIterator<AKKResourceNode> It(GetWorld()); It; ++It)
	{
		AKKResourceNode* N = *It;
		if (!N || N->IsDepleted()) continue;
		const float D2 = FVector::DistSquared2D(L, N->GetActorLocation());
		if (D2 < BestD2) { BestD2 = D2; Best = N; }
	}
	return Best;
}

void AKKPlayerCharacter::Server_Primary_Implementation()
{
	if (bDead || !AttrSet) return;
	if (AttrSet->GetStamina() < SwingStamina)
	{
		// Enerji yok: cılız tık (dürüst geri bildirim, KO click)
		if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFX(this, FName("click"));
		return;
	}
	ASC->ApplyModToAttribute(UKKAttributeSet::GetStaminaAttribute(), EGameplayModOp::Additive, -SwingStamina);
	Multicast_SwingFX();

	// 1) Önce hasat (mobil tek-tuş sözleşmesi: SALDIR yakındaki düğümü keser)
	if (AKKResourceNode* Node = FindNodeInRange(HarvestRange))
	{
		Node->ServerHarvest(this);
		return;
	}
	// 2) Kılıç: önümüzdeki yarım dairede hasar verilebilirler
	const FVector L = GetActorLocation();
	const FVector Fwd = GetActorForwardVector();
	for (TActorIterator<APawn> It(GetWorld()); It; ++It)
	{
		APawn* P = *It;
		if (!P || P == this) continue;
		const FVector To = P->GetActorLocation() - L;
		if (To.SizeSquared2D() > MeleeRange * MeleeRange) continue;
		if (FVector::DotProduct(Fwd, To.GetSafeNormal2D()) < 0.35f) continue;
		if (IKKDamageable* D = Cast<IKKDamageable>(P))
		{
			if (D->IsKKAlive()) D->ReceiveKKDamage(MeleeDamage, this);
		}
	}
}

void AKKPlayerCharacter::Server_Interact_Implementation()
{
	if (bDead) return;
	if (AKKResourceNode* Node = FindNodeInRange(HarvestRange))
	{
		if (Node->GetResourceType() == EKKResource::Bush) { Node->ServerHarvest(this); return; }
	}
	// Kafesteki yurttaşı kurtar (Faz 1: Ayla) — kapı kontrolünden önce: kurtarma her zaman önceliklidir.
	for (TActorIterator<AKKVillager> It(GetWorld()); It; ++It)
	{
		if (It->IsCaged() &&
			FVector::DistSquared2D(GetActorLocation(), It->GetActorLocation()) < HarvestRange * HarvestRange)
		{
			It->Rescue(this);
			return;
		}
	}
	// Yakındaki kapıyı aç/kapat (Faz 1)
	if (UKKBuildGridSubsystem* Grid = GetWorld()->GetSubsystem<UKKBuildGridSubsystem>())
	{
		const FIntPoint T = ComputeBuildTile();
		AKKBuildable* B = Grid->FindAt(T.X, T.Y);
		if (!B) // ileri karoda yoksa üstünde durduğumuz karoya da bak
		{
			if (const UKKWorldGenSubsystem* Gen = GetWorld()->GetSubsystem<UKKWorldGenSubsystem>())
			{
				const FIntPoint Here = Gen->WorldToTile(GetActorLocation());
				B = Grid->FindAt(Here.X, Here.Y);
			}
		}
		if (B && B->GetBuildType() == EKKBuildType::Door) { B->ServerToggleDoor(); return; }
	}
	// Et > böğürtlen: çiğ et +35 (Faz 2'de kamp ateşinde pişirme gelince çiğ et risk taşıyacak — şimdilik cömert).
	if (Inventory && Inventory->ConsumeItem(FName("meat"), 1))
	{
		ASC->ApplyModToAttribute(UKKAttributeSet::GetHungerAttribute(), EGameplayModOp::Additive, +35.f);
		Multicast_EatFX();
		return;
	}
	// Elinde böğürtlen varsa ye (KO doUse zinciri)
	if (Inventory && Inventory->ConsumeItem(FName("berry"), 1))
	{
		ASC->ApplyModToAttribute(UKKAttributeSet::GetHungerAttribute(), EGameplayModOp::Additive, +22.f);
		Multicast_EatFX();
	}
}

void AKKPlayerCharacter::ReceiveKKDamage(float Amount, AActor* DamageInstigator)
{
	if (!HasAuthority() || bDead || Amount <= 0.f) return;
	// Katil takibi: yalnız GERÇEK saldırganlar buradan geçer (açlık doğrudan attribute keser).
	// 10 sn pencere: "gölge vurdu, 2 dk sonra açlıktan öldü" -> katil sayılmaz, kese yere düşer.
	LastHitBy = DamageInstigator;
	LastHitTime = GetWorld()->GetTimeSeconds();
	ASC->ApplyModToAttribute(UKKAttributeSet::GetHealthAttribute(), EGameplayModOp::Additive, -Amount);
	Multicast_HurtFX();
	if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(this))
	{
		Bus->BroadcastTag(KKTags::Player_Damaged, 0, Amount, GetActorLocation());
	}
	if (AttrSet->GetHealth() <= 0.f) Die();
}

// ================== İNŞA MODU (Faz 1) ==================
static EKKBuildType SelToType(uint8 Sel)
{
	switch (Sel % 3)
	{
	case 0:  return EKKBuildType::Wall;
	case 1:  return EKKBuildType::Door;
	default: return EKKBuildType::Ballista;
	}
}


FIntPoint AKKPlayerCharacter::ComputeBuildTile() const
{
	// Bir karo ileri: yerleştirme her zaman bakılan yöne olur (KO "önündeki kareye koy" hissi).
	// DÜRÜST NOT: istemci hayaleti ile sunucu doğrulaması aynı formülü kullanır; ağ gecikmesinde
	// transform 1 kare sapabilir — Faz 1 için kabul, Faz 4 co-op cilasında istemci karo önerisi eklenir.
	const FVector Probe = GetActorLocation() + GetActorForwardVector() * 120.f;
	if (const UKKWorldGenSubsystem* Gen = GetWorld()->GetSubsystem<UKKWorldGenSubsystem>())
	{
		return Gen->WorldToTile(Probe);
	}
	return FIntPoint::ZeroValue;
}

void AKKPlayerCharacter::ToggleBuildMode()
{
	if (bDead) return;
	bBuildMode = !bBuildMode;
	if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFX(this, FName("click"));

	if (bBuildMode && !GhostMesh)
	{
		// Hayalet: tek küp, çarpışmasız, mutlak konum/rotasyon (karaktere değil dünyaya hizalı).
		GhostMesh = NewObject<UStaticMeshComponent>(this);
		GhostMesh->SetupAttachment(GetRootComponent());
		GhostMesh->RegisterComponent();
		if (UStaticMesh* M = LoadObject<UStaticMesh>(nullptr, TEXT("/Engine/BasicShapes/Cube.Cube"))) GhostMesh->SetStaticMesh(M);
		if (UMaterialInterface* Base = LoadObject<UMaterialInterface>(nullptr, TEXT("/Engine/BasicShapes/BasicShapeMaterial.BasicShapeMaterial")))
		{
			GhostMID = UMaterialInstanceDynamic::Create(Base, this);
			GhostMesh->SetMaterial(0, GhostMID);
		}
		GhostMesh->SetCollisionEnabled(ECollisionEnabled::NoCollision);
		GhostMesh->SetCastShadow(false);
		GhostMesh->SetUsingAbsoluteLocation(true);
		GhostMesh->SetUsingAbsoluteRotation(true);
		AddInstanceComponent(GhostMesh);
		// DÜRÜST NOT: BasicShapeMaterial opak — yarı saydam hayalet Faz 1 sonunda özel
		// translucent malzeme varlığıyla gelir; şimdilik içerlek ölçek + nabız okunaklılığı sağlar.
	}
	if (GhostMesh) GhostMesh->SetVisibility(bBuildMode);
	UpdateBuildGhost();
	UpdateContextHint();
}

void AKKPlayerCharacter::UpdateBuildGhost()
{
	if (!bBuildMode || !GhostMesh) return;
	const UKKWorldGenSubsystem*  Gen  = GetWorld()->GetSubsystem<UKKWorldGenSubsystem>();
	const UKKBuildGridSubsystem* Grid = GetWorld()->GetSubsystem<UKKBuildGridSubsystem>();
	if (!Gen || !Grid) return;

	const FIntPoint T = ComputeBuildTile();
	const EKKBuildType BT = SelToType(BuildSel);

	// Geçerlilik = ızgara uygun + maliyet karşılanıyor (istemci kopyası; nihai söz sunucuda).
	bool bAfford = true;
	if (Inventory)
	{
		const FKKBuildCost Cost = KKBuild::GetCost(BT);
		if (Cost.Count1 > 0 && Inventory->GetCount(Cost.Item1) < Cost.Count1) bAfford = false;
		if (Cost.Count2 > 0 && Inventory->GetCount(Cost.Item2) < Cost.Count2) bAfford = false;
	}
	bGhostValid = Grid->CanPlaceAt(T.X, T.Y) && bAfford;

	// Şekil: tipe göre içerlek siluet; nabız okunaklılık için (saydamlık yerine).
	const float Pulse = 0.92f + 0.05f * FMath::Sin(GetWorld()->GetTimeSeconds() * 6.f);
	if (BT == EKKBuildType::Wall)
	{
		GhostMesh->SetWorldLocation(Gen->TileToWorld(T.X, T.Y, 110.f));
		GhostMesh->SetWorldScale3D(FVector(0.9f, 0.9f, 2.1f) * Pulse);
	}
	else if (BT == EKKBuildType::Door)
	{
		GhostMesh->SetWorldLocation(Gen->TileToWorld(T.X, T.Y, 105.f));
		GhostMesh->SetWorldScale3D(FVector(0.18f, 0.95f, 2.05f) * Pulse);
	}
	else // Balista: bodur kule silueti
	{
		GhostMesh->SetWorldLocation(Gen->TileToWorld(T.X, T.Y, 50.f));
		GhostMesh->SetWorldScale3D(FVector(0.78f, 0.78f, 1.0f) * Pulse);
	}
	if (GhostMID)
	{
		GhostMID->SetVectorParameterValue(TEXT("Color"),
			bGhostValid ? KKPalette::Hex(TEXT("2ecc71")) : KKPalette::Hex(TEXT("e23d4f")));
	}
}

void AKKPlayerCharacter::Server_PlaceBuild_Implementation(uint8 TypeByte)
{
	if (bDead || !Inventory) return;
	const EKKBuildType BT = SelToType(TypeByte);
	if (UKKBuildGridSubsystem* Grid = GetWorld()->GetSubsystem<UKKBuildGridSubsystem>())
	{
		const FIntPoint T = ComputeBuildTile();
		Grid->ServerPlace(BT, T.X, T.Y, Inventory); // maliyet + doğrulama tek yerde
	}
}

void AKKPlayerCharacter::Die()
{
	bBuildMode = false;
	if (GhostMesh) GhostMesh->SetVisibility(false);

	bDead = true;

	// ===== ÇOK OYUNCULU YAĞMA KURALI: eşya katile gider, oyun BİTMEZ =====
	// Katil oyuncu -> envanterine doğrudan akar (PvP/Diyar dili, friendly-fire dahil — Faz 4'te kapatılabilir).
	// Katil gölge  -> sırtlanır; o gölge ölünce (kılıç/balista/şafak fark etmez) kese düşer.
	// Katil yok    -> ölüm noktasına kese (açlık/çevre) — eşya asla buharlaşmaz.
	if (Inventory && !Inventory->IsEmpty())
	{
		AActor* Killer = (GetWorld()->GetTimeSeconds() - LastHitTime < 10.f) ? LastHitBy.Get() : nullptr;
		TMap<FName, int32> Drop;
		Inventory->TakeAllInto(Drop);

		if (AKKPlayerCharacter* PK = Cast<AKKPlayerCharacter>(Killer); PK && PK->IsKKAlive() && PK->Inventory)
		{
			PK->Inventory->AddMap(Drop);
		}
		else if (AKKShadowEnemy* SK = Cast<AKKShadowEnemy>(Killer); SK && SK->IsKKAlive())
		{
			SK->AddStolenLoot(Drop);
		}
		else
		{
			FActorSpawnParameters P;
			P.SpawnCollisionHandlingOverride = ESpawnActorCollisionHandlingMethod::AdjustIfPossibleButAlwaysSpawn;
			if (AKKLootBag* Bag = GetWorld()->SpawnActor<AKKLootBag>(AKKLootBag::StaticClass(),
				GetActorLocation(), FRotator::ZeroRotator, P))
			{
				Bag->InitLoot(Drop);
			}
		}
	}

	GetCharacterMovement()->DisableMovement();
	Multicast_DeathFX(true);
	if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(this))
	{
		Bus->BroadcastTag(KKTags::Player_Died);
	}
	FTimerHandle H;
	GetWorldTimerManager().SetTimer(H, this, &AKKPlayerCharacter::Respawn, 4.f, false);
}

void AKKPlayerCharacter::Respawn()
{
	if (UKKWorldGenSubsystem* Gen = GetWorld()->GetSubsystem<UKKWorldGenSubsystem>())
	{
		const FIntPoint Camp = Gen->FindStartCampTile();
		SetActorLocation(Gen->TileToWorld(Camp.X, Camp.Y, 120.f));
	}
	AttrSet->SetHealth(AttrSet->GetMaxHealth() * 0.6f);
	AttrSet->SetHunger(50.f);
	bDead = false;
	GetCharacterMovement()->SetMovementMode(MOVE_Walking);
	Multicast_DeathFX(false);
	if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(this))
	{
		Bus->BroadcastTag(KKTags::Player_Respawned);
	}
}

void AKKPlayerCharacter::ApplyLoadedState(float Health, float Stamina, float Hunger)
{
	if (!AttrSet) return;
	AttrSet->SetHealth(FMath::Clamp(Health, 1.f, AttrSet->GetMaxHealth()));
	AttrSet->SetStamina(FMath::Clamp(Stamina, 0.f, AttrSet->GetMaxStamina()));
	AttrSet->SetHunger(FMath::Clamp(Hunger, 0.f, AttrSet->GetMaxHunger()));
}

float AKKPlayerCharacter::GetAttrValue(EKKAttr A) const
{
	if (!AttrSet) return 0.f;
	switch (A)
	{
	case EKKAttr::Health:     return AttrSet->GetHealth();
	case EKKAttr::MaxHealth:  return AttrSet->GetMaxHealth();
	case EKKAttr::Stamina:    return AttrSet->GetStamina();
	case EKKAttr::MaxStamina: return AttrSet->GetMaxStamina();
	case EKKAttr::Hunger:     return AttrSet->GetHunger();
	default:                  return AttrSet->GetMaxHunger();
	}
}

float AKKPlayerCharacter::GetAttrPct(EKKAttr A) const
{
	switch (A)
	{
	case EKKAttr::Health:  return GetAttrValue(EKKAttr::Health)  / FMath::Max(1.f, GetAttrValue(EKKAttr::MaxHealth));
	case EKKAttr::Stamina: return GetAttrValue(EKKAttr::Stamina) / FMath::Max(1.f, GetAttrValue(EKKAttr::MaxStamina));
	default:               return GetAttrValue(EKKAttr::Hunger)  / FMath::Max(1.f, GetAttrValue(EKKAttr::MaxHunger));
	}
}

void AKKPlayerCharacter::UpdateContextHint()
{
	if (bBuildMode)
	{
		const EKKBuildType BT = SelToType(BuildSel);
		const FKKBuildCost C = KKBuild::GetCost(BT);
		FString Cost = FString::Printf(TEXT("%d odun"), C.Count1);
		if (C.Count2 > 0) Cost += FString::Printf(TEXT(" + %d taş"), C.Count2);
		CachedHint = FText::FromString(FString::Printf(
			TEXT("İNŞA · %s (%s) — SALDIR: yerleştir · E: değiştir · B: çık"),
			*KKBuild::DisplayName(BT).ToString(), *Cost));
		return;
	}
	if (const AKKResourceNode* N = FindNodeInRange(HarvestRange))
	{
		switch (N->GetResourceType())
		{
		case EKKResource::Tree: CachedHint = FText::FromString(TEXT("SALDIR: Odun kes"));  return;
		case EKKResource::Rock: CachedHint = FText::FromString(TEXT("SALDIR: Taş kır"));   return;
		default:                CachedHint = FText::FromString(TEXT("E: Böğürtlen topla")); return;
		}
	}
	if (Inventory && Inventory->GetCount(FName("meat")) > 0)
	{
		CachedHint = FText::FromString(TEXT("E: Et ye (+35)"));
		return;
	}
	if (Inventory && Inventory->GetCount(FName("berry")) > 0)
	{
		CachedHint = FText::FromString(TEXT("E: Böğürtlen ye"));
		return;
	}
	CachedHint = FText::GetEmpty();
}

void AKKPlayerCharacter::Tick(float Dt)
{
	Super::Tick(Dt);
	const float Now = GetWorld()->GetTimeSeconds();

	// --- Kılıç savuruşu animasyonu (tüm makinelerde) ---
	if (AttackT > 0.f && SwordPivot)
	{
		AttackT = FMath::Max(0.f, AttackT - Dt);
		const float A = 1.f - (AttackT / 0.18f);
		SwordPivot->SetRelativeRotation(FRotator(0.f, FMath::Lerp(-80.f, 80.f, A), 0.f));
		if (AttackT <= 0.f) SwordPivot->SetRelativeRotation(FRotator(0, -35, 0));
	}

	// --- Hasar flaşı ---
	if (FlashT > 0.f && BodyMID)
	{
		FlashT = FMath::Max(0.f, FlashT - Dt * 4.f);
		const FLinearColor Base = FLinearColor::FromSRGBColor(FColor::FromHex(TEXT("3e7fd0")));
		BodyMID->SetVectorParameterValue(TEXT("Color"), FMath::Lerp(Base, FLinearColor(1.f, 0.35f, 0.3f), FlashT));
	}

	// --- Meşale: gece otomatik, titrek (KO punch ışığı ruhu) ---
	if (TorchLight)
	{
		float Dark = 0.f;
		if (const UKKTimeOfDaySubsystem* Time = GetWorld()->GetSubsystem<UKKTimeOfDaySubsystem>())
			Dark = Time->GetDarkness01();
		const float Flicker = FMath::Sin(Now * 9.f) * 0.5f + FMath::Sin(Now * 23.f) * 0.5f;
		TorchLight->SetIntensity(Dark * (3800.f + Flicker * 600.f));
	}

	// --- İpucu (yalnız yerel kontrol, 5 Hz) ---
	if (IsLocallyControlled())
	{
		HintAcc += Dt;
		if (HintAcc > 0.2f) { HintAcc = 0.f; UpdateContextHint(); }
		if (bBuildMode) UpdateBuildGhost();
	}

	if (!HasAuthority() || bDead) return;

	// --- Hayatta kalma ekonomisi (4 Hz, otorite) ---
	SurvivalAcc += Dt;
	if (SurvivalAcc >= 0.25f)
	{
		const float Step = SurvivalAcc;
		SurvivalAcc = 0.f;
		// Açlık: tam bar ~300 sn (KO temposuna yakın)
		ASC->ApplyModToAttribute(UKKAttributeSet::GetHungerAttribute(), EGameplayModOp::Additive, -(100.f / 300.f) * Step);
		if (AttrSet->GetHunger() <= 0.f)
		{
			ASC->ApplyModToAttribute(UKKAttributeSet::GetHealthAttribute(), EGameplayModOp::Additive, -2.f * Step);
			if (AttrSet->GetHealth() <= 0.f) { Die(); return; }
		}
		// Enerji yenilenir
		ASC->ApplyModToAttribute(UKKAttributeSet::GetStaminaAttribute(), EGameplayModOp::Additive, +10.f * Step);
	}

	// --- Su engeli: yumuşak itme (KO solid karo davranışı) ---
	if (const UKKWorldGenSubsystem* Gen = GetWorld()->GetSubsystem<UKKWorldGenSubsystem>())
	{
		const FIntPoint T = Gen->WorldToTile(GetActorLocation());
		if (Gen->IsBlockedTile(Gen->GetTile(T.X, T.Y)))
		{
			SetActorLocation(LastSafeLoc, false);
		}
		else
		{
			LastSafeLoc = GetActorLocation();
		}
	}
}

// ---- Çoklu yayın FX uygulamaları ----
void AKKPlayerCharacter::Multicast_SwingFX_Implementation()
{
	AttackT = 0.18f;
	if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFX(this, FName("swing"));
}

void AKKPlayerCharacter::Multicast_HurtFX_Implementation()
{
	FlashT = 1.f;
	if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFX(this, FName("hurt"));
}

void AKKPlayerCharacter::Multicast_EatFX_Implementation()
{
	if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFX(this, FName("eat"));
}

void AKKPlayerCharacter::Multicast_DeathFX_Implementation(bool bNowDead)
{
	// Gövde yana devrilir, kafa görünür kalır — KO'nun "X göz" ölüm okunaklılığının 3B karşılığı.
	if (BodyMesh) BodyMesh->SetRelativeRotation(bNowDead ? FRotator(0, 0, 80) : FRotator::ZeroRotator);
	if (bNowDead)
	{
		if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFX(this, FName("playerDie"));
	}
}
