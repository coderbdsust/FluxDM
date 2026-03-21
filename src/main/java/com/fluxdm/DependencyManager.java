package com.fluxdm;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.*;

/**
 * Locates or auto-downloads yt-dlp and ffmpeg binaries.
 * Downloaded binaries are stored in ~/.fluxdm/bin/
 */
public class DependencyManager {

    private static final Path BIN_DIR = Paths.get(System.getProperty("user.home"), ".fluxdm", "bin");

    private static final boolean IS_MAC   = System.getProperty("os.name", "").toLowerCase().contains("mac");
    private static final boolean IS_LINUX = System.getProperty("os.name", "").toLowerCase().contains("linux");

    // ─── yt-dlp ──────────────────────────────────────────────────────────────

    /** Find yt-dlp on the system or in BIN_DIR. Returns absolute path or null. */
    public static String findYtDlp() {
        String home = System.getProperty("user.home");
        String[] candidates = {
            BIN_DIR.resolve("yt-dlp").toString(),
            "/opt/homebrew/bin/yt-dlp",
            "/usr/local/bin/yt-dlp",
            home + "/.local/bin/yt-dlp",
            "/usr/bin/yt-dlp",
            "yt-dlp",
        };
        return probeExe(candidates, "--version");
    }

    /**
     * Ensure yt-dlp is available — find it or download it.
     * @param status callback for progress messages (may be null)
     * @return absolute path to yt-dlp binary
     * @throws IOException if download fails
     */
    public static String ensureYtDlp(Consumer<String> status) throws IOException {
        String found = findYtDlp();
        if (found != null) return found;

        if (status != null) status.accept("Downloading yt-dlp...");

        Files.createDirectories(BIN_DIR);
        String binaryName = IS_MAC ? "yt-dlp_macos" : "yt-dlp_linux";
        String downloadUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/" + binaryName;

        Path target = BIN_DIR.resolve("yt-dlp");
        downloadFile(downloadUrl, target, status);
        makeExecutable(target);

        // Verify it works
        String path = target.toString();
        if (probeExe(new String[]{path}, "--version") == null) {
            throw new IOException("Downloaded yt-dlp binary is not functional");
        }
        if (status != null) status.accept("yt-dlp ready");
        return path;
    }

    // ─── ffmpeg ──────────────────────────────────────────────────────────────

    /** Find ffmpeg on the system or in BIN_DIR. Returns absolute path or null. */
    public static String findFfmpeg() {
        String home = System.getProperty("user.home");
        String[] candidates = {
            BIN_DIR.resolve("ffmpeg").toString(),
            "/opt/homebrew/bin/ffmpeg",
            "/usr/local/bin/ffmpeg",
            "/opt/local/bin/ffmpeg",
            home + "/bin/ffmpeg",
            home + "/.local/bin/ffmpeg",
            "/usr/bin/ffmpeg",
            "ffmpeg",
        };
        String result = probeExe(candidates, "-version");
        if (result != null) return result;

        // Last resort: which/where
        try {
            String whichCmd = IS_MAC || IS_LINUX ? "which" : "where";
            Process p = new ProcessBuilder(whichCmd, "ffmpeg").redirectErrorStream(true).start();
            String line = new BufferedReader(new InputStreamReader(p.getInputStream()))
                .lines().findFirst().orElse("").trim();
            p.waitFor(5, TimeUnit.SECONDS);
            if (!line.isEmpty() && new File(line).exists()) return line;
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Ensure ffmpeg is available — find it or download it.
     * @param status callback for progress messages (may be null)
     * @return absolute path to ffmpeg binary
     * @throws IOException if download fails
     */
    public static String ensureFfmpeg(Consumer<String> status) throws IOException {
        String found = findFfmpeg();
        if (found != null) return found;

        if (status != null) status.accept("Downloading ffmpeg...");

        Files.createDirectories(BIN_DIR);

        if (IS_MAC) {
            downloadFfmpegMac(status);
        } else if (IS_LINUX) {
            downloadFfmpegLinux(status);
        } else {
            throw new IOException("Unsupported OS for auto-download: " + System.getProperty("os.name"));
        }

        Path target = BIN_DIR.resolve("ffmpeg");
        String path = target.toString();
        if (probeExe(new String[]{path}, "-version") == null) {
            throw new IOException("Downloaded ffmpeg binary is not functional");
        }
        if (status != null) status.accept("ffmpeg ready");
        return path;
    }

    // ─── macOS ffmpeg download ───────────────────────────────────────────────

    private static void downloadFfmpegMac(Consumer<String> status) throws IOException {
        Path zipFile = BIN_DIR.resolve("ffmpeg.zip");
        try {
            downloadFile("https://evermeet.cx/ffmpeg/get/zip", zipFile, status);

            if (status != null) status.accept("Extracting ffmpeg...");
            // Extract the zip — contains a single "ffmpeg" binary
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = new File(entry.getName()).getName(); // strip any directory prefix
                    if (name.equals("ffmpeg")) {
                        Path out = BIN_DIR.resolve("ffmpeg");
                        Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                        makeExecutable(out);
                        break;
                    }
                }
            }
        } finally {
            Files.deleteIfExists(zipFile);
        }
    }

