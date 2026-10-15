package com.crabmods.algocraft.logic;

import com.crabmods.algocraft.AlgoCraft;
import com.crabmods.algocraft.item.TrophyItem;
import com.crabmods.algocraft.logic.AchievementRegistry.Achievement;
import com.crabmods.algocraft.logic.AchievementRegistry.AchievementType;
import com.crabmods.algocraft.logic.AchievementRegistry.Rarity;
import com.crabmods.algocraft.world.AlgoCraftSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages achievement tracking and trophy awarding for players.
 * Integrates with AlgoCraftSavedData for persistence.
 */
public class AchievementManager {
    
    // In-memory cache of earned achievements per player UUID (thread-safe)
    private final Map<UUID, Set<String>> playerAchievements = new ConcurrentHashMap<>();
    
    private AchievementManager() {}

    public Set<UUID> getTrackedPlayers() { return Set.copyOf(playerAchievements.keySet()); }
    
    public static AchievementManager createForWorld() {
        return new AchievementManager();
    }

    public static AchievementManager forPlayer(ServerPlayer player) {
        return AlgoCraftSavedData.get(player.serverLevel()).getAchievementManager();
    }
    
    /**
     * Load achievements for a player from saved data.
     */
    public void loadPlayerAchievements(UUID playerUuid, CompoundTag savedData) {
        Set<String> achievements = ConcurrentHashMap.newKeySet();
        
        if (savedData != null && savedData.contains("EarnedAchievements")) {
            ListTag list = savedData.getList("EarnedAchievements", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                achievements.add(list.getString(i));
            }
        }
        
        playerAchievements.put(playerUuid, achievements);
    }
    
    /**
     * Load achievements directly from a Set (used by AlgoCraftSavedData).
     */
    public void loadPlayerAchievementsFromSet(UUID playerUuid, Set<String> achievements) {
        Set<String> threadSafeSet = ConcurrentHashMap.newKeySet();
        threadSafeSet.addAll(achievements);
        playerAchievements.put(playerUuid, threadSafeSet);
    }
    
    /**
     * Save achievements for a player to CompoundTag.
     */
    public CompoundTag savePlayerAchievements(UUID playerUuid) {
        CompoundTag tag = new CompoundTag();
        Set<String> achievements = playerAchievements.getOrDefault(playerUuid, ConcurrentHashMap.newKeySet());
        
        ListTag list = new ListTag();
        for (String achievementId : achievements) {
            list.add(StringTag.valueOf(achievementId));
        }
        tag.put("EarnedAchievements", list);
        
        return tag;
    }
    
    /**
     * Check if a player has earned a specific achievement.
     */
    public boolean hasAchievement(UUID playerUuid, String achievementId) {
        Set<String> achievements = playerAchievements.get(playerUuid);
        return achievements != null && achievements.contains(achievementId);
    }
    
    /**
     * Get all achievements earned by a player.
     */
    public Set<String> getPlayerAchievements(UUID playerUuid) {
        return Collections.unmodifiableSet(
            playerAchievements.getOrDefault(playerUuid, ConcurrentHashMap.newKeySet())
        );
    }
    
    /**
     * Award an achievement to a player if not already earned.
     * Returns true if the achievement was newly awarded.
     */
    public boolean awardAchievement(ServerPlayer player, String achievementId) {
        UUID uuid = player.getUUID();
        
        // Initialize player if not present (thread-safe)
        playerAchievements.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        
        Set<String> achievements = playerAchievements.get(uuid);
        if (achievements.contains(achievementId)) {
            return false; // Already has this achievement
        }
        
        Achievement achievement = AchievementRegistry.get(achievementId);
        if (achievement == null) {
            AlgoCraft.LOGGER.warn("Attempted to award unknown achievement: {}", achievementId);
            return false;
        }
        
        // Add to earned achievements
        achievements.add(achievementId);
        
        // Award trophy item
        awardTrophy(player, achievement);
        syncAdvancements(player);
        
        // Show achievement notification
        showAchievementNotification(player, achievement);
        
        // Play sound based on rarity
        playAchievementSound(player, achievement.getRarity());
        
        return true;
    }
    
