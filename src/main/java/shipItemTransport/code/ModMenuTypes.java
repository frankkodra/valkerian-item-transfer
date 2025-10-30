package shipItemTransport.code;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ShipItemTransportMod.MODID);

    public static final RegistryObject<MenuType<ShipItemTransportMenu>> SHIP_ITEM_TRANSPORTER_MENU =
            MENUS.register("ship_item_transporter_menu",
                    () -> IForgeMenuType.create((containerId, inv, data) -> {
                        BlockPos pos = data.readBlockPos();
                        boolean mode = data.readBoolean(); // READ THE MODE
                        int blockCount = data.readInt(); // READ THE BLOCK COUNT
                        int chestCount = data.readInt(); // READ THE CHEST COUNT
                        boolean onShip = data.readBoolean();
                        long multiblockId = data.readLong();
                        return new ShipItemTransportMenu(containerId, inv, inv.player.level().getBlockEntity(pos), mode, blockCount, chestCount,onShip,multiblockId);
                    }));
}