// KKVillager.h — Ayla: kafeste bulunan ilk yurttaş (plan 55+58, Faz 1 dilimi). [TKT-F1-016]
// Gündüz: ağaç kes -> odunu sana getir. Gece: Kalp Taşı'na sığın. Ölürse GERİ GELMEZ —
// isimli karakterin ağırlığı budur; taşıdığı odun kese olarak düşer (yağma kuralıyla tutarlı).
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Character.h"
#include "Core/KKDamageable.h"
#include "KKVillager.generated.h"

class UStaticMeshComponent;
class UKKInventoryComponent;
class AKKResourceNode;

UENUM()
enum class EKKVillagerState : uint8
{
	Caged,     // kafeste — E ile kurtar
	GoTree,    // hedef ağaca yürüyor
	Chop,      // kesiyor (1.4 sn ritim)
	Deliver,   // odunu en yakın oyuncuya taşıyor
	NightHide, // kalbe sığınmış, şafağı bekliyor
};

UCLASS()
class KAYIPKRALLIK_API AKKVillager : public ACharacter, public IKKDamageable
{
	GENERATED_BODY()

public:
	AKKVillager();

	/** Sunucu: kafesi kır, krallığa katıl. Oyuncunun E'si buraya düşer. */
	void Rescue(AActor* By);

	bool IsRescued() const { return VState != EKKVillagerState::Caged; }
	bool IsCaged()  const { return VState == EKKVillagerState::Caged; }

	/** GameMode kayıttan kurarken: 1 = kurtarılmış başlat (kafessiz, kalp yanında). */
	void InitFromSave(bool bAlreadyRescued);

	// ---- IKKDamageable: friendly-fire onu da bulur; bedeli kalıcıdır ----
	virtual void ReceiveKKDamage(float Amount, AActor* DamageInstigator) override;
	virtual bool IsKKAlive() const override { return HP > 0.f; }

protected:
	virtual void BeginPlay() override;
	virtual void Tick(float Dt) override;
	virtual void GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const override;

	UPROPERTY(ReplicatedUsing = OnRep_VState) EKKVillagerState VState = EKKVillagerState::Caged;
	UPROPERTY(ReplicatedUsing = OnRep_Carry)  bool bCarrying = false; // elindeki odun görseli

	UFUNCTION() void OnRep_VState();
	UFUNCTION() void OnRep_Carry();

	UFUNCTION(NetMulticast, Unreliable) void Multicast_ChopFX();
	void Multicast_ChopFX_Implementation();

	void BuildVisual();
	void ApplyCageVisual();             // kafes çubukları görünür/gizli + hareket kilidi
	AKKResourceNode* FindNearbyTree() const;
	AActor* FindNearestPlayer() const;
	void Die();

	UPROPERTY() TObjectPtr<UKKInventoryComponent> Inventory; // hasat ödülü buraya akar (tek hasat yolu)
	UPROPERTY() TObjectPtr<UStaticMeshComponent> CarryLog;
	UPROPERTY() TArray<TObjectPtr<UStaticMeshComponent>> CageParts;

	TWeakObjectPtr<AKKResourceNode> TargetTree;
	float ChopCd = 0.f;
	float RescanCd = 0.f;
	float HP = 40.f;

	static constexpr float WorkRadius  = 1400.f; // kalpten ~14 karo: ev civarı çalışır
	static constexpr float ChopRange   = 170.f;
	static constexpr float DeliverRange= 150.f;
	static constexpr float ChopRhythm  = 1.4f;
};
