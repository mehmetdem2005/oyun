// KKResourceNode.cpp
#include "World/KKResourceNode.h"
#include "World/KKWorldGenSubsystem.h"
#include "Player/KKPlayerCharacter.h"
#include "Items/KKInventoryComponent.h"
#include "Audio/KKAudioSubsystem.h"
#include "Core/KKMessageBusSubsystem.h"
#include "Core/KKGameplayTags.h"
#include "Components/StaticMeshComponent.h"
#include "Materials/MaterialInstanceDynamic.h"
#include "Net/UnrealNetwork.h"
#include "Engine/StaticMesh.h"
#include "Engine/World.h"

namespace
{
	const TCHAR* ResNodeCubeM   = TEXT("/Engine/BasicShapes/Cube.Cube");
	const TCHAR* ResNodeSphereM = TEXT("/Engine/BasicShapes/Sphere.Sphere");
	const TCHAR* ResNodeConeM   = TEXT("/Engine/BasicShapes/Cone.Cone");
	const TCHAR* ResNodeCylM    = TEXT("/Engine/BasicShapes/Cylinder.Cylinder");
	const TCHAR* ResNodeBaseMat = TEXT("/Engine/BasicShapes/BasicShapeMaterial.BasicShapeMaterial");
}

AKKResourceNode::AKKResourceNode()
{
	PrimaryActorTick.bCanEverTick = true;
	PrimaryActorTick.TickInterval = 0.f;
	bReplicates = true;
	SetReplicateMovement(false); // statik: bant genişliği sıfır (plan 103 disiplini)
	NetDormancy = DORM_DormantAll; // değişim anında uyandırılır

	Root = CreateDefaultSubobject<USceneComponent>(TEXT("Root"));
	SetRootComponent(Root);
}

void AKKResourceNode::GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const
{
	Super::GetLifetimeReplicatedProps(OutLifetimeProps);
	DOREPLIFETIME(AKKResourceNode, Type);
	DOREPLIFETIME(AKKResourceNode, Variant);
	DOREPLIFETIME(AKKResourceNode, Tile);
	DOREPLIFETIME(AKKResourceNode, HitsLeft);
	DOREPLIFETIME(AKKResourceNode, bDepleted);
}

void AKKResourceNode::InitNode(EKKResource InType, FIntPoint InTile, int32 InVariant, const FVector2D& Jitter)
{
	Type = InType; Tile = InTile; Variant = InVariant;
	HitsLeft = (Type == EKKResource::Tree) ? 3 : (Type == EKKResource::Rock ? 4 : 1);

	if (UKKWorldGenSubsystem* Gen = GetWorld()->GetSubsystem<UKKWorldGenSubsystem>())
	{
		FVector L = Gen->TileToWorld(Tile.X, Tile.Y, 0.f);
		L.X += Jitter.X; L.Y += Jitter.Y;
		SetActorLocation(L);
		// Kayıttan dönen "hasat edilmiş" durum:
		if (Gen->IsHarvested(NodeKey(), GetWorld()->GetTimeSeconds()))
		{
			bDepleted = true; HitsLeft = 0;
		}
	}
	BuildVisual();
	SetDepletedVisual(bDepleted);
}

void AKKResourceNode::BeginPlay()
{
	Super::BeginPlay();
	// İstemcide replike değerlerle görsel kur (sunucu InitNode'da kurdu).
	if (Parts.Num() == 0 && !HasAuthority())
	{
		BuildVisual();
		SetDepletedVisual(bDepleted);
	}
}

UStaticMeshComponent* AKKResourceNode::AddPart(const TCHAR* MeshPath, const FLinearColor& Color,
                                               const FVector& RelLoc, const FVector& Scale,
                                               const FRotator& RelRot, bool bCollide)
{
	UStaticMeshComponent* C = NewObject<UStaticMeshComponent>(this);
	C->SetupAttachment(Root);
	C->RegisterComponent();
	if (UStaticMesh* M = LoadObject<UStaticMesh>(nullptr, MeshPath)) C->SetStaticMesh(M);
	if (UMaterialInterface* Base = LoadObject<UMaterialInterface>(nullptr, ResNodeBaseMat))
	{
		UMaterialInstanceDynamic* MID = UMaterialInstanceDynamic::Create(Base, this);
		MID->SetVectorParameterValue(TEXT("Color"), Color);
		C->SetMaterial(0, MID);
	}
	C->SetRelativeLocation(RelLoc);
	C->SetRelativeRotation(RelRot);
	C->SetRelativeScale3D(Scale);
	C->SetCollisionEnabled(bCollide ? ECollisionEnabled::QueryAndPhysics : ECollisionEnabled::NoCollision);
	C->SetCanEverAffectNavigation(false);
	AddInstanceComponent(C);
	Parts.Add(C);
	return C;
}

