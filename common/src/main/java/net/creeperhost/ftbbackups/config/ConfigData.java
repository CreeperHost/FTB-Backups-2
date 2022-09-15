package net.creeperhost.ftbbackups.config;

import blue.endless.jankson.Comment;

import java.util.ArrayList;
import java.util.List;

public class ConfigData {
    @Comment("Allow the creation of backups automatically")
    public boolean enabled = true;

    @Comment("Permission level to use the /backup command")
    public int command_permission_level = 3;

    @Comment("Only send backup status to server ops")
    public boolean notify_op_only = true;

    @Comment("Don't send backup status at all")
    public boolean do_not_notify = false;

    @Comment("Maximum number of backups to keep")
    public int max_backups = 5;

    @Comment("This is done with Javas implementation of cron, More info here \n (https://www.cronmaker.com)")
    public String backup_cron = "0 */30 * * * ?";

    @Comment("Time between manual backups using the command")
    public int manual_backups_time = 0;

    @Comment("Only run a backup if a player has been online since the last backup")
    public boolean only_if_players_been_online = true;

    @Comment("Additional directories to include in backup")
    public List<String> additional_directories = new ArrayList<>();

    @Comment("Display file size in backup message")
    public boolean display_file_size = false;
}
