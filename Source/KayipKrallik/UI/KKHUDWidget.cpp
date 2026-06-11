// KKHUDWidget.cpp
#include "UI/KKHUDWidget.h"
#include "Player/KKPlayerCharacter.h"
#include "Items/KKInventoryComponent.h"
#include "Core/KKTimeOfDaySubsystem.h"
#include "Core/KKMessageBusSubsystem.h"
#include "Core/KKGameplayTags.h"
#include "World/KKHeartStone.h"
#include "World/KKWorldTypes.h"
#include "EngineUtils.h"
#include "Styling/CoreStyle.h"

#include "Blueprint/WidgetTree.h"
#include "Components/CanvasPanel.h"
#include "Components/CanvasPanelSlot.h"
#include "Components/VerticalBox.h"
#include "Components/VerticalBoxSlot.h"
#include "Components/HorizontalBox.h"
#include "Components/HorizontalBoxSlot.h"
#include "Components/Overlay.h"
#include "Components/OverlaySlot.h"
#include "Components/ProgressBar.h"
#include "Components/TextBlock.h"
#include "Components/SizeBox.h"
#include "Components/Border.h"

namespace
{
	FLinearColor Hex(const TCHAR* H) { return FLinearColor::FromSRGBColor(FColor::FromHex(H)); }
}

void UKKHUDWidget::NativeOnInitialized()
{
	Super::NativeOnInitialized();
	BuildTree();
	SetVisibility(ESlateVisibility::HitTestInvisible); // HUD asla girdi yutmaz
}

