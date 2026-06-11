// KKAudioSubsystem.h — Prosedürel chiptune SFX motoru (KO WebAudio tablosunun PCM portu). [TKT-F0-021]
// Faz 0 sözleşmesi: tek API PlaySFX(FName) — Faz 3'te MetaSounds varlıkları aynı API'nin
// arkasına takılır, çağıran kod DEĞİŞMEZ (port & adapters).
#pragma once

#include "CoreMinimal.h"
#include "Subsystems/GameInstanceSubsystem.h"
#include "KKAudioSubsystem.generated.h"

UCLASS()
class KAYIPKRALLIK_API UKKAudioSubsystem : public UGameInstanceSubsystem
{
	GENERATED_BODY()

public:
	static UKKAudioSubsystem* Get(const UObject* WorldContextObject);

	/** 2D çal (UI + yakın dünya). Bilinen kimlikler: pickup, swing, chop, mine, hitE,
	 *  hurt, craft, eat, place, enemyDie, quest, click, rustle. */
	void PlaySFX(const UObject* WorldContext, FName Id);

	/** Faz 0: 2D'ye düşer. Faz 3: mesafe zayıflatması + Sound Concurrency varlıkları. */
	void PlaySFXAt(const UObject* WorldContext, FName Id, const FVector& Location)
	{
		PlaySFX(WorldContext, Id);
	}

private:
	static constexpr int32 SR = 32000; // örnekleme hızı (mobil dostu)

	struct FRendered { TArray<uint8> PCM; float Duration = 0.f; };
	TMap<FName, FRendered> Cache;

	struct FActive { double Until = 0.0; FName Category; };
	TArray<FActive> Active;

	const FRendered* GetOrRender(FName Id);
	static void Render(FName Id, FRendered& Out);
	FName CategoryOf(FName Id) const;
	bool  PassesConcurrency(FName Cat, float Dur);
};
