package com.ireddragonicy.konabessnext.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralized File I/O utilities to replace scattered logic in Editors and Core.
 */
public class FileUtil {

    public static List<String> readLines(String path) throws IOException {
        List<String> lines = new ArrayList<>();
        File file = new File(path);
        if (!file.exists()) return lines;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    public static void writeLines(String path, List<String> lines) throws IOException {
        File file = new File(path);
        prepareFileForWrite(file);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    public static void writeString(String path, String content) throws IOException {
        File file = new File(path);
        prepareFileForWrite(file);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(content);
        }
    }

    private static void prepareFileForWrite(File file) throws IOException {
        if (file.exists()) {
            if (!file.delete()) {
                file.setWritable(true);
                if (!file.delete()) {
                    // Try best effort
                }
            }
        }

        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("Failed to create file: " + file.getAbsolutePath());
        }

        file.setReadable(true, false);
        file.setWritable(true, false);
    }

    public static void copyFile(File source, File dest) throws IOException {
        try (java.io.InputStream is = new java.io.FileInputStream(source);
             java.io.OutputStream os = new java.io.FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        }
    }
}