    // ─── Linux ffmpeg download ───────────────────────────────────────────────

    private static void downloadFfmpegLinux(Consumer<String> status) throws IOException {
        Path tarFile = BIN_DIR.resolve("ffmpeg.tar.xz");
        try {
            downloadFile("https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz", tarFile, status);

            if (status != null) status.accept("Extracting ffmpeg...");
            // Use tar to extract — find the ffmpeg binary inside the archive
            Process p = new ProcessBuilder("tar", "-xJf", tarFile.toString(), "-C", BIN_DIR.toString(),
                    "--strip-components=1", "--wildcards", "*/ffmpeg")
                .redirectErrorStream(true).start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            try {
                p.waitFor(120, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("ffmpeg extraction interrupted");
            }
            if (p.exitValue() != 0) {
                throw new IOException("Failed to extract ffmpeg from tar archive");
            }
            makeExecutable(BIN_DIR.resolve("ffmpeg"));
        } finally {
            Files.deleteIfExists(tarFile);
        }
    }

    // ─── Utilities ───────────────────────────────────────────────────────────

    /** Probe an executable by running it with the given flag. Returns the first working path. */
    private static String probeExe(String[] candidates, String versionFlag) {
        for (String c : candidates) {
            try {
                Process p = new ProcessBuilder(c, versionFlag).redirectErrorStream(true).start();
                p.getInputStream().transferTo(OutputStream.nullOutputStream());
                if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) return c;
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Download a file from a URL with redirect following. */
    private static void downloadFile(String urlStr, Path target, Consumer<String> status) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "FluxDM/1.0");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);

            // Manual redirect following (some servers redirect across protocols)
            int hops = 0;
            while (hops++ < 10) {
                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String loc = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (loc == null) throw new IOException("Redirect without Location header");
                    url = new URL(loc);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "FluxDM/1.0");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(60000);
                    conn.setInstanceFollowRedirects(true);
                } else {
                    break;
                }
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code + " downloading " + urlStr);
            }

            long total = conn.getContentLengthLong();
            try (InputStream in = new BufferedInputStream(conn.getInputStream(), 131072);
                 OutputStream out = new FileOutputStream(target.toFile())) {
                byte[] buf = new byte[131072];
                long bytes = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    bytes += n;
                    if (status != null && total > 0) {
                        int pct = (int) (bytes * 100 / total);
                        status.accept("Downloading... " + pct + "%");
                    }
                }
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Make a file executable and remove macOS quarantine attribute. */
    private static void makeExecutable(Path file) throws IOException {
        file.toFile().setExecutable(true, false);
        if (IS_MAC) {
            try {
                new ProcessBuilder("xattr", "-d", "com.apple.quarantine", file.toString())
                    .redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
    }
}
