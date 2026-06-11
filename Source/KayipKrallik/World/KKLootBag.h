// KKLootBag.h — Yağma kesesi: katilsiz ölümün ya da ölen hırsız gölgenin bıraktığı eşya. [TKT-F1-014]
// Çok oyunculu kuralın yere düşen yarısı: "eşya yok olmaz, DÜNYADA bir yerdedir — git al."
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "KKLootBag.generated.h"

class UStaticMeshComponent;
class USphereComponent;

UCLASS()
class KAYIPKRALLIK_API AKKLootBag : public AActor
{
	GENERATED_BODY()

public:
	AKKLootBag();

	/** Sunucu: içerik ata. Boş haritayla çağrılırsa kendini iptal eder. */
	void InitLoot(const TMap<FName, int32>& In);

	/** Kayıt köprüsü (sunucu okur). */
	const TMap<FName, int32>& GetLoot() const { return Loot; }

protected:
	virtual void BeginPlay() override;

	UFUNCTION()
	void OnTouch(UPrimitiveComponent* OverlappedComp, AActor* Other, UPrimitiveComponent* OtherComp,
	             int32 OtherBodyIndex, bool bFromSweep, const FHitResult& Sweep);

	/** Tüm makineler: alınma anı — ses + anında gizlenme (yıkım 0.25 sn gecikmeli, RPC kaybolmasın). */
	UFUNCTION(NetMulticast, Reliable) void Multicast_Taken();
	void Multicast_Taken_Implementation();

	void BuildVisual();

	/** Yalnız sunucu bilir; istemcinin içeriği bilmesi gerekmez (sürpriz = teşvik). */
	TMap<FName, int32> Loot;
	bool bTaken = false;

	UPROPERTY() TObjectPtr<USphereComponent>     Touch;
	UPROPERTY() TObjectPtr<UStaticMeshComponent> Sack;
	UPROPERTY() TObjectPtr<UStaticMeshComponent> Band;

	// DÜRÜST NOT: keseler kayda YAZILMAZ (Faz 1 kalan) — oturum içinde kalıcı,
	// kaydet-çık-gir yapan kese içeriğini kaybeder. README'de işaretli.
};
