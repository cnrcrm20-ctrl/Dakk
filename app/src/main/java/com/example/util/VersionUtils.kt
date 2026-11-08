package com.example.util

object VersionUtils {

    /** "v2.1.0", "2.1", "2.1.0-beta" gibi değerleri sayısal parçalara ayırır. */
    fun parts(version: String): List<Int> =
        version.trim()
            .removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .split('.')
            .map { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    /** remote, local'den daha yeniyse true döner. */
    fun isNewer(remote: String, local: String): Boolean {
        if (remote.isBlank()) return false
        val r = parts(remote)
        val l = parts(local)
        val size = maxOf(r.size, l.size)
        for (i in 0 until size) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
