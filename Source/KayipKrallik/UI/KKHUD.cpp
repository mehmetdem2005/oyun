// KKHUD.cpp
#include "UI/KKHUD.h"
#include "UI/KKHUDWidget.h"
#include "Blueprint/UserWidget.h"

void AKKHUD::BeginPlay()
{
	Super::BeginPlay();
	if (PlayerOwner && PlayerOwner->IsLocalController())
	{
		Widget = CreateWidget<UKKHUDWidget>(PlayerOwner, UKKHUDWidget::StaticClass());
		if (Widget) Widget->AddToViewport();
	}
}
