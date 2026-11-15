package dev.risenid.galaxyfirm.data.net

enum class UpdateType {
    MAJOR,
    SECURITY,
    REGULAR
}

object BuildVersion {
    private const val ORDER = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private data class Key(val bootloader: Int, val year: Int, val month: Int, val major: Int, val build: Int)

    private fun key(pda: String): Key? {
        val s = pda.trim().uppercase()
        if (s.length < 6) return null
        val tail = s.takeLast(5).map { ORDER.indexOf(it) }
        if (tail.any { it < 0 }) return null
        val month = tail[3] - ORDER.indexOf('A')
        if (month !in 0..11) return null
        return Key(tail[0], tail[2], month, tail[1], tail[4])
    }

    fun compare(a: String, b: String): Int? {
        val ka = key(a) ?: return null
        val kb = key(b) ?: return null
        var yearDiff = ka.year - kb.year
        if (yearDiff > 18) yearDiff -= 26 else if (yearDiff < -18) yearDiff += 26
        return when {
            yearDiff != 0 -> yearDiff
            ka.month != kb.month -> ka.month - kb.month
            ka.bootloader != kb.bootloader -> ka.bootloader - kb.bootloader
            ka.major != kb.major -> ka.major - kb.major
            else -> ka.build - kb.build
        }
    }

    fun isNewer(candidate: String, current: String): Boolean =
        candidate != current && (compare(candidate, current)?.let { it > 0 } ?: true)

    fun classify(new: String, old: String?, newAndroid: String?, oldAndroid: String?): UpdateType {
        val newOs = newAndroid?.trim()?.substringBefore('.')?.toIntOrNull()
        val oldOs = oldAndroid?.trim()?.substringBefore('.')?.toIntOrNull()
        if (newOs != null && oldOs != null && newOs > oldOs) return UpdateType.MAJOR
        val newKey = key(new)
        val oldKey = old?.let(::key)
        if (newKey != null && oldKey != null && newKey.major > oldKey.major) return UpdateType.MAJOR
        val clean = new.trim().uppercase()
        if (newKey != null && clean[clean.length - 6] == 'S') return UpdateType.SECURITY
        return UpdateType.REGULAR
    }
}
