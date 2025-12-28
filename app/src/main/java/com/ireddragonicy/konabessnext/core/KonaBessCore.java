package com.ireddragonicy.konabessnext.core;

import com.ireddragonicy.konabessnext.R;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.os.Environment;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.ireddragonicy.konabessnext.utils.AssetsUtil;
import com.ireddragonicy.konabessnext.utils.RootHelper;

import android.content.SharedPreferences;

public class KonaBessCore {
    // Constants
    private static final String[] REQUIRED_BINARIES = { "dtc", "magiskboot" };
    private static final byte[] DTB_MAGIC = { (byte) 0xD0, (byte) 0x0D, (byte) 0xFE, (byte) 0xED };
    private static final int DTB_HEADER_SIZE = 8;
    private static final int BYTE_MASK = 0xFF;

    // Regex patterns for parsing (compiled once for performance)
    // Relaxed pattern: captures ID and the rest of the line content for robust
    // matching
    private static final Pattern DTB_MODEL_PATTERN = Pattern.compile("/(\\d+)\\.dts:(.*)");
    private static final Pattern DTB_FILE_PATTERN = Pattern.compile("/(\\d+)\\.dts");

    // Chip mappings - maps model string to chip type
    private static final Map<String, ChipInfo.type> CHIP_MAPPINGS = new HashMap<String, ChipInfo.type>() {
        {
            put("kona v2.1", ChipInfo.type.kona);
            put("kona v2", ChipInfo.type.kona);
            put("SM8150 v2", ChipInfo.type.msmnile);
            put("Lahaina V2.1", ChipInfo.type.lahaina);
            put("Lahaina v2.1", ChipInfo.type.lahaina);
            put("Lito", ChipInfo.type.lito_v1);
            put("Lito v2", ChipInfo.type.lito_v2);
            put("Lagoon", ChipInfo.type.lagoon);
            put("Shima", ChipInfo.type.shima);
            put("Yupik", ChipInfo.type.yupik);
            put("Waipio", ChipInfo.type.waipio_singleBin);
            put("Waipio v2", ChipInfo.type.waipio_singleBin);
            put("Cape", ChipInfo.type.cape_singleBin);
            put("Kalama v2", ChipInfo.type.kalama);
            put("KalamaP v2", ChipInfo.type.kalama);
            put("Diwali", ChipInfo.type.diwali);
            put("Ukee", ChipInfo.type.ukee_singleBin);
            put("Pineapple v2", ChipInfo.type.pineapple);
            put("PineappleP v2", ChipInfo.type.pineapple);
            put("Cliffs SoC", ChipInfo.type.cliffs_singleBin);
            put("Cliffs 7 SoC", ChipInfo.type.cliffs_7_singleBin);
            put("KalamaP SG SoC", ChipInfo.type.kalama_sg_singleBin);
            put("Sun v2 SoC", ChipInfo.type.sun);
            put("Sun Alt. Thermal Profile v2 SoC", ChipInfo.type.sun);
            put("SunP v2 SoC", ChipInfo.type.sun);
            put("SunP v2 Alt. Thermal Profile SoC", ChipInfo.type.sun);
            put("Canoe v2 SoC", ChipInfo.type.canoe);
            put("CanoeP v2 SoC", ChipInfo.type.canoe);
            put("Tuna 7 SoC", ChipInfo.type.tuna);
            put("Tuna SoC", ChipInfo.type.tuna);
        }
    };

    // Cache for system properties
    private static final Map<String, String> PROPERTY_CACHE = new ConcurrentHashMap<>();

    // DTB metadata caches (populated by single batch IPC calls)
    private static Map<Integer, String> dtbModelCache;
    private static Set<Integer> singleBinDtbs;

    // State variables
    public static String dts_path;
    public static String boot_name;
    public static List<Dtb> dtbs;

    private static int dtb_num;
    private static boolean prepared = false;
    private static DtbType dtb_type;
    private static Dtb currentDtb;
    private static String filesDir;

