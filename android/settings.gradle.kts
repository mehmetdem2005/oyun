// Kayıp Krallık — modüler yapı: :core (saf Kotlin, kural) + :app (Android kabuk)
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "KayipKrallik"
include(":core", ":app")
