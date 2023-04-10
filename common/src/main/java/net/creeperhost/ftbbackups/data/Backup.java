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
    private boolean complete;

    public Backup() {
        //Default to true if field does not exist in json.
        //Ensures compatibility with previous versions that did not have this field.
        complete = true;
    }

    public Backup(String worldName, long createTime, String backupLocation, long size, float ratio, String sha1, String preview, boolean snapshot, String backupName, Format backupFormat, boolean complete) {
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
        this.complete = complete;
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

    public Backup setRatio(float ratio) {
        this.ratio = ratio;
        return this;
    }

    public Backup setSha1(String sha1) {
        this.sha1 = sha1;
        return this;
    }

    public Backup setComplete() {
        complete = true;
        return this;
    }
}
