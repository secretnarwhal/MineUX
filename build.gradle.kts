import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = providers.gradleProperty("archives_base_name").get()
    group = providers.gradleProperty("maven_group").get()
    version = "${libs.versions.minecraft.get()}-${providers.gradleProperty("mod_version").get()}"
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.jdk.get().toInt())
    }
}

// The three parts of MineUX:
//   core    -- the UI itself: desktop, window manager, shell, menus, social client. No mixins:
//              it is reached through com.mineux.core.Hooks, which MineUX's mixins call here and
//              a host mod that builds core into itself may call too.
//   visuals -- the visual modules, full edition only. Built on core.
//   main    -- the Fabric layer: entry point, mixins, the Host core runs on. Built on core only;
//              it finds visuals at runtime, when the jar has them.
// The edition a dev run plays: ./gradlew runClient for the full one, -Pedition=lite for lite.
val edition = providers.gradleProperty("edition").getOrElse("full")
require(edition == "full" || edition == "lite") { "edition must be 'full' or 'lite', not '$edition'" }

val core = sourceSets.create("core") {
    compileClasspath += configurations["compileClasspath"]
    runtimeClasspath += configurations["runtimeClasspath"]
}
val visuals = sourceSets.create("visuals") {
    compileClasspath += core.output + core.compileClasspath
    runtimeClasspath += core.output + core.runtimeClasspath
}
sourceSets.main {
    compileClasspath += core.output
    runtimeClasspath += core.output
    if (edition == "full") runtimeClasspath += visuals.output
}
sourceSets.test {
    compileClasspath += core.output + visuals.output
    runtimeClasspath += core.output + visuals.output
}

// One mod in a dev run, whichever folders its classes come from.
loom {
    mods {
        create("mineux") {
            sourceSet(sourceSets.main.get())
            sourceSet(core)
            if (edition == "full") sourceSet(visuals)
        }
    }
}

// The mixin config's compatibilityLevel has to name the class file version the toolchain emits.
val declaredMixinLevel = Regex("\"compatibilityLevel\"\\s*:\\s*\"JAVA_(\\d+)\"")
    .find(providers.fileContents(layout.projectDirectory.file("src/main/resources/mineux.mixins.json")).asText.get())
    ?.groupValues?.get(1)
require(declaredMixinLevel == libs.versions.jdk.get()) {
    "mineux.mixins.json declares compatibilityLevel JAVA_$declaredMixinLevel, but the " +
        "toolchain compiles this mod for Java ${libs.versions.jdk.get()}. Move them together."
}

fun modProps(name: String) = mapOf(
    "version" to project.version.toString(),
    "mod_name" to name,
    "minecraft_version" to libs.versions.minecraft.get(),
    "loader_version" to libs.versions.fabric.loader.get(),
    "jdk_version" to libs.versions.jdk.get()
)

