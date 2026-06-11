// KKCampfire.cpp
#include "World/KKCampfire.h"
#include "World/KKWorldTypes.h"
#include "Core/KKTimeOfDaySubsystem.h"
#include "Components/PointLightComponent.h"
#include "Components/StaticMeshComponent.h"
#include "Materials/MaterialInstanceDynamic.h"
#include "Engine/StaticMesh.h"
#include "Engine/World.h"

AKKCampfire::AKKCampfire()
{
	PrimaryActorTick.bCanEverTick = true;
	bReplicates = true;
	SetReplicateMovement(false);

	Root = CreateDefaultSubobject<USceneComponent>(TEXT("Root"));
	SetRootComponent(Root);

	Light = CreateDefaultSubobject<UPointLightComponent>(TEXT("Light"));
	Light->SetupAttachment(Root);
	Light->SetRelativeLocation(FVector(0, 0, 70));
	Light->SetLightColor(KKPalette::Hex(TEXT("ffb45e"))); // KO meşale turuncusu
	Light->SetAttenuationRadius(820.f);
	Light->SetIntensity(4200.f);
	Light->SetCastShadows(false); // mobil bütçe 6.3: dinamik gölge yok
}

void AKKCampfire::BeginPlay()
{
	Super::BeginPlay();
	BuildVisual();
	Phase = FMath::FRandRange(0.f, 6.28f);
}

void AKKCampfire::BuildVisual()
{
	using namespace KKPalette;
	auto Add = [this](const TCHAR* Mesh, const FLinearColor& C, FVector L, FVector S, FRotator R, bool bCol) -> UStaticMeshComponent*
	{
		UStaticMeshComponent* P = NewObject<UStaticMeshComponent>(this);
		P->SetupAttachment(Root);
		P->RegisterComponent();
		if (UStaticMesh* M = LoadObject<UStaticMesh>(nullptr, Mesh)) P->SetStaticMesh(M);
		if (UMaterialInterface* B = LoadObject<UMaterialInterface>(nullptr, TEXT("/Engine/BasicShapes/BasicShapeMaterial.BasicShapeMaterial")))
		{
			UMaterialInstanceDynamic* MID = UMaterialInstanceDynamic::Create(B, this);
			MID->SetVectorParameterValue(TEXT("Color"), C);
			P->SetMaterial(0, MID);
		}
		P->SetRelativeLocation(L); P->SetRelativeScale3D(S); P->SetRelativeRotation(R);
		P->SetCollisionEnabled(bCol ? ECollisionEnabled::QueryAndPhysics : ECollisionEnabled::NoCollision);
		P->SetCanEverAffectNavigation(false);
		AddInstanceComponent(P);
		return P;
	};

	// Taş çember (KO drawFire: 7 taş)
	for (int32 i = 0; i < 7; ++i)
	{
		const float A = (float)i / 7.f * 2.f * PI;
		Add(TEXT("/Engine/BasicShapes/Cube.Cube"), Hex(TEXT("8b8f99")),
			FVector(FMath::Cos(A) * 62.f, FMath::Sin(A) * 62.f, 9.f),
			FVector(0.18f, 0.14f, 0.14f), FRotator(0, FMath::RadiansToDegrees(A), 0), true);
	}
	// Çapraz kütükler (#6e4525)
	Add(TEXT("/Engine/BasicShapes/Cylinder.Cylinder"), Hex(TEXT("6e4525")), FVector(0, 0, 14), FVector(0.1f, 0.1f, 0.9f), FRotator(90, 28, 0), false);
	Add(TEXT("/Engine/BasicShapes/Cylinder.Cylinder"), Hex(TEXT("6e4525")), FVector(0, 0, 16), FVector(0.1f, 0.1f, 0.9f), FRotator(90, -32, 0), false);
	// Alev konileri (#ff8a23 / #ffc23d / #fff1a8)
	Flame      = Add(TEXT("/Engine/BasicShapes/Cone.Cone"), Hex(TEXT("ff8a23")), FVector(0, 0, 52), FVector(0.5f, 0.5f, 0.85f), FRotator::ZeroRotator, false);
	FlameInner = Add(TEXT("/Engine/BasicShapes/Cone.Cone"), Hex(TEXT("ffc23d")), FVector(0, 0, 58), FVector(0.3f, 0.3f, 0.55f), FRotator::ZeroRotator, false);
	Add(TEXT("/Engine/BasicShapes/Cone.Cone"), Hex(TEXT("fff1a8")), FVector(0, 0, 62), FVector(0.14f, 0.14f, 0.3f), FRotator::ZeroRotator, false);
}

void AKKCampfire::Tick(float Dt)
{
	Super::Tick(Dt);
	Phase += Dt;

	// KO alev titremesi: iki sinüsün toplamı.
	const float Fl = FMath::Sin(Phase * 10.f) * 0.10f + FMath::Sin(Phase * 23.f) * 0.06f;
	if (Flame)      Flame->SetRelativeScale3D(FVector(0.5f, 0.5f, 0.85f + Fl));
	if (FlameInner) FlameInner->SetRelativeScale3D(FVector(0.3f, 0.3f, 0.55f + Fl * 0.7f));

	// Gündüz ışık kısılır (bütçe + estetik), gece tam güç + titreme.
	float Dark = 1.f;
	if (const UKKTimeOfDaySubsystem* Time = GetWorld()->GetSubsystem<UKKTimeOfDaySubsystem>())
	{
		Dark = FMath::Max(0.12f, Time->GetDarkness01());
	}
	if (Light) Light->SetIntensity((4200.f + Fl * 2600.f) * Dark);
}
