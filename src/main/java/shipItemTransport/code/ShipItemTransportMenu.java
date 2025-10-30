package shipItemTransport.code;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;

public class ShipItemTransportMenu extends AbstractContainerMenu {
    public final ShipItemTransportBlockEntity blockEntity;
    private final ContainerLevelAccess levelAccess;
    private boolean currentMode; // stores the mode from server
    private int currentBlockCount; // stores the block count from server
    private int currentChestCount; // stores the chest count from server
    private boolean currentIsOnShip; // NEW: stores ship status from server
    private long currentShipId; // NEW: stores ship ID from server

    // Client-side constructor
    public ShipItemTransportMenu(int containerId, Inventory inv, FriendlyByteBuf extraData) {
        this(containerId, inv,
                inv.player.level().getBlockEntity(extraData.readBlockPos()),
                extraData.readBoolean(),  // mode
                extraData.readInt(),      // block count
                extraData.readInt(),      // chest count
                extraData.readBoolean(),  // NEW: isOnShip
                extraData.readLong()      // NEW: shipId
        );
    }

    // Server-side constructor with mode, block count, and chest count
    public ShipItemTransportMenu(int containerId, Inventory inv, BlockEntity blockEntity,
                                 boolean currentMode, int currentBlockCount, int currentChestCount,
                                 boolean currentIsOnShip, long currentShipId) {
        super(ModMenuTypes.SHIP_ITEM_TRANSPORTER_MENU.get(), containerId);

        if (blockEntity instanceof ShipItemTransportBlockEntity) {
            this.blockEntity = (ShipItemTransportBlockEntity) blockEntity;
        } else {
            this.blockEntity = null;
        }

        this.levelAccess = ContainerLevelAccess.create(inv.player.level(), blockEntity != null ? blockEntity.getBlockPos() : BlockPos.ZERO);
        this.currentMode = currentMode;
        this.currentBlockCount = currentBlockCount;
        this.currentChestCount = currentChestCount;
        this.currentIsOnShip = currentIsOnShip; // NEW
        this.currentShipId = currentShipId;     // NEW
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // No shift-clicking
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null) {
            return false;
        }
        return stillValid(levelAccess, player, ShipItemTransportMod.SHIP_ITEM_TRANSPORTER.get());
    }

    public ShipItemTransportBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getMultiblockSize() {
        return currentBlockCount; // Return the stored block count from server
    }

    public int getChestCount() {
        return currentChestCount; // Return the stored chest count from server
    }

    public boolean isImportMode() {
        return currentMode; // Return the stored mode from server
    }

    // UPDATED: Get ship connection info for GUI - now uses synchronized data
    public String getShipConnectionInfo() {
        if (currentIsOnShip && currentShipId != -1) {
            return "Ship " + currentShipId;
        }
        return "Ground";
    }

    // NEW: Get raw ship data
    public boolean isOnShip() {
        return currentIsOnShip;
    }

    public long getShipId() {
        return currentShipId;
    }

    public void setCurrentMode(boolean mode) {
        this.currentMode = mode;
    }

    public void setCurrentBlockCount(int blockCount) {
        this.currentBlockCount = blockCount;
    }

    public void setCurrentChestCount(int chestCount) {
        this.currentChestCount = chestCount;
    }

    // NEW: Set ship info from sync packet
    public void setShipInfo(boolean isOnShip, long shipId) {
        this.currentIsOnShip = isOnShip;
        this.currentShipId = shipId;
    }

    public void toggleMode() {
        if (blockEntity.getLevel().isClientSide) {
            Logger.sendMessage("in method toggling mode on client",true);
        }
        else  {
            Logger.sendMessage("in method toggling mode on server",true);
        }
        if (blockEntity != null && blockEntity.getMultiblockId() != null && blockEntity.getLevel() != null) {
            MultiblockManager manager = MultiblockManager.get(blockEntity.getLevel());

            if (manager != null) {
                manager.toggleMultiblockMode(blockEntity.getMultiblockId());
                if (blockEntity.getLevel().isClientSide) {
                    Logger.sendMessage("toggling mode on client",true);
                }
                else  {
                    Logger.sendMessage("toggling mode on server",true);
                }
            }
        } else if (blockEntity != null) {
            blockEntity.toggleMode();
        }
    }
}