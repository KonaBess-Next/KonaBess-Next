package com.ireddragonicy.konabessnext.model

/**
 * Predefined voltage level presets for Qualcomm GPU chipsets.
 *
 * Each preset maps a 0-based index to a human-readable label like "128 - SVS".
 * Chips reference a preset by name instead of duplicating the full map in JSON,
 * dramatically reducing definitions.json size and maintenance burden.
 *
 * Pattern: index → "${index + 1} - LEVEL_NAME"
 */
object LevelPresets {

    /** Base levels shared by most 416-levelCount chipsets (17 named levels). */
    private val BASE = mapOf(
        15 to "16 - RETENTION",
        47 to "48 - MIN_SVS",
        55 to "56 - LOW_SVS_D1",
        63 to "64 - LOW_SVS",
        79 to "80 - LOW_SVS_L1",
        95 to "96 - LOW_SVS_L2",
        127 to "128 - SVS",
        143 to "144 - SVS_L0",
        191 to "192 - SVS_L1",
        223 to "224 - SVS_L2",
        255 to "256 - NOM",
        319 to "320 - NOM_L1",
        335 to "336 - NOM_L2",
        351 to "352 - NOM_L3",
        383 to "384 - TURBO",
        399 to "400 - TURBO_L0",
        415 to "416 - TURBO_L1"
    )

    /** Upper extension: TURBO_L2 through SUPER_TURBO_NO_CPR at 480. */
    private val UPPER_480 = mapOf(
        431 to "432 - TURBO_L2",
        447 to "448 - TURBO_L3",
        463 to "464 - SUPER_TURBO",
        479 to "480 - SUPER_TURBO_NO_CPR"
    )

    /** Extra granular LOW_SVS sub-levels (D2, D0, P1) + NOM_L0. */
    private val KALAMA_EXTRA = mapOf(
        51 to "52 - LOW_SVS_D2",
        59 to "60 - LOW_SVS_D0",
        71 to "72 - LOW_SVS_P1",
        287 to "288 - NOM_L0"
    )

    /** Ultra-granular LOW_SVS sub-levels (D3, D2.5, D1.5) + TURBO_L4. */
    private val SUN_EXTRA = mapOf(
        49 to "50 - LOW_SVS_D3",
        50 to "51 - LOW_SVS_D2_5",
        53 to "54 - LOW_SVS_D1_5",
        451 to "452 - TURBO_L4"
    )

    // ── Presets ────────────────────────────────────────────────

    /**
     * Standard 416-level preset (17 named levels).
     * Used by: SD865, SD855, SD765G, SD690, SD780G, SD778G, SD8Gen1, SD8+Gen1,
     *          SD7Gen1, SD7Gen2, SD888(SingleBin), SD8sGen3, Montague, MontagueP,
     *          Parrot, Ravelin, RavelinP
     */
    val STANDARD_416: Map<Int, String> = BASE

    /**
     * Lahaina 464-level preset (20 named levels).
     * Used by: SD888 (Multi-Bin) — has TURBO_L2 + legacy SUPER_TURBO naming.
     */
    val LAHAINA_464: Map<Int, String> = BASE + mapOf(
        431 to "432 - TURBO_L2",
        447 to "448 - SUPER_TURBO",
        463 to "464 - SUPER_TURBO_NO_CPR"
    )

    /**
     * Kalama 480-level preset (25 named levels).
     * Used by: SD8Gen2, SD8Gen2 for Galaxy, SD7+Gen3, Snapdragon QCS, Tuna, SD8Gen3 for Galaxy
     */
    val KALAMA_480: Map<Int, String> = BASE + KALAMA_EXTRA + UPPER_480

    /**
     * Pineapple 480-level preset (21 named levels).
     * Used by: SD8Gen3 — MIN_SVS shifted to index 31, LOW_SVS_D1 at index 47.
     */
    val PINEAPPLE_480: Map<Int, String> = mapOf(
        15 to "16 - RETENTION",
        31 to "32 - MIN_SVS",
        47 to "48 - LOW_SVS_D1",
        63 to "64 - LOW_SVS",
        79 to "80 - LOW_SVS_L1",
        95 to "96 - LOW_SVS_L2",
        127 to "128 - SVS",
        143 to "144 - SVS_L0",
        191 to "192 - SVS_L1",
        223 to "224 - SVS_L2",
        255 to "256 - NOM",
        287 to "288 - NOM_L0",
        319 to "320 - NOM_L1",
        335 to "336 - NOM_L2",
        351 to "352 - NOM_L3",
        383 to "384 - TURBO",
        399 to "400 - TURBO_L0",
        415 to "416 - TURBO_L1"
    ) + UPPER_480

    /**
     * Sun 480-level preset (28 named levels).
     * Used by: SD8Elite, SD8EliteGen5 (Canoe) — most granulated sub-levels.
     */
    val SUN_480: Map<Int, String> = BASE + KALAMA_EXTRA + SUN_EXTRA + UPPER_480