    public enum DtbType {
        DTB("dtb"),
        KERNEL_DTB("kernel_dtb"),
        BOTH("both");

        private final String filename;

        DtbType(String filename) {
            this.filename = filename;
        }

        public String getFilename() {
            return filename;
        }
    }

    public static class Dtb {
        public final int id;
        public final ChipInfo.type type;

        public Dtb(int id, ChipInfo.type type) {
            this.id = id;
            this.type = type;
        }
    }

    // Initialize and cleanup methods
    public static void cleanEnv(Context context) throws IOException {
        resetState();
        filesDir = context.getFilesDir().getAbsolutePath();
        RootHelper.execShForOutput("rm -rf " + filesDir + "/*");
    }

    public static void resetState() {
        prepared = false;
        dts_path = null;
        dtbs = null;
        boot_name = null;
        dtbModelCache = null;
        singleBinDtbs = null;
        PROPERTY_CACHE.clear();
    }

    public static void setupEnv(Context context) throws IOException {
        filesDir = context.getFilesDir().getAbsolutePath();

        for (String binary : REQUIRED_BINARIES) {
            File file = new File(filesDir, binary);
            AssetsUtil.exportFiles(context, binary, file.getAbsolutePath());

            if (!file.setExecutable(true) || !file.canExecute()) {
                throw new IOException("Failed to set executable: " + binary);
            }
        }
    }

    // System operations
    public static void reboot() throws IOException {
        if (!RootHelper.execAndCheck("svc power reboot")) {
            throw new IOException("Failed to reboot");
        }
    }

    public static String getCurrent(String name) {
        return PROPERTY_CACHE.computeIfAbsent(name.toLowerCase(), key -> {
            String propertyName = switch (key) {
                case "brand" -> "ro.product.brand";
                case "name" -> "ro.product.name";
                case "model" -> "ro.product.model";
                case "board" -> "ro.product.board";
                case "id" -> "ro.product.build.id";
                case "version" -> "ro.product.build.version.release";
                case "fingerprint" -> "ro.product.build.fingerprint";
                case "manufacturer" -> "ro.product.manufacturer";
                case "device" -> "ro.product.device";
                case "slot" -> "ro.boot.slot_suffix";
                default -> null;
            };
            return propertyName != null ? getSystemProperty(propertyName, "") : "";
        });
    }

    public static void setCurrentDtb(Dtb dtb) {
        currentDtb = dtb;
        if (dtb != null) {
            ChipInfo.which = dtb.type;
        }
    }

    // Boot image operations
    public static void getBootImage(Context context) throws IOException {
        try {
            getBootImageByType(context, "vendor_boot");
            boot_name = "vendor_boot";
        } catch (Exception e) {
            getBootImageByType(context, "boot");
            boot_name = "boot";
        }
    }

    private static void getBootImageByType(Context context, String type) throws IOException {
        // Check root access first with clear error message
        if (!RootHelper.isRootAvailable()) {
            throw new IOException(
                    "Root access not available. Please grant root permission in your root manager (Magisk/KernelSU/APatch) and try again.");
        }

        String bootImgPath = filesDir + "/boot.img";
        String partition = "/dev/block/bootdevice/by-name/" + type + getCurrent("slot");

        if (!RootHelper.execAndCheck(
                String.format("dd if=%s of=%s && chmod 644 %s", partition, bootImgPath, bootImgPath))) {
            throw new IOException("Failed to get " + type + " image. Please check if root permission is granted.");
        }

        File target = new File(bootImgPath);
        if (!target.exists() || !target.canRead()) {
            target.delete();
            throw new IOException("Boot image not readable");
        }
    }

    public static void writeBootImage(Context context) throws IOException {
        String newBootPath = filesDir + "/boot_new.img";
        String partition = "/dev/block/bootdevice/by-name/" + boot_name + getCurrent("slot");

        if (!RootHelper.execAndCheck(String.format("dd if=%s of=%s", newBootPath, partition))) {
            throw new IOException("Failed to write boot image");
        }
    }

