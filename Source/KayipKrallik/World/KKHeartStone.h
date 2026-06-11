// KKHeartStone.h — Kalp Taşı: krallığın kalbi, kuşatmanın gerçek hedefi (plan Faz 1). [TKT-F1-010]
// Düşerse oyun biter; ona kadar her duvar, her kapı bu taş için vardır.
// Yıkılınca YOK OLMAZ — kararmış haliyle dünyada kalır (yenilginin anıtı, KO dürüstlüğü).
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "Core/KKDamageable.h"
#include "KKHeartStone.generated.h"

class UStaticMeshComponent;
class UPointLightComponent;

UCLASS()
class KAYIPKRALLIK_API AKKHeartStone : public AActor, public IKKDamageable
{
	GENERATED_BODY()

public:
	AKKHeartStone();

	// ---- IKKDamageable ----
	virtual void ReceiveKKDamage(float Amount, AActor* DamageInstigator) override;
	virtual bool IsKKAlive() const override { return !bDestroyed; }

	float GetHPPct() const { return MaxHP > 0.f ? FMath::Clamp(HP / MaxHP, 0.f, 1.f) : 0.f; }
	bool  IsDestroyed() const { return bDestroyed; }

protected:
	virtual void BeginPlay() override;
	virtual void Tick(float Dt) override;
	virtual void GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const override;

	/** HP replike: HUD kalp barı istemcide de doğru akar (sunucu otoritesi, salt okunur tüketim). */
	UPROPERTY(Replicated)                       float HP = 300.f;
	UPROPERTY(Replicated)                       float MaxHP = 300.f;
	UPROPERTY(ReplicatedUsing = OnRep_Destroyed) bool bDestroyed = false;

	UFUNCTION() void OnRep_Destroyed();

	UFUNCTION(NetMulticast, Unreliable) void Multicast_HitFX();
	void Multicast_HitFX_Implementation();

	void BuildVisual();
	void ApplyDestroyedVisual();

	UPROPERTY() TObjectPtr<UStaticMeshComponent> Pedestal;
	UPROPERTY() TObjectPtr<UStaticMeshComponent> Gem;        // havada süzülen altın çekirdek
	UPROPERTY() TObjectPtr<UPointLightComponent> GlowLight;  // gece fener: "eve dön" çağrısı
	UPROPERTY() TObjectPtr<class UMaterialInstanceDynamic> GemMID;

	float Phase = 0.f;     // süzülme + dönüş
	float FlashT = 0.f;    // vuruş flaşı
	FVector GemBaseLoc = FVector(0, 0, 150);
};
