// KKTimeOfDaySubsystem.h — Gün/gece kalbi: KO'nun 240sn CYCLE'inin UE portu. [TKT-F0-006]
// Otorite zamani ilerletir ve GameState'e replike eder; her makine görseli kendisi sürer.
#pragma once

#include "CoreMinimal.h"
#include "Subsystems/WorldSubsystem.h"
#include "KKTimeOfDaySubsystem.generated.h"

class ADirectionalLight;
class AExponentialHeightFog;

UCLASS()
class KAYIPKRALLIK_API UKKTimeOfDaySubsystem : public UTickableWorldSubsystem
{
	GENERATED_BODY()

public:
	static constexpr float CycleSeconds = 240.f; // KO: CYCLE = 240

	virtual void Tick(float DeltaTime) override;
	virtual TStatId GetStatId() const override
	{
		RETURN_QUICK_DECLARE_CYCLE_STAT(UKKTimeOfDaySubsystem, STATGROUP_Tickables);
	}

	void RegisterSun(ADirectionalLight* InSun) { Sun = InSun; }
	void RegisterFog(AExponentialHeightFog* InFog) { Fog = InFog; }

	/** Otorite kayıttan yükleme / yeni gün ayarı için. */
	void SetTime(float InTimeSec, int32 InDay);

	float GetTimeSec() const { return TimeSec; }
	int32 GetDay() const { return Day; }

	/** 0 = gündüz, 1 = zifiri gece (KO 'darkness' eşdeğeri). */
	float GetDarkness01() const { return Darkness; }
	bool  IsNight() const { return Darkness > 0.5f; }

	/** "Gün 3 · 14:30" biçimli saat metni (06:00 başlangıç, KO ile aynı). */
	FText GetClockText() const;

private:
	float TimeSec = 0.f;   // 0 = 06:00
	int32 Day = 1;
	float Darkness = 0.f;
	bool  bWasNight = false;

	TWeakObjectPtr<ADirectionalLight> Sun;
	TWeakObjectPtr<AExponentialHeightFog> Fog;

	bool IsAuthority() const;
	void PushToGameState();
	void PullFromGameState();
	static float ComputeDarkness(float DayFrac);
	void ApplyVisuals();
};
