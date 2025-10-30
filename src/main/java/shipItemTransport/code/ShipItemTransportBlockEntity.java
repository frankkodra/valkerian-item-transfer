package shipItemTransport.code;

import net.minecraft.core.BlockPos;
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

    public ShipItemTransportBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIP_ITEM_TRANSPORTER.get(), pos, state);
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

    public boolean isImportMode() {
        // Delegate to MultiblockManager
        if (multiblockId != null && level != null && !level.isClientSide) {
            MultiblockManager manager = MultiblockManager.get(level);
            if (manager != null) {
                return manager.getMultiblockMode(multiblockId);
            }
        }
        return true; // Default to import
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
        super.saveAdditional(tag);

        if (multiblockId != null) {
            tag.putString("MultiblockId", multiblockId);

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
    }

    // FIXED: Use the correct NBT method signatures without HolderLookup.Provider
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

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

            Logger.sendMessage("Loaded block at " + getBlockPos() + " belonging to multiblock " + multiblockId, false);
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

    public static void tick(Level level, BlockPos pos, BlockState state, ShipItemTransportBlockEntity blockEntity) {
        // Item transport logic can be added here later
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
        return new ShipItemTransportMenu(containerId, playerInventory, this, isImportMode(), getMultiblockSize(), getChestCount());
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
            buf.writeBoolean(isImportMode()); // Write the mode to the buffer
            buf.writeInt(getMultiblockSize()); // Write the block count to the buffer
            buf.writeInt(getChestCount()); // Write the chest count to the buffer
        });
    }
}
