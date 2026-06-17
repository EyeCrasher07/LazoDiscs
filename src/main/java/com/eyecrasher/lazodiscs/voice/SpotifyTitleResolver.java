package com.eyecrasher.lazodiscs.voice;

import com.eyecrasher.lazodiscs.LazoDiscs;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class SpotifyTitleResolver {
    private SpotifyTitleResolver() {
    }

    public static Optional<String> resolveTitle(String spotifyUrl) {
        return resolveMetadata(spotifyUrl).map(SpotifyMetadata::searchQuery);
    }

    public static Optional<SpotifyMetadata> resolveMetadata(String spotifyUrl) {
        Optional<SpotifyMetadata> fromPage = resolveFromSpotifyPage(spotifyUrl);
        if (fromPage.isPresent()) return fromPage;
        return resolveFromOEmbed(spotifyUrl);
    }

    private static Optional<SpotifyMetadata> resolveFromSpotifyPage(String spotifyUrl) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(spotifyUrl).toURL().openConnection();
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 LazoDiscs/1.0.0");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) return Optional.empty();

            StringBuilder html = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) html.append(line).append('\n');
            }
            return parseMetadataFromHtml(html.toString());
        } catch (Exception e) {
            LazoDiscs.LOGGER.debug("Could not resolve Spotify page metadata for '{}': {}", spotifyUrl, e.toString());
            return Optional.empty();
        }
    }

    private static Optional<SpotifyMetadata> parseMetadataFromHtml(String html) {
        String title = extractMeta(html, "og:title")
                .or(() -> extractMeta(html, "twitter:title"))
                .map(SpotifyTitleResolver::cleanPlainText)
                .orElse(null);
        String description = extractMeta(html, "og:description")
                .or(() -> extractMeta(html, "twitter:description"))
                .map(SpotifyTitleResolver::cleanPlainText)
                .orElse("");

        List<String> artists = new ArrayList<>();
        Long durationMs = null;

        String ldJson = extractScriptByType(html, "application/ld+json").orElse("");
        if (!ldJson.isBlank()) {
            String ldTitle = extractJsonStringAfter(ldJson, "\"@type\"", "\"name\"").orElse(null);
            if (ldTitle != null && !ldTitle.isBlank()) title = cleanPlainText(ldTitle);

            String artistBlock = substringAfter(ldJson, "\"byArtist\"").orElse(ldJson);
            List<String> parsedArtists = extractAllJsonStrings(artistBlock, "name", 6);
            for (String artist : parsedArtists) {
                String a = cleanPlainText(artist);
                if (!a.isBlank() && !a.equalsIgnoreCase(title) && !artists.contains(a)) artists.add(a);
            }

            durationMs = extractJsonString(ldJson, "duration")
                    .map(SpotifyTitleResolver::parseIsoDurationMillis)
                    .orElse(null);
        }

        // Spotify description often looks like:
        // "Song · Artist · 2024" or "Artist · Song · 2024" depending on region.
        if (artists.isEmpty() && !description.isBlank()) {
            parseArtistsFromDescription(description, title).forEach(a -> {
                if (!artists.contains(a)) artists.add(a);
            });
        }

        if (title == null || title.isBlank()) return Optional.empty();
        String normalizedTitle = stripSpotifyDecorations(title);
        if (artists.isEmpty()) {
            SplitTitle split = splitTitleAndArtist(normalizedTitle);
            normalizedTitle = split.title();
            artists.addAll(split.artists());
        }

        if (normalizedTitle.isBlank()) return Optional.empty();
        return Optional.of(new SpotifyMetadata(normalizedTitle, List.copyOf(artists), durationMs));
    }

    private static Optional<SpotifyMetadata> resolveFromOEmbed(String spotifyUrl) {
        try {
            String encoded = URLEncoder.encode(spotifyUrl, StandardCharsets.UTF_8);
            URI uri = URI.create("https://open.spotify.com/oembed?url=" + encoded);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "LazoDiscs/1.0.0");
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) return Optional.empty();
            StringBuilder json = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) json.append(line);
            }
            String rawTitle = extractJsonString(json.toString(), "title").orElse(null);
            if (rawTitle == null || rawTitle.isBlank()) return Optional.empty();
            SplitTitle split = splitTitleAndArtist(stripSpotifyDecorations(rawTitle));
            return Optional.of(new SpotifyMetadata(split.title(), List.copyOf(split.artists()), null));
        } catch (Exception e) {
            LazoDiscs.LOGGER.debug("Could not resolve Spotify oEmbed title for '{}': {}", spotifyUrl, e.toString());
            return Optional.empty();
        }
    }

    private static List<String> parseArtistsFromDescription(String description, String title) {
        String cleaned = cleanPlainText(description).replace("•", "·");
        String[] parts = cleaned.split("\\s*·\\s*");
        List<String> out = new ArrayList<>();
        for (String raw : parts) {
            String p = raw.trim();
            if (p.isBlank()) continue;
            String lower = p.toLowerCase(Locale.ROOT);
            if (lower.equals("song") || lower.equals("album") || lower.equals("single") || lower.equals("playlist") || lower.matches("\\d{4}")) continue;
            if (title != null && normalize(p).equals(normalize(title))) continue;
            if (p.length() > 80) continue;
            out.add(p);
            if (out.size() >= 3) break;
        }
        return out;
    }

    private static SplitTitle splitTitleAndArtist(String raw) {
        String t = cleanPlainText(raw);
        List<String> artists = new ArrayList<>();

        String[] patterns = {
                " - song and lyrics by ",
                " - song by ",
                " - single by ",
                " - album by ",
                " by "
        };
        for (String pattern : patterns) {
            int idx = t.toLowerCase(Locale.ROOT).indexOf(pattern);
            if (idx > 0) {
                String title = t.substring(0, idx).trim();
                String artistPart = t.substring(idx + pattern.length()).trim();
                addArtists(artists, artistPart);
                return new SplitTitle(title, artists);
            }
        }

        // Fallback for messy oEmbed titles like "Song Artist - Album/Playlist".
        int dash = t.lastIndexOf(" - ");
        if (dash > 0) {
            String left = t.substring(0, dash).trim();
            String right = t.substring(dash + 3).trim();
            // Only treat the right side as artist if it looks short and not like an album/playlist noise.
            if (right.length() <= 50 && !right.toLowerCase(Locale.ROOT).contains("spotify")) {
                // In many Spotify snippets the right part can be artist. If it is a playlist/album,
                // the scorer still uses the left title words and won't rely solely on it.
                addArtists(artists, right);
                return new SplitTitle(left, artists);
            }
        }

        return new SplitTitle(t, artists);
    }

    private static void addArtists(List<String> artists, String artistPart) {
        String[] pieces = artistPart.split("\\s*(,|&| and | feat\\.? | ft\\.? | x )\\s*");
        for (String piece : pieces) {
            String a = cleanPlainText(piece);
            if (!a.isBlank() && !artists.contains(a)) artists.add(a);
            if (artists.size() >= 4) break;
        }
    }

    private static String stripSpotifyDecorations(String title) {
        String t = cleanPlainText(title);
        t = t.replace(" | Spotify", "").trim();
        return t.trim();
    }

    private static Optional<String> extractMeta(String html, String property) {
        String[] needles = {
                "property=\"" + property + "\"",
                "name=\"" + property + "\"",
                "property='" + property + "'",
                "name='" + property + "'"
        };
        for (String needle : needles) {
            int pos = html.indexOf(needle);
            if (pos < 0) continue;
            int tagStart = html.lastIndexOf('<', pos);
            int tagEnd = html.indexOf('>', pos);
            if (tagStart < 0 || tagEnd < 0) continue;
            String tag = html.substring(tagStart, tagEnd + 1);
            Optional<String> content = extractAttribute(tag, "content");
            if (content.isPresent()) return content;
        }
        return Optional.empty();
    }

    private static Optional<String> extractScriptByType(String html, String type) {
        int typePos = html.indexOf("type=\"" + type + "\"");
        if (typePos < 0) typePos = html.indexOf("type='" + type + "'");
        if (typePos < 0) return Optional.empty();
        int startTagEnd = html.indexOf('>', typePos);
        if (startTagEnd < 0) return Optional.empty();
        int end = html.indexOf("</script>", startTagEnd);
        if (end < 0) return Optional.empty();
        return Optional.of(html.substring(startTagEnd + 1, end));
    }

    private static Optional<String> extractAttribute(String tag, String attr) {
        int pos = tag.indexOf(attr + "=\"");
        char quote = '"';
        if (pos < 0) {
            pos = tag.indexOf(attr + "='");
            quote = '\'';
        }
        if (pos < 0) return Optional.empty();
        int start = tag.indexOf(quote, pos);
        if (start < 0) return Optional.empty();
        int end = tag.indexOf(quote, start + 1);
        if (end < 0) return Optional.empty();
        return Optional.of(unescapeHtml(tag.substring(start + 1, end)));
    }

    private static Optional<String> substringAfter(String value, String needle) {
        int idx = value.indexOf(needle);
        if (idx < 0) return Optional.empty();
        return Optional.of(value.substring(idx + needle.length()));
    }

    private static Optional<String> extractJsonStringAfter(String json, String after, String key) {
        int start = json.indexOf(after);
        if (start < 0) return Optional.empty();
        return extractJsonString(json.substring(start), key.replace("\"", ""));
    }

    private static Optional<String> extractJsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyPos = json.indexOf(needle);
        if (keyPos < 0) return Optional.empty();
        int colon = json.indexOf(':', keyPos + needle.length());
        if (colon < 0) return Optional.empty();
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) return Optional.empty();
        return readJsonString(json, quote);
    }

    private static List<String> extractAllJsonStrings(String json, String key, int limit) {
        List<String> out = new ArrayList<>();
        String needle = "\"" + key + "\"";
        int from = 0;
        while (out.size() < limit) {
            int keyPos = json.indexOf(needle, from);
            if (keyPos < 0) break;
            int colon = json.indexOf(':', keyPos + needle.length());
            int quote = colon < 0 ? -1 : json.indexOf('"', colon + 1);
            if (quote < 0) break;
            readJsonString(json, quote).ifPresent(out::add);
            from = quote + 1;
        }
        return out;
    }

    private static Optional<String> readJsonString(String json, int openingQuote) {
        StringBuilder out = new StringBuilder();
        boolean escape = false;
        for (int i = openingQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                switch (c) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            String hex = json.substring(i + 1, i + 5);
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException ignored) {
                                out.append("\\u").append(hex);
                                i += 4;
                            }
                        }
                    }
                    default -> out.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                return Optional.of(out.toString());
            } else {
                out.append(c);
            }
        }
        return Optional.empty();
    }

    private static Long parseIsoDurationMillis(String iso) {
        try {
            // Enough for Spotify-style PT3M42S values.
            String s = iso.toUpperCase(Locale.ROOT);
            if (!s.startsWith("PT")) return null;
            s = s.substring(2);
            long totalSeconds = 0;
            StringBuilder number = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isDigit(c)) {
                    number.append(c);
                    continue;
                }
                if (number.isEmpty()) continue;
                long value = Long.parseLong(number.toString());
                number.setLength(0);
                if (c == 'H') totalSeconds += value * 3600;
                else if (c == 'M') totalSeconds += value * 60;
                else if (c == 'S') totalSeconds += value;
            }
            return totalSeconds > 0 ? totalSeconds * 1000L : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String cleanPlainText(String value) {
        return unescapeHtml(value).replaceAll("\\s+", " ").trim();
    }

    private static String unescapeHtml(String value) {
        return value.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("\\([^)]*\\)", " ")
                .replaceAll("\\[[^]]*]", " ")
                .replaceAll("[^\\p{L}\\p{Nd}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record SplitTitle(String title, List<String> artists) {
    }

    public record SpotifyMetadata(String title, List<String> artists, Long durationMs) {
        public String searchQuery() {
            StringBuilder query = new StringBuilder(title == null ? "" : title.trim());
            for (String artist : artists) {
                if (artist != null && !artist.isBlank()) query.append(' ').append(artist.trim());
            }
            return query.toString().replaceAll("\\s+", " ").trim();
        }

        public String primaryArtist() {
            return artists.isEmpty() ? "" : artists.get(0);
        }
    }
}
