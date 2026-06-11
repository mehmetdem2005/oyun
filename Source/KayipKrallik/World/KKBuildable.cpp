// KKBuildable.cpp — Duvar/Kapı: KO paleti, kapı menteşesi, çift taraflı ızgara kaydı. [TKT-F1-002]
#include "World/KKBuildable.h"
#include "World/KKWorldTypes.h"
#include "World/KKBuildGridSubsystem.h"
#include "Audio/KKAudioSubsystem.h"
#include "Core/KKMessageBusSubsystem.h"
#include "Core/KKGameplayTags.h"
#include "KayipKrallik.h"
#include "Components/StaticMeshComponent.h"
#include "Materials/MaterialInstanceDynamic.h"
#include "Engine/StaticMesh.h"
#include "Engine/World.h"
#include "Net/UnrealNetwork.h"

namespace
{
	const TCHAR* BuildCubeM   = TEXT("/Engine/BasicShapes/Cube.Cube");
	const TCHAR* BuildBaseMat = TEXT("/Engine/BasicShapes/BasicShapeMaterial.BasicShapeMaterial");

	UStaticMeshComponent* BuildAddPart(AActor* Owner, USceneComponent* Parent, const FLinearColor& Color,
	                              const FVector& RelLoc, const FVector& Scale, bool bCollide)
	{
		UStaticMeshComponent* C = NewObject<UStaticMeshComponent>(Owner);
		C->SetupAttachment(Parent);
		C->RegisterComponent();
		if (UStaticMesh* M = LoadObject<UStaticMesh>(nullptr, BuildCubeM)) C->SetStaticMesh(M);
		if (UMaterialInterface* Base = LoadObject<UMaterialInterface>(nullptr, BuildBaseMat))
		{
			UMaterialInstanceDynamic* MID = UMaterialInstanceDynamic::Create(Base, Owner);
			MID->SetVectorParameterValue(TEXT("Color"), Color);
			C->SetMaterial(0, MID);
		}
		C->SetRelativeLocation(RelLoc);
		C->SetRelativeScale3D(Scale);
		C->SetCollisionEnabled(bCollide ? ECollisionEnabled::QueryAndPhysics : ECollisionEnabled::NoCollision);
		C->SetCastShadow(false); // mobil bütçe 6.3
		C->SetCanEverAffectNavigation(true); // yapılar gölge yolunu KESER — kalenin var olma sebebi
		Owner->AddInstanceComponent(C);
		return C;
	}
}

AKKBuildable::AKKBuildable()
{
	PrimaryActorTick.bCanEverTick = true;        // yalnız sarsıntı anında etkinleşir
	PrimaryActorTick.bStartWithTickEnabled = false;
	bReplicates = true;
	SetReplicateMovement(false); // yerleştirildiği yerde kalır — bant genişliği sıfır
	NetDormancy = DORM_DormantAll;

	USceneComponent* Root = CreateDefaultSubobject<USceneComponent>(TEXT("Root"));
	SetRootComponent(Root);
}

void AKKBuildable::GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const
{
	Super::GetLifetimeReplicatedProps(OutLifetimeProps);
	DOREPLIFETIME(AKKBuildable, Type);
	DOREPLIFETIME(AKKBuildable, Tile);
	DOREPLIFETIME(AKKBuildable, bDoorOpen);
}

void AKKBuildable::InitBuild(EKKBuildType InType, FIntPoint InTile, const FVector& WorldLoc)
{
	check(HasAuthority());
	Type = InType;
	Tile = InTile;
	HP   = MaxHPFor(InType);
	SetActorLocation(WorldLoc);
	BuildVisual();
	// Sunucu ızgara kaydı BURADA: BeginPlay spawn anında koşar ve o anda Tile henüz
	// atanmamıştır — orada kaydolmak her yapıyı (0,0) anahtarına yazardı.
	if (UKKBuildGridSubsystem* Grid = GetWorld() ? GetWorld()->GetSubsystem<UKKBuildGridSubsystem>() : nullptr)
	{
		Grid->RegisterLocal(this);
	}
	FlushNetDormancy(); // ilk replikasyonu garanti et
}

