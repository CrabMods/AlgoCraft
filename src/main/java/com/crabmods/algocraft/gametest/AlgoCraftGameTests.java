package com.crabmods.algocraft.gametest;

import com.crabmods.algocraft.AlgoCraft;
import com.crabmods.algocraft.AlgorithmComputerBlock;
import com.crabmods.algocraft.item.TrophyItem;
import com.crabmods.algocraft.logic.AchievementManager;
import com.crabmods.algocraft.logic.AchievementRegistry;
import com.crabmods.algocraft.logic.ModItems;
import com.crabmods.algocraft.logic.Problem;
import com.crabmods.algocraft.logic.SubmissionResult;
import com.crabmods.algocraft.network.PacketSubmitSolution;
import com.crabmods.algocraft.network.SolvedProblemRewardService;
import com.crabmods.algocraft.server.SolvingPlayerManager;
import com.crabmods.algocraft.world.AlgoCraftSavedData;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(AlgoCraft.MODID)
@PrefixGameTestTemplate(false)
public final class AlgoCraftGameTests {
    private static final String EMPTY_TEMPLATE = "empty";
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final long FIXED_AWARD_TIME = 1_786_006_800_000L;
    private static final String PLAYER_NAME = "GameTest";
    private static final String PLAYER_UUID = "00000000-0000-0000-0000-000000000001";
    private static final int ASYNC_SUBMISSION_TIMEOUT_TICKS = 24_000;

    private AlgoCraftGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void algorithm_computer_registry_state_and_shape_are_game_valid(GameTestHelper helper) {
        ForgeWireCompatibilityChecks.verify();
        assertTrue(AlgoCraft.ALGORITHM_COMPUTER_BLOCK.isPresent(),
                "algorithm computer block must be bound in the block registry");
        assertTrue(AlgoCraft.ALGORITHM_COMPUTER_BLOCK_ITEM.isPresent(),
                "algorithm computer block item must be bound in the item registry");

        Block block = AlgoCraft.ALGORITHM_COMPUTER_BLOCK.get();
        assertTrue(block instanceof AlgorithmComputerBlock,
                "algorithm computer must use AlgorithmComputerBlock, got " + block.getClass().getName());
        assertFalse(new ItemStack(AlgoCraft.ALGORITHM_COMPUTER_BLOCK_ITEM.get()).isEmpty(),
                "algorithm computer item stack must not be empty");

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState expected = block.defaultBlockState().setValue(AlgorithmComputerBlock.FACING, facing);
            helper.setBlock(POS, expected);

            BlockState actual = helper.getBlockState(POS);
            assertSame(block, actual.getBlock(), "placed algorithm computer should keep its block type");
            assertEquals(facing, actual.getValue(AlgorithmComputerBlock.FACING),
                    "placed algorithm computer should keep facing " + facing);

            VoxelShape shape = actual.getShape(helper.getLevel(), helper.absolutePos(POS));
            assertFalse(shape.isEmpty(), "algorithm computer shape must not be empty for " + facing);
            assertFalse(Shapes.block().equals(shape), "algorithm computer shape must not be a full cube for " + facing);
            assertTrue(shape.toAabbs().size() >= 4,
                    "algorithm computer should expose multiple physical parts for " + facing);
            assertBoundsStayInsideBlock(shape.bounds(), facing);
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void algorithm_computer_interaction_accepts_real_player_for_every_facing(GameTestHelper helper) {
        Block block = AlgoCraft.ALGORITHM_COMPUTER_BLOCK.get();
        assertTrue(block instanceof AlgorithmComputerBlock,
                "algorithm computer interaction test requires AlgorithmComputerBlock, got " + block.getClass().getName());
        AlgorithmComputerBlock computerBlock = (AlgorithmComputerBlock) block;
        ServerPlayer player = makeGameTestServerPlayer(helper);

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState expected = computerBlock.defaultBlockState().setValue(AlgorithmComputerBlock.FACING, facing);
            helper.setBlock(POS, expected);

            BlockPos absolutePos = helper.absolutePos(POS);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolutePos), facing, absolutePos, false);
            InteractionResult result = computerBlock.use(
                    helper.getBlockState(POS),
                    helper.getLevel(),
                    absolutePos,
                    player,
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    hit
            );

