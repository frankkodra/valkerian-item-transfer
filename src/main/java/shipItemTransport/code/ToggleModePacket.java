// ToggleModePacket.java
package shipItemTransport.code;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Supplier;

public class ToggleModePacket {
    private final BlockPos blockPos;

    public ToggleModePacket(BlockPos blockPos) {
        this.blockPos = blockPos;
    }

    public ToggleModePacket(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                Level level = player.level();
                if (level.getBlockEntity(blockPos) instanceof ShipItemTransportBlockEntity blockEntity) {
                    boolean newMode;
                    String multiblockId = blockEntity.getMultiblockId();

                    if (multiblockId != null) {
                        MultiblockManager manager = MultiblockManager.get(level);
                        if (manager != null) {
                            manager.toggleMultiblockMode(multiblockId);
                            newMode = manager.getMultiblockMode(multiblockId);

                            // Send sync packet to players viewing ANY block in this multiblock
                            SyncModePacket syncPacket = new SyncModePacket(newMode);
                            for (ServerPlayer serverPlayer :(List<ServerPlayer>) level.players()) {
                                // Check if player has a menu open for the SAME MULTIBLOCK
                                if (serverPlayer.containerMenu instanceof ShipItemTransportMenu menu &&
                                        menu.getBlockEntity() != null) {

                                    String viewedMultiblockId = menu.getBlockEntity().getMultiblockId();

                                    // Send if viewing the same multiblock
                                    if (multiblockId.equals(viewedMultiblockId)) {
                                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), syncPacket);
                                    }
                                }
                            }
                        }
                    } else {
                        // Single block - only send to player viewing this specific block
                        blockEntity.toggleMode();
                        newMode = blockEntity.isImportMode();

                        if (player.containerMenu instanceof ShipItemTransportMenu menu &&
                                menu.getBlockEntity() != null &&
                                menu.getBlockEntity().getBlockPos().equals(blockPos)) {
                            SyncModePacket syncPacket = new SyncModePacket(newMode);
                            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), syncPacket);
                        }
                    }
                }
            }
        });
        return true;
    }
}
