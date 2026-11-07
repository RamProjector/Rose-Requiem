package com.roserequiem.app.data

internal sealed interface YaraXScanResult {
    data object Clean : YaraXScanResult
    data class ThreatsFound(val ruleNames: List<String>) : YaraXScanResult
    data class Unavailable(val reason: String) : YaraXScanResult
}

/** Thin JNI boundary for the bundled YARA-X engine and pinned ReversingLabs rules. */
internal object YaraXNativeScanner {
    private val libraryLoad = lazy { runCatching { System.loadLibrary("rose_yara_x") } }

    fun scanFile(path: String): YaraXScanResult {
        val loadResult = libraryLoad.value
        if (loadResult.isFailure) {
            return YaraXScanResult.Unavailable("The local malware-scanning engine is unavailable.")
        }

        return try {
            val response = scanNative(path)
            when {
                response == "CLEAN" -> YaraXScanResult.Clean
                response.startsWith("MATCH\n") -> {
                    val names = response.substringAfter('\n')
                        .lineSequence()
                        .filter(String::isNotBlank)
                        .take(MAX_REPORTED_RULES)
                        .toList()
                    if (names.isEmpty()) {
                        YaraXScanResult.Unavailable("The scanner returned an incomplete match result.")
                    } else {
                        YaraXScanResult.ThreatsFound(names)
                    }
                }
                response.startsWith("ERROR\n") -> {
                    YaraXScanResult.Unavailable("The file could not be checked by the local signature scanner.")
                }
                else -> YaraXScanResult.Unavailable("The local scanner returned an invalid result.")
            }
        } catch (_: LinkageError) {
            YaraXScanResult.Unavailable("The local malware-scanning engine could not be started.")
        } catch (_: Exception) {
            YaraXScanResult.Unavailable("The file could not be checked by the local signature scanner.")
        }
    }

    private external fun scanNative(path: String): String

    private const val MAX_REPORTED_RULES = 8
}
