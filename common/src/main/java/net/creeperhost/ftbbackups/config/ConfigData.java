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

    @Comment("Backup retention mode. Valid Modes: MAX_BACKUPS, TIERED\nNote: TIERED mode is an experimental feature, Use at your own risk.")
    public RetentionMode retention_mode = RetentionMode.MAX_BACKUPS;

    @Comment("Applies to retention_mode:MAX_BACKUPS, Sets the maximum number of backups to keep")
    public int max_backups = 5;

    @Comment("Applies to retention_mode:TIERED, The latest x number of backups will be retained")
    public int keep_latest = 5;

    @Comment("Applies to retention_mode:TIERED, Sets number of hourly backups to keep")
    public int keep_hourly = 1;

    @Comment("Applies to retention_mode:TIERED, Sets number of daily backups to keep")
    public int keep_daily = 1;

    @Comment("Applies to retention_mode:TIERED, Sets number of weekly backups to keep")
    public int keep_weekly = 1;

    @Comment("Applies to retention_mode:TIERED, Sets number of monthly backups to keep")
    public int keep_monthly = 1;

    @Comment("""
            This is done with an implementation of cron from the Quartz java library.
            More info here
            (http://www.cronmaker.com)""")
    public String backup_cron = "0 */30 * * * ?";

    @Comment("Time between manual backups using the command")
    public int manual_backups_time = 0;

    @Comment("Only run a backup if a player has been online since the last backup")
    public boolean only_if_players_been_online = true;

    @Deprecated(forRemoval = true)
    @Comment("Additional directories to include in backup")
    public List<String> additional_directories = new ArrayList<>();

    @Comment("""
            Additional files and directories to include in backup.
            Can specify a file name, path relative to server directory or wildcard file path
            Examples:                       (All file paths are relative to server root)
            fileName.txt                    Any/all file named "fileName.txt"
            folder/file.txt                 Exact file path
            folder/                         Everything in this folder
            path/starts/with*               Any files who's path starts with
            *path/ends/with.txt             Any files who's path ends with
            *path/contains*                 Any files who's path contains
            Note: You can now specify the entire minecraft server directory, but if you do this, you must change "backup_location" to a location
            outside the server directory to avoid backing up previous backups.""")
    public List<String> additional_files = new ArrayList<>();

    @Comment("Display file size in backup message")
    public boolean display_file_size = false;

    @Comment("backup location, The default \".\" creates a folder called \"backups\" inside the server directory.")
    public String backup_location = ".";

    @Comment("Specify the backup format. Valid options are ZIP, ZSTD and DIRECTORY")
    public Format backup_format = Format.ZIP;

    @Comment("Minimum free disk space in MB. If a backup's creation would leave less than this amount of disk space remaining, the backup will be aborted.")
    public long minimum_free_space = 0;

    @Comment("If the previous backup failed due to lack of space, the oldest backup will be deleted to free space.")
    public boolean free_space_if_needed = false;

    @Comment("""
            Specify files or folders to be excluded.
            Can specify a file name, path relative to server directory or wildcard file path
            Examples:                       (All file paths are relative to server root)
            fileName.txt                    Any/all file named "fileName.txt"
            folder/file.txt                 Exact file path
            folder/                         Everything in this folder
            path/starts/with*               Any files who's path starts with
            *path/ends/with.txt             Any files who's path ends with
            *path/contains*                 Any files who's path contains""")
    public List<String> excluded = new ArrayList<>();

    @Comment("The dimension used when creating backup preview image, specify \"all\" to enable automatic detection of primary dimension (can be very slow)\nSpecify \"none\" to disable preview")
    public String preview_dimension = "minecraft:overworld";
}