    /**
     * Check and award any achievements based on player stats.
     * 
     * Optimized: Uses early exit checks to avoid unnecessary awardAchievement calls
     * when player already has the achievement.
     */
    public void checkAndAwardAchievements(ServerPlayer player, int totalSolved, int streak,
                                           int easyCount, int mediumCount, int hardCount) {
        UUID uuid = player.getUUID();
        Set<String> earned = playerAchievements.getOrDefault(uuid, ConcurrentHashMap.newKeySet());
        
        // ==================== Milestone Achievements ====================
        
        // First problem
        if (totalSolved >= 1 && !earned.contains(AchievementRegistry.FIRST_SOLVE)) {
            awardAchievement(player, AchievementRegistry.FIRST_SOLVE);
        }
        
        // Problem count milestones (check in descending order for efficiency)
        if (totalSolved >= 500 && !earned.contains(AchievementRegistry.LEGEND)) {
            awardAchievement(player, AchievementRegistry.LEGEND);
        }
        if (totalSolved >= 250 && !earned.contains(AchievementRegistry.GRANDMASTER)) {
            awardAchievement(player, AchievementRegistry.GRANDMASTER);
        }
        if (totalSolved >= 100 && !earned.contains(AchievementRegistry.MASTER)) {
            awardAchievement(player, AchievementRegistry.MASTER);
        }
        if (totalSolved >= 50 && !earned.contains(AchievementRegistry.EXPERT)) {
            awardAchievement(player, AchievementRegistry.EXPERT);
        }
        if (totalSolved >= 25 && !earned.contains(AchievementRegistry.JOURNEYMAN)) {
            awardAchievement(player, AchievementRegistry.JOURNEYMAN);
        }
        if (totalSolved >= 10 && !earned.contains(AchievementRegistry.APPRENTICE)) {
            awardAchievement(player, AchievementRegistry.APPRENTICE);
        }
        
        // ==================== Streak Achievements ====================
        
        if (streak >= 100 && !earned.contains(AchievementRegistry.STREAK_100)) {
            awardAchievement(player, AchievementRegistry.STREAK_100);
        }
        if (streak >= 30 && !earned.contains(AchievementRegistry.STREAK_30)) {
            awardAchievement(player, AchievementRegistry.STREAK_30);
        }
        if (streak >= 14 && !earned.contains(AchievementRegistry.STREAK_14)) {
            awardAchievement(player, AchievementRegistry.STREAK_14);
        }
        if (streak >= 7 && !earned.contains(AchievementRegistry.STREAK_7)) {
            awardAchievement(player, AchievementRegistry.STREAK_7);
        }
        if (streak >= 3 && !earned.contains(AchievementRegistry.STREAK_3)) {
            awardAchievement(player, AchievementRegistry.STREAK_3);
        }
        
        // ==================== Difficulty Achievements ====================
        
        // First of each difficulty
        if (easyCount >= 1 && !earned.contains(AchievementRegistry.FIRST_EASY)) {
            awardAchievement(player, AchievementRegistry.FIRST_EASY);
        }
        if (mediumCount >= 1 && !earned.contains(AchievementRegistry.FIRST_MEDIUM)) {
            awardAchievement(player, AchievementRegistry.FIRST_MEDIUM);
        }
        if (hardCount >= 1 && !earned.contains(AchievementRegistry.FIRST_HARD)) {
            awardAchievement(player, AchievementRegistry.FIRST_HARD);
        }
        
        // Difficulty mastery
        if (easyCount >= 100 && !earned.contains(AchievementRegistry.EASY_MASTER)) {
            awardAchievement(player, AchievementRegistry.EASY_MASTER);
        }
        if (mediumCount >= 50 && !earned.contains(AchievementRegistry.MEDIUM_MASTER)) {
            awardAchievement(player, AchievementRegistry.MEDIUM_MASTER);
        }
        if (hardCount >= 25 && !earned.contains(AchievementRegistry.HARD_MASTER)) {
            awardAchievement(player, AchievementRegistry.HARD_MASTER);
        }
        
        // Balance achievement
        if (easyCount >= 10 && mediumCount >= 10 && hardCount >= 10 
                && !earned.contains(AchievementRegistry.BALANCED)) {
            awardAchievement(player, AchievementRegistry.BALANCED);
        }
        // Perfectionist (checked when 10 consecutive correct)
    }
    
