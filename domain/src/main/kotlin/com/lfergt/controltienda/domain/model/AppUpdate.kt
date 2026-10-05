package com.lfergt.controltienda.domain.model

/**
 * Versión publicada "X.Y.Z" (el tag vX.Y.Z del release). [code] es el versionCode de Android:
 * Gradle lo calcula igual, por eso minor y patch van de 0 a 99.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    val code: Int get() = major * 10_000 + minor * 100 + patch

    override fun compareTo(other: AppVersion): Int = code.compareTo(other.code)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""v?(\d{1,4})\.(\d{1,2})\.(\d{1,2})""")

        /** Acepta "1.2.3" y "v1.2.3"; null si el texto no es una versión publicada. */
        fun parse(text: String): AppVersion? = PATTERN.matchEntire(text.trim())?.destructured
            ?.let { (major, minor, patch) -> AppVersion(major.toInt(), minor.toInt(), patch.toInt()) }
    }
}

/** Una versión más nueva que la instalada, lista para descargar. */
data class AppUpdate(
    val version: AppVersion,
    val notes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    /** SHA-256 que publica GitHub para el archivo; null si no lo informa. */
    val sha256: String?,
)
