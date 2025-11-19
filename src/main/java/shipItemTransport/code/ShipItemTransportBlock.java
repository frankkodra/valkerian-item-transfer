package shipItemTransport.code;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ShipItemTransportBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");


    public ShipItemTransportBlock(Properties properties) {
        super(properties
                .noOcclusion()
                .strength(3.0f, 6.0f) // Hardness and resistance
                .requiresCorrectToolForDrops() // Requires pickaxe
        );
        this.registerDefaultState(this.stateDefinition.any()
                   .setValue(FACING, Direction.UP)
                .setValue(FORMED, false));
    }

    // Add these methods for tool requirements:
    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return player.hasCorrectToolForDrops(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED);
    }
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        // Drop the block item itself
        return Collections.singletonList(new ItemStack(ShipItemTransportMod.SHIP_ITEM_TRANSPORTER_ITEM.get()));
    }
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        return this.defaultBlockState().setValue(FACING, facing);

    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        Logger.sendMessage("Block placed at " + pos + " - processing immediately 1", true);
        if (!level.isClientSide && level.isLoaded(pos)) {
            Logger.sendMessage("Block placed at " + pos + " - processing immediately 2", true);
            MultiblockManager manager = MultiblockManager.get(level);
            if (manager != null) {
                manager.onBlockPlaced(pos);

                // Scan for existing chests near this block
                scanForNearbyChests(level, pos, manager);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && level.isLoaded(pos) && state.getBlock() != newState.getBlock()) {
            Logger.sendMessage("Block at " + pos + " is being removed", false);
            MultiblockManager manager = MultiblockManager.get(level);
            if (manager != null) {
                manager.onBlockRemoved(pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // Handle neighbor changes for chest connections
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);

        if (!level.isClientSide) {
            MultiblockManager manager = MultiblockManager.get(level);
            if (manager != null) {
                // Check if neighbor is a chest
                if (ChestHelper.isChest(level, neighborPos)) {
                    Logger.sendMessage("Chest detected at " + neighborPos + " near multiblock block at " + pos, false);
                    manager.handleChestNearMultiblock(neighborPos, pos);
                } else {
                    // Check if a chest was removed
                    manager.handlePossibleChestRemoval(neighborPos);
                }
            }
        }
    }

    private void scanForNearbyChests(Level level, BlockPos blockPos, MultiblockManager manager) {
        for (Direction dir : getValidConnectionDirections(level, blockPos)) {
            BlockPos checkPos = blockPos.relative(dir);
            if (ChestHelper.isChest(level, checkPos)) {
                Logger.sendMessage("Found existing chest at " + checkPos + " during block placement", false);
                manager.handleChestNearMultiblock(checkPos, blockPos);
            }
        }
    }

    private Direction[] getValidConnectionDirections(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Direction facing = state.getValue(FACING);

        // All directions except the facing direction
        return Arrays.stream(Direction.values())
                .filter(dir -> dir != facing)
                .toArray(Direction[]::new);
    }

    // Updated use method to use the new openMenu approach
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ShipItemTransportBlockEntity transportBlockEntity) {
                // Use the new openMenu method that includes mode data
                transportBlockEntity.openMenu((ServerPlayer) player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShipItemTransportBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.SHIP_ITEM_TRANSPORTER.get(), ShipItemTransportBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
