package com.ireddragonicy.konabessnext.data;

import com.ireddragonicy.konabessnext.R;
import com.ireddragonicy.konabessnext.core.ChipInfo;

import android.app.Activity;
import android.util.SparseArray;
import java.util.HashMap;
import java.util.Map;

public class KonaBessStr {

    // Cache for converters to avoid repeated lookups
    private static final Map<ChipInfo.type, ChipConverter> CHIP_CONVERTERS = new HashMap<>();

    // Initialize converters once
    static {
        CHIP_CONVERTERS.put(ChipInfo.type.kona, new KonaConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.kona_singleBin, new KonaSingleBinConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.msmnile, new MsmnileConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.msmnile_singleBin, new MsmnileSingleBinConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.lahaina, new LahainaConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.lahaina_singleBin, new LahainaSingleBinConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.lito_v1, new LitoConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.lito_v2, new LitoConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.lagoon, new LagoonConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.shima, new ShimaConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.yupik, new DefaultConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.waipio_singleBin, new WaipioSingleBinConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.cape_singleBin, new CapeSingleBinConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.kalama, new KalamaConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.diwali, new DiwaliConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.ukee_singleBin, new UkeeConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.pineapple, new PineappleConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.cliffs_singleBin, new CliffsSingleBinConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.cliffs_7_singleBin, new Cliffs7SingleBinConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.kalama_sg_singleBin, new KalamaSgSingleBinConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.sun, new DefaultConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.canoe, new CanoeConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.tuna, new TunaConverter());
        CHIP_CONVERTERS.put(ChipInfo.type.pineapple_sg, new PineappleSgConverter());
    }

    public static String convert_bins(int which, Activity activity) throws Exception {
        ChipConverter converter = CHIP_CONVERTERS.get(ChipInfo.which);
        if (converter == null) {
            throw new Exception("Unsupported chip type: " + ChipInfo.which);
        }
        return converter.convert(which, activity);
    }

    // Base interface for all converters
    private interface ChipConverter {
        String convert(int which, Activity activity);
    }

    // Abstract base class with common functionality
    private abstract static class BaseConverter implements ChipConverter {
        protected final SparseArray<Integer> binMappings = new SparseArray<>();

        @Override
        public String convert(int which, Activity activity) {
            Integer resourceId = binMappings.get(which);
            if (resourceId != null) {
                return activity.getString(resourceId);
            }
            return activity.getString(R.string.unknown_table) + which;
        }
    }

    // Specific converter implementations
    private static class KonaConverter extends BaseConverter {
        KonaConverter() {
            binMappings.put(0, R.string.sdm865);
            binMappings.put(1, R.string.sdm865p);
            binMappings.put(2, R.string.sdm865m);
            binMappings.put(3, R.string.sd870);
        }
    }

    private static class KonaSingleBinConverter extends BaseConverter {
        KonaSingleBinConverter() {
            binMappings.put(0, R.string.sdm865_singlebin);
        }
    }

    private static class MsmnileConverter extends BaseConverter {
        MsmnileConverter() {
            binMappings.put(0, R.string.sdm855);
            binMappings.put(1, R.string.sdm855p);
        }
    }

    private static class MsmnileSingleBinConverter extends BaseConverter {
        MsmnileSingleBinConverter() {
            binMappings.put(0, R.string.sdm855_singlebin);
        }
    }

    private static class LahainaConverter extends BaseConverter {
        LahainaConverter() {
            binMappings.put(0, R.string.sdm888);
            binMappings.put(3, R.string.sdm888p);
        }
    }

    private static class LahainaSingleBinConverter extends BaseConverter {
        LahainaSingleBinConverter() {
            binMappings.put(0, R.string.sdm888_singlebin);
        }
    }

    private static class LitoConverter extends BaseConverter {
        LitoConverter() {
            binMappings.put(1, R.string.sd765g);
            binMappings.put(3, R.string.sd765);
        }
    }

    private static class LagoonConverter extends BaseConverter {
        LagoonConverter() {
            binMappings.put(2, R.string.sdm750g);
        }
    }

    private static class ShimaConverter extends BaseConverter {
        ShimaConverter() {
            binMappings.put(1, R.string.sd780g);
        }
    }

