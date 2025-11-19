package shipItemTransport.code;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(ShipItemTransportMod.MODID)
public class ShipItemTransportMod {
    public static final String MODID = "ship_item_transport_mod";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<Block> SHIP_ITEM_TRANSPORTER = BLOCKS.register("ship_item_transporter",
            () -> new ShipItemTransportBlock(BlockBehaviour.Properties.copy(Blocks.DIAMOND_BLOCK)));

    public static final RegistryObject<Item> SHIP_ITEM_TRANSPORTER_ITEM = ITEMS.register("ship_item_transporter",
            () -> new BlockItem(SHIP_ITEM_TRANSPORTER.get(), new Item.Properties().stacksTo(64)
                     // Optional: makes it stand out
            ));

    public ShipItemTransportMod() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        Logger.startNewLogFile();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTab.CREATIVE_MODE_TABS.register(modEventBus); // Register the creative tab
        ModMenuTypes.MENUS.register(modEventBus);
        NetworkHandler.register();
        MinecraftForge.EVENT_BUS.register(new MultiblockManager(null)); // We just need to register the event handler

        // Load config when mod starts
        Config.load();
    }
}