void AKKResourceNode::BuildVisual()
{
	// Organiklik: karo hash'inden deterministik yaw + ölçek (KO jitter ruhu, estetik).
	UKKWorldGenSubsystem* Gen = GetWorld() ? GetWorld()->GetSubsystem<UKKWorldGenSubsystem>() : nullptr;
	const double H1 = Gen ? Gen->Hash01(Tile.X, Tile.Y, 32) : 0.5;
	const double H2 = Gen ? Gen->Hash01(Tile.X, Tile.Y, 33) : 0.5;
	const float Yaw = float(H1 * 360.0);
	const float S   = 0.88f + float(H2) * 0.28f;
	Root->SetRelativeRotation(FRotator(0.f, Yaw, 0.f));
	Root->SetRelativeScale3D(FVector(S));

	using namespace KKPalette;
	if (Type == EKKResource::Tree)
	{
		// KO drawTree paleti: gövde #6e4a2a, kozalak yeşilleri varyanta göre.
		AddPart(ResNodeCylM, Hex(TEXT("6e4a2a")), FVector(0, 0, 55), FVector(0.16f, 0.16f, 1.1f));
		if (Variant == 1) // yuvarlak yapraklı
		{
			AddPart(ResNodeSphereM, Hex(TEXT("2f9e4f")), FVector(0, 0, 170), FVector(1.5f), FRotator::ZeroRotator, false);
			AddPart(ResNodeSphereM, Hex(TEXT("23753a")), FVector(35, 20, 140), FVector(1.0f), FRotator::ZeroRotator, false);
			AddPart(ResNodeSphereM, Hex(TEXT("49bd66")), FVector(-28, -14, 195), FVector(0.7f), FRotator::ZeroRotator, false);
		}
		else // çam (KO varyant 0/2)
		{
			const FLinearColor A = (Variant == 2) ? Hex(TEXT("14532e")) : Hex(TEXT("1f7a44"));
			const FLinearColor B = (Variant == 2) ? Hex(TEXT("1b6b3c")) : Hex(TEXT("2a955a"));
			AddPart(ResNodeConeM, A, FVector(0, 0, 110), FVector(1.7f, 1.7f, 1.3f), FRotator::ZeroRotator, false);
			AddPart(ResNodeConeM, B, FVector(0, 0, 180), FVector(1.3f, 1.3f, 1.1f), FRotator::ZeroRotator, false);
			AddPart(ResNodeConeM, A, FVector(0, 0, 245), FVector(0.9f, 0.9f, 0.95f), FRotator::ZeroRotator, false);
		}
	}
	else if (Type == EKKResource::Rock)
	{
		const float RS = (Variant == 1) ? 1.25f : (Variant == 2 ? 0.82f : 1.f);
		AddPart(ResNodeCubeM, Hex(TEXT("7e8694")), FVector(0, 0, 28 * RS), FVector(0.78f * RS, 0.62f * RS, 0.5f * RS), FRotator(0, 18, 0));
		AddPart(ResNodeCubeM, Hex(TEXT("a8b0bd")), FVector(8, -6, 58 * RS), FVector(0.42f * RS, 0.4f * RS, 0.3f * RS), FRotator(0, -12, 6), false);
		if (Variant == 2) // yosunlu (KO #4f9b58 detayı)
			AddPart(ResNodeCubeM, Hex(TEXT("4f9b58")), FVector(-16, 14, 44 * RS), FVector(0.2f, 0.18f, 0.08f), FRotator::ZeroRotator, false);
	}
	else // Bush
	{
		AddPart(ResNodeSphereM, Hex(TEXT("2c7a3d")), FVector(0, 0, 32), FVector(0.78f), FRotator::ZeroRotator, false);
		AddPart(ResNodeSphereM, Hex(TEXT("3c9b51")), FVector(-20, 10, 40), FVector(0.5f),  FRotator::ZeroRotator, false);
		AddPart(ResNodeSphereM, Hex(TEXT("2c7a3d")), FVector(18, -8, 36),  FVector(0.52f), FRotator::ZeroRotator, false);
		// Böğürtlenler (#e23d4f): hasatta gizlenir, dolunca görünür.
		const FVector BPos[3] = { FVector(-14, -16, 46), FVector(16, 12, 50), FVector(2, -2, 62) };
		for (const FVector& P : BPos)
		{
			UStaticMeshComponent* Berry = AddPart(ResNodeSphereM, Hex(TEXT("e23d4f")), P, FVector(0.11f), FRotator::ZeroRotator, false);
			BerryParts.Add(Berry);
		}
	}
}

