package shipItemTransport.code;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class ShipItemTransportBlockEntity extends BlockEntity implements MenuProvider {
    private String multiblockId = null;
    private Set<BlockPos> multiblockMembers = new HashSet<>();
    boolean needsRegistration = true;
    private String oldMultiblockId = null;
    private boolean importMode = true;

    public ShipItemTransportBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIP_ITEM_TRANSPORTER.get(), pos, state);
    }
    public boolean isImportMode() {
        // If part of a multiblock, get mode from manager. Otherwise use local mode.
        if (multiblockId != null && level != null && !level.isClientSide) {
            MultiblockManager manager = MultiblockManager.get(level);
            if (manager != null) {
                return manager.getMultiblockMode(multiblockId);
            }
        }
        return importMode;
    }
    public void setImportMode(boolean importMode) {
        this.importMode = importMode;
        setChanged();
    }

    public void setMultiblock(String id, Set<BlockPos> members) {
        this.multiblockId = id;
        this.multiblockMembers = new HashSet<>(members);
        setChanged();

        // Notify client of changes
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }

        if (level != null && !level.isClientSide) {
            BlockState currentState = level.getBlockState(getBlockPos());
            boolean shouldBeFormed = (id != null && members.size() > 1);

            if (currentState.getValue(ShipItemTransportBlock.FORMED) != shouldBeFormed) {
                level.setBlock(getBlockPos(), currentState.setValue(ShipItemTransportBlock.FORMED, shouldBeFormed), 3);
            }
        }

        Logger.sendMessage("Block at " + getBlockPos() + " set to multiblock " + id + " with " + members.size() + " blocks", false);
    }

    public String getMultiblockId() {
        return multiblockId;
    }

    public Set<BlockPos> getMultiblockMembers() {
        return new HashSet<>(multiblockMembers);
    }



    public int getMultiblockSize() {
        // Delegate to MultiblockManager
        if (multiblockId != null && level != null && !level.isClientSide) {
            MultiblockManager manager = MultiblockManager.get(level);
            if (manager != null) {
                return manager.getMultiblockBlocks(multiblockId).size();
            }
        }
        return 1; // Single block if no multiblock or invalid state
    }

    public int getChestCount() {
        // Delegate to MultiblockManager
        if (multiblockId != null && level != null && !level.isClientSide) {
            MultiblockManager manager = MultiblockManager.get(level);
            if (manager != null) {
                return manager.getMultiblockChestCount(multiblockId);
            }
        }
        return 0; // No chests if no multiblock or invalid state
    }

    // NEW: Get ship connection info
    public boolean isOnShip() {
        if (level == null || level.isClientSide || multiblockId == null) return false;
        MultiblockManager manager = MultiblockManager.get(level);
        return manager != null && manager.isMultiblockOnShip(multiblockId);
    }

    // NEW: Get ship connection info for GUI display
    public String getShipConnectionInfo() {
        if (level == null || level.isClientSide || multiblockId == null) return "Ground";
        MultiblockManager manager = MultiblockManager.get(level);
        return manager != null ? manager.getMultiblockShipInfo(multiblockId) : "Ground";
    }

    public void toggleMode() {
        // Delegate to MultiblockManager
        if (multiblockId != null && level != null && !level.isClientSide) {
            MultiblockManager manager = MultiblockManager.get(level);
            if (manager != null) {
                manager.toggleMultiblockMode(multiblockId);
                setChanged();

                // Notify client of changes
                if (level != null) {
                    level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                }
            }
        }
    }

    // FIXED: Use the correct NBT method signatures without HolderLookup.Provider
    @Override
    protected void saveAdditional(CompoundTag tag) {
        Logger.sendMessage("saving additional data for block entity: multiblock id == "+multiblockId,true);
        super.saveAdditional(tag);

        if (multiblockId != null) {
            tag.putString("MultiblockId", multiblockId);
        }

        // NEW: Save import mode
        tag.putBoolean("ImportMode", importMode);

        ListTag membersList = new ListTag();
        for (BlockPos pos : multiblockMembers) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            membersList.add(posTag);
        }
        tag.put("MultiblockMembers", membersList);
    }

    // FIXED: Use the correct NBT method signatures
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        Logger.sendMessage("Loaded block at " + getBlockPos(), true);

        // NEW: Load import mode
        if (tag.contains("ImportMode")) {
            importMode = tag.getBoolean("ImportMode");
        }

        if (tag.contains("MultiblockId")) {
            multiblockId = tag.getString("MultiblockId");

            multiblockMembers.clear();
            ListTag membersList = tag.getList("MultiblockMembers", Tag.TAG_COMPOUND);
            for (int i = 0; i < membersList.size(); i++) {
                CompoundTag posTag = membersList.getCompound(i);
                BlockPos pos = new BlockPos(
                        posTag.getInt("x"),
                        posTag.getInt("y"),
                        posTag.getInt("z")
                );
                multiblockMembers.add(pos);
            }

            Logger.sendMessage("Loaded block at " + getBlockPos() + " belonging to multiblock " + multiblockId + " with mode: " + (importMode ? "IMPORT" : "EXPORT"), true);
        }
    }


    // NEW: Handle registration in the tick method (runs on main thread)
    public static void tick(Level level, BlockPos pos, BlockState state, ShipItemTransportBlockEntity blockEntity) {
        if (blockEntity.needsRegistration) {
            blockEntity.registerWithMultiblock();
            blockEntity.needsRegistration = false;
        }

        // Your existing tick logic can go here too
    }

    // NEW: Self-registration method
    private void registerWithMultiblock() {
        while (level == null) {
            try {
                Thread.sleep(300);
                Logger.sendMessage("Waiting for block entity Level to load", true);
            }catch (InterruptedException e) {
                    Logger.sendMessage("Interrupted while sleeping block entity", true);
                }
        }
        if(level.isClientSide() ==true ) return;
        MultiblockManager manager = MultiblockManager.get(level);
        if (manager == null) return;

        Logger.sendMessage("Block at " + getBlockPos() + " registering with multiblock " + multiblockId, false);

        // Check if multiblock exists
        Set<BlockPos> existingBlocks = manager.getMultiblockBlocks(multiblockId);

        if (existingBlocks.isEmpty()) {
            // Multiblock doesn't exist - create it with just this block
            Set<BlockPos> newBlocks = new HashSet<>();
            newBlocks.add(getBlockPos());
            manager.recreateMultiblock(newBlocks, importMode,multiblockId);

            Logger.sendMessage("Created new multiblock " + multiblockId + " with block at " + getBlockPos(), false);
        } else {
            // Multiblock exists - add this block to it
            Set<BlockPos> updatedBlocks = new HashSet<>(existingBlocks);
            updatedBlocks.add(getBlockPos());
            manager.updateMultiblock(multiblockId, updatedBlocks);

            Logger.sendMessage("Added block at " + getBlockPos() + " to existing multiblock " + multiblockId, false);
        }

        // Update this block entity with current multiblock data
        Set<BlockPos> currentMembers = manager.getMultiblockBlocks(multiblockId);
        setMultiblock(multiblockId, currentMembers);

        // Scan for chests (existing behavior)
        scanForChests();
    }

    // NEW: Chest scanning after registration
    private void scanForChests() {
        if (level == null || level.isClientSide || multiblockId == null) return;

        MultiblockManager manager = MultiblockManager.get(level);
        if (manager == null) return;

        // Use existing chest scanning logic from MultiblockManager
        for (Direction dir : ChestHelper.getValidConnectionDirections(level, getBlockPos())) {
            BlockPos checkPos = getBlockPos().relative(dir);
            if (ChestHelper.isChest(level, checkPos)) {
                manager.handleChestNearMultiblock(checkPos, getBlockPos());
            }
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide && multiblockId != null) {
            if (level.isLoaded(worldPosition) &&
                    !level.getBlockState(worldPosition).is(getBlockState().getBlock())) {

                MultiblockManager manager = MultiblockManager.get(level);
                if (manager != null) {
                    manager.onBlockRemoved(getBlockPos());
                }
            }
        }
        super.setRemoved();
    }

    // MenuProvider implementation
    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ship_item_transport_mod.ship_item_transporter");
    }



    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag);
        return tag;
    }

    // Make sure your createMenu method looks like this:
    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        // This runs on SERVER side only
        // Just return a menu instance - Forge will handle the network sync
        return new ShipItemTransportMenu(containerId, playerInventory, this, isImportMode(), getMultiblockSize(), getChestCount(),isOnShip(),MultiblockManager.get(level).getMultiblockShipId(multiblockId));
    }

    // ADD THIS METHOD - Forge calls this automatically to write data to client
    public void markForClientUpdate() {
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            setChanged();
        }
    }

    // ADD THIS METHOD to open screen with extra data
    public void openMenu(ServerPlayer player) {
        NetworkHooks.openScreen(player, this, buf -> {
            buf.writeBlockPos(getBlockPos());
            buf.writeBoolean(isImportMode());
            buf.writeInt(getMultiblockSize());
            buf.writeInt(getChestCount());
            // NEW: Add ship info to the buffer
            buf.writeBoolean(isOnShip());
            long shipId = MultiblockManager.get(level).getMultiblockShipId(multiblockId);
            buf.writeLong(shipId );
        });
    }
}
