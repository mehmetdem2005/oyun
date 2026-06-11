// KKAudioSubsystem.cpp — Sentez reçeteleri Kayıp Orman SFX tablosundan BİREBİR.
#include "Audio/KKAudioSubsystem.h"
#include "Core/KKGameInstance.h"
#include "KayipKrallik.h"
#include "Sound/SoundWaveProcedural.h"
#include "Kismet/GameplayStatics.h"
#include "Engine/Engine.h"
#include "Engine/World.h"

namespace
{
	enum class EWave : uint8 { Sine, Square, Triangle, Saw };

	struct FMixer
	{
		TArray<float> Buf;

		void EnsureLen(int32 Samples)
		{
			if (Buf.Num() < Samples) Buf.SetNumZeroed(Samples);
		}

		// KO tone(): frekans üstel rampa (f1>0 ise), kazanç üstel sönüm -> 0.001
		void AddTone(float F0, float F1, float Dur, EWave Wave, float Vol, float At = 0.f)
		{
			const int32 SR = 32000;
			const int32 N = int32(Dur * SR);
			const int32 Off = int32(At * SR);
			EnsureLen(Off + N);
			F0 = FMath::Max(1.f, F0);
			const bool bRamp = F1 > 0.f;
			F1 = FMath::Max(1.f, F1);
			double Ph = 0.0;
			for (int32 i = 0; i < N; ++i)
			{
				const float T = float(i) / float(N);
				const float F = bRamp ? F0 * FMath::Pow(F1 / F0, T) : F0;
				Ph += 2.0 * PI * F / SR;
				float S;
				switch (Wave)
				{
				case EWave::Square:   S = FMath::Sin(Ph) >= 0.f ? 1.f : -1.f; break;
				case EWave::Triangle: S = (2.f / PI) * FMath::Asin(FMath::Sin(Ph)); break;
				case EWave::Saw:      S = 2.f * FMath::Frac(Ph / (2.0 * PI)) - 1.f; break;
				default:              S = FMath::Sin(Ph); break;
				}
				const float G = Vol * FMath::Pow(0.001f / FMath::Max(0.002f, Vol), T); // üstel sönüm
				Buf[Off + i] += S * G;
			}
		}

		// KO noiseHit(): rampalı beyaz gürültü + tek kutuplu alçak geçiren
		void AddNoise(float Dur, float Vol, float LowpassHz, float At = 0.f)
		{
			const int32 SR = 32000;
			const int32 N = int32(Dur * SR);
			const int32 Off = int32(At * SR);
			EnsureLen(Off + N);
			const float Alpha = 1.f - FMath::Exp(-2.f * PI * LowpassHz / SR);
			float Y = 0.f;
			for (int32 i = 0; i < N; ++i)
			{
				const float X = FMath::FRandRange(-1.f, 1.f) * (1.f - float(i) / float(N));
				Y += Alpha * (X - Y);
				Buf[Off + i] += Y * Vol;
			}
		}

		void ToPCM16(TArray<uint8>& Out) const
		{
			Out.SetNumUninitialized(Buf.Num() * 2);
			int16* P = reinterpret_cast<int16*>(Out.GetData());
			for (int32 i = 0; i < Buf.Num(); ++i)
			{
				P[i] = int16(FMath::Clamp(Buf[i], -1.f, 1.f) * 32767.f);
			}
		}
	};
}

UKKAudioSubsystem* UKKAudioSubsystem::Get(const UObject* Ctx)
{
	if (!GEngine || !Ctx) return nullptr;
	if (const UWorld* W = GEngine->GetWorldFromContextObject(Ctx, EGetWorldErrorMode::ReturnNull))
	{
		if (UGameInstance* GI = W->GetGameInstance())
		{
			return GI->GetSubsystem<UKKAudioSubsystem>();
		}
	}
	return nullptr;
}