float AKKResourceNode::RespawnDelay() const
{
	switch (Type)
	{
	case EKKResource::Tree: return 90.f;
	case EKKResource::Rock: return 120.f;
	default:                return 45.f; // Bush
	}
}

int64 AKKResourceNode::NodeKey() const { return UKKWorldGenSubsystem::MakeNodeKey(Tile.X, Tile.Y); }

bool AKKResourceNode::ServerHarvest(AActor* Harvester)
{
	if (!HasAuthority() || bDepleted || !Harvester) return false;

	FlushNetDormancy();
	--HitsLeft;
	Multicast_HitFX();

	// Her başarılı vuruş 1 kaynak verir (mobil için sıkı geri bildirim döngüsü).
	const FName ItemId = (Type == EKKResource::Tree) ? FName("wood")
	                   : (Type == EKKResource::Rock) ? FName("stone") : FName("berry");
	const int32 Amount = (Type == EKKResource::Bush) ? 2 : 1;
	if (UKKInventoryComponent* Inv = Harvester->FindComponentByClass<UKKInventoryComponent>())
	{
		Inv->AddItem(ItemId, Amount);
	}
	if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(this))
	{
		Bus->BroadcastTag(KKTags::Resource_Harvested, Amount, 0.f, GetActorLocation(), ItemId.ToString());
	}

	if (HitsLeft <= 0)
	{
		bDepleted = true;
		OnRep_Depleted();
		if (UKKWorldGenSubsystem* Gen = GetWorld()->GetSubsystem<UKKWorldGenSubsystem>())
		{
			Gen->MarkHarvested(NodeKey(), GetWorld()->GetTimeSeconds() + RespawnDelay());
		}
	}
	return true;
}

void AKKResourceNode::OnRep_Depleted() { SetDepletedVisual(bDepleted); }

void AKKResourceNode::SetDepletedVisual(bool bHidden)
{
	if (Type == EKKResource::Bush)
	{
		// Çalı kalır, sadece meyveler gider (KO bushFull davranışı).
		for (UStaticMeshComponent* B : BerryParts) if (B) B->SetVisibility(!bHidden);
		return;
	}
	SetActorHiddenInGame(bHidden);
	SetActorEnableCollision(!bHidden);
}

void AKKResourceNode::Multicast_HitFX_Implementation()
{
	ShakeT = 0.28f;
	if (UKKAudioSubsystem* Audio = UKKAudioSubsystem::Get(this))
	{
		const FName Id = (Type == EKKResource::Tree) ? FName("chop")
		               : (Type == EKKResource::Rock) ? FName("mine") : FName("rustle");
		Audio->PlaySFXAt(this, Id, GetActorLocation());
	}
}

void AKKResourceNode::Tick(float Dt)
{
	Super::Tick(Dt);

	// Vuruş sarsıntısı (KO hitT sallanması — estetik geri bildirim).
	if (ShakeT > 0.f)
	{
		ShakeT = FMath::Max(0.f, ShakeT - Dt);
		const float A = ShakeT / 0.28f;
		const float Off = FMath::Sin(GetWorld()->GetTimeSeconds() * 45.f) * 6.f * A;
		Root->SetRelativeLocation(FVector(Off, 0.f, 0.f));
	}

	// Yeniden doğma denetimi (sunucu, 2 sn'de bir — ucuz).
	if (HasAuthority() && bDepleted)
	{
		RespawnCheckAcc += Dt;
		if (RespawnCheckAcc > 2.f)
		{
			RespawnCheckAcc = 0.f;
			UKKWorldGenSubsystem* Gen = GetWorld()->GetSubsystem<UKKWorldGenSubsystem>();
			if (Gen && !Gen->IsHarvested(NodeKey(), GetWorld()->GetTimeSeconds()))
			{
				FlushNetDormancy();
				bDepleted = false;
				HitsLeft = (Type == EKKResource::Tree) ? 3 : (Type == EKKResource::Rock ? 4 : 1);
				OnRep_Depleted();
			}
		}
	}
}