    public static void backupBootImage(Context context) throws IOException {
        String source = filesDir + "/boot.img";

        // Use internal storage root directory
        File destDir = Environment.getExternalStorageDirectory();

        if (destDir != null && !destDir.exists()) {
            destDir.mkdirs();
        }

        String dest = destDir.getAbsolutePath() + "/" + boot_name + ".img";
        RootHelper.execShForOutput("cp -f " + source + " " + dest);
    }

    // ========================================================================
    // Device Detection (OPTIMIZED: NATIVE JAVA I/O)
    // ========================================================================

    private static final Pattern MODEL_PROPERTY = Pattern.compile("model\\s*=\\s*\"([^\"]+)\"");

    public static void checkDevice(Context context) throws IOException {
        setupEnv(context);
        dtbs = new ArrayList<>();

        // OPTIMIZATION: Grant read permissions once so Java can read files directly
        // This is significantly faster than executing 'grep' via shell 100+ times
        RootHelper.execShForOutput("chmod 644 " + filesDir + "/*.dts");

        for (int i = 0; i < dtb_num; i++) {
            File dtsFile = new File(filesDir, i + ".dts");
            if (!dtsFile.exists())
                continue;

            String content = readFileToString(dtsFile);
            ChipInfo.type chipType = detectChipType(content, i);

            if (chipType != ChipInfo.type.unknown) {
                dtbs.add(new Dtb(i, chipType));
            }
        }
    }

    private static ChipInfo.type detectChipType(String content, int index) {
        // Extract "model" property using Regex
        Matcher m = MODEL_PROPERTY.matcher(content);
        String modelContent = "";
        if (m.find()) {
            modelContent = m.group(1);
        }

        // Special case for OP4A79 device
        if ("OP4A79".equals(getCurrent("device")) && modelContent.contains("kona v2")) {
            return isSingleBin(content) ? ChipInfo.type.kona_singleBin : ChipInfo.type.kona;
        }

        // Match against chip mappings
        for (Map.Entry<String, ChipInfo.type> entry : CHIP_MAPPINGS.entrySet()) {
            if (modelContent.contains(entry.getKey())) {
                ChipInfo.type baseType = entry.getValue();

                // Check if it needs single bin variant
                if (baseType == ChipInfo.type.kona || baseType == ChipInfo.type.msmnile ||
                        baseType == ChipInfo.type.lahaina) {
                    return determineChipVariant(index, baseType, content);
                }

                return baseType;
            }
        }

        return ChipInfo.type.unknown;
    }

    private static boolean isSingleBin(String content) {
        return content.contains("qcom,gpu-pwrlevels {");
    }

    private static ChipInfo.type determineChipVariant(int index, ChipInfo.type regular, String content) {
        try {
            ChipInfo.type singleBin = ChipInfo.type.valueOf(regular.name() + "_singleBin");
            return isSingleBin(content) ? singleBin : regular;
        } catch (IllegalArgumentException e) {
            return regular;
        }
    }

