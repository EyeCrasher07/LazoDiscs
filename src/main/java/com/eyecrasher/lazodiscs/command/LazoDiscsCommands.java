package com.eyecrasher.lazodiscs.command;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.config.LazoDiscsConfig;
import com.eyecrasher.lazodiscs.data.CustomDiscData;
import com.eyecrasher.lazodiscs.data.DiscDataUtil;
import com.eyecrasher.lazodiscs.voice.AudioCache;
import com.eyecrasher.lazodiscs.voice.AudioLoadExecutor;
import com.eyecrasher.lazodiscs.voice.LavaPcmFeeder;
import com.eyecrasher.lazodiscs.voice.SpotifyTitleResolver;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class LazoDiscsCommands {
    private static final int SEARCH_PAGE_SIZE = 5;
    private static final int SEARCH_MAX_RESULTS = 20;

    private LazoDiscsCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lazodiscs")
                .then(Commands.literal("burn")
                        .requires(LazoDiscsCommands::canBurn)
                        .then(Commands.argument("url", StringArgumentType.string())
                                .executes(ctx -> burn(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "url"), null))
                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                        .executes(ctx -> burn(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "url"), StringArgumentType.getString(ctx, "title"))))))
                .then(Commands.literal("clear")
                        .requires(LazoDiscsCommands::canBurn)
                        .executes(ctx -> clear(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("search")
                        .requires(LazoDiscsCommands::canBurn)
                        .then(Commands.argument("query", StringArgumentType.string())
                                .executes(ctx -> search(ctx.getSource(), StringArgumentType.getString(ctx, "query"), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1, 20))
                                        .executes(ctx -> search(ctx.getSource(), StringArgumentType.getString(ctx, "query"), IntegerArgumentType.getInteger(ctx, "page"))))))
        );
    }

    private static boolean canBurn(CommandSourceStack source) {
        return !LazoDiscsConfig.REQUIRE_PERMISSION_FOR_BURN_COMMAND.get() || source.hasPermission(LazoDiscsConfig.BURN_PERMISSION_LEVEL.get());
    }

    private static int burn(ServerPlayer player, String rawUrl, String rawTitle) {
        ItemStack stack = player.getMainHandItem();
        if (!DiscDataUtil.isMusicDisc(stack)) {
            player.sendSystemMessage(Component.literal("Hold a vanilla music disc in your main hand."));
            return 0;
        }

        String url;
        try {
            url = DiscDataUtil.validateUrl(rawUrl);
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal("Invalid URL: " + e.getMessage()));
            return 0;
        }

        String title;
        if (rawTitle == null || rawTitle.isBlank()) {
            String lower = url.toLowerCase(Locale.ROOT);
            if (lower.startsWith("spotify:") || lower.contains("open.spotify.com/")) {
                title = SpotifyTitleResolver.resolveTitle(url).orElse(url);
            } else {
                title = url;
            }
        } else {
            title = rawTitle.trim();
        }

        CustomDiscData data = new CustomDiscData(
                url,
                title,
                DiscDataUtil.clampRange(LazoDiscsConfig.DEFAULT_RANGE.get()),
                LazoDiscsConfig.DEFAULT_VOLUME.get().floatValue(),
                UUID.randomUUID()
        );
        DiscDataUtil.write(stack, data);
        AudioCache.preload(data);
        player.sendSystemMessage(Component.literal("Burned LazoDisc: " + title));
        return 1;
    }

    private static int clear(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!DiscDataUtil.hasCustomDisc(stack)) {
            player.sendSystemMessage(Component.literal("This item is not a LazoDisc."));
            return 0;
        }
        DiscDataUtil.clear(stack);
        player.sendSystemMessage(Component.literal("LazoDisc data removed."));
        return 1;
    }

    private static int stopAll(CommandSourceStack source) {
        LazoDiscs.playback().stopAll("command");
        source.sendSuccess(() -> Component.literal("Stopped all active LazoDisc sources."), true);
        return 1;
    }

    private static int cacheStats(CommandSourceStack source) {
        AudioCache.CacheStats stats = AudioCache.stats();
        double mib = stats.approximateBytes() / 1024.0D / 1024.0D;
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "LazoDiscs cache: %d cached, %d loading, %.2f MiB, %d active sources.",
                stats.cachedTracks(),
                stats.loadingTracks(),
                mib,
                LazoDiscs.playback().activeCount()
        )), false);
        return 1;
    }

    private static int clearCache(CommandSourceStack source) {
        AudioCache.clear();
        source.sendSuccess(() -> Component.literal("LazoDiscs RAM cache cleared."), true);
        return 1;
    }

    private static int search(CommandSourceStack source, String query, int page) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can use /lazodiscs search."));
            return 0;
        }

        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.isBlank()) {
            player.sendSystemMessage(Component.literal("Usage: /lazodiscs search \"song name\""));
            return 0;
        }

        int safePage = Math.max(1, page);
        player.sendSystemMessage(Component.literal("Searching: " + cleanQuery).withStyle(ChatFormatting.GRAY));

        AudioLoadExecutor.submit(() -> {
            try {
                List<LavaPcmFeeder.SearchResult> results = LavaPcmFeeder.searchYoutubeMusic(cleanQuery, SEARCH_MAX_RESULTS);
                player.getServer().execute(() -> sendSearchPage(player, cleanQuery, safePage, results));
            } catch (Throwable t) {
                LazoDiscs.LOGGER.warn("LazoDiscs search failed for '{}': {}", cleanQuery, t.toString());
                player.getServer().execute(() -> player.sendSystemMessage(Component.literal("Search failed: " + t.getMessage()).withStyle(ChatFormatting.RED)));
            }
        });
        return 1;
    }

    private static void sendSearchPage(ServerPlayer player, String query, int page, List<LavaPcmFeeder.SearchResult> results) {
        if (results.isEmpty()) {
            player.sendSystemMessage(Component.literal("No songs found for: " + query).withStyle(ChatFormatting.RED));
            return;
        }

        int totalPages = Math.max(1, (results.size() + SEARCH_PAGE_SIZE - 1) / SEARCH_PAGE_SIZE);
        int safePage = Math.max(1, Math.min(page, totalPages));
        int start = (safePage - 1) * SEARCH_PAGE_SIZE;
        int end = Math.min(results.size(), start + SEARCH_PAGE_SIZE);

        player.sendSystemMessage(Component.literal("=== LazoDiscs Search: " + query + " ===").withStyle(ChatFormatting.GOLD));
        for (int i = start; i < end; i++) {
            LavaPcmFeeder.SearchResult result = results.get(i);
            String title = sanitizeTitle(result.title());
            String author = sanitizeTitle(result.author());
            String burnCommand = "/lazodiscs burn " + quote(result.url()) + " " + title;
            Component line = Component.literal((i + 1) + ". ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(title).withStyle(style -> style
                            .withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, burnCommand))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to paste: " + burnCommand)))))
                    .append(Component.literal(" - " + author + " " + formatDuration(result.lengthMs())).withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(line);
        }

        MutableComponent nav = Component.literal("      ");

        if (safePage > 1) {
            String prev = "/lazodiscs search " + quote(query) + " " + (safePage - 1);
            nav = nav.append(Component.literal("<").withStyle(style -> style
                    .withColor(ChatFormatting.YELLOW)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, prev))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Previous page")))));
        } else {
            nav = nav.append(Component.literal("<").withStyle(ChatFormatting.DARK_GRAY));
        }

        nav = nav.append(Component.literal("  Page " + safePage + "/" + totalPages + "  ").withStyle(ChatFormatting.GRAY));

        if (safePage < totalPages) {
            String next = "/lazodiscs search " + quote(query) + " " + (safePage + 1);
            nav = nav.append(Component.literal(">").withStyle(style -> style
                    .withColor(ChatFormatting.YELLOW)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, next))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Next page")))));
        } else {
            nav = nav.append(Component.literal(">").withStyle(ChatFormatting.DARK_GRAY));
        }

        player.sendSystemMessage(nav);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String sanitizeTitle(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String formatDuration(long lengthMs) {
        if (lengthMs <= 0) return "";
        long totalSeconds = lengthMs / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return "(" + minutes + ":" + (seconds < 10 ? "0" : "") + seconds + ")";
    }
}
