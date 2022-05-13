package net.creeperhost.ftbbackups.config;

import blue.endless.jankson.Comment;

public class ConfigData {
    @Comment("Allow the creation of backups automatically")
    public boolean enabled = true;

    @Comment("Permission level to use the /backup command")
    public int command_permission_level = 3;

    @Comment("Only send backup status to server ops")
    public boolean notify_op_only = false;

    @Comment("Maximum number of backups to keep")
    public int max_backups = 5;

    @Comment("This is done with Javas implementation of cron, More info here \n (https://docs.oracle.com/cd/E12058_01/doc/doc.1014/e12030/cron_expressions.htm)")
    public String backup_cron = "0 */30 * * * ?";

    @Comment("Time between manual backups using the command")
    public int manual_backups_time = 0;

    @Comment("Only run a backup if a player has been online since the last backup")
    public boolean only_if_players_been_online = true;
}
