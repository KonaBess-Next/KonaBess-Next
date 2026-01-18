package com.ireddragonicy.konabessnext.core;

import android.app.Activity;
import android.util.SparseArray; // Using SparseArray for better Android performance with int keys

import com.ireddragonicy.konabessnext.R;
import com.ireddragonicy.konabessnext.core.strategy.ChipArchitecture;
import com.ireddragonicy.konabessnext.core.strategy.MultiBinStrategy;
import com.ireddragonicy.konabessnext.core.strategy.SingleBinStrategy;

public class ChipInfo {

    // Stateless strategy instances to avoid redundant object creation
    private static final ChipArchitecture MULTI_BIN = new MultiBinStrategy();
    private static final ChipArchitecture SINGLE_BIN = new SingleBinStrategy();

    public enum Type {
        // Resource ID, maxLevels, ignoreVolt, minLevelOffset, levelConfig, strategy,
        // voltPattern
        kona(R.string.sdm865_series, 11, false, 2, LevelConfig.CONFIG_416, MULTI_BIN, "gpu-opp-table_v2"),
        kona_singleBin(R.string.sdm865_singlebin, 11, false, 2, LevelConfig.CONFIG_416, SINGLE_BIN, "gpu-opp-table_v2"),
        msmnile(R.string.sdm855_series, 11, false, 2, LevelConfig.CONFIG_416, MULTI_BIN, "gpu_opp_table_v2"),
        msmnile_singleBin(R.string.sdm855_singlebin, 11, false, 2, LevelConfig.CONFIG_416, SINGLE_BIN,
                "gpu_opp_table_v2"),
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

        public final int descriptionRes;
        public final int maxTableLevels;
        public final boolean ignoreVoltTable;
        public final int minLevelOffset;
        public final ChipArchitecture architecture;
        public final String voltTablePattern;
        private final LevelConfig levelConfig;

        Type(int descriptionRes, int maxTableLevels, boolean ignoreVoltTable,
                int minLevelOffset, LevelConfig levelConfig, ChipArchitecture architecture, String voltTablePattern) {
            this.descriptionRes = descriptionRes;
            this.maxTableLevels = maxTableLevels;
            this.ignoreVoltTable = ignoreVoltTable;
            this.minLevelOffset = minLevelOffset;
            this.levelConfig = levelConfig;
            this.architecture = architecture;
            this.voltTablePattern = voltTablePattern;
        }

        public String getDescription(Activity activity) {
            return activity.getString(this.descriptionRes);
        }

        public int[] getLevels() {
            return levelConfig.levels;
        }

        public String[] getLevelStrings() {
            return levelConfig.levelStrings;
        }

        public boolean isEquivalentTo(Type other) {
            if (other == null)
                return false;
            if (this == other)
                return true;
            return normalize(this.name()).equals(normalize(other.name()));
        }

        private String normalize(String name) {
            return name.replace("_singleBin", "").replace("lito_v2", "lito_v1");
        }
    }

    public static Type which;

    // Backward compatibility wrapper
    public static class rpmh_levels {
        public static int[] levels() {
            return which != null ? which.getLevels() : new int[0];
        }

        public static String[] level_str() {
            return which != null ? which.getLevelStrings() : new String[0];
        }
    }

    private enum LevelConfig {
        CONFIG_416(416, LevelTemplate.STANDARD),
        CONFIG_464(464, LevelTemplate.EXTENDED),
        CONFIG_480(480, LevelTemplate.FULL),
        CONFIG_480_EXT(480, LevelTemplate.FULL_EXTENDED);

        final int[] levels;
        final String[] levelStrings;

        LevelConfig(int size, LevelTemplate template) {
            this.levels = new int[size];
            this.levelStrings = new String[size];
            for (int i = 0; i < size; i++) {
                levels[i] = i + 1;
                levelStrings[i] = template.getLevelString(i, levels[i]);
            }
        }
    }

