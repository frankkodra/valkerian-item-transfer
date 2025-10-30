
package shipItemTransport.code;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ShipItemTransportMod.MODID);

    public static final RegistryObject<BlockEntityType<ShipItemTransportBlockEntity>> SHIP_ITEM_TRANSPORTER =
            BLOCK_ENTITIES.register("ship_item_transporter",
                    () -> BlockEntityType.Builder.of(ShipItemTransportBlockEntity::new,
                            ShipItemTransportMod.SHIP_ITEM_TRANSPORTER.get()).build(null));
}