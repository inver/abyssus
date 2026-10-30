package net.nevinsky.abyssus.lib.core.assets.sky.clouds

data class CloudMeta(
    val technique: CloudTechnique = CloudTechnique.SHELLS,
    val low: CloudBand? = null,
    val mid: CloudBand? = null,
    val high: CloudBand? = null,
) {
    /** True when there is anything to draw: at least one band. */
    val visible: Boolean get() = low != null || mid != null || high != null

    /** The bands from the highest down, the order they are drawn in (lower bands hide higher ones). */
    fun bandsFarToNear(): List<CloudBand> {
        val res = ArrayList<CloudBand>().toMutableList()
        addIfNotNull(res, high)
        addIfNotNull(res, mid)
        addIfNotNull(res, low)
        return res
    }

    private fun addIfNotNull(list: MutableList<CloudBand>, obj: CloudBand?) {
        if (obj != null) {
            list.add(obj)
        }
    }

    /**
     * One band of clouds: [type] between [base] and [top] metres, covering [coverage] (0 to 1) of the sky with [density]
     * (0 or more), drifting at [windX] / [windZ] metres per second.
     */
    data class CloudBand(
        val type: CloudType,
        val level: CloudLevel = type.level,
        val base: Float = type.base,
        val top: Float = type.top,
        val coverage: Float = type.coverage,
        val density: Float = type.density,
        var windX: Float = type.windX,
        var windZ: Float = type.windZ,
    ) {
        val thickness: Float get() = top - base
        fun setWind(arr: Array<Float>?) {
            if (arr == null || arr.size != 2) {
                return
            }
            windX = arr[0]
            windZ = arr[1]
        }
    }
}

enum class CloudTechnique(val key: String) {
    LAYERED("layered"),
    SHELLS("shells"),
    VOLUMETRIC("volumetric"),
}

/** The three cloud bands of a sky, keyed in `meta.json` by [key], from the lowest up. */
enum class CloudLevel(val key: String, val limits: CloudBandLimits) {
    LOW("low", CloudBandLimits(300f, 2500f)),
    MID("mid", CloudBandLimits(2000f, 7000f)),
    HIGH("high", CloudBandLimits(6000f, 13000f)),
}

/** The altitudes, in metres, a band's `base` and `top` must lie within (both inclusive). */
data class CloudBandLimits(val min: Float, val max: Float) {
    operator fun contains(altitude: Float): Boolean = altitude in min..max
}

/**
 * A cloud type, valid only in its [level], with the values an omitted band field takes. [scale] is the size of its
 * features in metres, [octaves] how much fine detail its noise has, [stretch] how much longer its features are along
 * x than along z (thin cirrus streaks) and [profile] its vertical shape; [CloudField] reads them, the `meta.json` does
 * not.
 */
enum class CloudType(
    val key: String,
    val level: CloudLevel,
    val base: Float,
    val top: Float,
    val coverage: Float,
    val density: Float,
    val windX: Float,
    val windZ: Float,
    val scale: Float,
    val octaves: Int,
    val stretch: Float,
    val profile: CloudProfile,
) {
    CUMULUS("cumulus", CloudLevel.LOW, 800f, 2000f, 0.4f, 0.8f, 4f, 1f, 2400f, 4, 1f, CloudProfile.HEAPED),
    STRATUS("stratus", CloudLevel.LOW, 400f, 900f, 0.85f, 0.5f, 3f, 0.5f, 6000f, 3, 1.5f, CloudProfile.LAYERED),
    STRATOCUMULUS(
        "stratocumulus",
        CloudLevel.LOW,
        600f,
        1600f,
        0.6f,
        0.7f,
        5f,
        1.5f,
        3200f,
        4,
        1.3f,
        CloudProfile.HEAPED
    ),
    ALTOCUMULUS("altocumulus", CloudLevel.MID, 3000f, 4200f, 0.45f, 0.5f, 10f, 2f, 1600f, 4, 1f, CloudProfile.HEAPED),
    ALTOSTRATUS(
        "altostratus",
        CloudLevel.MID,
        3500f,
        5500f,
        0.75f,
        0.4f,
        12f,
        3f,
        8000f,
        3,
        1.5f,
        CloudProfile.LAYERED
    ),
    CIRRUS("cirrus", CloudLevel.HIGH, 8000f, 9500f, 0.35f, 0.15f, 25f, 5f, 6000f, 5, 4f, CloudProfile.WISPY),
    CIRROSTRATUS(
        "cirrostratus",
        CloudLevel.HIGH,
        8500f,
        10500f,
        0.6f,
        0.12f,
        22f,
        4f,
        12000f,
        3,
        2.5f,
        CloudProfile.WISPY
    ),
}

/** How a cloud type's density is spread between its band's base and top (see [CloudField.shape]). */
enum class CloudProfile {
    /** Domes: only the densest columns reach the top (cumulus). */
    HEAPED,

    /** A slab with soft edges (stratus). */
    LAYERED,

    /** A thin sheet in the middle of the band (cirrus). */
    WISPY,
}