tasks.processResources {
    val props = modProps(if (edition == "full") "MineUX" else "MineUX Lite")
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

// The name in fabric.mod.json is the dev run's edition (processResources); each jar sets its own.
fun named(spec: CopySpec, from: String, to: String) =
    spec.filesMatching("fabric.mod.json") { filter { it.replace("\"name\": \"$from\"", "\"name\": \"$to\"") } }

// build/libs/mineux-<mc>-<version>.jar: the full edition.
tasks.jar {
    from(core.output)
    from(visuals.output)
    named(this, "MineUX Lite", "MineUX")
}

// build/libs/mineux-<mc>-<version>-lite.jar: no visuals.
val liteJar = tasks.register<Jar>("liteJar") {
    group = "build"
    description = "MineUX Lite: the desktop without the visual modules."
    archiveClassifier = "lite"
    from(sourceSets.main.get().output)
    from(core.output)
    named(this, "MineUX", "MineUX Lite")
    // The lite jar is the full one minus visuals: refuse it if anything of visuals got in.
    doLast {
        ZipFile(archiveFile.get().asFile).use { zip ->
            val leaked = zip.entries().asSequence().map { it.name }.filter { it.startsWith("com/mineux/visuals/") }.toList()
            require(leaked.isEmpty()) { "the lite jar carries visuals classes: $leaked" }
        }
    }
}

tasks.assemble { dependsOn(liteJar) }

// ---- visuals (stream D): the full edition's mixin config ----------------------------------------
// mineux.visuals.mixins.json lives in the visuals source set, so only the full jar (and a full dev
// run) carries it; fabric.mod.json lists it in the same two places only. The lite jar must name
// neither the config nor any of its classes.
val visualsMixins = "\"mineux.visuals.mixins.json\""
fun withVisualsMixins(spec: CopySpec, on: Boolean) = spec.filesMatching("fabric.mod.json") {
    filter {
        val bare = it.replace(", $visualsMixins", "")
        if (on && bare.contains("\"mineux.mixins.json\"")) bare.replace("\"mineux.mixins.json\"", "\"mineux.mixins.json\", $visualsMixins") else bare
    }
}
tasks.processResources { withVisualsMixins(this, edition == "full") }
tasks.jar { withVisualsMixins(this, true) }
liteJar.configure {
    withVisualsMixins(this, false)
    doLast {
        ZipFile(archiveFile.get().asFile).use { zip ->
            require(zip.getEntry("mineux.visuals.mixins.json") == null) { "the lite jar carries mineux.visuals.mixins.json" }
            val mod = zip.getInputStream(zip.getEntry("fabric.mod.json")).bufferedReader().readText()
            require(!mod.contains("mineux.visuals")) { "the lite jar's fabric.mod.json names the visuals' mixins" }
        }
    }
}
val visualsMixinLevel = Regex("\"compatibilityLevel\"\\s*:\\s*\"JAVA_(\\d+)\"")
    .find(providers.fileContents(layout.projectDirectory.file("src/visuals/resources/mineux.visuals.mixins.json")).asText.get())
    ?.groupValues?.get(1)
require(visualsMixinLevel == libs.versions.jdk.get()) {
    "mineux.visuals.mixins.json declares compatibilityLevel JAVA_$visualsMixinLevel, but the toolchain compiles for Java ${libs.versions.jdk.get()}."
}

tasks.test {
    useJUnitPlatform {
        excludeTags("bare")
    }
}

// MineUX with nothing added (tests tagged "bare"): a JVM of its own, so no other test's
// extension is registered yet. They prove a MineUX player is offered only MineUX's commands.
val bareTest = tasks.register<Test>("bareTest") {
    description = "Runs the tests tagged bare: MineUX with no extension registered."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("bare")
    }
}
tasks.check { dependsOn(bareTest) }

// ---- Screens (stream C): monitors and windows as screens in the world, in both editions ----
// Its own source set on core, with its own mixin config (mineux.screens.mixins.json) and the
// native capture library (native/, Rust), built by cargo and packed at /mineux/native/screens.dll.
val screens = sourceSets.create("screens") {
    compileClasspath += core.output + core.compileClasspath
    runtimeClasspath += core.output + core.runtimeClasspath
}
sourceSets.main { runtimeClasspath += screens.output }
sourceSets.test {
    compileClasspath += screens.output
    runtimeClasspath += screens.output
}
loom.mods.named("mineux") { sourceSet(screens) }

val screensDll = layout.projectDirectory.file("native/target/release/screens.dll")
val cargoBuild = tasks.register("screensNative") {
    group = "build"
    description = "Builds Screens' capture library (native/, cargo --release); fails when cargo and the DLL are both missing."
    inputs.files(fileTree("native/src"), "native/Cargo.toml", "native/Cargo.lock")
    outputs.file(screensDll)
    doLast {
        val home = System.getProperty("user.home")
        val exe = if (System.getProperty("os.name").startsWith("Windows")) "cargo.exe" else "cargo"
        val cargo = listOfNotNull(System.getenv("CARGO"), "$home/.cargo/bin/$exe")
            .map(::File).firstOrNull { it.isFile }
            ?: System.getenv("PATH").orEmpty().split(File.pathSeparator).map { File(it, exe) }.firstOrNull { it.isFile }
        val dll = screensDll.asFile
        if (cargo == null) {
            require(dll.isFile) {
                "Screens needs its native library: install the MSVC Rust toolchain (cargo) or build it by hand " +
                    "with `cargo build --release` in native/ -- ${dll.path} is missing"
            }
            logger.warn("cargo not found; packing the existing ${dll.path}")
            return@doLast
        }
        val proc = ProcessBuilder(cargo.path, "build", "--release").directory(file("native")).inheritIO().start()
        require(proc.waitFor() == 0) { "cargo build --release failed in native/ (see above)" }
        require(dll.isFile) { "cargo finished but ${dll.path} is missing" }
    }
}
tasks.named<ProcessResources>("processScreensResources") {
    dependsOn(cargoBuild)
    from(screensDll) { into("mineux/native") }
}
tasks.jar { from(screens.output) }
liteJar.configure { from(screens.output) }
tasks.test {
    // -Dmineux.screens.liveRelay=wss://mineux.com/api/screens runs LiveRelayTest against the real relay.
    System.getProperty("mineux.screens.liveRelay")?.let { systemProperty("mineux.screens.liveRelay", it) }
}

