package net.creeperhost.ftbbackups.data;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Backup {
    private String worldName = "";
    private long createTime = 0;
    private List<String> backupLocation = new ArrayList<>();
    private long size = 0;
    private float ratio = 0;
    private List<String> sha1 = new ArrayList<>();
    private String preview = "";
    private boolean snapshot = false;
    private String backupName = "";

    public Backup(String worldName, long createTime, List<String> backupLocation, long size, float ratio, List<String> sha1, String preview, boolean snapshot, String backupName) {
        this.worldName = worldName;
        this.createTime = createTime;
        this.backupLocation = backupLocation;
        this.size = size;
        this.ratio = ratio;
        this.sha1 = sha1;
        this.preview = preview;
        this.snapshot = snapshot;
        this.backupName = backupName;
    }

    public String getWorldName() {
        return worldName;
    }

    public long getSize() {
        return size;
    }

    public List<String> getSha1() {
        return sha1;
    }

    public boolean isProtected() { return snapshot; }

    public long getCreateTime() {
        return createTime;
    }

    public float getRatio() {
        return ratio;
    }

    public List<String> getBackupLocation() {
        return backupLocation;
    }

    public String getBackupName()
    {
        return backupName;
    }
}