void UKKHUDWidget::BuildTree()
{
	if (WidgetTree->RootWidget) return;

	UCanvasPanel* Canvas = WidgetTree->ConstructWidget<UCanvasPanel>(UCanvasPanel::StaticClass());
	WidgetTree->RootWidget = Canvas;

	// --- Sol üst: durum barları (KO #stats) ---
	UVerticalBox* Stats = WidgetTree->ConstructWidget<UVerticalBox>(UVerticalBox::StaticClass());
	if (UCanvasPanelSlot* S = Canvas->AddChildToCanvas(Stats))
	{
		S->SetAnchors(FAnchors(0.f, 0.f));
		S->SetPosition(FVector2D(24, 20));
		S->SetAutoSize(true);
	}
	HpBar = MakeBarRow(Stats, TEXT("CAN"),    Hex(TEXT("e23d4f")));
	StBar = MakeBarRow(Stats, TEXT("ENERJİ"), Hex(TEXT("e8b73d")));
	HuBar = MakeBarRow(Stats, TEXT("AÇLIK"),  Hex(TEXT("e07b2f")));

	// --- Üst orta: saat (KO #clock) ---
	ClockText = WidgetTree->ConstructWidget<UTextBlock>(UTextBlock::StaticClass());
	ClockText->SetColorAndOpacity(FSlateColor(FLinearColor(1, 1, 1, 0.92f)));
	if (UCanvasPanelSlot* S = Canvas->AddChildToCanvas(ClockText))
	{
		S->SetAnchors(FAnchors(0.5f, 0.f));
		S->SetAlignment(FVector2D(0.5f, 0.f));
		S->SetPosition(FVector2D(0, 18));
		S->SetAutoSize(true);
	}

	// --- Saat altı: dikey dilim hedefi (her zaman görünür pusula) ---
	GoalText = WidgetTree->ConstructWidget<UTextBlock>(UTextBlock::StaticClass());
	GoalText->SetColorAndOpacity(FSlateColor(FLinearColor(1, 1, 1, 0.55f)));
	GoalText->SetText(FText::FromString(TEXT("Hedef: Gün 10'a ulaş · Kalp Taşı'nı koru")));
	if (UCanvasPanelSlot* S = Canvas->AddChildToCanvas(GoalText))
	{
		S->SetAnchors(FAnchors(0.5f, 0.f));
		S->SetAlignment(FVector2D(0.5f, 0.f));
		S->SetPosition(FVector2D(0, 44));
		S->SetAutoSize(true);
	}

	// --- Sağ üst: KALP barı (krallığın nabzı; düşerken herkes görmeli) ---
	{
		UVerticalBox* HeartBox = WidgetTree->ConstructWidget<UVerticalBox>(UVerticalBox::StaticClass());
		HeartBar = MakeBarRow(HeartBox, TEXT("KALP"), KKPalette::Hex(TEXT("e23d4f")));
		if (UCanvasPanelSlot* S = Canvas->AddChildToCanvas(HeartBox))
		{
			S->SetAnchors(FAnchors(1.f, 0.f));
			S->SetAlignment(FVector2D(1.f, 0.f));
			S->SetPosition(FVector2D(-18, 16));
			S->SetAutoSize(true);
		}
	}

	// --- Merkez: büyük mesaj bandı (zafer/yenilgi) — gizli başlar ---
	BigMessage = WidgetTree->ConstructWidget<UTextBlock>(UTextBlock::StaticClass());
	BigMessage->SetColorAndOpacity(FSlateColor(FLinearColor(1, 1, 1, 0.f)));
	BigMessage->SetJustification(ETextJustify::Center);
	BigMessage->SetFont(FSlateFontInfo(FCoreStyle::GetDefaultFontStyle("Bold", 30))); // getter'a güvenme: motor varsayılanı her sürümde var
	if (UCanvasPanelSlot* S = Canvas->AddChildToCanvas(BigMessage))
	{
		S->SetAnchors(FAnchors(0.5f, 0.4f));
		S->SetAlignment(FVector2D(0.5f, 0.5f));
		S->SetPosition(FVector2D(0, 0));
		S->SetAutoSize(true);
	}

	// --- Merkez-üst: geçici olay bandı (3 sn'de söner) ---
	ToastText = WidgetTree->ConstructWidget<UTextBlock>(UTextBlock::StaticClass());
	ToastText->SetColorAndOpacity(FSlateColor(FLinearColor(1, 1, 1, 0.f)));
	ToastText->SetJustification(ETextJustify::Center);
	ToastText->SetFont(FSlateFontInfo(FCoreStyle::GetDefaultFontStyle("Bold", 18)));
	if (UCanvasPanelSlot* S = Canvas->AddChildToCanvas(ToastText))
	{
		S->SetAnchors(FAnchors(0.5f, 0.f));
		S->SetAlignment(FVector2D(0.5f, 0.f));
		S->SetPosition(FVector2D(0, 78));
		S->SetAutoSize(true);
	}

	// --- Alt orta: hotbar (odun / taş / böğürtlen) ---
	UHorizontalBox* Hotbar = WidgetTree->ConstructWidget<UHorizontalBox>(UHorizontalBox::StaticClass());
	if (UCanvasPanelSlot* S = Canvas->AddChildToCanvas(Hotbar))
	{
		S->SetAnchors(FAnchors(0.5f, 1.f));
		S->SetAlignment(FVector2D(0.5f, 1.f));
		S->SetPosition(FVector2D(0, -18));
		S->SetAutoSize(true);
	}
	CountWood  = MakeHotbarSlot(Hotbar, Hex(TEXT("8a5a2b")));
	CountStone = MakeHotbarSlot(Hotbar, Hex(TEXT("9aa0ad")));
	CountBerry = MakeHotbarSlot(Hotbar, Hex(TEXT("e23d4f")));
	CountMeat  = MakeHotbarSlot(Hotbar, Hex(TEXT("e8a0c0"))); // çiçek pembesi = taze et (palet-içi)

	// --- İpucu (hotbar üstü) ---
	HintText = WidgetTree->ConstructWidget<UTextBlock>(UTextBlock::StaticClass());
	HintText->SetColorAndOpacity(FSlateColor(Hex(TEXT("ffd76a"))));
	if (UCanvasPanelSlot* S = Canvas->AddChildToCanvas(HintText))
	{
		S->SetAnchors(FAnchors(0.5f, 1.f));
		S->SetAlignment(FVector2D(0.5f, 1.f));
		S->SetPosition(FVector2D(0, -84));
		S->SetAutoSize(true);
	}

	// --- Tam ekran hasar flaşı (KO hurtFlash) ---
	DamageFlash = WidgetTree->ConstructWidget<UBorder>(UBorder::StaticClass());
	DamageFlash->SetBrushColor(FLinearColor(0.9f, 0.08f, 0.08f, 0.f));
	if (UCanvasPanelSlot* S = Canvas->AddChildToCanvas(DamageFlash))
	{
		S->SetAnchors(FAnchors(0.f, 0.f, 1.f, 1.f));
		S->SetOffsets(FMargin(0));
	}
}

