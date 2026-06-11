// KKPlayerController.h — Enhanced Input'u TAMAMEN kodda kurar (editör varlığı sıfır). [TKT-F0-017]
// Mobil: DefaultInput.ini sanal joystick'leri Gamepad_Left2D/RightX-Y olarak besler.
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/PlayerController.h"
#include "KKPlayerController.generated.h"

class UInputAction;
class UInputMappingContext;
struct FInputActionValue;

UCLASS()
class KAYIPKRALLIK_API AKKPlayerController : public APlayerController
{
	GENERATED_BODY()

protected:
	virtual void BeginPlay() override;
	virtual void SetupInputComponent() override;

	UPROPERTY() TObjectPtr<UInputAction> IA_Move;
	UPROPERTY() TObjectPtr<UInputAction> IA_Primary;
	UPROPERTY() TObjectPtr<UInputAction> IA_Interact;
	UPROPERTY() TObjectPtr<UInputAction> IA_Build;     // B: inşa modu (Faz 1)
	UPROPERTY() TObjectPtr<UInputMappingContext> Ctx;

	bool bInputReady = false;
	void EnsureInputSetup();

	void OnMove(const FInputActionValue& V);
	void OnPrimary(const FInputActionValue& V);
	void OnInteract(const FInputActionValue& V);
	void OnBuild(const FInputActionValue& V);
};
