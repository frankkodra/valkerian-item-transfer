// BlockCountSyncPacket.java
package shipItemTransport.code;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class BlockCountSyncPacket {
    private final int blockCount;

    public BlockCountSyncPacket(int blockCount) {
        this.blockCount = blockCount;
    }

    public BlockCountSyncPacket(FriendlyByteBuf buf) {
        this.blockCount = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(blockCount);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // This runs on the CLIENT side
            Minecraft minecraft = Minecraft.getInstance();
            // If player has the GUI open, update the block count
            if (minecraft.screen instanceof ShipItemTransportScreen screen) {
                screen.updateBlockCountFromServer(blockCount);
            }
        });
        return true;
    }
}
