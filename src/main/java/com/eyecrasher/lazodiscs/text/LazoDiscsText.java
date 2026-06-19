package com.eyecrasher.lazodiscs.text;

import com.eyecrasher.lazodiscs.config.LazoDiscsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

public final class LazoDiscsText {
    private LazoDiscsText() {
    }

    public static String sourceLineName() {
        return choose("Discs", "Пластинки");
    }

    public static String nowPlaying(String title) {
        return format("Now playing: %s", "Сейчас играет: %s", title);
    }

    public static MutableComponent holdDisc() {
        return component("Hold a vanilla music disc in your main hand.", "Держи ванильную пластинку в основной руке.");
    }

    public static MutableComponent invalidUrl(String reason) {
        return component("Invalid URL: %s", "Неверная ссылка: %s", reason);
    }

    public static MutableComponent burned(String title) {
        return component("Burned LazoDisc: %s", "Записан LazoDisc: %s", title);
    }

    public static MutableComponent audioLoadFailed(String title, String reason) {
        return Component.literal(reason == null || reason.isBlank() ? unknown() : reason);
    }

    public static MutableComponent notLazoDisc() {
        return component("This item is not a LazoDisc.", "Этот предмет не является LazoDisc.");
    }

    public static MutableComponent dataRemoved() {
        return component("LazoDisc data removed.", "Данные LazoDisc удалены.");
    }

    public static MutableComponent stoppedAll() {
        return component("Stopped all active LazoDisc sources.", "Все активные источники LazoDisc остановлены.");
    }

    public static MutableComponent searchPlayersOnly() {
        return component("Only players can use /lazodiscs search.", "Только игроки могут использовать /lazodiscs search.");
    }

    public static MutableComponent searchUsage() {
        return component("Usage: /lazodiscs search \"song name\"", "Использование: /lazodiscs search \"название песни\"");
    }

    public static MutableComponent searchNamesOnly() {
        return component(
                "Use /lazodiscs search only with song names. Use /lazodiscs burn for links.",
                "В /lazodiscs search вводи только названия песен. Для ссылок используй /lazodiscs burn."
        );
    }

    public static MutableComponent searching(String query) {
        return component("Searching: %s", "Ищу: %s", query);
    }

    public static MutableComponent searchFailed(String reason) {
        return component("Search failed: %s", "Поиск не удался: %s", reason);
    }

    public static MutableComponent noSongsFound(String query) {
        return component("No songs found for: %s", "Песни не найдены: %s", query);
    }

    public static MutableComponent searchHeader(String query) {
        return component("=== LazoDiscs Search: %s ===", "=== Поиск LazoDiscs: %s ===", query);
    }

    public static String clickToPaste(String command) {
        return format("Click to paste: %s", "Нажми, чтобы вставить: %s", command);
    }

    public static MutableComponent previousPage() {
        return component("Previous page", "Предыдущая страница");
    }

    public static MutableComponent nextPage() {
        return component("Next page", "Следующая страница");
    }

    public static MutableComponent page(int page, int totalPages) {
        return component("  Page %d/%d  ", "  Страница %d/%d  ", page, totalPages);
    }

    public static String unknown() {
        return choose("Unknown", "Неизвестно");
    }

    public static String urlEmpty() {
        return choose("URL is empty", "Ссылка пустая");
    }

    public static String urlInvalid() {
        return choose("Invalid URL", "Неверная ссылка");
    }

    public static String httpDisabled() {
        return choose("HTTP URLs are disabled", "HTTP-ссылки отключены");
    }

    public static String httpsDisabled() {
        return choose("HTTPS URLs are disabled", "HTTPS-ссылки отключены");
    }

    public static String unsupportedScheme() {
        return choose(
                "Only HTTP/HTTPS URLs or spotify: URIs are supported",
                "Поддерживаются только HTTP/HTTPS-ссылки или spotify: URI"
        );
    }

    public static String domainNotAllowed() {
        return choose("Domain is not allowed by config", "Домен запрещен в конфиге");
    }

    public static String spotifyTrackOnly() {
        return choose(
                "Only Spotify track links are supported for LazoDiscs. Search album/playlist tracks by song name.",
                "Для LazoDiscs поддерживаются только ссылки на Spotify-треки. Треки из альбомов/плейлистов ищи по названию."
        );
    }

    public static String spotifyInvalidTrack() {
        return choose("Invalid Spotify track link", "Неверная ссылка на Spotify-трек");
    }

    public static String spotifyDisabled() {
        return choose("Spotify search is disabled in config", "Поиск Spotify отключен в конфиге");
    }

    public static String audioNoMatches() {
        return choose("No matching audio was found", "Подходящее аудио не найдено");
    }

    public static String audioResolveTimedOut(int seconds) {
        return format("Audio resolve timed out after %d seconds", "Поиск аудио превысил лимит %d сек.", seconds);
    }

    public static String searchTimedOut(int seconds) {
        return format("Search timed out after %d seconds", "Поиск превысил лимит %d сек.", seconds);
    }

    public static String audioDecodedZeroSamples() {
        return choose("Decoded audio contains no samples", "В аудио не найдено звуковых сэмплов");
    }

    public static String audioNoFrames() {
        return choose(
                "No audio frames were received from LavaPlayer for 15 seconds",
                "LavaPlayer не отдавал аудиофреймы 15 секунд"
        );
    }

    public static String trackStuck(long thresholdMs) {
        return format("Track got stuck for %d ms", "Трек завис на %d мс", thresholdMs);
    }

    public static String trackTooLong(long maxSeconds) {
        return format(
                "Track is too long. Max: %s",
                "Трек слишком длинный. Максимум: %s",
                formatSeconds(maxSeconds)
        );
    }

    public static String language() {
        String configured = LazoDiscsConfig.LANGUAGE.get();
        if (configured == null || configured.isBlank()) return "en_us";
        String normalized = configured.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("ru") || normalized.equals("ru_ru") || normalized.equals("russian")) return "ru_ru";
        return "en_us";
    }

    private static MutableComponent component(String en, String ru, Object... args) {
        return Component.literal(format(en, ru, args));
    }

    private static String format(String en, String ru, Object... args) {
        String template = choose(en, ru);
        return args.length == 0 ? template : String.format(Locale.ROOT, template, args);
    }

    private static String choose(String en, String ru) {
        return language().equals("ru_ru") ? ru : en;
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
