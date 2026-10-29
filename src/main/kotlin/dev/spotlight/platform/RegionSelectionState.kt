package dev.spotlight.platform

internal data class RegionPoint(val x: Int, val y: Int, val z: Int) {
    fun coordinates() = "$x $y $z"
}

internal class RegionSelectionState(
    val blockId: String?, val mode: String, val mask: String = "replace", val replaceBlockId: String? = null,
) {
    init {
        if (replaceBlockId != null) {
            require(blockId != null && mode == "replace") { "Block filter requires fill replace mode" }
            require(replaceBlockId.matches(Regex("[a-z0-9_.-]+:[a-z0-9/._-]+"))) { "Invalid replacement block ID" }
        }
        if (blockId != null) {
            require(blockId.matches(Regex("[a-z0-9_.-]+:[a-z0-9/._-]+"))) { "Invalid block ID" }
            require(mode in setOf("replace", "destroy", "keep", "hollow", "outline")) { "Invalid fill mode" }
        } else {
            require(mask in setOf("replace", "masked")) { "Clone mask must be replace or masked" }
            require(mode in setOf("normal", "force", "move")) { "Invalid clone mode" }
        }
    }

    var first: RegionPoint? = null
        private set
    var second: RegionPoint? = null
        private set
    var destination: RegionPoint? = null
        private set

    fun select(left: Boolean, point: RegionPoint) {
        if (!left) {
            require(first != null) { "Select first corner with LMB first" }
            second = point
            destination = null
        } else if (blockId == null && second != null) {
            destination = point
        } else first = point
    }

    fun bounds(): Pair<RegionPoint, RegionPoint>? {
        val a = first ?: return null
        val b = second ?: return null
        return RegionPoint(minOf(a.x, b.x), minOf(a.y, b.y), minOf(a.z, b.z)) to
            RegionPoint(maxOf(a.x, b.x), maxOf(a.y, b.y), maxOf(a.z, b.z))
    }

    fun volume(): Long? = bounds()?.let { (a, b) ->
        Math.multiplyExact(Math.multiplyExact(b.x.toLong() - a.x + 1, b.y.toLong() - a.y + 1), b.z.toLong() - a.z + 1)
    }

    fun destinationBounds(): Pair<RegionPoint, RegionPoint>? {
        val start = destination ?: return null
        val (a, b) = bounds() ?: return null
        fun end(value: Int, min: Int, max: Int) = Math.toIntExact(value.toLong() + max.toLong() - min)
        return start to RegionPoint(end(start.x, a.x, b.x), end(start.y, a.y, b.y), end(start.z, a.z, b.z))
    }

    fun command(minY: Int, maxY: Int): String {
        val (a, b) = requireNotNull(bounds()) { "Select both source corners first" }
        fun valid(p: RegionPoint) = p.x.toLong() in -29_999_999L..29_999_999L &&
            p.z.toLong() in -29_999_999L..29_999_999L && p.y in minY..maxY
        require(valid(a) && valid(b)) { "Source outside build height or world coordinates" }
        volume() 
        if (blockId != null) return "/fill ${a.coordinates()} ${b.coordinates()} $blockId $mode" +
                    (replaceBlockId?.let { " $it" } ?: "")
        val (d, end) = requireNotNull(destinationBounds()) { "Select clone destination with LMB first" }
        require(valid(d) && valid(end)) { "Clone destination outside build height or world coordinates" }
        return "/clone ${a.coordinates()} ${b.coordinates()} ${d.coordinates()} $mask $mode"
    }
}
