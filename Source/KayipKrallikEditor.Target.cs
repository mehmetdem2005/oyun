// KayipKrallikEditor.Target.cs — Editör hedefi. [TKT-F0-001]
using UnrealBuildTool;

public class KayipKrallikEditorTarget : TargetRules
{
	public KayipKrallikEditorTarget(TargetInfo Target) : base(Target)
	{
		Type = TargetType.Editor;
		DefaultBuildSettings = BuildSettingsVersion.Latest;
		IncludeOrderVersion = EngineIncludeOrderVersion.Latest;
		ExtraModuleNames.Add("KayipKrallik");
	}
}
