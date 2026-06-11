// KKAttributeSet.cpp
#include "Combat/KKAttributeSet.h"
#include "Net/UnrealNetwork.h"

UKKAttributeSet::UKKAttributeSet()
{
	InitMaxHealth(100.f);  InitHealth(100.f);
	InitMaxStamina(100.f); InitStamina(100.f);
	InitMaxHunger(100.f);  InitHunger(100.f);
}

void UKKAttributeSet::PreAttributeChange(const FGameplayAttribute& Attribute, float& NewValue)
{
	Super::PreAttributeChange(Attribute, NewValue);
	if (Attribute == GetHealthAttribute())       NewValue = FMath::Clamp(NewValue, 0.f, GetMaxHealth());
	else if (Attribute == GetStaminaAttribute()) NewValue = FMath::Clamp(NewValue, 0.f, GetMaxStamina());
	else if (Attribute == GetHungerAttribute())  NewValue = FMath::Clamp(NewValue, 0.f, GetMaxHunger());
}

void UKKAttributeSet::GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const
{
	Super::GetLifetimeReplicatedProps(OutLifetimeProps);
	DOREPLIFETIME_CONDITION_NOTIFY(UKKAttributeSet, Health,     COND_None, REPNOTIFY_Always);
	DOREPLIFETIME_CONDITION_NOTIFY(UKKAttributeSet, MaxHealth,  COND_None, REPNOTIFY_Always);
	DOREPLIFETIME_CONDITION_NOTIFY(UKKAttributeSet, Stamina,    COND_None, REPNOTIFY_Always);
	DOREPLIFETIME_CONDITION_NOTIFY(UKKAttributeSet, MaxStamina, COND_None, REPNOTIFY_Always);
	DOREPLIFETIME_CONDITION_NOTIFY(UKKAttributeSet, Hunger,     COND_None, REPNOTIFY_Always);
	DOREPLIFETIME_CONDITION_NOTIFY(UKKAttributeSet, MaxHunger,  COND_None, REPNOTIFY_Always);
}

void UKKAttributeSet::OnRep_Health(const FGameplayAttributeData& Old)     { GAMEPLAYATTRIBUTE_REPNOTIFY(UKKAttributeSet, Health, Old); }
void UKKAttributeSet::OnRep_MaxHealth(const FGameplayAttributeData& Old)  { GAMEPLAYATTRIBUTE_REPNOTIFY(UKKAttributeSet, MaxHealth, Old); }
void UKKAttributeSet::OnRep_Stamina(const FGameplayAttributeData& Old)    { GAMEPLAYATTRIBUTE_REPNOTIFY(UKKAttributeSet, Stamina, Old); }
void UKKAttributeSet::OnRep_MaxStamina(const FGameplayAttributeData& Old) { GAMEPLAYATTRIBUTE_REPNOTIFY(UKKAttributeSet, MaxStamina, Old); }
void UKKAttributeSet::OnRep_Hunger(const FGameplayAttributeData& Old)     { GAMEPLAYATTRIBUTE_REPNOTIFY(UKKAttributeSet, Hunger, Old); }
void UKKAttributeSet::OnRep_MaxHunger(const FGameplayAttributeData& Old)  { GAMEPLAYATTRIBUTE_REPNOTIFY(UKKAttributeSet, MaxHunger, Old); }
