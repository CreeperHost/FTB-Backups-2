package net.creeperhost.ftbbackups.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.creeperhost.ftbbackups.BackupHandler;
import net.creeperhost.ftbbackups.config.Config;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TranslatableComponent;

public class BackupCommand {
    public static long lastManualBackupTime = 0;

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("backup").requires(commandSourceStack -> commandSourceStack.hasPermission(Config.cached().command_permission_level)).executes(BackupCommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> cs) {
        if (Config.cached().manual_backups_time == 0) {
            cs.getSource().getServer().saveAllChunks(true, true, true);
            BackupHandler.isDirty = true;
            BackupHandler.createBackup(cs.getSource().getServer());
        } else {
            long configTimeFromMinutes = ((long) Config.cached().manual_backups_time) * 60000000000L;
            long lastBackupWithConfig = lastManualBackupTime + configTimeFromMinutes;

            if (System.nanoTime() > lastBackupWithConfig) {
                lastManualBackupTime = System.nanoTime();
                BackupHandler.createBackup(cs.getSource().getServer());
            } else {
                cs.getSource().sendFailure(new TranslatableComponent("Unable to create backup, Last backup was taken less than " + Config.cached().max_backups + " Minutes ago"));
            }
        }

        return 0;
    }
}
