package com.eyecrasher.lazodiscs.command;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.config.LazoDiscsConfig;
import com.eyecrasher.lazodiscs.data.CustomDiscData;
import com.eyecrasher.lazodiscs.data.DiscDataUtil;
import com.eyecrasher.lazodiscs.server.LazoDiscsPermissions;
import com.eyecrasher.lazodiscs.text.LazoDiscsText;
import com.eyecrasher.lazodiscs.voice.AudioLoadExecutor;
import com.eyecrasher.lazodiscs.voice.LavaPcmFeeder;
import com.eyecrasher.lazodiscs.voice.SpotifyTitleResolver;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class LazoDiscsCommands {
    private static final int SEARCH_MAX_RESULTS = 5;

    private LazoDiscsCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lazodisc")
                .then(Commands.literal("burn")
                        .then(Commands.argument("url", StringArgumentType.string())
                                .executes(ctx -> burn(ctx.getSource(), StringArgumentType.getString(ctx, "url"), null))
                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                        .executes(ctx -> burn(ctx.getSource(), StringArgumentType.getString(ctx, "url"), StringArgumentType.getString(ctx, "title"))))))
                .then(Commands.literal("erase")
                        .executes(ctx -> erase(ctx.getSource())))
                .then(Commands.literal("stopall")
                        .executes(ctx -> stopAll(ctx.getSource())))
                .then(Commands.literal("search")
                        .executes(ctx -> search(ctx.getSource(), ""))
                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                .executes(ctx -> search(ctx.getSource(), StringArgumentType.getString(ctx, "query")))))
        );
    }

    private static int burn(CommandSourceStack source, String rawUrl, String rawTitle) {
        if (!LazoDiscsPermissions.canBurn(source)) {
            source.sendFailure(LazoDiscsText.noPermission());
            return 0;
        }

        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(LazoDiscsText.playersOnly());
            return 0;
        }

        ItemStack stack = player.getMainHandItem();
        if (!DiscDataUtil.isMusicDisc(stack)) {
            player.sendSystemMessage(LazoDiscsText.holdDisc());
            return 0;
        }

        String url;
        try {
            url = DiscDataUtil.validateUrl(rawUrl);
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(LazoDiscsText.invalidUrl(e.getMessage()));
            return 0;
        }

        String titleHint = rawTitle == null || rawTitle.isBlank() ? null : rawTitle.trim();
        player.sendSystemMessage(LazoDiscsText.resolvingTrack().withStyle(ChatFormatting.GRAY));

        var server = player.createCommandSourceStack().getServer();
        AudioLoadExecutor.submit(() -> {
            try {
                LavaPcmFeeder.ResolvedTrack resolved = LavaPcmFeeder.resolveTrack(url, titleHint);
                String title = chooseBurnTitle(url, titleHint, resolved);
                server.execute(() -> finishBurn(player, url, title));
            } catch (Throwable t) {
                LazoDiscs.LOGGER.warn("LazoDiscs could not burn '{}': {}", url, t.toString());
                server.execute(() -> player.sendSystemMessage(LazoDiscsText.burnFailed(messageOf(t)).withStyle(ChatFormatting.RED)));
            }
        });
        return 1;
    }

    private static void finishBurn(ServerPlayer player, String url, String title) {
        if (player.isRemoved()) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!DiscDataUtil.isMusicDisc(stack)) {
            player.sendSystemMessage(LazoDiscsText.holdDisc());
            return;
        }

        CustomDiscData data = new CustomDiscData(
                url,
                title,
                DiscDataUtil.clampRange(LazoDiscsConfig.DEFAULT_RANGE.get()),
                LazoDiscsConfig.DEFAULT_VOLUME.get().floatValue(),
                UUID.randomUUID()
        );
        DiscDataUtil.write(stack, data);
        player.sendSystemMessage(LazoDiscsText.burned(title));
    }

    private static int erase(CommandSourceStack source) {
        if (!LazoDiscsPermissions.canErase(source)) {
            source.sendFailure(LazoDiscsText.noPermission());
            return 0;
        }

        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(LazoDiscsText.playersOnly());
            return 0;
        }

        ItemStack stack = player.getMainHandItem();
        if (!DiscDataUtil.hasCustomDisc(stack)) {
            player.sendSystemMessage(LazoDiscsText.notLazoDisc());
            return 0;
        }
        DiscDataUtil.clear(stack);
        player.sendSystemMessage(LazoDiscsText.dataRemoved());
        return 1;
    }

    private static int stopAll(CommandSourceStack source) {
        if (!LazoDiscsPermissions.canStopAll(source)) {
            source.sendFailure(LazoDiscsText.noPermission());
            return 0;
        }

        LazoDiscs.playback().stopAll("command");
        source.sendSuccess(LazoDiscsText::stoppedAll, true);
        return 1;
    }

    private static int search(CommandSourceStack source, String query) {
        if (!LazoDiscsPermissions.canSearch(source)) {
            source.sendFailure(LazoDiscsText.noPermission());
            return 0;
        }

        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(LazoDiscsText.playersOnly());
            return 0;
        }

        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.isBlank()) {
            player.sendSystemMessage(LazoDiscsText.searchUsage());
            return 0;
        }
        if (looksLikeLink(cleanQuery)) {
            player.sendSystemMessage(LazoDiscsText.searchNamesOnly().withStyle(ChatFormatting.RED));
            return 0;
        }
        player.sendSystemMessage(LazoDiscsText.searching(cleanQuery).withStyle(ChatFormatting.GRAY));

        var server = source.getServer();
        AudioLoadExecutor.submit(() -> {
            try {
                List<LavaPcmFeeder.SearchResult> results = LavaPcmFeeder.search(cleanQuery, SEARCH_MAX_RESULTS);
                server.execute(() -> sendSearchResults(player, cleanQuery, results));
            } catch (Throwable t) {
                LazoDiscs.LOGGER.warn("LazoDiscs search failed for '{}': {}", cleanQuery, t.toString());
                server.execute(() -> player.sendSystemMessage(LazoDiscsText.searchFailed(messageOf(t)).withStyle(ChatFormatting.RED)));
            }
        });
        return 1;
    }

    private static void sendSearchResults(ServerPlayer player, String query, List<LavaPcmFeeder.SearchResult> results) {
        if (results.isEmpty()) {
            player.sendSystemMessage(LazoDiscsText.noSongsFound(query).withStyle(ChatFormatting.RED));
            return;
        }

        player.sendSystemMessage(LazoDiscsText.searchHeader(query).withStyle(ChatFormatting.GOLD));
        int end = Math.min(results.size(), SEARCH_MAX_RESULTS);
        for (int i = 0; i < end; i++) {
            LavaPcmFeeder.SearchResult result = results.get(i);
            String title = sanitizeTitle(result.title());
            String author = sanitizeTitle(result.author());
            String burnCommand = "/lazodisc burn " + quote(result.url()) + " " + title;
            Component line = Component.literal((i + 1) + ". ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(title).withStyle(style -> style
                            .withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent.SuggestCommand(burnCommand))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(LazoDiscsText.clickToPaste(burnCommand))))))
                    .append(Component.literal(" - " + author + " " + formatDuration(result.lengthMs())).withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(line);
        }
    }

    private static boolean looksLikeLink(String value) {
        if (SpotifyTitleResolver.looksLikeSpotify(value)) return true;
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("www.")
                || lower.contains("://")
                || lower.contains("youtube.com/")
                || lower.contains("youtu.be/")
                || lower.contains("spotify.com/")
                || lower.contains("soundcloud.com/");
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String sanitizeTitle(String value) {
        if (value == null || value.isBlank()) return LazoDiscsText.unknown();
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String chooseBurnTitle(String url, String titleHint, LavaPcmFeeder.ResolvedTrack resolved) {
        if (titleHint != null && !titleHint.isBlank()) {
            return titleHint.trim();
        }
        if (resolved != null && resolved.title() != null && !resolved.title().isBlank() && !resolved.title().equalsIgnoreCase(LazoDiscsText.unknown())) {
            return sanitizeTitle(resolved.title());
        }
        if (SpotifyTitleResolver.looksLikeSpotify(url)) {
            return SpotifyTitleResolver.resolveTitle(url).orElse(url);
        }
        return url;
    }

    private static String messageOf(Throwable t) {
        if (t == null) return LazoDiscsText.unknown();
        String message = t.getMessage();
        Throwable cause = t.getCause();
        if ((message == null || message.isBlank()) && cause != null) return messageOf(cause);
        if (cause != null && message != null && message.equals(cause.toString())) return messageOf(cause);
        return message == null || message.isBlank() ? t.getClass().getSimpleName() : message;
    }

    private static String formatDuration(long lengthMs) {
        if (lengthMs <= 0) return "";
        long totalSeconds = lengthMs / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return "(" + minutes + ":" + (seconds < 10 ? "0" : "") + seconds + ")";
    }

}
