package com.mikoalopex.ccsconnector.content;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

final class ConnectorBoardGeometry {
    static final DirectionProperty FACING = BlockStateProperties.FACING;

    private ConnectorBoardGeometry() {
    }

    static VoxelShape shapeForFacing(Direction facing) {
        Direction mountedFace = facing.getAxis() == Direction.Axis.Y ? facing : facing.getOpposite();
        return switch (mountedFace) {
            case DOWN -> Shapes.box(0.0, 0.8125, 0.0, 1.0, 1.0, 1.0);
            case UP -> Shapes.box(0.0, 0.0, 0.0, 1.0, 0.1875, 1.0);
            case NORTH -> Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 0.1875);
            case SOUTH -> Shapes.box(0.0, 0.0, 0.8125, 1.0, 1.0, 1.0);
            case WEST -> Shapes.box(0.0, 0.0, 0.0, 0.1875, 1.0, 1.0);
            case EAST -> Shapes.box(0.8125, 0.0, 0.0, 1.0, 1.0, 1.0);
        };
    }
}