            assertEquals(InteractionResult.SUCCESS, result,
                    "algorithm computer right-click should be accepted for " + facing);
            BlockState actual = helper.getBlockState(POS);
            assertSame(block, actual.getBlock(),
                    "algorithm computer interaction should not replace the block for " + facing);
            assertEquals(facing, actual.getValue(AlgorithmComputerBlock.FACING),
                    "algorithm computer interaction should preserve facing " + facing);
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void achievement_trophies_create_runtime_stacks_with_award_metadata(GameTestHelper helper) {
        Set<String> seenTrophyItems = new HashSet<>();
        Set<Integer> seenModelData = new HashSet<>();

        for (AchievementRegistry.Achievement achievement : AchievementRegistry.getAll()) {
            assertTrue(ModItems.isKnownTrophyItemId(achievement.getTrophyItemId()),
                    achievement.getId() + " declares unknown trophy item " + achievement.getTrophyItemId());

            TrophyItem trophyItem = ModItems.getTrophyForItemId(achievement.getTrophyItemId());
            assertNotNull(trophyItem, achievement.getId() + " trophy item should resolve after registry load");
            seenTrophyItems.add(achievement.getTrophyItemId());
            assertTrue(seenModelData.add(achievement.getTrophyModelData()),
                    achievement.getId() + " reuses custom model data " + achievement.getTrophyModelData());

            ItemStack stack = TrophyItem.createTrophy(
                    trophyItem,
                    achievement,
                    PLAYER_NAME,
                    PLAYER_UUID,
                    FIXED_AWARD_TIME
            );

            assertSame((Item) trophyItem, stack.getItem(), achievement.getId() + " should use its declared trophy item");
            assertEquals(1, stack.getMaxStackSize(), achievement.getId() + " trophy should stack to one");
            assertEquals(achievement.getId(), TrophyItem.getAchievementId(stack),
                    achievement.getId() + " stack should store achievement id");
            assertEquals(PLAYER_NAME, TrophyItem.getPlayerName(stack),
                    achievement.getId() + " stack should store player name");
            assertEquals(PLAYER_UUID, TrophyItem.getPlayerUuid(stack),
                    achievement.getId() + " stack should store player uuid");
            assertEquals(FIXED_AWARD_TIME, TrophyItem.getTimestamp(stack),
                    achievement.getId() + " stack should store exact award time");

            Integer modelData = stack.hasTag() && stack.getTag().contains("CustomModelData")
                    ? stack.getTag().getInt("CustomModelData") : null;
            assertNotNull(modelData, achievement.getId() + " stack should store custom model data");
            assertEquals(achievement.getTrophyModelData(), modelData.intValue(),
                    achievement.getId() + " stack should select its achievement-specific model");
        }

        assertEquals(23, AchievementRegistry.getAll().size(), "all registered achievements should be covered");
        assertEquals(Set.of("bronze_trophy", "silver_trophy", "gold_trophy", "diamond_trophy", "netherite_trophy"),
                seenTrophyItems, "achievement set should exercise every trophy tier at runtime");
        assertEquals(AchievementRegistry.getAll().size(), seenModelData.size(),
                "every achievement should have a unique runtime model selector");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void achievement_manager_awards_declared_trophies_to_real_server_player(GameTestHelper helper) {
        ServerPlayer player = makeGameTestServerPlayer(helper);
        player.getInventory().clearContent();

        UUID playerUuid = player.getUUID();
        AchievementManager manager = AchievementManager.forPlayer(player);
        manager.loadPlayerAchievementsFromSet(playerUuid, Set.of());

        long beforeAward = System.currentTimeMillis();
        manager.checkAndAwardAchievements(player, 1, 1, 1, 0, 0);

        assertTrue(manager.hasAchievement(playerUuid, AchievementRegistry.FIRST_SOLVE),
                "first solve achievement should be recorded for the real server player");
        assertTrue(manager.hasAchievement(playerUuid, AchievementRegistry.FIRST_EASY),
                "first easy achievement should be recorded for the real server player");

        Set<String> trophyAchievementIds = new HashSet<>();
        int trophyStacks = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!(stack.getItem() instanceof TrophyItem)) {
                continue;
            }
            trophyStacks++;
            String achievementId = TrophyItem.getAchievementId(stack);
            trophyAchievementIds.add(achievementId);
            AchievementRegistry.Achievement achievement = AchievementRegistry.get(achievementId);
            assertNotNull(achievement, "awarded trophy should point at a registered achievement: " + achievementId);
            assertSame(ModItems.getTrophyForItemId(achievement.getTrophyItemId()), stack.getItem(),
                    achievementId + " should award the exact declared trophy item");
            assertEquals(player.getName().getString(), TrophyItem.getPlayerName(stack),
                    achievementId + " trophy should store the server player's name");
            assertEquals(playerUuid.toString(), TrophyItem.getPlayerUuid(stack),
                    achievementId + " trophy should store the server player's UUID");
            assertTrue(TrophyItem.getTimestamp(stack) >= beforeAward,
                    achievementId + " trophy should store a real award timestamp");

            Integer modelData = stack.hasTag() && stack.getTag().contains("CustomModelData")
                    ? stack.getTag().getInt("CustomModelData") : null;
            assertNotNull(modelData, achievementId + " trophy should keep custom model data after inventory insertion");
            assertEquals(achievement.getTrophyModelData(), modelData.intValue(),
                    achievementId + " trophy should keep its achievement-specific model selector");
        }

        assertEquals(Set.of(AchievementRegistry.FIRST_SOLVE, AchievementRegistry.FIRST_EASY),
                trophyAchievementIds, "first solve should award exactly the milestone and difficulty trophies");
        assertEquals(2, trophyStacks, "first solve should put two trophy stacks in the player inventory");

