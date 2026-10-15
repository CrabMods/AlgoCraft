package com.crabmods.algocraft.event;

import com.crabmods.algocraft.AlgoCraft;
import com.crabmods.algocraft.network.PacketSyncProgress;
import com.crabmods.algocraft.world.AlgoCraftSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = AlgoCraft.MODID)
public class PlayerEventHandler {
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.crabmods.algocraft.server.ServerBankService.announce(player);
            AlgoCraftSavedData data = AlgoCraftSavedData.get(player.serverLevel());
            data.getAchievementManager().syncAdvancements(player);
            data.getAchievementManager().claimPendingTrophies(player);
            PacketSyncProgress.sendSafely(player, data.getPlayerProgress(player.getUUID()));
        }
    }
}
