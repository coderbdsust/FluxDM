package com.fluxdm;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.*;

/**
 * Auto-downloads and manages yt-dlp and ffmpeg binaries.
 * Checks system PATH first, then falls back to a local bin directory.
 * On Windows: &lt;app-dir&gt;/bin/ (portable, next to the JAR).
 * On macOS/Linux: ~/.fluxdm/bin/ (follows Unix conventions).
 * Downloads from official sources if not found anywhere.
 */
public class BinaryManager {

    static final Path BIN_DIR = resolveBinDir();
    private static final Object LOCK = new Object();

    private enum OS { MACOS, LINUX, WINDOWS }
    private enum Arch { X86_64, ARM64 }

    private static final OS CURRENT_OS;
    private static final Arch CURRENT_ARCH;

    static {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("mac")) CURRENT_OS = OS.MACOS;
        else if (osName.contains("win")) CURRENT_OS = OS.WINDOWS;
        else CURRENT_OS = OS.LINUX;

        String arch = System.getProperty("os.arch").toLowerCase();
        CURRENT_ARCH = (arch.contains("aarch64") || arch.contains("arm64")) ? Arch.ARM64 : Arch.X86_64;
    }

    /**
     * Ensures yt-dlp is available. Checks system PATH, then ~/.fluxdm/bin/.
     * Downloads from GitHub releases if not found.
     */
    public static String ensureYtDlp(Consumer<String> status) {
        synchronized (LOCK) {
            String found = findExisting("yt-dlp", systemPaths("yt-dlp"));
            if (found != null) return found;

            String local = localPath("yt-dlp");
            if (isWorking(local, "--version")) return local;

            try {
                Files.createDirectories(BIN_DIR);
                String filename = CURRENT_OS == OS.WINDOWS ? "yt-dlp.exe" : "yt-dlp";
                String url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/" + filename;
                notify(status, "Downloading yt-dlp...");
                downloadFile(url, BIN_DIR.resolve(filename), status);
                makeExecutable(BIN_DIR.resolve(filename));

                String installed = localPath("yt-dlp");
                if (isWorking(installed, "--version")) {
                    notify(status, "yt-dlp ready");
                    return installed;
                }
            } catch (Exception e) {
                System.err.println("FluxDM: yt-dlp download failed: " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * Ensures ffmpeg is available. Checks system PATH, then ~/.fluxdm/bin/.
     * Downloads platform-specific static build if not found.
     * Returns null if unavailable (ffmpeg is optional — app degrades gracefully).
     */
    public static String ensureFfmpeg(Consumer<String> status) {
        synchronized (LOCK) {
            String found = findExisting("ffmpeg", systemPaths("ffmpeg"));
            if (found != null) return found;

            String local = localPath("ffmpeg");
            if (isWorking(local, "-version")) return local;

            try {
                Files.createDirectories(BIN_DIR);
                notify(status, "Downloading ffmpeg...");

                switch (CURRENT_OS) {
                    case MACOS   -> downloadFfmpegMacOS(status);
                    case LINUX   -> downloadFfmpegLinux(status);
                    case WINDOWS -> downloadFfmpegWindows(status);
                }

                String installed = localPath("ffmpeg");
                if (isWorking(installed, "-version")) {
                    notify(status, "ffmpeg ready");
                    return installed;
                }
            } catch (Exception e) {
                System.err.println("FluxDM: ffmpeg download failed: " + e.getMessage());
            }
            return null;
        }
    }

    // ─── Platform-specific ffmpeg downloads ───────────────────────────────────

    private static void downloadFfmpegMacOS(Consumer<String> status) throws IOException {
        Path zipFile = BIN_DIR.resolve("ffmpeg-dl.zip");
        try {
            downloadFile("https://evermeet.cx/ffmpeg/get/zip", zipFile, status);
            extractFromZip(zipFile, "ffmpeg", BIN_DIR.resolve("ffmpeg"));
            makeExecutable(BIN_DIR.resolve("ffmpeg"));
        } finally {
            Files.deleteIfExists(zipFile);
        }
    }

    private static void downloadFfmpegLinux(Consumer<String> status) throws IOException {
        String archSuffix = CURRENT_ARCH == Arch.ARM64 ? "linuxarm64" : "linux64";
        String url = "https://github.com/BtbN/FFmpeg-Builds/releases/latest/download/"
                + "ffmpeg-master-latest-" + archSuffix + "-gpl.tar.xz";
        Path tarFile = BIN_DIR.resolve("ffmpeg-dl.tar.xz");
        try {
            downloadFile(url, tarFile, status);
            extractFfmpegFromTar(tarFile);
        } finally {
            Files.deleteIfExists(tarFile);
        }
    }

    private static void downloadFfmpegWindows(Consumer<String> status) throws IOException {
        String url = "https://github.com/BtbN/FFmpeg-Builds/releases/latest/download/"
                + "ffmpeg-master-latest-win64-gpl.zip";
        Path zipFile = BIN_DIR.resolve("ffmpeg-dl.zip");
        try {
            downloadFile(url, zipFile, status);
            extractFromZip(zipFile, "ffmpeg.exe", BIN_DIR.resolve("ffmpeg.exe"));
        } finally {
            Files.deleteIfExists(zipFile);
        }
    }

    // ─── Extraction helpers ──────────────────────────────────────────────────

    private static void extractFromZip(Path zipFile, String targetName, Path destination) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = Path.of(entry.getName()).getFileName().toString();
                if (name.equals(targetName)) {
                    Files.copy(zis, destination, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new IOException(targetName + " not found in archive");
    }

    private static void extractFfmpegFromTar(Path tarFile) throws IOException {
        Path tempDir = Files.createTempDirectory("fluxdm-ffmpeg");
        try {
            Process p = new ProcessBuilder("tar", "-xf", tarFile.toString(), "-C", tempDir.toString())
                    .redirectErrorStream(true).start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            try { p.waitFor(120, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

            try (var stream = Files.walk(tempDir)) {
                Optional<Path> found = stream
                        .filter(path -> path.getFileName().toString().equals("ffmpeg") && Files.isRegularFile(path))
                        .findFirst();
                if (found.isPresent()) {
                    Files.copy(found.get(), BIN_DIR.resolve("ffmpeg"), StandardCopyOption.REPLACE_EXISTING);
                    makeExecutable(BIN_DIR.resolve("ffmpeg"));
                } else {
                    throw new IOException("ffmpeg binary not found in archive");
                }
            }
        } finally {
            deleteRecursively(tempDir);
        }
    }

    // ─── Utility methods ─────────────────────────────────────────────────────

    private static Path resolveBinDir() {
        if (CURRENT_OS == OS.WINDOWS) {
            try {
                Path jarDir = Path.of(BinaryManager.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).getParent();
                return jarDir.resolve("bin");
            } catch (Exception e) {
                return Path.of(System.getProperty("user.dir"), "bin");
            }
        }
        return Path.of(System.getProperty("user.home"), ".fluxdm", "bin");
    }

    /**
     * Returns platform-appropriate search paths for a binary.
     */
    private static String[] systemPaths(String binary) {
        String home = System.getProperty("user.home");
        String exe = CURRENT_OS == OS.WINDOWS ? binary + ".exe" : binary;
        List<String> paths = new ArrayList<>();
        if (CURRENT_OS == OS.WINDOWS) {
            paths.add(BIN_DIR.resolve(exe).toString());
            paths.add(Path.of(System.getenv("LOCALAPPDATA") != null
                    ? System.getenv("LOCALAPPDATA") : home, binary, "bin", exe).toString());
            paths.add(Path.of("C:\\", binary, "bin", exe).toString());
            paths.add(Path.of("C:\\", binary, exe).toString());
        } else {
            paths.add("/opt/homebrew/bin/" + binary);
            paths.add("/usr/local/bin/" + binary);
            paths.add("/opt/local/bin/" + binary);
            paths.add(Path.of(home, "bin", binary).toString());
            paths.add(Path.of(home, ".local", "bin", binary).toString());
            paths.add("/usr/bin/" + binary);
        }
        paths.add(binary); // bare name — relies on PATH
        return paths.toArray(String[]::new);
    }

    /**
     * Finds a binary on the system without downloading.
     * Used by DownloadTask to locate yt-dlp/ffmpeg.
     */
    public static String findBinary(String binary) {
        String found = findExisting(binary, systemPaths(binary));
        if (found != null) return found;
        String local = localPath(binary);
        if (isWorking(local, binary.equals("ffmpeg") ? "-version" : "--version")) return local;
        return null;
    }

    private static String findExisting(String binary, String... paths) {
        String flag = binary.equals("ffmpeg") ? "-version" : "--version";
        for (String path : paths) {
            if (isWorking(path, flag)) return path;
        }
        return null;
    }

    private static boolean isWorking(String path, String flag) {
        try {
            Process p = new ProcessBuilder(path, flag).redirectErrorStream(true).start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String localPath(String name) {
        String filename = CURRENT_OS == OS.WINDOWS ? name + ".exe" : name;
        return BIN_DIR.resolve(filename).toString();
    }

    private static void downloadFile(String urlStr, Path target, Consumer<String> status) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", "FluxDM/2.0");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(120000);
        conn.setInstanceFollowRedirects(true);

        // Manual redirect following (GitHub cross-domain redirects)
        int code = conn.getResponseCode();
        int hops = 0;
        while ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308) && hops++ < 10) {
            String loc = conn.getHeaderField("Location");
            conn.disconnect();
            if (loc == null) break;
            conn = (HttpURLConnection) new URL(loc).openConnection();
            conn.setRequestProperty("User-Agent", "FluxDM/2.0");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
            conn.setInstanceFollowRedirects(true);
            code = conn.getResponseCode();
        }
        if (code != 200) throw new IOException("HTTP " + code + " from " + urlStr);

        long total = conn.getContentLengthLong();
        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(target.toFile())) {
            byte[] buf = new byte[131072];
            long dl = 0;
            int n, lastPct = -1;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                dl += n;
                if (total > 0) {
                    int pct = (int) (dl * 100 / total);
                    if (pct != lastPct) {
                        notify(status, "Downloading... " + pct + "%");
                        lastPct = pct;
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void makeExecutable(Path path) {
        if (CURRENT_OS == OS.WINDOWS) return;
        try {
            Set<PosixFilePermission> perms = new HashSet<>(Files.getPosixFilePermissions(path));
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(path, perms);
        } catch (Exception ignored) {}
    }

    private static void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    private static void notify(Consumer<String> status, String msg) {
        if (status != null) status.accept(msg);
        System.out.println("FluxDM: " + msg);
    }
}