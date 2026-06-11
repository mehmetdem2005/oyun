// KKCritter.h — Ürkek fauna: Tavşan + Geyik (plan 153 "Ürkek" sınıfının ilk iki üyesi). [TKT-F1-018]
// HEPSİ DÜŞMAN DEĞİL: bunlar kaçar, saldırmaz. Avcılık (155): et/post düşürür;
// aşırı avlanma bölge popülasyonunu düşürür (spawner takip eder), zaman toparlar.
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Character.h"
#include "Core/KKDamageable.h"
#include "KKCritter.generated.h"

class UStaticMeshComponent;

UENUM()
enum class EKKCritterKind : uint8
{
	Rabbit, // küçük, çevik: 10 HP -> 1 et
	Deer,   // iri, temkinli: 30 HP -> 2 et + 1 post (post = Faz 2 zanaat girdisi, şimdiden ekonomide)
};

UCLASS()
class KAYIPKRALLIK_API AKKCritter : public ACharacter, public IKKDamageable
{
	GENERATED_BODY()

public:
	AKKCritter();

	/** Sunucu: tür ata (spawner çağırır). Görsel + hız + HP buradan kurulur. */
	void InitKind(EKKCritterKind InKind);

	// ---- IKKDamageable ----
	virtual void ReceiveKKDamage(float Amount, AActor* DamageInstigator) override;
	virtual bool IsKKAlive() const override { return HP > 0.f; }

protected:
	virtual void BeginPlay() override;
	virtual void Tick(float Dt) override;
	virtual void GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const override;

	UPROPERTY(ReplicatedUsing = OnRep_Kind) EKKCritterKind Kind = EKKCritterKind::Rabbit;
	UFUNCTION() void OnRep_Kind();

	void BuildVisual();
	AActor* NearestPlayer(float& OutD2) const;
	void Die(AActor* Killer);

	UPROPERTY() TObjectPtr<UStaticMeshComponent> Body; // zıplama bob'u için tutulur (tavşan)
	bool bVisualBuilt = false;

	float HP = 10.f;
	float Phase = 0.f;
	FVector WanderDir = FVector::ZeroVector;
	float WanderT = 0.f;   // mevcut yürüyüş/duraklama kalan süresi
	bool  bWandering = false;

	static constexpr float FleeRadius = 420.f; // oyuncu bu kadar yaklaşırsa kaç (Ürkek sözleşmesi)
};
