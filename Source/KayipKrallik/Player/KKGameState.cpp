#include "Player/KKGameState.h"
#include "Net/UnrealNetwork.h"

void AKKGameState::GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const
{
	Super::GetLifetimeReplicatedProps(OutLifetimeProps);
	DOREPLIFETIME(AKKGameState, WorldSeed);
	DOREPLIFETIME(AKKGameState, Day);
	DOREPLIFETIME(AKKGameState, TimeSec);
}
