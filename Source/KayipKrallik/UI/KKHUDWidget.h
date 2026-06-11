// KKHUDWidget.h — HUD: tamamen WidgetTree ile koddan kurulur (KO HUD düzeni). [TKT-F0-022]
// CAN/ENERJİ/AÇLIK barları + saat + 3 slot hotbar + bağlamsal ipucu + hasar flaşı.
#pragma once

#include "CoreMinimal.h"
#include "Blueprint/UserWidget.h"
#include "KKHUDWidget.generated.h"

class UCanvasPanel;
class UProgressBar;
class UTextBlock;
class UBorder;

UCLASS()
class KAYIPKRALLIK_API UKKHUDWidget : public UUserWidget
{
	GENERATED_BODY()

protected:
	virtual void NativeOnInitialized() override;
	virtual void NativeConstruct() override;
	virtual void NativeDestruct() override;
	virtual void NativeTick(const FGeometry& Geo, float Dt) override;

	UPROPERTY() TObjectPtr<UProgressBar> HpBar;
	UPROPERTY() TObjectPtr<UProgressBar> StBar;
	UPROPERTY() TObjectPtr<UProgressBar> HuBar;
	UPROPERTY() TObjectPtr<UTextBlock>   ClockText;
	UPROPERTY() TObjectPtr<UTextBlock>   HintText;
	UPROPERTY() TObjectPtr<UTextBlock>   GoalText;     // saat altı: "Hedef: Gün 10 · Kalbi koru"
	UPROPERTY() TObjectPtr<UProgressBar> HeartBar;     // sağ üst: Kalp Taşı canı
	UPROPERTY() TObjectPtr<UTextBlock>   BigMessage;   // merkez: zafer / yenilgi bandı
	UPROPERTY() TObjectPtr<UTextBlock>   ToastText;    // geçici olay bandı (kurtarma, kayıp…)
	float ToastT = 0.f;
	void ShowToast(const FString& Msg, const FLinearColor& Col);
	UPROPERTY() TObjectPtr<UTextBlock>   CountWood;
	UPROPERTY() TObjectPtr<UTextBlock>   CountStone;
	UPROPERTY() TObjectPtr<UTextBlock>   CountBerry;
	UPROPERTY() TObjectPtr<UTextBlock>   CountMeat;
	UPROPERTY() TObjectPtr<UBorder>      DamageFlash;

	float FlashA = 0.f;
	FDelegateHandle DamageHandle;
	FDelegateHandle HeartHandle;
	FDelegateHandle VictoryHandle;
	FDelegateHandle VRescueHandle;
	FDelegateHandle VDiedHandle;
	FDelegateHandle SiegeHandle;
	TWeakObjectPtr<class AKKHeartStone> HeartRef; // tembel bulunur, sonra anket edilir

	void BuildTree();
	UProgressBar* MakeBarRow(class UVerticalBox* Box, const FString& Label, const FLinearColor& Fill);
	UTextBlock* MakeHotbarSlot(class UHorizontalBox* Box, const FLinearColor& ChipColor);
};
