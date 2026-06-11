// KayipKrallik.Build.cs — Modül bağımlılıkları. [TKT-F0-001]
// Kural: bağımlılık dar tutulur. AIModule yalnız AAIController possess için (navmesh YOK, Faz 2'de EQS gelir).
using UnrealBuildTool;

public class KayipKrallik : ModuleRules
{
	public KayipKrallik(ReadOnlyTargetRules Target) : base(Target)
	{
		PCHUsage = PCHUsageMode.UseExplicitOrSharedPCHs;

		PublicDependencyModuleNames.AddRange(new string[]
		{
			"Core", "CoreUObject", "Engine", "InputCore",
			"EnhancedInput",
			"GameplayAbilities", "GameplayTags", "GameplayTasks",
			"AIModule",
			"UMG"
		});

		PrivateDependencyModuleNames.AddRange(new string[]
		{
			"Slate", "SlateCore"
		});
	}
}
