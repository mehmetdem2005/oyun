// KKWorldStreamer.cpp
#include "World/KKWorldStreamer.h"
#include "World/KKWorldGenSubsystem.h"
#include "World/KKResourceNode.h"
#include "World/KKCampfire.h"
#include "KayipKrallik.h"
#include "Components/HierarchicalInstancedStaticMeshComponent.h"
#include "Materials/MaterialInstanceDynamic.h"
#include "Engine/StaticMesh.h"
#include "Engine/World.h"
#include "Kismet/GameplayStatics.h"

AKKWorldStreamer::AKKWorldStreamer()
{
	PrimaryActorTick.bCanEverTick = true;
	PrimaryActorTick.TickInterval = 0.25f;
	bReplicates = false; // tamamen yerel görsel sistem (plan 102: arazi sync edilmez)
	SetRootComponent(CreateDefaultSubobject<USceneComponent>(TEXT("Root")));
}

void AKKWorldStreamer::BeginPlay()
{
	Super::BeginPlay();
	EnsureAssets();
}

void AKKWorldStreamer::EnsureAssets()
{
	if (CubeMesh) return;
	CubeMesh = LoadObject<UStaticMesh>(nullptr, TEXT("/Engine/BasicShapes/Cube.Cube"));

	for (uint8 i = 0; i <= (uint8)EKKTile::Camp; ++i)
	{
		TileMIDs.Add(i, MakeMID(KKPalette::Tile((EKKTile)i)));
	}
	// KO paintCell dekor renkleri:
	TuftMID       = MakeMID(KKPalette::Hex(TEXT("357a44")));
	FlowerGoldMID = MakeMID(KKPalette::Hex(TEXT("e9d96a")));
	FlowerPinkMID = MakeMID(KKPalette::Hex(TEXT("e8a0c0")));
}

UMaterialInstanceDynamic* AKKWorldStreamer::MakeMID(const FLinearColor& C)
{
	UMaterialInterface* Base = LoadObject<UMaterialInterface>(nullptr, TEXT("/Engine/BasicShapes/BasicShapeMaterial.BasicShapeMaterial"));
	UMaterialInstanceDynamic* MID = UMaterialInstanceDynamic::Create(Base, this);
	if (MID) MID->SetVectorParameterValue(TEXT("Color"), C);
	return MID;
}

UHierarchicalInstancedStaticMeshComponent* AKKWorldStreamer::MakeHISM(UMaterialInstanceDynamic* MID, bool bCollide)
{
	UHierarchicalInstancedStaticMeshComponent* H = NewObject<UHierarchicalInstancedStaticMeshComponent>(this);
	H->SetupAttachment(GetRootComponent());
	H->RegisterComponent();
	H->SetStaticMesh(CubeMesh);
	H->SetMaterial(0, MID);
	H->SetCollisionEnabled(bCollide ? ECollisionEnabled::QueryAndPhysics : ECollisionEnabled::NoCollision);
	H->SetCanEverAffectNavigation(false);
	H->SetCastShadow(false); // mobil bütçe: zemin gölgesi kapalı
	AddInstanceComponent(H);
	return H;
}

FIntPoint AKKWorldStreamer::PlayerChunk() const
{
	const APawn* P = UGameplayStatics::GetPlayerPawn(GetWorld(), 0);
	const UKKWorldGenSubsystem* Gen = GetWorld()->GetSubsystem<UKKWorldGenSubsystem>();
	if (!P || !Gen) return FIntPoint::ZeroValue;
	const FIntPoint T = Gen->WorldToTile(P->GetActorLocation());
	return FIntPoint(FMath::FloorToInt32((float)T.X / UKKWorldGenSubsystem::ChunkTiles),
	                 FMath::FloorToInt32((float)T.Y / UKKWorldGenSubsystem::ChunkTiles));
}

void AKKWorldStreamer::ForceBuildAround(const FVector& WorldLoc, int32 Radius)
{
	EnsureAssets();
	const UKKWorldGenSubsystem* Gen = GetWorld()->GetSubsystem<UKKWorldGenSubsystem>();
	if (!Gen) return;
	const FIntPoint T = Gen->WorldToTile(WorldLoc);
	const FIntPoint C(FMath::FloorToInt32((float)T.X / UKKWorldGenSubsystem::ChunkTiles),
	                  FMath::FloorToInt32((float)T.Y / UKKWorldGenSubsystem::ChunkTiles));
	for (int32 dy = -Radius; dy <= Radius; ++dy)
		for (int32 dx = -Radius; dx <= Radius; ++dx)
			BuildChunk(C + FIntPoint(dx, dy));
}

void AKKWorldStreamer::Tick(float Dt)
{
	Super::Tick(Dt);
	ScanAndQueue();

	int32 Built = 0;
	while (BuildQueue.Num() > 0 && Built < MaxBuildsPerTick)
	{
		const FIntPoint C = BuildQueue[0];
		BuildQueue.RemoveAt(0);
		if (!Loaded.Contains(C)) { BuildChunk(C); ++Built; }
	}
}

void AKKWorldStreamer::ScanAndQueue()
{
	const FIntPoint PC = PlayerChunk();

	// Yükleme adayları (yakından uzağa).
	for (int32 R = 0; R <= LoadRadius; ++R)
		for (int32 dy = -R; dy <= R; ++dy)
			for (int32 dx = -R; dx <= R; ++dx)
			{
				if (FMath::Max(FMath::Abs(dx), FMath::Abs(dy)) != R) continue;
				const FIntPoint C = PC + FIntPoint(dx, dy);
				if (!Loaded.Contains(C) && !BuildQueue.Contains(C)) BuildQueue.Add(C);
			}

	// Boşaltma (KO sweep eşleniği).
	TArray<FIntPoint> ToUnload;
	for (const TPair<FIntPoint, FKKChunkVisual>& P : Loaded)
	{
		const FIntPoint D = P.Key - PC;
		if (FMath::Max(FMath::Abs(D.X), FMath::Abs(D.Y)) > UnloadRadius) ToUnload.Add(P.Key);
	}
	for (const FIntPoint& C : ToUnload) UnloadChunk(C);
}

