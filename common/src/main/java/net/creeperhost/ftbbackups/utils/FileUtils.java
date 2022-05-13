package net.creeperhost.ftbbackups.utils;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
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

    public static void pack(Path zipFilePath, Path serverRoot, Iterable<Path> sourcePaths) throws IOException {
        Path p = Files.createFile(zipFilePath);
        Map<String, Path> seenFiles = new HashMap<>();
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(p))) {
            for (Path sourcePath : sourcePaths) {
                try (Stream<Path> pathStream = Files.walk(sourcePath)) {
                    for (Path path : (Iterable<Path>) pathStream::iterator) {
                        if (Files.isDirectory(path)) continue;

                        packIntoZip(zs, serverRoot, path);
                    }
                }
            }
        }
    }

    private static void packIntoZip(ZipOutputStream zos, Path rootDir, Path file) throws IOException {
        // Don't pack session.lock files
        if (file.getFileName().toString().equals("session.lock")) return;
        // Ensure files are readable
        if (!Files.isReadable(file)) return;

        ZipEntry zipEntry = new ZipEntry(rootDir.relativize(file).toString());
        zos.putNextEntry(zipEntry);
        Files.copy(file, zos);
        zos.closeEntry();
    }

    public static boolean isChildOf(Path path, Path parent) {
        if (path == null) return false;
        if (path.equals(parent)) return true;

        return isChildOf(path.getParent(), parent);
    }

    public static String getSha1(Path path) {
        try {
            HashCode sha1HashCode = com.google.common.io.Files.asByteSource(path.toFile()).hash(Hashing.sha1());
            return sha1HashCode.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";
    }

    public static long getFolderSize(File folder) {
        long length = 0;
        File[] files = folder.listFiles();

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
}
