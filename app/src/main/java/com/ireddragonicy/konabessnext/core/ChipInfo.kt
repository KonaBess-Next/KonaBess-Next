package com.ireddragonicy.konabessnext.core

import android.app.Activity
import android.util.SparseArray
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.core.strategy.ChipArchitecture
import com.ireddragonicy.konabessnext.core.strategy.MultiBinStrategy
import com.ireddragonicy.konabessnext.core.strategy.SingleBinStrategy

object ChipInfo {

    // Stateless strategy instances to avoid redundant object creation
    private val MULTI_BIN: ChipArchitecture = MultiBinStrategy()
    private val SINGLE_BIN: ChipArchitecture = SingleBinStrategy()

    @JvmField
    var which: Type? = null

    enum class Type(
        @JvmField val descriptionRes: Int,
        @JvmField val maxTableLevels: Int,
        @JvmField val ignoreVoltTable: Boolean,
        @JvmField val minLevelOffset: Int,
        private val levelConfig: LevelConfig,
        @JvmField val architecture: ChipArchitecture,
        @JvmField val voltTablePattern: String?
    ) {
        // Resource ID, maxLevels, ignoreVolt, minLevelOffset, levelConfig, strategy, voltPattern
        kona(R.string.sdm865_series, 11, false, 2, LevelConfig.CONFIG_416, MULTI_BIN, "gpu-opp-table_v2"),
        kona_singleBin(R.string.sdm865_singlebin, 11, false, 2, LevelConfig.CONFIG_416, SINGLE_BIN, "gpu-opp-table_v2"),
        msmnile(R.string.sdm855_series, 11, false, 2, LevelConfig.CONFIG_416, MULTI_BIN, "gpu_opp_table_v2"),
        msmnile_singleBin(R.string.sdm855_singlebin, 11, false, 2, LevelConfig.CONFIG_416, SINGLE_BIN, "gpu_opp_table_v2"),
        lahaina(R.string.sdm888, 11, true, 1, LevelConfig.CONFIG_464, MULTI_BIN, null),
        lahaina_singleBin(R.string.sdm888_singlebin, 11, true, 1, LevelConfig.CONFIG_416, SINGLE_BIN, null),
        lito_v1(R.string.lito_v1_series, 11, false, 2, LevelConfig.CONFIG_416, MULTI_BIN, "gpu-opp-table"),
        lito_v2(R.string.lito_v2_series, 11, false, 2, LevelConfig.CONFIG_416, MULTI_BIN, "gpu-opp-table"),
        lagoon(R.string.lagoon_series, 11, false, 2, LevelConfig.CONFIG_416, MULTI_BIN, "gpu-opp-table"),
        shima(R.string.sd780g, 11, true, 1, LevelConfig.CONFIG_416, MULTI_BIN, null),
        yupik(R.string.sd778g, 11, true, 1, LevelConfig.CONFIG_416, MULTI_BIN, null),
        waipio_singleBin(R.string.sd8g1_singlebin, 16, true, 1, LevelConfig.CONFIG_416, SINGLE_BIN, null),
        cape_singleBin(R.string.sd8g1p_singlebin, 16, true, 1, LevelConfig.CONFIG_416, SINGLE_BIN, null),
        kalama(R.string.sd8g2, 16, true, 1, LevelConfig.CONFIG_480, MULTI_BIN, null),
        diwali(R.string.sd7g1, 16, true, 1, LevelConfig.CONFIG_416, MULTI_BIN, null),
        ukee_singleBin(R.string.sd7g2, 16, true, 1, LevelConfig.CONFIG_416, SINGLE_BIN, null),
        pineapple(R.string.sd8g3, 16, true, 1, LevelConfig.CONFIG_480, MULTI_BIN, null),
        cliffs_singleBin(R.string.sd8sg3, 16, true, 1, LevelConfig.CONFIG_480, SINGLE_BIN, null),
        cliffs_7_singleBin(R.string.sd7pg3, 16, true, 1, LevelConfig.CONFIG_480, SINGLE_BIN, null),
        kalama_sg_singleBin(R.string.sdg3xg2, 16, true, 1, LevelConfig.CONFIG_480, SINGLE_BIN, null),
        sun(R.string.sd8e, 16, true, 1, LevelConfig.CONFIG_480_EXT, MULTI_BIN, null),
        canoe(R.string.sd8e_gen5, 16, true, 1, LevelConfig.CONFIG_480_EXT, MULTI_BIN, null),
        tuna(R.string.sd8sg4, 16, true, 1, LevelConfig.CONFIG_480_EXT, MULTI_BIN, null),
        pineapple_sg(R.string.sdg3g3, 14, true, 1, LevelConfig.CONFIG_480, MULTI_BIN, null),
        kalamap_qcs_singleBin(R.string.sd_kalamap_qcs, 16, true, 1, LevelConfig.CONFIG_480, SINGLE_BIN, null),
        unknown(R.string.unknown, 11, false, 0, LevelConfig.CONFIG_416, MULTI_BIN, null);

        fun getDescription(activity: Activity): String {
            return activity.getString(this.descriptionRes)
        }

        fun getLevels(): IntArray {
            return levelConfig.levels
        }

        fun getLevelStrings(): Array<String> {
            return levelConfig.levelStrings
        }

        fun isEquivalentTo(other: Type?): Boolean {
            if (other == null) return false
            if (this == other) return true
            return normalize(this.name) == normalize(other.name)
        }

        private fun normalize(name: String): String {
            return name.replace("_singleBin", "").replace("lito_v2", "lito_v1")
        }
    }