        manager.checkAndAwardAchievements(player, 1, 1, 1, 0, 0);
        assertEquals(2, countTrophyStacks(player),
                "rechecking already earned achievements must not duplicate trophy items");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void solved_problem_reward_application_updates_real_player_progress_once_per_day(GameTestHelper helper) {
        ServerPlayer player = makeGameTestServerPlayer(helper);
        player.getInventory().clearContent();

        UUID playerUuid = player.getUUID();
        AlgoCraftSavedData data = AlgoCraftSavedData.get(player.serverLevel());
        String problemId = "gametest-packet-solve-" + Long.toUnsignedString(System.nanoTime());
        long solveTime = 1_786_046_400_000L;

        int totalBefore = data.getTotalSolved(playerUuid);
        int easyBefore = data.getDifficultyCount(playerUuid, "easy");
        int consecutiveBefore = data.getConsecutiveCorrect(playerUuid);

        SolvedProblemRewardService.applySolvedProblem(player, problemId, "easy", 90_000L, solveTime);

        assertTrue(data.isProblemSolved(playerUuid, problemId),
                "first solve should record the solved problem in SavedData");
        assertEquals(solveTime, data.getLastSolvedTime(playerUuid, problemId),
                "first solve should store the exact solve timestamp");
        assertEquals(totalBefore + 1, data.getTotalSolved(playerUuid),
                "first solve should increment total solved exactly once");
        assertEquals(easyBefore + 1, data.getDifficultyCount(playerUuid, "easy"),
                "first easy solve should increment the easy counter exactly once");
        assertEquals(consecutiveBefore + 1, data.getConsecutiveCorrect(playerUuid),
                "first accepted solve should increment consecutive-correct submissions");
        assertTrue(data.getPlayerProgress(playerUuid).containsKey(problemId),
                "progress sync payload source should include the newly solved problem");
        assertTrue(countTotalInventoryItems(player) > 0,
                "first solve should grant tangible inventory rewards to the real server player");

        int inventoryItemsAfterFirstSolve = countTotalInventoryItems(player);
        SolvedProblemRewardService.applySolvedProblem(player, problemId, "easy", 90_000L, solveTime + 1_000L);

        assertEquals(solveTime, data.getLastSolvedTime(playerUuid, problemId),
                "same-day duplicate solve must not rewrite the progress timestamp");
        assertEquals(totalBefore + 1, data.getTotalSolved(playerUuid),
                "same-day duplicate solve must not increment total solved");
        assertEquals(easyBefore + 1, data.getDifficultyCount(playerUuid, "easy"),
                "same-day duplicate solve must not increment the easy counter");
        assertEquals(consecutiveBefore + 1, data.getConsecutiveCorrect(playerUuid),
                "same-day duplicate solve must not count as another accepted submission");
        assertEquals(inventoryItemsAfterFirstSolve, countTotalInventoryItems(player),
                "same-day duplicate solve must not grant another inventory reward");

        long nextDaySolveTime = solveTime + 86_400_000L;
        SolvedProblemRewardService.applySolvedProblem(player, problemId, "easy", 90_000L, nextDaySolveTime);

        assertEquals(nextDaySolveTime, data.getLastSolvedTime(playerUuid, problemId),
                "next-day repeat solve should refresh the progress timestamp for daily reward tracking");
        assertEquals(totalBefore + 1, data.getTotalSolved(playerUuid),
                "next-day repeat solve must not count as a new unique problem");
        assertEquals(easyBefore + 1, data.getDifficultyCount(playerUuid, "easy"),
                "next-day repeat solve must not count as a new unique easy problem");
        assertEquals(consecutiveBefore + 2, data.getConsecutiveCorrect(playerUuid),
                "next-day accepted repeat solve should count as another correct submission");
        assertTrue(countTotalInventoryItems(player) > inventoryItemsAfterFirstSolve,
                "next-day repeat solve should grant the smaller daily reward");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void server_submission_judge_awards_only_accepted_code(GameTestHelper helper) {
        ServerPlayer player = makeGameTestServerPlayer(helper);
        player.getInventory().clearContent();

        UUID playerUuid = player.getUUID();
        AlgoCraftSavedData data = AlgoCraftSavedData.get(player.serverLevel());
        Problem problem = simpleServerJudgedProblem("gametest-server-submit-" + Long.toUnsignedString(System.nanoTime()));
        long solveTime = 1_786_132_800_000L;

        int totalBefore = data.getTotalSolved(playerUuid);
        int easyBefore = data.getDifficultyCount(playerUuid, "easy");

        SubmissionResult wrongResult = PacketSubmitSolution.submitVerifiedProblem(player, problem, """
                class Solution {
                    public int solve() {
                        return 2;
                    }
                }
                """, 90_000L, solveTime);

        assertFalse(wrongResult.isSuccess(), "wrong code should fail the real server Judge");
        assertFalse(data.isProblemSolved(playerUuid, problem.getId()),
                "wrong code must not record progress even if the client sent a submit packet");
        assertEquals(totalBefore, data.getTotalSolved(playerUuid),
                "wrong code must not increment total solved");
        assertEquals(easyBefore, data.getDifficultyCount(playerUuid, "easy"),
                "wrong code must not increment difficulty counters");
        assertEquals(0, data.getConsecutiveCorrect(playerUuid),
                "wrong code should reset consecutive correct submissions");
        assertEquals(0, countTotalInventoryItems(player),
                "wrong code must not grant reward items");

        SubmissionResult acceptedResult = PacketSubmitSolution.submitVerifiedProblem(player, problem, """
                class Solution {
                    public int solve() {
                        return 1;
                    }
                }
                """, 90_000L, solveTime + 1_000L);

        assertTrue(acceptedResult.isSuccess(), "correct code should pass the real server Judge");
        assertTrue(data.isProblemSolved(playerUuid, problem.getId()),
                "accepted server-judged submission should record progress");
        assertEquals(totalBefore + 1, data.getTotalSolved(playerUuid),
                "accepted server-judged submission should increment total solved once");
        assertEquals(easyBefore + 1, data.getDifficultyCount(playerUuid, "easy"),
                "accepted server-judged submission should increment the problem difficulty once");
        assertEquals(1, data.getConsecutiveCorrect(playerUuid),
                "accepted submission after a failure should restart the consecutive-correct chain");
        assertTrue(countTotalInventoryItems(player) > 0,
                "accepted server-judged submission should grant reward items");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void server_submission_rejects_invalid_verified_problem_ids_before_progress(GameTestHelper helper) {
        ServerPlayer player = makeGameTestServerPlayer(helper);
        player.getInventory().clearContent();

        UUID playerUuid = player.getUUID();
        AlgoCraftSavedData data = AlgoCraftSavedData.get(player.serverLevel());
        long solveTime = 1_786_176_000_000L;

        int totalBefore = data.getTotalSolved(playerUuid);
        int easyBefore = data.getDifficultyCount(playerUuid, "easy");

        Problem blankIdProblem = simpleServerJudgedProblem(" ");
        SubmissionResult blankIdResult = PacketSubmitSolution.submitVerifiedProblem(player, blankIdProblem, """
                class Solution {
                    public int solve() {
                        return 1;
                    }
                }
                """, 90_000L, solveTime);

        assertFalse(blankIdResult.isSuccess(),
                "verified server submission with a blank problem id must be rejected before Judge");
        assertFalse(data.isProblemSolved(playerUuid, blankIdProblem.getId()),
                "blank verified problem id must not be recorded as solved progress");

        Problem oversizedIdProblem = simpleServerJudgedProblem("x".repeat(257));
        CompletableFuture<SubmissionResult> oversizedFuture = PacketSubmitSolution.submitAsyncVerifiedProblem(
                player,
                oversizedIdProblem,
                """
                        class Solution {
                            public int solve() {
                                return 1;
                            }
                        }
                        """,
                90_000L
        );

        assertTrue(oversizedFuture.isDone(),
                "oversized verified problem id should be rejected before async judge scheduling");
        SubmissionResult oversizedResult = oversizedFuture.join();
        assertFalse(oversizedResult.isSuccess(),
                "async verified server submission with an oversized problem id must be rejected");
        assertFalse(PacketSubmitSolution.hasPlayerSubmissionInFlightForGameTest(player),
                "invalid verified problem id rejection must not leave a per-player judge slot in flight");
        assertFalse(data.isProblemSolved(playerUuid, oversizedIdProblem.getId()),
                "oversized verified problem id must not be recorded as solved progress");
        assertEquals(totalBefore, data.getTotalSolved(playerUuid),
                "invalid verified problem ids must not increment total solved");
        assertEquals(easyBefore, data.getDifficultyCount(playerUuid, "easy"),
                "invalid verified problem ids must not increment difficulty counters");
        assertEquals(0, countTotalInventoryItems(player),
                "invalid verified problem ids must not grant reward items");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void server_submission_missing_problem_counts_as_failure_without_rewards(GameTestHelper helper) {
        ServerPlayer player = makeGameTestServerPlayer(helper);
        player.getInventory().clearContent();

        UUID playerUuid = player.getUUID();
        AlgoCraftSavedData data = AlgoCraftSavedData.get(player.serverLevel());
        long solveTime = 1_786_190_400_000L;
        String priorProblemId = "gametest-server-prior-solve-" + Long.toUnsignedString(System.nanoTime());
        String missingProblemId = "gametest-server-missing-problem-" + Long.toUnsignedString(System.nanoTime());

        SolvedProblemRewardService.applySolvedProblem(player, priorProblemId, "easy", 90_000L, solveTime);
        assertEquals(1, data.getConsecutiveCorrect(playerUuid),
                "test setup should start from a non-zero consecutive-correct streak");
        player.getInventory().clearContent();

        int totalBefore = data.getTotalSolved(playerUuid);
        int easyBefore = data.getDifficultyCount(playerUuid, "easy");

        CompletableFuture<SubmissionResult> missingResultFuture = PacketSubmitSolution.submitAsync(player, missingProblemId, """
                class Solution {
                    public int solve() {
                        return 1;
                    }
                }
                """, 90_000L);

        assertTrue(missingResultFuture.isDone(),
                "missing server problem should be rejected before async Judge scheduling");
        SubmissionResult missingResult = missingResultFuture.join();
        assertFalse(missingResult.isSuccess(),
                "missing server problem should produce a rejected submission result");
        assertEquals("Problem not found on server", missingResult.getMessage(),
                "missing server problem should report the missing-problem reason");
        assertEquals(0, data.getConsecutiveCorrect(playerUuid),
                "missing server problem must count as a failed submission and reset the streak");
        assertFalse(data.isProblemSolved(playerUuid, missingProblemId),
                "missing server problem must not be recorded as solved progress");
        assertEquals(totalBefore, data.getTotalSolved(playerUuid),
                "missing server problem must not increment total solved");
        assertEquals(easyBefore, data.getDifficultyCount(playerUuid, "easy"),
                "missing server problem must not increment difficulty counters");
        assertEquals(0, countTotalInventoryItems(player),
                "missing server problem must not grant reward items");
        assertFalse(PacketSubmitSolution.hasPlayerSubmissionInFlightForGameTest(player),
                "missing server problem rejection must not leave a per-player judge slot in flight");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void server_submission_speed_achievement_uses_server_solving_time(GameTestHelper helper) {
        ServerPlayer player = makeGameTestServerPlayer(helper);
        player.getInventory().clearContent();

        UUID playerUuid = player.getUUID();
        AchievementManager manager = AchievementManager.forPlayer(player);
        manager.loadPlayerAchievementsFromSet(playerUuid, Set.of());

        long submitTime = 1_786_219_200_000L;
        Problem spoofedClientTimeProblem = simpleServerJudgedProblem(
                "gametest-server-speed-spoof-" + Long.toUnsignedString(System.nanoTime()));

        SubmissionResult spoofedClientTimeResult = PacketSubmitSolution.submitVerifiedProblem(player, spoofedClientTimeProblem, """
                class Solution {
                    public int solve() {
                        return 1;
                    }
                }
                """, 1L, submitTime);

        assertTrue(spoofedClientTimeResult.isSuccess(),
                "correct code should still pass when the client spoofs a tiny solve time");
        assertFalse(manager.hasAchievement(playerUuid, AchievementRegistry.SPEED_DEMON),
                "client-supplied solveTimeMs must not award Speed Demon without a server-side solving session");

        Problem mismatchedSessionProblem = simpleServerJudgedProblem(
                "gametest-server-speed-mismatch-" + Long.toUnsignedString(System.nanoTime()));
        long mismatchedSessionStart = submitTime + 60_000L;
        long mismatchedSubmitTime = mismatchedSessionStart + 10_000L;
        SolvingPlayerManager.setSolving(player, true, spoofedClientTimeProblem.getId(), mismatchedSessionStart);

        SubmissionResult mismatchedSessionResult = PacketSubmitSolution.submitVerifiedProblem(player, mismatchedSessionProblem, """
                class Solution {
                    public int solve() {
                        return 1;
                    }
                }
                """, 1L, mismatchedSubmitTime);

        assertTrue(mismatchedSessionResult.isSuccess(),
                "correct code should pass even when the active solving session is for another problem");
        assertFalse(manager.hasAchievement(playerUuid, AchievementRegistry.SPEED_DEMON),
                "a server-side solving session for another problem must not award Speed Demon");

        Problem trustedServerTimeProblem = simpleServerJudgedProblem(
                "gametest-server-speed-trusted-" + Long.toUnsignedString(System.nanoTime()));
        long serverStartTime = submitTime + 180_000L;
        long serverSubmitTime = serverStartTime + 10_000L;
        SolvingPlayerManager.setSolving(player, true, trustedServerTimeProblem.getId(), serverStartTime);

        SubmissionResult trustedServerTimeResult = PacketSubmitSolution.submitVerifiedProblem(player, trustedServerTimeProblem, """
                class Solution {
                    public int solve() {
                        return 1;
                    }
                }
                """, 999_999_999L, serverSubmitTime);

        assertTrue(trustedServerTimeResult.isSuccess(),
                "correct code should pass when the server has a trusted solving session");
        assertTrue(manager.hasAchievement(playerUuid, AchievementRegistry.SPEED_DEMON),
                "server-measured solving time under 60 seconds should award Speed Demon even if client time is slow");

        SolvingPlayerManager.setSolving(player, false, trustedServerTimeProblem.getId(), serverSubmitTime + 1L);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void solving_session_is_problem_bound_and_expires(GameTestHelper helper) {
        ServerPlayer player = makeGameTestServerPlayer(helper);
        String problemId = "gametest-solving-session-" + Long.toUnsignedString(System.nanoTime());
        String otherProblemId = problemId + "-other";
        long startTime = System.currentTimeMillis();

        SolvingPlayerManager.setSolving(player, true, problemId, startTime);

        assertTrue(SolvingPlayerManager.isSolving(player),
                "fresh solving session should protect the real server player");
        assertEquals(5_000L, SolvingPlayerManager.getSolvingElapsedMs(player, problemId, startTime + 5_000L).orElse(-1L),
                "matching problem should expose the server-measured elapsed time");
        SolvingPlayerManager.setSolving(player, true, problemId, startTime + 55_000L);
        assertEquals(58_000L, SolvingPlayerManager.getSolvingElapsedMs(player, problemId, startTime + 58_000L).orElse(-1L),
                "duplicate same-problem solving start must not reset the server timer");
        assertFalse(SolvingPlayerManager.getSolvingElapsedMs(player, otherProblemId, startTime + 5_000L).isPresent(),
                "different problem id must not reuse another problem's solving session");

        long expiredTime = startTime + SolvingPlayerManager.MAX_SOLVING_SESSION_MS + 1L;
        assertFalse(SolvingPlayerManager.getSolvingElapsedMs(player, problemId, expiredTime).isPresent(),
                "expired solving session should not expose elapsed time");
        assertFalse(SolvingPlayerManager.isSolving(player),
                "expired solving session should be cleared from protection state");

        SolvingPlayerManager.setSolving(player, true, problemId, expiredTime + 1_000L);
        SolvingPlayerManager.setSolving(player, false, problemId, expiredTime + 2_000L);
        assertFalse(SolvingPlayerManager.isSolving(player),
                "explicit stop packet should clear protection state");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = ASYNC_SUBMISSION_TIMEOUT_TICKS,
            batch = "async_submission_success")
    public static void server_submission_async_path_applies_judged_result_on_server_thread(GameTestHelper helper) {
        ServerPlayer player = makeGameTestServerPlayer(helper);
        player.getInventory().clearContent();

        UUID playerUuid = player.getUUID();
        AlgoCraftSavedData data = AlgoCraftSavedData.get(player.serverLevel());
        Problem problem = simpleServerJudgedProblem("gametest-server-submit-async-" + Long.toUnsignedString(System.nanoTime()));

        int totalBefore = data.getTotalSolved(playerUuid);
        int easyBefore = data.getDifficultyCount(playerUuid, "easy");
        long startedNanos = System.nanoTime();

        CompletableFuture<SubmissionResult> future = PacketSubmitSolution.submitAsyncVerifiedProblem(player, problem, """
                class Solution {
                    public int solve() {
                        return 1;
                    }
                }
                """, 120_000L);

        helper.startSequence().thenWaitUntil(() ->
                assertTrue(future.isDone(), "async server judge should complete: " + asyncState(future, player, startedNanos)))
                .thenExecute(() -> {
            SubmissionResult result = future.join();
            assertTrue(result.isSuccess(), "async correct code should pass the real server Judge: "
                    + result.getMessage() + " " + result.getDetails().stream()
                    .map(detail -> detail.getError()).toList());
            assertTrue(data.isProblemSolved(playerUuid, problem.getId()),
                    "async accepted submission should record progress on the server thread");
            assertEquals(totalBefore + 1, data.getTotalSolved(playerUuid),
                    "async accepted submission should increment total solved once");
            assertEquals(easyBefore + 1, data.getDifficultyCount(playerUuid, "easy"),
                    "async accepted submission should increment the problem difficulty once");
            assertEquals(1, data.getConsecutiveCorrect(playerUuid),
                    "async accepted submission should increment consecutive-correct submissions");
            assertTrue(countTotalInventoryItems(player) > 0,
                    "async accepted submission should grant reward items after server-thread application");
        }).thenSucceed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = ASYNC_SUBMISSION_TIMEOUT_TICKS,
            batch = "async_submission_malformed_success")
    public static void server_submission_rejects_malformed_success_without_rewards(GameTestHelper helper) {
        ServerPlayer player = makeGameTestServerPlayer(helper);
        player.getInventory().clearContent();

        UUID playerUuid = player.getUUID();
        AlgoCraftSavedData data = AlgoCraftSavedData.get(player.serverLevel());
        Problem problem = simpleServerJudgedProblem("gametest-server-submit-malformed-success-"
                + Long.toUnsignedString(System.nanoTime()));

        int totalBefore = data.getTotalSolved(playerUuid);
        int easyBefore = data.getDifficultyCount(playerUuid, "easy");
        long startedNanos = System.nanoTime();

        CompletableFuture<SubmissionResult> future = PacketSubmitSolution.submitAsyncVerifiedProblemWithJudgeForGameTest(
                player,
                problem,
                """
                        class Solution {
                            public int solve() {
                                return 1;
                            }
                        }
                        """,
                120_000L,
                AlgoCraftGameTests::malformedSuccessfulSubmission);

        helper.succeedWhen(() -> {
            assertTrue(future.isDone(),
                    "malformed accepted judge result should complete: " + asyncState(future, player, startedNanos));
            SubmissionResult result = future.join();
            assertFalse(result.isSuccess(),
                    "malformed accepted judge result must be downgraded before rewards");
            assertEquals("Malformed judge result", result.getMessage(),
                    "malformed accepted judge result should report the server-side consistency failure");
            assertFalse(PacketSubmitSolution.hasPlayerSubmissionInFlightForGameTest(player),
                    "malformed accepted judge result should release the per-player judge slot");
            assertFalse(data.isProblemSolved(playerUuid, problem.getId()),
                    "malformed accepted judge result must not record solved progress");
            assertEquals(totalBefore, data.getTotalSolved(playerUuid),
                    "malformed accepted judge result must not increment total solved");
            assertEquals(easyBefore, data.getDifficultyCount(playerUuid, "easy"),
                    "malformed accepted judge result must not increment difficulty counters");
            assertEquals(0, countTotalInventoryItems(player),
                    "malformed accepted judge result must not grant reward items");
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = ASYNC_SUBMISSION_TIMEOUT_TICKS,
            batch = "async_submission_duplicate")
    public static void server_submission_async_duplicate_click_is_rejected_while_first_submission_runs(GameTestHelper helper) {
        ServerPlayer player = makeGameTestServerPlayer(helper);
        player.getInventory().clearContent();

        UUID playerUuid = player.getUUID();
        AlgoCraftSavedData data = AlgoCraftSavedData.get(player.serverLevel());
        Problem problem = simpleServerJudgedProblem("gametest-server-submit-async-duplicate-"
                + Long.toUnsignedString(System.nanoTime()));

        int totalBefore = data.getTotalSolved(playerUuid);
        int easyBefore = data.getDifficultyCount(playerUuid, "easy");
        long startedNanos = System.nanoTime();

        CompletableFuture<SubmissionResult> first = PacketSubmitSolution.submitAsyncVerifiedProblem(player, problem, """
                class Solution {
                    public int solve() {
                        long sink = 0L;
                        for (long i = 0L; i < 5_000_000L; i++) {
                            sink += i;
                        }
                        return sink >= 0L ? 1 : 0;
                    }
                }
                """, 120_000L);

        assertTrue(PacketSubmitSolution.hasPlayerSubmissionInFlightForGameTest(player),
                "first async submission should reserve the per-player judge slot before returning");

        CompletableFuture<SubmissionResult> second = PacketSubmitSolution.submitAsyncVerifiedProblem(player, problem, """
                class Solution {
                    public int solve() {
                        return 1;
                    }
                }
                """, 120_000L);

        assertTrue(second.isDone(),
                "duplicate async submission should be rejected immediately while the first judge is in flight");
        SubmissionResult duplicateResult = second.join();
        assertFalse(duplicateResult.isSuccess(),
                "duplicate async submission must not be accepted while a prior submit is running");
        assertEquals("Submission already running", duplicateResult.getMessage(),
                "duplicate async submission should report the per-player limiter reason");
        assertEquals(totalBefore, data.getTotalSolved(playerUuid),
                "duplicate async submission must not write solved progress before the first result applies");
        assertEquals(easyBefore, data.getDifficultyCount(playerUuid, "easy"),
                "duplicate async submission must not increment difficulty counters");
        assertEquals(0, countTotalInventoryItems(player),
                "duplicate async submission must not grant reward items");

        helper.succeedWhen(() -> {
            assertTrue(first.isDone(),
                    "first async submission should still complete after rejecting a duplicate: "
                            + asyncState(first, player, startedNanos));
            SubmissionResult firstResult = first.join();
            assertTrue(firstResult.isSuccess(), "first async correct code should pass the real server Judge");
            assertFalse(PacketSubmitSolution.hasPlayerSubmissionInFlightForGameTest(player),
                    "per-player judge slot should be released after the first async completion");
            assertTrue(data.isProblemSolved(playerUuid, problem.getId()),
                    "first async accepted submission should record progress after duplicate rejection");
            assertEquals(totalBefore + 1, data.getTotalSolved(playerUuid),
                    "duplicate rejection plus first success should increment total solved exactly once");
            assertEquals(easyBefore + 1, data.getDifficultyCount(playerUuid, "easy"),
                    "duplicate rejection plus first success should increment difficulty exactly once");
            assertTrue(countTotalInventoryItems(player) > 0,
                    "first async accepted submission should still grant reward items after duplicate rejection");
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = ASYNC_SUBMISSION_TIMEOUT_TICKS,
            batch = "async_submission_global_capacity")
    public static void server_submission_async_global_capacity_rejects_extra_player_and_recovers(GameTestHelper helper) {
        ServerPlayer firstPlayer = makeGameTestServerPlayer(helper);
        ServerPlayer secondPlayer = makeGameTestServerPlayer(helper);
        ServerPlayer thirdPlayer = makeGameTestServerPlayer(helper);
        ServerPlayer fourthPlayer = makeGameTestServerPlayer(helper);
        ServerPlayer rejectedPlayer = makeGameTestServerPlayer(helper);
        List<ServerPlayer> saturatedPlayers = List.of(firstPlayer, secondPlayer, thirdPlayer, fourthPlayer);
        for (ServerPlayer player : saturatedPlayers) {
            player.getInventory().clearContent();
        }
        rejectedPlayer.getInventory().clearContent();

        UUID rejectedPlayerUuid = rejectedPlayer.getUUID();
        AlgoCraftSavedData rejectedPlayerData = AlgoCraftSavedData.get(rejectedPlayer.serverLevel());
        Problem problem = simpleServerJudgedProblem("gametest-server-submit-global-capacity-"
                + Long.toUnsignedString(System.nanoTime()));
        int rejectedTotalBefore = rejectedPlayerData.getTotalSolved(rejectedPlayerUuid);
        int rejectedEasyBefore = rejectedPlayerData.getDifficultyCount(rejectedPlayerUuid, "easy");
        long startedNanos = System.nanoTime();

        String validCode = """
                class Solution {
                    public int solve() {
                        return 1;
                    }
                }
                """;
        CompletableFuture<SubmissionResult> controlledSaturation = new CompletableFuture<>();
        List<CompletableFuture<SubmissionResult>> saturatedFutures = saturatedPlayers.stream()
                .map(player -> PacketSubmitSolution.submitAsyncVerifiedProblemWithJudgeForGameTest(
                        player,
                        problem,
                        validCode,
                        120_000L,
                        controlledSaturation::join))
                .toList();
        for (ServerPlayer player : saturatedPlayers) {
            assertTrue(PacketSubmitSolution.hasPlayerSubmissionInFlightForGameTest(player),
                    "each saturation player should reserve a per-player judge slot before returning");
        }

        CompletableFuture<SubmissionResult> rejectedFuture =
                PacketSubmitSolution.submitAsyncVerifiedProblemWithJudgeForGameTest(
                        rejectedPlayer,
                        problem,
                        validCode,
                        120_000L,
                        AlgoCraftGameTests::successfulSubmission);

        assertTrue(rejectedFuture.isDone(),
                "fifth async submission should be rejected immediately while global judge capacity is full");
        SubmissionResult rejectedResult = rejectedFuture.join();
        assertFalse(rejectedResult.isSuccess(),
                "fifth async submission must not be accepted while global judge capacity is full");
        assertEquals("Server judge capacity is full", rejectedResult.getMessage(),
                "fifth async submission should report the global limiter reason");
        assertFalse(PacketSubmitSolution.hasPlayerSubmissionInFlightForGameTest(rejectedPlayer),
                "global-capacity rejection must release the rejected player's per-player judge slot");
        assertFalse(rejectedPlayerData.isProblemSolved(rejectedPlayerUuid, problem.getId()),
                "global-capacity rejection must not record solved progress");
        assertEquals(rejectedTotalBefore, rejectedPlayerData.getTotalSolved(rejectedPlayerUuid),
                "global-capacity rejection must not increment total solved");
        assertEquals(rejectedEasyBefore, rejectedPlayerData.getDifficultyCount(rejectedPlayerUuid, "easy"),
                "global-capacity rejection must not increment difficulty counters");
        assertEquals(0, countTotalInventoryItems(rejectedPlayer),
                "global-capacity rejection must not grant reward items");

        controlledSaturation.complete(failedSubmission("Controlled global capacity saturation released"));
        AtomicReference<CompletableFuture<SubmissionResult>> recoveryFutureRef = new AtomicReference<>();
        helper.succeedWhen(() -> {
            assertTrue(saturatedFutures.stream().allMatch(CompletableFuture::isDone),
                    "controlled saturating submissions should release global capacity: "
                            + asyncState(saturatedFutures, saturatedPlayers, startedNanos));
            for (int i = 0; i < saturatedFutures.size(); i++) {
                SubmissionResult result = saturatedFutures.get(i).join();
                assertFalse(result.isSuccess(),
                        "saturating submission " + i + " should fail instead of awarding progress");
                assertFalse(PacketSubmitSolution.hasPlayerSubmissionInFlightForGameTest(saturatedPlayers.get(i)),
                        "saturating submission " + i + " should release its per-player judge slot");
            }

            if (recoveryFutureRef.get() == null) {
                recoveryFutureRef.set(PacketSubmitSolution.submitAsyncVerifiedProblemWithJudgeForGameTest(
                        rejectedPlayer,
                        problem,
                        validCode,
                        120_000L,
                        AlgoCraftGameTests::successfulSubmission));
            }
            CompletableFuture<SubmissionResult> recoveryFuture = recoveryFutureRef.get();
            assertTrue(recoveryFuture.isDone(),
                    "normal submission should complete after global capacity is released: "
                            + asyncState(recoveryFuture, rejectedPlayer, startedNanos));
            SubmissionResult recoveryResult = recoveryFuture.join();
            assertTrue(recoveryResult.isSuccess(),
                    "normal submission should pass after global capacity is released");
            assertFalse(PacketSubmitSolution.hasPlayerSubmissionInFlightForGameTest(rejectedPlayer),
                    "recovered submission should release the rejected player's per-player judge slot");
            assertTrue(rejectedPlayerData.isProblemSolved(rejectedPlayerUuid, problem.getId()),
                    "recovered submission should record solved progress");
            assertEquals(rejectedTotalBefore + 1, rejectedPlayerData.getTotalSolved(rejectedPlayerUuid),
                    "global-capacity rejection plus recovery should increment total solved exactly once");
            assertEquals(rejectedEasyBefore + 1, rejectedPlayerData.getDifficultyCount(rejectedPlayerUuid, "easy"),
                    "global-capacity rejection plus recovery should increment difficulty exactly once");
            assertTrue(countTotalInventoryItems(rejectedPlayer) > 0,
                    "recovered submission should grant reward items after capacity is released");
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = ASYNC_SUBMISSION_TIMEOUT_TICKS,
            batch = "async_submission_failure_retry")
    public static void server_submission_async_failure_releases_limiter_before_completion_callback(GameTestHelper helper) {
        ServerPlayer player = makeGameTestServerPlayer(helper);
        player.getInventory().clearContent();

        UUID playerUuid = player.getUUID();
        AlgoCraftSavedData data = AlgoCraftSavedData.get(player.serverLevel());
        Problem problem = simpleServerJudgedProblem("gametest-server-submit-async-retry-" + Long.toUnsignedString(System.nanoTime()));

        int totalBefore = data.getTotalSolved(playerUuid);
        int easyBefore = data.getDifficultyCount(playerUuid, "easy");
        long startedNanos = System.nanoTime();

        CompletableFuture<SubmissionResult> first = PacketSubmitSolution.submitAsyncVerifiedProblem(player, problem, """
                class Solution {
                    public int solve() {
                        return 1
                    }
                }
                """, 120_000L);

        AtomicReference<SubmissionResult> firstResultRef = new AtomicReference<>();
        AtomicReference<Throwable> firstErrorRef = new AtomicReference<>();
        AtomicReference<String> firstStateViolationRef = new AtomicReference<>();
        AtomicReference<Boolean> playerLimiterReleasedBeforeCallbackRef = new AtomicReference<>();
        first.whenComplete((firstResult, firstError) -> {
            firstResultRef.set(firstResult);
            firstErrorRef.set(firstError);
            if (data.isProblemSolved(playerUuid, problem.getId())) {
                firstStateViolationRef.set("failed async code recorded progress");
            } else if (data.getTotalSolved(playerUuid) != totalBefore) {
                firstStateViolationRef.set("failed async code incremented total solved");
            } else if (data.getDifficultyCount(playerUuid, "easy") != easyBefore) {
                firstStateViolationRef.set("failed async code incremented difficulty counters");
            } else if (countTotalInventoryItems(player) != 0) {
                firstStateViolationRef.set("failed async code granted reward items");
            }
            playerLimiterReleasedBeforeCallbackRef.set(!PacketSubmitSolution.hasPlayerSubmissionInFlightForGameTest(player));
        });

        helper.succeedWhen(() -> {
            assertTrue(first.isDone(), "failed async code should complete: " + asyncState(first, player, startedNanos));
            assertTrue(firstErrorRef.get() == null,
                    "broken async code should complete as a judged rejection instead of an exceptional future");
            SubmissionResult firstResult = firstResultRef.get();
            assertNotNull(firstResult, "broken async code should produce a rejected submission result");
            assertFalse(firstResult.isSuccess(),
                    "broken async code should fail before any reward state is applied");
            assertTrue(firstStateViolationRef.get() == null,
                    firstStateViolationRef.get() == null ? "failed async code left reward state untouched" : firstStateViolationRef.get());
            assertEquals(Boolean.TRUE, playerLimiterReleasedBeforeCallbackRef.get(),
                    "failed submit future callback should observe a released per-player judge limiter");
        });
    }

    private static void assertBoundsStayInsideBlock(AABB bounds, Direction facing) {
        double epsilon = 1.0E-6;
        assertTrue(bounds.minX >= -epsilon && bounds.minY >= -epsilon && bounds.minZ >= -epsilon,
                "algorithm computer " + facing + " bounds underflow block space: " + bounds);
        assertTrue(bounds.maxX <= 1.0 + epsilon && bounds.maxY <= 1.0 + epsilon && bounds.maxZ <= 1.0 + epsilon,
                "algorithm computer " + facing + " bounds overflow block space: " + bounds);
    }

    private static int countTrophyStacks(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof TrophyItem) {
                count++;
            }
        }
        return count;
    }

    private static int countTotalInventoryItems(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            count += stack.getCount();
        }
        return count;
    }

    private static Problem simpleServerJudgedProblem(String id) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setTitle("Server judged test problem");
        problem.setDifficulty("easy");
        problem.setInitialCode("""
                class Solution {
                    public int solve() {
                        return 0;
                    }
                }
                """);
        problem.setExamples(List.of(testCase("", "1")));
        return problem;
    }

    private static Problem.TestCase testCase(String input, String output) {
        Problem.TestCase testCase = new Problem.TestCase();
        testCase.setInput(input);
        testCase.setOutput(output);
        return testCase;
    }

    private static SubmissionResult successfulSubmission() {
        SubmissionResult result = new SubmissionResult();
        result.setSuccess(true);
        result.setMessage("Accepted");
        result.setPassedCount(1);
        result.setTotalCount(1);
        return result;
    }

    private static SubmissionResult malformedSuccessfulSubmission() {
        SubmissionResult result = new SubmissionResult();
        result.setSuccess(true);
        result.setMessage("Accepted");
        result.setPassedCount(0);
        result.setTotalCount(1);
        return result;
    }

    private static SubmissionResult failedSubmission(String message) {
        SubmissionResult result = new SubmissionResult();
        result.setSuccess(false);
        result.setMessage(message);
        result.setTotalCount(1);
        return result;
    }

    private static String asyncState(CompletableFuture<SubmissionResult> future, ServerPlayer player, long startedNanos) {
        long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000L;
        return "elapsedMs=" + elapsedMs
                + ", done=" + future.isDone()
                + ", cancelled=" + future.isCancelled()
                + ", completedExceptionally=" + future.isCompletedExceptionally()
                + ", playerInFlight=" + PacketSubmitSolution.hasPlayerSubmissionInFlightForGameTest(player);
    }

    private static String asyncState(List<CompletableFuture<SubmissionResult>> futures,
                                     List<ServerPlayer> players,
                                     long startedNanos) {
        StringBuilder builder = new StringBuilder("elapsedMs=")
                .append((System.nanoTime() - startedNanos) / 1_000_000L);
        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<SubmissionResult> future = futures.get(i);
            ServerPlayer player = players.get(i);
            builder.append(", [").append(i)
                    .append(": done=").append(future.isDone())
                    .append(", cancelled=").append(future.isCancelled())
                    .append(", completedExceptionally=").append(future.isCompletedExceptionally())
                    .append(", playerInFlight=").append(PacketSubmitSolution.hasPlayerSubmissionInFlightForGameTest(player))
                    .append("]");
        }
        return builder.toString();
    }

    private static ServerPlayer makeGameTestServerPlayer(GameTestHelper helper) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-mock-player");
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                profile
        ) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player);
        return player;
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertNotNull(Object actual, String message) {
        assertTrue(actual != null, message);
    }

    private static void assertSame(Object expected, Object actual, String message) {
        assertTrue(expected == actual, message + " (expected same instance)");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        assertTrue(expected == null ? actual == null : expected.equals(actual),
                message + " (expected=" + expected + ", actual=" + actual + ")");
    }

    private static void assertEquals(int expected, int actual, String message) {
        assertTrue(expected == actual, message + " (expected=" + expected + ", actual=" + actual + ")");
    }

    private static void assertEquals(long expected, long actual, String message) {
        assertTrue(expected == actual, message + " (expected=" + expected + ", actual=" + actual + ")");
    }
}
