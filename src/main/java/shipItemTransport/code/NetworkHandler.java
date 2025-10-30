package shipItemTransport.code;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ShipItemTransportMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        INSTANCE.registerMessage(id++, ToggleModePacket.class, ToggleModePacket::toBytes, ToggleModePacket::new, ToggleModePacket::handle);
        INSTANCE.registerMessage(id++, SyncModePacket.class, SyncModePacket::toBytes, SyncModePacket::new, SyncModePacket::handle);
        INSTANCE.registerMessage(id++, BlockCountSyncPacket.class, BlockCountSyncPacket::toBytes, BlockCountSyncPacket::new, BlockCountSyncPacket::handle);
        INSTANCE.registerMessage(id++, ChestCountSyncPacket.class, ChestCountSyncPacket::toBytes, ChestCountSyncPacket::new, ChestCountSyncPacket::handle);
        INSTANCE.registerMessage(id++, ShipInfoSyncPacket.class, ShipInfoSyncPacket::toBytes, ShipInfoSyncPacket::new, ShipInfoSyncPacket::handle);
    }
}

