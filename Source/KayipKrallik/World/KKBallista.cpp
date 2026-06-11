// KKBallista.cpp — Balista: ahşap kule + dönen gergi kolu + altın-çelik cıvata izi. [TKT-F1-012]
#include "World/KKBallista.h"
#include "World/KKWorldTypes.h"
#include "Enemy/KKShadowEnemy.h"
#include "Audio/KKAudioSubsystem.h"
#include "KayipKrallik.h"
#include "Components/StaticMeshComponent.h"
#include "Materials/MaterialInstanceDynamic.h"
#include "Engine/StaticMesh.h"
#include "Engine/World.h"
#include "EngineUtils.h"
#include "TimerManager.h"

namespace
{
	const TCHAR* BallistaCubeM   = TEXT("/Engine/BasicShapes/Cube.Cube");
	const TCHAR* BallistaCylM    = TEXT("/Engine/BasicShapes/Cylinder.Cylinder");
	const TCHAR* BallistaBaseMat = TEXT("/Engine/BasicShapes/BasicShapeMaterial.BasicShapeMaterial");

	UStaticMeshComponent* BallistaPart(AActor* O, USceneComponent* P, const TCHAR* Mesh, const FLinearColor& Col,
	                           const FVector& Loc, const FVector& Scl, const FRotator& Rot, bool bCollide)
	{
		UStaticMeshComponent* C = NewObject<UStaticMeshComponent>(O);
		C->SetupAttachment(P);
		C->RegisterComponent();
		if (UStaticMesh* M = LoadObject<UStaticMesh>(nullptr, Mesh)) C->SetStaticMesh(M);
		if (UMaterialInterface* B = LoadObject<UMaterialInterface>(nullptr, BallistaBaseMat))
		{
			UMaterialInstanceDynamic* MID = UMaterialInstanceDynamic::Create(B, O);
			MID->SetVectorParameterValue(TEXT("Color"), Col);
			C->SetMaterial(0, MID);
		}
		C->SetRelativeLocation(Loc); C->SetRelativeScale3D(Scl); C->SetRelativeRotation(Rot);
		C->SetCollisionEnabled(bCollide ? ECollisionEnabled::QueryAndPhysics : ECollisionEnabled::NoCollision);
		C->SetCastShadow(false);
		O->AddInstanceComponent(C);
		return C;
	}
}

AKKBallista::AKKBallista()
{
	// Taban "yalnız sarsıntıda tik" der; balista her zaman tarar/nişan alır.
	PrimaryActorTick.bStartWithTickEnabled = true;
}

void AKKBallista::BuildVisual()
{
	bVisualBuilt = true;
	const FLinearColor Wood  = KKPalette::Hex(TEXT("6e4a2a"));
	const FLinearColor Panel = KKPalette::Hex(TEXT("8a5a32"));
	const FLinearColor Steel = KKPalette::Hex(TEXT("a8b0bd"));

	// Kaide: çapraz ahşap ayaklar + platform — duvarla aynı karo dilinde, yapı GÖVDESİ çarpışır.
	BallistaPart(this, GetRootComponent(), BallistaCubeM, Wood,  FVector(0, 0, 32), FVector(0.82f, 0.82f, 0.64f), FRotator::ZeroRotator, true);
	BallistaPart(this, GetRootComponent(), BallistaCubeM, Panel, FVector(0, 0, 68), FVector(0.58f, 0.58f, 0.10f), FRotator(0, 45, 0), false);

	// Nişan pivotu: yatay dönüş burada — kol + cıvata buna bağlı.
	Pivot = NewObject<USceneComponent>(this);
	Pivot->SetupAttachment(GetRootComponent());
	Pivot->RegisterComponent();
	Pivot->SetRelativeLocation(FVector(0, 0, 84));
	AddInstanceComponent(Pivot);

	// Gergi kolu: hafif yukarı açılı uzun kiriş; ucu = namlu (X+ ileri).
	Arm = BallistaPart(this, Pivot, BallistaCubeM, Wood, FVector(22, 0, 10), FVector(0.78f, 0.10f, 0.10f), FRotator(-8, 0, 0), false);
	// Yay kanatları: kısa çapraz çubuklar — siluet "balista" diye okunsun.
	BallistaPart(this, Pivot, BallistaCylM, Panel, FVector(2, 0, 12), FVector(0.06f, 0.06f, 0.62f), FRotator(90, 0, 90), false);

	// Tek cıvata: yeniden kullanılır, boşta gizli — çelik gövde.
	BoltMesh = BallistaPart(this, GetRootComponent(), BallistaCubeM, Steel, FVector::ZeroVector, FVector(0.42f, 0.045f, 0.045f), FRotator::ZeroRotator, false);
	BoltMesh->SetUsingAbsoluteLocation(true);
	BoltMesh->SetUsingAbsoluteRotation(true);
	BoltMesh->SetVisibility(false);
}