    /**
     * Cliffs minimal preset (2 named levels).
     * Used by: SD8sGen3 — only RETENTION and NOM are labeled.
     */
    val CLIFFS_MINIMAL: Map<Int, String> = mapOf(
        15 to "16 - RETENTION",
        255 to "256 - NOM"
    )

    /**
     * Alor 480-level preset (21 named levels).
     * Used by: SD8EliteGen5 (Alor) — unique P1 at 76, NOM_L2 at 384, no NOM_L3/TURBO/TURBO_L0.
     */
    val ALOR_480: Map<Int, String> = mapOf(
        49 to "50 - LOW_SVS_D3",
        50 to "51 - LOW_SVS_D2_5",
        51 to "52 - LOW_SVS_D2",
        53 to "54 - LOW_SVS_D1_5",
        55 to "56 - LOW_SVS_D1",
        59 to "60 - LOW_SVS_D0",
        63 to "64 - LOW_SVS",
        75 to "76 - LOW_SVS_P1",
        79 to "80 - LOW_SVS_L1",
        95 to "96 - LOW_SVS_L2",
        127 to "128 - SVS",
        143 to "144 - SVS_L0",
        191 to "192 - SVS_L1",
        223 to "224 - SVS_L2",
        255 to "256 - NOM",
        319 to "320 - NOM_L1",
        383 to "384 - NOM_L2",
        415 to "416 - TURBO_L1",
        431 to "432 - TURBO_L2",
        447 to "448 - TURBO_L3",
        451 to "452 - TURBO_L4"
    )

    // ── Registry ──────────────────────────────────────────────

    private val REGISTRY: Map<String, Map<Int, String>> = mapOf(
        "standard_416" to STANDARD_416,
        "lahaina_464" to LAHAINA_464,
        "kalama_480" to KALAMA_480,
        "pineapple_480" to PINEAPPLE_480,
        "sun_480" to SUN_480,
        "cliffs_minimal" to CLIFFS_MINIMAL,
        "alor_480" to ALOR_480
    )

    /**
     * Resolve a preset name to its level map.
     * @return the preset levels, or null if the name is not recognized.
     */
    fun resolve(presetName: String): Map<Int, String>? = REGISTRY[presetName]

    /** All available preset names. */
    val availablePresets: Set<String> get() = REGISTRY.keys

    // ── Smart Inference ───────────────────────────────────────

    /**
     * Model-name patterns mapped to their preset.
     * Checked in order — first match wins. More specific patterns come first.
     */
    private val MODEL_PATTERNS: List<Pair<Regex, String>> = listOf(
        // Alor — unique layout, must match before generic "Sun/Canoe"
        Regex("""(?i)\bAlor\b""") to "alor_480",
        // Cliffs (non-7) — minimal preset with only RETENTION + NOM
        Regex("""(?i)\bCliffs\s+SoC\b""") to "cliffs_minimal",
        // Cliffs 7 — kalama-style
        Regex("""(?i)\bCliffs\s+7\b""") to "kalama_480",
        // Sun / Canoe — ultra-granulated sub-levels
        Regex("""(?i)\b(Sun|Canoe)\b""") to "sun_480",
        // Pineapple — shifted MIN_SVS
        Regex("""(?i)\bPineapple\b""") to "pineapple_480",
        // Kalama / KalamaP / Tuna — kalama-style 480
        Regex("""(?i)\b(Kalama|Tuna)\b""") to "kalama_480",
        // Lahaina — legacy super_turbo naming at 464
        Regex("""(?i)\bLahaina\b""") to "lahaina_464",
        // Known 416-level chipsets by codename
        Regex("""(?i)\b(kona|msmnile|Lito|Lagoon|Shima|Yupik|Waipio|Cape|Diwali|Ukee|Montague|Parrot|Ravelin)\b""") to "standard_416",
    )

    /**
     * Infer the best level preset for a dynamically detected chip.
     *
     * Resolution priority:
     * 1. Match model name against known Qualcomm codenames
     * 2. Fall back by levelCount (480 → kalama_480, 464 → lahaina_464, else → standard_416)
     *
     * @param detectedModel  Model string from DTS (e.g. "Qualcomm Technologies, Inc. Tuna SoC")
     * @param levelCount     The chip's levelCount (416, 464, or 480)
     * @return preset name suitable for [resolve]
     */
    fun inferPreset(detectedModel: String?, levelCount: Int = 480): String {
        // 1. Try model-name matching
        if (!detectedModel.isNullOrBlank()) {
            for ((pattern, preset) in MODEL_PATTERNS) {
                if (pattern.containsMatchIn(detectedModel)) return preset
            }
        }

        // 2. Fall back by levelCount
        return when (levelCount) {
            in 0..416 -> "standard_416"
            in 417..464 -> "lahaina_464"
            else -> "kalama_480" // 480 is the safe default — superset of standard_416
        }
    }
}