void UKKAudioSubsystem::Render(FName Id, FRendered& Out)
{
	FMixer M;
	const FString S = Id.ToString();

	if      (S == TEXT("pickup"))   { M.AddTone(620, 990, 0.09f, EWave::Sine, 0.12f); }
	else if (S == TEXT("swing"))    { M.AddTone(260, 110, 0.08f, EWave::Triangle, 0.09f); }
	else if (S == TEXT("chop"))     { M.AddNoise(0.08f, 0.22f, 1100); M.AddTone(170, 90, 0.07f, EWave::Square, 0.07f); }
	else if (S == TEXT("mine"))     { M.AddNoise(0.06f, 0.26f, 2600); M.AddTone(820, 420, 0.06f, EWave::Square, 0.07f); }
	else if (S == TEXT("hitE"))     { M.AddTone(900, 300, 0.07f, EWave::Square, 0.10f); }
	else if (S == TEXT("hurt"))     { M.AddTone(160, 70, 0.22f, EWave::Saw, 0.18f); M.AddNoise(0.12f, 0.18f, 700); }
	else if (S == TEXT("craft"))    { M.AddTone(523, 0, 0.09f, EWave::Square, 0.11f); M.AddTone(784, 0, 0.13f, EWave::Square, 0.11f, 0.10f); }
	else if (S == TEXT("eat"))      { M.AddTone(300, 380, 0.06f, EWave::Sine, 0.12f); M.AddTone(340, 430, 0.06f, EWave::Sine, 0.12f, 0.08f); }
	else if (S == TEXT("place"))    { M.AddTone(120, 60, 0.13f, EWave::Sine, 0.20f); M.AddNoise(0.07f, 0.16f, 500); }
	else if (S == TEXT("enemyDie")) { M.AddTone(440, 70, 0.26f, EWave::Saw, 0.12f); }
	else if (S == TEXT("playerDie")){ M.AddTone(220, 40, 0.50f, EWave::Saw, 0.16f); M.AddNoise(0.30f, 0.10f, 380, 0.08f); } // ağır, uzun düşüş — düşman ölümünden ayrışır
	else if (S == TEXT("quest"))    { M.AddTone(523, 0, 0.10f, EWave::Square, 0.10f);
	                                  M.AddTone(659, 0, 0.10f, EWave::Square, 0.10f, 0.10f);
	                                  M.AddTone(784, 0, 0.10f, EWave::Square, 0.10f, 0.20f);
	                                  M.AddTone(1047, 0, 0.16f, EWave::Square, 0.10f, 0.30f); }
	else if (S == TEXT("rustle"))   { M.AddNoise(0.10f, 0.14f, 900); }
	else if (S == TEXT("critterDie")){ M.AddTone(220, 120, 0.12f, EWave::Sine, 0.12f); M.AddNoise(0.08f, 0.10f, 600); } // yumuşak av düşüşü
	else if (S == TEXT("bolt"))    { M.AddNoise(0.08f, 0.16f, 1800); M.AddTone(320, 180, 0.07f, EWave::Triangle, 0.09f); } // gergi bırakışı + vınlama
	else if (S == TEXT("heartHit")){ M.AddTone(95, 70, 0.12f, EWave::Sine, 0.22f); M.AddNoise(0.05f, 0.20f, 500); } // derin çan-gong
	else if (S == TEXT("heartDie")){ M.AddTone(160, 28, 0.90f, EWave::Saw, 0.20f); M.AddNoise(0.50f, 0.15f, 300, 0.10f); } // krallığın çöküşü
	else if (S == TEXT("door"))     { M.AddNoise(0.05f, 0.14f, 800); M.AddTone(140, 90, 0.10f, EWave::Square, 0.08f, 0.02f); } // ahşap menteşe
	else                            { M.AddTone(800, 0, 0.04f, EWave::Square, 0.06f); } // click + bilinmeyen

	M.ToPCM16(Out.PCM);
	Out.Duration = float(M.Buf.Num()) / float(SR);
}

const UKKAudioSubsystem::FRendered* UKKAudioSubsystem::GetOrRender(FName Id)
{
	if (const FRendered* Found = Cache.Find(Id)) return Found;
	FRendered R;
	Render(Id, R);
	return &Cache.Add(Id, MoveTemp(R));
}

FName UKKAudioSubsystem::CategoryOf(FName Id) const
{
	static const TSet<FName> Combat = { "swing", "chop", "mine", "hitE", "hurt", "enemyDie", "playerDie" };
	static const TSet<FName> UI     = { "click", "quest", "craft" };
	if (Combat.Contains(Id)) return FName("Combat");
	if (UI.Contains(Id))     return FName("UI");
	return FName("World");
}

bool UKKAudioSubsystem::PassesConcurrency(FName Cat, float Dur)
{
	const double Now = FPlatformTime::Seconds();
	Active.RemoveAll([Now](const FActive& A) { return A.Until <= Now; });

	int32 Count = 0;
	for (const FActive& A : Active) if (A.Category == Cat) ++Count;

	const int32 Cap = (Cat == FName("Combat")) ? 6 : (Cat == FName("UI") ? 4 : 6);
	if (Count >= Cap) return false; // KO yaklaşımı: taşanı at (en ucuz, kulak fark etmez)

	Active.Add({ Now + Dur, Cat });
	return true;
}

void UKKAudioSubsystem::PlaySFX(const UObject* Ctx, FName Id)
{
	if (!Ctx) return;
	const FRendered* R = GetOrRender(Id);
	if (!R || R->PCM.Num() == 0) return;
	if (!PassesConcurrency(CategoryOf(Id), R->Duration)) return;

	// Her çalış için taze prosedürel dalga: kuyruk tek kullanımlık.
	USoundWaveProcedural* W = NewObject<USoundWaveProcedural>(GetTransientPackage());
	W->SetSampleRate(SR);
	W->NumChannels = 1;
	W->Duration = R->Duration;
	W->bLooping = false;
	W->QueueAudio(R->PCM.GetData(), R->PCM.Num());
	// Dürüst not: bazı platform ses yolları finite Duration'da kuyruğu erken kesebilir;
	// cihazda sessiz kalan SFX olursa Duration'a +0.05 pay verin (Faz 3'te MetaSounds'a geçilecek).

	float Master = 0.5f;
	if (const UWorld* World = GEngine->GetWorldFromContextObject(Ctx, EGetWorldErrorMode::ReturnNull))
	{
		if (const UKKGameInstance* GI = World->GetGameInstance<UKKGameInstance>())
		{
			Master = GI->MasterVolume;
		}
	}
	UGameplayStatics::PlaySound2D(Ctx, W, Master);
}
