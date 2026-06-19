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
                .then(Commands.argument("db", DoubleArgumentType.doubleArg(-100.0, 0.0))
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
    }
}
