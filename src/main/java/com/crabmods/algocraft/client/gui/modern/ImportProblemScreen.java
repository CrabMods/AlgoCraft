package com.crabmods.algocraft.client.gui.modern;

import com.crabmods.algocraft.logic.ProblemManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class ImportProblemScreen extends Screen {
    public static final String FORMAT_GUIDE_URL = "https://github.com/CrabMods/AlgoCraft/blob/1.21.1/docs/PROBLEM_FORMAT.md";
    private final Screen parent;
    private EditBox inputField;
    private EditBox nameField;
    private Component status = Component.translatable("algocraft.gui.import.private_note");
    private int statusColor = IdeTheme.TEXT_MUTED;
    private int importType = 0; // 0: URL Repository, 1: Local File
    private Button typeButton;
    private Button importButton;
    private boolean importInProgress;
    private static BiFunction<String, String, CompletableFuture<Void>> remoteImportActionForSmokeTest;

    public ImportProblemScreen(Screen parent) {
        super(Component.translatable("algocraft.gui.import_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String savedName = this.nameField == null ? "" : this.nameField.getValue();
        String savedInput = this.inputField == null ? "" : this.inputField.getValue();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Responsive width - ensure elements fit on narrow screens
        int elementWidth = Math.min(200, this.width - 40);
        int halfWidth = elementWidth / 2;

        // Type Button
        this.typeButton = IdeButton.of(getTypeText(), button -> {
            importType = (importType + 1) % 2;
            button.setMessage(getTypeText());
            updateVisibility();
        }, centerX - halfWidth, centerY - 60, elementWidth, 20);
        this.addRenderableWidget(this.typeButton);

        // Name Field (for Repository Name)
        this.nameField = new EditBox(this.font, centerX - halfWidth, centerY - 30, elementWidth, 20, Component.translatable("algocraft.gui.import.name"));
        this.nameField.setMaxLength(128);
        this.nameField.setHint(Component.translatable("algocraft.gui.import.name_hint"));
        this.nameField.setValue(savedName);
        this.addRenderableWidget(this.nameField);

        // Input Field (URL or Path)
        this.inputField = new EditBox(this.font, centerX - halfWidth, centerY, elementWidth, 20, Component.translatable("algocraft.gui.import.input"));
        this.inputField.setMaxLength(2048);
        this.inputField.setValue(savedInput);
        this.addRenderableWidget(this.inputField);

        // Import Button
        this.importButton = IdeButton.primary(Component.translatable("algocraft.gui.import.do_import"), button -> {
            doImport();
        }, centerX - halfWidth, centerY + 30, elementWidth, 20);
        this.importButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("algocraft.gui.import.private_note")));
        this.importButton.active = !importInProgress;
        this.addRenderableWidget(this.importButton);

        // Help opens the official format on GitHub, with the normal external-link confirmation.
        int guideWidth = (elementWidth - 6) * 3 / 5;
        Button guideButton = IdeButton.subtle(Component.translatable("algocraft.gui.import.format"), button -> {
            this.minecraft.setScreen(new ConfirmLinkScreen(confirmed -> {
                if (confirmed) Util.getPlatform().openUri(FORMAT_GUIDE_URL);
                this.minecraft.setScreen(this);
            }, FORMAT_GUIDE_URL, true));
        }, centerX - halfWidth, centerY + 60, guideWidth, 20);
        guideButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("algocraft.gui.import.format_hint")));
        this.addRenderableWidget(guideButton);

        // Back Button
        this.addRenderableWidget(IdeButton.subtle(Component.translatable("algocraft.gui.import.back"), button -> {
            this.onClose();
        }, centerX - halfWidth + guideWidth + 6, centerY + 60, elementWidth - guideWidth - 6, 20));

        updateVisibility();
    }

    private Component getTypeText() {
        return switch (importType) {
            case 0 -> Component.translatable("algocraft.gui.import.mode.remote");
            case 1 -> Component.translatable("algocraft.gui.import.mode.local");
            default -> Component.translatable("algocraft.gui.import.mode.unknown");
        };
    }

    private void updateVisibility() {
        if (importType == 0) {
            nameField.visible = true;
            inputField.setHint(Component.translatable("algocraft.gui.import.url_hint"));
        } else {
            nameField.visible = false;
            inputField.setHint(Component.translatable("algocraft.gui.import.file_hint"));
        }
    }

    private void doImport() {
        if (importInProgress) {
            return;
        }

        String input = inputField.getValue().trim();
        if (input.isEmpty()) {
            setStatus(Component.translatable("algocraft.gui.import.status.empty_input"));
            return;
        }

        if (importType == 0) {
            // Remote Repository
            String name = nameField.getValue().trim();
            if (name.isEmpty()) {
                setStatus(Component.translatable("algocraft.gui.import.status.empty_name"));
                return;
            }

            beginRemoteImport(name, input);
        } else {
            importLocalFile(input);
        }
    }

    private void beginRemoteImport(String name, String url) {
        importInProgress = true;
        if (importButton != null) {
            importButton.active = false;
        }
        setStatus(Component.translatable("algocraft.gui.import.status.downloading"));

        CompletableFuture<Void> future;
        try {
            BiFunction<String, String, CompletableFuture<Void>> smokeAction = remoteImportActionForSmokeTest;
            future = isSmokeTestEnabled() && smokeAction != null
                    ? smokeAction.apply(name, url)
                    : ProblemManager.downloadAndUpdateRepository(name, url);
        } catch (Throwable e) {
            future = CompletableFuture.failedFuture(e);
        }

        future.thenRun(() -> runOnClientThread(this::finishRemoteImportSuccess))
                .exceptionally(e -> {
                    runOnClientThread(() -> finishRemoteImportFailure(e));
                    return null;
                });
    }

    private void finishRemoteImportSuccess() {
        importInProgress = false;
        if (importButton != null) {
            importButton.active = true;
        }
        setStatus(Component.translatable("algocraft.gui.import.status.success"));
    }

    private void finishRemoteImportFailure(Throwable error) {
        importInProgress = false;
        if (importButton != null) {
            importButton.active = true;
        }
        setStatus(Component.translatable("algocraft.gui.import.status.error", UiErrorMessages.fromThrowable(error)));
    }

    private void importLocalFile(String input) {
        File file = new File(input);
        if (!file.exists()) {
            setStatus(Component.translatable("algocraft.gui.import.status.file_not_found"));
            return;
        }

        Path backup = null;
        boolean hadExistingTarget = false;
        Path target = null;
        try {
            File userDir = new File(Minecraft.getInstance().gameDirectory, "algorithm_challenges/user");
            if (!userDir.exists()) userDir.mkdirs();

            target = userDir.toPath().resolve(file.getName()).normalize();
            hadExistingTarget = Files.exists(target);
            if (hadExistingTarget) {
                backup = Files.createTempFile(userDir.toPath(), ".algocraft-import-backup-", ".json");
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            ProblemManager.refreshAll();
            setStatus(Component.translatable("algocraft.gui.import.status.success"));
        } catch (Exception e) {
            rollbackLocalImportTarget(target, backup, hadExistingTarget, e);
            setStatus(Component.translatable("algocraft.gui.import.status.error", UiErrorMessages.fromThrowable(e)));
        } finally {
            if (backup != null) {
                try {
                    Files.deleteIfExists(backup);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void rollbackLocalImportTarget(Path target, Path backup, boolean hadExistingTarget, Exception original) {
        if (target == null) {
            return;
        }
        try {
            if (hadExistingTarget && backup != null && Files.exists(backup)) {
                Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(target);
            }
            ProblemManager.refreshAll();
        } catch (Exception rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }

    private void setStatus(Component status) {
        this.status = status;
        String key = status.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text ? text.getKey() : "";
        this.statusColor = key.endsWith(".success") ? IdeTheme.ACCENT : key.endsWith(".downloading") ? IdeTheme.INFO : IdeTheme.WARNING;
    }

    private static void runOnClientThread(Runnable action) {
        Minecraft minecraft = Minecraft.getInstance();
        if ((minecraft != null && minecraft.isSameThread()) || RenderSystem.isOnRenderThread()) {
            action.run();
        } else if (minecraft != null) {
            minecraft.execute(action);
        } else {
            action.run();
        }
    }

    public String getStatusTextForSmokeTest() {
        requireSmokeTest();
        return this.status.getString();
    }

    public boolean isImportInProgressForSmokeTest() {
        requireSmokeTest();
        return this.importInProgress;
    }

    public boolean isImportButtonActiveForSmokeTest() {
        requireSmokeTest();
        return this.importButton != null && this.importButton.active;
    }

    public boolean isNameFieldVisibleForSmokeTest() {
        requireSmokeTest();
        return this.nameField != null && this.nameField.visible;
    }

    public String getNameForSmokeTest() { requireSmokeTest(); return this.nameField.getValue(); }
    public String getInputForSmokeTest() { requireSmokeTest(); return this.inputField.getValue(); }

    public void setRemoteFieldsForSmokeTest(String name, String url) {
        requireSmokeTest();
        this.importType = 0;
        updateVisibility();
        this.nameField.setValue(name);
        this.inputField.setValue(url);
    }

    public void setLocalFileForSmokeTest(String path) {
        requireSmokeTest();
        this.importType = 1;
        updateVisibility();
        this.inputField.setValue(path);
    }

    public static void setRemoteImportActionForSmokeTest(BiFunction<String, String, CompletableFuture<Void>> action) {
        requireSmokeTest();
        remoteImportActionForSmokeTest = action;
    }

    public static void clearRemoteImportActionForSmokeTest() {
        remoteImportActionForSmokeTest = null;
    }

    private static void requireSmokeTest() {
        if (!isSmokeTestEnabled()) {
            throw new IllegalStateException("ImportProblemScreen smoke-test hooks are disabled");
        }
    }

    private static boolean isSmokeTestEnabled() {
        return Boolean.getBoolean("algocraft.ideSmokeTest");
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int centerY = this.height / 2;
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, Math.max(4, centerY - 78), IdeTheme.TEXT);
        if (status != null && !status.getString().isEmpty()) {
            int wrapWidth = Math.max(40, cardWidth() - 16);
            var lines = this.font.split(status, wrapWidth);
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                guiGraphics.drawCenteredString(this.font, lines.get(i), this.width / 2, centerY + 88 + i * 10, this.statusColor);
            }
        }
    }

    private int cardWidth() {
        return Math.min(200, this.width - 40) + 24;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, IdeTheme.BACKGROUND);
        int cardWidth = cardWidth();
        int top = Math.max(0, this.height / 2 - 86);
        int bottom = Math.min(this.height, this.height / 2 + 118);
        IdeTheme.frame(guiGraphics, this.width / 2 - cardWidth / 2, top, cardWidth, bottom - top, IdeTheme.PANEL, IdeTheme.BORDER);
        guiGraphics.fill(this.width / 2 - cardWidth / 2 + 1, top + 1, this.width / 2 + cardWidth / 2 - 1, top + 3, IdeTheme.ACCENT);
    }
    
    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
