package com.mikoalopex.ccsconnector;

import com.mikoalopex.ccsconnector.content.CCSynaxisBridgeBlock;
import com.mikoalopex.ccsconnector.content.CCSynaxisBridgeBlockEntity;
import com.mikoalopex.ccsconnector.content.CCSynaxisSuperHubBlock;
import com.mikoalopex.ccsconnector.content.CCSynaxisSuperHubBlockEntity;
import com.mikoalopex.ccsconnector.content.LogOutputAssistantBlock;
import com.mikoalopex.ccsconnector.content.LogOutputAssistantBlockEntity;
import com.mikoalopex.ccsconnector.synaxis.CCSynaxisBridgeComponent;
import com.mikoalopex.ccsconnector.synaxis.CCSynaxisSuperHubComponent;
import com.mikoalopex.ccsconnector.synaxis.LogOutputAssistantComponent;
import com.mojang.logging.LogUtils;
import com.verr1.synaxis.foundation.cimulink.game.body.PlantPortProviders;
import com.verr1.synaxis.foundation.cimulink.game.runtime.CimulinkWorldRuntimes;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.Optional;

@Mod(CCSConnector.MODID)
public class CCSConnector {
    public static final String MODID = "ccsconnector";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredBlock<CCSynaxisBridgeBlock> CC_SYNAXIS_BRIDGE = BLOCKS.register(
            "cc_synaxis_bridge",
            () -> new CCSynaxisBridgeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<CCSynaxisSuperHubBlock> CC_SYNAXIS_SUPER_HUB = BLOCKS.register(
            "cc_synaxis_super_hub",
            () -> new CCSynaxisSuperHubBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<LogOutputAssistantBlock> LOG_OUTPUT_ASSISTANT = BLOCKS.register(
            "log_output_assistant",
            () -> new LogOutputAssistantBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final DeferredItem<BlockItem> CC_SYNAXIS_BRIDGE_ITEM = ITEMS.register(
            "cc_synaxis_bridge",
            () -> new BlockItem(CC_SYNAXIS_BRIDGE.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> CC_SYNAXIS_SUPER_HUB_ITEM = ITEMS.register(
            "cc_synaxis_super_hub",
            () -> new BlockItem(CC_SYNAXIS_SUPER_HUB.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> LOG_OUTPUT_ASSISTANT_ITEM = ITEMS.register(
            "log_output_assistant",
            () -> new BlockItem(LOG_OUTPUT_ASSISTANT.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CCSynaxisBridgeBlockEntity>> CC_SYNAXIS_BRIDGE_BE =
            BLOCK_ENTITY_TYPES.register(
                    "cc_synaxis_bridge",
                    () -> BlockEntityType.Builder.of(CCSynaxisBridgeBlockEntity::new, CC_SYNAXIS_BRIDGE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CCSynaxisSuperHubBlockEntity>> CC_SYNAXIS_SUPER_HUB_BE =
            BLOCK_ENTITY_TYPES.register(
                    "cc_synaxis_super_hub",
                    () -> BlockEntityType.Builder.of(CCSynaxisSuperHubBlockEntity::new, CC_SYNAXIS_SUPER_HUB.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LogOutputAssistantBlockEntity>> LOG_OUTPUT_ASSISTANT_BE =
            BLOCK_ENTITY_TYPES.register(
                    "log_output_assistant",
                    () -> BlockEntityType.Builder.of(LogOutputAssistantBlockEntity::new, LOG_OUTPUT_ASSISTANT.get()).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_MODE_TABS.register(
            "tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ccsconnector"))
                    .icon(() -> CC_SYNAXIS_BRIDGE_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(CC_SYNAXIS_BRIDGE_ITEM.get());
                        output.accept(CC_SYNAXIS_SUPER_HUB_ITEM.get());
                        output.accept(LOG_OUTPUT_ASSISTANT_ITEM.get());
                    })
                    .build());

    public CCSConnector(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CimulinkWorldRuntimes.componentRegistry().register(CCSynaxisBridgeComponent.INSTANCE);
            CimulinkWorldRuntimes.componentRegistry().register(CCSynaxisSuperHubComponent.INSTANCE);
            CimulinkWorldRuntimes.componentRegistry().register(LogOutputAssistantComponent.INSTANCE);
            registerSynaxisPlantPorts();
        });
    }

    private static void registerSynaxisPlantPorts() {
        PlantPortProviders.register(CC_SYNAXIS_BRIDGE_BE.get(), (blockEntity, endpointId, requestedName) -> {
            if (blockEntity instanceof CCSynaxisBridgeBlockEntity bridge) {
                return Optional.of(bridge.createPlantPort(endpointId, requestedName));
            }
            return Optional.empty();
        });
        PlantPortProviders.register(CC_SYNAXIS_SUPER_HUB_BE.get(), (blockEntity, endpointId, requestedName) -> {
            if (blockEntity instanceof CCSynaxisSuperHubBlockEntity hub) {
                return Optional.of(hub.createPlantPort(endpointId, requestedName));
            }
            return Optional.empty();
        });
        PlantPortProviders.register(LOG_OUTPUT_ASSISTANT_BE.get(), (blockEntity, endpointId, requestedName) -> {
            if (blockEntity instanceof LogOutputAssistantBlockEntity assistant) {
                return Optional.of(assistant.createPlantPort(endpointId, requestedName));
            }
            return Optional.empty();
        });
        LOGGER.info("Registered CC: Synaxis Bridge PlantPort provider");
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                PeripheralCapability.get(),
                CC_SYNAXIS_BRIDGE_BE.get(),
                (bridge, side) -> bridge.peripheral());
        event.registerBlockEntity(
                PeripheralCapability.get(),
                CC_SYNAXIS_SUPER_HUB_BE.get(),
                (hub, side) -> hub.peripheral());
    }
}