void AKKBuildable::BeginPlay()
{
	Super::BeginPlay();

	// İstemci: replike Type geldiyse görseli kur (OnRep yarışına karşı çifte güvence).
	if (!bVisualBuilt && Type != EKKBuildType::None)
	{
		BuildVisual();
	}

	// İstemcide yerel ızgaraya kaydol (ilk replikasyon BeginPlay'den önce gelir, Tile geçerli) —
	// hayalet geçerliliği buradan okur. Sunucuda Type burada henüz None'dır; kayıt InitBuild'de yapılır.
	if (Type != EKKBuildType::None)
	{
		if (UKKBuildGridSubsystem* Grid = GetWorld() ? GetWorld()->GetSubsystem<UKKBuildGridSubsystem>() : nullptr)
		{
			Grid->RegisterLocal(this);
		}
	}

	// Yerleştirme sesi: spawn anı = yerleştirme anı (sunucu + tüm istemciler duyar).
	if (GetNetMode() != NM_DedicatedServer)
	{
		if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFX(this, FName("place"));
	}
}

void AKKBuildable::EndPlay(const EEndPlayReason::Type Reason)
{
	if (UKKBuildGridSubsystem* Grid = GetWorld() ? GetWorld()->GetSubsystem<UKKBuildGridSubsystem>() : nullptr)
	{
		Grid->UnregisterLocal(this);
	}
	Super::EndPlay(Reason);
}

void AKKBuildable::OnRep_Type()
{
	if (!bVisualBuilt && Type != EKKBuildType::None) BuildVisual();
}

void AKKBuildable::OnRep_DoorOpen()
{
	ApplyDoorState(/*bPlaySound=*/true);
}

void AKKBuildable::ServerToggleDoor()
{
	if (!HasAuthority() || Type != EKKBuildType::Door) return;
	bDoorOpen = !bDoorOpen;
	ApplyDoorState(true);     // dinleyen sunucu kendi görselini günceller
	FlushNetDormancy();       // uyuyan aktörü uyandır — değişiklik istemcilere aksın
}

void AKKBuildable::BuildVisual()
{
	bVisualBuilt = true;

	if (Type == EKKBuildType::Wall)
	{
		// İki sıra taş: alt koyu, üst açık ve hafif içerlek — SANAT-YONU "blok + tek renk" dili.
		// Karo 100 uu; eksen hizalı (duvarlar çizgi gibi okunmalı, jitter YOK).
		WallBase = BuildAddPart(this, GetRootComponent(), KKPalette::Hex(TEXT("7e8694")),
		                   FVector(0, 0, 65),  FVector(1.00f, 1.00f, 1.30f), true);
		WallTop  = BuildAddPart(this, GetRootComponent(), KKPalette::Hex(TEXT("a8b0bd")),
		                   FVector(0, 0, 175), FVector(0.92f, 0.92f, 0.90f), true);
	}
	else if (Type == EKKBuildType::Door)
	{
		// Çerçeve: iki ahşap dikme + lento; panel sol dikmedeki menteşeye bağlı döner.
		const FLinearColor Wood  = KKPalette::Hex(TEXT("6e4a2a"));
		const FLinearColor Panel = KKPalette::Hex(TEXT("8a5a32"));

		PostL  = BuildAddPart(this, GetRootComponent(), Wood, FVector(0, -46, 105), FVector(0.16f, 0.16f, 2.10f), true);
		PostR  = BuildAddPart(this, GetRootComponent(), Wood, FVector(0,  46, 105), FVector(0.16f, 0.16f, 2.10f), true);
		Lintel = BuildAddPart(this, GetRootComponent(), Wood, FVector(0,   0, 218), FVector(0.18f, 1.04f, 0.16f), true);

		Hinge = NewObject<USceneComponent>(this);
		Hinge->SetupAttachment(GetRootComponent());
		Hinge->RegisterComponent();
		Hinge->SetRelativeLocation(FVector(0, -38, 100)); // sol dikmenin iç yüzü
		AddInstanceComponent(Hinge);

		// Panel menteşeden Y+ yönüne uzanır: pivotu kenarda tutmak için yerel ofset = yarı genişlik.
		DoorPanel = BuildAddPart(this, Hinge, Panel, FVector(0, 38, 0), FVector(0.12f, 0.72f, 1.80f), true);

		ApplyDoorState(/*bPlaySound=*/false);
	}
}

