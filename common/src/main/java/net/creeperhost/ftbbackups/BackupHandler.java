package net.creeperhost.ftbbackups;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.piegames.blockmap.MinecraftDimension;
import de.piegames.blockmap.renderer.RegionRenderer;
import de.piegames.blockmap.renderer.RenderSettings;
import de.piegames.blockmap.repack.org.joml.Vector2ic;
import de.piegames.blockmap.world.Region;
import de.piegames.blockmap.world.RegionFolder;
import net.creeperhost.ftbbackups.config.Config;
import net.creeperhost.ftbbackups.data.Backup;
import net.creeperhost.ftbbackups.data.Backups;
import net.creeperhost.ftbbackups.utils.FileUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class BackupHandler {

    private static Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Path serverRoot;
    private static Path backupFolderPath;
    private static Path worldFolder;
    public static final AtomicBoolean backupRunning = new AtomicBoolean(false);
    private static final AtomicBoolean backupFailed = new AtomicBoolean(false);
    private static AtomicReference<String> backupPreview = new AtomicReference<>("");
    public static boolean isDirty = false;

    public static AtomicReference<Backups> backups = new AtomicReference<>(new Backups());
    private static String failReason = "";
    private static long lastAutoBackup = 0;

    public static CompletableFuture<Void> currentFuture;
    public static Path defaultBackupLocation;

    public static void init(MinecraftServer minecraftServer) {
        serverRoot = minecraftServer.getServerDirectory().toPath().normalize().toAbsolutePath();
        defaultBackupLocation = serverRoot.resolve("backups");

        if(!Config.cached().backup_location.equalsIgnoreCase("."))
        {
            try
            {
                Path configPath = Path.of(Config.cached().backup_location);
                if(Files.exists(configPath))
                {
                    FTBBackups.LOGGER.info("Using configured backups directory at {}" , configPath.toAbsolutePath());
                    backupFolderPath = configPath;
                }
                else
                {
                    FTBBackups.LOGGER.error(configPath.toAbsolutePath() + " does not exist, please create the directory before continuing");
                    backupFolderPath = defaultBackupLocation;
                }
            }
            catch (Exception e)
            {
                FTBBackups.LOGGER.error("Unable to find backup folder from config {} using default {}", Config.cached().backup_location, defaultBackupLocation.toAbsolutePath());
                e.printStackTrace();
                backupFolderPath = defaultBackupLocation;
            }
        }
        else
        {
            backupFolderPath = defaultBackupLocation;
        }
        createBackupFolder(defaultBackupLocation);
        createBackupFolder(backupFolderPath);

        loadJson();
        FTBBackups.LOGGER.info("Starting backup cleaning thread");
        if(FTBBackups.backupExecutor == null || FTBBackups.backupExecutor.isShutdown())
        {
            FTBBackups.backupExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("FTB Backups backup thread %d").build());
        }
        if(FTBBackups.backupCleanerWatcherExecutorService.isShutdown())
        {
            FTBBackups.backupCleanerWatcherExecutorService = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder().setNameFormat("FTB Backups scheduled executor %d").build());
        }
        FTBBackups.backupCleanerWatcherExecutorService.scheduleAtFixedRate(BackupHandler::clean, 0, 30, TimeUnit.SECONDS);
    }

    public static String createPreview(MinecraftServer minecraftServer) {
        try
        {
            MinecraftDimension dim = MinecraftDimension.OVERWORLD;
            RenderSettings settings = new RenderSettings();
            RegionRenderer renderer = new RegionRenderer(settings);
            Path worldPath = minecraftServer.getWorldPath(LevelResource.ROOT).toAbsolutePath();
            Path dimPath = worldPath.resolve(dim.getRegionPath());
            Path previewPath = worldPath.resolve("backupPreview");
            RegionFolder.WorldRegionFolder w = RegionFolder.WorldRegionFolder.load(dimPath, renderer, false);
            RegionFolder.CachedRegionFolder r = RegionFolder.CachedRegionFolder.create(w, false, previewPath);
            try {
                Files.walk(previewPath).forEach((f) -> {
                    try {
                        Files.deleteIfExists(f);
                    } catch (IOException ignored) {
                    }
                });
                Files.deleteIfExists(previewPath);
                Files.createDirectories(previewPath);
            } catch (Exception ignored) {}
            Vector2ic lastPos = null;
            long lastTimestamp = 0;
            for (Vector2ic p : w.listRegions())
            {
                try
                {
                    long thisTimestamp = w.getTimestamp(p);
                    //TODO: Implement size checking too, later.
                    if (thisTimestamp > lastTimestamp)
                    {
                        lastPos = p;
                        lastTimestamp = thisTimestamp;
                    }
                } catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (lastPos != null) {
                Region render = r.render(lastPos);
                ImageIO.write(render.getImage(), "png", baos);
            }
            byte[] image = baos.toByteArray();
            try {
                Files.walk(previewPath).forEach((f) -> {
                    try {
                        Files.deleteIfExists(f);
                    } catch (IOException ignored) {
                    }
                });
                Files.deleteIfExists(previewPath);
            } catch(Exception ignored) {}
            baos.close();
            return "data:image/png;base64, " + Base64.getEncoder().encodeToString(image);
        } catch (Exception ex) {
            ex.printStackTrace();
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
        if(FTBBackups.isShutdown) return;

        if (Config.cached().only_if_players_been_online && !BackupHandler.isDirty)
        {
            FTBBackups.LOGGER.info("Skipping backup, no players have been online since last backup.");
            return;
        }
        worldFolder = minecraftServer.getWorldPath(LevelResource.ROOT).toAbsolutePath();
        FTBBackups.LOGGER.info("Found world folder at " + worldFolder);
        Calendar calendar = Calendar.getInstance();
        String date = calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1) + "-" + calendar.get(Calendar.DATE);
        String time = calendar.get(Calendar.HOUR_OF_DAY) + "-" + calendar.get(Calendar.MINUTE) + "-" + calendar.get(Calendar.SECOND);
        String backupName = date + "_" + time + ".zip";
        Path backupLocation = backupFolderPath.resolve(backupName);

        if (canCreateBackup())
        {
            lastAutoBackup = System.currentTimeMillis();

            backupRunning.set(true);
            //Force save all player data before we start the backup
            minecraftServer.getPlayerList().saveAll();
            //Force save all the chunk data
            minecraftServer.saveAllChunks(true, true, true);
            //Set the worlds not to save while we are creating a backup
            setNoSave(minecraftServer, true);
            //Store the time we started the backup
            AtomicLong startTime = new AtomicLong(System.nanoTime());
            //Store the finishTime outside the thread for later use
            AtomicLong finishTime = new AtomicLong();

            //Start the backup process in its own thread
            currentFuture = CompletableFuture.runAsync(() ->
            {
                try {
                    //Warn all online players that the server is going to start creating a backup
                    alertPlayers(minecraftServer, Component.translatable(FTBBackups.MOD_ID + ".backup.starting"));
                    //Create the full path to this backup
                    Path backupPath = backupFolderPath.resolve(backupName);
                    //Create a zip of the world folder and store it in the /backup folder
                    List<Path> backupPaths = new LinkedList<>();
                    backupPaths.add(worldFolder);
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
                    FileUtils.pack(backupPath, serverRoot, backupPaths);
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
                    FTBBackups.LOGGER.error("Failed to create backup");
                    //Print the stacktrace
                    e.printStackTrace();
                }
            }, FTBBackups.backupExecutor).thenRun(() ->
            {
                currentFuture = null;
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
                //Set world save state to false to allow saves again
                setNoSave(minecraftServer, false);
                //Alert players that backup has finished being created
                alertPlayers(minecraftServer, Component.translatable("Backup finished in " + format(elapsedTime) + (Config.cached().display_file_size ? " Size: " + FileUtils.getSizeString(backupLocation.toFile().length()) : "")));
                //Get the sha1 of the new backup .zip to store to the json file
                String sha1 = FileUtils.getSha1(backupLocation);
                //Do some math to figure out the ratio of compression
                float ratio = (float) backupLocation.toFile().length() / (float) FileUtils.getFolderSize(worldFolder.toFile());
                FTBBackups.LOGGER.info("Backup size " + FileUtils.getSizeString(backupLocation.toFile().length()) + " World Size " + FileUtils.getSizeString(FileUtils.getFolderSize(worldFolder.toFile())));
                //Create the backup data entry to store to the json file

                Backup backup = new Backup(worldFolder.normalize().getFileName().toString(), System.currentTimeMillis(), backupLocation.toString(), FileUtils.getSize(backupLocation.toFile()), ratio, sha1, backupPreview.get(), protect, name);
                addBackup(backup);

                updateJson();
                FTBBackups.LOGGER.info("New backup created at " + backupLocation + " size: " + FileUtils.getSizeString(backupLocation) + " Took: " + format(elapsedTime) + " Sha1: " + sha1);
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
        if (backups == null) return null;
        if (backups.get().isEmpty()) return null;
        Backup currentOldest = null;
        for (Backup backup : backups.get().getBackups()) {
            if(backup.isProtected()) continue;
            if (currentOldest == null) currentOldest = backup;
            if (backup.getCreateTime() < currentOldest.getCreateTime()) {
                currentOldest = backup;
            }
        }
        return currentOldest;
    }

    public static void clean() {
        //Don't run clean if there is a backup already running
        try {
            if(FTBBackups.isShutdown) return;
            if (backupRunning.get()) return;
            if (backups.get().unprotectedSize() > Config.cached().max_backups) {
                FTBBackups.LOGGER.info("More backups than " + Config.cached().max_backups + " found, Removing oldest backup");
                int backupsNeedRemoving = (backups.get().unprotectedSize() - Config.cached().max_backups);
                if (backupsNeedRemoving > 0 && getOldestBackup() != null) {
                    for (int i = 0; i < backupsNeedRemoving; i++) {
                        Path backupFile = Path.of(getOldestBackup().getBackupLocation());
                        if (Files.exists(backupFile)) {
                            boolean removed = Files.deleteIfExists(backupFile);
                            String log = removed ? "Removed old backup " + backupFile.getFileName() : " Failed to remove backup " + backupFile.getFileName();
                            FTBBackups.LOGGER.info(log);
                        }
                    }
                    verifyOldBackups();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            fileOutputStream.close();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public static boolean canCreateBackup() {
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
            if (System.currentTimeMillis()< (lastAutoBackup + 60000L)) {
                failReason = "Manuel backup was recently taken";
                return false;
            }
        }

        if(currentFuture != null)
        {
            failReason = "backup thread is somehow still running";
            FTBBackups.LOGGER.error("currentFuture is not null??");
            return false;
        }

        long free = backupFolderPath.toFile().getFreeSpace();
        long currentWorldSize = FileUtils.getFolderSize(worldFolder.toFile());
        for (String p : Config.cached().additional_directories) {
            try {
                Path path = worldFolder.getParent().resolve(p);
                if (Files.exists(path) && Files.isDirectory(path)) {
                    currentWorldSize += FileUtils.getFolderSize(path.toFile());
                }
            } catch(Exception ignored) {}
        }

        if (getLatestBackup() == null) {
            FTBBackups.LOGGER.info("Current world size: " + FileUtils.getSizeString(currentWorldSize) + " Current free space: " + FileUtils.getSizeString(free));
            if (currentWorldSize > free) {
                failReason = "not enough free space on device";
                return false;
            }
        } else {
            long latestBackupSize = getLatestBackup().getSize();
            float ratio = getLatestBackup().getRatio();
            long expectedSize = (int) (Math.ceil(currentWorldSize * ratio) / 100) * 105L;

            FTBBackups.LOGGER.info("Last backup size: " + FileUtils.getSizeString(latestBackupSize) + " Current world size: " + FileUtils.getSizeString(currentWorldSize)
                    + " Current free space: " + FileUtils.getSizeString(free) + " ExpectedSize " + FileUtils.getSizeString(expectedSize));
            if (expectedSize > free) {
                failReason = "not enough free space on device";
                return false;
            }
        }
        return true;
    }

    public static void verifyOldBackups() {
        if (backups == null) return;
        if (backups.get().isEmpty()) return;
        List<Backup> backupsCopy = new ArrayList<>(backups.get().getBackups());
        for (Backup backup : backupsCopy) {
            FTBBackups.LOGGER.debug("Verifying backup " + backup.getBackupLocation());

            if (!Files.exists(Path.of(backup.getBackupLocation()))) {
                removeBackup(backup);
                FTBBackups.LOGGER.info("File missing, removing from backups " + backup.getBackupLocation());
            }
        }
        updateJson();
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

    public static String format(long nano)
    {
        Duration duration = Duration.ofNanos(nano);

        long mins = duration.toMinutes();
        long seconds  = duration.minusMinutes(mins).toSeconds();
        long mili = duration.minusSeconds(seconds).toMillis();

        return mins + "m, " +  seconds + "s, " + mili + "ms";
    }
}