// ---- the obfuscator's source rules, checked on core, visuals and screens -----------------------
// A host may build core and visuals into itself and obfuscate them (Skidfuscator), so the
// checks such an obfuscating build needs run here on every build: HoistCheck (calls the
// obfuscator would reorder past a field read, float/long unary minus), PureCheck (static
// (primitives)->int methods that are not pure), SwitchCheck (string, pattern and enum switches).
val sourceChecks = sourceSets.create("sourceChecks") {
    java.setSrcDirs(listOf("tools"))
    java.include("HoistCheck.java", "PureCheck.java", "SwitchCheck.java")
}
dependencies {
    "sourceChecksImplementation"("org.ow2.asm:asm:9.9")
    "sourceChecksImplementation"("org.ow2.asm:asm-tree:9.9")
}
for (check in listOf("HoistCheck", "PureCheck", "SwitchCheck")) {
    val task = tasks.register<JavaExec>(check.replaceFirstChar { it.lowercase() }) {
        group = "verification"
        description = "tools/$check.java over the compiled core and visuals"
        dependsOn(tasks.named("compileCoreJava"), tasks.named("compileVisualsJava"), tasks.named("compileScreensJava"))
        classpath = sourceChecks.runtimeClasspath
        mainClass = check
        val dirs = listOf(core, visuals, screens).flatMap { it.output.classesDirs.files }
        args = dirs.map { it.path }
    }
    tasks.check { dependsOn(task) }
}

// ---- private brand checks: optional, skipped when their files are absent -----------------------
// brand_words.txt feeds the rebrand tests (BrandWords); brand_check.py scans the built jars.
// Neither is in this repo: they are looked for in tools/ here, then in a private checkout beside
// this one (-PmineuxPrivate=<dir>, default ../mineux-private), and the build passes without them.
val privateTools = listOf(
    layout.projectDirectory.dir("tools").asFile,
    File(providers.gradleProperty("mineuxPrivate").map { file(it) }.getOrElse(file("../mineux-private")), "tools"),
)
fun privateTool(name: String): File = privateTools.map { File(it, name) }.firstOrNull { it.isFile } ?: File(privateTools[0], name)
val brandWords = privateTool("brand_words.txt")
tasks.withType<Test>().configureEach {
    if (brandWords.isFile) {
        val words = brandWords.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        systemProperty("mineux.brandWords", words.joinToString(","))
    }
}
val brandScript = privateTool("brand_check.py")
val brandCheck = tasks.register("brandCheck") {
    group = "verification"
    description = "tools/brand_check.py over both jars, when that script is present."
    dependsOn(tasks.jar, liteJar)
    val jars = listOf(tasks.jar.flatMap { it.archiveFile }, liteJar.flatMap { it.archiveFile })
    doLast {
        if (!brandScript.isFile) {
            logger.lifecycle("brandCheck: brand_check.py not found (tools/ or the private checkout), skipped")
            return@doLast
        }
        val exe = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        val python = listOfNotNull(System.getenv("PYTHON")).map(::File).firstOrNull { it.isFile }
            ?: listOf("python", "python3").flatMap { name ->
                System.getenv("PATH").orEmpty().split(File.pathSeparator).map { File(it, name + exe) }
            }.firstOrNull { it.isFile && it.length() > 0 }
        if (python == null) {
            logger.warn("brandCheck: no python found (set PYTHON), skipped")
            return@doLast
        }
        val args = listOf(python.path, brandScript.path) + jars.map { it.get().asFile.path }
        val proc = ProcessBuilder(args).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        val ok = proc.waitFor() == 0
        if (ok) logger.lifecycle("brandCheck: $out") else logger.error(out)
        require(ok) { "tools/brand_check.py found brand words in the jars (see above)" }
    }
}
tasks.check { dependsOn(brandCheck) }
