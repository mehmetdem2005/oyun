// KKAttributeSet.h — GAS öznitelikleri: Can / Enerji / Açlık (KO üçlüsü). [TKT-F0-012]
// Faz 0 sözleşmesi: nitelikler GAS'ta yaşar, değişimler ApplyModToAttribute ile;
// GameplayEffect varlık ağı Faz 1'de gelir (plan 6.2 GAS iskeleti).
#pragma once

#include "CoreMinimal.h"
#include "AttributeSet.h"
#include "AbilitySystemComponent.h"
#include "KKAttributeSet.generated.h"

/** Kayıt/HUD köprüsü için sade erişim anahtarı. */
enum class EKKAttr : uint8
{
	Health, MaxHealth, Stamina, MaxStamina, Hunger, MaxHunger
};

#ifndef ATTRIBUTE_ACCESSORS
#define ATTRIBUTE_ACCESSORS(ClassName, PropertyName) \
	GAMEPLAYATTRIBUTE_PROPERTY_GETTER(ClassName, PropertyName) \
	GAMEPLAYATTRIBUTE_VALUE_GETTER(PropertyName) \
	GAMEPLAYATTRIBUTE_VALUE_SETTER(PropertyName) \
	GAMEPLAYATTRIBUTE_VALUE_INITTER(PropertyName)
#endif

UCLASS()
class KAYIPKRALLIK_API UKKAttributeSet : public UAttributeSet
{
	GENERATED_BODY()

public:
	UKKAttributeSet();

	virtual void PreAttributeChange(const FGameplayAttribute& Attribute, float& NewValue) override;
	virtual void GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const override;

	UPROPERTY(BlueprintReadOnly, Category="KK", ReplicatedUsing = OnRep_Health)
	FGameplayAttributeData Health;
	ATTRIBUTE_ACCESSORS(UKKAttributeSet, Health)

	UPROPERTY(BlueprintReadOnly, Category="KK", ReplicatedUsing = OnRep_MaxHealth)
	FGameplayAttributeData MaxHealth;
	ATTRIBUTE_ACCESSORS(UKKAttributeSet, MaxHealth)

	UPROPERTY(BlueprintReadOnly, Category="KK", ReplicatedUsing = OnRep_Stamina)
	FGameplayAttributeData Stamina;
	ATTRIBUTE_ACCESSORS(UKKAttributeSet, Stamina)

	UPROPERTY(BlueprintReadOnly, Category="KK", ReplicatedUsing = OnRep_MaxStamina)
	FGameplayAttributeData MaxStamina;
	ATTRIBUTE_ACCESSORS(UKKAttributeSet, MaxStamina)

	UPROPERTY(BlueprintReadOnly, Category="KK", ReplicatedUsing = OnRep_Hunger)
	FGameplayAttributeData Hunger;
	ATTRIBUTE_ACCESSORS(UKKAttributeSet, Hunger)

	UPROPERTY(BlueprintReadOnly, Category="KK", ReplicatedUsing = OnRep_MaxHunger)
	FGameplayAttributeData MaxHunger;
	ATTRIBUTE_ACCESSORS(UKKAttributeSet, MaxHunger)

protected:
	UFUNCTION() void OnRep_Health(const FGameplayAttributeData& Old);
	UFUNCTION() void OnRep_MaxHealth(const FGameplayAttributeData& Old);
	UFUNCTION() void OnRep_Stamina(const FGameplayAttributeData& Old);
	UFUNCTION() void OnRep_MaxStamina(const FGameplayAttributeData& Old);
	UFUNCTION() void OnRep_Hunger(const FGameplayAttributeData& Old);
	UFUNCTION() void OnRep_MaxHunger(const FGameplayAttributeData& Old);
};
