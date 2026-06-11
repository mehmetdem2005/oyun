// KKHeartStone.cpp — Kalp Taşı: süzülen altın çekirdek, gece feneri, yenilgi anıtı. [TKT-F1-010]
#include "World/KKHeartStone.h"
#include "World/KKWorldTypes.h"
#include "Core/KKMessageBusSubsystem.h"
#include "Core/KKGameplayTags.h"
#include "Core/KKTimeOfDaySubsystem.h"
#include "Audio/KKAudioSubsystem.h"
#include "KayipKrallik.h"
#include "Components/StaticMeshComponent.h"
#include "Components/PointLightComponent.h"
#include "Materials/MaterialInstanceDynamic.h"
#include "Engine/StaticMesh.h"
#include "Engine/World.h"
#include "Net/UnrealNetwork.h"

namespace
{
	const TCHAR* CubeM   = TEXT("/Engine/BasicShapes/Cube.Cube");
	const TCHAR* CylM    = TEXT("/Engine/BasicShapes/Cylinder.Cylinder");
	const TCHAR* BaseMat = TEXT("/Engine/BasicShapes/BasicShapeMaterial.BasicShapeMaterial");

	UStaticMeshComponent* AddPart(AActor* O, USceneComponent* P, const TCHAR* Mesh, const FLinearColor& Col,
	                              const FVector& Loc, const FVector& Scl, const FRotator& Rot,
	                              UMaterialInstanceDynamic** OutMID = nullptr)
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
			if (OutMID) *OutMID = MID;
		}
		C->SetRelativeLocation(Loc); C->SetRelativeScale3D(Scl); C->SetRelativeRotation(Rot);
		C->SetCollisionEnabled(ECollisionEnabled::QueryAndPhysics);
		C->SetCastShadow(false);
		O->AddInstanceComponent(C);
		return C;
	}
}

AKKHeartStone::AKKHeartStone()
{
	PrimaryActorTick.bCanEverTick = true; // süzülme her makinede; ucuz (tek aktör)
	bReplicates = true;
	SetReplicateMovement(false);
	// DİKKAT: Buildable'ın aksine UYUMAZ — HP barı HUD'da canlı akmalı, dormancy HP repini geciktirir.

	USceneComponent* Root = CreateDefaultSubobject<USceneComponent>(TEXT("Root"));
	SetRootComponent(Root);
}

void AKKHeartStone::GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const
{
	Super::GetLifetimeReplicatedProps(OutLifetimeProps);
	DOREPLIFETIME(AKKHeartStone, HP);
	DOREPLIFETIME(AKKHeartStone, MaxHP);
	DOREPLIFETIME(AKKHeartStone, bDestroyed);
}

void AKKHeartStone::BeginPlay()
{
	Super::BeginPlay();
	BuildVisual();
	if (bDestroyed) ApplyDestroyedVisual(); // geç katılan istemci yıkık hali doğru görsün
}

void AKKHeartStone::BuildVisual()
{
	// Kaide: taş halka + silindir sütun — duvarla aynı taş dili (SANAT-YONU 2).
	const FLinearColor Stone = KKPalette::Hex(TEXT("7e8694"));
	const FLinearColor Light = KKPalette::Hex(TEXT("a8b0bd"));
	const FLinearColor Gold  = KKPalette::Hex(TEXT("e8b73d")); // HUD ENERJİ altını = krallık enerjisi

	for (int32 i = 0; i < 6; ++i) // altıgen taş halka
	{
		const float A = i * PI / 3.f;
		AddPart(this, GetRootComponent(), CubeM, (i % 2) ? Light : Stone,
		        FVector(FMath::Cos(A) * 58.f, FMath::Sin(A) * 58.f, 14.f),
		        FVector(0.26f, 0.26f, 0.28f), FRotator(0, FMath::RadiansToDegrees(A), 0));
	}
	Pedestal = AddPart(this, GetRootComponent(), CylM, Stone,
	                   FVector(0, 0, 55), FVector(0.34f, 0.34f, 1.10f), FRotator::ZeroRotator);

	// Çekirdek: 45° yatırılmış küp = elmas siluet; süzülür ve döner.
	Gem = AddPart(this, GetRootComponent(), CubeM, Gold,
	              GemBaseLoc, FVector(0.42f), FRotator(45.f, 0.f, 45.f), &GemMID);
	Gem->SetCollisionEnabled(ECollisionEnabled::NoCollision); // vuruşlar kaideye; çekirdek kutsal

	GlowLight = NewObject<UPointLightComponent>(this);
	GlowLight->SetupAttachment(GetRootComponent());
	GlowLight->RegisterComponent();
	GlowLight->SetRelativeLocation(FVector(0, 0, 160));
	GlowLight->SetLightColor(KKPalette::Hex(TEXT("e8b73d")));
	GlowLight->SetAttenuationRadius(1100.f);
	GlowLight->SetIntensity(2600.f);
	GlowLight->SetCastShadows(false); // mobil bütçe 6.3
	AddInstanceComponent(GlowLight);
}

