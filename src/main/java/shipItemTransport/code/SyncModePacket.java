// SyncModePacket.java
package shipItemTransport.code;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncModePacket {
    private final boolean isImportMode;

    public SyncModePacket(boolean isImportMode) {
        this.isImportMode = isImportMode;
    }

    public SyncModePacket(FriendlyByteBuf buf) {
        this.isImportMode = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(isImportMode);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // This runs on the CLIENT side
            Minecraft minecraft = Minecraft.getInstance();
            // If player has the GUI open, update it (server already verified it's the right multiblock)
            if (minecraft.screen instanceof ShipItemTransportScreen screen) {
                screen.updateModeFromServer(isImportMode);
            }
        });
        return true;
    }
}