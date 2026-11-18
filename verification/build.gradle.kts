plugins { kotlin("jvm") }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
sourceSets {
    main {
        kotlin.srcDirs("../app/src/main/java/com/aminfathi/nava/network", "../app/src/main/java/com/aminfathi/nava/ai")
        kotlin.exclude("**/InterviewOrchestrator.kt")
    }
    test { kotlin.srcDir("../app/src/test/java") }
}
dependencies {
    implementation(project(":domain"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}