void AKKBallista::Tick(float Dt)
{
	Super::Tick(Dt); // sarsıntı (vurulunca) tabandan gelir

	// --- Kozmetik cıvata uçuşu (tüm makineler) ---
	if (BoltT >= 0.f && BoltMesh)
	{
		BoltT += Dt;
		const float A = FMath::Clamp(BoltT / FlightDur, 0.f, 1.f);
		const FVector P = FMath::Lerp(BoltFrom, BoltTo, A);
		BoltMesh->SetWorldLocation(P);
		BoltMesh->SetWorldRotation((BoltTo - BoltFrom).Rotation());
		if (A >= 1.f) { BoltMesh->SetVisibility(false); BoltT = -1.f; }
	}

	// --- Boşta tarama: hedef yokken ağır dönüş — "uyanık nöbetçi" hissi (tüm makineler) ---
	if (Pivot && BoltT < 0.f && !PendingTarget.IsValid())
	{
		IdleScan += Dt * 18.f;
		Pivot->SetRelativeRotation(FRotator(0.f, IdleScan, 0.f));
	}

	// --- Sunucu: hedef bul + ateşle ---
	if (!HasAuthority() || !IsKKAlive()) return;
	FireCd = FMath::Max(0.f, FireCd - Dt);
	if (FireCd <= 0.f) ServerAcquireAndFire();
}

void AKKBallista::ServerAcquireAndFire()
{
	AKKShadowEnemy* Best = nullptr;
	float BestD2 = Range * Range;
	for (TActorIterator<AKKShadowEnemy> It(GetWorld()); It; ++It) // gece tavanı 18 aktör — iterasyon ucuz
	{
		if (!It->IsKKAlive()) continue;
		const float D2 = FVector::DistSquared2D(GetActorLocation(), It->GetActorLocation());
		if (D2 < BestD2) { BestD2 = D2; Best = *It; }
	}
	if (!Best) return;

	PendingTarget = Best;
	FireCd = FireDelay;
	Multicast_FireFX(Best->GetActorLocation());
	GetWorldTimerManager().SetTimer(HitTimer, this, &AKKBallista::ApplyDelayedHit, FlightDur, false);
}

void AKKBallista::ApplyDelayedHit()
{
	AKKShadowEnemy* T = PendingTarget.Get();
	PendingTarget = nullptr;
	if (!T || !T->IsKKAlive()) return;
	// Uçuş payı: hedef bu sürede biraz kaymış olabilir — menzil+150 toleransı dürüst orta yol.
	if (FVector::DistSquared2D(GetActorLocation(), T->GetActorLocation()) > FMath::Square(Range + 150.f)) return;
	T->ReceiveKKDamage(BoltDamage, this);
}

void AKKBallista::Multicast_FireFX_Implementation(FVector TargetLoc)
{
	const FVector Muzzle = GetActorLocation() + FVector(0, 0, 96);
	if (Pivot)
	{
		FRotator Aim = (TargetLoc - GetActorLocation()).Rotation();
		Pivot->SetRelativeRotation(FRotator(0.f, Aim.Yaw, 0.f)); // anlık nişan; tween Faz 2 cilası
	}
	BoltFrom = Muzzle;
	BoltTo   = TargetLoc + FVector(0, 0, 40.f); // gölgenin gövde hizası
	BoltT    = 0.f;
	if (BoltMesh) BoltMesh->SetVisibility(true);

	if (GetNetMode() != NM_DedicatedServer)
	{
		if (UKKAudioSubsystem* A = UKKAudioSubsystem::Get(this)) A->PlaySFXAt(this, FName("bolt"), Muzzle);
	}
}
