import java.io.File
plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "org.msrobotika.astrotab"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "org.msrobotika.astrotab"
        minSdk = 23
        targetSdk = 36
        versionCode = 9
        versionName = "0.7.1"
        ndk { abiFilters += "armeabi-v7a" }
    }
    signingConfigs { getByName("debug") { storeFile = rootProject.file(".gradle/astrotab-debug.keystore") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].assets.srcDir("../core/src/main/resources")
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true; checkReleaseBuilds = true }
    packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*") }
}
dependencies {
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
// SDKs de tests declarados y descargados por Gradle, independientes de user.home/.m2.
// Una configuración por versión evita que la resolución de conflictos retenga solo la más nueva.
val robolectricSdks=listOf("6.0.1_r3-robolectric-r1-i7","9-robolectric-4913185-2-i7","15-robolectric-12650502-i7").mapIndexed { index,version ->
    configurations.create("robolectricSdk$index").also { configuration ->
        dependencies.add(configuration.name,"org.robolectric:android-all-instrumented:$version")
    }
}
val prepareRobolectricSdk by tasks.registering(Sync::class) {
    robolectricSdks.forEach { from(it) }
    into(layout.buildDirectory.dir("robolectric-sdk"))
}
// Recursos expandidos: evita ZipFS/create=true sobre jars en Windows restringido.
val prepareNativeRuntime by tasks.registering(Copy::class) {
    inputs.property("expandedResourcesVersion",1)
    from({ configurations.getByName("debugUnitTestRuntimeClasspath").filter { it.name.startsWith("nativeruntime-dist-") }.map { zipTree(it) } })
    include("fonts/**","icu/**","native/**")
    into(layout.buildDirectory.dir("native-runtime"))
}
tasks.withType<Test>().configureEach {
    dependsOn(prepareRobolectricSdk,prepareNativeRuntime)
    doFirst {
        layout.buildDirectory.dir("screens").get().asFile.mkdirs()
        rootProject.file(".gradle/tmp").mkdirs()
    }
    doFirst { classpath=files(classpath.files.map { if(it.name.startsWith("nativeruntime-dist-")) layout.buildDirectory.dir("native-runtime").get().asFile else it }) }
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", layout.buildDirectory.dir("robolectric-sdk").get().asFile.absolutePath)
    systemProperty("java.io.tmpdir", rootProject.layout.projectDirectory.dir(".gradle/tmp").asFile.absolutePath)
    systemProperty("astrotab.screens", layout.buildDirectory.dir("screens").get().asFile.absolutePath)
}

// Clave de depuración local; no se distribuye ni sirve para una publicación.
val generateDebugKey by tasks.registering(Exec::class) {
    val key = rootProject.file(".gradle/astrotab-debug.keystore")
    outputs.file(key)
    onlyIf { !key.exists() }
    commandLine(File(System.getProperty("java.home"), "bin/keytool" + if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""),
        "-genkeypair", "-keystore", key.absolutePath, "-storepass", "android", "-keypass", "android",
        "-alias", "AndroidDebugKey", "-dname", "CN=Android Debug,O=MS Robotika,C=ES",
        "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-storetype", "JKS")
}
tasks.matching { it.name == "validateSigningDebug" }.configureEach { dependsOn(generateDebugKey) }
