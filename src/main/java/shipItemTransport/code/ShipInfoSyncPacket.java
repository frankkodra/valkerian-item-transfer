package shipItemTransport.code;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShipInfoSyncPacket {
    private final boolean isOnShip;
    private final long shipId;

    public ShipInfoSyncPacket(boolean isOnShip, long shipId) {
        this.isOnShip = isOnShip;
        this.shipId = shipId;
    }

    public ShipInfoSyncPacket(FriendlyByteBuf buf) {
        this.isOnShip = buf.readBoolean();
        this.shipId = buf.readLong();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(isOnShip);
        buf.writeLong(shipId);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // This runs on the CLIENT side
            Minecraft minecraft = Minecraft.getInstance();
            // If player has the GUI open, update the ship info
            if (minecraft.screen instanceof ShipItemTransportScreen screen) {
                screen.updateShipInfoFromServer(isOnShip, shipId);
            }
        });
        return true;
    }
}