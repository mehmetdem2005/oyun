// :core — Android'siz saf Kotlin/JVM modülü. Tüm oyun kuralı burada yaşar,
// burada derlenir, burada test edilir. Kabuk (:app) yalnız buna bağımlıdır.
plugins { id("org.jetbrains.kotlin.jvm") version "2.0.21" }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

// "smoke" kaynak kümesi: davranış senaryoları (src/smoke/kotlin/Smoke.kt)
val main = sourceSets["main"]
val smoke = sourceSets.create("smoke") {
    compileClasspath += main.output
    runtimeClasspath += main.output
}
dependencies { "smokeImplementation"(kotlin("stdlib")) }

// KALİTE KAPISI: bu görev geçmeden CI APK üretmez (bkz. .github/workflows)
tasks.register<JavaExec>("smoke") {
    group = "verification"
    description = "Çekirdek oyun kuralı duman senaryoları"
    classpath = smoke.runtimeClasspath
    mainClass.set("SmokeKt")
}
