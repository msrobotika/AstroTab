plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    compileOnly("org.json:json:20240303")
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}
tasks.register("testDebugUnitTest") { dependsOn("test") }

tasks.withType<Test>().configureEach {
    doFirst { rootProject.file(".gradle/tmp").mkdirs() }
    systemProperty("java.io.tmpdir", rootProject.layout.projectDirectory.dir(".gradle/tmp").asFile.absolutePath)
}