    private enum LevelTemplate {
        STANDARD {
            @Override
            String getLevelString(int index, int value) {
                return STANDARD_LABELS.get(index, String.valueOf(value));
            }
        },
        EXTENDED {
            @Override
            String getLevelString(int index, int value) {
                String label = STANDARD_LABELS.get(index);
                if (label != null)
                    return label;
                return EXTENDED_LABELS.get(index, String.valueOf(value));
            }
        },
        FULL {
            @Override
            String getLevelString(int index, int value) {
                String label = STANDARD_LABELS.get(index);
                if (label != null)
                    return label;
                label = EXTENDED_LABELS.get(index); // Note: original logic prioritized EXTENDED over FULL specific
                if (label != null)
                    return label;
                return FULL_LABELS.get(index, String.valueOf(value));
            }
        },
        FULL_EXTENDED {
            @Override
            String getLevelString(int index, int value) {
                String label = STANDARD_LABELS.get(index);
                if (label != null)
                    return label;
                label = EXTENDED_LABELS.get(index);
                if (label != null)
                    return label;
                label = FULL_LABELS.get(index);
                if (label != null)
                    return label;
                return FULL_EXTENDED_LABELS.get(index, String.valueOf(value));
            }
        };

        abstract String getLevelString(int index, int value);

        // Usage of SparseArray for better performance on Android compared to
        // HashMap<Integer, String>
        private static final SparseArray<String> STANDARD_LABELS = new SparseArray<>();
        private static final SparseArray<String> EXTENDED_LABELS = new SparseArray<>();
        private static final SparseArray<String> FULL_LABELS = new SparseArray<>();
        private static final SparseArray<String> FULL_EXTENDED_LABELS = new SparseArray<>();

        static {
            STANDARD_LABELS.put(15, "16 - RETENTION");
            STANDARD_LABELS.put(47, "48 - MIN_SVS");
            STANDARD_LABELS.put(55, "56 - LOW_SVS_D1");
            STANDARD_LABELS.put(63, "64 - LOW_SVS");
            STANDARD_LABELS.put(79, "80 - LOW_SVS_L1");
            STANDARD_LABELS.put(95, "96 - LOW_SVS_L2");
            STANDARD_LABELS.put(127, "128 - SVS");
            STANDARD_LABELS.put(143, "144 - SVS_L0");
            STANDARD_LABELS.put(191, "192 - SVS_L1");
            STANDARD_LABELS.put(223, "224 - SVS_L2");
            STANDARD_LABELS.put(255, "256 - NOM");
            STANDARD_LABELS.put(319, "320 - NOM_L1");
            STANDARD_LABELS.put(335, "336 - NOM_L2");
            STANDARD_LABELS.put(351, "352 - NOM_L3");
            STANDARD_LABELS.put(383, "384 - TURBO");
            STANDARD_LABELS.put(399, "400 - TURBO_L0");
            STANDARD_LABELS.put(415, "416 - TURBO_L1");

            EXTENDED_LABELS.put(431, "432 - TURBO_L2");
            EXTENDED_LABELS.put(447, "448 - SUPER_TURBO");
            EXTENDED_LABELS.put(463, "464 - SUPER_TURBO_NO_CPR");

            FULL_LABELS.put(51, "52 - LOW_SVS_D2");
            FULL_LABELS.put(59, "60 - LOW_SVS_D0");
            FULL_LABELS.put(71, "72 - LOW_SVS_P1");
            FULL_LABELS.put(287, "288 - NOM_L0");
            FULL_LABELS.put(431, "432 - TURBO_L2");
            FULL_LABELS.put(447, "448 - TURBO_L3");
            FULL_LABELS.put(463, "464 - SUPER_TURBO");
            FULL_LABELS.put(479, "480 - SUPER_TURBO_NO_CPR");

            FULL_EXTENDED_LABELS.put(49, "50 - LOW_SVS_D3");
            FULL_EXTENDED_LABELS.put(50, "51 - LOW_SVS_D2_5");
            FULL_EXTENDED_LABELS.put(53, "54 - LOW_SVS_D1_5");
            FULL_EXTENDED_LABELS.put(451, "452 - TURBO_L4");
        }
    }
}
