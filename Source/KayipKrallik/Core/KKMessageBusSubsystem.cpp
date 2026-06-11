// KKMessageBusSubsystem.cpp
#include "Core/KKMessageBusSubsystem.h"
#include "KayipKrallik.h"
#include "Engine/Engine.h"
#include "Engine/World.h"
#include "Engine/GameInstance.h"

UKKMessageBusSubsystem* UKKMessageBusSubsystem::Get(const UObject* WorldContextObject)
{
	if (!GEngine || !WorldContextObject) return nullptr;
	if (const UWorld* World = GEngine->GetWorldFromContextObject(WorldContextObject, EGetWorldErrorMode::ReturnNull))
	{
		if (UGameInstance* GI = World->GetGameInstance())
		{
			return GI->GetSubsystem<UKKMessageBusSubsystem>();
		}
	}
	return nullptr;
}

FDelegateHandle UKKMessageBusSubsystem::Subscribe(FGameplayTag Tag, TFunction<void(const FKKMessage&)> Callback)
{
	return Channels.FindOrAdd(Tag).AddLambda(MoveTemp(Callback));
}

void UKKMessageBusSubsystem::Unsubscribe(FGameplayTag Tag, FDelegateHandle Handle)
{
	if (FKKMessageSignature* Chan = Channels.Find(Tag))
	{
		Chan->Remove(Handle);
	}
}

void UKKMessageBusSubsystem::Broadcast(const FKKMessage& Message)
{
	if (const FKKMessageSignature* Chan = Channels.Find(Message.Tag))
	{
		Chan->Broadcast(Message);
	}
}

void UKKMessageBusSubsystem::BroadcastTag(FGameplayTag Tag, int32 IntValue, float FloatValue,
                                          const FVector& Location, const FString& StringValue)
{
	FKKMessage M;
	M.Tag = Tag; M.IntValue = IntValue; M.FloatValue = FloatValue;
	M.Location = Location; M.StringValue = StringValue;
	Broadcast(M);
}
