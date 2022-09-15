package net.creeperhost.ftbbackups.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.creeperhost.ftbbackups.BackupHandler;
import net.creeperhost.ftbbackups.config.Config;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

import java.util.Locale;

public class BackupCommand {
    public static long lastManualBackupTime = 0;

    public static final SuggestionProvider<CommandSourceStack> SUGGESTIONS = (commandContext, suggestionsBuilder) -> {
        String[] strings = new String[]{"start", "snapshot"};
        return SharedSuggestionProvider.suggest(strings, suggestionsBuilder);
    };

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("backup")
                .requires(cs -> cs.hasPermission(hasPerm(cs.getServer())))
                .then(
                        Commands.argument("command", StringArgumentType.string()).suggests(SUGGESTIONS)
                                .executes(cs -> execute(cs, StringArgumentType.getString(cs, "command")))
                );
    }

    public static int hasPerm(MinecraftServer server)
    {
        if(server.isDedicatedServer() || server instanceof IntegratedServer integratedServer && integratedServer.isPublished()) return Config.cached().command_permission_level;
        return 0;
    }

    private static int execute(CommandContext<CommandSourceStack> cs, String command) {
        boolean isProtected = false;
        switch(command.toLowerCase(Locale.ROOT)) {
            case "snapshot":
                isProtected = true;
                break;
        }
        if (Config.cached().manual_backups_time == 0) {
            cs.getSource().getServer().saveAllChunks(true, true, true);
            BackupHandler.isDirty = true;
            BackupHandler.createBackup(cs.getSource().getServer(), isProtected);
        } else {
            long configTimeFromMinutes = ((long) Config.cached().manual_backups_time) * 60_000;
            long lastBackupWithConfig = lastManualBackupTime + configTimeFromMinutes;

            if (System.currentTimeMillis() > lastBackupWithConfig) {
                lastManualBackupTime = System.currentTimeMillis() ;
                BackupHandler.createBackup(cs.getSource().getServer(), isProtected);
            } else {
                cs.getSource().sendFailure(Component.literal("Unable to create backup, Last backup was taken less than " + Config.cached().max_backups + " Minutes ago"));
            }
        }
        return 0;
    }
}
