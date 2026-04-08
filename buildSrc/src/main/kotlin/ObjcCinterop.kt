import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import java.io.File

private val iosTargetPlatform = mapOf(
    "iosArm64" to ("arm64" to "iphoneos"),
    "iosSimulatorArm64" to ("arm64" to "iphonesimulator"),
    "iosX64" to ("x86_64" to "iphonesimulator"),
)

/**
 * Compiles an ObjC source file into a static library and registers Gradle tasks
 * so that the K/N compilation depends on the archive.
 *
 * Call this alongside `cinterops.create(...)` — cinterop generates Kotlin bindings
 * from the header; this function bridges the `.m` implementation.
 *
 * @param targetName K/N target name (e.g. "iosArm64")
 * @param name       source file base name (without extension)
 * @param srcDir     directory containing the `.h` and `.m` files
 * @return path to the static library archive, for use with `-include-binary`
 */
fun Project.compileObjcLibrary(
    targetName: String,
    name: String,
    srcDir: File,
): String {
    val (arch, sdk) = iosTargetPlatform[targetName]
        ?: error("Unknown iOS target: $targetName (known: ${iosTargetPlatform.keys})")

    val objcBuildDir = layout.buildDirectory.dir("objc/$targetName")
    val taskSuffix = targetName.replaceFirstChar { it.uppercase() }
    val archivePath = objcBuildDir.get().file("lib$name.a").asFile.absolutePath

    val compileObjc =
        tasks.register("compileObjc$taskSuffix", Exec::class.java) {
            val outDir = objcBuildDir.get().asFile
            inputs.files(fileTree(srcDir) { include("*.m", "*.h") })
            outputs.dir(outDir)
            doFirst { outDir.mkdirs() }
            commandLine(
                "xcrun", "--sdk", sdk,
                "clang", "-arch", arch,
                "-c", "-fobjc-arc",
                "-framework", "CoreBluetooth",
                "-I", srcDir.absolutePath,
                "-o", File(outDir, "$name.o").absolutePath,
                File(srcDir, "$name.m").absolutePath,
            )
        }

    tasks.register("archiveObjc$taskSuffix", Exec::class.java) {
        dependsOn(compileObjc)
        val outDir = objcBuildDir.get().asFile
        inputs.file(File(outDir, "$name.o"))
        outputs.file(File(outDir, "lib$name.a"))
        commandLine(
            "xcrun", "--sdk", sdk,
            "ar", "rcs",
            File(outDir, "lib$name.a").absolutePath,
            File(outDir, "$name.o").absolutePath,
        )
    }

    return archivePath
}
