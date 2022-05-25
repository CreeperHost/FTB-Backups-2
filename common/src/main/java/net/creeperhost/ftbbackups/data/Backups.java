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
        int c = 0;
        for(Backup backup : getBackups()) {
            if(backup.isProtected()) continue;
            c++;
        }
        return c;
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

    //Gson


}