UProgressBar* UKKHUDWidget::MakeBarRow(UVerticalBox* Box, const FString& Label, const FLinearColor& Fill)
{
	UHorizontalBox* Row = WidgetTree->ConstructWidget<UHorizontalBox>(UHorizontalBox::StaticClass());

	UTextBlock* L = WidgetTree->ConstructWidget<UTextBlock>(UTextBlock::StaticClass());
	L->SetText(FText::FromString(Label));
	L->SetColorAndOpacity(FSlateColor(FLinearColor(1, 1, 1, 0.85f)));
	if (UHorizontalBoxSlot* LS = Cast<UHorizontalBoxSlot>(Row->AddChild(L)))
	{
		LS->SetPadding(FMargin(0, 0, 8, 0));
		LS->SetVerticalAlignment(VAlign_Center);
	}

	USizeBox* Sz = WidgetTree->ConstructWidget<USizeBox>(USizeBox::StaticClass());
	Sz->SetWidthOverride(220.f);
	Sz->SetHeightOverride(14.f);
	UProgressBar* Bar = WidgetTree->ConstructWidget<UProgressBar>(UProgressBar::StaticClass());
	Bar->SetFillColorAndOpacity(Fill);
	Bar->SetPercent(1.f);
	Sz->AddChild(Bar);
	if (UHorizontalBoxSlot* BS = Cast<UHorizontalBoxSlot>(Row->AddChild(Sz)))
	{
		BS->SetVerticalAlignment(VAlign_Center);
	}

	if (UVerticalBoxSlot* RS = Cast<UVerticalBoxSlot>(Box->AddChild(Row)))
	{
		RS->SetPadding(FMargin(0, 3));
	}
	return Bar;
}

UTextBlock* UKKHUDWidget::MakeHotbarSlot(UHorizontalBox* Box, const FLinearColor& ChipColor)
{
	USizeBox* Sz = WidgetTree->ConstructWidget<USizeBox>(USizeBox::StaticClass());
	Sz->SetWidthOverride(48.f);
	Sz->SetHeightOverride(48.f);

	UOverlay* Ov = WidgetTree->ConstructWidget<UOverlay>(UOverlay::StaticClass());
	Sz->AddChild(Ov);

	UBorder* Bg = WidgetTree->ConstructWidget<UBorder>(UBorder::StaticClass());
	Bg->SetBrushColor(FLinearColor(0.05f, 0.07f, 0.11f, 0.72f)); // KO slot zemini
	if (UOverlaySlot* OS = Cast<UOverlaySlot>(Ov->AddChild(Bg)))
	{
		OS->SetHorizontalAlignment(HAlign_Fill);
		OS->SetVerticalAlignment(VAlign_Fill);
	}

	UBorder* Chip = WidgetTree->ConstructWidget<UBorder>(UBorder::StaticClass());
	Chip->SetBrushColor(ChipColor);
	if (UOverlaySlot* OS = Cast<UOverlaySlot>(Ov->AddChild(Chip)))
	{
		OS->SetHorizontalAlignment(HAlign_Center);
		OS->SetVerticalAlignment(VAlign_Center);
		OS->SetPadding(FMargin(11)); // 48 - 2*11 = 26px renk çipi
	}

	UTextBlock* Count = WidgetTree->ConstructWidget<UTextBlock>(UTextBlock::StaticClass());
	Count->SetText(FText::FromString(TEXT("0")));
	Count->SetColorAndOpacity(FSlateColor(FLinearColor::White));
	if (UOverlaySlot* OS = Cast<UOverlaySlot>(Ov->AddChild(Count)))
	{
		OS->SetHorizontalAlignment(HAlign_Right);
		OS->SetVerticalAlignment(VAlign_Bottom);
		OS->SetPadding(FMargin(0, 0, 4, 2));
	}

	if (UHorizontalBoxSlot* HS = Cast<UHorizontalBoxSlot>(Box->AddChild(Sz)))
	{
		HS->SetPadding(FMargin(4, 0));
	}
	return Count;
}

void UKKHUDWidget::NativeConstruct()
{
	Super::NativeConstruct();
	if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(this))
	{
		TWeakObjectPtr<UKKHUDWidget> Weak = this;
		DamageHandle = Bus->Subscribe(KKTags::Player_Damaged, [Weak](const FKKMessage&)
		{
			if (UKKHUDWidget* W = Weak.Get()) W->FlashA = 0.65f;
		});
		HeartHandle = Bus->Subscribe(KKTags::Game_HeartDestroyed, [Weak](const FKKMessage&)
		{
			if (UKKHUDWidget* W = Weak.Get())
			{
				W->BigMessage->SetText(FText::FromString(TEXT("KALP TAŞI DÜŞTÜ\nKrallık karanlığa gömüldü…")));
				W->BigMessage->SetColorAndOpacity(FSlateColor(KKPalette::Hex(TEXT("e23d4f"))));
			}
		});
		VRescueHandle = Bus->Subscribe(KKTags::Villager_Rescued, [Weak](const FKKMessage& M)
		{
			if (UKKHUDWidget* W = Weak.Get())
				W->ShowToast(M.StringValue + TEXT(" katıldı — krallığın ilk yurttaşı!"), KKPalette::Hex(TEXT("e8b73d")));
		});
		VDiedHandle = Bus->Subscribe(KKTags::Villager_Died, [Weak](const FKKMessage& M)
		{
			if (UKKHUDWidget* W = Weak.Get())
				W->ShowToast(M.StringValue + TEXT(" düştü… isimli yurttaşlar geri gelmez."), KKPalette::Hex(TEXT("e23d4f")));
		});
		SiegeHandle = Bus->Subscribe(KKTags::Night_BigSiege, [Weak](const FKKMessage& M)
		{
			if (UKKHUDWidget* W = Weak.Get())
				W->ShowToast(FString::Printf(TEXT("BÜYÜK KUŞATMA — %d. gece, gölgeler sürüyle geliyor!"), M.IntValue),
				             KKPalette::Hex(TEXT("e23d4f")));
		});
		VictoryHandle = Bus->Subscribe(KKTags::Game_Victory, [Weak](const FKKMessage&)
		{
			if (UKKHUDWidget* W = Weak.Get())
			{
				W->BigMessage->SetText(FText::FromString(TEXT("GÜN 10 — KRALLIK AYAKTA!\nKuşatma kırıldı; bundan sonrası senin hikâyen.")));
				W->BigMessage->SetColorAndOpacity(FSlateColor(KKPalette::Hex(TEXT("e8b73d"))));
			}
		});
	}
}

