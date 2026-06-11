// KKDamageable.h — Hasar alabilen varlık arayüzü (oyuncu + gölge ortak dili). [TKT-F0-004]
#pragma once

#include "CoreMinimal.h"
#include "UObject/Interface.h"
#include "KKDamageable.generated.h"

class AActor;

UINTERFACE(MinimalAPI)
class UKKDamageable : public UInterface
{
	GENERATED_BODY()
};

class KAYIPKRALLIK_API IKKDamageable
{
	GENERATED_BODY()

public:
	/** Yalnız otoritede çağrılır (HasAuthority disiplini, plan 6.4). */
	virtual void ReceiveKKDamage(float Amount, AActor* DamageInstigator) = 0;
	virtual bool IsKKAlive() const { return true; }
};
