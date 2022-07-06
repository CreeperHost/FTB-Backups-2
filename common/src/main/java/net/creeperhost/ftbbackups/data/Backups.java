package net.creeperhost.ftbbackups.data;

import java.util.ArrayList;
import java.util.List;

public class Backups {
    private List<Backup> backups = new ArrayList<>();

    public void add(Backup backup) {
        backups.add(backup);
    }

    public boolean isEmpty() {
        return backups.isEmpty();
    }

    public int size() {
        return backups.size();
    }

    public int unprotectedSize() {
        return getBackups().stream().filter(backup ->!backup.isProtected()).toList().size();
    }

    public boolean contains(Backup backup) {
        return backups.contains(backup);
    }

    public void remove(Backup backup) {
        backups.remove(backup);
    }

    public List<Backup> getBackups() {
        return backups;
    }
}
