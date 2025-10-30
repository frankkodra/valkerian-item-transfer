package shipItemTransport.code;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import java.util.*;

public class ChestHelper {

    public static boolean isChest(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;

        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        // Check if it's a vanilla chest
        if (block instanceof ChestBlock) {
            return true;
        }
        Logger.sendMessage("block is not a chest",false);

        // Check if it has item handler capability (modded chests)
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
        }
        Logger.sendMessage("block is not a chest",false);

        return false;
    }

    public static boolean isDoubleChest(Level level, BlockPos chestPos1, BlockPos chestPos2) {
        Logger.sendMessage("in is double chest method",false);
        if (!level.isLoaded(chestPos1) || !level.isLoaded(chestPos2)) return false;

        BlockState state1 = level.getBlockState(chestPos1);
        BlockState state2 = level.getBlockState(chestPos2);

        // Must be same block type
        if (state1.getBlock() != state2.getBlock()) return false;

        // Handle vanilla chests - use the proper double chest detection
        if (state1.getBlock() instanceof ChestBlock) {
            ChestType type1 = state1.getValue(ChestBlock.TYPE);
            ChestType type2 = state2.getValue(ChestBlock.TYPE);
            Direction facing1 = state1.getValue(ChestBlock.FACING);
            Direction facing2 = state2.getValue(ChestBlock.FACING);
            Logger.sendMessage("Checking if double chest between " + chestPos1 + " and " + chestPos2 + " (facing1: " + facing1 + ")"+" (facing2: " + facing2 + ") axis == "+facing1.getAxis()+" type 1== "+type1, false);
            if(facing1!=facing2) {
                return false;
            }


                if(facing1==Direction.SOUTH){
                    if(  type1==ChestType.LEFT&&chestPos1.getX()-1==chestPos2.getX()) {
                        Logger.sendMessage("Detected double chest between " + chestPos1 + " and " + chestPos2 + " (facing1: " + facing1 + ")"+" (facing2: " + facing2 + ") axis == "+facing1.getAxis(), false);
                            return true;
            }
                    if(type1==ChestType.RIGHT&&chestPos1.getX()+1==chestPos2.getX()) {
                    Logger.sendMessage("Detected double chest between " + chestPos1 + " and " + chestPos2 + " (facing1: " + facing1 + ")"+" (facing2: " + facing2 + ") axis == "+facing1.getAxis(), false);

                    return true;
                }
                }
            if(facing1==Direction.NORTH){
                if(  type1==ChestType.LEFT&&chestPos1.getX()+1==chestPos2.getX()) {
                    Logger.sendMessage("Detected double chest between " + chestPos1 + " and " + chestPos2 + " (facing1: " + facing1 + ")"+" (facing2: " + facing2 + ") axis == "+facing1.getAxis(), false);
                    return true;
                }
                if(type1==ChestType.RIGHT&&chestPos1.getX()-1==chestPos2.getX()) {
                    Logger.sendMessage("Detected double chest between " + chestPos1 + " and " + chestPos2 + " (facing1: " + facing1 + ")"+" (facing2: " + facing2 + ") axis == "+facing1.getAxis(), false);

                    return true;
                }

            }
            if(facing1==Direction.EAST) {
                if ( type1 == ChestType.RIGHT && chestPos1.getZ() - 1 == chestPos2.getZ() ) {
                    Logger.sendMessage("Detected double chest between " + chestPos1 + " and " + chestPos2 + " (facing1: " + facing1 + ")" + " (facing2: " + facing2 + ") axis == " + facing1.getAxis(), false);

                    return true;
                }
                if (  type1 == ChestType.LEFT && chestPos1.getZ() + 1 == chestPos2.getZ() ) {
                    Logger.sendMessage("Detected double chest between " + chestPos1 + " and " + chestPos2 + " (facing1: " + facing1 + ")" + " (facing2: " + facing2 + ") axis == " + facing1.getAxis(), false);

                    return true;
                }
            }
            if(facing1==Direction.WEST) {
                if ( type1 == ChestType.RIGHT && chestPos1.getZ() + 1 == chestPos2.getZ() ) {
                    Logger.sendMessage("Detected double chest between " + chestPos1 + " and " + chestPos2 + " (facing1: " + facing1 + ")" + " (facing2: " + facing2 + ") axis == " + facing1.getAxis(), false);

                    return true;
                }
                if (  type1 == ChestType.LEFT && chestPos1.getZ() - 1 == chestPos2.getZ() ) {
                    Logger.sendMessage("Detected double chest between " + chestPos1 + " and " + chestPos2 + " (facing1: " + facing1 + ")" + " (facing2: " + facing2 + ") axis == " + facing1.getAxis(), false);

                    return true;
                }
            }

            // They must face the same direction and be opposite types

        }

        // For other modded chests with item capability, use adjacency as fallback


        return false;
    }

    private static boolean areChestsConnected(ChestBlockEntity chest1, ChestBlockEntity chest2) {
        // Vanilla chests store their neighbor in a field, but we need to use the block state
        // This is a more reliable way to detect connected chests
        BlockState state1 = chest1.getBlockState();
        BlockState state2 = chest2.getBlockState();

        if (state1.getBlock() instanceof ChestBlock && state2.getBlock() instanceof ChestBlock) {
            ChestType type1 = state1.getValue(ChestBlock.TYPE);
            ChestType type2 = state2.getValue(ChestBlock.TYPE);

            // If either chest is not SINGLE, they're part of a double chest
            return type1 != ChestType.SINGLE || type2 != ChestType.SINGLE;
        }

        return false;
    }

    private static boolean arePositionsAdjacent(BlockPos pos1, BlockPos pos2) {
        // Check if positions are adjacent horizontally (double chests don't form vertically)
        int dx = Math.abs(pos1.getX() - pos2.getX());
        int dz = Math.abs(pos1.getZ() - pos2.getZ());

        return (dx == 1 && dz == 0) || (dx == 0 && dz == 1);
    }

    public static Set<BlockPos> findConnectedChests(Level level, BlockPos startPos) {
        Set<BlockPos> connected = new HashSet<>();
        connected.add(startPos);

        // Only check for ONE direct double chest connection
        // Don't do recursive flood fill that creates chains
        for (Direction dir : getPossibleDoubleChestDirections()) {
            BlockPos neighbor = startPos.relative(dir);  // Fixed: use startPos instead of current
            Logger.sendMessage("Checking Direction for double chest dir== "+ dir,false);

            if (isChest(level, neighbor) &&
                    isDoubleChest(level, startPos, neighbor)) {
                // Only add the direct neighbor if it forms a valid double chest
                connected.add(neighbor);
                Logger.sendMessage("Found direct double chest connection: " + startPos + " <-> " + neighbor, false);
                // STOP after finding one double chest - don't continue the chain
                break;
            }
        }

        return connected;
    }

    private static Direction[] getPossibleDoubleChestDirections() {
        // Double chests can form horizontally (not vertically)
        return new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
    }

    public static BlockPos determinePrimaryPosition(Set<BlockPos> chestGroup) {
        if (chestGroup.size() == 1) {
            return chestGroup.iterator().next();
        }

        // For double chests, find the primary (LEFT or west/north) chest
        List<BlockPos> positions = new ArrayList<>(chestGroup);
        BlockPos pos1 = positions.get(0);
        BlockPos pos2 = positions.get(1);

        // Get the block states to determine which is the primary
        Level level = getLevelFromAnyPosition(positions);
        if (level != null) {
            BlockState state1 = level.getBlockState(pos1);
            BlockState state2 = level.getBlockState(pos2);

            if (state1.getBlock() instanceof ChestBlock && state2.getBlock() instanceof ChestBlock) {
                ChestType type1 = state1.getValue(ChestBlock.TYPE);
                ChestType type2 = state2.getValue(ChestBlock.TYPE);

                // The LEFT chest is primary in a double chest
                if (type1 == ChestType.LEFT) return pos1;
                if (type2 == ChestType.LEFT) return pos2;

                // If we can't determine from type, use position-based fallback
            }
        }

        // Fallback: use west-most, then north-most position as primary
        return positions.stream()
                .min((p1, p2) -> {
                    int xCompare = Integer.compare(p1.getX(), p2.getX());
                    if (xCompare != 0) return xCompare;
                    return Integer.compare(p1.getZ(), p2.getZ());
                })
                .orElse(positions.get(0));
    }

    // Helper method to get level from any position in the group
    private static Level getLevelFromAnyPosition(Collection<BlockPos> positions) {
        if (positions.isEmpty()) return null;
        BlockPos firstPos = positions.iterator().next();
        // This assumes all positions are in the same level
        // In practice, we'd need to store level reference, but for now return null
        return null;
    }

    public static Direction[] getValidConnectionDirections(Level level, BlockPos multiblockPos) {
        BlockState state = level.getBlockState(multiblockPos);
        if (state.hasProperty(ShipItemTransportBlock.FACING)) {
            Direction facing = state.getValue(ShipItemTransportBlock.FACING);
            // All directions except the facing direction
            return Arrays.stream(Direction.values())
                    .filter(dir -> dir != facing)
                    .toArray(Direction[]::new);
        }
        return Direction.values();
    }
}