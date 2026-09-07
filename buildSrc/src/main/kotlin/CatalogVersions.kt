import java.io.File

object CatalogVersions {
    private val cache = mutableMapOf<File, Map<String, String>>()

    fun requiredVersion(
        key: String,
        rootDir: File,
    ): String =
        cache
            .getOrPut(rootDir) { loadVersions(rootDir) }[key]
            ?: error("Version '$key' not found in gradle/libs.versions.toml [versions]")

    private fun loadVersions(rootDir: File): Map<String, String> {
        val versions = linkedMapOf<String, String>()
        var inVersionsSection = false

        for (line in rootDir.resolve("gradle/libs.versions.toml").readLines()) {
            val trimmed = line.trim()
            when {
                trimmed == "[versions]" -> inVersionsSection = true
                inVersionsSection && trimmed.startsWith("[") -> break
                inVersionsSection && trimmed.isNotEmpty() && !trimmed.startsWith("#") -> {
                    val match = VERSION_ENTRY.find(trimmed)
                    if (match != null) {
                        versions[match.groupValues[1]] = match.groupValues[2]
                    }
                }
            }
        }

        return versions
    }

    private val VERSION_ENTRY = Regex("""^([\w-]+)\s*=\s*"([^"]+)"""")
}
