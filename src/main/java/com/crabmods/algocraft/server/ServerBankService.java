package com.crabmods.algocraft.server;

import com.crabmods.algocraft.AlgoCraft;
import com.crabmods.algocraft.logic.ProblemManager;
import com.crabmods.algocraft.logic.catalog.PublishedCatalog;
import com.crabmods.algocraft.network.*;
import com.crabmods.algocraft.world.AlgoCraftSavedData;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import com.crabmods.algocraft.network.compat.PacketDistributor;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = AlgoCraft.MODID)
public final class ServerBankService {
    private static volatile PublishedCatalog current;
    private static final Map<UUID, Transfer> transfers = new LinkedHashMap<>();
    private static boolean updating;
    private record Transfer(ServerPlayer player, PublishedCatalog catalog, int index) {}
    public static PublishedCatalog current() { return current; }
    public static void start(MinecraftServer server) {
        ProblemManager.init();
        try { current = build(server); }
        catch (IOException e) { throw new IllegalStateException("Cannot publish the server problem bank", e); }
    }
    public static void stop() { current = null; transfers.clear(); updating = false; }
    private static PublishedCatalog build(MinecraftServer server) throws IOException {
        return PublishedCatalog.build(ProblemManager.getRepositories(), AlgoCraftSavedData.get(server.overworld()).getEnabledBanks());
    }
    public static void announce(ServerPlayer player) {
        PublishedCatalog catalog = current;
        if (catalog == null) return;
        transfers.remove(player.getUUID());
        try {
            PacketDistributor.sendToPlayer(player, new PacketCatalogManifest(
                    AlgoCraftSavedData.get(player.serverLevel()).getCatalogServerId().toString(),
                    catalog.revision(), catalog.byteSize(), catalog.chunkCount()));
        } catch (UnsupportedOperationException ignored) { /* fake/vanilla test player */ }
    }
    public static void request(ServerPlayer player, String revision) {
        PublishedCatalog catalog = current;
        if (catalog == null) return;
        if (!catalog.revision().equals(revision)) { announce(player); return; }
        // One transfer per player. Duplicate requests do not rewind an active transfer.
        transfers.putIfAbsent(player.getUUID(), new Transfer(player, catalog, 0));
    }
    @SubscribeEvent public static void tick(ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        int budget = 8; // bound global transfer work, independent of connected player count
        List<UUID> round = new ArrayList<>(transfers.keySet());
        for (UUID id : round) {
            if (budget-- <= 0) break;
            Transfer transfer = transfers.remove(id);
            if (transfer == null || transfer.player().hasDisconnected()) continue;
            try {
                PacketDistributor.sendToPlayer(transfer.player(), new PacketCatalogChunk(
                        transfer.catalog().revision(), transfer.index(), transfer.catalog().chunk(transfer.index())));
                if (transfer.index() + 1 < transfer.catalog().chunkCount())
                    transfers.put(id, new Transfer(transfer.player(), transfer.catalog(), transfer.index() + 1));
            } catch (UnsupportedOperationException ignored) { }
        }
    }
    public static boolean matches(String repository, String id, String version) {
        PublishedCatalog catalog = current;
        var entry = catalog == null ? null : catalog.get(id);
        return entry != null && entry.repository().equals(repository) && entry.version().equals(version);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var root = Commands.literal("algocraft");
        var bank = Commands.literal("bank").requires(source -> source.hasPermission(2));
        bank.then(Commands.literal("list").executes(ctx -> {
            var enabled = AlgoCraftSavedData.get(ctx.getSource().getServer().overworld()).getEnabledBanks();
            for (var repo : ProblemManager.getRepositories()) {
                String id = PublishedCatalog.repositoryId(repo.getName());
                ctx.getSource().sendSuccess(() -> Component.literal(id + " · " + repo.getProblems().size() + " · " + (enabled.contains(id) ? "enabled" : "disabled")), false);
            }
            return 1;
        }));
        for (boolean enabled : new boolean[]{true, false}) {
            bank.then(Commands.literal(enabled ? "enable" : "disable").then(Commands.argument("id", StringArgumentType.string()).executes(ctx -> {
                String id = StringArgumentType.getString(ctx, "id");
                if (ProblemManager.getRepositories().stream().noneMatch(r -> PublishedCatalog.repositoryId(r.getName()).equals(id))) {
                    ctx.getSource().sendFailure(Component.translatable("algocraft.bank.unknown", id)); return 0;
                }
                if (updating) { ctx.getSource().sendFailure(Component.translatable("algocraft.bank.busy")); return 0; }
                var data = AlgoCraftSavedData.get(ctx.getSource().getServer().overworld());
                Set<String> candidate = new HashSet<>(data.getEnabledBanks());
                if (enabled) candidate.add(id); else candidate.remove(id);
                try {
                    PublishedCatalog built = PublishedCatalog.build(ProblemManager.getRepositories(), candidate);
                    data.setEnabledBanks(candidate);
                    publish(ctx.getSource().getServer(), built);
                    ctx.getSource().sendSuccess(() -> Component.translatable(enabled ? "algocraft.bank.enabled" : "algocraft.bank.disabled", id), true);
                    return 1;
                } catch (IOException e) { ctx.getSource().sendFailure(Component.translatable("algocraft.bank.publication_failed", e.getMessage())); return 0; }
            })));
        }
        bank.then(Commands.literal("reload").executes(ctx -> update(ctx.getSource(), ProblemManager::refreshAllAsync, null)));
        bank.then(Commands.literal("update-official").executes(ctx -> update(ctx.getSource(), ProblemManager::forceRefreshOfficial, null)));
        bank.then(Commands.literal("install").then(Commands.argument("name", StringArgumentType.string())
                .then(Commands.argument("url", StringArgumentType.greedyString()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name"), url = StringArgumentType.getString(ctx, "url");
                    return update(ctx.getSource(), () -> ProblemManager.downloadAndUpdateRepository(name, url), PublishedCatalog.repositoryId(name));
                }))));
        var trophy = Commands.literal("trophy");
        trophy.then(Commands.literal("claim").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            var manager = AlgoCraftSavedData.get(player.serverLevel()).getAchievementManager();
            int delivered = manager.claimPendingTrophies(player);
            ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("algocraft.trophy.claim_result", "Delivered trophies / 已领取奖杯: %s", delivered), false);
            return delivered;
        }));
        trophy.then(Commands.literal("list").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            var data = AlgoCraftSavedData.get(player.serverLevel());
            ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("algocraft.trophy.pending_count", "Pending trophies / 待领取奖杯: %s. /algocraft trophy claim",
                    data.getPendingTrophies(player.getUUID()).size()), false);
            for (var award : data.getPendingTrophies(player.getUUID())) {
                ctx.getSource().sendSuccess(() -> com.crabmods.algocraft.logic.AchievementRegistry
                        .get(award.achievementId()).getName(), false);
            }
            return 1;
        }));
        trophy.then(Commands.literal("recover").requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", net.minecraft.commands.arguments.GameProfileArgument.gameProfile())
                        .executes(ctx -> {
                            int total = 0;
                            for (var profile : net.minecraft.commands.arguments.GameProfileArgument.getGameProfiles(ctx, "player")) {
                                var data = AlgoCraftSavedData.get(ctx.getSource().getServer().overworld());
                                int count = data.getAchievementManager().recoverLegacyTrophies(profile.getId(), profile.getName(), data);
                                ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(profile.getId());
                                if (online != null) data.getAchievementManager().claimPendingTrophies(online);
                                ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("algocraft.trophy.recovered",
                                        "Queued legacy trophies for %s: %s / 旧版奖杯已保存待领取", profile.getName(), count), false);
                                total += count;
                            }
                            return total;
                        })));
        trophy.then(Commands.literal("status").requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", net.minecraft.commands.arguments.GameProfileArgument.gameProfile())
                        .executes(ctx -> {
                            for (var profile : net.minecraft.commands.arguments.GameProfileArgument.getGameProfiles(ctx, "player")) {
                                var data = AlgoCraftSavedData.get(ctx.getSource().getServer().overworld());
                                var stats = data.getPlayerStats(profile.getId());
                                int earned = data.getAchievementManager().getPlayerAchievements(profile.getId()).size();
                                int pending = data.getPendingTrophies(profile.getId()).size();
                                ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("algocraft.trophy.status",
                                        "AlgoCraft %s uuid=%s solved=%s easy=%s medium=%s hard=%s achievements=%s pending=%s",
                                        profile.getName(), profile.getId().toString(), stats.totalSolved, stats.easyCount,
                                        stats.mediumCount, stats.hardCount, earned, pending), false);
                            }
                            return 1;
                        })));
        root.then(trophy);
        root.then(bank);
        event.getDispatcher().register(root);
    }
    private static int update(net.minecraft.commands.CommandSourceStack source,
                              java.util.function.Supplier<CompletableFuture<Void>> action, String enable) {
        if (updating) { source.sendFailure(Component.translatable("algocraft.bank.busy")); return 0; }
        updating = true;
        Set<String> enabled = new HashSet<>(AlgoCraftSavedData.get(source.getServer().overworld()).getEnabledBanks());
        if (enable != null) enabled.add(enable);
        CompletableFuture<Void> operation;
        try { operation = action.get(); }
        catch (Throwable e) { operation = CompletableFuture.failedFuture(e); }
        operation.thenApplyAsync(ignored -> {
            try { return PublishedCatalog.build(ProblemManager.getRepositories(), enabled); }
            catch (IOException e) { throw new java.util.concurrent.CompletionException(e); }
        }).whenComplete((built, error) -> source.getServer().execute(() -> {
            updating = false;
            if (error != null) { source.sendFailure(Component.translatable("algocraft.bank.update_failed", error.getMessage())); return; }
            AlgoCraftSavedData.get(source.getServer().overworld()).setEnabledBanks(enabled);
            publish(source.getServer(), built);
            source.sendSuccess(() -> Component.translatable("algocraft.bank.published", built.size()), true);
        }));
        source.sendSuccess(() -> Component.translatable("algocraft.bank.validating"), false);
        return 1;
    }
    private static void publish(MinecraftServer server, PublishedCatalog catalog) {
        current = catalog;
        transfers.clear();
        server.getPlayerList().getPlayers().forEach(ServerBankService::announce);
    }
}
