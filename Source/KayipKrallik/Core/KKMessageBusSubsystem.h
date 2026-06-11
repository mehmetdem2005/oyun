// KKMessageBusSubsystem.h — Hafif GameplayTag yayın/abone otobüsü. [TKT-F0-003]
// Plan 6.2: Sistemler birbirini TANIMAZ; yalnız tag mesajlarıyla konuşur.
// Not: Lyra'nın GameplayMessageRouter'ı motor eklentisi DEĞİL; bu yüzden
// bağımlılıksız kendi minimal eşdeğerimizi taşıyoruz (kesin eşleşmeli, hiyerarşi yürümez).
#pragma once

#include "CoreMinimal.h"
#include "GameplayTagContainer.h"
#include "Subsystems/GameInstanceSubsystem.h"
#include "KKMessageBusSubsystem.generated.h"

USTRUCT(BlueprintType)
struct FKKMessage
{
	GENERATED_BODY()

	UPROPERTY(BlueprintReadWrite, Category="KK") FGameplayTag Tag;
	UPROPERTY(BlueprintReadWrite, Category="KK") int32   IntValue   = 0;
	UPROPERTY(BlueprintReadWrite, Category="KK") float   FloatValue = 0.f;
	UPROPERTY(BlueprintReadWrite, Category="KK") FVector Location   = FVector::ZeroVector;
	UPROPERTY(BlueprintReadWrite, Category="KK") FString StringValue;
	UPROPERTY(BlueprintReadWrite, Category="KK") TObjectPtr<UObject> Source = nullptr;
};

DECLARE_MULTICAST_DELEGATE_OneParam(FKKMessageSignature, const FKKMessage&);

UCLASS()
class KAYIPKRALLIK_API UKKMessageBusSubsystem : public UGameInstanceSubsystem
{
	GENERATED_BODY()

public:
	/** Dünya bağlamından otobüse kestirme erişim. */
	static UKKMessageBusSubsystem* Get(const UObject* WorldContextObject);

	FDelegateHandle Subscribe(FGameplayTag Tag, TFunction<void(const FKKMessage&)> Callback);
	void Unsubscribe(FGameplayTag Tag, FDelegateHandle Handle);

	void Broadcast(const FKKMessage& Message);

	/** Kısa yol: sık kullanılan alanlarla yayın. */
	void BroadcastTag(FGameplayTag Tag, int32 IntValue = 0, float FloatValue = 0.f,
	                  const FVector& Location = FVector::ZeroVector, const FString& StringValue = FString());

private:
	TMap<FGameplayTag, FKKMessageSignature> Channels;
};
