import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class CatalogVersionsTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `loads versions from the versions section`() {
        val rootDir =
            File(tempDir.toFile(), "project").apply {
                mkdirs()
                resolve("gradle").mkdirs()
                resolve("gradle/libs.versions.toml").writeText(
                    """
                    [versions]
                    kotlin = "2.4.20-RC2"
                    netty = "4.1.137.Final"

                    [libraries]
                    kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
                    """.trimIndent(),
                )
            }

        assertEquals("2.4.20-RC2", CatalogVersions.requiredVersion("kotlin", rootDir))
        assertEquals("4.1.137.Final", CatalogVersions.requiredVersion("netty", rootDir))
    }
}
