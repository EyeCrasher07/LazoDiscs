package com.eyecrasher.lazodiscs.config;

import com.eyecrasher.lazodiscs.LazoDiscs;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LazoDiscsConfig {
    private static final List<Value<?>> VALUES = new ArrayList<>();

    public static final IntValue DEFAULT_RANGE = intValue("playback.defaultRange", 64, 1, 512);
    public static final IntValue MAX_RANGE = intValue("playback.maxRange", 128, 1, 1024);
    public static final DoubleValue DEFAULT_VOLUME = doubleValue("playback.defaultVolume", 1.0D, 0.0D, 4.0D);
    public static final DoubleValue SOURCE_LINE_DEFAULT_VOLUME = doubleValue("playback.sourceLineDefaultVolume", 1.0D, 0.0D, 1.0D);
    public static final StringValue LANGUAGE = stringValue("language", "en_us");
    public static final BooleanValue ALLOW_HTTP = booleanValue("security.allowHttp", false);
    public static final BooleanValue ALLOW_HTTPS = booleanValue("security.allowHttps", true);
    public static final StringListValue ALLOWED_DOMAINS = stringListValue("security.allowedDomains", List.of());
    public static final BooleanValue REQUIRE_PERMISSION_FOR_BURN_COMMAND = booleanValue("security.requirePermissionForBurnCommand", true);
    public static final IntValue BURN_PERMISSION_LEVEL = intValue("security.burnPermissionLevel", 2, 0, 4);
    public static final BooleanValue REQUIRE_PERMISSION_FOR_ERASE_COMMAND = booleanValue("security.requirePermissionForEraseCommand", true);
    public static final IntValue ERASE_PERMISSION_LEVEL = intValue("security.erasePermissionLevel", 2, 0, 4);
    public static final BooleanValue REQUIRE_PERMISSION_FOR_SEARCH_COMMAND = booleanValue("security.requirePermissionForSearchCommand", true);
    public static final IntValue SEARCH_PERMISSION_LEVEL = intValue("security.searchPermissionLevel", 2, 0, 4);
    public static final BooleanValue REQUIRE_PERMISSION_FOR_PLAY = booleanValue("security.requirePermissionForPlay", false);
    public static final IntValue PLAY_PERMISSION_LEVEL = intValue("security.playPermissionLevel", 2, 0, 4);
    public static final IntValue LAVAPLAYER_LOAD_TIMEOUT_SECONDS = intValue("lavaplayer.loadTimeoutSeconds", 60, 5, 300);
    public static final IntValue POSITION_UPDATE_INTERVAL_TICKS = intValue("playback.positionUpdateIntervalTicks", 5, 1, 200);
    public static final IntValue VALIDATION_INTERVAL_TICKS = intValue("playback.validationIntervalTicks", 20, 1, 200);
    public static final BooleanValue SPOTIFY_SEARCH_VIA_YOUTUBE = booleanValue("lavaplayer.spotifySearchViaYoutube", true);
    public static final IntValue MAX_STREAMING_TRACK_LENGTH_SECONDS = intValue("lavaplayer.maxStreamingTrackLengthSeconds", 0, 0, 24 * 60 * 60);
    public static final IntValue MAX_CONCURRENT_AUDIO_LOADS = intValue("lavaplayer.maxConcurrentAudioLoads", 3, 1, 32);

    private LazoDiscsConfig() {
    }

    public static void load() {
        try {
            Files.createDirectories(configDir());
            Path file = configFile();
            if (Files.notExists(file)) {
                writeDefault(file);
            }
            read(file);
        } catch (Exception e) {
            LazoDiscs.LOGGER.warn("Could not load LazoDiscs config, using defaults: {}", e.toString());
        }
    }

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(LazoDiscs.MOD_ID);
    }

    public static Path configFile() {
        return configDir().resolve("config.toml");
    }

    private static void read(Path file) throws Exception {
        String section = "";
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    section = trimmed.substring(1, trimmed.length() - 1).trim();
                    continue;
                }

                int eq = trimmed.indexOf('=');
                if (eq <= 0) continue;
                String key = trimmed.substring(0, eq).trim();
                String path = section.isBlank() ? key : section + "." + key;
                String raw = trimmed.substring(eq + 1).trim();
                for (Value<?> value : VALUES) {
                    if (value.path.equals(path)) {
                        value.read(raw);
                        break;
                    }
                }
            }
        }
    }

    private static void writeDefault(Path file) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("language = \"en_us\"");
            writer.newLine();
            writer.newLine();
            writer.write("[playback]");
            writer.newLine();
            writer.write("defaultRange = 64");
            writer.newLine();
            writer.write("maxRange = 128");
            writer.newLine();
            writer.write("defaultVolume = 1.0");
            writer.newLine();
            writer.write("sourceLineDefaultVolume = 1.0");
            writer.newLine();
            writer.write("positionUpdateIntervalTicks = 5");
            writer.newLine();
            writer.write("validationIntervalTicks = 20");
            writer.newLine();
            writer.newLine();
            writer.write("[lavaplayer]");
            writer.newLine();
            writer.write("loadTimeoutSeconds = 60");
            writer.newLine();
            writer.write("spotifySearchViaYoutube = true");
            writer.newLine();
            writer.write("maxStreamingTrackLengthSeconds = 0");
            writer.newLine();
            writer.write("maxConcurrentAudioLoads = 3");
            writer.newLine();
            writer.newLine();
            writer.write("[security]");
            writer.newLine();
            writer.write("allowHttp = false");
            writer.newLine();
            writer.write("allowHttps = true");
            writer.newLine();
            writer.write("allowedDomains = []");
            writer.newLine();
            writer.write("requirePermissionForBurnCommand = true");
            writer.newLine();
            writer.write("burnPermissionLevel = 2");
            writer.newLine();
            writer.write("requirePermissionForEraseCommand = true");
            writer.newLine();
            writer.write("erasePermissionLevel = 2");
            writer.newLine();
            writer.write("requirePermissionForSearchCommand = true");
            writer.newLine();
            writer.write("searchPermissionLevel = 2");
            writer.newLine();
            writer.write("requirePermissionForPlay = false");
            writer.newLine();
            writer.write("playPermissionLevel = 2");
            writer.newLine();
        }
    }

    private static String stripComment(String raw) {
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == '#' && !quoted) {
                return raw.substring(0, i).trim();
            }
        }
        return raw.trim();
    }

    private static String parseString(String raw) {
        String value = stripComment(raw);
        if (value.startsWith("\"")) {
            int end = findClosingQuote(value);
            if (end > 0) return unescape(value.substring(1, end));
        }
        return value;
    }

    private static int findClosingQuote(String value) {
        boolean escaped = false;
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) escaped = false;
            else if (c == '\\') escaped = true;
            else if (c == '"') return i;
        }
        return -1;
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!escaped) {
                if (c == '\\') escaped = true;
                else out.append(c);
                continue;
            }
            switch (c) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                default -> out.append(c);
            }
            escaped = false;
        }
        if (escaped) out.append('\\');
        return out.toString();
    }

    private static List<String> parseStringList(String raw) {
        String value = stripComment(raw);
        if (!value.startsWith("[") || !value.endsWith("]")) return List.of();
        String body = value.substring(1, value.length() - 1).trim();
        if (body.isBlank()) return List.of();

        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                current.append(c);
                escaped = true;
            } else if (c == '"') {
                current.append(c);
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                addListValue(out, current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        addListValue(out, current.toString());
        return List.copyOf(out);
    }

    private static void addListValue(List<String> out, String raw) {
        String value = parseString(raw);
        if (!value.isBlank()) out.add(value);
    }

    private static IntValue intValue(String path, int defaultValue, int min, int max) {
        IntValue value = new IntValue(path, defaultValue, min, max);
        VALUES.add(value);
        return value;
    }

    private static DoubleValue doubleValue(String path, double defaultValue, double min, double max) {
        DoubleValue value = new DoubleValue(path, defaultValue, min, max);
        VALUES.add(value);
        return value;
    }

    private static BooleanValue booleanValue(String path, boolean defaultValue) {
        BooleanValue value = new BooleanValue(path, defaultValue);
        VALUES.add(value);
        return value;
    }

    private static StringValue stringValue(String path, String defaultValue) {
        StringValue value = new StringValue(path, defaultValue);
        VALUES.add(value);
        return value;
    }

    private static StringListValue stringListValue(String path, List<String> defaultValue) {
        StringListValue value = new StringListValue(path, defaultValue);
        VALUES.add(value);
        return value;
    }

    public abstract static class Value<T> {
        private final String path;
        private final T defaultValue;
        protected T value;

        protected Value(String path, T defaultValue) {
            this.path = path;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
        }

        public T get() {
            return value;
        }

        protected T defaultValue() {
            return defaultValue;
        }

        protected abstract void read(String raw);
    }

    public static final class IntValue extends Value<Integer> {
        private final int min;
        private final int max;

        private IntValue(String path, int defaultValue, int min, int max) {
            super(path, defaultValue);
            this.min = min;
            this.max = max;
        }

        @Override
        protected void read(String raw) {
            try {
                int parsed = Integer.parseInt(stripComment(raw));
                value = Math.max(min, Math.min(max, parsed));
            } catch (Exception ignored) {
                value = defaultValue();
            }
        }
    }

    public static final class DoubleValue extends Value<Double> {
        private final double min;
        private final double max;

        private DoubleValue(String path, double defaultValue, double min, double max) {
            super(path, defaultValue);
            this.min = min;
            this.max = max;
        }

        @Override
        protected void read(String raw) {
            try {
                double parsed = Double.parseDouble(stripComment(raw));
                value = Math.max(min, Math.min(max, parsed));
            } catch (Exception ignored) {
                value = defaultValue();
            }
        }
    }

    public static final class BooleanValue extends Value<Boolean> {
        private BooleanValue(String path, boolean defaultValue) {
            super(path, defaultValue);
        }

        @Override
        protected void read(String raw) {
            String parsed = stripComment(raw).toLowerCase(Locale.ROOT);
            if ("true".equals(parsed)) value = true;
            else if ("false".equals(parsed)) value = false;
            else value = defaultValue();
        }
    }

    public static final class StringValue extends Value<String> {
        private StringValue(String path, String defaultValue) {
            super(path, defaultValue);
        }

        @Override
        protected void read(String raw) {
            value = parseString(raw);
            if (value.isBlank()) value = defaultValue();
        }
    }

    public static final class StringListValue extends Value<List<String>> {
        private StringListValue(String path, List<String> defaultValue) {
            super(path, List.copyOf(defaultValue));
        }

        @Override
        protected void read(String raw) {
            value = parseStringList(raw);
        }
    }
}
