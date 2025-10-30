// ChestCountSyncPacket.java
package shipItemTransport.code;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ChestCountSyncPacket {
    private final int chestCount;

    public ChestCountSyncPacket(int chestCount) {
        this.chestCount = chestCount;
    }

    public ChestCountSyncPacket(FriendlyByteBuf buf) {
        this.chestCount = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(chestCount);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // This runs on the CLIENT side
            Minecraft minecraft = Minecraft.getInstance();
            // If player has the GUI open, update the chest count
            if (minecraft.screen instanceof ShipItemTransportScreen screen) {
                screen.updateChestCountFromServer(chestCount);
            }
        });
        return true;
    }
}
