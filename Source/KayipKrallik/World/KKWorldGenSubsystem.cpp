// KKWorldGenSubsystem.cpp — KO satır satır portu; sabitlere DOKUNMA (determinizm sözleşmesi).
#include "World/KKWorldGenSubsystem.h"
#include "KayipKrallik.h"

void UKKWorldGenSubsystem::InitSeed(int32 InSeed)
{
	Seed = InSeed;
	CampCache.Reset();
	CampNone.Reset();
	Harvested.Reset();
	UE_LOG(LogKK, Log, TEXT("[WorldGen] Seed = %d"), Seed);
}

double UKKWorldGenSubsystem::Hash01(int32 X, int32 Y, int32 Salt) const
{
	// JS: imul(x,374761393)+imul(y,668265263)+imul(seed,974711); h=imul(h^(h>>>13),1274126177); h^=h>>>16
	const uint32 S = uint32(Seed + Salt);
	uint32 H = uint32(X) * 374761393u + uint32(Y) * 668265263u + S * 974711u;
	H = (H ^ (H >> 13)) * 1274126177u;
	H ^= H >> 16;
	return double(H) / 4294967296.0;
}

static FORCEINLINE double KKSmooth(double T) { return T * T * (3.0 - 2.0 * T); }
static FORCEINLINE double KKLerp(double A, double B, double T) { return A + (B - A) * T; }

double UKKWorldGenSubsystem::VNoise(double X, double Y, int32 Salt) const
{
	const int32 XI = FMath::FloorToInt32(X), YI = FMath::FloorToInt32(Y);
	const double XF = X - XI, YF = Y - YI;
	const double A = Hash01(XI,     YI,     Salt);
	const double B = Hash01(XI + 1, YI,     Salt);
	const double C = Hash01(XI,     YI + 1, Salt);
	const double D = Hash01(XI + 1, YI + 1, Salt);
	const double SX = KKSmooth(XF), SY = KKSmooth(YF);
	return KKLerp(KKLerp(A, B, SX), KKLerp(C, D, SX), SY);
}

double UKKWorldGenSubsystem::FBM(double X, double Y, int32 Salt, int32 Oct) const
{
	double V = 0.0, Amp = 0.5, F = 1.0, Tot = 0.0;
	for (int32 i = 0; i < Oct; ++i)
	{
		V += VNoise(X * F, Y * F, Salt + i * 101) * Amp;
		Tot += Amp; Amp *= 0.5; F *= 2.0;
	}
	return V / Tot;
}

bool UKKWorldGenSubsystem::CampInfo(int32 RX, int32 RY, FIntPoint& OutCenter) const
{
	const FIntPoint Key(RX, RY);
	if (const FIntPoint* Found = CampCache.Find(Key)) { OutCenter = *Found; return true; }
	if (CampNone.Contains(Key)) return false;

	const bool bHas = (RX == 0 && RY == 0) || Hash01(RX, RY, 700) < 0.06;
	if (!bHas) { CampNone.Add(Key); return false; }

	OutCenter.X = RX * 28 + 8 + FMath::FloorToInt32(Hash01(RX, RY, 701) * 12.0);
	OutCenter.Y = RY * 28 + 8 + FMath::FloorToInt32(Hash01(RX, RY, 702) * 12.0);
	CampCache.Add(Key, OutCenter);
	return true;
}

double UKKWorldGenSubsystem::CampDist(int32 TX, int32 TY) const
{
	FIntPoint C;
	const int32 RX = FMath::FloorToInt32(double(TX) / 28.0);
	const int32 RY = FMath::FloorToInt32(double(TY) / 28.0);
	if (!CampInfo(RX, RY, C)) return 99.0;
	const double DX = TX - C.X, DY = TY - C.Y;
	return FMath::Sqrt(DX * DX + DY * DY);
}

