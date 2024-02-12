package net.creeperhost.ftbbackups.data;

import net.creeperhost.ftbbackups.config.Format;

import java.util.Objects;

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

    public void setSize(long size) {
        this.size = size;
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

    public boolean isComplete() {
        return complete;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Backup backup = (Backup) o;
        return createTime == backup.createTime && size == backup.size && Float.compare(backup.ratio, ratio) == 0 && snapshot == backup.snapshot && Objects.equals(worldName, backup.worldName) && Objects.equals(backupLocation, backup.backupLocation) && Objects.equals(sha1, backup.sha1) && Objects.equals(preview, backup.preview) && Objects.equals(backupName, backup.backupName) && backupFormat == backup.backupFormat;
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, createTime, backupLocation, size, ratio, sha1, preview, snapshot, backupName, backupFormat);
    }
}