void AKKBuildable::ReceiveKKDamage(float Amount, AActor* /*DamageInstigator*/)
{
	if (!HasAuthority() || HP <= 0.f) return;
	HP -= Amount;
	FlushNetDormancy();      // uyuyan aktörün multicast'i istemcilere ulaşsın
	Multicast_HitFX();       // dinleyen sunucu dahil herkes sarsıntı + ses alır

	if (HP <= 0.f)
	{
		// Yıkım: ızgara EndPlay->UnregisterLocal ile kendini temizler; istemciler aktör
		// kanalının kapanışından görür. DÜRÜST NOT: dedicated sunucuda yıkım SESİ
		// istemcide çalmaz (aktör çoktan gitti) — Faz 2 FX geçişinde çözülecek.
		if (GetNetMode() != NM_DedicatedServer)
		{
			if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this))
				A->PlaySFXAt(this, Type == EKKBuildType::Wall ? FName("mine") : FName("chop"), GetActorLocation());
		}
		if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(this))
		{
			FKKMessage M; M.Tag = KKTags::Build_Destroyed; M.IntValue = int32(Type); M.Location = GetActorLocation(); M.Source = this;
			Bus->Broadcast(M);
		}
		Destroy();
	}
}

void AKKBuildable::Multicast_HitFX_Implementation()
{
	ShakeT = 0.25f;
	ShakeOrigin = GetActorLocation();
	SetActorTickEnabled(true);
	if (GetNetMode() != NM_DedicatedServer)
	{
		if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this))
			A->PlaySFXAt(this, Type == EKKBuildType::Wall ? FName("mine") : FName("chop"), GetActorLocation());
	}
}

void AKKBuildable::Tick(float Dt)
{
	Super::Tick(Dt);
	if (ShakeT > 0.f)
	{
		ShakeT = FMath::Max(0.f, ShakeT - Dt);
		const float A = ShakeT / 0.25f; // sönerek azalan genlik
		const FVector J(FMath::FRandRange(-4.f, 4.f) * A, FMath::FRandRange(-4.f, 4.f) * A, 0.f);
		SetActorLocation(ShakeOrigin + J); // ReplicateMovement kapalı: sarsıntı tamamen yerel
		if (ShakeT <= 0.f)
		{
			SetActorLocation(ShakeOrigin);
			SetActorTickEnabled(false);
		}
	}
}

void AKKBuildable::ApplyDoorState(bool bPlaySound)
{
	if (Type != EKKBuildType::Door || !Hinge) return;

	// Açık: panel -100° savrulur ve çarpışması kapanır — oyuncu/gölge geçidi açılır.
	Hinge->SetRelativeRotation(FRotator(0.f, bDoorOpen ? -100.f : 0.f, 0.f));
	if (DoorPanel)
	{
		DoorPanel->SetCollisionEnabled(bDoorOpen ? ECollisionEnabled::NoCollision
		                                         : ECollisionEnabled::QueryAndPhysics);
	}
	if (bPlaySound && GetNetMode() != NM_DedicatedServer)
	{
		if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFX(this, FName("door"));
	}
}
