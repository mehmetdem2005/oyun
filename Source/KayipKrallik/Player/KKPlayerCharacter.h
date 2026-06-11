// KKPlayerCharacter.h — Şövalye: 2.5D top-down kahraman (plan 6.2 Oyuncu dikey dilimi). [TKT-F0-015]
// Görsel motor temel şekillerinden (KO drawPlayer paleti); meşale ışığı geceyi taşır.
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Character.h"
#include "AbilitySystemInterface.h"
#include "Core/KKDamageable.h"
#include "Combat/KKAttributeSet.h"
#include "World/KKBuildTypes.h"
#include "KKPlayerCharacter.generated.h"

class USpringArmComponent;
class UCameraComponent;
class UPointLightComponent;
class UStaticMeshComponent;
class UKKAbilitySystemComponent;
class UKKInventoryComponent;
class AKKResourceNode;

UCLASS()
class KAYIPKRALLIK_API AKKPlayerCharacter : public ACharacter, public IAbilitySystemInterface, public IKKDamageable
{
	GENERATED_BODY()

public:
	AKKPlayerCharacter();

	virtual UAbilitySystemComponent* GetAbilitySystemComponent() const override;
	virtual void PossessedBy(AController* NewController) override;
	virtual void Tick(float Dt) override;

	// ---- Girdi (controller yönlendirir) ----
	void Move(const FVector2D& Axis);
	void PrimaryAction();   // SALDIR/KES: önce düğüm, yoksa kılıç · inşa modunda YERLEŞTİR
	void TryInteract();     // E: topla / böğürtlen ye / kapı aç · inşa modunda TİP DEĞİŞTİR
	void ToggleBuildMode(); // B: inşa modunu aç/kapat (Faz 1)
	bool IsInBuildMode() const { return bBuildMode; }

	// ---- IKKDamageable ----
	virtual void ReceiveKKDamage(float Amount, AActor* DamageInstigator) override;
	virtual bool IsKKAlive() const override { return !bDead; }

	// ---- HUD / Kayıt köprüsü ----
	float GetAttrValue(EKKAttr A) const;
	float GetAttrPct(EKKAttr A) const;
	FText GetContextHint() const { return CachedHint; }
	void  ApplyLoadedState(float Health, float Stamina, float Hunger);

protected:
	virtual void BeginPlay() override;

	// ---- Bileşenler ----
	UPROPERTY(VisibleAnywhere, Category="KK") TObjectPtr<USpringArmComponent> SpringArm;
	UPROPERTY(VisibleAnywhere, Category="KK") TObjectPtr<UCameraComponent> Camera;
	UPROPERTY(VisibleAnywhere, Category="KK") TObjectPtr<UPointLightComponent> TorchLight;
	UPROPERTY(VisibleAnywhere, Category="KK") TObjectPtr<UKKAbilitySystemComponent> ASC;
	UPROPERTY() TObjectPtr<UKKAttributeSet> AttrSet;
	UPROPERTY(VisibleAnywhere, Category="KK") TObjectPtr<UKKInventoryComponent> Inventory;

	UPROPERTY() TObjectPtr<UStaticMeshComponent> BodyMesh;
	UPROPERTY() TObjectPtr<UStaticMeshComponent> HeadMesh;
	UPROPERTY() TObjectPtr<USceneComponent> SwordPivot;
	UPROPERTY() TObjectPtr<UStaticMeshComponent> SwordMesh;
	UPROPERTY() TObjectPtr<class UMaterialInstanceDynamic> BodyMID;

	// ---- Sunucu RPC'leri ----
	UFUNCTION(Server, Reliable) void Server_Primary();
	void Server_Primary_Implementation();
	UFUNCTION(Server, Reliable) void Server_Interact();
	void Server_Interact_Implementation();
	UFUNCTION(Server, Reliable) void Server_PlaceBuild(uint8 TypeByte);
	void Server_PlaceBuild_Implementation(uint8 TypeByte);

	// ---- Çoklu yayın FX ----
	UFUNCTION(NetMulticast, Unreliable) void Multicast_SwingFX();
	void Multicast_SwingFX_Implementation();
	UFUNCTION(NetMulticast, Unreliable) void Multicast_HurtFX();
	void Multicast_HurtFX_Implementation();
	UFUNCTION(NetMulticast, Unreliable) void Multicast_EatFX();
	void Multicast_EatFX_Implementation();
	UFUNCTION(NetMulticast, Reliable)   void Multicast_DeathFX(bool bDead);
	void Multicast_DeathFX_Implementation(bool bDead);

	void BuildVisual();
	AKKResourceNode* FindNodeInRange(float Range) const;
	void Die();
	void Respawn();
	void UpdateContextHint();
	void UpdateBuildGhost();
	FIntPoint ComputeBuildTile() const; // istemci hayaleti + sunucu doğrulaması AYNI formülü kullanır

	// ---- Durum ----
	bool  bDead = false;
	float AttackT = 0.f;          // kılıç savuruşu (0.18s)
	float FlashT = 0.f;           // hasar flaşı
	float HintAcc = 0.f;
	float SurvivalAcc = 0.f;      // açlık/enerji 4 Hz uygulanır
	FVector LastSafeLoc = FVector::ZeroVector;
	FText CachedHint;
	TWeakObjectPtr<AActor> LastHitBy; // yağma yönlendirme: son saldıran (10 sn pencere)
	float LastHitTime = -100.f;

	// ---- İnşa modu (Faz 1) — tamamen yerel girdi durumu; doğrulama sunucuda ----
	bool  bBuildMode = false;
	uint8 BuildSel = 0;            // 0=Duvar, 1=Kapı
	bool  bGhostValid = false;
	UPROPERTY() TObjectPtr<UStaticMeshComponent> GhostMesh;
	UPROPERTY() TObjectPtr<class UMaterialInstanceDynamic> GhostMID;

	static constexpr float HarvestRange = 180.f;
	static constexpr float MeleeRange   = 170.f;
	static constexpr float MeleeDamage  = 20.f;
	static constexpr float SwingStamina = 8.f;
};
