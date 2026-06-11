// KKPlayerController.cpp
#include "Player/KKPlayerController.h"
#include "Player/KKPlayerCharacter.h"
#include "KayipKrallik.h"
#include "Engine/LocalPlayer.h"
#include "EnhancedInputComponent.h"
#include "EnhancedInputSubsystems.h"
#include "InputAction.h"
#include "InputMappingContext.h"
#include "InputModifiers.h"
#include "InputActionValue.h"

void AKKPlayerController::BeginPlay()
{
	Super::BeginPlay();
	EnsureInputSetup();
}

void AKKPlayerController::SetupInputComponent()
{
	Super::SetupInputComponent();
	EnsureInputSetup();
}

void AKKPlayerController::EnsureInputSetup()
{
	if (bInputReady || !IsLocalController() || !InputComponent) return;

	ULocalPlayer* LP = GetLocalPlayer();
	UEnhancedInputLocalPlayerSubsystem* Sub = LP ? ULocalPlayer::GetSubsystem<UEnhancedInputLocalPlayerSubsystem>(LP) : nullptr;
	UEnhancedInputComponent* EIC = Cast<UEnhancedInputComponent>(InputComponent);
	if (!Sub || !EIC) return;

	// ---- Eylemler ----
	IA_Move = NewObject<UInputAction>(this);
	IA_Move->ValueType = EInputActionValueType::Axis2D;

	IA_Primary = NewObject<UInputAction>(this);
	IA_Primary->ValueType = EInputActionValueType::Boolean;

	IA_Interact = NewObject<UInputAction>(this);
	IA_Interact->ValueType = EInputActionValueType::Boolean;
	IA_Build = NewObject<UInputAction>(this);
	IA_Build->ValueType = EInputActionValueType::Boolean;

	// ---- Bağlam + tuşlar ----
	Ctx = NewObject<UInputMappingContext>(this);
	auto Map = [this](UInputAction* A, FKey K, bool bSwizzle, bool bNegate)
	{
		FEnhancedActionKeyMapping& M = Ctx->MapKey(A, K);
		if (bSwizzle) M.Modifiers.Add(NewObject<UInputModifierSwizzleAxis>(this)); // X -> Y
		if (bNegate)  M.Modifiers.Add(NewObject<UInputModifierNegate>(this));
	};

	// Hareket: ekran-sağ = X ekseni, ekran-yukarı = Y ekseni (karakter Move() sözleşmesi).
	Map(IA_Move, EKeys::D, false, false);
	Map(IA_Move, EKeys::A, false, true);
	Map(IA_Move, EKeys::W, true,  false);
	Map(IA_Move, EKeys::S, true,  true);
	Map(IA_Move, EKeys::Gamepad_Left2D, false, false); // mobil sol sanal joystick

	// SALDIR/KES: LMB + Space + gamepad A + sağ sanal joystick (herhangi yön = saldır)
	Map(IA_Primary, EKeys::LeftMouseButton, false, false);
	Map(IA_Primary, EKeys::SpaceBar, false, false);
	Map(IA_Primary, EKeys::Gamepad_FaceButton_Bottom, false, false);
	Map(IA_Primary, EKeys::Gamepad_RightX, false, false);
	Map(IA_Primary, EKeys::Gamepad_RightY, false, false);

	// Etkileşim: E + gamepad B
	Map(IA_Interact, EKeys::E, false, false);
	Map(IA_Interact, EKeys::Gamepad_FaceButton_Right, false, false);
	Map(IA_Build, EKeys::B, false, false);
	Map(IA_Build, EKeys::Gamepad_FaceButton_Top, false, false); // Y/üçgen

	Sub->ClearAllMappings();
	Sub->AddMappingContext(Ctx, 0);

	EIC->BindAction(IA_Move,     ETriggerEvent::Triggered, this, &AKKPlayerController::OnMove);
	EIC->BindAction(IA_Primary,  ETriggerEvent::Started,   this, &AKKPlayerController::OnPrimary);
	EIC->BindAction(IA_Interact, ETriggerEvent::Started,   this, &AKKPlayerController::OnInteract);
	EIC->BindAction(IA_Build,    ETriggerEvent::Started,   this, &AKKPlayerController::OnBuild);

	bInputReady = true;
	UE_LOG(LogKK, Log, TEXT("[Input] Enhanced Input kod-ici kuruldu (PC + mobil sanal joystick)."));
}

void AKKPlayerController::OnMove(const FInputActionValue& V)
{
	if (AKKPlayerCharacter* C = Cast<AKKPlayerCharacter>(GetPawn()))
	{
		C->Move(V.Get<FVector2D>());
	}
}

void AKKPlayerController::OnPrimary(const FInputActionValue&)
{
	if (AKKPlayerCharacter* C = Cast<AKKPlayerCharacter>(GetPawn())) C->PrimaryAction();
}

void AKKPlayerController::OnBuild(const FInputActionValue&)
{
	if (AKKPlayerCharacter* C = Cast<AKKPlayerCharacter>(GetPawn())) C->ToggleBuildMode();
}

void AKKPlayerController::OnInteract(const FInputActionValue&)
{
	if (AKKPlayerCharacter* C = Cast<AKKPlayerCharacter>(GetPawn())) C->TryInteract();
}
