// KKBuildable.h — Yerleştirilmiş yapı aktörü: Duvar / Kapı (plan 7.2). [TKT-F1-002]
// Görsel: motor temel şekilleri + KO paleti (SANAT-YONU 2). Kapı paneli açılınca çarpışmasını kapatır.
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "World/KKBuildTypes.h"
#include "Core/KKDamageable.h"
#include "KKBuildable.generated.h"

class UStaticMeshComponent;

UCLASS()
class KAYIPKRALLIK_API AKKBuildable : public AActor, public IKKDamageable
{
	GENERATED_BODY()

public:
	AKKBuildable();

	/** Sunucu: tip + karo ata, dünyaya yerleştir. Spawn sonrası BİR KEZ çağrılır. */
	void InitBuild(EKKBuildType InType, FIntPoint InTile, const FVector& WorldLoc);

	/** Sunucu: kapıyı aç/kapat. Duvarda sessizce yok sayılır. */
	void ServerToggleDoor();

	// ---- IKKDamageable: gölgeler duvarı kemirir (Faz 2 kuşatmasının önizlemesi) ----
	virtual void ReceiveKKDamage(float Amount, AActor* DamageInstigator) override;
	virtual bool IsKKAlive() const override { return HP > 0.f; }
	static float MaxHPFor(EKKBuildType T)
	{
		switch (T)
		{
		case EKKBuildType::Wall:     return 120.f;
		case EKKBuildType::Ballista: return 80.f;  // değerli ama kırılgan — duvar ARKASINA kur
		default:                     return 60.f;  // kapı
		}
	}

	EKKBuildType GetBuildType() const { return Type; }
	FIntPoint    GetTile() const { return Tile; }
	bool         IsDoorOpen() const { return bDoorOpen; }

protected:
	virtual void BeginPlay() override;
	virtual void Tick(float Dt) override; // yalnız sarsıntı sırasında açık (varsayılan kapalı)
	virtual void EndPlay(const EEndPlayReason::Type Reason) override;
	virtual void GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const override;

	UPROPERTY(ReplicatedUsing = OnRep_Type) EKKBuildType Type = EKKBuildType::None;
	UPROPERTY(Replicated)                   FIntPoint    Tile = FIntPoint::ZeroValue;
	UPROPERTY(ReplicatedUsing = OnRep_DoorOpen) bool     bDoorOpen = false;

	UFUNCTION() void OnRep_Type();
	UFUNCTION() void OnRep_DoorOpen();

	UFUNCTION(NetMulticast, Unreliable) void Multicast_HitFX();
	void Multicast_HitFX_Implementation();

	virtual void BuildVisual(); // alt sınıflar (Balista) kendi siluetini kurar
	void ApplyDoorState(bool bPlaySound);

	UPROPERTY() TObjectPtr<UStaticMeshComponent> WallBase;
	UPROPERTY() TObjectPtr<UStaticMeshComponent> WallTop;
	UPROPERTY() TObjectPtr<UStaticMeshComponent> PostL;
	UPROPERTY() TObjectPtr<UStaticMeshComponent> PostR;
	UPROPERTY() TObjectPtr<UStaticMeshComponent> Lintel;
	UPROPERTY() TObjectPtr<USceneComponent>      Hinge;     // panel bu pivota bağlı döner
	UPROPERTY() TObjectPtr<UStaticMeshComponent> DoorPanel;

	bool bVisualBuilt = false;

	float   HP = 0.f;           // sunucu otoritesi; istemci yıkımı aktör yok oluşundan görür
	float   ShakeT = 0.f;       // vuruş sarsıntısı sayacı (yerel kozmetik)
	FVector ShakeOrigin = FVector::ZeroVector;
};
