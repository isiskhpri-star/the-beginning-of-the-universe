package protection

import java.io.File
import java.security.MessageDigest

/**
 * Runtime half of the meta-protection layer: verifies at startup that the
 * files which protect this application have not been altered since they were
 * sealed with `./scripts/protection-guard.sh seal`.
 *
 * The build-time half ([scripts/protection-guard.sh] plus the Protection Guard
 * workflow) blocks tampered code from being merged; this class refuses to let
 * tampered code run.
 *
 * Usage:
 * ```kotlin
 * fun main() {
 *     IntegrityGuard(File(".")).enforce()
 *     // ... start the application
 * }
 * ```
 */
class IntegrityGuard(
    private val repoRoot: File,
    private val manifestPath: String = "protection/manifest.sha256",
) {

    /** Outcome of a single protected file's check. */
    sealed class Finding(val path: String) {
        class Verified(path: String) : Finding(path)
        class Encrypted(path: String) : Finding(path)
        class Missing(path: String) : Finding(path)
        class Tampered(path: String, val expected: String, val actual: String) : Finding(path)
    }

    /** Aggregate result of a full verification pass. */
    data class Report(val findings: List<Finding>) {
        val violations: List<Finding> get() = findings.filter { it is Finding.Missing || it is Finding.Tampered }
        val isIntact: Boolean get() = violations.isEmpty()

        fun describe(): String = buildString {
            appendLine("Integrity report: ${findings.size} protected files, ${violations.size} violations")
            for (finding in findings) {
                when (finding) {
                    is Finding.Verified -> appendLine("  OK        ${finding.path}")
                    is Finding.Encrypted -> appendLine("  SEALED    ${finding.path} (encrypted at rest)")
                    is Finding.Missing -> appendLine("  MISSING   ${finding.path}")
                    is Finding.Tampered -> {
                        appendLine("  TAMPERED  ${finding.path}")
                        appendLine("            expected ${finding.expected}")
                        appendLine("            actual   ${finding.actual}")
                    }
                }
            }
        }
    }

    /** Raised when protected code has been altered, removed, or left unsealed. */
    class IntegrityViolation(message: String) : SecurityException(message) {
        constructor(report: Report) : this(report.describe())
    }

    /**
     * Verifies every entry in the manifest and returns a [Report] describing
     * each file. Tampering is reported rather than thrown — use [enforce] to
     * halt the application. A missing manifest is itself an
     * [IntegrityViolation] and is thrown.
     */
    fun verify(): Report {
        val manifest = File(repoRoot, manifestPath)
        if (!manifest.isFile) {
            throw IntegrityViolation(
                "missing integrity manifest: $manifestPath — seal it with ./scripts/protection-guard.sh seal",
            )
        }

        val findings = manifest.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val expected = line.substringBefore("  ")
                val path = line.substringAfter("  ").trim()
                val file = File(repoRoot, path)

                when {
                    !file.isFile -> Finding.Missing(path)
                    expected == ENCRYPTED_MARKER -> Finding.Encrypted(path)
                    file.isGitCryptBlob() -> Finding.Encrypted(path)
                    else -> {
                        val actual = file.sha256()
                        if (actual == expected) Finding.Verified(path) else Finding.Tampered(path, expected, actual)
                    }
                }
            }

        return Report(findings)
    }

    /**
     * Verifies the protection layer and throws [IntegrityViolation] if any
     * protected file is missing or modified. Call this before any privileged
     * subsystem starts.
     */
    fun enforce(): Report {
        val report = verify()
        if (!report.isIntact) throw IntegrityViolation(report)
        return report
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { stream ->
            val buffer = ByteArray(DIGEST_BUFFER_BYTES)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun File.isGitCryptBlob(): Boolean {
        val header = ByteArray(GITCRYPT_MAGIC.size)
        inputStream().use { stream ->
            if (stream.read(header) != header.size) return false
        }
        return header.contentEquals(GITCRYPT_MAGIC)
    }

    private companion object {
        const val ENCRYPTED_MARKER = "ENCRYPTED"
        const val DIGEST_BUFFER_BYTES = 8192
        val GITCRYPT_MAGIC = byteArrayOf(0) + "GITCRYPT".toByteArray()
    }
}
