package com.eyecrasher.lazodiscs.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

public final class LazoDiscsText {
    private LazoDiscsText() {
    }

    public static String sourceLineName() {
        return "Discs";
    }

    public static String nowPlaying(String title) {
        return format("Now playing: %s", title);
    }

    public static MutableComponent holdDisc() {
        return component("Hold a vanilla music disc in your main hand.");
    }

    public static MutableComponent invalidUrl(String reason) {
        return component("Invalid URL: %s", reason);
    }

    public static MutableComponent burned(String title) {
        return component("Burned LazoDisc: %s", title);
    }

    public static MutableComponent audioLoadFailed(String title, String reason) {
        return Component.literal(reason == null || reason.isBlank() ? unknown() : reason);
    }

    public static MutableComponent notLazoDisc() {
        return component("This item is not a LazoDisc.");
    }

    public static MutableComponent dataRemoved() {
        return component("LazoDisc data removed.");
    }

    public static MutableComponent stoppedAll() {
        return component("Stopped all active LazoDisc sources.");
    }

    public static MutableComponent searchPlayersOnly() {
        return component("Only players can use /lazodisc search.");
    }

    public static MutableComponent searchUsage() {
        return component("Usage: /lazodisc search \"song name or link\"");
    }

    public static MutableComponent searching(String query) {
        return component("Searching: %s", query);
    }

    public static MutableComponent searchFailed(String reason) {
        return component("Search failed: %s", reason);
    }

    public static MutableComponent noSongsFound(String query) {
        return component("No songs found for: %s", query);
    }

    public static MutableComponent searchHeader(String query) {
        return component("=== LazoDiscs Search: %s ===", query);
    }

    public static String clickToPaste(String command) {
        return format("Click to paste: %s", command);
    }

    public static MutableComponent previousPage() {
        return component("Previous page");
    }

    public static MutableComponent nextPage() {
        return component("Next page");
    }

    public static MutableComponent page(int page, int totalPages) {
        return component("  Page %d/%d  ", page, totalPages);
    }

    public static String unknown() {
        return "Unknown";
    }

    public static String urlEmpty() {
        return "URL is empty";
    }

    public static String urlInvalid() {
        return "Invalid URL";
    }

    public static String httpDisabled() {
        return "HTTP URLs are disabled";
    }

    public static String httpsDisabled() {
        return "HTTPS URLs are disabled";
    }

    public static String unsupportedScheme() {
        return "Only HTTP/HTTPS URLs or spotify: URIs are supported";
    }

    public static String domainNotAllowed() {
        return "Domain is not allowed by config";
    }

    public static String spotifyTrackOnly() {
        return "Only Spotify track links are supported for LazoDiscs. Search album/playlist tracks by song name.";
    }

    public static String spotifyInvalidTrack() {
        return "Invalid Spotify track link";
    }

    public static String spotifyDisabled() {
        return "Spotify search is disabled in config";
    }

    public static String spotifyMetadataFailed() {
        return "Could not read Spotify track metadata";
    }

    public static String audioNoMatches() {
        return "No matching audio was found";
    }

    public static String audioResolveTimedOut(int seconds) {
        return format("Audio resolve timed out after %d seconds", seconds);
    }

    public static String searchTimedOut(int seconds) {
        return format("Search timed out after %d seconds", seconds);
    }

    public static String audioDecodedZeroSamples() {
        return "Decoded audio contains no samples";
    }

    public static String audioNoFrames() {
        return "No audio frames were received from LavaPlayer for 15 seconds";
    }

    public static String trackStuck(long thresholdMs) {
        return format("Track got stuck for %d ms", thresholdMs);
    }

    public static String trackTooLong(long maxSeconds) {
        return format("Track is too long. Max: %s", formatSeconds(maxSeconds));
    }

    private static MutableComponent component(String template, Object... args) {
        return Component.literal(format(template, args));
    }

    private static String format(String template, Object... args) {
        return args.length == 0 ? template : String.format(Locale.ROOT, template, args);
    }

    private static String formatSeconds(long seconds) {
        long safeSeconds = Math.max(0L, seconds);
        long hours = safeSeconds / 3600L;
        long minutes = (safeSeconds % 3600L) / 60L;
        long remainingSeconds = safeSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainingSeconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, remainingSeconds);
    }
}
