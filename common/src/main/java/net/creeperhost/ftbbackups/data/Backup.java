package net.creeperhost.ftbbackups.data;

public class Backup {
    private String worldName = "";
    private long createTime = 0;
    private String backupLocation = "";
    private long size = 0;
    private float ratio = 0;
    private String sha1 = "";
    private String preview = "";
    private boolean snapshot = false;

    public Backup(String worldName, long createTime, String backupLocation, long size, float ratio, String sha1, String preview, boolean snapshot) {
        this.worldName = worldName;
        this.createTime = createTime;
        this.backupLocation = backupLocation;
        this.size = size;
        this.ratio = ratio;
        this.sha1 = sha1;
        this.preview = preview;
        this.snapshot = snapshot;
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
}
