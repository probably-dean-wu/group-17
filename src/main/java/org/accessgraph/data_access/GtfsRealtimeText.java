package org.accessgraph.data_access;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the text format of a GTFS-Realtime feed into nested blocks. It supports the layout the TTC feed
 * uses: one field per line, written either as <code>name: value</code> or as a named block in braces.
 * Reading the text format keeps the application free of a protobuf dependency.
 */
final class GtfsRealtimeText {
    private GtfsRealtimeText() { }

    /** The fields between one pair of braces, or of the whole feed, in the order they appear. */
    static final class Block {
        private final Map<String, List<String>> values = new LinkedHashMap<>();
        private final Map<String, List<Block>> blocks = new LinkedHashMap<>();

        /** Every value written for the field, without quotes. */
        List<String> values(String name) {
            return values.getOrDefault(name, List.of());
        }

        /** The first value written for the field, or null when the field is absent. */
        String value(String name) {
            List<String> found = values(name);
            return found.isEmpty() ? null : found.get(0);
        }

        /** Every nested block with the given name. */
        List<Block> blocks(String name) {
            return blocks.getOrDefault(name, List.of());
        }
    }

    static Block parse(String text) throws IOException {
        return read(text.lines().iterator(), false);
    }

    private static Block read(Iterator<String> lines, boolean nested) throws IOException {
        Block block = new Block();
        while (lines.hasNext()) {
            String line = lines.next().trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equals("}")) {
                if (!nested) {
                    throw new IOException("The alert feed closes a block that was never opened.");
                }
                return block;
            }
            if (line.endsWith("{") && line.indexOf('"') < 0) {
                String name = line.substring(0, line.length() - 1).replace(":", "").trim();
                block.blocks.computeIfAbsent(name, key -> new ArrayList<>()).add(read(lines, true));
            } else {
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    throw new IOException("The alert feed has a line that is not a field.");
                }
                block.values.computeIfAbsent(line.substring(0, colon).trim(), key -> new ArrayList<>())
                        .add(unquote(line.substring(colon + 1).trim()));
            }
        }
        if (nested) {
            throw new IOException("The alert feed ends inside a block.");
        }
        return block;
    }

    /** Removes the quotes around a string and resolves its escapes. Numbers and names are returned as they are. */
    private static String unquote(String value) throws IOException {
        if (!value.startsWith("\"")) {
            return value;
        }
        if (value.length() < 2 || !value.endsWith("\"")) {
            throw new IOException("The alert feed has a string that is not closed.");
        }
        String body = value.substring(1, value.length() - 1);
        // Octal escapes spell out the bytes of UTF-8 characters, so the text is rebuilt as bytes.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int index = 0;
        while (index < body.length()) {
            if (body.charAt(index) != '\\' || index == body.length() - 1) {
                int next = body.indexOf('\\', index + 1);
                int end = next < 0 ? body.length() : next;
                bytes.writeBytes(body.substring(index, end).getBytes(StandardCharsets.UTF_8));
                index = end;
                continue;
            }
            int digits = 0;
            while (digits < 3 && index + 1 + digits < body.length()
                    && Character.digit(body.charAt(index + 1 + digits), 8) >= 0) {
                digits++;
            }
            if (digits > 0) {
                bytes.write(Integer.parseInt(body.substring(index + 1, index + 1 + digits), 8));
                index += 1 + digits;
            } else {
                bytes.writeBytes(unescaped(body.charAt(index + 1)).getBytes(StandardCharsets.UTF_8));
                index += 2;
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static String unescaped(char code) {
        switch (code) {
            case 'n':
                return "\n";
            case 't':
                return "\t";
            case 'r':
                return "\r";
            default:
                // A quote or a backslash stands for itself.
                return String.valueOf(code);
        }
    }
}