void AKKHeartStone::Tick(float Dt)
{
	Super::Tick(Dt);
	Phase += Dt;

	if (Gem && !bDestroyed)
	{
		// Süzülme + ağır dönüş: "canlı" hissi tek satır maliyetle.
		Gem->SetRelativeLocation(GemBaseLoc + FVector(0, 0, FMath::Sin(Phase * 1.6f) * 9.f));
		Gem->SetRelativeRotation(FRotator(45.f, FMath::Fmod(Phase * 24.f, 360.f), 45.f));
	}
	if (GlowLight)
	{
		// Gece fener parlar, gündüz kısılır + nabız — Kingdom'ın "eve dön" çağrısı.
		float Darkness = 0.f;
		if (const UKKTimeOfDaySubsystem* T = GetWorld()->GetSubsystem<UKKTimeOfDaySubsystem>())
			Darkness = T->GetDarkness01();
		const float Pulse = 1.f + 0.10f * FMath::Sin(Phase * 2.2f);
		GlowLight->SetIntensity(bDestroyed ? 0.f : (800.f + Darkness * 2400.f) * Pulse);
	}
	if (FlashT > 0.f && GemMID)
	{
		FlashT = FMath::Max(0.f, FlashT - Dt * 4.f);
		GemMID->SetVectorParameterValue(TEXT("Color"),
			FMath::Lerp(KKPalette::Hex(TEXT("e8b73d")), FLinearColor::White, FlashT));
	}
}

void AKKHeartStone::ReceiveKKDamage(float Amount, AActor* /*DamageInstigator*/)
{
	if (!HasAuthority() || bDestroyed) return;
	HP = FMath::Max(0.f, HP - Amount);
	Multicast_HitFX();

	if (HP <= 0.f)
	{
		bDestroyed = true;
		ApplyDestroyedVisual(); // dinleyen sunucu anında görür; istemciler OnRep ile
		if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(this))
		{
			Bus->BroadcastTag(KKTags::Game_HeartDestroyed);
		}
		UE_LOG(LogKK, Warning, TEXT("[Heart] KALP TASI DUSTU."));
	}
}

void AKKHeartStone::Multicast_HitFX_Implementation()
{
	FlashT = 1.f;
	if (GetNetMode() != NM_DedicatedServer)
	{
		if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFXAt(this, FName("heartHit"), GetActorLocation());
	}
}

void AKKHeartStone::OnRep_Destroyed()
{
	if (bDestroyed) ApplyDestroyedVisual();
}

void AKKHeartStone::ApplyDestroyedVisual()
{
	// Anıt hali: çekirdek kararır, yere çöker, ışık söner. Dünya yenilgiyi hatırlar.
	if (Gem)
	{
		Gem->SetRelativeLocation(FVector(0, 0, 96));
		Gem->SetRelativeRotation(FRotator(45.f, 20.f, 45.f));
		if (GemMID) GemMID->SetVectorParameterValue(TEXT("Color"), KKPalette::Hex(TEXT("7e8694")));
	}
	if (GetNetMode() != NM_DedicatedServer)
	{
		if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFXAt(this, FName("heartDie"), GetActorLocation());
	}
}
