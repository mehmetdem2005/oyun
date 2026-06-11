// KKInventoryComponent.cpp
#include "Items/KKInventoryComponent.h"
#include "Core/KKMessageBusSubsystem.h"
#include "Core/KKGameplayTags.h"
#include "Net/UnrealNetwork.h"

UKKInventoryComponent::UKKInventoryComponent()
{
	SetIsReplicatedByDefault(true);
	PrimaryComponentTick.bCanEverTick = false;
}

void UKKInventoryComponent::GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const
{
	Super::GetLifetimeReplicatedProps(OutLifetimeProps);
	DOREPLIFETIME(UKKInventoryComponent, Items);
}

void UKKInventoryComponent::AddItem(FName Id, int32 Count)
{
	if (Count <= 0 || Id.IsNone()) return;
	for (FKKItemStack& S : Items)
	{
		if (S.Id == Id) { S.Count += Count; NotifyChanged(); return; }
	}
	FKKItemStack NewS; NewS.Id = Id; NewS.Count = Count;
	Items.Add(NewS);
	NotifyChanged();
}

bool UKKInventoryComponent::ConsumeItem(FName Id, int32 Count)
{
	for (int32 i = 0; i < Items.Num(); ++i)
	{
		if (Items[i].Id == Id && Items[i].Count >= Count)
		{
			Items[i].Count -= Count;
			if (Items[i].Count <= 0) Items.RemoveAt(i);
			NotifyChanged();
			return true;
		}
	}
	return false;
}

int32 UKKInventoryComponent::GetCount(FName Id) const
{
	for (const FKKItemStack& S : Items) if (S.Id == Id) return S.Count;
	return 0;
}

void UKKInventoryComponent::ExportTo(TMap<FName, int32>& Out) const
{
	Out.Reset();
	for (const FKKItemStack& S : Items) Out.Add(S.Id, S.Count);
}

void UKKInventoryComponent::ImportFrom(const TMap<FName, int32>& In)
{
	Items.Reset();
	for (const TPair<FName, int32>& P : In)
	{
		if (P.Value > 0) { FKKItemStack S; S.Id = P.Key; S.Count = P.Value; Items.Add(S); }
	}
	NotifyChanged();
}

void UKKInventoryComponent::OnRep_Items() { NotifyChanged(); }

void UKKInventoryComponent::NotifyChanged()
{
	if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(this))
	{
		Bus->BroadcastTag(KKTags::Inventory_Changed);
	}
}

void UKKInventoryComponent::TakeAllInto(TMap<FName, int32>& Out)
{
	for (const FKKItemStack& S : Items)
	{
		if (S.Count > 0) Out.FindOrAdd(S.Id) += S.Count;
	}
	Items.Reset();
	NotifyChanged(); // kurbanın hotbar'ı anında sıfırlanır — kayıp HİSSEDİLMELİ
}

void UKKInventoryComponent::AddMap(const TMap<FName, int32>& In)
{
	for (const auto& P : In)
	{
		if (P.Value > 0) AddItem(P.Key, P.Value); // AddItem yayın/birleştirme kurallarını zaten bilir
	}
}
