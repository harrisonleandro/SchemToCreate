plugins {
    java
    application
}

group = "dev.schemtocreate"
version = "1.0.0"

description = "Offline converter from WorldEdit Sponge Schematic (.schem) to Create Schematic (.nbt)"

java {
    toolchain {
        // 17 is the floor (Minecraft 1.20.1 / Create 6.0.8 era). The code also runs on 21.
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    // CLI parsing: declarative, generates --help, zero transitive dependencies.
    implementation("info.picocli:picocli:4.7.6")

    // Logging facade required by the spec, plus a backend for the standalone jar.
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("dev.schemtocreate.cli.Main")
    // Gives `./gradlew installDist` a launcher literally named `schemtocreate`, so the
    // documented command works verbatim instead of only as `java -jar ...`.
    applicationName = "schemtocreate"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing", "-parameters"))
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

tasks.test {
    useJUnitPlatform()
    // The "gigantic schematic" tests stream millions of blocks; give them room but keep
    // the heap small enough that a regression into non-streaming code actually fails.
    maxHeapSize = System.getProperty("test.heap", "512m")
    // Headless makes the GUI launch decision deterministic and guarantees no test can
    // accidentally open a window on a build machine.
    systemProperty("java.awt.headless", "true")
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

/**
 * Self-contained executable jar. Built by hand instead of pulling in the Shadow plugin:
 * the dependency set is tiny and unshaded, so plain unpacking is enough and keeps the
 * build reproducible offline after the first dependency download.
 */
val fatJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the standalone executable jar (SchemToCreate.jar)"

    archiveBaseName.set("SchemToCreate")
    archiveClassifier.set("")
    archiveVersion.set("")

    manifest {
        attributes(
            "Main-Class" to application.mainClass.get(),
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Multi-Release" to "true"
        )
    }

    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })

    // Signature files from third-party jars break a merged jar; module descriptors and
    // duplicated licence files are noise.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.MF")
    exclude("module-info.class", "META-INF/versions/*/module-info.class")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("build") {
    dependsOn(fatJar)
}

/**
 * Regenerates the sample schematics under examples/. The generator lives in the test source
 * set because it writes the Sponge format, which the shipped tool only reads.
 */
tasks.register<JavaExec>("generateExamples") {
    group = "documentation"
    description = "Writes sample .schem files into examples/"
    mainClass.set("dev.schemtocreate.testutil.ExampleGenerator")
    classpath = sourceSets.test.get().runtimeClasspath
    args("examples")
}

tasks.named<JavaExec>("run") {
    // Allows: ./gradlew run --args="input.schem output.nbt"
    standardInput = System.`in`
}
