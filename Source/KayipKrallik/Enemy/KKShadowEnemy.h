// KKShadowEnemy.h — Gece gölgesi: KO drawEnemy'nin 3B'si (#221440 gövde, beyaz gözler). [TKT-F0-019]
// Faz 0: ham C++ kovalama FSM'i. Faz 2'de StateTree + EQS'e taşınır (plan 6.2 notu) —
// bu sınıfın dış sözleşmesi (spawn/dawn-death/damage) o geçişte DEĞİŞMEZ.
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Character.h"
#include "Core/KKDamageable.h"
#include "KKShadowEnemy.generated.h"

class UStaticMeshComponent;
class UMaterialInstanceDynamic;

UCLASS()
class KAYIPKRALLIK_API AKKShadowEnemy : public ACharacter, public IKKDamageable
{
	GENERATED_BODY()

public:
	AKKShadowEnemy();

	virtual void Tick(float Dt) override;
	virtual void GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const override;

	virtual void ReceiveKKDamage(float Amount, AActor* DamageInstigator) override;
	virtual bool IsKKAlive() const override { return !bDying && HP > 0.f; }

	/** Şafak emri (spawner çağırır, sunucu). */
	void ForceDawnDeath();

protected:
	virtual void BeginPlay() override;

	UPROPERTY() TObjectPtr<UStaticMeshComponent> Body;
	UPROPERTY() TObjectPtr<UStaticMeshComponent> EyeL;
	UPROPERTY() TObjectPtr<UStaticMeshComponent> EyeR;
	UPROPERTY() TObjectPtr<UMaterialInstanceDynamic> BodyMID;

	UFUNCTION() void OnRep_HasLoot();
	void ShowLootGem();
	void DropStolenLoot(); // sunucu: her iki ölüm yolu da buradan geçer

	UFUNCTION(NetMulticast, Unreliable) void Multicast_HitFX();
	void Multicast_HitFX_Implementation();
	UFUNCTION(NetMulticast, Reliable)   void Multicast_StartDeath(float ShrinkDur);
	void Multicast_StartDeath_Implementation(float ShrinkDur);

	void BuildVisual();
	AActor* FindTarget() const; // en yakın canlı: oyuncu(lar) ∪ Kalp Taşı

	/** Sunucu: kurbanın envanterini sırtlan. Gölge artık YÜRÜYEN GANİMET — öldür, geri al. */
	void AddStolenLoot(const TMap<FName, int32>& In);

	float HP = 30.f;
	UPROPERTY(ReplicatedUsing = OnRep_HasLoot) bool bHasLoot = false; // istemciler altın taşı görsün
	TMap<FName, int32> Stolen;                                        // içerik yalnız sunucuda
	UPROPERTY() TObjectPtr<UStaticMeshComponent> LootGem;
	float AttackCd = 0.f;
	float Phase = 0.f;
	float FlashT = 0.f;
	bool  bDying = false;
	float DeathT = 0.f, DeathDur = 0.6f;
	FVector BaseBodyScale = FVector(0.8f, 0.8f, 1.35f);

	static constexpr float TouchRange  = 110.f;
	static constexpr float TouchDamage = 10.f;
	static constexpr float StructDamage = 8.f;  // duvar 120HP ~ 15 vuruş: bir gece dayanır, ikincide gedik
};