    private static class WaipioSingleBinConverter extends BaseConverter {
        WaipioSingleBinConverter() {
            binMappings.put(0, R.string.sd8g1_singlebin);
        }
    }

    private static class CapeSingleBinConverter extends BaseConverter {
        CapeSingleBinConverter() {
            binMappings.put(0, R.string.sd8g1p_singlebin);
        }
    }

    private static class KalamaConverter extends BaseConverter {
        KalamaConverter() {
            binMappings.put(0, R.string.sd8g2_for_galaxy);
            binMappings.put(1, R.string.sd8g2);
        }
    }

    private static class DiwaliConverter extends BaseConverter {
        DiwaliConverter() {
            binMappings.put(3, R.string.sd7g1);
        }
    }

    private static class UkeeConverter extends BaseConverter {
        UkeeConverter() {
            binMappings.put(0, R.string.sd7g2);
        }
    }

    private static class PineappleConverter extends BaseConverter {
        PineappleConverter() {
            binMappings.put(0, R.string.sd8g3_for_galaxy);
            binMappings.put(1, R.string.sd8g3);
        }
    }

    private static class CliffsSingleBinConverter extends BaseConverter {
        CliffsSingleBinConverter() {
            binMappings.put(0, R.string.sd8sg3);
        }
    }

    private static class Cliffs7SingleBinConverter extends BaseConverter {
        Cliffs7SingleBinConverter() {
            binMappings.put(0, R.string.sd7pg3);
        }
    }

    private static class KalamaSgSingleBinConverter extends BaseConverter {
        KalamaSgSingleBinConverter() {
            binMappings.put(0, R.string.sdg3xg2);
        }
    }

    private static class CanoeConverter extends BaseConverter {
        CanoeConverter() {
            binMappings.put(1, R.string.sd8e_gen5);
        }
    }

    // Special case for Tuna with hardcoded strings
    private static class TunaConverter implements ChipConverter {
        private static final SparseArray<String> TUNA_MAPPINGS = new SparseArray<>();
        static {
            TUNA_MAPPINGS.put(0, "Speed Bin 0 (0x0)");
            TUNA_MAPPINGS.put(1, "Speed Bin 1 (0xd8)");
            TUNA_MAPPINGS.put(2, "Speed Bin 2 (0xf2)");
        }

        @Override
        public String convert(int which, Activity activity) {
            String result = TUNA_MAPPINGS.get(which);
            return result != null ? result : activity.getString(R.string.unknown_table) + which;
        }
    }

    private static class PineappleSgConverter extends BaseConverter {
        PineappleSgConverter() {
            binMappings.put(0, R.string.sdg3g3);
        }
    }

    // Default converter for chips with no specific mappings
    private static class DefaultConverter implements ChipConverter {
        @Override
        public String convert(int which, Activity activity) {
            return activity.getString(R.string.unknown_table) + which;
        }
    }

    // Constants for common strings
    private static final String QCOM_PREFIX = "qcom,";
    private static final String GPU_FREQ = "gpu-freq";
    private static final String LEVEL = "level";
    private static final String BUS = "bus";
    private static final String ACD = "acd";

    public static String convert_level_params(String input, Activity activity) {
        String processed = input.replace(QCOM_PREFIX, "");

        if (GPU_FREQ.equals(processed)) {
            return activity.getString(R.string.freq);
        }
        if (LEVEL.equals(processed)) {
            return activity.getString(R.string.volt);
        }

        return processed;
    }

    public static String help(String what, Activity activity) {
        if (what.equals(QCOM_PREFIX + GPU_FREQ)) {
            return activity.getString(ChipInfo.which.ignoreVoltTable
                    ? R.string.help_gpufreq_aio
                    : R.string.help_gpufreq);
        }
        if (what.contains(BUS)) {
            return activity.getString(R.string.help_bus);
        }
        if (what.contains(ACD)) {
            return activity.getString(R.string.help_acd);
        }
        return "";
    }

    public static String generic_help(Activity activity) {
        return activity.getString(ChipInfo.which.ignoreVoltTable
                ? R.string.help_msg_aio
                : R.string.help_msg);
    }
}
