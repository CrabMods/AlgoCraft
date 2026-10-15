package com.crabmods.algocraft.client.test;

import com.crabmods.algocraft.AlgoCraft;
import com.crabmods.algocraft.AlgorithmComputerBlock;
import com.crabmods.algocraft.client.ClientHooks;
import com.crabmods.algocraft.client.gui.modern.IdeLayoutEngine;
import com.crabmods.algocraft.client.gui.modern.ImportProblemScreen;
import com.crabmods.algocraft.client.gui.modern.ModernAlgorithmScreen;
import com.crabmods.algocraft.client.gui.modern.SubmissionHistoryScreen;
import com.crabmods.algocraft.client.gui.modern.UiErrorMessages;
import com.crabmods.algocraft.logic.Judge;
import com.crabmods.algocraft.logic.CodeExecutor;
import com.crabmods.algocraft.logic.Problem;
import com.crabmods.algocraft.logic.ProblemManager;
import com.crabmods.algocraft.logic.SubmissionResult;
import com.crabmods.algocraft.network.PacketSubmitSolution;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.RenderTickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class IdeClientSmokeTest {
    private static final String ENABLED_PROPERTY = "algocraft.ideSmokeTest";
    private static final int WIDE_WINDOW_WIDTH = 960;
    private static final int WIDE_WINDOW_HEIGHT = 540;
    private static final int NARROW_WINDOW_WIDTH = 640;
    private static final int NARROW_WINDOW_HEIGHT = 480;
    private static final String VALID_SUDOKU_SOLUTION = String.join("\n",
            "class Solution {",
            "    public boolean isValidSudoku(char[][] board) {",
            "        boolean[][] rows = new boolean[9][10];",
            "        boolean[][] cols = new boolean[9][10];",
            "        boolean[][] boxes = new boolean[9][10];",
            "        for (int r = 0; r < 9; r++) {",
            "            for (int c = 0; c < 9; c++) {",
            "                char ch = board[r][c];",
            "                if (ch == '.') {",
            "                    continue;",
            "                }",
            "                int digit = ch - '0';",
            "                int box = (r / 3) * 3 + c / 3;",
            "                if (rows[r][digit] || cols[c][digit] || boxes[box][digit]) {",
            "                    return false;",
            "                }",
            "                rows[r][digit] = true;",
            "                cols[c][digit] = true;",
            "                boxes[box][digit] = true;",
            "            }",
            "        }",
            "        return true;",
            "    }",
            "}");
    private static final String HOSTILE_OVERSIZED_WRONG_ANSWER_CODE = String.join("\n",
            "class Solution {",
            "    public String solve() {",
            "        return \"x\".repeat(8_000);",
            "    }",
            "}");
    private static final String BROKEN_COMPILATION_CODE = String.join("\n",
            "class Solution {",
            "    public boolean isValidSudoku(char[][] board) {",
            "        return true",
            "    }",
            "}");
    private static final String OVERSIZED_SUBMISSION_CODE = String.join("\n",
            "class Solution {",
            "    public boolean isValidSudoku(char[][] board) {",
            "        return true;",
            "    }",
            "}",
            "// " + "x".repeat(PacketSubmitSolution.MAX_CODE_LENGTH));
    private static final String HOSTILE_REMOTE_ERROR_TAIL = "REMOTE_ERROR_TAIL_" + "x".repeat(2_000);
    private static final String HOSTILE_OFFICIAL_UPDATE_ERROR =
            "manifest totalProblems mismatch " + HOSTILE_REMOTE_ERROR_TAIL;
    private static final String HOSTILE_IMPORT_ERROR =
            "manifest signature mismatch " + HOSTILE_REMOTE_ERROR_TAIL;
    private static final String LOCAL_IMPORT_PROBLEM_ID = "900001";
    private static final String LOCAL_IMPORT_INVALID_PROBLEM_ID = "900002";
    private static boolean registered;

    private final Gson gson = new Gson();
    private final Path outputDir;
    private final Path resultFile;
    private final Path questionBankRoot;
    private final String smokeProblemId;
    private final List<String> smokeProblemIds;
    private final int stressPasses;
    private final int maxTicks;
    private final List<String> checks = new ArrayList<>();
    private final List<Map<String, Object>> captures = new ArrayList<>();
    private final String smokeWorldId = "algocraft_ide_smoke_" + Long.toUnsignedString(System.nanoTime());

    private Phase phase = Phase.WAIT_READY;
    private int bankReviewIndex;
    private int bankLanguageIndex;
    private List<Problem> bankReviewProblems = List.of();
    private final List<String> reviewedProblemIds = new ArrayList<>();
    private int totalTicks;
    private int phaseTicks;
    private boolean finished;
    private boolean webOpenObserved;
    private ModernAlgorithmScreen ideScreen;
    private Problem smokeProblem;
    private SmokeProblemCase primarySmokeCase;
    private final List<SmokeProblemCase> representativeCases = new ArrayList<>();
    private int representativeCaseIndex;
    private CompletableFuture<Void> pendingOfficialUpdate;
    private int officialUpdateStartCount;
    private CompletableFuture<SubmissionResult> pendingSubmissionJudge;
    private int submissionJudgeStartCount;
    private CompletableFuture<Void> pendingRemoteImport;
    private int remoteImportStartCount;
    private String lastRemoteImportName;
    private String lastRemoteImportUrl;
    private boolean officialCacheSeeded;
    private int worldLoadCount;
    private boolean cleanupPassed;

    private IdeClientSmokeTest() {
        this.outputDir = Paths.get(System.getProperty("algocraft.ideSmokeOutputDir", "build/algocraft-ide-smoke"))
                .toAbsolutePath()
                .normalize();
        this.resultFile = this.outputDir.resolve("result.json");
        this.questionBankRoot = Paths.get(System.getProperty("algocraft.ideSmokeQuestionBank", "question_bank/official"))
                .toAbsolutePath()
                .normalize();
        this.smokeProblemId = System.getProperty("algocraft.ideSmokeProblemId", "7");
        this.smokeProblemIds = parseProblemIds(System.getProperty("algocraft.ideSmokeProblemIds", this.smokeProblemId));
        this.stressPasses = Math.max(1, Integer.getInteger("algocraft.ideSmokeStressPasses", 1));
        this.maxTicks = Math.max(900, Integer.getInteger("algocraft.ideSmokeMaxTicks", 900));
    }

    public static void maybeRegister() {
        if (!Boolean.getBoolean(ENABLED_PROPERTY) || registered) {
            return;
        }
        registered = true;
        IdeClientSmokeTest test = new IdeClientSmokeTest();
        ClientHooks.setWebIdeOpenObserverForSmokeTest(test::recordWebIdeOpen);
        ModernAlgorithmScreen.setOfficialUpdateActionForSmokeTest(test::createOfficialUpdateFuture);
        ModernAlgorithmScreen.setSubmissionJudgeActionForSmokeTest(test::createSubmissionJudgeFuture);
        ModernAlgorithmScreen.setExampleActionForSmokeTest((code, cases) ->
                CompletableFuture.completedFuture(CodeExecutor.executeBatch(code, cases)));
        ImportProblemScreen.setRemoteImportActionForSmokeTest(test::createRemoteImportFuture);
        MinecraftForge.EVENT_BUS.addListener(test::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(test::onRenderFramePost);
        AlgoCraft.LOGGER.info("AlgoCraft IDE client smoke test registered");
    }

    private void onClientTick(ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) return;
        if (finished) {
            return;
        }
        try {
            runTick(Minecraft.getInstance());
        } catch (Throwable t) {
            fail("Unhandled smoke-test exception: " + t.getClass().getSimpleName() + ": " + t.getMessage(), t);
        }
    }

    private void onRenderFramePost(RenderTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (finished) {
            return;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getOverlay() instanceof LoadingOverlay) {
                return;
            }
            if (phase == Phase.MODEL_SHOWCASE_WORLD && phaseTicks >= 20 && minecraft.screen == null) {
                require(minecraft.level != null && showcaseComputerPos != null
                                && minecraft.level.getBlockState(showcaseComputerPos).is(AlgoCraft.ALGORITHM_COMPUTER_BLOCK.get()),
                        "Model showcase computer reached the client world before its rendered capture");
                captureModelShowcase(minecraft, "model-computer-world");
                minecraft.setScreen(new ModelShowcaseScreen());
                setPhase(Phase.MODEL_SHOWCASE_ITEMS);
            } else if (phase == Phase.MODEL_SHOWCASE_ITEMS && phaseTicks >= 4
                    && minecraft.screen instanceof ModelShowcaseScreen) {
                captureModelShowcase(minecraft, "model-items-gui");
                minecraft.setScreen(null);
                minecraft.options.hideGui = this.hideGuiBeforeShowcase;
                openIdeThroughInWorldComputer(minecraft);
                setPhase(Phase.OPEN_IDE);
            } else if (phase == Phase.CAPTURE_WIDE && phaseTicks >= 8) {
                captureAndAssert(minecraft, "ide-wide", true);
                setPhase(Phase.BUTTON_SEQUENCE);
            } else if (phase == Phase.CAPTURE_ADVANCEMENTS && phaseTicks >= 12) {
                require(minecraft.screen instanceof net.minecraft.client.gui.screens.advancements.AdvancementsScreen,
                        "Vanilla Advancements screen is active for the AlgoCraft tree");
                try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                    Path screenshotPath = outputDir.resolve("algocraft-advancement-tree.png");
                    image.writeToFile(screenshotPath);
                    java.util.Set<Integer> colors = new java.util.HashSet<>();
                    for (int y = image.getHeight()/4; y < image.getHeight()*3/4; y+=3)
                        for (int x = image.getWidth()/4; x < image.getWidth()*3/4; x+=3)
                            colors.add(image.getPixelRGBA(x,y));
                    require(colors.size() > 32, "AlgoCraft vanilla advancement tree is rendered with visible detail");
                }
                pass("passed", "IDE and vanilla advancement client acceptance completed");
            } else if (phase == Phase.CAPTURE_SAMPLE && phaseTicks >= 8) {
                SmokeProblemCase sample = this.representativeCases.get(this.representativeCaseIndex);
                captureAndAssert(minecraft, "sample-p" + sample.problem().getId(), true);
                setPhase(Phase.SAMPLE_INTERACTION);
            } else if (phase == Phase.CAPTURE_NARROW && phaseTicks >= 8) {
                captureAndAssert(minecraft, "ide-narrow", false);
                setPhase(Phase.RESIZE_COMPACT);
            } else if (phase == Phase.CAPTURE_COMPACT && phaseTicks >= 8) {
                captureAndAssert(minecraft, "ide-scaled", false);
                setPhase(Phase.BANK_REVIEW);
            }
        } catch (Throwable t) {
            fail("Render smoke-test exception: " + t.getClass().getSimpleName() + ": " + t.getMessage(), t);
        }
    }

    private void runTick(Minecraft minecraft) throws IOException {
        UnattendedClientTestMode.verify();
        this.totalTicks++;
        this.phaseTicks++;
        if (this.totalTicks > this.maxTicks) {
            fail("Timed out in phase " + phase, null);
            return;
        }
        if (minecraft.getOverlay() instanceof LoadingOverlay || !minecraft.isRunning()) {
            return;
        }

        switch (phase) {
            case WAIT_READY -> {
                configureWindow(minecraft, WIDE_WINDOW_WIDTH, WIDE_WINDOW_HEIGHT);
                cleanSmokeLocalImportFixtures(FMLPaths.GAMEDIR.get());
                ProblemManager.getProblems();
                require(Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("algorithm_challenges/repos/official/manifest.json")),
                        "First installation loads the bundled official bank without a seeded cache");
                createTemporarySingleplayerWorld(minecraft);
                setPhase(Phase.WAIT_WORLD);
            }
            case WAIT_WORLD -> {
                if (!isTemporarySingleplayerWorldReady(minecraft) || !isComputerPlacementReady(minecraft)
                        || minecraft.screen != null) {
                    return;
                }
                require(minecraft.level != null && minecraft.player != null && minecraft.gameMode != null,
                        "Temporary singleplayer smoke world loaded for in-world IDE entry");
                worldLoadCount++;
                placeModelShowcase(minecraft);
                setPhase(Phase.MODEL_SHOWCASE_WORLD);
            }
            case MODEL_SHOWCASE_WORLD -> {
                // Capture after the actual world render, never a preceding loading-screen frame.
            }
            case MODEL_SHOWCASE_ITEMS -> {
                // The render callback owns the screenshot and the transition out of this screen.
            }
            case OPEN_IDE -> {
                if (phaseTicks < 6) {
                    return;
                }
                require(minecraft.screen instanceof ModernAlgorithmScreen,
                        "ModernAlgorithmScreen opened by right-clicking the in-world algorithm computer block");
                this.ideScreen = (ModernAlgorithmScreen) minecraft.screen;
                loadRepresentativeCases();
                this.primarySmokeCase = requirePrimarySmokeCase();
                this.smokeProblem = this.primarySmokeCase.problem();
                require(ProblemManager.getProblems().size() >= this.representativeCases.size(),
                        "IDE smoke loaded official cached problems through ProblemManager before screen interaction");
                this.ideScreen.selectProblemForSmokeTest(this.smokeProblem);
                require(this.ideScreen.getCurrentProblemForSmokeTest() != null, "Smoke problem selected through the real screen");
                require(!this.ideScreen.getCurrentProblemForSmokeTest().getVisuals().isEmpty(), "Smoke problem has structured diagram metadata");
                requireProblemImagesLoadedIfExpected(this.ideScreen, "Smoke problem");
                verifyIdeLayout(this.ideScreen, true);
                setPhase(Phase.CAPTURE_WIDE);
            }
            case BUTTON_SEQUENCE -> {
                for (int pass = 1; pass <= this.stressPasses; pass++) {
                    runButtonSequence(minecraft, this.primarySmokeCase, pass);
                }
                this.representativeCaseIndex = 0;
                setPhase(Phase.SAMPLE_SELECT);
            }
            case SAMPLE_SELECT -> {
                if (this.representativeCaseIndex >= this.representativeCases.size()) {
                    setPhase(Phase.RESIZE_NARROW);
                    return;
                }
                SmokeProblemCase sample = this.representativeCases.get(this.representativeCaseIndex);
                ModernAlgorithmScreen screen = requireIdeScreen(minecraft);
                screen.selectProblemForSmokeTest(sample.problem());
                require(sample.problem().getId().equals(screen.getCurrentProblemForSmokeTest().getId()),
                        "Representative problem p" + sample.problem().getId() + " selected in the real IDE");
                requireProblemImagesLoadedIfExpected(screen, "Representative problem p" + sample.problem().getId());
                verifyIdeLayout(screen, true);
                setPhase(Phase.CAPTURE_SAMPLE);
            }
            case SAMPLE_INTERACTION -> {
                SmokeProblemCase sample = this.representativeCases.get(this.representativeCaseIndex);
                runRepresentativeProblemSample(minecraft, sample);
                this.representativeCaseIndex++;
                setPhase(Phase.SAMPLE_SELECT);
            }
            case RESIZE_NARROW -> {
                require(minecraft.screen instanceof ModernAlgorithmScreen, "IDE screen survived modal button round-trips");
                this.ideScreen = (ModernAlgorithmScreen) minecraft.screen;
                Problem problemBeforeResize = this.ideScreen.getCurrentProblemForSmokeTest();
                String codeBeforeResize = this.ideScreen.getCodeTextForSmokeTest();
                configureWindow(minecraft, NARROW_WINDOW_WIDTH, NARROW_WINDOW_HEIGHT);
                if (minecraft.screen instanceof ModernAlgorithmScreen resized) {
                    this.ideScreen = resized;
                    require(this.ideScreen.getCurrentProblemForSmokeTest() != null
                                    && problemBeforeResize.getId().equals(this.ideScreen.getCurrentProblemForSmokeTest().getId()),
                            "Resize preserves the selected problem");
                    require(codeBeforeResize.equals(this.ideScreen.getCodeTextForSmokeTest()),
                            "Resize preserves the pasted solution text");
                }
                verifyIdeLayout(this.ideScreen, false);
                clickTopButton(this.ideScreen, IdeLayoutEngine.Control.TOGGLE_VIEW);
                require(this.ideScreen.isShowingDescriptionForSmokeTest(), "Narrow View button switches to statement mode");
                require(this.ideScreen.isProblemViewerVisibleForSmokeTest() && !this.ideScreen.isCodeEditorVisibleForSmokeTest(),
                        "Narrow statement mode shows the problem image panel without the editor");
                requireProblemImagesLoadedIfExpected(this.ideScreen, "Narrow statement mode");
                setPhase(Phase.CAPTURE_NARROW);
            }
            case CLOSE_IDE -> {
                require(minecraft.screen instanceof ModernAlgorithmScreen, "IDE screen is active before close-button smoke check");
                ModernAlgorithmScreen screen = (ModernAlgorithmScreen) minecraft.screen;
                clickTopButton(screen, IdeLayoutEngine.Control.CLOSE);
                require(!(minecraft.screen instanceof ModernAlgorithmScreen), "Close button exits the IDE screen");
                minecraft.options.guiScale().set(1);
                configureWindow(minecraft, WIDE_WINDOW_WIDTH, WIDE_WINDOW_HEIGHT);
                var server = minecraft.getSingleplayerServer();
                require(server != null && minecraft.player != null, "Integrated server is ready for vanilla advancement review");
                var playerId = minecraft.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    var manager = com.crabmods.algocraft.logic.AchievementManager.forPlayer(player);
                    manager.awardAchievement(player, "first_solve");
                    manager.syncAdvancements(player);
                });
                setPhase(Phase.WAIT_ADVANCEMENTS);
            }
            case RESIZE_COMPACT -> {
                ModernAlgorithmScreen screen = requireIdeScreen(minecraft);
                String code = screen.getCodeTextForSmokeTest();
                minecraft.options.guiScale().set(2);
                minecraft.resizeDisplay();
                screen = requireIdeScreen(minecraft);
                require(screen.width <= 330 && screen.height <= 250, "GUI scale 2 exercises the 320x240 compact IDE");
                require(code.equals(screen.getCodeTextForSmokeTest()), "GUI scale change preserves code");
                for (var button : IdeLayoutEngine.calculateTopButtons(screen.width)) {
                    assertClickableAt(screen, "Scaled " + button.control(), button.rect());
                }
                require(IdeLayoutEngine.calculateTopButtons(screen.width).size() == IdeLayoutEngine.Control.values().length,
                        "Scaled IDE exposes every toolbar control including View");
                this.bankReviewProblems = ProblemManager.getProblems().stream()
                        .filter(p -> p.getId().matches("[0-9]+"))
                        .sorted(Comparator.comparingInt(p -> Integer.parseInt(p.getId()))).toList();
                require(bankReviewProblems.size() == 500, "Whole-bank real client review includes exactly 500 official problems");
                setPhase(Phase.CAPTURE_COMPACT);
            }
            case BANK_REVIEW -> {
                if (bankReviewIndex >= bankReviewProblems.size()) {
                    bankReviewIndex = 0;
                    if (++bankLanguageIndex >= 2) {
                        minecraft.getLanguageManager().setSelected("en_us");
                        setPhase(Phase.PRODUCTION_RUN);
                        return;
                    }
                    minecraft.getLanguageManager().setSelected("zh_cn");
                }
                Problem problem = bankReviewProblems.get(bankReviewIndex++);
                ModernAlgorithmScreen screen = requireIdeScreen(minecraft);
                screen.selectProblemForSmokeTest(problem);
                require(screen.getMissingProblemImageBlockCountForSmokeTest() == 0,
                        "Whole-bank " + (bankLanguageIndex == 0 ? "en_us" : "zh_cn") + " p" + problem.getId() + " has no missing images");
                require(screen.getLoadedProblemImageBlockCountForSmokeTest() == problem.getVisuals().size(),
                        "Whole-bank p" + problem.getId() + " loads every declared image");
                var layout = IdeLayoutEngine.calculatePanels(screen.width, screen.height, screen.isShowingDescriptionForSmokeTest());
                require(layout.problem().bottom() <= screen.height - IdeLayoutEngine.consoleHeight(screen.height),
                        "Whole-bank p" + problem.getId() + " statement stays above console at GUI scale 2");
                reviewedProblemIds.add((bankLanguageIndex == 0 ? "en_us:" : "zh_cn:") + problem.getId());
            }
            case PRODUCTION_RUN -> {
                ModernAlgorithmScreen screen = requireIdeScreen(minecraft);
                if (screen.isShowingDescriptionForSmokeTest()) clickTopButton(screen, IdeLayoutEngine.Control.TOGGLE_VIEW);
                screen.selectProblemForSmokeTest(primarySmokeCase.problem());
                pasteSolutionAndVerifyFormatting(minecraft, screen, primarySmokeCase.solutionCode());
                ModernAlgorithmScreen.clearExampleActionForSmokeTest();
                clickTopButton(screen, IdeLayoutEngine.Control.RUN);
                require(screen.isRunInProgressForSmokeTest(), "Production Run returns to the client while example judge is pending");
                require(!screen.isSubmitButtonActiveForSmokeTest(), "Production Run disables concurrent Submit");
                clickTopButton(screen, IdeLayoutEngine.Control.TOGGLE_VIEW);
                require(screen.isShowingDescriptionForSmokeTest(), "Production Run keeps View responsive while example judge is pending");
                setPhase(Phase.WAIT_PRODUCTION_RUN);
            }
            case WAIT_PRODUCTION_RUN -> {
                ModernAlgorithmScreen screen = requireIdeScreen(minecraft);
                if (screen.isRunInProgressForSmokeTest()) return;
                require(screen.getConsoleTextForSmokeTest().contains("PASS"), "Production asynchronous Run passes real example execution");
                clickTopButton(screen, IdeLayoutEngine.Control.TOGGLE_VIEW);
                ModernAlgorithmScreen.clearSubmissionJudgeActionForSmokeTest();
                clickTopButton(screen, IdeLayoutEngine.Control.SUBMIT);
                require(screen.isSubmissionInProgressForSmokeTest(), "Production Submit returns to the client while real judge is pending");
                setPhase(Phase.WAIT_PRODUCTION_SUBMIT);
            }
            case WAIT_PRODUCTION_SUBMIT -> {
                ModernAlgorithmScreen screen = requireIdeScreen(minecraft);
                if (screen.isSubmissionInProgressForSmokeTest()) return;
                require(screen.getLastSmokeSubmissionResultForSmokeTest() != null
                        && screen.getLastSmokeSubmissionResultForSmokeTest().isSuccess(), "Production asynchronous Submit passes all official cases");
                String code = screen.getCodeTextForSmokeTest();
                screen.selectProblemForSmokeTest(bankReviewProblems.get(0));
                screen.selectProblemForSmokeTest(primarySmokeCase.problem());
                require(code.equals(screen.getCodeTextForSmokeTest()), "Switching away and back restores the independent problem draft");
                screen.onClose();
                minecraft.setScreen(new ModernAlgorithmScreen());
                screen = requireIdeScreen(minecraft);
                screen.selectProblemForSmokeTest(primarySmokeCase.problem());
                require(code.equals(screen.getCodeTextForSmokeTest()), "Closing and reopening IDE restores the saved problem draft");
                setPhase(Phase.CLOSE_IDE);
            }
            case WAIT_ADVANCEMENTS -> {
                var client = minecraft.player.connection.getAdvancements();
                var root = client.getAdvancements().get(new net.minecraft.resources.ResourceLocation("algocraft", "achievements/root"));
                if (root == null) return;
                require(root != null, "AlgoCraft root advancement reached the real client");
                minecraft.setScreen(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(client));
                client.setSelectedTab(root, true);
                setPhase(Phase.CAPTURE_ADVANCEMENTS);
            }
            case CAPTURE_ADVANCEMENTS -> { }
            case CAPTURE_WIDE, CAPTURE_SAMPLE, CAPTURE_NARROW, CAPTURE_COMPACT, FINISHED -> {
            }
        }
    }

    private void runButtonSequence(Minecraft minecraft, SmokeProblemCase sample, int pass) {
        this.checks.add("Starting IDE interaction pass " + pass + "/" + this.stressPasses);
        ModernAlgorithmScreen screen = requireIdeScreen(minecraft);

        pasteSolutionAndVerifyFormatting(minecraft, screen, sample.solutionCode());
        clickTopButton(screen, IdeLayoutEngine.Control.RUN);
        require(screen.getConsoleTextForSmokeTest().contains("Running Examples")
                        || screen.getConsoleTextForSmokeTest().contains("正在运行")
                        || screen.getConsoleTextForSmokeTest().contains(sample.problem().getTitle()),
                "Run button executes the example path and updates the console");
        String runConsole = screen.getConsoleTextForSmokeTest();
        require(runConsole.contains("PASS"),
                "Run button executes the pasted solution and reports PASS; console=" + compact(runConsole));
        require(!runConsole.contains("FAIL") && !runConsole.contains("Compilation failed"),
                "Run console has no failure after the pasted solution; console=" + compact(runConsole));

        clickTopButton(screen, IdeLayoutEngine.Control.TOGGLE_VIEW);
        require(screen.isShowingDescriptionForSmokeTest(), "View button switches to statement-only mode");
        require(screen.isProblemViewerVisibleForSmokeTest() && !screen.isCodeEditorVisibleForSmokeTest(),
                "Statement-only mode shows the problem panel and hides the editor");
        clickTopButton(screen, IdeLayoutEngine.Control.TOGGLE_VIEW);
        require(!screen.isShowingDescriptionForSmokeTest(), "View button returns to coding mode");

        if (pass == 1) {
            runPendingSubmissionSequence(minecraft, sample);
            screen = requireIdeScreen(minecraft);
        }

        clickTopButton(screen, IdeLayoutEngine.Control.SUBMIT);
        String submitConsole = screen.getConsoleTextForSmokeTest();
        SubmissionResult submitResult = screen.getLastSmokeSubmissionResultForSmokeTest();
        require(submitConsole.contains(ModernAlgorithmScreen.SMOKE_SUBMIT_CONSOLE),
                "Submit button runs the local judge without needing a server connection");
        require(submitResult != null && submitResult.isSuccess(),
                "Submit button accepts the pasted solution through Judge.grade");
        require(submitResult.getTotalCount() > 0 && submitResult.getPassedCount() == submitResult.getTotalCount(),
                "Submit button reports every official test case as passed");
        require(!submitConsole.contains("Submission Failed") && !submitConsole.contains("FAIL"),
                "Submit console has no failure after the pasted solution");

        if (pass == 1) {
            runWrongAnswerRecoverySequence(minecraft, sample);
            runCompilationErrorRecoverySequence(minecraft, sample);
            runOversizedSubmissionRejectionSequence(minecraft, sample);
            screen = requireIdeScreen(minecraft);
        }

        clickTopButton(screen, IdeLayoutEngine.Control.HISTORY);
        require(minecraft.screen instanceof SubmissionHistoryScreen, "History button opens submission history screen");
        clickRectAllowingSharedHit(minecraft.screen, "history back", new IdeLayoutEngine.Rect(minecraft.screen.width / 2 - 100, minecraft.screen.height - 30, 200, 20));
        require(minecraft.screen instanceof ModernAlgorithmScreen, "History back button returns to IDE");
        screen = (ModernAlgorithmScreen) minecraft.screen;
        requirePastedSolutionStillPresent(screen, "history round-trip", pass, sample);

        exerciseImportScreen(minecraft, screen, pass == 1);
        screen = requireIdeScreen(minecraft);
        requirePastedSolutionStillPresent(screen, "import round-trip", pass, sample);

        exerciseOfficialUpdate(minecraft, screen, pass, sample);

        this.webOpenObserved = false;
        clickTopButton(screen, IdeLayoutEngine.Control.WEB);
        require(this.webOpenObserved, "Web IDE button reaches the web-open action");
        requirePastedSolutionStillPresent(screen, "web action", pass, sample);
    }

    private void runPendingSubmissionSequence(Minecraft minecraft, SmokeProblemCase sample) {
        this.checks.add("Starting IDE pending-submit scenario");
        ModernAlgorithmScreen screen = requireIdeScreen(minecraft);
        pasteSolutionAndVerifyFormatting(minecraft, screen, sample.solutionCode());

        this.pendingSubmissionJudge = new CompletableFuture<>();
        int startsBefore = this.submissionJudgeStartCount;
        clickTopButton(screen, IdeLayoutEngine.Control.SUBMIT);
        require(this.submissionJudgeStartCount == startsBefore + 1,
                "Submit starts exactly one asynchronous judge action on click");
        require(screen.isSubmissionInProgressForSmokeTest(),
                "Submit enters an in-progress state while the judge future is pending");
        require(!screen.isSubmitButtonActiveForSmokeTest(),
                "Submit button is disabled while judge future is pending");
        require(isSubmittingConsole(screen.getConsoleTextForSmokeTest()),
                "Submit shows the translated submitting state while judge is pending");

        clickDisabledRectExpectingNoAction(screen, "submit duplicate click while judge pending",
                topButtonRect(screen, IdeLayoutEngine.Control.SUBMIT));
        require(this.submissionJudgeStartCount == startsBefore + 1,
                "Repeated Submit clicks do not start a second concurrent judge");

        clickTopButton(screen, IdeLayoutEngine.Control.TOGGLE_VIEW);
        require(screen.isShowingDescriptionForSmokeTest(),
                "View button remains responsive while submit judge is pending");
        clickTopButton(screen, IdeLayoutEngine.Control.TOGGLE_VIEW);
        require(!screen.isShowingDescriptionForSmokeTest(),
                "View button returns to coding mode while submit judge is pending");

        this.pendingSubmissionJudge.complete(Judge.grade(sample.problem(), sample.solutionCode()));
        require(!screen.isSubmissionInProgressForSmokeTest(),
                "Submit clears in-progress state after judge success");
        require(screen.isSubmitButtonActiveForSmokeTest(),
                "Submit button is re-enabled after judge success");
        SubmissionResult result = screen.getLastSmokeSubmissionResultForSmokeTest();
        require(result != null && result.isSuccess(),
                "Pending Submit applies the completed judge result");
        require(screen.getConsoleTextForSmokeTest().contains(ModernAlgorithmScreen.SMOKE_SUBMIT_CONSOLE),
                "Pending Submit writes the completed result to the IDE console");
        require(sample.solutionCode().equals(screen.getCodeTextForSmokeTest()),
                "Pending Submit does not mutate the user's code text");
        this.pendingSubmissionJudge = null;

        this.pendingSubmissionJudge = new CompletableFuture<>();
        startsBefore = this.submissionJudgeStartCount;
        clickTopButton(screen, IdeLayoutEngine.Control.SUBMIT);
        require(this.submissionJudgeStartCount == startsBefore + 1,
                "Submit starts exactly one asynchronous judge action before failed completion");
        require(screen.isSubmissionInProgressForSmokeTest(),
                "Submit enters an in-progress state before judge failure");
        require(!screen.isSubmitButtonActiveForSmokeTest(),
                "Submit button is disabled before judge failure");

        this.pendingSubmissionJudge.completeExceptionally(
                new IllegalStateException("smoke forced judge failure"));
        require(!screen.isSubmissionInProgressForSmokeTest(),
                "Submit clears in-progress state after judge failure");
        require(screen.isSubmitButtonActiveForSmokeTest(),
                "Submit button is re-enabled after judge failure");
        SubmissionResult failedResult = screen.getLastSmokeSubmissionResultForSmokeTest();
        require(failedResult != null && !failedResult.isSuccess(),
                "Pending Submit records a rejected result after judge failure");
        String failedConsole = screen.getConsoleTextForSmokeTest();
        require(failedConsole.contains(ModernAlgorithmScreen.SMOKE_SUBMIT_CONSOLE)
                        && isSubmissionFailedConsole(failedConsole)
                        && failedConsole.contains("smoke forced judge failure"),
                "Pending Submit writes a translated failure after judge failure");
        require(sample.solutionCode().equals(screen.getCodeTextForSmokeTest()),
                "Pending Submit failure does not mutate the user's code text");
        this.pendingSubmissionJudge = null;
    }

    private void createTemporarySingleplayerWorld(Minecraft minecraft) {
        LevelSettings settings = new LevelSettings(
                this.smokeWorldId,
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                WorldDataConfiguration.DEFAULT
        );
        minecraft.createWorldOpenFlows().createFreshLevel(
                this.smokeWorldId,
                settings,
                WorldOptions.defaultWithRandomSeed(),
                WorldPresets::createNormalWorldDimensions
        );
        require(minecraft.screen != null, "Temporary singleplayer smoke world creation entered Minecraft's loading flow");
    }

    private static boolean isTemporarySingleplayerWorldReady(Minecraft minecraft) {
        return minecraft.level != null
                && minecraft.player != null
                && minecraft.gameMode != null
                && minecraft.hasSingleplayerServer()
                && minecraft.getSingleplayerServer() != null
                && minecraft.getSingleplayerServer().isReady()
                && minecraft.getSingleplayerServer().getLevel(Level.OVERWORLD) != null;
    }

    private boolean isComputerPlacementReady(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return false;
        }
        ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(Level.OVERWORLD);
        if (serverLevel == null) {
            return false;
        }
        BlockPos computerPos = smokeComputerPos(minecraft);
        return minecraft.level.isLoaded(computerPos) && serverLevel.isLoaded(computerPos);
    }

    private void openIdeThroughInWorldComputer(Minecraft minecraft) {
        BlockPos computerPos = smokeComputerPos(minecraft);
        BlockState computerState = AlgoCraft.ALGORITHM_COMPUTER_BLOCK.get()
                .defaultBlockState()
                .setValue(AlgorithmComputerBlock.FACING, Direction.WEST);

        ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(Level.OVERWORLD);
        require(serverLevel != null, "Integrated server overworld exists before placing the algorithm computer");
        serverLevel.setBlock(computerPos, computerState, Block.UPDATE_ALL);
        require(serverLevel.getBlockState(computerPos).is(AlgoCraft.ALGORITHM_COMPUTER_BLOCK.get()),
                "Algorithm computer block was mirrored into the integrated server world for in-world entry");

        minecraft.level.setBlock(computerPos, computerState, Block.UPDATE_ALL);
        require(minecraft.level.getBlockState(computerPos).is(AlgoCraft.ALGORITHM_COMPUTER_BLOCK.get()),
                "Algorithm computer block was placed in the client world for in-world entry");

        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(computerPos), Direction.WEST, computerPos, false);
        InteractionResult result = minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        require(result.consumesAction(),
                "Algorithm computer useItemOn consumed the real client block interaction; result=" + result);
    }

    private boolean hideGuiBeforeShowcase;
    private BlockPos showcaseComputerPos;

    /** Places the computer in front of the camera so its model can be reviewed in a real render. */
    private void placeModelShowcase(Minecraft minecraft) {
        BlockPos computerPos = smokeComputerPos(minecraft);
        this.showcaseComputerPos = computerPos.immutable();
        ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(Level.OVERWORLD);
        require(serverLevel != null, "Integrated server overworld exists before the model showcase");
        BlockPos origin = minecraft.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 0, -2), origin.offset(3, 2, 2))) {
            serverLevel.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, -1, -2), origin.offset(3, -1, 2))) {
            serverLevel.setBlock(pos, net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        }
        serverLevel.setDayTime(6000L);
        BlockState computerState = AlgoCraft.ALGORITHM_COMPUTER_BLOCK.get()
                .defaultBlockState()
                .setValue(AlgorithmComputerBlock.FACING, Direction.WEST);
        serverLevel.setBlock(computerPos, computerState, Block.UPDATE_ALL);
        BlockPos trophyPos = computerPos.north();
        var trophyBlock = com.crabmods.algocraft.logic.ModTrophyBlocks.GOLD.get();
        serverLevel.setBlock(trophyPos, trophyBlock.defaultBlockState().setValue(com.crabmods.algocraft.TrophyBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
        if (serverLevel.getBlockEntity(trophyPos) instanceof com.crabmods.algocraft.TrophyBlockEntity trophy) {
            var achievement = com.crabmods.algocraft.logic.AchievementRegistry.getAll().stream()
                    .filter(a -> a.getTrophyItemId().equals("gold_trophy")).findFirst().orElseThrow();
            trophy.setTrophy(com.crabmods.algocraft.item.TrophyItem.createTrophy(
                    com.crabmods.algocraft.logic.ModItems.GOLD_TROPHY.get(), achievement, "Smoke Test", minecraft.player.getUUID().toString(), 1_780_000_000_000L));
        }
        double cameraX = origin.getX() + 0.5D;
        double cameraY = origin.getY();
        double cameraZ = origin.getZ() + 1.4D;
        Vec3 eye = new Vec3(cameraX, cameraY + minecraft.player.getEyeHeight(), cameraZ);
        Vec3 target = Vec3.atCenterOf(computerPos).add(0.0D, -0.05D, 0.0D);
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        var serverPlayer = minecraft.getSingleplayerServer().getPlayerList().getPlayer(minecraft.player.getUUID());
        require(serverPlayer != null, "Authoritative fixture player exists before showcase camera placement");
        minecraft.getSingleplayerServer().execute(() -> serverPlayer.connection.teleport(cameraX, cameraY, cameraZ, yaw, pitch));
        this.hideGuiBeforeShowcase = minecraft.options.hideGui;
        minecraft.options.hideGui = true;
    }

    private void captureModelShowcase(Minecraft minecraft, String name) throws IOException {
        require(minecraft.getOverlay() == null && (name.equals("model-computer-world")
                        ? minecraft.screen == null : minecraft.screen instanceof ModelShowcaseScreen),
                name + " capture observes its requested scene without a loading screen or overlay");
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            Path screenshotPath = outputDir.resolve(name + ".png");
            Files.createDirectories(outputDir);
            image.writeToFile(screenshotPath);
            int colors = countColors(image);
            require(colors >= 24, name + " model showcase rendered visible model detail; colors=" + colors);
            if (minecraft.screen instanceof ModelShowcaseScreen screen) assertTrophySlotBounds(image, screen);
        }
    }

    private void assertTrophySlotBounds(NativeImage image, ModelShowcaseScreen screen) {
        int scale = Math.max(2, Math.min(6, (screen.width - 20) / (ModelShowcaseScreen.ITEMS.length * 18)));
        int cell = 18 * scale, left = (screen.width - cell * ModelShowcaseScreen.ITEMS.length) / 2;
        int top = Math.max(4, (screen.height - cell * 2) / 2);
        double sx = image.getWidth() / (double) screen.width, sy = image.getHeight() / (double) screen.height;
        int background = image.getPixelRGBA(0, 0);
        for (int row = 0; row < 2; row++) for (int i = 1; i < ModelShowcaseScreen.ITEMS.length; i++) {
            int outside = 0;
            for (int y = (int)((top + row * cell) * sy); y < (top + (row + 1) * cell) * sy; y++) {
                for (int x = (int)((left + i * cell) * sx); x < (left + (i + 1) * cell) * sx; x++) {
                    boolean inIcon = x >= (left + i * cell + scale) * sx && x < (left + i * cell + 17 * scale) * sx
                            && y >= (top + row * cell + scale) * sy && y < (top + row * cell + 17 * scale) * sy;
                    if (!inIcon && image.getPixelRGBA(x, y) != background) outside++;
                }
            }
            require(outside == 0, "Actual rendered trophy stays inside its inventory icon: row=" + row + ", item=" + ModelShowcaseScreen.ITEMS[i] + ", outsidePixels=" + outside);
        }
    }

    private static int countColors(NativeImage image) {
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        for (int y = 0; y < image.getHeight(); y += 4) {
            for (int x = 0; x < image.getWidth(); x += 4) {
                colors.add(image.getPixelRGBA(x, y) & 0x00F0F0F0);
            }
        }
        return colors.size();
    }

    /** Renders the computer and every trophy tier as inventory icons at a readable scale. */
    private static final class ModelShowcaseScreen extends Screen {
        private static final String[] ITEMS = {
                "algorithm_computer", "bronze_trophy", "silver_trophy", "gold_trophy", "diamond_trophy", "netherite_trophy"
        };

        private ModelShowcaseScreen() {
            super(net.minecraft.network.chat.Component.translatable("itemGroup.algocraft"));
        }

        @Override
        public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics) {
            graphics.fill(0, 0, this.width, this.height, 0xFF8B8B8B);
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
            int scale = Math.max(2, Math.min(6, (this.width - 20) / (ITEMS.length * 18)));
            int cell = 18 * scale;
            int left = (this.width - cell * ITEMS.length) / 2;
            int top = Math.max(4, (this.height - cell * 2) / 2);
            for (int row = 0; row < 2; row++) {
                for (int i = 0; i < ITEMS.length; i++) {
                    net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                    new net.minecraft.resources.ResourceLocation("algocraft", ITEMS[i])));
                    if (row == 1 && i > 0) {
                        // Second row: an achievement variant of each tier.
                        int[] variants = {0, 202, 401, 106, 107, 404};
                        stack.getOrCreateTag().putInt("CustomModelData", variants[i]);
                    }
                    graphics.pose().pushPose();
                    graphics.pose().translate(left + i * cell + scale, top + row * cell + scale, 0);
                    graphics.pose().scale(scale, scale, 1);
                    graphics.renderItem(stack, 0, 0);
                    graphics.pose().popPose();
                }
            }
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private static BlockPos smokeComputerPos(Minecraft minecraft) {
        return minecraft.player.blockPosition().offset(2, 0, 0);
    }

    private void runRepresentativeProblemSample(Minecraft minecraft, SmokeProblemCase sample) {
        ModernAlgorithmScreen screen = requireIdeScreen(minecraft);
        require(sample.problem().getId().equals(screen.getCurrentProblemForSmokeTest().getId()),
                "Representative sample p" + sample.problem().getId() + " remains selected before interaction");
        pasteSolutionAndVerifyFormatting(minecraft, screen, sample.solutionCode());
        clickTopButton(screen, IdeLayoutEngine.Control.RUN);
        String runConsole = screen.getConsoleTextForSmokeTest();
        require(runConsole.contains("PASS"),
                "Representative sample p" + sample.problem().getId() + " Run passes examples; console=" + compact(runConsole));
        require(!runConsole.contains("FAIL") && !runConsole.contains("Compilation failed"),
                "Representative sample p" + sample.problem().getId() + " Run has no failures; console=" + compact(runConsole));

        clickTopButton(screen, IdeLayoutEngine.Control.SUBMIT);
        SubmissionResult result = screen.getLastSmokeSubmissionResultForSmokeTest();
        require(result != null && result.isSuccess(),
                "Representative sample p" + sample.problem().getId() + " Submit is accepted through Judge.grade; console="
                        + compact(screen.getConsoleTextForSmokeTest()));
        require(result.getTotalCount() > 0 && result.getPassedCount() == result.getTotalCount(),
                "Representative sample p" + sample.problem().getId() + " Submit passes every official test case");
        require(sample.solutionCode().equals(screen.getCodeTextForSmokeTest()),
                "Representative sample p" + sample.problem().getId() + " keeps pasted code after Run and Submit");
    }

    private void runWrongAnswerRecoverySequence(Minecraft minecraft, SmokeProblemCase sample) {
        this.checks.add("Starting IDE wrong-answer recovery scenario");
        ModernAlgorithmScreen screen = requireIdeScreen(minecraft);

        pasteCodeAndVerifyFormatting(minecraft, screen, HOSTILE_OVERSIZED_WRONG_ANSWER_CODE,
                "Hostile wrong-answer code");
        clickTopButton(screen, IdeLayoutEngine.Control.RUN);
        String failedRunConsole = screen.getConsoleTextForSmokeTest();
        require(failedRunConsole.contains("FAIL: Expected") && failedRunConsole.contains("[truncated]"),
                "Run button reports a bounded wrong-answer output; console=" + compact(failedRunConsole));
        require(!failedRunConsole.contains("x".repeat(2_500)),
                "Run console does not retain the oversized actual output in full");

        clickTopButton(screen, IdeLayoutEngine.Control.SUBMIT);
        SubmissionResult failedSubmit = screen.getLastSmokeSubmissionResultForSmokeTest();
        require(failedSubmit != null && !failedSubmit.isSuccess(),
                "Submit button rejects the hostile wrong-answer code through Judge.grade");
        require("Wrong Answer".equals(failedSubmit.getMessage()),
                "Hostile wrong-answer submit is classified as Wrong Answer, not a crash: " + failedSubmit.getMessage());
        SubmissionResult.TestCaseResult failedDetail = firstFailedDetail(failedSubmit);
        require(failedDetail != null, "Failed submit records at least one failed case detail");
        String actual = failedDetail.getActual();
        require(actual.contains("[truncated]"), "Failed submit detail keeps the actual output bounded");
        require(actual.length() < 2_100, "Failed submit actual output is safe for the in-game result panel");
        require(!actual.contains("x".repeat(2_500)),
                "Failed submit detail does not retain the oversized actual output in full");
        require(HOSTILE_OVERSIZED_WRONG_ANSWER_CODE.equals(screen.getCodeTextForSmokeTest()),
                "Failed submit does not mutate the user's code text");

        pasteSolutionAndVerifyFormatting(minecraft, screen, sample.solutionCode());
        clickTopButton(screen, IdeLayoutEngine.Control.RUN);
        String recoveredRunConsole = screen.getConsoleTextForSmokeTest();
        require(recoveredRunConsole.contains("PASS") && !recoveredRunConsole.contains("FAIL"),
                "Run button recovers after a failed oversized-output submission; console=" + compact(recoveredRunConsole));

        clickTopButton(screen, IdeLayoutEngine.Control.SUBMIT);
        SubmissionResult recoveredSubmit = screen.getLastSmokeSubmissionResultForSmokeTest();
        require(recoveredSubmit != null && recoveredSubmit.isSuccess(),
                "Submit button accepts the corrected solution after a failed submission");
        require(sample.solutionCode().equals(screen.getCodeTextForSmokeTest()),
                "Corrected solution remains in the editor after recovery submit");
    }

    private void runCompilationErrorRecoverySequence(Minecraft minecraft, SmokeProblemCase sample) {
        this.checks.add("Starting IDE compilation-error recovery scenario");
        ModernAlgorithmScreen screen = requireIdeScreen(minecraft);

        pasteCodeAndVerifyFormatting(minecraft, screen, BROKEN_COMPILATION_CODE,
                "Broken compilation code");
        clickTopButton(screen, IdeLayoutEngine.Control.RUN);
        String failedRunConsole = screen.getConsoleTextForSmokeTest();
        require(failedRunConsole.contains("Compilation failed"),
                "Run button reports compilation errors without crashing; console=" + compact(failedRunConsole));
        require(!failedRunConsole.contains("PASS"),
                "Run console does not report PASS for uncompilable code; console=" + compact(failedRunConsole));

        clickTopButton(screen, IdeLayoutEngine.Control.SUBMIT);
        String failedSubmitConsole = screen.getConsoleTextForSmokeTest();
        SubmissionResult failedSubmit = screen.getLastSmokeSubmissionResultForSmokeTest();
        require(failedSubmitConsole.contains(ModernAlgorithmScreen.SMOKE_SUBMIT_CONSOLE),
                "Submit button still uses the local judge for uncompilable code");
        require(failedSubmit != null && !failedSubmit.isSuccess(),
                "Submit button rejects uncompilable code through Judge.grade");
        require("Compilation Error".equals(failedSubmit.getMessage()),
                "Uncompilable submit is classified as Compilation Error, not a crash: " + failedSubmit.getMessage());
        SubmissionResult.TestCaseResult failedDetail = firstFailedDetail(failedSubmit);
        require(failedDetail != null && failedDetail.hasError(),
                "Compilation-error submit records an error detail");
        require(failedDetail.getError().contains("Compilation failed"),
                "Compilation-error detail includes the compiler failure");
        require(BROKEN_COMPILATION_CODE.equals(screen.getCodeTextForSmokeTest()),
                "Compilation-error submit does not mutate the user's code text");

        pasteSolutionAndVerifyFormatting(minecraft, screen, sample.solutionCode());
        clickTopButton(screen, IdeLayoutEngine.Control.RUN);
        String recoveredRunConsole = screen.getConsoleTextForSmokeTest();
        require(recoveredRunConsole.contains("PASS") && !recoveredRunConsole.contains("Compilation failed"),
                "Run button recovers after a compilation-error submission; console=" + compact(recoveredRunConsole));

        clickTopButton(screen, IdeLayoutEngine.Control.SUBMIT);
        SubmissionResult recoveredSubmit = screen.getLastSmokeSubmissionResultForSmokeTest();
        require(recoveredSubmit != null && recoveredSubmit.isSuccess(),
                "Submit button accepts the corrected solution after a compilation-error submission");
        require(sample.solutionCode().equals(screen.getCodeTextForSmokeTest()),
                "Corrected solution remains in the editor after compilation-error recovery submit");
    }

    private void runOversizedSubmissionRejectionSequence(Minecraft minecraft, SmokeProblemCase sample) {
        this.checks.add("Starting IDE oversized-submission rejection scenario");
        ModernAlgorithmScreen screen = requireIdeScreen(minecraft);

        pasteCodeOnlyAndVerifyFormatting(minecraft, screen, OVERSIZED_SUBMISSION_CODE,
                "Oversized submission code");
        clickTopButton(screen, IdeLayoutEngine.Control.SUBMIT);
        require(screen.getLastSmokeSubmissionResultForSmokeTest() == null,
                "Submit button rejects oversized code before invoking Judge.grade");
        String console = screen.getConsoleTextForSmokeTest();
        require(isSubmitCodeTooLargeConsole(console) && console.contains(String.valueOf(PacketSubmitSolution.MAX_CODE_LENGTH)),
                "Submit button shows the translated oversized-code rejection before network send; console=" + compact(console));
        require(OVERSIZED_SUBMISSION_CODE.equals(screen.getCodeTextForSmokeTest()),
                "Oversized submit rejection does not mutate the user's code text");

        pasteSolutionAndVerifyFormatting(minecraft, screen, sample.solutionCode());
        clickTopButton(screen, IdeLayoutEngine.Control.SUBMIT);
        SubmissionResult recoveredSubmit = screen.getLastSmokeSubmissionResultForSmokeTest();
        require(recoveredSubmit != null && recoveredSubmit.isSuccess(),
                "Submit button accepts corrected solution after oversized-code rejection; console=" + compact(screen.getConsoleTextForSmokeTest()));
    }

    private void requirePastedSolutionStillPresent(ModernAlgorithmScreen screen, String stage, int pass, SmokeProblemCase sample) {
        String actualCode = screen.getCodeTextForSmokeTest();
        String preview = actualCode == null ? "null" : actualCode.substring(0, Math.min(60, actualCode.length())).replace("\n", "\\n");
        require(sample.solutionCode().equals(actualCode),
                "Pasted solution survives " + stage + " in pass " + pass
                        + " (actual length=" + (actualCode == null ? -1 : actualCode.length()) + ", preview=" + preview + ")");
    }

    private void exerciseOfficialUpdate(Minecraft minecraft, ModernAlgorithmScreen screen, int pass, SmokeProblemCase sample) {
        this.pendingOfficialUpdate = new CompletableFuture<>();
        int startsBefore = this.officialUpdateStartCount;
        clickRect(screen, "update official sidebar", new IdeLayoutEngine.Rect(10, 60, IdeLayoutEngine.SIDEBAR_WIDTH - 20, 20));
        require(this.officialUpdateStartCount == startsBefore + 1,
                "Update Official starts exactly one refresh action on click");
        require(screen.isOfficialUpdateInProgressForSmokeTest(),
                "Update Official enters an in-progress state while the refresh future is pending");
        require(!screen.isUpdateOfficialButtonActiveForSmokeTest(),
                "Update Official button is disabled while a refresh is in progress");
        require(isOfficialUpdateRunningConsole(screen.getConsoleTextForSmokeTest()),
                "Update Official shows the translated running state in the console");

        clickDisabledRectExpectingNoAction(screen, "update official duplicate click",
                new IdeLayoutEngine.Rect(10, 60, IdeLayoutEngine.SIDEBAR_WIDTH - 20, 20));
        require(this.officialUpdateStartCount == startsBefore + 1,
                "Repeated Update Official clicks do not start a second concurrent refresh");

        this.pendingOfficialUpdate.complete(null);
        require(!screen.isOfficialUpdateInProgressForSmokeTest(),
                "Update Official clears in-progress state after success");
        require(screen.isUpdateOfficialButtonActiveForSmokeTest(),
                "Update Official button is re-enabled after success");
        require(isOfficialUpdateSuccessConsole(screen.getConsoleTextForSmokeTest()),
                "Update Official shows the translated success state in the console");
        requirePastedSolutionStillPresent(screen, "update official success", pass, sample);

        this.pendingOfficialUpdate = new CompletableFuture<>();
        startsBefore = this.officialUpdateStartCount;
        clickRect(screen, "update official sidebar failure", new IdeLayoutEngine.Rect(10, 60, IdeLayoutEngine.SIDEBAR_WIDTH - 20, 20));
        require(this.officialUpdateStartCount == startsBefore + 1,
                "Update Official can be started again after a successful refresh");
        require(screen.isOfficialUpdateInProgressForSmokeTest(),
                "Update Official enters an in-progress state before a failure");
        this.pendingOfficialUpdate.completeExceptionally(new IllegalStateException(HOSTILE_OFFICIAL_UPDATE_ERROR));
        require(!screen.isOfficialUpdateInProgressForSmokeTest(),
                "Update Official clears in-progress state after failure");
        require(screen.isUpdateOfficialButtonActiveForSmokeTest(),
                "Update Official button is re-enabled after failure");
        String updateFailureConsole = screen.getConsoleTextForSmokeTest();
        require(isOfficialUpdateErrorConsole(updateFailureConsole)
                        && updateFailureConsole.contains("manifest totalProblems mismatch"),
                "Update Official shows the translated failure state and the remote error message");
        requireBoundedExternalErrorText(updateFailureConsole, "Update Official failure console");
        requirePastedSolutionStillPresent(screen, "update official failure", pass, sample);
    }

    private void exerciseImportScreen(Minecraft minecraft, ModernAlgorithmScreen screen, boolean exerciseLocalImport) {
        clickRect(screen, "import sidebar", new IdeLayoutEngine.Rect(10, 82, IdeLayoutEngine.SIDEBAR_WIDTH - 20, 20));
        require(minecraft.screen instanceof ImportProblemScreen, "Import sidebar button opens import screen");
        ImportProblemScreen importScreen = (ImportProblemScreen) minecraft.screen;
        require(importScreen.isNameFieldVisibleForSmokeTest(), "Import screen opens in remote repository mode");

        clickImportSubmit(importScreen, "import remote empty submit");
        require(isImportEmptyInputStatus(importScreen.getStatusTextForSmokeTest()),
                "Import remote mode reports empty input through translated status text");

        clickImportTypeToggle(importScreen);
        require(!importScreen.isNameFieldVisibleForSmokeTest(), "Import type toggle switches to local file mode");
        clickImportSubmit(importScreen, "import local empty submit");
        require(isImportEmptyInputStatus(importScreen.getStatusTextForSmokeTest()),
                "Import local mode reports empty input through translated status text");

        if (exerciseLocalImport) {
            LocalImportFixture invalidFixture = writeInvalidLocalImportProblemFixture();
            int problemCountBeforeInvalidImport = ProblemManager.getProblems().size();
            importScreen.setLocalFileForSmokeTest(invalidFixture.path().toString());
            clickImportSubmit(importScreen, "import local invalid file submit");
            require(isImportErrorStatus(importScreen.getStatusTextForSmokeTest()),
                    "Import local invalid file reports translated failure status");
            require(!Files.exists(localImportTarget(invalidFixture)),
                    "Import local invalid file is not left in the user repository");
            require(ProblemManager.getProblem("user:" + invalidFixture.id()) == null,
                    "Import local invalid file does not publish a user-prefixed problem");
            require(ProblemManager.getProblems().size() == problemCountBeforeInvalidImport,
                    "Import local invalid file preserves the current ProblemManager cache");

            LocalImportFixture localFixture = writeLocalImportProblemFixture();
            importScreen.setLocalFileForSmokeTest(localFixture.path().toString());
            require(!importScreen.isNameFieldVisibleForSmokeTest(),
                    "Import local valid-file mode keeps the repository-name field hidden");
            clickImportSubmit(importScreen, "import local valid file submit");
            require(isImportSuccessStatus(importScreen.getStatusTextForSmokeTest()),
                    "Import local submit shows translated success status after a valid problem file");
            Path copiedProblem = localImportTarget(localFixture);
            require(Files.isRegularFile(copiedProblem),
                    "Import local submit copies a real problem JSON into the user repository");
            Problem importedProblem = ProblemManager.getProblem("user:" + localFixture.id());
            require(importedProblem != null && localFixture.title().equals(importedProblem.getTitle()),
                    "Import local submit refreshes ProblemManager with the user-prefixed problem");
        }

        clickImportTypeToggle(importScreen);
        require(importScreen.isNameFieldVisibleForSmokeTest(), "Import type toggle returns to remote repository mode");

        importScreen.setRemoteFieldsForSmokeTest("Smoke Remote", "https://example.test/smoke-course");
        int helpWidth = (Math.min(200, importScreen.width - 40) - 6) * 3 / 5;
        clickRect(importScreen, "official format guide", new IdeLayoutEngine.Rect(
                importScreen.width / 2 - Math.min(200, importScreen.width - 40) / 2,
                importScreen.height / 2 + 60, helpWidth, 20));
        require(minecraft.screen instanceof net.minecraft.client.gui.screens.ConfirmLinkScreen,
                "Official format guide asks before opening GitHub");
        Screen confirmation = minecraft.screen;
        var cancel = confirmation.children().stream()
                .filter(child -> child instanceof net.minecraft.client.gui.components.Button)
                .map(child -> (net.minecraft.client.gui.components.Button) child)
                .filter(button -> button.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("gui.cancel").getString()))
                .findFirst().orElseThrow();
        clickRect(confirmation, "cancel format link", new IdeLayoutEngine.Rect(
                cancel.getX(), cancel.getY(), cancel.getWidth(), cancel.getHeight()));
        require(minecraft.screen == importScreen && "Smoke Remote".equals(importScreen.getNameForSmokeTest())
                        && "https://example.test/smoke-course".equals(importScreen.getInputForSmokeTest()),
                "Returning from format help preserves the typed import draft");
        this.pendingRemoteImport = new CompletableFuture<>();
        int startsBefore = this.remoteImportStartCount;
        clickImportSubmit(importScreen, "import remote submit");
        require(this.remoteImportStartCount == startsBefore + 1,
                "Import remote submit starts exactly one repository install action");
        require("Smoke Remote".equals(this.lastRemoteImportName)
                        && "https://example.test/smoke-course".equals(this.lastRemoteImportUrl),
                "Import remote submit passes the typed name and URL to the repository installer");
        require(importScreen.isImportInProgressForSmokeTest(), "Import remote submit enters an in-progress state");
        require(!importScreen.isImportButtonActiveForSmokeTest(), "Import button is disabled while remote import is pending");
        require(isImportDownloadingStatus(importScreen.getStatusTextForSmokeTest()),
                "Import remote submit shows translated downloading status");
        clickDisabledRectExpectingNoAction(importScreen, "import duplicate remote submit", importButtonRect(importScreen));
        require(this.remoteImportStartCount == startsBefore + 1,
                "Repeated Import clicks do not start a second concurrent remote import");

        this.pendingRemoteImport.complete(null);
        require(!importScreen.isImportInProgressForSmokeTest(), "Import clears in-progress state after success");
        require(importScreen.isImportButtonActiveForSmokeTest(), "Import button is re-enabled after success");
        require(isImportSuccessStatus(importScreen.getStatusTextForSmokeTest()),
                "Import shows translated success status after remote import succeeds");

        importScreen.setRemoteFieldsForSmokeTest("Broken Remote", "https://example.test/broken-course");
        this.pendingRemoteImport = new CompletableFuture<>();
        startsBefore = this.remoteImportStartCount;
        clickImportSubmit(importScreen, "import remote submit failure");
        require(this.remoteImportStartCount == startsBefore + 1,
                "Import can be started again after a successful remote import");
        this.pendingRemoteImport.completeExceptionally(new IllegalStateException(HOSTILE_IMPORT_ERROR));
        require(!importScreen.isImportInProgressForSmokeTest(), "Import clears in-progress state after failure");
        require(importScreen.isImportButtonActiveForSmokeTest(), "Import button is re-enabled after failure");
        String importFailureStatus = importScreen.getStatusTextForSmokeTest();
        require(isImportErrorStatus(importFailureStatus)
                        && importFailureStatus.contains("manifest signature mismatch"),
                "Import shows translated failure status and the remote error message");
        requireBoundedExternalErrorText(importFailureStatus, "Import failure status");

        clickImportBack(importScreen);
        require(minecraft.screen instanceof ModernAlgorithmScreen, "Import back button returns to IDE");
    }

    private void requireBoundedExternalErrorText(String text, String label) {
        require(text.contains("[truncated]"), label + " marks oversized external error messages as truncated");
        require(!text.contains(HOSTILE_REMOTE_ERROR_TAIL),
                label + " does not retain the full external error payload");
        require(text.length() <= UiErrorMessages.MAX_ERROR_MESSAGE_CHARS + 120,
                label + " remains short enough for the in-game UI; length=" + text.length());
        require(!text.contains("\n") && !text.contains("\r"),
                label + " keeps external error text on one UI line");
    }

    private CompletableFuture<Void> createOfficialUpdateFuture() {
        this.officialUpdateStartCount++;
        return this.pendingOfficialUpdate != null ? this.pendingOfficialUpdate : CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<SubmissionResult> createSubmissionJudgeFuture(Problem problem, String code) {
        this.submissionJudgeStartCount++;
        if (this.pendingSubmissionJudge != null) {
            return this.pendingSubmissionJudge;
        }
        return CompletableFuture.completedFuture(Judge.grade(problem, code));
    }

    private CompletableFuture<Void> createRemoteImportFuture(String name, String url) {
        this.remoteImportStartCount++;
        this.lastRemoteImportName = name;
        this.lastRemoteImportUrl = url;
        return this.pendingRemoteImport != null ? this.pendingRemoteImport : CompletableFuture.completedFuture(null);
    }

    private LocalImportFixture writeLocalImportProblemFixture() {
        try {
            Files.createDirectories(outputDir);
            Path fixtureDir = outputDir.resolve("local-import");
            Files.createDirectories(fixtureDir);
            Path problemFile = fixtureDir.resolve("p" + LOCAL_IMPORT_PROBLEM_ID + ".json");
            String title = "Smoke Local Import " + smokeWorldId;

            JsonObject problem = new JsonObject();
            problem.addProperty("id", LOCAL_IMPORT_PROBLEM_ID);
            problem.addProperty("title", title);
            problem.addProperty("description", "# " + title + "\n\nReturn the input value unchanged.");
            problem.addProperty("difficulty", "EASY");
            problem.addProperty("initialCode", String.join("\n",
                    "class Solution {",
                    "    public int identity(int n) {",
                    "        return n;",
                    "    }",
                    "}"));

            JsonArray tags = new JsonArray();
            tags.add("Smoke");
            tags.add("Import");
            problem.add("tags", tags);

            JsonArray examples = new JsonArray();
            JsonObject example = new JsonObject();
            example.addProperty("input", "n = 42");
            example.addProperty("output", "42");
            examples.add(example);
            problem.add("examples", examples);

            JsonArray tests = new JsonArray();
            JsonObject test = new JsonObject();
            test.addProperty("input", "n = -7");
            test.addProperty("output", "-7");
            tests.add(test);
            problem.add("tests", tests);

            Files.writeString(problemFile, gson.toJson(problem), StandardCharsets.UTF_8);
            return new LocalImportFixture(problemFile, LOCAL_IMPORT_PROBLEM_ID, title);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create local import smoke fixture", e);
        }
    }

    private LocalImportFixture writeInvalidLocalImportProblemFixture() {
        try {
            Files.createDirectories(outputDir);
            Path fixtureDir = outputDir.resolve("local-import");
            Files.createDirectories(fixtureDir);
            Path problemFile = fixtureDir.resolve("p" + LOCAL_IMPORT_INVALID_PROBLEM_ID + ".json");
            String title = "Broken Smoke Local Import " + smokeWorldId;

            JsonObject problem = new JsonObject();
            problem.addProperty("id", LOCAL_IMPORT_INVALID_PROBLEM_ID);
            Files.writeString(problemFile, gson.toJson(problem), StandardCharsets.UTF_8);
            return new LocalImportFixture(problemFile, LOCAL_IMPORT_INVALID_PROBLEM_ID, title);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create invalid local import smoke fixture", e);
        }
    }

    private static Path localImportTarget(LocalImportFixture fixture) {
        return FMLPaths.GAMEDIR.get()
                .resolve("algorithm_challenges/user")
                .resolve(fixture.path().getFileName());
    }

    private static boolean isOfficialUpdateRunningConsole(String consoleText) {
        return consoleText.contains("Updating official repository") || consoleText.contains("正在更新");
    }

    private static boolean isOfficialUpdateSuccessConsole(String consoleText) {
        return consoleText.contains("Official repository updated") || consoleText.contains("官方题库已更新");
    }

    private static boolean isOfficialUpdateErrorConsole(String consoleText) {
        return consoleText.contains("Official update failed") || consoleText.contains("官方题库更新失败");
    }

    private static boolean isImportEmptyInputStatus(String statusText) {
        return statusText.contains("Input cannot be empty")
                || statusText.contains("\u8f93\u5165\u4e0d\u80fd\u4e3a\u7a7a");
    }

    private static boolean isImportDownloadingStatus(String statusText) {
        return statusText.contains("Downloading")
                || statusText.contains("\u6b63\u5728\u4e0b\u8f7d");
    }

    private static boolean isImportSuccessStatus(String statusText) {
        return (statusText.contains("Imported for personal practice") && statusText.contains("No server rewards"))
                || (statusText.contains("已导入个人练习题") && statusText.contains("不发放服务器奖励"));
    }

    private static boolean isImportErrorStatus(String statusText) {
        return statusText.contains("Error:")
                || statusText.contains("\u9519\u8bef");
    }

    private static boolean isSubmitCodeTooLargeConsole(String text) {
        return text.contains("exceeds the maximum length")
                || text.contains("\u8d85\u8fc7\u6700\u5927\u957f\u5ea6");
    }

    private static boolean isSubmittingConsole(String text) {
        return text.contains("Submitting")
                || text.contains("\u6b63\u5728\u63d0\u4ea4");
    }

    private static boolean isSubmissionFailedConsole(String text) {
        return text.contains("Submission Failed")
                || text.contains("\u63d0\u4ea4\u672a\u901a\u8fc7");
    }

    private void pasteSolutionAndVerifyFormatting(Minecraft minecraft, ModernAlgorithmScreen screen, String solutionCode) {
        pasteCodeAndVerifyFormatting(minecraft, screen, solutionCode, "Pasted solution");
    }

    private void pasteCodeAndVerifyFormatting(Minecraft minecraft, ModernAlgorithmScreen screen, String code, String label) {
        pasteCodeOnlyAndVerifyFormatting(minecraft, screen, code, label);
        verifyEditorSelectionReplacementUndoRedo(screen, code, label);
        verifyCaretEditing(minecraft, screen, code, label);
    }

    private void verifyCaretEditing(Minecraft minecraft, ModernAlgorithmScreen screen, String code, String label) {
        require(screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, GLFW.GLFW_MOD_CONTROL), label + " caret moves to document start");
        require(screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0) && screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0),
                label + " arrows move caret inside the first line");
        require(screen.charTyped('x', 0), label + " typed character reaches the focused editor");
        require((code.substring(0, 2) + "x" + code.substring(2)).equals(screen.getCodeTextForSmokeTest()),
                label + " typing inserts at the middle caret rather than document end");
        require(screen.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL), label + " undo mid-line insertion");
        require(code.equals(screen.getCodeTextForSmokeTest()), label + " undo restores mid-line text");
        require(screen.keyPressed(GLFW.GLFW_KEY_Y, 0, GLFW.GLFW_MOD_CONTROL), label + " redo mid-line insertion");
        require(screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0), label + " backspace acts at restored caret");
        require(code.equals(screen.getCodeTextForSmokeTest()), label + " backspace removes the mid-line character only");
        require(screen.keyPressed(GLFW.GLFW_KEY_DELETE, 0, 0), label + " Delete removes the character after the caret");
        require((code.substring(0, 2) + code.substring(3)).equals(screen.getCodeTextForSmokeTest()),
                label + " Delete respects mid-line caret");
        require(screen.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL), label + " undo restores Delete");
        screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, GLFW.GLFW_MOD_CONTROL);
        var editor = screen.children().stream()
                .filter(child -> child instanceof com.crabmods.algocraft.client.gui.component.CodeEditorWidget)
                .map(child -> (com.crabmods.algocraft.client.gui.component.CodeEditorWidget) child).findFirst().orElseThrow();
        double x = editor.getX() + 29 + minecraft.font.width(code.substring(0, 2));
        double y = editor.getY() + 5;
        require(screen.mouseClicked(x, y, 0), label + " mouse click positions the caret at a rendered glyph boundary");
        screen.mouseReleased(x, y, 0);
        require(screen.charTyped('x', 0), label + " typing follows clicked caret");
        require((code.substring(0, 2) + "x" + code.substring(2)).equals(screen.getCodeTextForSmokeTest()),
                label + " click-to-type inserts in the middle of the line");
        screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        require(code.equals(screen.getCodeTextForSmokeTest()), label + " caret regression leaves original runnable code");
    }

    private void pasteCodeOnlyAndVerifyFormatting(Minecraft minecraft, ModernAlgorithmScreen screen, String code, String label) {
        IdeLayoutEngine.PanelLayout layout = IdeLayoutEngine.calculatePanels(screen.width, screen.height, screen.isShowingDescriptionForSmokeTest());
        IdeLayoutEngine.Rect editor = layout.editor();
        clickRectAllowingSharedHit(screen, "code editor focus",
                new IdeLayoutEngine.Rect(editor.x() + 36, editor.y() + 4, 24, 12));
        setClipboardAndVerify(minecraft, code, label);

        boolean selected = screen.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
        require(selected, label + " Ctrl+A keybinding is handled by the focused editor");
        boolean pasted = screen.keyPressed(GLFW.GLFW_KEY_V, 0, GLFW.GLFW_MOD_CONTROL);
        require(pasted, label + " Ctrl+V keybinding pastes through the focused editor");

        String actualCode = screen.getCodeTextForSmokeTest();
        require(code.equals(actualCode), label + " is preserved exactly in the in-game editor");
        require(!actualCode.contains("\r"), label + " uses LF newlines in the editor");
        require(actualCode.contains("class "), label + " keeps the Java class declaration");
        require(actualCode.split("\n", -1).length == code.split("\n", -1).length,
                label + " preserves the expected line count");
    }

    private void verifyEditorSelectionReplacementUndoRedo(ModernAlgorithmScreen screen, String code, String label) {
        boolean selectedForEnter = screen.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
        require(selectedForEnter, label + " Ctrl+A selects the whole editor before Enter replacement");
        boolean entered = screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        require(entered, label + " Enter key is handled by the focused editor");
        require("\n".equals(screen.getCodeTextForSmokeTest()),
                label + " Enter over selected code replaces selected text instead of keeping stale code");

        boolean undo = screen.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
        require(undo, label + " Ctrl+Z keybinding is handled after selection replacement");
        require(code.equals(screen.getCodeTextForSmokeTest()),
                label + " Ctrl+Z restores the pasted code after selection replacement");

        boolean redo = screen.keyPressed(GLFW.GLFW_KEY_Y, 0, GLFW.GLFW_MOD_CONTROL);
        require(redo, label + " Ctrl+Y keybinding is handled after undo");
        require("\n".equals(screen.getCodeTextForSmokeTest()),
                label + " Ctrl+Y reapplies the selection replacement");

        boolean finalUndo = screen.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
        require(finalUndo, label + " final Ctrl+Z keybinding restores runnable code");
        require(code.equals(screen.getCodeTextForSmokeTest()),
                label + " final Ctrl+Z leaves the runnable pasted code in the editor");
    }

    private void setClipboardAndVerify(Minecraft minecraft, String code, String label) {
        String actual = "";
        for (int attempt = 1; attempt <= 8; attempt++) {
            minecraft.keyboardHandler.setClipboard(code);
            actual = minecraft.keyboardHandler.getClipboard();
            if (code.equals(actual)) {
                require(true, label + " reaches the Minecraft clipboard");
                return;
            }
            sleepClipboardRetry();
            actual = minecraft.keyboardHandler.getClipboard();
            if (code.equals(actual)) {
                require(true, label + " reaches the Minecraft clipboard after retry " + attempt);
                return;
            }
        }

        require(false, label + " reaches the Minecraft clipboard"
                + " (expected length=" + code.length()
                + ", actual length=" + (actual == null ? -1 : actual.length())
                + ", actual preview=" + compact(actual) + ")");
    }

    private static void sleepClipboardRetry() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static SubmissionResult.TestCaseResult firstFailedDetail(SubmissionResult result) {
        for (SubmissionResult.TestCaseResult detail : result.getDetails()) {
            if (!detail.isPassed()) {
                return detail;
            }
        }
        return null;
    }

    private ModernAlgorithmScreen requireIdeScreen(Minecraft minecraft) {
        require(minecraft.screen instanceof ModernAlgorithmScreen, "Minecraft is currently showing ModernAlgorithmScreen");
        this.ideScreen = (ModernAlgorithmScreen) minecraft.screen;
        return this.ideScreen;
    }

    private void requireProblemImagesLoadedIfExpected(ModernAlgorithmScreen screen, String context) {
        Problem selected = screen.getCurrentProblemForSmokeTest();
        if (selected == null || selected.getVisuals().isEmpty()) {
            return;
        }
        require(screen.getLoadedProblemImageBlockCountForSmokeTest() > 0,
                context + " statement widget loaded at least one diagram image block");
        require(screen.getMissingProblemImageBlockCountForSmokeTest() == 0,
                context + " statement widget has no missing-image placeholder blocks");
        if (screen.isProblemViewerVisibleForSmokeTest()) {
            require(screen.getVisibleLoadedProblemImageBlockCountForSmokeTest() > 0,
                    context + " statement widget has a visible loaded diagram image block");
        }
    }

    private void loadRepresentativeCases() throws IOException {
        this.representativeCases.clear();
        for (String problemId : this.smokeProblemIds) {
            this.representativeCases.add(loadSmokeCase(problemId));
        }
        require(!this.representativeCases.isEmpty(), "IDE smoke test has at least one representative problem");
    }

    private SmokeProblemCase requirePrimarySmokeCase() {
        for (SmokeProblemCase sample : this.representativeCases) {
            if (sample.problem().getId().equals(this.smokeProblemId)) {
                return sample;
            }
        }
        return this.representativeCases.get(0);
    }

    private SmokeProblemCase loadSmokeCase(String problemId) throws IOException {
        Path problemFile = questionBankRoot.resolve("p" + problemId + ".json");
        require(Files.isRegularFile(problemFile), "Smoke problem file exists: " + problemFile);
        JsonObject json = JsonParser.parseString(Files.readString(problemFile, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray solutions = json.getAsJsonArray("solutions");
        require(solutions != null && !solutions.isEmpty(), "Smoke problem p" + problemId + " has a reference solution");
        String solutionCode = solutions.get(0).getAsJsonObject().get("code").getAsString();
        require(solutionCode != null && !solutionCode.isBlank(), "Smoke problem p" + problemId + " reference solution is non-empty");
        Problem problem = ProblemManager.getProblem(problemId);
        require(problem != null, "Smoke problem p" + problemId + " loaded through ProblemManager official cache");
        require(problem.isValid(), "Smoke ProblemManager problem is valid: p" + problemId);
        require(problem.getAssetBaseDir() != null && !problem.getAssetBaseDir().equals(questionBankRoot),
                "Smoke problem p" + problemId + " uses the game official cache asset base, not direct question_bank injection");
        return new SmokeProblemCase(problem, solutionCode);
    }

    private void seedOfficialRepositoryCache() throws IOException {
        if (officialCacheSeeded) {
            return;
        }
        Path sourceRoot = questionBankRoot.toAbsolutePath().normalize();
        Path manifestPath = sourceRoot.resolve("manifest.json");
        require(Files.isRegularFile(manifestPath), "IDE smoke official fixture manifest exists: " + manifestPath);

        Path gameDir = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        cleanSmokeLocalImportFixtures(gameDir);
        Path officialCache = gameDir.resolve("algorithm_challenges").resolve("repos").resolve("official").normalize();
        require(officialCache.startsWith(gameDir), "IDE smoke official cache stays inside the game directory");

        Path parent = officialCache.getParent();
        Files.createDirectories(parent);
        Path staging = Files.createTempDirectory(parent, "official-smoke-cache-");
        boolean installed = false;
        try {
            copyOfficialManifestFile(sourceRoot, staging, "manifest.json");
            JsonObject manifest = JsonParser.parseString(Files.readString(manifestPath, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray files = manifest.getAsJsonArray("files");
            require(files != null && !files.isEmpty(), "IDE smoke official fixture manifest lists repository files");
            for (int i = 0; i < files.size(); i++) {
                JsonObject file = files.get(i).getAsJsonObject();
                String name = file.get("name").getAsString();
                copyOfficialManifestFile(sourceRoot, staging, name);
            }

            deleteRecursively(officialCache);
            Files.move(staging, officialCache, StandardCopyOption.REPLACE_EXISTING);
            installed = true;
            officialCacheSeeded = true;
            checks.add("IDE smoke seeded official repository cache from manifest fixture: " + officialCache);
        } finally {
            if (!installed) {
                deleteRecursively(staging);
            }
        }
    }

    private void copyOfficialManifestFile(Path sourceRoot, Path staging, String name) throws IOException {
        Path source = sourceRoot.resolve(name).normalize();
        Path target = staging.resolve(name).normalize();
        require(source.startsWith(sourceRoot), "IDE smoke fixture manifest file stays inside question bank: " + name);
        require(target.startsWith(staging), "IDE smoke staged manifest file stays inside cache staging: " + name);
        require(Files.isRegularFile(source), "IDE smoke fixture manifest file exists: " + name);
        Files.createDirectories(target.getParent());
        if (source.getFileName().toString().endsWith(".json")) {
            Files.writeString(target, normalizeJsonForManifestFixture(source), StandardCharsets.UTF_8);
        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String normalizeJsonForManifestFixture(Path source) throws IOException {
        return Files.readString(source, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace("\r", "\n");
    }

    private void cleanSmokeLocalImportFixtures(Path gameDir) throws IOException {
        Path userDir = gameDir.resolve("algorithm_challenges").resolve("user").normalize();
        if (!userDir.startsWith(gameDir) || !Files.isDirectory(userDir)) {
            return;
        }
        Files.deleteIfExists(userDir.resolve("p" + LOCAL_IMPORT_PROBLEM_ID + ".json"));
        Files.deleteIfExists(userDir.resolve("p" + LOCAL_IMPORT_INVALID_PROBLEM_ID + ".json"));
        checks.add("IDE smoke cleared stale local import fixtures from the user repository");
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private static List<String> parseProblemIds(String ids) {
        List<String> parsed = new ArrayList<>();
        for (String id : ids.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                parsed.add(trimmed);
            }
        }
        return parsed.isEmpty() ? List.of("7") : List.copyOf(parsed);
    }

    private void configureWindow(Minecraft minecraft, int width, int height) {
        require(RenderSystem.isOnRenderThread(), "Client smoke actions are running on the render thread");
        minecraft.options.guiScale().set(1);
        minecraft.getWindow().setWindowed(width, height);
        minecraft.resizeDisplay();
        if (minecraft.screen != null) {
            minecraft.screen.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        }
        require(minecraft.getWindow().getGuiScaledWidth() >= width - 8, "GUI width resized to " + width);
        require(minecraft.getWindow().getGuiScaledHeight() >= height - 8, "GUI height resized to " + height);
    }

    private void verifyIdeLayout(ModernAlgorithmScreen screen, boolean expectSplit) {
        require(screen.width > 0 && screen.height > 0, "IDE screen has positive dimensions");
        List<IdeLayoutEngine.ButtonLayout> buttons = IdeLayoutEngine.calculateTopButtons(screen.width);
        require(buttons.stream().anyMatch(button -> button.control() == IdeLayoutEngine.Control.CLOSE), "Close button is always present");

        for (int i = 0; i < buttons.size(); i++) {
            IdeLayoutEngine.ButtonLayout left = buttons.get(i);
            require(left.rect().x() >= IdeLayoutEngine.SIDEBAR_WIDTH, left.control() + " button is inside the top bar");
            require(left.rect().right() <= screen.width, left.control() + " button stays inside the screen");
            assertClickableAt(screen, left.control().name(), left.rect());
            for (int j = i + 1; j < buttons.size(); j++) {
                IdeLayoutEngine.ButtonLayout right = buttons.get(j);
                require(!left.rect().overlaps(right.rect()), left.control() + " and " + right.control() + " buttons do not overlap");
            }
        }

        assertClickableAt(screen, "Update Official", new IdeLayoutEngine.Rect(10, 60, IdeLayoutEngine.SIDEBAR_WIDTH - 20, 20));
        assertClickableAt(screen, "Import", new IdeLayoutEngine.Rect(10, 82, IdeLayoutEngine.SIDEBAR_WIDTH - 20, 20));
        if (expectSplit) {
            require(screen.isProblemViewerVisibleForSmokeTest() && screen.isCodeEditorVisibleForSmokeTest(),
                    "Wide IDE layout shows both problem statement and code editor");
        } else {
            require(!screen.isProblemViewerVisibleForSmokeTest() && screen.isCodeEditorVisibleForSmokeTest(),
                    "Narrow IDE layout defaults to the code editor without overlap");
        }
    }

    private void clickTopButton(ModernAlgorithmScreen screen, IdeLayoutEngine.Control control) {
        clickRect(screen, control.name(), topButtonRect(screen, control));
    }

    private static IdeLayoutEngine.Rect topButtonRect(ModernAlgorithmScreen screen, IdeLayoutEngine.Control control) {
        return IdeLayoutEngine.calculateTopButtons(screen.width).stream()
                .filter(button -> button.control() == control)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(control + " button is not visible at width " + screen.width))
                .rect();
    }

    private void clickImportTypeToggle(Screen screen) {
        int elementWidth = Math.min(200, screen.width - 40);
        int centerX = screen.width / 2;
        int centerY = screen.height / 2;
        clickRect(screen, "import type toggle", new IdeLayoutEngine.Rect(centerX - elementWidth / 2, centerY - 60, elementWidth, 20));
    }

    private void clickImportSubmit(Screen screen, String label) {
        clickRect(screen, label, importButtonRect(screen));
    }

    private static IdeLayoutEngine.Rect importButtonRect(Screen screen) {
        int elementWidth = Math.min(200, screen.width - 40);
        int centerX = screen.width / 2;
        int centerY = screen.height / 2;
        return new IdeLayoutEngine.Rect(centerX - elementWidth / 2, centerY + 30, elementWidth, 20);
    }

    private void clickImportBack(Screen screen) {
        int elementWidth = Math.min(200, screen.width - 40);
        int centerX = screen.width / 2;
        int centerY = screen.height / 2;
        int guideWidth = (elementWidth - 6) * 3 / 5;
        clickRect(screen, "import back", new IdeLayoutEngine.Rect(centerX - elementWidth / 2 + guideWidth + 6,
                centerY + 60, elementWidth - guideWidth - 6, 20));
    }

    private void clickRect(Screen screen, String label, IdeLayoutEngine.Rect rect) {
        assertClickableAt(screen, label, rect);
        clickRectAllowingSharedHit(screen, label, rect);
    }

    private void clickRectAllowingSharedHit(Screen screen, String label, IdeLayoutEngine.Rect rect) {
        double mouseX = rect.x() + rect.width() / 2.0;
        double mouseY = rect.y() + rect.height() / 2.0;
        boolean clicked = screen.mouseClicked(mouseX, mouseY, 0);
        screen.mouseReleased(mouseX, mouseY, 0);
        require(clicked, label + " click is consumed by a live widget");
    }

    private void clickDisabledRectExpectingNoAction(Screen screen, String label, IdeLayoutEngine.Rect rect) {
        double mouseX = rect.x() + rect.width() / 2.0;
        double mouseY = rect.y() + rect.height() / 2.0;
        boolean clicked = screen.mouseClicked(mouseX, mouseY, 0);
        screen.mouseReleased(mouseX, mouseY, 0);
        require(!clicked, label + " is ignored while the widget is disabled");
    }

    private void assertClickableAt(Screen screen, String label, IdeLayoutEngine.Rect rect) {
        double mouseX = rect.x() + rect.width() / 2.0;
        double mouseY = rect.y() + rect.height() / 2.0;
        int matches = 0;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && widget.active && child.isMouseOver(mouseX, mouseY)) {
                matches++;
            }
        }
        require(matches == 1, label + " has exactly one active clickable widget at its center");
    }

    private void captureAndAssert(Minecraft minecraft, String name, boolean expectWide) throws IOException {
        require(minecraft.screen instanceof ModernAlgorithmScreen, "IDE screen is active before " + name + " capture");
        ModernAlgorithmScreen screen = (ModernAlgorithmScreen) minecraft.screen;
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            Path screenshotPath = outputDir.resolve(name + ".png");
            Files.createDirectories(outputDir);
            image.writeToFile(screenshotPath);

            require(image.getWidth() >= screen.width, name + " framebuffer covers the GUI width");
            require(image.getHeight() >= screen.height, name + " framebuffer covers the GUI height");
            if (expectWide) {
                require(screen.width >= 760, name + " uses a wide GUI width");
            } else {
                require(screen.width < 760, name + " uses a narrow GUI width");
            }

            PixelStats stats = analyzeProblemStatementRegion(image, screen);
            int visualMetadataCount = screen.getCurrentProblemForSmokeTest().getVisuals().size();
            int loadedImageBlocks = screen.getLoadedProblemImageBlockCountForSmokeTest();
            int missingImageBlocks = screen.getMissingProblemImageBlockCountForSmokeTest();
            int visibleImageBlocks = screen.getVisibleLoadedProblemImageBlockCountForSmokeTest();
            Map<String, Object> capture = new LinkedHashMap<>();
            capture.put("name", name);
            capture.put("path", screenshotPath.toString());
            capture.put("framebufferWidth", image.getWidth());
            capture.put("framebufferHeight", image.getHeight());
            capture.put("guiWidth", screen.width);
            capture.put("guiHeight", screen.height);
            capture.put("sampledPixels", stats.sampledPixels());
            capture.put("uniqueColors", stats.uniqueColors());
            capture.put("nonDarkPixels", stats.nonDarkPixels());
            capture.put("visualMetadataCount", visualMetadataCount);
            capture.put("loadedImageBlocks", loadedImageBlocks);
            capture.put("missingImageBlocks", missingImageBlocks);
            capture.put("visibleImageBlocks", visibleImageBlocks);
            capture.put("editorVisible", screen.isCodeEditorVisibleForSmokeTest());
            if (screen.isCodeEditorVisibleForSmokeTest()) {
                PixelStats editorStats = analyzeEditorCodeRegion(image, screen);
                capture.put("editorSampledPixels", editorStats.sampledPixels());
                capture.put("editorUniqueColors", editorStats.uniqueColors());
                capture.put("editorNonDarkPixels", editorStats.nonDarkPixels());
                require(!screen.getCodeTextForSmokeTest().isBlank(), name + " code editor has non-empty code text to render");
                require(editorStats.sampledPixels() > 100, name + " samples the code editor text region");
                require(editorStats.uniqueColors() >= 4, name + " code editor screenshot has rendered syntax/text color variation");
                require(editorStats.nonDarkPixels() >= 40, name + " code editor screenshot has visible rendered text pixels");
            }
            captures.add(capture);

            require(stats.sampledPixels() > 100, name + " samples the problem statement panel region");
            if (visualMetadataCount == 0) {
                require(stats.nonDarkPixels() >= 80, name + " no-diagram problem statement has visible text pixels");
                require(stats.uniqueColors() >= 2, name + " no-diagram problem statement is not a flat background");
            } else {
                require(loadedImageBlocks > 0, name + " statement widget loaded at least one diagram image block");
                require(missingImageBlocks == 0, name + " statement widget has no missing-image placeholder blocks");
                require(visibleImageBlocks > 0, name + " statement widget has a visible loaded diagram image block");
                require(stats.uniqueColors() >= 14, name + " problem panel has enough color detail to prove an image rendered");
                require(stats.nonDarkPixels() >= 20, name + " problem panel is not just the dark placeholder/background");
            }
            checks.add(name + " screenshot captured and pixel-validated: " + screenshotPath);
        }
    }

    private PixelStats analyzeProblemStatementRegion(NativeImage image, ModernAlgorithmScreen screen) {
        IdeLayoutEngine.PanelLayout layout = IdeLayoutEngine.calculatePanels(screen.width, screen.height, screen.isShowingDescriptionForSmokeTest());
        IdeLayoutEngine.Rect problem = layout.problem();
        double scaleX = image.getWidth() / (double) screen.width;
        double scaleY = image.getHeight() / (double) screen.height;
        int x1 = scaled(problem.x() + 8, scaleX, image.getWidth());
        int y1 = scaled(problem.y() + 48, scaleY, image.getHeight());
        int x2 = scaled(problem.right() - 8, scaleX, image.getWidth());
        int y2 = scaled(Math.min(problem.y() + 220, problem.bottom() - 8), scaleY, image.getHeight());

        Map<Integer, Boolean> colors = new LinkedHashMap<>();
        int sampled = 0;
        int nonDark = 0;
        int stepX = Math.max(1, (x2 - x1) / 80);
        int stepY = Math.max(1, (y2 - y1) / 80);
        for (int y = y1; y < y2; y += stepY) {
            for (int x = x1; x < x2; x += stepX) {
                int pixel = image.getPixelRGBA(x, y);
                int bucketed = pixel & 0x00F8F8F8;
                colors.put(bucketed, Boolean.TRUE);
                sampled++;
                int c1 = pixel & 0xFF;
                int c2 = (pixel >> 8) & 0xFF;
                int c3 = (pixel >> 16) & 0xFF;
                if (Math.max(c1, Math.max(c2, c3)) > 90) {
                    nonDark++;
                }
            }
        }
        return new PixelStats(sampled, colors.size(), nonDark);
    }

    private PixelStats analyzeEditorCodeRegion(NativeImage image, ModernAlgorithmScreen screen) {
        IdeLayoutEngine.PanelLayout layout = IdeLayoutEngine.calculatePanels(screen.width, screen.height, screen.isShowingDescriptionForSmokeTest());
        IdeLayoutEngine.Rect editor = layout.editor();
        double scaleX = image.getWidth() / (double) screen.width;
        double scaleY = image.getHeight() / (double) screen.height;
        int x1 = scaled(editor.x() + 32, scaleX, image.getWidth());
        int y1 = scaled(editor.y() + 8, scaleY, image.getHeight());
        int x2 = scaled(editor.x() + Math.min(380, editor.width() - 8), scaleX, image.getWidth());
        int y2 = scaled(Math.min(editor.y() + 110, editor.bottom() - 8), scaleY, image.getHeight());

        Map<Integer, Boolean> colors = new LinkedHashMap<>();
        int sampled = 0;
        int nonDark = 0;
        int stepX = Math.max(1, (x2 - x1) / 80);
        int stepY = Math.max(1, (y2 - y1) / 50);
        for (int y = y1; y < y2; y += stepY) {
            for (int x = x1; x < x2; x += stepX) {
                int pixel = image.getPixelRGBA(x, y);
                int bucketed = pixel & 0x00F8F8F8;
                colors.put(bucketed, Boolean.TRUE);
                sampled++;
                int c1 = pixel & 0xFF;
                int c2 = (pixel >> 8) & 0xFF;
                int c3 = (pixel >> 16) & 0xFF;
                if (Math.max(c1, Math.max(c2, c3)) > 90) {
                    nonDark++;
                }
            }
        }
        return new PixelStats(sampled, colors.size(), nonDark);
    }

    private static int scaled(int guiCoordinate, double scale, int limit) {
        return Math.max(0, Math.min(limit - 1, (int) Math.round(guiCoordinate * scale)));
    }

    private void recordWebIdeOpen() {
        this.webOpenObserved = true;
    }

    private void setPhase(Phase next) {
        this.phase = next;
        this.phaseTicks = 0;
        AlgoCraft.LOGGER.info("AlgoCraft IDE smoke test phase: {}", next);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            fail(message, null);
            throw new IllegalStateException(message);
        }
        this.checks.add(message);
    }

    private static String compact(String text) {
        if (text == null) {
            return "";
        }
        String compacted = text.replace("\r", "\\r").replace("\n", "\\n");
        return compacted.length() > 500 ? compacted.substring(0, 500) + "..." : compacted;
    }

    private void pass(String status, String message) {
        require((pendingOfficialUpdate == null || pendingOfficialUpdate.isDone())
                        && (pendingSubmissionJudge == null || pendingSubmissionJudge.isDone())
                        && (pendingRemoteImport == null || pendingRemoteImport.isDone()),
                "Final cleanup has no pending fixture futures");
        clearTestHooks();
        net.minecraft.client.KeyMapping.releaseAll();
        UnattendedClientTestMode.clipboard = "";
        cleanupPassed = true;
        writeResult(status, message, null);
        finish(0);
    }

    private void fail(String message, Throwable throwable) {
        writeResult("failed", message, throwable);
        finish(1);
    }

    private void writeResult(String status, String message, Throwable throwable) {
        try {
            Files.createDirectories(outputDir);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", status);
            result.put("message", message);
            result.put("phase", phase.name());
            result.put("ticks", totalTicks);
            result.put("questionBankRoot", questionBankRoot.toString());
            result.put("smokeProblemId", smokeProblemId);
            result.put("smokeProblemIds", smokeProblemIds);
            result.put("stressPasses", stressPasses);
            result.put("checks", checks);
            result.put("captures", captures);
            result.put("reviewedProblemIds", reviewedProblemIds);
            if (status.equals("passed")) result.put("desktopIsolation", UnattendedClientTestMode.receipt());
            result.put("schemaVersion", 1);
            result.put("sessionId", System.getenv("ALGOCRAFT_TEST_SESSION_ID"));
            result.put("processStartCount", 1);
            result.put("worldLoadCount", worldLoadCount);
            result.put("scenarioCount", 1);
            result.put("failedCount", status.equals("passed") ? 0 : 1);
            result.put("cleanupFailureCount", cleanupPassed ? 0 : 1);
            result.put("allScenariosPassed", status.equals("passed") && cleanupPassed);
            result.put("requestedScenarios", List.of("ide-review"));
            result.put("observedScenarios", List.of("ide-review"));
            List<Map<String, Object>> artifacts = new ArrayList<>();
            for (var capture : captures) {
                Path path = Path.of(capture.get("path").toString());
                String hash = sha256(path);
                capture.put("sha256", hash);
                artifacts.add(Map.of("path", path.toString(), "sha256", hash));
            }
            result.put("scenarios", List.of(Map.of("id", "ide-review", "passed", status.equals("passed"),
                    "cleanupPassed", cleanupPassed, "startedAtTick", 0, "finishedAtTick", totalTicks, "artifacts", artifacts)));
            result.put("officialManifestSha256", sha256(questionBankRoot.resolve("manifest.json")));
            if (throwable != null) {
                result.put("exception", throwable.toString());
            }
            try (Writer writer = Files.newBufferedWriter(resultFile, StandardCharsets.UTF_8)) {
                gson.toJson(result, writer);
            }
        } catch (IOException e) {
            AlgoCraft.LOGGER.error("Failed to write IDE smoke-test result", e);
        }
    }

    private void finish(int exitCode) {
        if (finished) {
            return;
        }
        this.finished = true;
        this.phase = Phase.FINISHED;
        clearTestHooks();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.isRunning()) {
            minecraft.stop();
        }
        // Let Minecraft save its integrated world and close native resources on
        // the render thread. A timed System.exit races that shutdown on Windows.
        // The outer gate checks both the scenario receipt and the process exit.
    }

    private static void clearTestHooks() {
        ClientHooks.clearWebIdeOpenObserverForSmokeTest();
        ModernAlgorithmScreen.clearOfficialUpdateActionForSmokeTest();
        ModernAlgorithmScreen.clearSubmissionJudgeActionForSmokeTest();
        ModernAlgorithmScreen.clearExampleActionForSmokeTest();
        ImportProblemScreen.clearRemoteImportActionForSmokeTest();
    }

    private static String sha256(Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private enum Phase {
        WAIT_READY,
        WAIT_WORLD,
        MODEL_SHOWCASE_WORLD,
        MODEL_SHOWCASE_ITEMS,
        OPEN_IDE,
        CAPTURE_WIDE,
        BUTTON_SEQUENCE,
        SAMPLE_SELECT,
        CAPTURE_SAMPLE,
        SAMPLE_INTERACTION,
        RESIZE_NARROW,
        CAPTURE_NARROW,
        RESIZE_COMPACT,
        CAPTURE_COMPACT,
        BANK_REVIEW,
        PRODUCTION_RUN,
        WAIT_PRODUCTION_RUN,
        WAIT_PRODUCTION_SUBMIT,
        CLOSE_IDE,
        WAIT_ADVANCEMENTS,
        CAPTURE_ADVANCEMENTS,
        FINISHED
    }

    private record PixelStats(int sampledPixels, int uniqueColors, int nonDarkPixels) {
    }

    private record SmokeProblemCase(Problem problem, String solutionCode) {
    }

    private record LocalImportFixture(Path path, String id, String title) {
    }
}