void AKKWorldStreamer::BuildChunk(const FIntPoint& Chunk)
{
	UKKWorldGenSubsystem* Gen = GetWorld()->GetSubsystem<UKKWorldGenSubsystem>();
	if (!Gen || Loaded.Contains(Chunk)) return;
	EnsureAssets();

	constexpr int32 N = UKKWorldGenSubsystem::ChunkTiles;
	constexpr float TS = UKKWorldGenSubsystem::TileSize;

	FKKChunkVisual Vis;
	TMap<uint8, UHierarchicalInstancedStaticMeshComponent*> Ground;
	UHierarchicalInstancedStaticMeshComponent* Tufts = nullptr;
	UHierarchicalInstancedStaticMeshComponent* FlG = nullptr;
	UHierarchicalInstancedStaticMeshComponent* FlP = nullptr;

	const bool bAuth = HasAuthority();

	for (int32 ly = 0; ly < N; ++ly)
	for (int32 lx = 0; lx < N; ++lx)
	{
		const int32 TX = Chunk.X * N + lx;
		const int32 TY = Chunk.Y * N + ly;
		const EKKTile Tile = Gen->GetTile(TX, TY);

		// --- Zemin küpü ---
		UHierarchicalInstancedStaticMeshComponent*& G = Ground.FindOrAdd((uint8)Tile);
		if (!G) { G = MakeHISM(TileMIDs[(uint8)Tile], true); Vis.Comps.Add(G); }
		const bool bWater = (Tile == EKKTile::Deep || Tile == EKKTile::Water);
		const float TopZ = bWater ? ((Tile == EKKTile::Deep) ? -26.f : -16.f) : 0.f; // kıyı basamağı (estetik)
		FTransform T(FRotator::ZeroRotator,
		             FVector((TX + 0.5f) * TS, (TY + 0.5f) * TS, TopZ - 5.f),
		             FVector(1.02f, 1.02f, 0.10f));
		G->AddInstance(T, /*bWorldSpace*/ true);

		// --- Dekor (KO paintCell süslemeleri: çim tutamı, çiçek) ---
		const double R1 = Gen->Hash01(TX, TY, 91);
		const double R2 = Gen->Hash01(TX, TY, 92);
		const double R3 = Gen->Hash01(TX, TY, 93);
		if ((Tile == EKKTile::Grass || Tile == EKKTile::DarkGrass) && R3 > 0.7)
		{
			if (!Tufts) { Tufts = MakeHISM(TuftMID, false); Vis.Comps.Add(Tufts); }
			Tufts->AddInstance(FTransform(FRotator(0, R1 * 360.0, 0),
				FVector(TX * TS + R1 * TS, TY * TS + R2 * TS, 7.f),
				FVector(0.07f, 0.07f, 0.16f)), true);
		}
		if (Tile == EKKTile::Grass && R1 > 0.94)
		{
			UHierarchicalInstancedStaticMeshComponent*& F = (R2 > 0.5) ? FlG : FlP;
			if (!F) { F = MakeHISM(R2 > 0.5 ? FlowerGoldMID : FlowerPinkMID, false); Vis.Comps.Add(F); }
			F->AddInstance(FTransform(FRotator::ZeroRotator,
				FVector(TX * TS + R2 * TS, TY * TS + R3 * TS, 6.f),
				FVector(0.06f, 0.06f, 0.06f)), true);
		}

		// --- Sunucu varlıkları: kaynak düğümleri + kamp ateşi ---
		if (bAuth)
		{
			const FKKResourceSpec Spec = Gen->GetResourceAt(TX, TY);
			if (Spec.Type != EKKResource::None)
			{
				AKKResourceNode* Node = GetWorld()->SpawnActor<AKKResourceNode>();
				if (Node)
				{
					Node->InitNode(Spec.Type, FIntPoint(TX, TY), Spec.Variant, Spec.JitterUU);
					Vis.OwnedActors.Add(Node);
				}
			}
			FIntPoint CampC;
			if (Gen->CampInfo(FMath::FloorToInt32(double(TX) / 28.0),
			                  FMath::FloorToInt32(double(TY) / 28.0), CampC)
			    && CampC.X == TX && CampC.Y == TY)
			{
				AKKCampfire* Fire = GetWorld()->SpawnActor<AKKCampfire>(
					AKKCampfire::StaticClass(), Gen->TileToWorld(TX, TY, 0.f), FRotator::ZeroRotator);
				if (Fire) Vis.OwnedActors.Add(Fire);
			}
		}
	}

	Loaded.Add(Chunk, MoveTemp(Vis));
}

void AKKWorldStreamer::UnloadChunk(const FIntPoint& Chunk)
{
	FKKChunkVisual* Vis = Loaded.Find(Chunk);
	if (!Vis) return;
	for (UHierarchicalInstancedStaticMeshComponent* C : Vis->Comps)
	{
		if (C) C->DestroyComponent();
	}
	if (HasAuthority())
	{
		// Düğüm durumu WorldGen hasat haritasında yaşar; aktör güvenle yok edilir (KO sweep).
		for (AActor* A : Vis->OwnedActors) if (IsValid(A)) A->Destroy();
	}
	Loaded.Remove(Chunk);
}
