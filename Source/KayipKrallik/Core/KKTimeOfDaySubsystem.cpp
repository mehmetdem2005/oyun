// KKTimeOfDaySubsystem.cpp
#include "Core/KKTimeOfDaySubsystem.h"
#include "Core/KKMessageBusSubsystem.h"
#include "Core/KKGameplayTags.h"
#include "Player/KKGameState.h"
#include "KayipKrallik.h"
#include "Engine/World.h"
#include "Engine/DirectionalLight.h"
#include "Engine/ExponentialHeightFog.h"
#include "Components/DirectionalLightComponent.h"
#include "Components/ExponentialHeightFogComponent.h"

// SANAT-YONU.md ile senkron palet (KO gece tonu #070a1e).
namespace
{
	const FLinearColor SunDay   (1.00f, 0.95f, 0.84f);
	const FLinearColor SunDusk  (1.00f, 0.52f, 0.28f);
	const FLinearColor SunNight (0.22f, 0.30f, 0.58f);
	const FLinearColor FogDay   (0.74f, 0.81f, 0.90f);
	const FLinearColor FogDusk  (0.55f, 0.34f, 0.30f);
	const FLinearColor FogNight (0.027f, 0.039f, 0.118f); // #070a1e
}

bool UKKTimeOfDaySubsystem::IsAuthority() const
{
	const UWorld* W = GetWorld();
	return W && W->GetNetMode() != NM_Client;
}

void UKKTimeOfDaySubsystem::SetTime(float InTimeSec, int32 InDay)
{
	TimeSec = FMath::Fmod(FMath::Max(0.f, InTimeSec), CycleSeconds);
	Day = FMath::Max(1, InDay);
	Darkness = ComputeDarkness(TimeSec / CycleSeconds);
	bWasNight = Darkness > 0.5f;
	PushToGameState();
}

float UKKTimeOfDaySubsystem::ComputeDarkness(float DayFrac)
{
	// KO eğrisinin eşdeğeri: 06:00 başlar; %45'e kadar gündüz, alacakaranlık,
	// %58-%92 zifiri gece, sonra şafak.
	if (DayFrac < 0.45f) return 0.f;
	if (DayFrac < 0.58f) return FMath::SmoothStep(0.f, 1.f, (DayFrac - 0.45f) / 0.13f);
	if (DayFrac < 0.92f) return 1.f;
	return 1.f - FMath::SmoothStep(0.f, 1.f, (DayFrac - 0.92f) / 0.08f);
}

void UKKTimeOfDaySubsystem::Tick(float DeltaTime)
{
	UWorld* W = GetWorld();
	if (!W || !W->IsGameWorld()) return;

	if (IsAuthority())
	{
		TimeSec += DeltaTime;
		if (TimeSec >= CycleSeconds)
		{
			TimeSec -= CycleSeconds;
			++Day;
			if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(W))
			{
				Bus->BroadcastTag(KKTags::Time_DayChanged, Day);
			}
			UE_LOG(LogKK, Log, TEXT("[Time] Gun %d basladi."), Day);
		}
		PushToGameState();
	}
	else
	{
		PullFromGameState();
	}

	Darkness = ComputeDarkness(TimeSec / CycleSeconds);

	// Gece/şafak geçiş yayınları yalnız otoritede (oyun mantığı), görsel herkeste.
	const bool bNight = Darkness > 0.5f;
	if (IsAuthority() && bNight != bWasNight)
	{
		if (UKKMessageBusSubsystem* Bus = UKKMessageBusSubsystem::Get(W))
		{
			Bus->BroadcastTag(bNight ? KKTags::Time_NightStarted : KKTags::Time_DawnStarted, Day);
		}
	}
	bWasNight = bNight;

	ApplyVisuals();
}

void UKKTimeOfDaySubsystem::PushToGameState()
{
	if (AKKGameState* GS = GetWorld() ? GetWorld()->GetGameState<AKKGameState>() : nullptr)
	{
		GS->Day = Day;
		GS->TimeSec = TimeSec;
	}
}

void UKKTimeOfDaySubsystem::PullFromGameState()
{
	if (const AKKGameState* GS = GetWorld() ? GetWorld()->GetGameState<AKKGameState>() : nullptr)
	{
		Day = GS->Day;
		TimeSec = GS->TimeSec;
	}
}

void UKKTimeOfDaySubsystem::ApplyVisuals()
{
	const float D = Darkness;
	// Alacakaranlık ağırlığı: geçiş bölgesinde turuncu vurgusu (estetik: SANAT-YONU 3.2).
	const float Duskiness = FMath::Clamp(1.f - FMath::Abs(D - 0.5f) * 2.f, 0.f, 1.f);

	if (ADirectionalLight* SunActor = Sun.Get())
	{
		if (UDirectionalLightComponent* L = SunActor->FindComponentByClass<UDirectionalLightComponent>())
		{
			FLinearColor C = FMath::Lerp(SunDay, SunNight, D);
			C = FMath::Lerp(C, SunDusk, Duskiness * 0.65f);
			L->SetLightColor(C);
			L->SetIntensity(FMath::Lerp(5.5f, 0.35f, D));
		}
		// Güneş yayı: gündüz tepeden (-58°), gece sıyırarak (-16°) — uzun gölgesiz mobil profil.
		const float Pitch = FMath::Lerp(-58.f, -16.f, D);
		const float Yaw   = 35.f + (TimeSec / CycleSeconds) * 60.f; // gün boyu hafif süzülme
		SunActor->SetActorRotation(FRotator(Pitch, Yaw, 0.f));
	}

	if (AExponentialHeightFog* FogActor = Fog.Get())
	{
		if (UExponentialHeightFogComponent* F = FogActor->FindComponentByClass<UExponentialHeightFogComponent>())
		{
			FLinearColor C = FMath::Lerp(FogDay, FogNight, D);
			C = FMath::Lerp(C, FogDusk, Duskiness * 0.5f);
			F->SetFogInscatteringColor(C);
			F->SetFogDensity(FMath::Lerp(0.012f, 0.028f, D)); // gece sis kalınlaşır: tehdit hissi
		}
	}
}

FText UKKTimeOfDaySubsystem::GetClockText() const
{
	const float Frac = TimeSec / CycleSeconds;
	const float Hour24 = FMath::Fmod(6.f + Frac * 24.f, 24.f);
	const int32 HH = FMath::FloorToInt32(Hour24);
	const int32 MM = FMath::FloorToInt32(FMath::Frac(Hour24) * 60.f);
	const TCHAR* Icon = (Darkness > 0.5f) ? TEXT("\u263E") : TEXT("\u2600");
	return FText::FromString(FString::Printf(TEXT("%s Gün %d · %02d:%02d"), Icon, Day, HH, MM));
}