EKKTile UKKWorldGenSubsystem::GetTile(int32 TX, int32 TY) const
{
	const double CD = CampDist(TX, TY);
	if (CD <= 3.3) return EKKTile::Camp;
	if (CD <= 4.6) return EKKTile::Path;

	const double E = FBM(TX * 0.045, TY * 0.045, 0, 4);
	if (E < 0.30)  return EKKTile::Deep;
	if (E < 0.345) return EKKTile::Water;
	if (E < 0.375) return EKKTile::Sand;

	const double P = FBM(TX * 0.018 + 40.0, TY * 0.018 - 40.0, 9, 2);
	if (FMath::Abs(P - 0.5) < 0.011) return EKKTile::Path;

	const double M = FBM(TX * 0.07 + 100.0, TY * 0.07 + 100.0, 5, 3);
	return (M > 0.60) ? EKKTile::DarkGrass : EKKTile::Grass;
}

FKKResourceSpec UKKWorldGenSubsystem::GetResourceAt(int32 TX, int32 TY) const
{
	FKKResourceSpec Out;
	const EKKTile Tile = GetTile(TX, TY);

	FIntPoint C;
	const bool bCampRegion = CampInfo(FMath::FloorToInt32(double(TX) / 28.0),
	                                  FMath::FloorToInt32(double(TY) / 28.0), C);
	const bool bNearCamp = bCampRegion && CampDist(TX, TY) < 7.0;

	// KO jitter: (h-0.5)*10px / 32px karo -> oransal sapma
	const double JX = (Hash01(TX, TY, 41) - 0.5) * (10.0 / 32.0) * TileSize;
	const double JY = (Hash01(TX, TY, 42) - 0.5) * (8.0  / 32.0) * TileSize;
	Out.JitterUU = FVector2D(JX, JY);

	if ((Tile == EKKTile::Grass || Tile == EKKTile::DarkGrass) && !bNearCamp)
	{
		const double R = Hash01(TX, TY, 31);
		const double F = FBM(TX * 0.06 + 50.0, TY * 0.06 + 50.0, 13, 3);
		if (F > 0.55 && R < 0.16)
		{
			Out.Type = EKKResource::Tree;
			Out.Variant = FMath::FloorToInt32(Hash01(TX, TY, 32) * 3.0);
		}
		else if (R > 0.974)
		{
			Out.Type = EKKResource::Rock;
			Out.Variant = FMath::FloorToInt32(Hash01(TX, TY, 33) * 3.0);
		}
		else if (Tile == EKKTile::Grass && R > 0.948)
		{
			Out.Type = EKKResource::Bush;
		}
	}
	else if (Tile == EKKTile::Sand && !bNearCamp && Hash01(TX, TY, 31) < 0.035)
	{
		Out.Type = EKKResource::Rock;
		Out.Variant = 2;
	}
	return Out;
}

FIntPoint UKKWorldGenSubsystem::FindStartCampTile() const
{
	FIntPoint C(8, 8);
	CampInfo(0, 0, C); // (0,0) bölgesi her zaman kamp içerir (KO kuralı)
	return C;
}

void UKKWorldGenSubsystem::MarkHarvested(int64 Key, float RespawnAt) { Harvested.Add(Key, RespawnAt); }

bool UKKWorldGenSubsystem::IsHarvested(int64 Key, float Now) const
{
	if (const float* T = Harvested.Find(Key))
	{
		return Now < *T; // süresi geçtiyse canlı say (temizlik tembel — KO alive() ile aynı)
	}
	return false;
}

void UKKWorldGenSubsystem::ExportHarvested(TArray<int64>& OutKeys, TArray<float>& OutTimes) const
{
	OutKeys.Reset(); OutTimes.Reset();
	for (const TPair<int64, float>& P : Harvested) { OutKeys.Add(P.Key); OutTimes.Add(P.Value); }
}

void UKKWorldGenSubsystem::ImportHarvested(const TArray<int64>& Keys, const TArray<float>& Times)
{
	Harvested.Reset();
	const int32 N = FMath::Min(Keys.Num(), Times.Num());
	for (int32 i = 0; i < N; ++i) Harvested.Add(Keys[i], Times[i]);
}
