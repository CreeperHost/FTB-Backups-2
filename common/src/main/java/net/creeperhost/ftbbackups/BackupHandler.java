package net.creeperhost.ftbbackups;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.creeperhost.ftbbackups.config.Config;
import net.creeperhost.ftbbackups.config.Format;
import net.creeperhost.ftbbackups.config.RetentionMode;
import net.creeperhost.ftbbackups.data.Backup;
import net.creeperhost.ftbbackups.data.Backups;
import net.creeperhost.ftbbackups.utils.FileUtils;
import net.creeperhost.ftbbackups.utils.TieredBackupTest;
import net.creeperhost.levelio.LevelIO;
import net.creeperhost.levelio.data.Level;
import net.creeperhost.levelpreview.ActivityScanner;
import net.creeperhost.levelpreview.CaptureHandler;
import net.creeperhost.levelpreview.ColourMap;
import net.creeperhost.levelpreview.LevelPreview;
import net.creeperhost.levelpreview.lib.CaptureArea;
import net.creeperhost.levelpreview.lib.SimplePNG;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class BackupHandler {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final LevelPreview PREVIEW = new LevelPreview(new MCNBTImpl());

    private static Path serverRoot;
    private static Path backupFolderPath;
    private static Path worldFolder;
    public static final AtomicBoolean backupRunning = new AtomicBoolean(false);
    private static final AtomicBoolean backupFailed = new AtomicBoolean(false);
    private static AtomicReference<String> backupPreview = new AtomicReference<>("");
    private static boolean isSpaceConstrained = false;
    public static boolean isDirty = false;

    public static AtomicReference<Backups> backups = new AtomicReference<>(new Backups());
    private static String failReason = "";
    private static long lastAutoBackup = 0;

    public static CompletableFuture<Void> currentFuture;
    public static Path defaultBackupLocation;

    public static void init(MinecraftServer minecraftServer) {
        serverRoot = minecraftServer.getServerDirectory().toPath().normalize().toAbsolutePath();
        defaultBackupLocation = serverRoot.resolve("backups");

        if (!Config.cached().backup_location.equalsIgnoreCase(".")) {
            try {
                Path configPath = Path.of(Config.cached().backup_location);
                if (Files.exists(configPath)) {
                    FTBBackups.LOGGER.info("Using configured backups directory at {}", configPath.toAbsolutePath());
                    backupFolderPath = configPath;
                } else {
                    FTBBackups.LOGGER.error(configPath.toAbsolutePath() + " does not exist, please create the directory before continuing");
                    backupFolderPath = defaultBackupLocation;
                }
            } catch (Exception e) {
                FTBBackups.LOGGER.error("Unable to find backup folder from config {} using default {}", Config.cached().backup_location, defaultBackupLocation.toAbsolutePath());
                e.printStackTrace();
                backupFolderPath = defaultBackupLocation;
            }
        } else {
            backupFolderPath = defaultBackupLocation;
        }
        createBackupFolder(defaultBackupLocation);
        createBackupFolder(backupFolderPath);

        loadJson();
        initPreview();
    }

    private static void initPreview() {
        //Add all known blocks to the block map
        ColourMap colourMap = PREVIEW.getColourMap();
        CaptureHandler.init(1);
        for (ResourceLocation regName : BuiltInRegistries.BLOCK.keySet()) {
            Block block = BuiltInRegistries.BLOCK.get(regName);
            int colour = block.defaultMapColor().col;
            colourMap.addBlockMapping(regName.toString(), colour);
        }
    }

    public static String createPreview(MinecraftServer minecraftServer) {
        try {
            long scanStart = System.currentTimeMillis();
            Path worldPath = minecraftServer.getWorldPath(LevelResource.ROOT).toAbsolutePath();
            PREVIEW.loadWorld(worldPath);
            LevelIO levelIO = PREVIEW.getLevelIO();

            String previewDim = Config.cached().preview_dimension;
            CaptureArea area;

            //Find dimensions with player activity
            if ("all".equals(previewDim)) {
                List<ActivityScanner> scanners = new ArrayList<>();
                for (Level level : levelIO.getLevels()) {
                    ActivityScanner scanner = new ActivityScanner(levelIO, level, 1);
                    if (scanner.findActivityClusters(512, 512, 1)) {
                        scanners.add(scanner);
                    }
                }

                if (scanners.isEmpty()) {
                    return ""; //This should only happen if all dimensions are empty.
                }

                //Get the scanner for the dimension with the highest habitation factor (The dim players have spent the most time in)
                scanners.sort(Comparator.comparingDouble(ActivityScanner::getTotalHabitationFactor).reversed());
                ActivityScanner scanner = scanners.get(0);
                //Since we set maxClusters to 1 we should have exactly 1 result.
                area = scanner.getResults().get(0);
            } else {
                Level level = levelIO.getLevel(previewDim);
                if (level == null) {
                    FTBBackups.LOGGER.error("Could not find specified dimension {}, using overworld", previewDim);
                    level = levelIO.getLevel("minecraft:overworld");
                    if (level == null) {
                        FTBBackups.LOGGER.warn("overworld dimension not found, will not create backup preview image");
                        return "";
                    }
                }
                ActivityScanner scanner = new ActivityScanner(levelIO, level, 1);
                if (!scanner.findActivityClusters(512, 512, 1)) {
                    return ""; //Dimension is empty
                }
                area = scanner.getResults().get(0);
            }

            long captureStart = System.currentTimeMillis();
            SimplePNG.SimpleImg capture = PREVIEW.newCapture()
                    .captureArea(area)
                    .doCapture()
                    .getImage();

            //Closes the underlying LevelIO.
            PREVIEW.close();

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            SimplePNG.writePNG(os, capture);
            byte[] image = os.toByteArray();

            FTBBackups.LOGGER.info("Backup preview created, Scan took {}ms, Capture took {}ms", captureStart - scanStart, System.currentTimeMillis() - captureStart);
            return "data:image/png;base64, " + Base64.getEncoder().encodeToString(image);
        } catch (Exception ex) {
            FTBBackups.LOGGER.error("An error occurred while generating backup preview", ex);
        }

        return "";
    }

    public static boolean isRunning() {
        return backupRunning.get();
    }

    public static void createBackup(MinecraftServer minecraftServer) {
        createBackup(minecraftServer, false, "automated");
    }

    public static void createBackup(MinecraftServer minecraftServer, boolean protect, String name) {
        try {
            if (FTBBackups.isShutdown || !Config.cached().enabled) return;

            if (Config.cached().only_if_players_been_online && !BackupHandler.isDirty) {
                FTBBackups.LOGGER.info("Skipping backup, no players have been online since last backup.");
                return;
            }
            worldFolder = minecraftServer.getWorldPath(LevelResource.ROOT).toAbsolutePath();
            FTBBackups.LOGGER.info("Found world folder at " + worldFolder);
            String backupName = TieredBackupTest.getBackupName();

            Path backupLocation = backupFolderPath.resolve(backupName);
            Format format = Config.cached().backup_format;

            if (canCreateBackup()) {
                lastAutoBackup = TieredBackupTest.getBackupTime();

                backupRunning.set(true);
                //Force save all the chunk and player data
                CompletableFuture<?> saveOp = minecraftServer.submit(() -> {
                    if (!minecraftServer.isCurrentlySaving()) {
                        minecraftServer.saveEverything(true, false, true);
                    }
                });
                //Set the worlds not to save while we are creating a backup
                setNoSave(minecraftServer, true);
                //Store the time we started the backup
                AtomicLong startTime = new AtomicLong(System.nanoTime());
                //Store the finishTime outside the thread for later use
                AtomicLong finishTime = new AtomicLong();

                //Add the backup entry first so in the event the backup is interrupted we are not left with an orphaned partial backup that will never get cleared automatically.
                Backup backup = new Backup(worldFolder.normalize().getFileName().toString(), lastAutoBackup, backupLocation.toString(), 0, 0, "", backupPreview.get(), protect, name, format, false);
                addBackup(backup);
                updateJson();

                //Start the backup process in its own thread
                currentFuture = CompletableFuture.runAsync(() ->
                {
                    try {
                        //Ensure that save operation we just scheduled has completed before we continue.
                        if (!saveOp.isDone()) {
                            FTBBackups.LOGGER.info("Waiting for world save to complete.");
                            saveOp.get(30, TimeUnit.SECONDS);
                        }

                        //Warn all online players that the server is going to start creating a backup
                        alertPlayers(minecraftServer, Component.translatable(FTBBackups.MOD_ID + ".backup.starting"));
                        //Create the full path to this backup
                        Path backupPath = backupFolderPath.resolve(backupName);
                        //Create a zip of the world folder and store it in the /backup folder
                        List<Path> backupPaths = new LinkedList<>();
                        backupPaths.add(worldFolder);

                        try (Stream<Path> pathStream = Files.walk(serverRoot)) {
                            for (Path path : (Iterable<Path>) pathStream::iterator) {
                                if (Files.isDirectory(path)) continue;
                                Path relFile = serverRoot.relativize(path);
                                if (!FileUtils.matchesAny(relFile, Config.cached().additional_files)) continue;

                                try {
                                    if (!FileUtils.isChildOf(path, serverRoot)) {
                                        FTBBackups.LOGGER.warn("Ignoring additional file {}, as it is not a child of the server root directory.", relFile);
                                        continue;
                                    }

                                    if (FileUtils.isChildOf(path, worldFolder)) {
                                        FTBBackups.LOGGER.warn("Ignoring additional file {}, as it is a child of the world folder.", relFile);
                                        continue;
                                    }

                                    if (FileUtils.isChildOf(path, backupFolderPath)) {
                                        FTBBackups.LOGGER.warn("Ignoring additional file {}, as it is a child of the backups folder.", relFile);
                                        continue;
                                    }

                                    if (Files.exists(path)) {
                                        backupPaths.add(path);
                                    }
                                } catch (Exception err) {
                                    FTBBackups.LOGGER.error("Failed to add additional file '{}' to the backup.", relFile, err);
                                }
                            }
                        }

                        for (String p : Config.cached().additional_directories) {
                            try {
                                Path path = serverRoot.resolve(p);
                                if (!FileUtils.isChildOf(path, serverRoot)) {
                                    FTBBackups.LOGGER.warn("Ignoring additional directory {}, as it is not a child of the server root directory.", p);
                                    continue;
                                }

                                if (path.equals(worldFolder)) {
                                    FTBBackups.LOGGER.warn("Ignoring additional directory {}, as it is the world folder.", p);
                                    continue;
                                }

                                if (FileUtils.isChildOf(path, worldFolder)) {
                                    FTBBackups.LOGGER.warn("Ignoring additional directory {}, as it is a child of the world folder.", p);
                                    continue;
                                }
                                if (FileUtils.isChildOf(path, backupFolderPath)) {
                                    FTBBackups.LOGGER.warn("Ignoring additional directory {}, as it is a child of the backups folder.", p);
                                    continue;
                                }

                                if (!Files.isDirectory(path)) {
                                    FTBBackups.LOGGER.warn("Ignoring additional directory {}, as it is not a directory..", p);
                                    continue;
                                }

                                if (Files.exists(path)) {
                                    backupPaths.add(path);
                                }
                            } catch (Exception err) {
                                FTBBackups.LOGGER.error("Failed to add additional directory '{}' to the backup.", p, err);
                            }
                        }
                        backupPreview.set(createPreview(minecraftServer));
                        if (format == Format.ZIP) {
                            FileUtils.zip(backupPath, serverRoot, backupPaths);
                        } else {
                            FileUtils.copy(backupPath, serverRoot, backupPaths);
                        }
                        //The backup did not fail
                        backupFailed.set(false);
                        BackupHandler.isDirty = false;
                    } catch (Exception e) {
                        //Set backup running state to false
                        backupRunning.set(false);
                        //The backup failed to store it
                        backupFailed.set(true);
                        //Set alerts to all players on the server
                        alertPlayers(minecraftServer, Component.translatable(FTBBackups.MOD_ID + ".backup.failed"));
                        //Log and print stacktraces
                        FTBBackups.LOGGER.error("Failed to create backup", e);
                        if (e instanceof FileAlreadyExistsException) {
                            TieredBackupTest.testBackupCount++;
                        }
                    }
                }, FTBBackups.backupExecutor).thenRun(() ->
                {
                    currentFuture = null;
                    //Set world save state to false to allow saves again
                    setNoSave(minecraftServer, false);
                    //If the backup failed then we don't need to do anything
                    if (backupFailed.get()) {
                        //This reset should not be needed but making sure anyway
                        backupFailed.set(false);
                        backupRunning.set(false);
                        return;
                    }

                    finishTime.set(System.nanoTime());
                    //Workout the time it took to create the backup
                    long elapsedTime = finishTime.get() - startTime.get();
                    //Set backup running state to false
                    backupRunning.set(false);
                    //Alert players that backup has finished being created
                    alertPlayers(minecraftServer, Component.translatable("Backup finished in " + format(elapsedTime) + (Config.cached().display_file_size ? " Size: " + FileUtils.getSizeString(backupLocation.toFile().length()) : "")));

                    String sha1;
                    float ratio = 1;
                    long backupSize = FileUtils.getSize(backupLocation.toFile());
                    if (format == Format.ZIP) {
                        //Get the sha1 of the new backup .zip to store to the json file
                        sha1 = FileUtils.getFileSha1(backupLocation);
                        //Do some math to figure out the ratio of compression
                        ratio = (float) backupSize / (float) FileUtils.getFolderSize(worldFolder.toFile());
                    } else {
                        sha1 = FileUtils.getDirectorySha1(backupLocation);
                    }

                    FTBBackups.LOGGER.info("Backup size " + FileUtils.getSizeString(backupLocation.toFile().length()) + " World Size " + FileUtils.getSizeString(FileUtils.getFolderSize(worldFolder.toFile())));
                    //Update backup entry data entry and mark it as complete.
                    backup.setRatio(ratio).setSha1(sha1).setComplete();
                    backup.setSize(backupSize);

                    updateJson();
                    FTBBackups.LOGGER.info("New backup created at " + backupLocation + " size: " + FileUtils.getSizeString(backupLocation) + " Took: " + format(elapsedTime) + " Sha1: " + sha1);

                    TieredBackupTest.testBackupCount++;
                });
            } else {
                //Create a new message for the failReason
                if (!failReason.isEmpty()) {
                    backupRunning.set(false);
                    String failMessage = "Unable to create backup, Reason: " + failReason;
                    //Alert players of the fail status using the message
                    alertPlayers(minecraftServer, Component.translatable(failMessage));
                    //Log the failMessage
                    FTBBackups.LOGGER.error(failMessage);
                    //Reset the fail to avoid confusion
                    failMessage = "";
                }
                backupRunning.set(false);
            }
        } catch (Exception e) {
            FTBBackups.LOGGER.error("An error occurred while running backup!", e);
            backupRunning.set(false);
        }
    }

    public static String genBackupFileName() {
        Calendar calendar = Calendar.getInstance();
        String date = calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1) + "-" + calendar.get(Calendar.DATE);
        String time = calendar.get(Calendar.HOUR_OF_DAY) + "-" + calendar.get(Calendar.MINUTE) + "-" + calendar.get(Calendar.SECOND);
        String backupName = date + "_" + time;
        if (Config.cached().backup_format == Format.ZIP) {
            backupName += ".zip";
        }
        return backupName;
    }

    public static void addBackup(Backup backup) {
        backups.getAndUpdate(backups1 ->
        {
            backups1.add(backup);
            return backups1;
        });
    }

    public static void removeBackup(Backup backup) {
        backups.getAndUpdate(backups1 ->
        {
            if (backups1.contains(backup)) {
                backups1.remove(backup);
                return backups1;
            }
            return backups1;
        });
    }

    @Nullable
    public static Backup getLatestBackup() {
        if (backups == null) return null;
        if (backups.get().isEmpty()) return null;
        Backup currentNewest = null;
        for (Backup backup : backups.get().getBackups()) {
            if (currentNewest == null) currentNewest = backup;
            if (backup.getCreateTime() > currentNewest.getCreateTime()) {
                currentNewest = backup;
            }
        }
        return currentNewest;
    }

    @Nullable
    public static Backup getOldestBackup() {
        if (backups.get().isEmpty()) return null;
        Backup currentOldest = null;
        for (Backup backup : backups.get().getBackups()) {
            if (backup.isProtected()) continue;
            if (currentOldest == null) currentOldest = backup;
            if (backup.getCreateTime() < currentOldest.getCreateTime()) {
                currentOldest = backup;
            }
        }
        return currentOldest;
    }

    public static void clean() {
        //Don't run clean if there is a backup already running, Or if in shutdown state
        if (FTBBackups.isShutdown) return;
        if (backupRunning.get()) return;
        if (backups == null) return;

        if (Config.cached().retention_mode == RetentionMode.MAX_BACKUPS) {
            cleanMax();
        } else {
            cleanTiered();
        }
    }

    private static void cleanMax() {
        int backupsNeedRemoving = 0;
        if (backups.get().unprotectedSize() > Config.cached().max_backups) {
            FTBBackups.LOGGER.info("More backups than " + Config.cached().max_backups + " found, Removing oldest backup");
            backupsNeedRemoving = (backups.get().unprotectedSize() - Config.cached().max_backups);
        } else if (isSpaceConstrained && Config.cached().free_space_if_needed) {
            FTBBackups.LOGGER.info("Insufficient space to create new backups, Removing oldest backup");
            isSpaceConstrained = false;
            backupsNeedRemoving = 1;
        }
        if (backupsNeedRemoving <= 0 || getOldestBackup() == null) return;

        for (int i = 0; i < backupsNeedRemoving; i++) {
            Backup oldest = getOldestBackup();
            deleteBackup(oldest);
        }
        verifyOldBackups();
    }

    private static void cleanTiered() {
        List<Backup> backups = new ArrayList<>(BackupHandler.backups.get().getBackups());
        //Don't touch protected backups
        backups.removeIf(Backup::isProtected);

        //Sort from newest to oldest
        backups.sort(Comparator.comparingLong(Backup::getCreateTime).reversed());

        if (backups.size() <= Config.cached().keep_latest) {
            return;
        }

        Map<Backup, String> backupsToKeep = new LinkedHashMap<>();

        //Keep the latest x backups
        if (Config.cached().keep_latest > 0) {
            for (Backup backup : backups.subList(0, Config.cached().keep_latest)) {
                backupsToKeep.put(backup, "Latest");
            }
        }

        computeRetained(backups, backupsToKeep, Config.cached().keep_hourly, Calendar.HOUR_OF_DAY);
        computeRetained(backups, backupsToKeep, Config.cached().keep_daily, Calendar.DAY_OF_YEAR);
        computeRetained(backups, backupsToKeep, Config.cached().keep_weekly, Calendar.WEEK_OF_YEAR);
        computeRetained(backups, backupsToKeep, Config.cached().keep_monthly, Calendar.MONTH);

        backups.removeAll(backupsToKeep.keySet());

        for (Backup backup : backups) {
            if (!TieredBackupTest.shouldRemoveBackup(backup)) {
                continue;
            }
            deleteBackup(backup);
        }
        verifyOldBackups();

        if (!backups.isEmpty()) {
            backupsToKeep.forEach((backup, rule) -> FTBBackups.LOGGER.info("Keeping Backup: {}, Rule: {}", new Date(backup.getCreateTime()), rule));
        }

        TieredBackupTest.cycleComplete();
    }

    private static void computeRetained(List<Backup> backups, Map<Backup, String> retained, int keepNumber, int timeUnit) {
        String name = timeUnit == Calendar.HOUR_OF_DAY ? "Hour" : timeUnit == Calendar.DAY_OF_YEAR ? "Day" : timeUnit == Calendar.WEEK_OF_YEAR ? "Week" : "Month";
        try {
            Calendar now = Calendar.getInstance();
            now.set(Calendar.MILLISECOND, 0);
            now.set(Calendar.SECOND, 0);
            now.set(Calendar.MINUTE, 0);
            if (timeUnit == Calendar.DAY_OF_YEAR) {
                now.set(Calendar.HOUR_OF_DAY, 0);
            }
            if (timeUnit == Calendar.WEEK_OF_YEAR) {
                now.set(Calendar.HOUR_OF_DAY, 0);
                now.set(Calendar.DAY_OF_WEEK, 0);
            }
            if (timeUnit == Calendar.MONTH) {
                now.set(Calendar.HOUR_OF_DAY, 0);
                now.set(Calendar.DAY_OF_MONTH, 0);
            }

            for (int i = 0; i < keepNumber; i++) {
                Calendar past = (Calendar) now.clone();
                past.add(timeUnit, -i);
                long end = past.getTimeInMillis();
                if (i == 0) {
                    //All time units lower than the current interval are zeroed out to ensure each retain window always starts at the same time.
                    //But this leaves a gap at i=0 where backups that should be kept can "fall through" and get deleted.
                    //This closes that gap.
                    end = Calendar.getInstance().getTimeInMillis();
                }
                past.add(timeUnit, -1);
                long start = past.getTimeInMillis();

                Backup latest = null;
                for (Backup backup : backups) {
                    long time = backup.getCreateTime();
                    //Always keep latest within time period.
                    if (time >= start && time < end && (latest == null || time > latest.getCreateTime())) {
                        latest = backup;
                    }
                }
                if (latest != null) {
                    TieredBackupTest.willKeep(name, latest);
                    String info = (retained.containsKey(latest) ? retained.get(latest) + "&" : "") + name;
                    retained.put(latest, info);
                }
            }
        } catch (Throwable e) {
            FTBBackups.LOGGER.error("An error occurred computing which backups to retain", e);
        }
    }

    private static void deleteBackup(Backup backup) {
        try {
            Path backupFile = Path.of(backup.getBackupLocation());
            if (Files.exists(backupFile)) {
                String log = "Removed old backup " + backupFile.getFileName();
                if (backup.getBackupFormat() == Format.DIRECTORY) {
                    org.apache.commons.io.FileUtils.deleteDirectory(backupFile.toFile());
                } else {
                    if (!Files.deleteIfExists(backupFile)) log = "Failed to remove backup " + backupFile.getFileName();
                }
                FTBBackups.LOGGER.info(log);
            }
        } catch (Exception e) {
            FTBBackups.LOGGER.info("An error occurred while deleting backup.", e);
        }
    }

    public static void loadJson() {
        Path json = defaultBackupLocation.resolve("backups.json");
        if (Files.exists(json)) {
            Gson gson = new Gson();
            try {
                FileReader fileReader = new FileReader(json.toFile());
                backups.getAndUpdate(backups1 -> (gson.fromJson(fileReader, Backups.class)));
                fileReader.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void updateJson() {
        try {
            String jsonString = "[]";
            if (!backups.get().isEmpty()) {
                jsonString = GSON.toJson(backups.get(), Backups.class);
            }
            writeToFile(jsonString);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void writeToFile(String json) {
        FTBBackups.LOGGER.info("Writing to file " + defaultBackupLocation.resolve("backups.json"));
        try (FileOutputStream fileOutputStream = new FileOutputStream(defaultBackupLocation.resolve("backups.json").toFile())) {
            IOUtils.write(json, fileOutputStream, Charset.defaultCharset());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public static boolean canCreateBackup() {
        File worldFile = worldFolder.toFile();
        if (!worldFile.exists() || !worldFile.isDirectory()) {
            FTBBackups.LOGGER.info("World folder does not exist or is not a directory: {}", worldFile.getAbsolutePath());
            failReason = "Invalid world folder";
            return false;
        }

        if (backupFolderPath == null) {
            failReason = "backup folder path is null";
            return false;
        }
        if (!backupFolderPath.toFile().exists()) {
            failReason = "backup folder does not exist";
            return false;
        }
        if (backupRunning.get()) {
            FTBBackups.LOGGER.info("Unable to start new backup as backup is already running");
            failReason = "Unable to start new backup as backup is already running";
            return false;
        }
        if (lastAutoBackup != 0 && Config.cached().manual_backups_time != 0) {
            if (System.currentTimeMillis() < (lastAutoBackup + 60000L)) {
                failReason = "Manuel backup was recently taken";
                return false;
            }
        }

        if (currentFuture != null) {
            failReason = "backup thread is somehow still running";
            FTBBackups.LOGGER.error("currentFuture is not null??");
            return false;
        }

        long minFreeSpace = Config.cached().minimum_free_space * 1000000L;
        long free = backupFolderPath.toFile().getFreeSpace() - minFreeSpace;
        long currentWorldSize = FileUtils.getFolderSize(worldFile);
        for (String p : Config.cached().additional_directories) {
            try {
                Path path = worldFolder.getParent().resolve(p);
                if (Files.exists(path) && Files.isDirectory(path)) {
                    currentWorldSize += FileUtils.getFolderSize(path.toFile());
                }
            } catch (Exception ignored) {
            }
        }

        currentWorldSize += FileUtils.getFolderSize(serverRoot, path -> FileUtils.matchesAny(serverRoot.relativize(path), Config.cached().additional_files));

        if (getLatestBackup() == null) {
            //This should be logged as an error because no new backups will be created until this is resolved.
            FTBBackups.LOGGER.error("Current world size: " + FileUtils.getSizeString(currentWorldSize) + " Current Available space: " + FileUtils.getSizeString(free));
            if (currentWorldSize > free) {
                failReason = "not enough free space on device";
                isSpaceConstrained = true;
                return false;
            }
        } else {
            long latestBackupSize = getLatestBackup().getSize();
            float ratio = getLatestBackup().getRatio();
            long expectedSize = (int) (Math.ceil(currentWorldSize * ratio) / 100) * 105L;

            FTBBackups.LOGGER.info("Last backup size: " + FileUtils.getSizeString(latestBackupSize) + " Current world size: " + FileUtils.getSizeString(currentWorldSize)
                    + " Current Available space: " + FileUtils.getSizeString(free) + " ExpectedSize " + FileUtils.getSizeString(expectedSize));
            if (expectedSize > free) {
                failReason = "not enough free space on device";
                isSpaceConstrained = true;
                return false;
            }
        }
        return true;
    }

    public static void verifyOldBackups() {
        if (backups == null) return;
        if (backups.get().isEmpty()) return;
        List<Backup> backupsCopy = new ArrayList<>(backups.get().getBackups());
        boolean update = false;
        for (Backup backup : backupsCopy) {
            FTBBackups.LOGGER.debug("Verifying backup " + backup.getBackupLocation());

            if (!Files.exists(Path.of(backup.getBackupLocation()))) {
                removeBackup(backup);
                update = true;
                FTBBackups.LOGGER.info("File missing, removing from backups " + backup.getBackupLocation());
            }
        }
        if (update){
            updateJson();
        }
    }

    public static void createBackupFolder(Path path) {
        if (!Files.exists(path)) {
            boolean backupFolderCreated = path.toFile().mkdirs();
            String log = backupFolderCreated ? "Created backup folder at " + path.toAbsolutePath() : "Failed to create backup folder at " + path.toAbsolutePath();
            FTBBackups.LOGGER.info(log);
        }
    }

    public static void setNoSave(MinecraftServer minecraftServer, boolean value) {
        for (ServerLevel level : minecraftServer.getAllLevels()) {
            if (level != null) {
                FTBBackups.LOGGER.info("Setting world " + level.dimension().location() + " save state to " + value);
                level.noSave = value;
            }
        }
    }

    public static void alertPlayers(MinecraftServer minecraftServer, Component message) {
        if (Config.cached().do_not_notify) return;
        if (Config.cached().notify_op_only && minecraftServer instanceof DedicatedServer) {
            for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
                if (player.hasPermissions(4)) {
                    player.displayClientMessage(message, false);
                }
            }
        } else {
            for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
                player.displayClientMessage(message, false);
            }
        }
    }

    public static String format(long nano) {
        Duration duration = Duration.ofNanos(nano);

        long mins = duration.toMinutes();
        long seconds = duration.minusMinutes(mins).toSeconds();
        long mili = duration.minusSeconds(seconds).toMillis();

        return mins + "m, " + seconds + "s, " + mili + "ms";
    }
}
