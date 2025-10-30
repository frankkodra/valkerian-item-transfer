package shipItemTransport.code;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ShipItemTransportScreen extends AbstractContainerScreen<ShipItemTransportMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ShipItemTransportMod.MODID, "textures/gui/ship_item_transporter_gui.png");

    private Button modeButton;
    private boolean currentImportMode;
    private int currentBlockCount;
    private int currentChestCount;
    private String currentShipInfo;

    public ShipItemTransportScreen(ShipItemTransportMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 140; // Increased height to fit ship connection info
        this.inventoryLabelY = 100000;
        this.currentImportMode = menu.isImportMode(); // Initialize with mode from server
        this.currentBlockCount = menu.getMultiblockSize(); // Initialize with block count from server
        this.currentChestCount = menu.getChestCount(); // Initialize with chest count from server
        this.currentShipInfo = menu.getShipConnectionInfo(); // Initialize with ship connection info
    }

    @Override
    protected void init() {
        super.init();

        this.modeButton = this.addRenderableWidget(Button.builder(
                        getModeButtonText(),
                        button -> {
                            if (menu.getBlockEntity() != null && minecraft != null) {
                                BlockPos pos = menu.getBlockEntity().getBlockPos();
                                if(menu.blockEntity.getLevel().isClientSide){
                                    Logger.sendMessage("in method init  on client",true);
                                }
                                else {
                                    Logger.sendMessage("in method init  on server",true);
                                }
                                NetworkHandler.INSTANCE.sendToServer(new ToggleModePacket(pos));
                                // Don't update locally - wait for server sync
                            }
                        })
                .bounds(leftPos + 60, topPos + 90, 56, 20) // Moved down to make room for ship info
                .build());
    }

    private Component getModeButtonText() {
        return Component.translatable(currentImportMode ? "gui.ship_item_transport.import" : "gui.ship_item_transport.export");
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, delta);
        renderTooltip(guiGraphics, mouseX, mouseY);

        // Update ship info from menu (in case it changed via sync packet)
        this.currentShipInfo = menu.getShipConnectionInfo();

        // Draw info using the synchronized values
        guiGraphics.drawString(this.font, "Connected Blocks: " + currentBlockCount, leftPos + 8, topPos + 20, 0x404040, false);
        guiGraphics.drawString(this.font, "Connected Chests: " + currentChestCount, leftPos + 8, topPos + 35, 0x404040, false);
        guiGraphics.drawString(this.font, "Mode: " + (currentImportMode ? "Import" : "Export"), leftPos + 8, topPos + 50, 0x404040, false);
        guiGraphics.drawString(this.font, "Connected to: " + currentShipInfo, leftPos + 8, topPos + 65, 0x404040, false);

        // Add some helpful info based on ship connection
        if (currentShipInfo.equals("Ground")) {
            guiGraphics.drawString(this.font, "Status: Ready for ship transfer", leftPos + 8, topPos + 80, 0x404040, false);
        } else {
            guiGraphics.drawString(this.font, "Status: Ship transport active", leftPos + 8, topPos + 80, 0x404040, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
    }

    public void updateModeFromServer(boolean serverImportMode) {
        this.currentImportMode = serverImportMode;
        if (this.modeButton != null) {
            this.modeButton.setMessage(getModeButtonText());
        }
    }

    public void updateBlockCountFromServer(int serverBlockCount) {
        this.currentBlockCount = serverBlockCount;
    }

    public void updateChestCountFromServer(int serverChestCount) {
        this.currentChestCount = serverChestCount;
    }

    // UPDATED: Update ship connection info from sync packet
    public void updateShipInfoFromServer(boolean isOnShip, long shipId) {
        // Update the menu with the new ship info
        menu.setShipInfo(isOnShip, shipId);
        // Update our local display string
        this.currentShipInfo = menu.getShipConnectionInfo();
    }
}