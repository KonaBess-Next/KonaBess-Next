package com.ireddragonicy.konabessnext.repository;

import com.ireddragonicy.konabessnext.core.ChipInfo;
import com.ireddragonicy.konabessnext.core.KonaBessCore;
import com.ireddragonicy.konabessnext.core.strategy.ChipArchitecture;
import com.ireddragonicy.konabessnext.model.Bin;
import com.ireddragonicy.konabessnext.model.Level;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for GPU frequency table data operations.
 * Wraps DTS parsing logic previously in GpuTableEditor.
 * This is a data layer component in MVVM architecture.
 */
public class GpuTableRepository {

    private List<String> linesInDts;
    private List<Bin> bins;
    private int binPosition;

    public GpuTableRepository() {
        this.linesInDts = new ArrayList<>();
        this.bins = new ArrayList<>();
        this.binPosition = -1;
    }

    /**
     * Initialize by reading DTS file.
     */
    public void init() throws IOException {
        linesInDts = new ArrayList<>();
        bins = new ArrayList<>();
        binPosition = -1;

        File file = new File(KonaBessCore.dts_path);
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                linesInDts.add(line);
            }
        }
    }

    /**
     * Decode GPU power levels from DTS lines.
     */
    public void decode() throws Exception {
        ChipArchitecture arch = ChipInfo.which.architecture;
        int i = -1;
        while (++i < linesInDts.size()) {
            String thisLine = linesInDts.get(i).trim();
            if (arch.isStartLine(thisLine)) {
                if (binPosition < 0) {
                    binPosition = i;
                }
                arch.decode(linesInDts, bins, i);
                i--; // Adjust index because lines were removed
            }
        }
    }

    /**
     * Generate DTS table from current bins.
     */
    public List<String> generateTable() {
        return ChipInfo.which.architecture.generateTable(bins);
    }

    /**
     * Generate complete DTS with table inserted.
     */
    public List<String> generateFullDts() {
        List<String> result = new ArrayList<>(linesInDts);
        result.addAll(binPosition, generateTable());
        return result;
    }

    /**
     * Write DTS to file.
     */
    public void writeOut(List<String> newDts) throws IOException {
        File file = new File(KonaBessCore.dts_path);

        if (file.exists()) {
            file.setWritable(true);
            if (!file.delete()) {
                throw new IOException("Cannot delete existing file: " + file.getAbsolutePath());
            }
        }

        if (!file.createNewFile()) {
            throw new IOException("Failed to create file: " + file.getAbsolutePath());
        }

        file.setReadable(true, false);
        file.setWritable(true, false);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (String line : newDts) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    // Getters
    public List<String> getLinesInDts() {
        return linesInDts;
    }

    public List<Bin> getBins() {
        return bins;
    }

    public int getBinPosition() {
        return binPosition;
    }

    // Setters for state restoration
    public void setLinesInDts(List<String> lines) {
        this.linesInDts = lines != null ? new ArrayList<>(lines) : new ArrayList<>();
    }

    public void setBins(List<Bin> bins) {
        this.bins = new ArrayList<>();
        if (bins != null) {
            for (Bin bin : bins) {
                this.bins.add(new Bin(bin));
            }
        }
    }

    public void setBinPosition(int position) {
        this.binPosition = position;
    }
}
