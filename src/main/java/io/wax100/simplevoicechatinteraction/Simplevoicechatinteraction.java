package io.wax100.simplevoicechatinteraction;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * SimpleVoiceChatInteraction のメインModクラス。
 * <p>
 * Simple Voice Chat と Minecraft のスカルク振動システムを連携させるサーバーサイド専用Mod。
 * ボイスチャットの処理は {@link VoiceChatSculkPlugin} が担当し、
 * Simple Voice Chat API の {@code @ForgeVoicechatPlugin} アノテーションにより自動検出される。
 */
@Mod(SimpleVoiceChatInteraction.MODID)
public class SimpleVoiceChatInteraction {

    public static final String MODID = "simplevoicechatinteraction";
    private static final Logger LOGGER = LogUtils.getLogger();

    public SimpleVoiceChatInteraction() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);

        // Mod設定を登録
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[SimpleVoiceChatInteraction] 共通セットアップ完了");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[SimpleVoiceChatInteraction] サーバー起動中 — ボイスチャット→スカルク振動ブリッジが有効です");
    }
}
