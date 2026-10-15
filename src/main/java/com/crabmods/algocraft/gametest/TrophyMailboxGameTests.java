package com.crabmods.algocraft.gametest;

import com.crabmods.algocraft.AlgoCraft;
import com.crabmods.algocraft.item.TrophyItem;
import com.crabmods.algocraft.logic.AchievementManager;
import com.crabmods.algocraft.logic.AchievementRegistry;
import com.crabmods.algocraft.logic.ModItems;
import com.crabmods.algocraft.world.AlgoCraftSavedData;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(AlgoCraft.MODID)
@PrefixGameTestTemplate(false)
public final class TrophyMailboxGameTests {
    @GameTest(template = "empty")
    public static void test_ticks_preserve_real_time_for_async_judging(GameTestHelper helper) {
        long started = System.nanoTime();
        helper.runAfterDelay(4, () -> {
            check(System.nanoTime() - started >= java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(100),
                    "test ticks must not outrun the real-time judge deadline");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void full_creative_inventory_never_consumes_earned_trophy(GameTestHelper helper) {
        withPlayer(helper, player -> {
            player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            var data = AlgoCraftSavedData.get(player.serverLevel());
            var manager = data.getAchievementManager();
            fill(player);
            // Reproduce the actual vanilla failure path before checking our replacement.
            ItemStack oldProbe = TrophyItem.createTrophy(ModItems.BRONZE_TROPHY.get(),
                    AchievementRegistry.get("first_solve"), "probe", player.getUUID().toString(), 1L);
            check(player.getInventory().add(oldProbe) && oldProbe.isEmpty(),
                    "vanilla add consumes an unstackable trophy in a full creative inventory");
            check(count(player) == 0, "vanilla probe did not reach any inventory slot");
            int entities = player.serverLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(2)).size();
            manager.awardAchievement(player, "first_solve");
            check(count(player) == 0 && data.getPendingTrophies(player.getUUID()).size() == 1,
                    "full creative inventory retains a pending server award");
            check(player.serverLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(2)).size() == entities,
                    "no trophy is dropped into the world");
            long timestamp = data.getPendingTrophies(player.getUUID()).get(0).timestamp();
            player.getInventory().setItem(9, ItemStack.EMPTY);
            check(manager.claimPendingTrophies(player) == 1, "claim delivers exactly one trophy");
            ItemStack delivered = player.getInventory().getItem(9);
            check("first_solve".equals(TrophyItem.getAchievementId(delivered)), "delivery uses the real empty slot");
            check(timestamp == TrophyItem.getTimestamp(delivered), "delayed claim preserves the award date");
            check(manager.claimPendingTrophies(player) == 0 && count(player) == 1, "repeat claim does not duplicate");
            check(!manager.awardAchievement(player, "first_solve"), "earned achievement cannot be awarded twice");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pending_awards_and_progress_survive_disk_write_and_reload(GameTestHelper helper) {
        withPlayer(helper, player -> {
            fill(player);
            var data = AlgoCraftSavedData.get(player.serverLevel());
            var manager = data.getAchievementManager();
            UUID id = player.getUUID();
            data.setProblemSolved(id, "persistence-fixture", 1234567L);
            data.incrementTotalSolved(id);
            data.incrementDifficultyCount(id, "hard");
            data.updateStreak(id, 1234567L);
            manager.awardAchievement(player, "first_hard");
            manager.awardAchievement(player, "first_solve");
            check(data.isDirty(), "award and pending queue mark world data dirty");
            var path = java.nio.file.Path.of("trophy-mailbox-restart-" + id + ".dat");
            try {
                NbtIo.writeCompressed(data.save(new CompoundTag()), path.toFile());
                var saved = NbtIo.readCompressed(path.toFile());
                var loaded = AlgoCraftSavedData.load(saved);
                check(loaded.isProblemSolved(id, "persistence-fixture") && loaded.getTotalSolved(id) == 1,
                        "accepted progress and total survive a new SavedData instance");
                check(loaded.getDifficultyCount(id, "hard") == 1 && loaded.getCurrentStreak(id) == 1,
                        "difficulty and streak counters survive reload");
                check(loaded.getAchievementManager().hasAchievement(id, "first_hard")
                        && loaded.getPendingTrophies(id).equals(data.getPendingTrophies(id)),
                        "earned achievements and original pending engravings survive disk reload");
                player.getInventory().setItem(10, ItemStack.EMPTY);
                check(manager.claimPendingTrophies(player) == 1 && data.getPendingTrophies(id).size() == 1,
                        "one free slot delivers one award and leaves the rest pending");
                NbtIo.writeCompressed(data.save(new CompoundTag()), path.toFile());
                loaded = AlgoCraftSavedData.load(NbtIo.readCompressed(path.toFile()));
                check(loaded.getPendingTrophies(id).size() == 1 && loaded.hasTrophyRecord(id, "first_hard"),
                        "delivered receipt and remaining pending queue survive another reload");
                player.getInventory().setItem(11, ItemStack.EMPTY);
                check(manager.claimPendingTrophies(player) == 1 && manager.claimPendingTrophies(player) == 0,
                        "remaining award is claimed once after space is freed");
            } catch (java.io.IOException e) { throw new GameTestAssertException(e.toString()); }
            finally {
                try { Files.deleteIfExists(path); }
                catch (java.io.IOException e) { throw new GameTestAssertException("Temporary trophy fixture cleanup failed: " + e); }
            }
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void old_achievements_sync_tree_without_duplicate_trophies(GameTestHelper helper) {
        withPlayer(helper, player -> {
            var manager = AchievementManager.forPlayer(player);
            manager.loadPlayerAchievementsFromSet(player.getUUID(), Set.of("first_solve", "apprentice", "speed_demon"));
            manager.syncAdvancements(player);
            for (String id : Set.of("root", "first_solve", "apprentice", "speed_demon")) {
                var advancement = player.getServer().getAdvancements().getAdvancement(
                        new net.minecraft.resources.ResourceLocation(AlgoCraft.MODID, "achievements/" + id));
                check(advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone(),
                        "saved achievement appears in vanilla progress tree: " + id);
            }
            for (var achievement : AchievementRegistry.getAll()) {
                check(player.getServer().getAdvancements().getAdvancement(new net.minecraft.resources.ResourceLocation(AlgoCraft.MODID, "achievements/" + achievement.getId())) != null,
                        "all 23 achievement nodes are loaded by the game");
            }
            check(count(player) == 0, "syncing the progress tree does not award trophies again");
            check(manager.recoverLegacyTrophies(player) == 3 && count(player) == 3,
                    "old awards without delivery receipts can be recovered");
            check(manager.recoverLegacyTrophies(player) == 0 && count(player) == 3,
                    "legacy recovery cannot be repeated to duplicate trophies");
            manager.syncAdvancements(player);
            check(count(player) == 3, "repeated tree sync remains display-only");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void achievement_only_player_is_included_in_world_save(GameTestHelper helper) {
        withPlayer(helper, player -> {
            var data = AlgoCraftSavedData.get(player.serverLevel());
            var manager = data.getAchievementManager();
            manager.awardAchievement(player, "first_solve");
            var loaded = AlgoCraftSavedData.load(data.save(new CompoundTag()));
            check(loaded.getAchievementManager().hasAchievement(player.getUUID(), "first_solve")
                    && loaded.hasTrophyRecord(player.getUUID(), "first_solve"),
                    "a player with achievements but no progress map entry is still saved");
        });
        helper.succeed();
    }

    private static void fill(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().items.size(); i++)
            player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
    }
    private static long count(ServerPlayer player) {
        return player.getInventory().items.stream().filter(stack -> stack.getItem() instanceof TrophyItem).count();
    }
    private static void withPlayer(GameTestHelper helper, Consumer<ServerPlayer> scenario) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "trophy-test"));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        player.getServer().getPlayerList().placeNewPlayer(connection, player);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.getInventory().clearContent();
        try { scenario.accept(player); }
        finally { player.getInventory().clearContent(); player.getServer().getPlayerList().remove(player); }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
