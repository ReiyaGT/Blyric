package app.blyric.update

data class Semver(val major: Int, val minor: Int, val patch: Int, val pre: String?) : Comparable<Semver> {
    override fun compareTo(other: Semver): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        return when {
            pre == null && other.pre == null -> 0
            pre == null -> 1          // rilis stabil > pre-release
            other.pre == null -> -1
            else -> pre.compareTo(other.pre)
        }
    }

    companion object {
        private val regex = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+.*)?$""")

        fun parse(s: String): Semver? {
            val m = regex.matchEntire(s.trim()) ?: return null
            val (a, b, c, pre) = m.destructured
            return Semver(a.toInt(), b.toInt(), c.toInt(), pre.ifEmpty { null })
        }
    }
}
