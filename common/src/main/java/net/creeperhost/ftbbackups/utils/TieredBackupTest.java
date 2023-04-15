package net.creeperhost.ftbbackups.utils;

import net.creeperhost.ftbbackups.BackupHandler;
import net.creeperhost.ftbbackups.FTBBackups;
import net.creeperhost.ftbbackups.config.Config;
import net.creeperhost.ftbbackups.config.Format;
import net.creeperhost.ftbbackups.data.Backup;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * To use this simply set enabled to true and then set up an in game timer to continuously run the backup command via a command block.
 * <p>
 * Created by brandon3055 on 15/04/2023
 */
public class TieredBackupTest {

    private static final boolean ENABLE_TIME_MACHINE = false;
    private static final boolean ENABLE_MONITORING = false;

    private static long testBackupStartTime = System.currentTimeMillis();
    public static long testBackupCount = 0;

    public static long getBackupTime() {
        if (!ENABLE_TIME_MACHINE) return System.currentTimeMillis();

        long time;
        if (testBackupCount < 20) { //Hour 0 to hour 10 in 30 minute intervals
            time = testBackupStartTime - (TimeUnit.MILLISECONDS.convert(30L * testBackupCount, TimeUnit.MINUTES));
        } else if (testBackupCount < 40) { //Day 0 to day 10 days in 12 hour intervals
            time = testBackupStartTime - (TimeUnit.MILLISECONDS.convert((testBackupCount - 20) * 12, TimeUnit.HOURS));
        } else if (testBackupCount < 60) { //Day 10 to day 30 days in 1 day intervals
            time = testBackupStartTime - (TimeUnit.MILLISECONDS.convert((testBackupCount - 40) + 10, TimeUnit.DAYS));
        } else if (testBackupCount < 80) { //Day 30 to day 170 days in 7 day intervals
            time = testBackupStartTime - (TimeUnit.MILLISECONDS.convert(((testBackupCount - 60) * 7) + 30, TimeUnit.DAYS));
        } else { // Start over
            testBackupStartTime += 70000;
            time = testBackupStartTime;
            testBackupCount = 0;
        }
        return time;
    }

    public static String getBackupName() {
        if (!ENABLE_TIME_MACHINE) return BackupHandler.genBackupFileName();

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(getBackupTime());
        String date = calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1) + "-" + calendar.get(Calendar.DATE);
        String time = calendar.get(Calendar.HOUR_OF_DAY) + "-" + calendar.get(Calendar.MINUTE) + "-" + calendar.get(Calendar.SECOND);
        String s = date + "_" + time;
        if (Config.cached().backup_format == Format.ZIP) {
            s += ".zip";
        }
        FTBBackups.LOGGER.info("Simulating backup at time: {}, Backup Count: {}", new Date(getBackupTime()), testBackupCount);
        return s;
    }

    private static Map<Backup, String> testKeptBackups = new HashMap<>();
    private static Map<String, Integer> testKeptCountThisCycle = new HashMap<>();

    public static boolean shouldRemoveBackup(Backup backup) {
        if (!ENABLE_MONITORING) return true;

        if (testKeptBackups.containsKey(backup)) {
            String name = testKeptBackups.get(backup);
            if (!testKeptCountThisCycle.containsKey(name) || testKeptCountThisCycle.get(name) < 5) {
                FTBBackups.LOGGER.error("Problem... This backup probably should not have been removed... {} : {}", backup, name);
                return false; //Break Point Here
            }
            testKeptBackups.remove(backup);
        }
        return true;
    }

    public static void willKeep(String name, Backup backup) {
        if (!ENABLE_MONITORING) return;

        testKeptCountThisCycle.put(name, testKeptCountThisCycle.getOrDefault(name, 0) + 1);
        if (!testKeptBackups.containsKey(backup) || !name.equals("Hour")) {
            testKeptBackups.put(backup, name);
        }
        FTBBackups.LOGGER.info("Keeping Backup: {}, Rule: {}", new Date(backup.getCreateTime()), name);
    }

    public static void cycleComplete() {
        if (!ENABLE_MONITORING) return;

        if (testKeptCountThisCycle.getOrDefault("Hour", 0) < 0) {
            FTBBackups.LOGGER.error("Lost Hour(s)");
        }
        if (testKeptCountThisCycle.getOrDefault("Day", 0) < 5) {
            FTBBackups.LOGGER.error("Lost Day(s)"); //Break Point Here
        }
        if (testKeptCountThisCycle.getOrDefault("Week", 0) < 5) {
            FTBBackups.LOGGER.error("Lost Week(s)"); //Break Point Here
        }
        if (testKeptCountThisCycle.getOrDefault("Month", 0) < 5) {
            FTBBackups.LOGGER.error("Lost Month(s)"); //Break Point Here
        }

        testKeptCountThisCycle.clear();
    }
}
