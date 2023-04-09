package net.creeperhost.ftbbackups.data;

import net.creeperhost.ftbbackups.config.Format;

public class Backup {
    private String worldName;
    private long createTime;
    private String backupLocation;
    private long size;
    private float ratio;
    private String sha1;
    private String preview;
    private boolean snapshot;
    private String backupName;
    private Format backupFormat;

    public Backup(String worldName, long createTime, String backupLocation, long size, float ratio, String sha1, String preview, boolean snapshot, String backupName, Format backupFormat) {
        this.worldName = worldName;
        this.createTime = createTime;
        this.backupLocation = backupLocation;
        this.size = size;
        this.ratio = ratio;
        this.sha1 = sha1;
        this.preview = preview;
        this.snapshot = snapshot;
        this.backupName = backupName;
        this.backupFormat = backupFormat;
    }

    public String getWorldName() {
        return worldName;
    }

    public long getSize() {
        return size;
    }

    public String getSha1() {
        return sha1;
    }

    public boolean isProtected() { return snapshot; }

    public long getCreateTime() {
        return createTime;
    }

    public float getRatio() {
        return ratio;
    }

    public String getBackupLocation() {
        return backupLocation;
    }

    public String getBackupName()
    {
        return backupName;
    }

    public Format getBackupFormat() {
        return backupFormat;
    }
}
