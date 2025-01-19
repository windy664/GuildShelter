package org.windy.guildshelter;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.windy.guildshelter.listener.neoforge.BlockInteractListener;

// 标注mod的ID，NeoForge会根据此ID来识别该mod
@Mod(Guildshelter.MODID)
public class Guildshelter {
    // 定义mod的ID，供后续使用
    public static final String MODID = "guildshelter";
    // 使用slf4j日志记录器
    private static final Logger LOGGER = LogUtils.getLogger();

    // 创建一个DeferredRegister来注册方块（所有方块将在"guildshelter"命名空间下注册）
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // 创建一个DeferredRegister来注册物品（所有物品将在"guildshelter"命名空间下注册）
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // 创建一个DeferredRegister来注册创造模式标签（所有标签将在"guildshelter"命名空间下注册）
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // 创建一个方块，ID为"guildshelter:example_block"，设置属性
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // 创建一个方块物品，ID为"guildshelter:example_block"，并且关联到上面的方块
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);
    // 创建一个食物物品，ID为"guildshelter:example_item"，并设置属性
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // 构造方法，NeoForge会自动调用它来加载mod
    public Guildshelter(IEventBus modEventBus, ModContainer modContainer) {
        // 注册commonSetup方法，用于通用的mod初始化
        modEventBus.addListener(this::commonSetup);


        // 注册NeoForge的事件总线，允许此类直接处理事件
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new BlockInteractListener());

        // 注册mod的配置
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    // 通用的mod初始化方法
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");

        // 如果配置中启用了logDirtBlock，则记录土方块的信息
        if (Config.logDirtBlock)
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));

        // 记录一些配置信息
        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        // 记录配置中的所有物品
        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }


    // 处理服务器启动事件
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    // 仅在客户端执行的代码，通过@EventBusSubscriber注解标记
    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        // 客户端初始化事件
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("HELLO FROM CLIENT SETUP");
        }
    }
}