void UKKHUDWidget::NativeDestruct()
{
	if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(this))
	{
		Bus->Unsubscribe(KKTags::Player_Damaged, DamageHandle);
		Bus->Unsubscribe(KKTags::Game_HeartDestroyed, HeartHandle);
		Bus->Unsubscribe(KKTags::Game_Victory, VictoryHandle);
		Bus->Unsubscribe(KKTags::Villager_Rescued, VRescueHandle);
		Bus->Unsubscribe(KKTags::Villager_Died, VDiedHandle);
		Bus->Unsubscribe(KKTags::Night_BigSiege, SiegeHandle);
	}
	Super::NativeDestruct();
}

void UKKHUDWidget::NativeTick(const FGeometry& Geo, float Dt)
{
	Super::NativeTick(Geo, Dt);

	if (FlashA > 0.f && DamageFlash)
	{
		FlashA = FMath::Max(0.f, FlashA - Dt * 1.8f);
		DamageFlash->SetBrushColor(FLinearColor(0.9f, 0.08f, 0.08f, FlashA * 0.5f));
	}

	const AKKPlayerCharacter* P = Cast<AKKPlayerCharacter>(GetOwningPlayerPawn());
	if (P)
	{
		if (HpBar) HpBar->SetPercent(P->GetAttrPct(EKKAttr::Health));
		if (StBar) StBar->SetPercent(P->GetAttrPct(EKKAttr::Stamina));
		if (HuBar) HuBar->SetPercent(P->GetAttrPct(EKKAttr::Hunger));
		if (HintText) HintText->SetText(P->GetContextHint());

		if (const UKKInventoryComponent* Inv = P->FindComponentByClass<UKKInventoryComponent>())
		{
			if (CountWood)  CountWood->SetText(FText::AsNumber(Inv->GetCount(FName("wood"))));
			if (CountStone) CountStone->SetText(FText::AsNumber(Inv->GetCount(FName("stone"))));
			if (CountBerry) CountBerry->SetText(FText::AsNumber(Inv->GetCount(FName("berry"))));
			if (CountMeat)  CountMeat->SetText(FText::AsNumber(Inv->GetCount(FName("meat"))));
		}
	}
	if (ClockText)
	{
		if (const UKKTimeOfDaySubsystem* Time = GetWorld()->GetSubsystem<UKKTimeOfDaySubsystem>())
		{
			ClockText->SetText(Time->GetClockText());
		}
	}

	if (ToastT > 0.f && ToastText)
	{
		ToastT = FMath::Max(0.f, ToastT - Dt);
		FLinearColor C = ToastText->GetColorAndOpacity().GetSpecifiedColor();
		C.A = FMath::Clamp(ToastT / 0.8f, 0.f, 1.f); // son 0.8 sn'de erir
		ToastText->SetColorAndOpacity(FSlateColor(C));
	}

	// Kalp barı: aktör tek; bir kez bul, sonra zayıf işaretçiyle anket et (replike HP istemcide akar).
	if (!HeartRef.IsValid())
	{
		for (TActorIterator<AKKHeartStone> It(GetWorld()); It; ++It) { HeartRef = *It; break; }
	}
	if (HeartBar && HeartRef.IsValid())
	{
		HeartBar->SetPercent(HeartRef->GetHPPct());
	}
}

void UKKHUDWidget::ShowToast(const FString& Msg, const FLinearColor& Col)
{
	if (!ToastText) return;
	ToastText->SetText(FText::FromString(Msg));
	FLinearColor C = Col; C.A = 1.f;
	ToastText->SetColorAndOpacity(FSlateColor(C));
	ToastT = 3.0f;
}
