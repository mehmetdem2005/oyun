// KKBallista.h — Balista: gece kuşatmasına ilk otomatik cevap (plan Faz 1 "4 yapı"). [TKT-F1-012]
// Sunucu hedefler ve hasar verir; nişan/cıvata tüm makinelerde kozmetik akar.
// Tasarım gerilimi: 80 HP — gölgeler onu da söker; duvar ARKASINA kurulmazsa gece yarısı susar.
#pragma once

#include "CoreMinimal.h"
#include "World/KKBuildable.h"
#include "KKBallista.generated.h"

class AKKShadowEnemy;

UCLASS()
class KAYIPKRALLIK_API AKKBallista : public AKKBuildable
{
	GENERATED_BODY()

public:
	AKKBallista();

protected:
	virtual void Tick(float Dt) override;       // Super = sarsıntı; üstüne tarama/nişan/cıvata
	virtual void BuildVisual() override;        // taban yerine kule silueti

	/** Tüm makineler: nişanı hedefe çevir + cıvata kozmetiğini başlat + ses. */
	UFUNCTION(NetMulticast, Unreliable) void Multicast_FireFX(FVector TargetLoc);
	void Multicast_FireFX_Implementation(FVector TargetLoc);

	void ServerAcquireAndFire();
	void ApplyDelayedHit();                      // 0.22 sn uçuş sonrası isabet (sunucu)

	UPROPERTY() TObjectPtr<USceneComponent>      Pivot;     // yaw nişanı
	UPROPERTY() TObjectPtr<UStaticMeshComponent> Arm;       // gergi kolu
	UPROPERTY() TObjectPtr<UStaticMeshComponent> BoltMesh;  // tek yeniden kullanılan cıvata (sıfır spawn maliyeti)

	TWeakObjectPtr<AKKShadowEnemy> PendingTarget; // uçuş sırasında hedef (sunucu)
	FTimerHandle HitTimer;

	float FireCd = 0.f;          // sunucu atış ritmi
	float BoltT = -1.f;          // kozmetik uçuş: <0 = boşta
	FVector BoltFrom, BoltTo;
	float IdleScan = 0.f;        // hedef yokken ağır tarama dönüşü

	static constexpr float Range      = 950.f;
	static constexpr float FireDelay  = 2.5f;
	static constexpr float BoltDamage = 12.f;   // gölge 30 HP -> 3 cıvata
	static constexpr float FlightDur  = 0.22f;
};
