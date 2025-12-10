package com.ireddragonicy.konabessnext;

import android.app.Activity;
import java.util.*;

public class ChipInfo {
    /**
     * Rich Enum representing supported Qualcomm chip types with embedded
     * properties.
     * Each enum constant contains:
     * - descriptionRes: Android string resource ID for display name
     * - maxTableLevels: Maximum GPU power levels supported
     * - ignoreVoltTable: Whether to skip voltage table editing
     * - minLevelOffset: Offset for minimum power level calculations
     * - levelConfig: Configuration for RPMH voltage levels
     */
    public enum type {
        // Resource ID, maxLevels, ignoreVolt, minLevelOffset, levelConfig
        kona(R.string.sdm865_series, 11, false, 2, LevelConfig.CONFIG_416),
        kona_singleBin(R.string.sdm865_singlebin, 11, false, 2, LevelConfig.CONFIG_416),
        msmnile(R.string.sdm855_series, 11, false, 2, LevelConfig.CONFIG_416),
        msmnile_singleBin(R.string.sdm855_singlebin, 11, false, 2, LevelConfig.CONFIG_416),
        lahaina(R.string.sdm888, 11, true, 1, LevelConfig.CONFIG_464),
        lahaina_singleBin(R.string.sdm888_singlebin, 11, true, 1, LevelConfig.CONFIG_416),
        lito_v1(R.string.lito_v1_series, 11, false, 2, LevelConfig.CONFIG_416),
        lito_v2(R.string.lito_v2_series, 11, false, 2, LevelConfig.CONFIG_416),
        lagoon(R.string.lagoon_series, 11, false, 2, LevelConfig.CONFIG_416),
        shima(R.string.sd780g, 11, true, 1, LevelConfig.CONFIG_416),
        yupik(R.string.sd778g, 11, true, 1, LevelConfig.CONFIG_416),
        waipio_singleBin(R.string.sd8g1_singlebin, 16, true, 1, LevelConfig.CONFIG_416),
        cape_singleBin(R.string.sd8g1p_singlebin, 16, true, 1, LevelConfig.CONFIG_416),
        kalama(R.string.sd8g2, 16, true, 1, LevelConfig.CONFIG_480),
        diwali(R.string.sd7g1, 16, true, 1, LevelConfig.CONFIG_416),
        ukee_singleBin(R.string.sd7g2, 16, true, 1, LevelConfig.CONFIG_416),
        pineapple(R.string.sd8g3, 16, true, 1, LevelConfig.CONFIG_480),
        cliffs_singleBin(R.string.sd8sg3, 16, true, 1, LevelConfig.CONFIG_480),
        cliffs_7_singleBin(R.string.sd7pg3, 16, true, 1, LevelConfig.CONFIG_480),
        kalama_sg_singleBin(R.string.sdg3xg2, 16, true, 1, LevelConfig.CONFIG_480),
        sun(R.string.sd8e, 16, true, 1, LevelConfig.CONFIG_480_EXT),
        canoe(R.string.sd8e_gen5, 16, true, 1, LevelConfig.CONFIG_480_EXT),
        tuna(R.string.sd8sg4, 16, true, 1, LevelConfig.CONFIG_480_EXT),
        unknown(R.string.unknown, 11, false, 0, LevelConfig.CONFIG_416);

        public final int descriptionRes;
        public final int maxTableLevels;
        public final boolean ignoreVoltTable;
        public final int minLevelOffset;
        private final LevelConfig levelConfig;

        type(int descriptionRes, int maxTableLevels, boolean ignoreVoltTable,
                int minLevelOffset, LevelConfig levelConfig) {
            this.descriptionRes = descriptionRes;
            this.maxTableLevels = maxTableLevels;
            this.ignoreVoltTable = ignoreVoltTable;
            this.minLevelOffset = minLevelOffset;
            this.levelConfig = levelConfig;
        }

        /**
         * Get localized description for this chip type.
         * 
         * @param activity Activity for resource access
         * @return Localized chip description string
         */
        public String getDescription(Activity activity) {
            return activity.getResources().getString(this.descriptionRes);
        }

        /**
         * Get RPMH voltage level values.
         * 
         * @return Array of level integers
         */
        public int[] getLevels() {
            return levelConfig.getLevels();
        }

        /**
         * Get RPMH voltage level display strings.
         * 
         * @return Array of level label strings
         */
        public String[] getLevelStrings() {
            return levelConfig.getLevelStrings();
        }

        /**
         * Check if this chip type is functionally equivalent to another.
         * Handles special case: lito_v1 and lito_v2 are considered equivalent.
         */
        public boolean isEquivalentTo(type other) {
            if (other == null) return false;
            if (this == other) return true;
            
            // Normalize both types by stripping _singleBin suffix
            String thisBase = this.name().replace("_singleBin", "");
            String otherBase = other.name().replace("_singleBin", "");
            
            // Normalize lito variants
            if (thisBase.equals("lito_v2")) thisBase = "lito_v1";
            if (otherBase.equals("lito_v2")) otherBase = "lito_v1";
            
            return thisBase.equals(otherBase);
        }
    }

    public static type which;

    // ========================================================================
    // Deprecated static methods - kept for backward compatibility
    // Use enum properties directly instead (e.g., ChipInfo.which.ignoreVoltTable)
    // ========================================================================

