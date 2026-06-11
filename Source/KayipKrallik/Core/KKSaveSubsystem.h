// KKSaveSubsystem.h — Kaydet/yükle + migrasyon zinciri. [TKT-F0-005]
#pragma once

#include "CoreMinimal.h"
#include "Subsystems/GameInstanceSubsystem.h"
#include "KKSaveSubsystem.generated.h"

class UKKSaveGame;

UCLASS()
class KAYIPKRALLIK_API UKKSaveSubsystem : public UGameInstanceSubsystem
{
	GENERATED_BODY()

public:
	static constexpr int32 CurrentVersion = 4;

	bool HasSave() const;

	/** Diskteki kaydı okur, migrasyon zincirinden geçirir. Yoksa nullptr. */
	UKKSaveGame* LoadMigrated();

	/** Dünyanın anlık durumunu toplar ve diske yazar. Yalnız otoritede çağrılmalı. */
	bool SaveWorld(UWorld* World);

	void DeleteSave();

private:
	static const TCHAR* SlotName() { return TEXT("KayipKrallik_Slot0"); }

	/** v1 -> v2 -> ... zinciri. Her adım tek sürüm yükseltir (plan 95). */
	void MigrateToCurrent(UKKSaveGame* Save) const;
};
