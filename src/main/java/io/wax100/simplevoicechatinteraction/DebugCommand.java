package io.wax100.simplevoicechatinteraction;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * デバッグ用コマンドを登録するクラス。
 * <p>
 * /voice_debug <dB> コマンドを使用して、マイクを使わずに
 * 任意の音量（デシベル）のボイスチャット入力をシミュレートする。
 */
@Mod.EventBusSubscriber(modid = Simplevoicechatinteraction.MODID)
public class DebugCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("voice_debug")
                .requires(source -> source.hasPermission(2)) // OP権限が必要
                .then(Commands.argument("db", DoubleArgumentType.doubleArg(0.0, 200.0))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            double db = DoubleArgumentType.getDouble(context, "db");
                            ServerPlayer player = source.getPlayerOrException();

                            if (VoiceChatSculkPlugin.instance != null) {
                                // 実際の音声インタラクションロジックを呼び出す
                                VoiceChatSculkPlugin.instance.processAudioInteraction(player, db, false);
                                
                                source.sendSuccess(() -> Component.literal(
                                        "§a[SVC Debug] §f" + db + " dB の音声をシミュレートしました。"), true);
                            } else {
                                source.sendFailure(Component.literal("§c[SVC Debug] プラグインが初期化されていません。"));
                            }
                            return 1;
                        })
                )
        );

        // 音量メーターの表示/非表示を切り替えるコマンド
        dispatcher.register(Commands.literal("voice_meter")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    VoiceMeterManager.toggleManual(player);
                    return 1;
                })
        );

        // 他プレイヤーのdBをモニタリングするコマンド
        dispatcher.register(Commands.literal("voice_monitor")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer admin = context.getSource().getPlayerOrException();
                    if (VoiceChatSculkPlugin.instance != null) {
                        VoiceChatSculkPlugin.instance.getActiveMonitors().remove(admin.getUUID());
                        VoiceMeterManager.clearMonitorMode(admin);
                        context.getSource().sendSuccess(() -> Component.literal("§a[SVC Monitor] モニタリングを停止しました。"), false);
                    }
                    return 1;
                })
                .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer admin = context.getSource().getPlayerOrException();
                            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "target");
                            if (VoiceChatSculkPlugin.instance != null) {
                                VoiceChatSculkPlugin.instance.getActiveMonitors().put(admin.getUUID(), target.getUUID());
                                VoiceMeterManager.setMonitorMode(admin, target.getScoreboardName());
                                context.getSource().sendSuccess(() -> Component.literal("§a[SVC Monitor] " + target.getScoreboardName() + " のマイク音量モニタリングを開始しました。"), false);
                            }
                            return 1;
                        })
                )
        );

        // コンフィグリロードコマンド
        dispatcher.register(Commands.literal("voice_reload")
                .requires(source -> source.hasPermission(2)) // OP権限が必要
                .executes(context -> {
                    net.minecraftforge.fml.config.ModConfig modConfig = net.minecraftforge.fml.config.ConfigTracker.INSTANCE.fileMap().values().stream()
                            .filter(config -> config.getModId().equals(Simplevoicechatinteraction.MODID))
                            .findFirst()
                            .orElse(null);

                    if (modConfig != null) {
                        try (com.electronwill.nightconfig.core.file.CommentedFileConfig fileConfig = 
                                com.electronwill.nightconfig.core.file.CommentedFileConfig.builder(modConfig.getFullPath())
                                    .sync().autosave().build()) {
                            fileConfig.load();
                            modConfig.getSpec().acceptConfig(fileConfig);
                            Config.reloadCachedValues();
                            
                            context.getSource().sendSuccess(() -> Component.literal("§a[SVC Interaction] 設定ファイルを再読み込みしました。 (Config reloaded)"), true);
                        } catch (Exception e) {
                            context.getSource().sendFailure(Component.literal("§c[SVC Interaction] 設定のリロードに失敗しました: " + e.getMessage()));
                        }
                    } else {
                        context.getSource().sendFailure(Component.literal("§c[SVC Interaction] コンフィグファイルが見つかりません。"));
                    }
                    return 1;
                })
        );
    }
}
