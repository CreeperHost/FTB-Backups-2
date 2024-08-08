package net.creeperhost.ftbbackups.utils;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import io.airlift.compress.zstd.ZstdOutputStream;
import net.creeperhost.ftbbackups.FTBBackups;
import net.creeperhost.ftbbackups.config.Config;
import net.creeperhost.ftbbackups.config.Format;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarOutputStream;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileUtils {

    public static final long KB = 1024L;
    public static final long MB = KB * 1024L;
    public static final long GB = MB * 1024L;
    public static final long TB = GB * 1024L;

    public static final double KB_D = 1024D;
    public static final double MB_D = KB_D * 1024D;
    public static final double GB_D = MB_D * 1024D;
    public static final double TB_D = GB_D * 1024D;

    public static void copy(Path outputDirectory, Path serverRoot, Iterable<Path> sourcePaths) throws IOException {
        Path dir = Files.createDirectory(outputDirectory);

        for (Path sourcePath : sourcePaths) {
            if (!Files.isDirectory(sourcePath)) {
                Path relFile = serverRoot.relativize(sourcePath);
                if (matchesAny(relFile, Config.cached().excluded)) continue;
                Path destFile = dir.resolve(relFile);
                Files.createDirectories(destFile.getParent());
                Files.copy(sourcePath, destFile);
            } else {
                try (Stream<Path> pathStream = Files.walk(sourcePath)) {
                    for (Path path : (Iterable<Path>) pathStream::iterator) {
                        if (Files.isDirectory(path)) continue;
                        Path relFile = serverRoot.relativize(path);
                        if (matchesAny(relFile, Config.cached().excluded)) continue;
                        Path destFile = dir.resolve(relFile);
                        Files.createDirectories(destFile.getParent());
                        Files.copy(path, destFile);
                    }
                }
            }
        }
    }

    public static void compress(Path zipFilePath, Path serverRoot, Iterable<Path> sourcePaths, Format format) throws IOException {
        Path p = Files.createFile(zipFilePath);
        try (OutputStream f = Files.newOutputStream(p);
             ZipOutputStream zipOut = format == Format.ZIP ? new ZipOutputStream(f) : null;
             TarOutputStream zstdOut = format == Format.ZSTD ? new TarOutputStream(new ZstdOutputStream(f)) : null) {
            for (Path sourcePath : sourcePaths) {
                if (!Files.isDirectory(sourcePath)) {
                    Path relFile = serverRoot.relativize(sourcePath);
                    if (matchesAny(relFile, Config.cached().excluded)) continue;
                    if (format == Format.ZIP) {
                        packIntoZip(zipOut, serverRoot, sourcePath);
                    } else {
                        packIntoTar(zstdOut, serverRoot, sourcePath);
                    }
                } else {
                    try (Stream<Path> pathStream = Files.walk(sourcePath)) {
                        for (Path path : (Iterable<Path>) pathStream::iterator) {
                            if (Files.isDirectory(path)) continue;
                            Path relFile = serverRoot.relativize(path);
                            if (matchesAny(relFile, Config.cached().excluded)) continue;
                            if (format == Format.ZIP) {
                                packIntoZip(zipOut, serverRoot, path);
                            } else {
                                packIntoTar(zstdOut, serverRoot, path);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean shouldSkipPacking(Path file) {
        // Don't pack session.lock files
        if (file.getFileName().toString().equals("session.lock")) return true;
        // Don't try and copy a file that does not exist
        if (!file.toFile().exists()) return true;
        // Ensure files are readable
        if (!Files.isReadable(file)) return true;

        return false;
    }

    private static void packIntoZip(ZipOutputStream zos, Path rootDir, Path file) throws IOException {
        if (shouldSkipPacking(file)) return;

        ZipEntry zipEntry = new ZipEntry(rootDir.relativize(file).toString());
        zos.putNextEntry(zipEntry);
        updateZipEntry(zipEntry, file);
        try {
            Files.copy(file, zos);
        } catch (Exception ignored) {
        }
        zos.closeEntry();
    }

    public static void updateZipEntry(ZipEntry zipEntry, Path path) {
        try {
            BasicFileAttributes basicFileAttributes = Files.readAttributes(path, BasicFileAttributes.class);
            zipEntry.setLastModifiedTime(basicFileAttributes.lastModifiedTime());
            zipEntry.setCreationTime(basicFileAttributes.creationTime());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void packIntoTar(TarOutputStream taos, Path rootDir, Path file) throws IOException {
        if (shouldSkipPacking(file)) return;

        TarEntry tarEntry = new TarEntry(file.toFile(), rootDir.relativize(file).toString());
        taos.putNextEntry(tarEntry);
        updateTarEntry(tarEntry, file);
        try {
            Files.copy(file, taos);
        } catch (Exception ignored) {
        }
    }

    public static void updateTarEntry(TarEntry tarEntry, Path path) {
        try {
            BasicFileAttributes basicFileAttributes = Files.readAttributes(path, BasicFileAttributes.class);
            tarEntry.setModTime(basicFileAttributes.lastModifiedTime().toMillis());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isChildOf(Path path, Path parent) {
        if (path == null) return false;
        if (path.equals(parent)) return true;

        return isChildOf(path.getParent(), parent);
    }

    public static String getFileSha1(Path path) {
        try {
            HashCode sha1HashCode = com.google.common.io.Files.asByteSource(path.toFile()).hash(Hashing.sha1());
            return sha1HashCode.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";
    }

    public static String getDirectorySha1(Path directory) {
        try {
            Hasher hasher = Hashing.sha1().newHasher();
            try (Stream<Path> pathStream = Files.walk(directory)) {
                for (Path path : (Iterable<Path>) pathStream::iterator) {
                    if (Files.isDirectory(path)) continue;
                    HashCode hash = com.google.common.io.Files.asByteSource(path.toFile()).hash(Hashing.sha1());
                    hasher.putBytes(hash.asBytes());
                }
            }
            return hasher.hash().toString();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";
    }

    public static long getFolderSize(File folder) {
        long length = 0;
        File[] files = folder.listFiles();
        if (files == null) {
            FTBBackups.LOGGER.warn("Attempt to get folder size for invalid folder: {}", folder.getAbsolutePath());
            if (folder.isFile()) {
                return folder.length();
            } else {
                return 0;
            }
        }

        int count = files.length;

        for (int i = 0; i < count; i++) {
            if (files[i].isFile()) {
                length += files[i].length();
            } else {
                length += getFolderSize(files[i]);
            }
        }
        return length;
    }

    public static long getFolderSize(Path folder, Predicate<Path> includeFile) {
        long size = 0;

        try {
            if (!Files.isDirectory(folder)) {
                return Files.size(folder);
            }

            try (Stream<Path> pathStream = Files.walk(folder)) {
                for (Path path : (Iterable<Path>) pathStream::iterator) {
                    if (Files.isDirectory(path) || !includeFile.test(path)) continue;
                    size += Files.size(path);
                }
            }
        } catch (IOException e) {
            FTBBackups.LOGGER.warn("Error occurred while calculating file size", e);
        }

        return size;
    }

//    public static void createTarGzipFolder(Path source, Path out) throws IOException
//    {
//        GzipParameters gzipParameters = new GzipParameters();
//        gzipParameters.setCompressionLevel(9);
//
//        try (OutputStream fOut = Files.newOutputStream(out); BufferedOutputStream buffOut = new BufferedOutputStream(fOut);
//             GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(buffOut, gzipParameters); TarArchiveOutputStream tOut = new TarArchiveOutputStream(gzOut))
//        {
//            Files.walkFileTree(source, new SimpleFileVisitor<>()
//            {
//                @Override
//                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
//                {
//                    // only copy files, no symbolic links
//                    if (attributes.isSymbolicLink())
//                    {
//                        return FileVisitResult.CONTINUE;
//                    }
//                    Path targetFile = source.relativize(file);
//                    try
//                    {
//                        if(!file.toFile().getName().equals("session.lock") && file.toFile().canRead())
//                        {
//                            TarArchiveEntry tarEntry = new TarArchiveEntry(file.toFile(), targetFile.toString());
//                            tOut.putArchiveEntry(tarEntry);
//                            Files.copy(file, tOut);
//                            tOut.closeArchiveEntry();
//                        }
//                    } catch (IOException e)
//                    {
//                        e.printStackTrace();
//                    }
//                    return FileVisitResult.CONTINUE;
//                }
//
//                @Override
//                public FileVisitResult visitFileFailed(Path file, IOException exc) {
//                    System.err.printf("Unable to tar.gz : %s%n%s%n", file, exc);
//                    return FileVisitResult.CONTINUE;
//                }
//            });
//            tOut.finish();
//        }
//    }

    public static String getSizeString(double b) {
        if (b >= TB_D) {
            return String.format("%.1fTB", b / TB_D);
        } else if (b >= GB_D) {
            return String.format("%.1fGB", b / GB_D);
        } else if (b >= MB_D) {
            return String.format("%.1fMB", b / MB_D);
        } else if (b >= KB_D) {
            return String.format("%.1fKB", b / KB_D);
        }

        return ((long) b) + "B";
    }

    public static String getSizeString(Path path) {
        return getSizeString(getSize(path.toFile()));
    }

    public static String getSizeString(File file) {
        return getSizeString(getSize(file));
    }

    public static long getSize(File file) {
        if (!file.exists()) {
            return 0L;
        } else if (file.isFile()) {
            return file.length();
        } else if (file.isDirectory()) {
            long length = 0L;
            File[] f1 = file.listFiles();
            if (f1 != null && f1.length > 0) {
                for (File aF1 : f1) {
                    length += getSize(aF1);
                }
            }
            return length;
        }
        return 0L;
    }

    public static boolean matchesAny(Path relPath, List<String> filters) {
        for (String filter : filters) {
            filter = filter.replaceAll("\\\\", "/");

            boolean directory = filter.endsWith("/");

            boolean sw = filter.startsWith("*");
            if (sw) filter = filter.substring(1);

            boolean ew = filter.endsWith("*") || directory;
            if (ew) filter = filter.substring(0, filter.length() - 1);

            boolean wildCard = sw || ew;

            boolean path = filter.contains("/");
            //Relative paths do not have a leading /
            if (filter.startsWith("/") && !sw) filter = filter.substring(1);

            //Is File Exclusion (e.g. fileName.txt)
            if (!path && !wildCard) {
                if (relPath.getFileName().toString().equals(filter)) {
                    return true;
                }
            }
            //Is Path Exclusion (e.g. world/region/fileName.txt)
            else if (path && !wildCard) {
                if (relPath.toString().equals(filter)) {
                    return true;
                }
            }
            // (e.g. *directory/file*)
            else if (sw && ew) {
                if (relPath.toString().contains(filter)) {
                    return true;
                }
            }
            // (e.g. *directory/fileName.txt)
            else if (sw) {
                if (relPath.toString().endsWith(filter)) {
                    return true;
                }
            }
            // (e.g. directory/file*)
            else {
                if (relPath.toString().startsWith(filter)) {
                    return true;
                }
            }
        }

        return false;
    }
}