    /**
     * @deprecated Use {@code type.ignoreVoltTable} instead
     */
    @Deprecated
    public static boolean shouldIgnoreVoltTable(type type) {
        return type != null && type.ignoreVoltTable;
    }

    /**
     * @deprecated Use {@code type.maxTableLevels} instead
     */
    @Deprecated
    public static int getMaxTableLevels(type type) {
        return type != null ? type.maxTableLevels : 11;
    }

    /**
     * @deprecated Use {@code type.isEquivalentTo(other)} instead
     */
    @Deprecated
    public static boolean checkChipGeneral(type input) {
        return which != null && which.isEquivalentTo(input);
    }

    /**
     * @deprecated Use {@code type.getDescription(activity)} instead
     */
    @Deprecated
    public static String name2chipdesc(String name, Activity activity) {
        try {
            return type.valueOf(name).getDescription(activity);
        } catch (IllegalArgumentException e) {
            return activity.getResources().getString(R.string.unknown);
        }
    }

    /**
     * @deprecated Use {@code type.getDescription(activity)} instead
     */
    @Deprecated
    public static String name2chipdesc(type t, Activity activity) {
        return t != null ? t.getDescription(activity)
                : activity.getResources().getString(R.string.unknown);
    }

    // ========================================================================
    // RPMH Voltage Levels (retained for backward compatibility)
    // ========================================================================

    public static class rpmh_levels {
        public static int[] levels() {
            return which != null ? which.getLevels() : new int[0];
        }

        public static String[] level_str() {
            return which != null ? which.getLevelStrings() : new String[0];
        }
    }

    // ========================================================================
    // Level Configuration - Internal enum for voltage level patterns
    // ========================================================================

    private enum LevelConfig {
        CONFIG_416(416, LevelTemplate.STANDARD),
        CONFIG_464(464, LevelTemplate.EXTENDED),
        CONFIG_480(480, LevelTemplate.FULL),
        CONFIG_480_EXT(480, LevelTemplate.FULL_EXTENDED);

        private final int[] levels;
        private final String[] levelStrings;

        LevelConfig(int size, LevelTemplate template) {
            this.levels = new int[size];
            this.levelStrings = new String[size];
            for (int i = 0; i < size; i++) {
                levels[i] = i + 1;
                levelStrings[i] = template.getLevelString(i, levels[i]);
            }
        }

        int[] getLevels() {
            return levels;
        }

        String[] getLevelStrings() {
            return levelStrings;
        }
    }

    // Template for level naming patterns
    private enum LevelTemplate {
        STANDARD {
            @Override
            String getLevelString(int index, int value) {
                return STANDARD_LABELS.getOrDefault(index, String.valueOf(value));
            }
        },
        EXTENDED {
            @Override
            String getLevelString(int index, int value) {
                String label = STANDARD_LABELS.get(index);
                if (label != null)
                    return label;
                return EXTENDED_LABELS.getOrDefault(index, String.valueOf(value));
            }
        },
        FULL {
            @Override
            String getLevelString(int index, int value) {
                String label = STANDARD_LABELS.get(index);
                if (label != null)
                    return label;
                label = EXTENDED_LABELS.get(index);
                if (label != null)
                    return label;
                return FULL_LABELS.getOrDefault(index, String.valueOf(value));
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
                return FULL_EXTENDED_LABELS.getOrDefault(index, String.valueOf(value));
            }
        };

        abstract String getLevelString(int index, int value);

        // Common label mappings
        private static final Map<Integer, String> STANDARD_LABELS = new HashMap<Integer, String>() {
            {
                put(15, "16 - RETENTION");
                put(47, "48 - MIN_SVS");
                put(55, "56 - LOW_SVS_D1");
                put(63, "64 - LOW_SVS");
                put(79, "80 - LOW_SVS_L1");
                put(95, "96 - LOW_SVS_L2");
                put(127, "128 - SVS");
                put(143, "144 - SVS_L0");
                put(191, "192 - SVS_L1");
                put(223, "224 - SVS_L2");
                put(255, "256 - NOM");
                put(319, "320 - NOM_L1");
                put(335, "336 - NOM_L2");
                put(351, "352 - NOM_L3");
                put(383, "384 - TURBO");
                put(399, "400 - TURBO_L0");
                put(415, "416 - TURBO_L1");
            }
        };

        private static final Map<Integer, String> EXTENDED_LABELS = new HashMap<Integer, String>() {
            {
                put(431, "432 - TURBO_L2");
                put(447, "448 - SUPER_TURBO");
                put(463, "464 - SUPER_TURBO_NO_CPR");
            }
        };

        private static final Map<Integer, String> FULL_LABELS = new HashMap<Integer, String>() {
            {
                put(51, "52 - LOW_SVS_D2");
                put(59, "60 - LOW_SVS_D0");
                put(71, "72 - LOW_SVS_P1");
                put(287, "288 - NOM_L0");
                put(431, "432 - TURBO_L2");
                put(447, "448 - TURBO_L3");
                put(463, "464 - SUPER_TURBO");
                put(479, "480 - SUPER_TURBO_NO_CPR");
            }
        };

        private static final Map<Integer, String> FULL_EXTENDED_LABELS = new HashMap<Integer, String>() {
            {
                put(49, "50 - LOW_SVS_D3");
                put(50, "51 - LOW_SVS_D2_5");
                put(53, "54 - LOW_SVS_D1_5");
                put(451, "452 - TURBO_L4");
            }
        };
    }
}
