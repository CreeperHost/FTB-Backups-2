package net.creeperhost.ftbbackups.data;

public class Backup {
    private String worldName = "";
    private long createTimeNano = 0;
    private String backupLocation = "";
    private long size = 0;
    private float ratio = 0;
    private String sha1 = "";

    public Backup(String worldName, long createTimeNano, String backupLocation, long size, float ratio, String sha1) {
        this.worldName = worldName;
        this.createTimeNano = createTimeNano;
        this.backupLocation = backupLocation;
        this.size = size;
        this.ratio = ratio;
        this.sha1 = sha1;
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

    public long getCreateTimeNano() {
        return createTimeNano;
    }

    public float getRatio() {
        return ratio;
    }

    public String getBackupLocation() {
        return backupLocation;
    }
}