    // Backward compatibility wrapper
    object rpmh_levels {
        @JvmStatic
        fun levels(): IntArray {
            return which?.getLevels() ?: IntArray(0)
        }

        @JvmStatic
        fun level_str(): Array<String> {
            return which?.getLevelStrings() ?: emptyArray()
        }
    }

    private enum class LevelConfig(size: Int, template: LevelTemplate) {
        CONFIG_416(416, LevelTemplate.STANDARD),
        CONFIG_464(464, LevelTemplate.EXTENDED),
        CONFIG_480(480, LevelTemplate.FULL),
        CONFIG_480_EXT(480, LevelTemplate.FULL_EXTENDED);

        val levels: IntArray = IntArray(size)
        val levelStrings: Array<String>

        init {
            levelStrings = Array(size) { "" }
            for (i in 0 until size) {
                levels[i] = i + 1
                levelStrings[i] = template.getLevelString(i, levels[i])
            }
        }
    }

    private enum class LevelTemplate {
        STANDARD {
            override fun getLevelString(index: Int, value: Int): String {
                return STANDARD_LABELS[index] ?: value.toString()
            }
        },
        EXTENDED {
            override fun getLevelString(index: Int, value: Int): String {
                return STANDARD_LABELS[index] ?: EXTENDED_LABELS[index] ?: value.toString()
            }
        },
        FULL {
            override fun getLevelString(index: Int, value: Int): String {
                var label = STANDARD_LABELS[index]
                if (label != null) return label
                label = EXTENDED_LABELS[index]
                if (label != null) return label
                return FULL_LABELS[index] ?: value.toString()
            }
        },
        FULL_EXTENDED {
            override fun getLevelString(index: Int, value: Int): String {
                return STANDARD_LABELS[index]
                    ?: EXTENDED_LABELS[index]
                    ?: FULL_LABELS[index]
                    ?: FULL_EXTENDED_LABELS[index]
                    ?: value.toString()
            }
        };

        abstract fun getLevelString(index: Int, value: Int): String

        companion object {
            private val STANDARD_LABELS = SparseArray<String>()
            private val EXTENDED_LABELS = SparseArray<String>()
            private val FULL_LABELS = SparseArray<String>()
            private val FULL_EXTENDED_LABELS = SparseArray<String>()

            init {
                STANDARD_LABELS.put(15, "16 - RETENTION")
                STANDARD_LABELS.put(47, "48 - MIN_SVS")
                STANDARD_LABELS.put(55, "56 - LOW_SVS_D1")
                STANDARD_LABELS.put(63, "64 - LOW_SVS")
                STANDARD_LABELS.put(79, "80 - LOW_SVS_L1")
                STANDARD_LABELS.put(95, "96 - LOW_SVS_L2")
                STANDARD_LABELS.put(127, "128 - SVS")
                STANDARD_LABELS.put(143, "144 - SVS_L0")
                STANDARD_LABELS.put(191, "192 - SVS_L1")
                STANDARD_LABELS.put(223, "224 - SVS_L2")
                STANDARD_LABELS.put(255, "256 - NOM")
                STANDARD_LABELS.put(319, "320 - NOM_L1")
                STANDARD_LABELS.put(335, "336 - NOM_L2")
                STANDARD_LABELS.put(351, "352 - NOM_L3")
                STANDARD_LABELS.put(383, "384 - TURBO")
                STANDARD_LABELS.put(399, "400 - TURBO_L0")
                STANDARD_LABELS.put(415, "416 - TURBO_L1")

                EXTENDED_LABELS.put(431, "432 - TURBO_L2")
                EXTENDED_LABELS.put(447, "448 - SUPER_TURBO")
                EXTENDED_LABELS.put(463, "464 - SUPER_TURBO_NO_CPR")

                FULL_LABELS.put(51, "52 - LOW_SVS_D2")
                FULL_LABELS.put(59, "60 - LOW_SVS_D0")
                FULL_LABELS.put(71, "72 - LOW_SVS_P1")
                FULL_LABELS.put(287, "288 - NOM_L0")
                FULL_LABELS.put(431, "432 - TURBO_L2")
                FULL_LABELS.put(447, "448 - TURBO_L3")
                FULL_LABELS.put(463, "464 - SUPER_TURBO")
                FULL_LABELS.put(479, "480 - SUPER_TURBO_NO_CPR")

                FULL_EXTENDED_LABELS.put(49, "50 - LOW_SVS_D3")
                FULL_EXTENDED_LABELS.put(50, "51 - LOW_SVS_D2_5")
                FULL_EXTENDED_LABELS.put(53, "54 - LOW_SVS_D1_5")
                FULL_EXTENDED_LABELS.put(451, "452 - TURBO_L4")
            }
        }
    }
}
