package shipItemTransport.code;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ShipItemTransportMod.MODID);

    public static final RegistryObject<CreativeModeTab> SHIP_TRANSPORT_TAB = CREATIVE_MODE_TABS.register("ship_transport_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + ShipItemTransportMod.MODID))
                    .icon(() -> new ItemStack(ShipItemTransportMod.SHIP_ITEM_TRANSPORTER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ShipItemTransportMod.SHIP_ITEM_TRANSPORTER.get());
                        // Add other items from your mod here in the future
                    })
                    .build());
}