    /* Optimized with early exit checks.
     */
    public void checkSpecialAchievements(ServerPlayer player, long solveTimeMillis, 
                                         int consecutiveCorrect, boolean isNightTime) {
        UUID uuid = player.getUUID();
        Set<String> earned = playerAchievements.getOrDefault(uuid, ConcurrentHashMap.newKeySet());
        
        // Night Owl - solved at night (between 12am and 6am real time)
        if (isNightTime && !earned.contains(AchievementRegistry.NIGHT_OWL)) {
            awardAchievement(player, AchievementRegistry.NIGHT_OWL);
        }
        
        // Speed Demon - solved in under 60 seconds
        if (solveTimeMillis > 0 && solveTimeMillis < 60000 
                && !earned.contains(AchievementRegistry.SPEED_DEMON)) {
            awardAchievement(player, AchievementRegistry.SPEED_DEMON);
        }
        
        // Perfectionist - 10 consecutive correct
        if (consecutiveCorrect >= 10 && !earned.contains(AchievementRegistry.PERFECTIONIST)) {
            awardAchievement(player, AchievementRegistry.PERFECTIONIST);
        }
    }
    
    /**
     * Check if player has completed all problems in a repository.
     * Call this when a problem is solved.
     * 
     * @param player The player
     * @param repositoryName The repository name (prefix before the problem ID)
     * @param totalProblemsInRepo The total number of problems in the repository
     * @param solvedInRepo The number of problems the player has solved in this repository
     */
    public void checkCompletionistAchievement(ServerPlayer player, String repositoryName, 
                                               int totalProblemsInRepo, int solvedInRepo) {
        if (totalProblemsInRepo > 0 && solvedInRepo >= totalProblemsInRepo) {
            awardAchievement(player, AchievementRegistry.COMPLETIONIST);
        }
    }
    
    /**
     * Award the trophy item to the player.
     */
    private void awardTrophy(ServerPlayer player, Achievement achievement) {
        AlgoCraftSavedData data = AlgoCraftSavedData.get(player.serverLevel());
        data.queueTrophy(player.getUUID(), achievement.getId(), player.getName().getString(),
                Instant.now().toEpochMilli());
        claimPendingTrophies(player);
    }

