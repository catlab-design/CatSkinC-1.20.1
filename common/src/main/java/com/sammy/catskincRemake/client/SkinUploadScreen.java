package com.sammy.catskincRemake.client;

import com.mojang.authlib.GameProfile;
import com.sammy.catskincRemake.mixin.client.PlayerEntityAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SkinUploadScreen extends Screen {
    private static final int COLOR_BG = 0xFF11141D;
    private static final int COLOR_PANEL = 0xCC181C26;
    private static final int COLOR_PANEL_INNER = 0xAA121722;
    private static final int COLOR_SLOT = 0x80202838;
    private static final int COLOR_BORDER = 0x66D7E8FF;
    private static final int COLOR_HOVER = 0x334D83A6;
    private static final int COLOR_SELECTED = 0x5565C8FF;
    private static final int COLOR_TEXT = 0xFFF1F6FF;
    private static final int COLOR_TEXT_DIM = 0xFF9FB3C9;
    private static final int COLOR_DANGER = 0x66A94444;

    private static final DateTimeFormatter HISTORY_TIME_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT);

    private File selectedFile;
    private int selectedWidth;
    private int selectedHeight;

    private CheckboxWidget slimToggle;
    private ButtonWidget browseButton;
    private ButtonWidget uploadButton;

    private Identifier previewId;
    private NativeImageBackedTexture previewTexture;
    private OtherClientPlayerEntity previewPlayer;
    private boolean previewSlim;

    private int previewPanelX;
    private int previewPanelY;
    private int previewPanelW;
    private int previewPanelH;

    private int sidePanelX;
    private int sidePanelY;
    private int sidePanelW;
    private int sidePanelH;

    private int dropX;
    private int dropY;
    private int dropW;
    private int dropH;

    private int historyX;
    private int historyY;
    private int historyW;
    private int historyH;
    private int historyRowH;
    private int historyScroll;

    private final List<HistoryEntry> history = new ArrayList<>();

    public SkinUploadScreen() {
        super(Text.translatable("screen.catskinc-remake.title"));
    }

    @Override
    protected void init() {
        float scale = ConfigManager.get().uiScale;
        int margin = scaled(10, scale);
        int gap = scaled(10, scale);
        int titleBlockH = scaled(24, scale);
        int rowH = scaled(20, scale);
        int gapY = scaled(6, scale);
        int innerPad = scaled(10, scale);

        int contentY = margin + titleBlockH + scaled(4, scale);
        int contentH = this.height - contentY - margin;
        if (contentH < scaled(200, scale)) {
            contentH = scaled(200, scale);
            contentY = Math.max(margin, this.height - margin - contentH);
        }

        int minSideW = scaled(320, scale);
        previewPanelW = clamp((int) (this.width * 0.46F), scaled(280, scale),
                this.width - minSideW - (margin * 2) - gap);
        previewPanelX = margin;
        previewPanelY = contentY;
        previewPanelH = contentH;

        sidePanelX = previewPanelX + previewPanelW + gap;
        sidePanelY = contentY;
        sidePanelW = this.width - sidePanelX - margin;
        sidePanelH = contentH;

        int previewTop = previewPanelY + scaled(28, scale);
        int previewBottom = previewPanelY + previewPanelH - scaled(54, scale);
        int previewAvailW = previewPanelW - innerPad * 2;
        int previewAvailH = Math.max(scaled(140, scale), previewBottom - previewTop);
        int previewSize = clamp(Math.min(previewAvailW, previewAvailH), scaled(170, scale), scaled(420, scale));

        dropW = previewSize;
        dropH = previewSize;
        dropX = previewPanelX + (previewPanelW - dropW) / 2;
        dropY = previewTop + Math.max(0, (previewAvailH - dropH) / 2);

        int buttonX = sidePanelX + innerPad;
        int buttonW = sidePanelW - innerPad * 2;
        int buttonY = sidePanelY + scaled(26, scale);

        int actionsHeight = (rowH * 3) + (gapY * 2);
        historyX = sidePanelX + innerPad;
        historyY = buttonY + actionsHeight + scaled(18, scale);
        historyW = sidePanelW - innerPad * 2;
        historyH = Math.max(scaled(72, scale), (sidePanelY + sidePanelH - innerPad) - historyY);
        historyRowH = scaled(52, scale);

        clearChildren();
        loadHistory();
        historyScroll = clamp(historyScroll, 0, maxHistoryScroll());

        browseButton = ButtonWidget.builder(
                        Text.literal("Browse PNG..."),
                        button -> browseForSkinFile())
                .dimensions(buttonX, buttonY, buttonW, rowH)
                .build();
        addDrawableChild(browseButton);

        slimToggle = new CheckboxWidget(
                buttonX,
                buttonY + (rowH + gapY),
                buttonW,
                rowH,
                Text.translatable("screen.catskinc-remake.slim_model"),
                false
        );
        addDrawableChild(slimToggle);

        uploadButton = ButtonWidget.builder(
                        Text.translatable("screen.catskinc-remake.button.upload"),
                        button -> doUpload())
                .dimensions(buttonX, buttonY + (rowH + gapY) * 2, buttonW, rowH)
                .build();
        addDrawableChild(uploadButton);

        rebuildPreviewPlayer();
        updateActionState();
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        init();
    }

    @Override
    public void close() {
        disposePreview();
        disposeHistory();
        super.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, COLOR_BG);
        context.fill(0, 0, this.width, this.height / 3, 0x331C2638);

        context.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2, scaled(8, ConfigManager.get().uiScale), COLOR_TEXT);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Skin change + skin history"),
                this.width / 2, scaled(20, ConfigManager.get().uiScale), COLOR_TEXT_DIM);

        drawPanel(context, previewPanelX, previewPanelY, previewPanelW, previewPanelH);
        drawPanel(context, sidePanelX, sidePanelY, sidePanelW, sidePanelH);

        context.drawTextWithShadow(textRenderer, Text.literal("Preview"), previewPanelX + 10, previewPanelY + 8, COLOR_TEXT);
        context.drawTextWithShadow(textRenderer, Text.literal("Change Skin"), sidePanelX + 10, sidePanelY + 8, COLOR_TEXT);

        renderPreviewArea(context, mouseX, mouseY);
        renderHistoryList(context, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void filesDragged(List<Path> paths) {
        for (Path path : paths) {
            File file = path.toFile();
            if (file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                setSelectedFile(file, false);
                return;
            }
        }
        toastError("toast.error.drag_not_png");
        ModSounds.play(ModSounds.UI_ERROR);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return onMouseScrolled(mouseX, mouseY, amount) || super.mouseScrolled(mouseX, mouseY, amount);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return onMouseScrolled(mouseX, mouseY, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isInside((int) mouseX, (int) mouseY, dropX, dropY, dropW, dropH)) {
                browseForSkinFile();
                return true;
            }
            if (tryHandleHistoryClick(mouseX, mouseY)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderPreviewArea(DrawContext context, int mouseX, int mouseY) {
        boolean hover = isInside(mouseX, mouseY, dropX, dropY, dropW, dropH);
        context.fill(dropX, dropY, dropX + dropW, dropY + dropH, hover ? COLOR_HOVER : COLOR_SLOT);
        drawRectBorder(context, dropX, dropY, dropW, dropH, hover ? 0x99D7E8FF : COLOR_BORDER);

        MinecraftClient client = MinecraftClient.getInstance();
        boolean slim = slimToggle != null && slimToggle.isChecked();
        if (previewId != null && (previewPlayer == null || previewSlim != slim)) {
            rebuildPreviewPlayer();
        }
        LivingEntity entity = previewPlayer != null ? previewPlayer : client.player;
        if (entity != null) {
            int x1 = dropX + 2;
            int y1 = dropY + 2;
            int x2 = dropX + dropW - 2;
            int y2 = dropY + dropH - 2;

            context.enableScissor(x1, y1, x2, y2);
            context.getMatrices().push();
            context.getMatrices().translate(0.0D, 0.0D, 1_000.0D);
            InventoryEntityRendererCompat.drawEntity(context, x1, y1, x2, y2, mouseX, mouseY, entity);
            context.getMatrices().pop();
            context.disableScissor();
        }

        String selectedName = selectedFile == null ? "No skin selected" : selectedFile.getName();
        String detail = selectedFile == null
                ? "PNG, square, power-of-two size (64-4096)"
                : selectedWidth + "x" + selectedHeight + (slim ? " | Slim" : " | Classic");

        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.catskinc-remake.drop_hint"),
                dropX + dropW / 2, dropY + dropH - 30, COLOR_TEXT);
        context.drawTextWithShadow(textRenderer, Text.literal(selectedName), dropX + 8, previewPanelY + previewPanelH - 34, COLOR_TEXT);
        context.drawTextWithShadow(textRenderer, Text.literal(detail), dropX + 8, previewPanelY + previewPanelH - 20, COLOR_TEXT_DIM);
    }

    private void renderHistoryList(DrawContext context, int mouseX, int mouseY) {
        context.drawTextWithShadow(textRenderer, Text.literal("Recent Skins (" + history.size() + ")"), historyX, historyY - 14, COLOR_TEXT);
        context.fill(historyX, historyY, historyX + historyW, historyY + historyH, COLOR_PANEL_INNER);
        drawRectBorder(context, historyX, historyY, historyW, historyH, 0x44D7E8FF);

        int viewportX = historyX + 1;
        int viewportY = historyY + 1;
        int viewportW = historyW - 7;
        int viewportH = historyH - 2;

        if (history.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("No history yet. Add skin with Browse or drag-and-drop."),
                    historyX + historyW / 2, historyY + historyH / 2 - 4, COLOR_TEXT_DIM);
            return;
        }

        context.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);
        for (int i = 0; i < history.size(); i++) {
            int rowY = historyY + 1 + (i * historyRowH) - historyScroll;
            if (rowY + historyRowH < viewportY || rowY > viewportY + viewportH) {
                continue;
            }

            int rowX = historyX + 1;
            int rowW = viewportW - 2;
            HistoryEntry entry = history.get(i);

            boolean selected = selectedFile != null && selectedFile.equals(entry.file);
            boolean hover = isInside(mouseX, mouseY, rowX, rowY, rowW, historyRowH - 1);
            int bg = selected ? COLOR_SELECTED : (hover ? COLOR_HOVER : 0x2232455A);
            context.fill(rowX, rowY, rowX + rowW, rowY + historyRowH - 1, bg);

            if (entry.thumbId != null) {
                int thumbSize = 40;
                int thumbX = rowX + 6;
                int thumbY = rowY + (historyRowH - thumbSize) / 2;
                context.drawTexture(entry.thumbId, thumbX, thumbY, 0, 0, thumbSize, thumbSize, thumbSize, thumbSize);
            }

            int textX = rowX + 52;
            int deleteSize = 12;
            int deleteX = rowX + rowW - deleteSize - 6;
            int deleteY = rowY + 6;

            String name = ellipsis(entry.file.getName(), deleteX - textX - 6);
            context.drawTextWithShadow(textRenderer, Text.literal(name), textX, rowY + 8, COLOR_TEXT);
            String meta = entry.width + "x" + entry.height + " | " + formatHistoryTime(entry.modifiedAt);
            context.drawTextWithShadow(textRenderer, Text.literal(meta), textX, rowY + 24, COLOR_TEXT_DIM);

            boolean deleteHover = isInside(mouseX, mouseY, deleteX, deleteY, deleteSize, deleteSize);
            context.fill(deleteX, deleteY, deleteX + deleteSize, deleteY + deleteSize, deleteHover ? COLOR_DANGER : 0x33202020);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("X"), deleteX + deleteSize / 2, deleteY + 2, COLOR_TEXT);
        }
        context.disableScissor();

        drawHistoryScrollbar(context);
    }

    private void drawHistoryScrollbar(DrawContext context) {
        int max = maxHistoryScroll();
        if (max <= 0) {
            return;
        }

        int trackX = historyX + historyW - 5;
        int trackY = historyY + 2;
        int trackH = historyH - 4;

        float ratio = historyH / (float) (historyH + max);
        int thumbH = Math.max(18, Math.round(trackH * ratio));
        int thumbY = trackY + Math.round((historyScroll / (float) max) * (trackH - thumbH));

        context.fill(trackX, trackY, trackX + 3, trackY + trackH, 0x442A3545);
        context.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xAAD7E8FF);
    }

    private boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isInside((int) mouseX, (int) mouseY, historyX, historyY, historyW, historyH)) {
            return false;
        }
        int max = maxHistoryScroll();
        if (max <= 0) {
            return false;
        }
        historyScroll = clamp(historyScroll - (int) (amount * (historyRowH * 0.65F)), 0, max);
        return true;
    }

    private boolean tryHandleHistoryClick(double mouseX, double mouseY) {
        if (!isInside((int) mouseX, (int) mouseY, historyX, historyY, historyW, historyH)) {
            return false;
        }

        int index = (int) ((mouseY - historyY + historyScroll) / historyRowH);
        if (index < 0 || index >= history.size()) {
            return true;
        }

        int rowY = historyY + 1 + (index * historyRowH) - historyScroll;
        int rowX = historyX + 1;
        int rowW = historyW - 9;
        int deleteSize = 12;
        int deleteX = rowX + rowW - deleteSize - 6;
        int deleteY = rowY + 6;
        if (isInside((int) mouseX, (int) mouseY, deleteX, deleteY, deleteSize, deleteSize)) {
            deleteHistory(index);
            return true;
        }

        setSelectedFile(history.get(index).file, true);
        return true;
    }

    private void doUpload() {
        if (selectedFile == null) {
            toastError("toast.error.read_failed");
            ModSounds.play(ModSounds.UI_ERROR);
            return;
        }
        ModLog.debug("UI upload requested: file='{}', slim={}", selectedFile.getName(),
                slimToggle != null && slimToggle.isChecked());

        MinecraftClient client = MinecraftClient.getInstance();
        boolean slim = slimToggle != null && slimToggle.isChecked();
        Toasts.UploadToast toast = Toasts.showUpload(
                Text.translatable("toast.upload.title"),
                Text.translatable("toast.upload.preparing")
        );
        ModSounds.play(ModSounds.UI_UPLOAD);
        client.setScreen(null);

        UUID playerUuid = client.player == null ? null : client.player.getUuid();
        ServerApiClient.uploadSkinAsync(selectedFile, playerUuid, slim, new ServerApiClient.ProgressListener() {
            @Override
            public void onStart(long totalBytes) {
                client.execute(() -> toast.update(0.0F, Text.translatable("toast.upload.start", humanBytes(totalBytes)).getString()));
            }

            @Override
            public void onProgress(long sent, long total) {
                float progress = total > 0L ? (sent / (float) total) : 0.0F;
                client.execute(() -> toast.update(progress, ((int) (progress * 100.0F)) + "%"));
            }

            @Override
            public void onDone(boolean ok, String messageOrSkinId) {
                client.execute(() -> {
                    toast.complete(ok, Text.translatable(ok ? "toast.upload.success" : "toast.upload.failed").getString());
                    if (!ok) {
                        ModSounds.play(ModSounds.UI_ERROR);
                        Toasts.error(Text.translatable("title.skin_management"),
                                Text.literal(messageOrSkinId == null ? Text.translatable("toast.upload.failed").getString() : messageOrSkinId));
                        return;
                    }

                    ModSounds.play(ModSounds.UI_COMPLETE);
                    if (client.player != null) {
                        applyLocalSkinToSelf(client.player.getUuid(), slim);
                        ServerApiClient.selectSkin(client.player.getUuid(), messageOrSkinId);
                        SkinManagerClient.setSlim(client.player.getUuid(), slim);
                        SkinManagerClient.refresh(client.player.getUuid());
                    }
                });
            }
        });
    }

    private void applyLocalSkinToSelf(UUID uuid, boolean slim) {
        if (selectedFile == null || uuid == null) {
            return;
        }
        try {
            SkinOverrideStore.putManagedFromFile(uuid, selectedFile, slim);
        } catch (Exception exception) {
            ModLog.warn("Failed to apply local skin override for self: " + selectedFile, exception);
        }
    }

    private void browseForSkinFile() {
        try {
            String defaultPath = selectedFile != null ? selectedFile.getAbsolutePath() : historyDir().getAbsolutePath();
            String picked = TinyFileDialogs.tinyfd_openFileDialog(
                    "Select skin PNG",
                    defaultPath,
                    null,
                    "PNG image",
                    false
            );
            if (picked == null || picked.isBlank()) {
                ModLog.trace("File dialog cancelled");
                return;
            }
            setSelectedFile(new File(picked), false);
        } catch (Exception exception) {
            ModLog.error("File picker failed", exception);
            toastError("toast.error.read_failed");
        }
    }

    private void setSelectedFile(File file, boolean fromHistory) {
        if (file == null) {
            return;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".png")) {
            toastError("toast.error.not_png");
            ModSounds.play(ModSounds.UI_ERROR);
            return;
        }

        try (FileInputStream in = new FileInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            int width = image.getWidth();
            int height = image.getHeight();
            if (!isValidSize(width, height)) {
                image.close();
                toastError("toast.error.invalid_dimensions");
                ModSounds.play(ModSounds.UI_ERROR);
                return;
            }

            File workingFile = fromHistory ? file : copyToHistory(file);

            disposePreview();
            previewTexture = new NativeImageBackedTexture(image);
            previewTexture.setFilter(false, false);
            previewId = Identifiers.mod("preview/" + System.nanoTime());
            MinecraftClient.getInstance().getTextureManager().registerTexture(previewId, previewTexture);

            selectedFile = workingFile;
            selectedWidth = width;
            selectedHeight = height;
            ModLog.debug("Skin file selected: file='{}', size={}x{}, fromHistory={}",
                    workingFile.getName(), width, height, fromHistory);
            if (!fromHistory) {
                toastInfo("toast.file.selected", workingFile.getName());
                ModSounds.play(ModSounds.UI_UPLOAD);
            }

            loadHistory();
            historyScroll = clamp(historyScroll, 0, maxHistoryScroll());
            rebuildPreviewPlayer();
            updateActionState();
        } catch (Exception exception) {
            ModLog.error("Failed to load selected skin file: " + file, exception);
            toastError("toast.error.read_failed");
            ModSounds.play(ModSounds.UI_ERROR);
        }
    }

    private void rebuildPreviewPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || previewId == null) {
            previewPlayer = null;
            return;
        }

        previewSlim = slimToggle != null && slimToggle.isChecked();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "catskinc_preview");
        previewPlayer = new OtherClientPlayerEntity(client.world, profile);
        previewPlayer.setPose(EntityPose.STANDING);
        previewPlayer.setInvisible(false);
        previewPlayer.setYaw(180.0F);
        previewPlayer.bodyYaw = 180.0F;
        previewPlayer.headYaw = 180.0F;
        previewPlayer.setPitch(0.0F);

        try {
            previewPlayer.getDataTracker().set(PlayerEntityAccessor.catskincRemake$getPlayerModelParts(), (byte) 0x7F);
        } catch (Exception exception) {
            ModLog.trace("Preview player model part update failed: {}", exception.getMessage());
        }

        SkinOverrideStore.put(previewPlayer.getUuid(), previewId, previewSlim);
    }

    private void disposePreview() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (previewId != null && client != null) {
            client.getTextureManager().destroyTexture(previewId);
            previewId = null;
        }
        if (previewPlayer != null) {
            SkinOverrideStore.clear(previewPlayer.getUuid());
            previewPlayer = null;
        }
        previewTexture = null;
    }

    private void disposeHistory() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (HistoryEntry entry : history) {
            if (entry.thumbId != null && client != null) {
                client.getTextureManager().destroyTexture(entry.thumbId);
            }
        }
        history.clear();
    }

    private void loadHistory() {
        disposeHistory();
        File[] files = historyDir().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (files == null) {
            return;
        }

        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort(Comparator.comparingLong(File::lastModified).reversed());

        for (File file : sorted) {
            try (FileInputStream in = new FileInputStream(file)) {
                NativeImage image = NativeImage.read(in);
                int w = image.getWidth();
                int h = image.getHeight();

                NativeImage thumb = scaleImage(image, 64);
                image.close();

                NativeImageBackedTexture texture = new NativeImageBackedTexture(thumb);
                texture.setFilter(false, false);
                Identifier thumbId = Identifiers.mod("thumb/" + System.nanoTime());
                MinecraftClient.getInstance().getTextureManager().registerTexture(thumbId, texture);

                HistoryEntry entry = new HistoryEntry(file, w, h, file.lastModified());
                entry.thumbId = thumbId;
                history.add(entry);
            } catch (Exception exception) {
                ModLog.trace("Skipped broken history skin '{}': {}", file.getName(), exception.getMessage());
            }
        }
    }

    private void deleteHistory(int index) {
        if (index < 0 || index >= history.size()) {
            return;
        }
        HistoryEntry entry = history.remove(index);
        try {
            Files.deleteIfExists(entry.file.toPath());
        } catch (Exception exception) {
            ModLog.warn("Failed to delete history file: " + entry.file, exception);
        }
        if (entry.thumbId != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(entry.thumbId);
        }
        if (selectedFile != null && selectedFile.equals(entry.file)) {
            selectedFile = null;
            selectedWidth = 0;
            selectedHeight = 0;
            disposePreview();
            updateActionState();
        }
        historyScroll = clamp(historyScroll, 0, maxHistoryScroll());
    }

    private int maxHistoryScroll() {
        return Math.max(0, (history.size() * historyRowH) - historyH);
    }

    private void updateActionState() {
        if (uploadButton != null) {
            uploadButton.active = selectedFile != null;
        }
    }

    private void drawPanel(DrawContext context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, COLOR_PANEL);
        drawRectBorder(context, x, y, w, h, COLOR_BORDER);
    }

    private static void drawRectBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    private boolean isInside(int x, int y, int rx, int ry, int rw, int rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    private String ellipsis(String text, int maxPx) {
        if (textRenderer.getWidth(text) <= maxPx) {
            return text;
        }
        String dots = "...";
        int dotsWidth = textRenderer.getWidth(dots);
        int index = text.length();
        while (index > 0 && textRenderer.getWidth(text.substring(0, index)) + dotsWidth > maxPx) {
            index--;
        }
        return index <= 0 ? dots : text.substring(0, index) + dots;
    }

    private File historyDir() {
        File dir = new File(MinecraftClient.getInstance().runDirectory, "skin_history");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private File copyToHistory(File source) throws Exception {
        File dir = historyDir();
        File sourceParent = source.getParentFile();
        if (sourceParent != null && sourceParent.getAbsoluteFile().equals(dir.getAbsoluteFile())) {
            return source;
        }

        String base = stripExt(source.getName());
        File target = new File(dir, base + ".png");
        int index = 2;
        while (target.exists()) {
            target = new File(dir, base + "_" + index + ".png");
            index++;
        }
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        return target;
    }

    private static String stripExt(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private static NativeImage scaleImage(NativeImage src, int size) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        NativeImage out = new NativeImage(size, size, true);
        for (int y = 0; y < size; y++) {
            int sy = Math.min(srcH - 1, (int) ((y / (float) size) * srcH));
            for (int x = 0; x < size; x++) {
                int sx = Math.min(srcW - 1, (int) ((x / (float) size) * srcW));
                out.setColor(x, y, src.getColor(sx, sy));
            }
        }
        return out;
    }

    private static boolean isValidSize(int width, int height) {
        boolean square = width == height;
        boolean pow2 = (width & (width - 1)) == 0;
        boolean inRange = width >= 64 && width <= 4096;
        return square && pow2 && inRange;
    }

    private static int scaled(int value, float scale) {
        return Math.max(1, Math.round(value * scale));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1_024L) {
            return bytes + " B";
        }
        if (bytes < 1_048_576L) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1_024.0F);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0F);
    }

    private static String formatHistoryTime(long millis) {
        return HISTORY_TIME_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private static void toastInfo(String key, Object... args) {
        Toasts.info(Text.translatable("title.skin_management"), Text.translatable(key, args));
    }

    private static void toastError(String key, Object... args) {
        Toasts.error(Text.translatable("title.skin_management"), Text.translatable(key, args));
    }

    private static final class HistoryEntry {
        private final File file;
        private final int width;
        private final int height;
        private final long modifiedAt;
        private Identifier thumbId;

        private HistoryEntry(File file, int width, int height, long modifiedAt) {
            this.file = file;
            this.width = width;
            this.height = height;
            this.modifiedAt = modifiedAt;
        }
    }
}
