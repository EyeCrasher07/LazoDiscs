package com.eyecrasher.lazodiscs.event;

import com.eyecrasher.lazodiscs.data.DiscDataUtil;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class LazoDiscsClientEvents {
    private LazoDiscsClientEvents() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!DiscDataUtil.hasCustomDisc(event.getItemStack())) {
            return;
        }

        // Vanilla music discs add their original record description as extra tooltip text
        // from the JUKEBOX_PLAYABLE component. For burned LazoDiscs we want the inventory
        // tooltip to show only the custom title, not the original disc song.
        var tooltip = event.getToolTip();
        if (tooltip.size() > 1) {
            tooltip.subList(1, tooltip.size()).clear();
        }
    }
}
