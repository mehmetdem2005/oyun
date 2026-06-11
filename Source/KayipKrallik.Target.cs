// KayipKrallik.Target.cs — Oyun (runtime) hedefi. [TKT-F0-001]
using UnrealBuildTool;

public class KayipKrallikTarget : TargetRules
{
	public KayipKrallikTarget(TargetInfo Target) : base(Target)
	{
		Type = TargetType.Game;
		DefaultBuildSettings = BuildSettingsVersion.Latest;
		IncludeOrderVersion = EngineIncludeOrderVersion.Latest;
		ExtraModuleNames.Add("KayipKrallik");
	}
}