    private static String readFileToString(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    // ========================================================================
    // DTB/DTS Conversion (OPTIMIZED: BATCHED SHELL EXECUTION)
    // ========================================================================
    // On devices with 100+ DTBs, executing shell commands in a loop causes
    // significant latency (10-50ms overhead per fork). These methods batch
    // all commands into a single shell execution for optimal performance.

    public static void bootImage2dts(Context context) throws IOException {
        unpackBootImage(context);
        dtb_num = dtbSplit(context);

        // OPTIMIZATION: Batch all DTB->DTS conversions into single shell execution
        // Instead of: for (i) { execShForOutput("./dtc ...") } which forks 100+ times
        // We now: execShForOutput("cd dir && ./dtc 0 && ./dtc 1 && ... && ./dtc N")
        StringBuilder batchCmd = new StringBuilder();
        batchCmd.append("cd ").append(filesDir);

        for (int i = 0; i < dtb_num; i++) {
            batchCmd.append(" && ./dtc -I dtb -O dts ")
                    .append(i).append(".dtb -o ").append(i).append(".dts")
                    .append(" && rm -f ").append(i).append(".dtb");
        }

        List<String> output = RootHelper.execShForOutput(batchCmd.toString());

        // Verify at least the last DTS file was created (batch succeeded)
        if (dtb_num > 0 && !new File(filesDir, (dtb_num - 1) + ".dts").exists()) {
            throw new IOException("DTB to DTS batch conversion failed: " + String.join("\n", output));
        }
    }

    private static void unpackBootImage(Context context) throws IOException {
        RootHelper.execShForOutput(
                String.format("cd %s && ./magiskboot unpack boot.img", filesDir));

        determineDtbType();
    }

    private static void determineDtbType() throws IOException {
        boolean hasKernelDtb = new File(filesDir, "kernel_dtb").exists();
        boolean hasDtb = new File(filesDir, "dtb").exists();

        if (hasKernelDtb && hasDtb) {
            dtb_type = DtbType.BOTH;
        } else if (hasKernelDtb) {
            dtb_type = DtbType.KERNEL_DTB;
        } else if (hasDtb) {
            dtb_type = DtbType.DTB;
        } else {
            throw new IOException("No DTB found in boot image");
        }
    }

    private static int dtbSplit(Context context) throws IOException {
        File dtbFile = getDtbFile();

        if (dtb_type == DtbType.BOTH) {
            new File(filesDir, "kernel_dtb").delete();
        }

        byte[] dtbBytes = Files.readAllBytes(dtbFile.toPath());
        List<Integer> offsets = findDtbOffsets(dtbBytes);

        writeDtbChunks(dtbBytes, offsets);
        dtbFile.delete();

        return offsets.size();
    }

    private static File getDtbFile() throws IOException {
        String filename = (dtb_type == DtbType.DTB || dtb_type == DtbType.BOTH)
                ? "dtb"
                : "kernel_dtb";
        return new File(filesDir, filename);
    }

    private static List<Integer> findDtbOffsets(byte[] data) {
        List<Integer> offsets = new ArrayList<>();
        int i = 0;

        while (i + DTB_HEADER_SIZE < data.length) {
            if (isDtbMagic(data, i)) {
                offsets.add(i);
                int size = readDtbSize(data, i + 4);
                i += Math.max(size, 1);
            } else {
                i++;
            }
        }

        return offsets;
    }

    private static boolean isDtbMagic(byte[] data, int offset) {
        return Arrays.equals(Arrays.copyOfRange(data, offset, offset + 4), DTB_MAGIC);
    }

    private static int readDtbSize(byte[] data, int offset) {
        return ((data[offset] & BYTE_MASK) << 24) |
                ((data[offset + 1] & BYTE_MASK) << 16) |
                ((data[offset + 2] & BYTE_MASK) << 8) |
                (data[offset + 3] & BYTE_MASK);
    }

    private static void writeDtbChunks(byte[] data, List<Integer> offsets) throws IOException {
        for (int i = 0; i < offsets.size(); i++) {
            int start = offsets.get(i);
            int end = (i + 1 < offsets.size()) ? offsets.get(i + 1) : data.length;

            Files.write(
                    Paths.get(filesDir, i + ".dtb"),
                    Arrays.copyOfRange(data, start, end));
        }
    }

    public static void dts2bootImage(Context context) throws IOException {
        // OPTIMIZATION: Batch DTS->DTB conversion, cat, copy, and repack into single
        // shell execution
        // This reduces 100+ process forks to exactly 1, eliminating shell context
        // initialization overhead
        StringBuilder batchCmd = new StringBuilder();
        batchCmd.append("cd ").append(filesDir);

        // Step 1: Convert all DTS files to DTB (batched)
        for (int i = 0; i < dtb_num; i++) {
            batchCmd.append(" && ./dtc -I dts -O dtb ")
                    .append(i).append(".dts -o ").append(i).append(".dtb");
        }

        // Step 2: Concatenate all DTB files into single output file
        String outputFilename = (dtb_type == DtbType.KERNEL_DTB) ? "kernel_dtb" : "dtb";
        batchCmd.append(" && cat");
        for (int i = 0; i < dtb_num; i++) {
            batchCmd.append(" ").append(i).append(".dtb");
        }
        batchCmd.append(" > ").append(outputFilename);

        // Step 3: Copy dtb to kernel_dtb if DtbType.BOTH
        if (dtb_type == DtbType.BOTH) {
            batchCmd.append(" && cp dtb kernel_dtb");
        }

        // Step 4: Repack boot image
        batchCmd.append(" && ./magiskboot repack boot.img boot_new.img");

        List<String> output = RootHelper.execShForOutput(batchCmd.toString());

        // Verify boot_new.img was created
        if (!new File(filesDir, "boot_new.img").exists()) {
            throw new IOException("DTS to boot image conversion failed: " + String.join("\n", output));
        }
    }

    // Utility methods
    public static void chooseTarget(Dtb dtb, Activity activity) {
        filesDir = activity.getFilesDir().getAbsolutePath();
        dts_path = filesDir + "/" + dtb.id + ".dts";
        ChipInfo.which = dtb.type;
        currentDtb = dtb;
        prepared = true;

        // Save selected chipset for auto-load on next app launch
        saveLastChipset(activity, dtb.id, dtb.type.name());
    }

    // ========================================================================
    // Chipset Preference Management (for auto-load feature)
    // ========================================================================

    private static final String PREFS_NAME = "KonaBessChipset";
    private static final String KEY_LAST_DTB_ID = "last_dtb_id";
    private static final String KEY_LAST_CHIP_TYPE = "last_chip_type";

    private static void saveLastChipset(Activity activity, int dtbId, String chipType) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_LAST_DTB_ID, dtbId)
                .putString(KEY_LAST_CHIP_TYPE, chipType)
                .apply();
    }

    public static boolean hasLastChipset(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        return prefs.contains(KEY_LAST_DTB_ID) && prefs.contains(KEY_LAST_CHIP_TYPE);
    }

    public static int getLastDtbId(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        return prefs.getInt(KEY_LAST_DTB_ID, -1);
    }

    public static String getLastChipType(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_CHIP_TYPE, null);
    }

    public static void clearLastChipset(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    // Try to restore last chipset automatically (called on app launch)
    public static boolean tryRestoreLastChipset(Activity activity) {
        if (!hasLastChipset(activity)) {
            return false;
        }

        int dtbId = getLastDtbId(activity);
        String chipTypeName = getLastChipType(activity);

        if (dtbId < 0 || chipTypeName == null) {
            return false;
        }

        try {
            ChipInfo.type chipType = ChipInfo.type.valueOf(chipTypeName);
            filesDir = activity.getFilesDir().getAbsolutePath();
            String dtsFile = filesDir + "/" + dtbId + ".dts";

            // Check if DTS file exists (device was previously prepared)
            if (new java.io.File(dtsFile).exists()) {
                dts_path = dtsFile;
                ChipInfo.which = chipType;
                currentDtb = new Dtb(dtbId, chipType);
                prepared = true;
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public static boolean isPrepared() {
        return prepared &&
                dts_path != null &&
                new File(dts_path).exists() &&
                ChipInfo.which != ChipInfo.type.unknown;
    }

    public static Dtb getCurrentDtb() {
        return currentDtb;
    }

    private static String getSystemProperty(String key, String defaultValue) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method method = clazz.getDeclaredMethod("get", String.class, String.class);
            return (String) method.invoke(null, key, defaultValue);
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    public static int getDtbIndex() {
        try {
            return Integer.parseInt(getSystemProperty("ro.boot.dtb_idx", "-1"));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