    /** Never use Inventory.add: a full creative inventory may consume the stack and return true. */
    public int claimPendingTrophies(ServerPlayer player) {
        AlgoCraftSavedData data = AlgoCraftSavedData.get(player.serverLevel());
        int delivered = 0;
        for (var award : data.getPendingTrophies(player.getUUID())) {
            Achievement achievement = AchievementRegistry.get(award.achievementId());
            Item item = achievement == null ? null : getTrophyItemForAchievement(achievement);
            if (item == null) continue;
            int slot = -1;
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                if (player.getInventory().items.get(i).isEmpty()) { slot = i; break; }
            }
            if (slot < 0) break;
            ItemStack trophy = TrophyItem.createTrophy(item, achievement, award.playerName(),
                    player.getUUID().toString(), award.timestamp());
            player.getInventory().setItem(slot, trophy.copy());
            if (!ItemStack.matches(trophy, player.getInventory().getItem(slot))) {
                throw new IllegalStateException("Trophy inventory insertion was not confirmed");
            }
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            data.markTrophyDelivered(player.getUUID(), award.achievementId());
            delivered++;
            player.sendSystemMessage(Component.translatableWithFallback("algocraft.trophy.delivered_inventory", "Trophy added to inventory / 奖杯已放入物品栏: %s", achievement.getName())
                    .withStyle(ChatFormatting.GOLD));
        }
        int pending = data.getPendingTrophies(player.getUUID()).size();
        if (pending > 0) {
            player.sendSystemMessage(Component.translatableWithFallback("algocraft.trophy.pending", "Pending trophies / 待领取奖杯: %s. Free slots, then claim / 腾出空位后领取:", pending)
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.translatableWithFallback("algocraft.trophy.claim_link", " /algocraft trophy claim").withStyle(style -> style
                            .withColor(ChatFormatting.AQUA)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                    net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                    "/algocraft trophy claim")))));
        }
        return delivered;
    }

    /** Admin-only recovery for old awards without a delivery receipt; each can be recovered once. */
    public int recoverLegacyTrophies(ServerPlayer player) {
        int queued = recoverLegacyTrophies(player.getUUID(), player.getName().getString(),
                AlgoCraftSavedData.get(player.serverLevel()));
        claimPendingTrophies(player);
        return queued;
    }
    public int recoverLegacyTrophies(UUID uuid, String name, AlgoCraftSavedData data) {
        int queued = 0;
        for (String id : getPlayerAchievements(uuid)) {
            if (AchievementRegistry.get(id) != null && !data.hasTrophyRecord(uuid, id)) {
                data.queueTrophy(uuid, id, name, Instant.now().toEpochMilli());
                queued++;
            }
        }
        return queued;
    }

    /** Sync display progress only; this never awards or requeues a trophy. */
    public void syncAdvancements(ServerPlayer player) {
        Set<String> earned = getPlayerAchievements(player.getUUID());
        if (earned.isEmpty()) return;
        grantAdvancement(player, "root");
        for (String id : earned) if (AchievementRegistry.get(id) != null) grantAdvancement(player, id);
    }
    private void grantAdvancement(ServerPlayer player, String id) {
        var advancement = player.getServer().getAdvancements().get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AlgoCraft.MODID, "achievements/" + id));
        if (advancement != null) player.getAdvancements().award(advancement, "earned");
    }

    /**
     * Get the exact trophy item declared by the achievement.
     */
    private Item getTrophyItemForAchievement(Achievement achievement) {
        return ModItems.getTrophyForItemId(achievement.getTrophyItemId());
    }
    
    /**
     * Show achievement unlock notification to player.
     */
    private void showAchievementNotification(ServerPlayer player, Achievement achievement) {
        // Title message
        Component title = Component.translatable("algocraft.achievement.unlocked")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        
        // Subtitle with achievement name
        Component subtitle = achievement.getName()
            .copy()
            .withStyle(achievement.getRarity().getColor());
        
        // Send as title and subtitle
        player.sendSystemMessage(
            Component.empty()
                .append(title)
                .append(Component.literal(" "))
                .append(subtitle)
        );
        
        // Also send detailed chat message
        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(
            Component.translatable("algocraft.achievement.trophy_prefix")
                .withStyle(ChatFormatting.GOLD)
                .append(achievement.getName().copy().withStyle(achievement.getRarity().getColor()))
        );
        player.sendSystemMessage(
            Component.literal("  ")
                .append(achievement.getDescription().copy().withStyle(ChatFormatting.GRAY))
        );
        player.sendSystemMessage(
            Component.literal("  ")
                .append(Component.translatable("algocraft.achievement.rarity",
                    achievement.getRarity().getDisplayName()))
                .withStyle(ChatFormatting.DARK_GRAY)
        );
        player.sendSystemMessage(Component.empty());
    }
    
    /**
     * Play sound effect based on achievement rarity.
     */
    private void playAchievementSound(ServerPlayer player, Rarity rarity) {
        player.level().playSound(null, player.blockPosition(), soundForRarity(rarity),
            SoundSource.PLAYERS, volumeForRarity(rarity), pitchForRarity(rarity));
    }

    static SoundEvent soundForRarity(Rarity rarity) {
        return switch (rarity) {
            case COMMON, UNCOMMON -> SoundEvents.PLAYER_LEVELUP;
            case RARE -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case EPIC -> SoundEvents.ENDER_DRAGON_GROWL;
            case LEGENDARY -> SoundEvents.END_PORTAL_SPAWN;
            case MYTHIC -> SoundEvents.TOTEM_USE;
        };
    }

    static float volumeForRarity(Rarity rarity) {
        return rarity.ordinal() >= Rarity.EPIC.ordinal() ? 1.0f : 0.7f;
    }

    static float pitchForRarity(Rarity rarity) {
        return 1.0f + (rarity.ordinal() * 0.1f);
    }
    
    /**
     * Get achievement statistics for a player.
     */
    public AchievementStats getStats(UUID playerUuid) {
        Set<String> earned = playerAchievements.getOrDefault(playerUuid, ConcurrentHashMap.newKeySet());
        
        int totalAvailable = AchievementRegistry.getAll().size();
        int totalEarned = earned.size();
        
        Map<AchievementType, Integer> byType = new EnumMap<>(AchievementType.class);
        Map<Rarity, Integer> byRarity = new EnumMap<>(Rarity.class);
        
        for (String achievementId : earned) {
            Achievement achievement = AchievementRegistry.get(achievementId);
            if (achievement != null) {
                byType.merge(achievement.getType(), 1, Integer::sum);
                byRarity.merge(achievement.getRarity(), 1, Integer::sum);
            }
        }
        
        return new AchievementStats(totalEarned, totalAvailable, byType, byRarity);
    }
    
    /**
     * Statistics about a player's achievements.
     */
    public record AchievementStats(
        int earned,
        int total,
        Map<AchievementType, Integer> byType,
        Map<Rarity, Integer> byRarity
    ) {
        public double getCompletionPercentage() {
            return total > 0 ? (earned * 100.0 / total) : 0;
        }
    }
}
