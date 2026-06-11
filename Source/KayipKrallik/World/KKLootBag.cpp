// KKLootBag.cpp — Üzerinden geç, al: mobil tek-dokunuş yağma (KO auto-pickup ruhu). [TKT-F1-014]
#include "World/KKLootBag.h"
#include "World/KKWorldTypes.h"
#include "Items/KKInventoryComponent.h"
#include "Player/KKPlayerCharacter.h"
#include "Audio/KKAudioSubsystem.h"
#include "KayipKrallik.h"
#include "Components/StaticMeshComponent.h"
#include "Components/SphereComponent.h"
#include "Materials/MaterialInstanceDynamic.h"
#include "Engine/StaticMesh.h"
#include "Engine/World.h"

AKKLootBag::AKKLootBag()
{
	PrimaryActorTick.bCanEverTick = false;
	bReplicates = true;
	SetReplicateMovement(false);

	Touch = CreateDefaultSubobject<USphereComponent>(TEXT("Touch"));
	SetRootComponent(Touch);
	Touch->SetSphereRadius(70.f);
	Touch->SetCollisionEnabled(ECollisionEnabled::QueryOnly);
	Touch->SetCollisionResponseToAllChannels(ECR_Ignore);
	Touch->SetCollisionResponseToChannel(ECC_Pawn, ECR_Overlap);
}

void AKKLootBag::InitLoot(const TMap<FName, int32>& In)
{
	check(HasAuthority());
	Loot = In;
	if (Loot.Num() == 0) { Destroy(); return; } // boş kese dünyayı kirletmez

	// İlk bindirme taraması BURADA: BeginPlay spawn anında koşar ve o anda Loot henüz boştur —
	// orada taramak, üstünde duran oyuncuya BOŞ keseyi verip asıl ganimeti buharlaştırırdı.
	// "Gölgeyi tepende öldürdün" senaryosu yine çalışır.
	TArray<AActor*> Already;
	Touch->GetOverlappingActors(Already, AKKPlayerCharacter::StaticClass());
	for (AActor* A : Already) { OnTouch(Touch, A, nullptr, 0, false, FHitResult()); if (bTaken) break; }
}

void AKKLootBag::BeginPlay()
{
	Super::BeginPlay();
	BuildVisual();
	if (HasAuthority())
	{
		Touch->OnComponentBeginOverlap.AddDynamic(this, &AKKLootBag::OnTouch);
	}
}

void AKKLootBag::BuildVisual()
{
	auto Part = [this](const FLinearColor& Col, const FVector& Loc, const FVector& Scl, const FRotator& Rot) -> UStaticMeshComponent*
	{
		UStaticMeshComponent* C = NewObject<UStaticMeshComponent>(this);
		C->SetupAttachment(Touch);
		C->RegisterComponent();
		if (UStaticMesh* M = LoadObject<UStaticMesh>(nullptr, TEXT("/Engine/BasicShapes/Cube.Cube"))) C->SetStaticMesh(M);
		if (UMaterialInterface* B = LoadObject<UMaterialInterface>(nullptr, TEXT("/Engine/BasicShapes/BasicShapeMaterial.BasicShapeMaterial")))
		{
			UMaterialInstanceDynamic* MID = UMaterialInstanceDynamic::Create(B, this);
			MID->SetVectorParameterValue(TEXT("Color"), Col);
			C->SetMaterial(0, MID);
		}
		C->SetRelativeLocation(Loc); C->SetRelativeScale3D(Scl); C->SetRelativeRotation(Rot);
		C->SetCollisionEnabled(ECollisionEnabled::NoCollision);
		C->SetCastShadow(false);
		AddInstanceComponent(C);
		return C;
	};

	// Çuval: 45° oturmuş ahşap-kahve küp + ALTIN boğum bandı — "değer burada" tek bakışta okunur.
	Sack = Part(KKPalette::Hex(TEXT("6e4a2a")), FVector(0, 0, 22), FVector(0.34f, 0.34f, 0.34f), FRotator(0, 45, 0));
	Band = Part(KKPalette::Hex(TEXT("e8b73d")), FVector(0, 0, 40), FVector(0.16f, 0.16f, 0.06f), FRotator(0, 45, 0));
}

void AKKLootBag::OnTouch(UPrimitiveComponent*, AActor* Other, UPrimitiveComponent*, int32, bool, const FHitResult&)
{
	if (!HasAuthority() || bTaken) return;
	AKKPlayerCharacter* P = Cast<AKKPlayerCharacter>(Other);
	if (!P || !P->IsKKAlive()) return;

	if (UKKInventoryComponent* Inv = P->FindComponentByClass<UKKInventoryComponent>())
	{
		bTaken = true;
		Inv->AddMap(Loot);
		Loot.Reset();
		Touch->SetCollisionEnabled(ECollisionEnabled::NoCollision);
		Multicast_Taken();
		// RPC'nin kanal kapanmadan gitmesi için yıkımı bir nefes ertele (en yalın yol = LifeSpan):
		SetLifeSpan(0.25f);
	}
}

void AKKLootBag::Multicast_Taken_Implementation()
{
	if (Sack) Sack->SetVisibility(false);
	if (Band) Band->SetVisibility(false);
	if (GetNetMode() != NM_DedicatedServer)
	{
		if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFXAt(this, FName("pickup"), GetActorLocation());
	}
}
