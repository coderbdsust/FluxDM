package com.fluxdm;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Manages yt-dlp and ffmpeg binary dependencies.
 * Checks system paths first, auto-downloads to ~/.fluxdm/bin/ if missing.
 */
public class DependencyManager {

    private static final String BIN_DIR = System.getProperty("user.home") + File.separator + ".fluxdm" + File.separator + "bin";
    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_MAC   = OS.contains("mac");
    private static final boolean IS_WIN   = OS.contains("win");

    // Cached paths: null = not checked, "" = checked but not found
    private static volatile String cachedYtDlp  = null;
    private static volatile String cachedFfmpeg = null;

    private static final Object INSTALL_LOCK = new Object();

    // ─── Find (check only, no install) ──────────────────────────────────────

    public static String findYtDlp() {
        if (cachedYtDlp != null) return cachedYtDlp.isEmpty() ? null : cachedYtDlp;
        String found = scanYtDlp();
        cachedYtDlp = found != null ? found : "";
        return found;
    }

    public static String findFfmpeg() {
        if (cachedFfmpeg != null) return cachedFfmpeg.isEmpty() ? null : cachedFfmpeg;
        String found = scanFfmpeg();
        cachedFfmpeg = found != null ? found : "";
        return found;
    }

    // ─── Ensure (find or auto-install) ──────────────────────────────────────

    /** Find yt-dlp, or auto-download if missing. Thread-safe. */
    public static String ensureYtDlp(Consumer<String> status) {
        String path = findYtDlp();
        if (path != null) return path;
        synchronized (INSTALL_LOCK) {
            // Double-check after acquiring lock
            cachedYtDlp = null;
            path = findYtDlp();
            if (path != null) return path;
            path = installYtDlp(status);
            if (path != null) cachedYtDlp = path;
            return path;
        }
    }

    /** Find ffmpeg, or auto-download if missing. Thread-safe. */
    public static String ensureFfmpeg(Consumer<String> status) {
        String path = findFfmpeg();
        if (path != null) return path;
        synchronized (INSTALL_LOCK) {
            cachedFfmpeg = null;
            path = findFfmpeg();
            if (path != null) return path;
            path = installFfmpeg(status);
            if (path != null) cachedFfmpeg = path;
            return path;
        }
    }

    /** Clear cached paths so next lookup re-scans the filesystem. */
    public static void clearCache() {
        cachedYtDlp = null;
        cachedFfmpeg = null;
    }

    // ─── Scan system paths ──────────────────────────────────────────────────

    private static String scanYtDlp() {
        String home = System.getProperty("user.home");
        String localBin = BIN_DIR + File.separator + (IS_WIN ? "yt-dlp.exe" : "yt-dlp");
        String[] candidates = {
            localBin,
            "/opt/homebrew/bin/yt-dlp",
            "/usr/local/bin/yt-dlp",
            home + "/.local/bin/yt-dlp",
            "/usr/bin/yt-dlp",
            "yt-dlp"
        };
        for (String c : candidates) {
            if (testBin(c, "--version")) return c;
        }
        return null;
    }

    private static String scanFfmpeg() {
        String home = System.getProperty("user.home");
        String localBin = BIN_DIR + File.separator + (IS_WIN ? "ffmpeg.exe" : "ffmpeg");
        String[] candidates = {
            localBin,
            "/opt/homebrew/bin/ffmpeg",
            "/usr/local/bin/ffmpeg",
            "/opt/local/bin/ffmpeg",
            home + "/bin/ffmpeg",
            home + "/.local/bin/ffmpeg",
            "/usr/bin/ffmpeg",
            "ffmpeg"
        };
        for (String c : candidates) {
            if (testBin(c, "-version")) return c;
        }
        // Last resort: which / where
        try {
            String whichCmd = IS_WIN ? "where" : "which";
            Process p = new ProcessBuilder(whichCmd, "ffmpeg").redirectErrorStream(true).start();
            String result = new BufferedReader(new InputStreamReader(p.getInputStream()))
                .lines().findFirst().orElse("").trim();
            p.waitFor(5, TimeUnit.SECONDS);
            if (!result.isEmpty() && new File(result).exists()) return result;
        } catch (Exception ignored) {}
        return null;
    }

    // ─── Install methods ────────────────────────────────────────────────────

    private static String installYtDlp(Consumer<String> status) {
        String url;
        String fileName;
        if (IS_MAC) {
            url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos";
            fileName = "yt-dlp";
        } else if (IS_WIN) {
            url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe";
            fileName = "yt-dlp.exe";
        } else {
            url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux";
            fileName = "yt-dlp";
        }

        File binDir = new File(BIN_DIR);
        binDir.mkdirs();
        File target = new File(binDir, fileName);

        try {
            status.accept("Downloading yt-dlp...");
            downloadFile(url, target, status);
            if (!IS_WIN) makeExecutable(target);

            if (testBin(target.getAbsolutePath(), "--version")) {
                status.accept("yt-dlp installed successfully");
                System.out.println("FluxDM: yt-dlp auto-installed to " + target.getAbsolutePath());
                return target.getAbsolutePath();
            } else {
                status.accept("yt-dlp verification failed");
                target.delete();
                return null;
            }
        } catch (Exception e) {
            status.accept("Failed to download yt-dlp: " + e.getMessage());
            System.err.println("FluxDM: yt-dlp install failed: " + e.getMessage());
            if (target.exists()) target.delete();
            return null;
        }
    }

    private static String installFfmpeg(Consumer<String> status) {
        File binDir = new File(BIN_DIR);
        binDir.mkdirs();
        String fileName = IS_WIN ? "ffmpeg.exe" : "ffmpeg";
        File target = new File(binDir, fileName);

        try {
            if (IS_MAC) {
                // evermeet.cx provides macOS static ffmpeg builds as zip
                String url = "https://evermeet.cx/ffmpeg/getrelease/zip";
                File zipFile = new File(binDir, "ffmpeg.zip");
                status.accept("Downloading ffmpeg for macOS...");
                downloadFile(url, zipFile, status);

                status.accept("Extracting ffmpeg...");
                Process p = new ProcessBuilder("unzip", "-o", "-j", zipFile.getAbsolutePath(), "-d", binDir.getAbsolutePath())
                    .redirectErrorStream(true).start();
                p.getInputStream().transferTo(OutputStream.nullOutputStream());
                p.waitFor(60, TimeUnit.SECONDS);
                zipFile.delete();
            } else if (IS_WIN) {
                String url = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip";
                File zipFile = new File(binDir, "ffmpeg-win.zip");
                status.accept("Downloading ffmpeg for Windows...");
                downloadFile(url, zipFile, status);

                // Extract just ffmpeg.exe using PowerShell
                status.accept("Extracting ffmpeg...");
                Process p = new ProcessBuilder("powershell", "-Command",
                    "Expand-Archive -Path '" + zipFile.getAbsolutePath() + "' -DestinationPath '" + binDir.getAbsolutePath() + "' -Force")
                    .redirectErrorStream(true).start();
                p.getInputStream().transferTo(OutputStream.nullOutputStream());
                p.waitFor(120, TimeUnit.SECONDS);
                zipFile.delete();

                // Move ffmpeg.exe from nested dir to bin dir
                File[] dirs = binDir.listFiles(f -> f.isDirectory() && f.getName().startsWith("ffmpeg-"));
                if (dirs != null) {
                    for (File d : dirs) {
                        File exe = new File(d, "bin" + File.separator + "ffmpeg.exe");
                        if (exe.exists()) {
                            exe.renameTo(target);
                            deleteRecursive(d);
                            break;
                        }
                    }
                }
            } else {
                // Linux: BtbN static builds
                String url = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz";
                File tarFile = new File(binDir, "ffmpeg-linux.tar.xz");
                status.accept("Downloading ffmpeg for Linux...");
                downloadFile(url, tarFile, status);

                status.accept("Extracting ffmpeg...");
                // Extract just the ffmpeg binary
                Process p = new ProcessBuilder("tar", "xf", tarFile.getAbsolutePath(),
                    "-C", binDir.getAbsolutePath(), "--strip-components=2", "--wildcards", "*/bin/ffmpeg")
                    .redirectErrorStream(true).start();
                p.getInputStream().transferTo(OutputStream.nullOutputStream());
                p.waitFor(120, TimeUnit.SECONDS);
                tarFile.delete();
            }

            if (!IS_WIN && target.exists()) makeExecutable(target);

            if (target.exists() && testBin(target.getAbsolutePath(), "-version")) {
                status.accept("ffmpeg installed successfully");
                System.out.println("FluxDM: ffmpeg auto-installed to " + target.getAbsolutePath());
                return target.getAbsolutePath();
            } else {
                status.accept("ffmpeg install failed — install manually: brew install ffmpeg");
                System.err.println("FluxDM: ffmpeg auto-install failed, target exists=" + target.exists());
                if (target.exists()) target.delete();
                return null;
            }
        } catch (Exception e) {
            status.accept("Failed to download ffmpeg: " + e.getMessage());
            System.err.println("FluxDM: ffmpeg install failed: " + e.getMessage());
            if (target.exists()) target.delete();
            return null;
        }
    }

    // ─── Utility ────────────────────────────────────────────────────────────

    private static boolean testBin(String path, String flag) {
        try {
            Process p = new ProcessBuilder(path, flag).redirectErrorStream(true).start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void downloadFile(String urlStr, File target, Consumer<String> status) throws IOException {
        HttpURLConnection conn = null;
        URL url = new URL(urlStr);
        int hops = 0;

        while (hops++ < 10) {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "FluxDM/2.0");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null) throw new IOException("Redirect with no Location header");
                url = new URL(loc);
                continue;
            }
            if (code != 200) throw new IOException("HTTP " + code + " from " + urlStr);
            break;
        }

        long total = conn.getContentLengthLong();
        try (InputStream in = new BufferedInputStream(conn.getInputStream(), 131072);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[131072];
            long downloaded = 0;
            int n;
            long lastStatusUpdate = 0;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                downloaded += n;
                long now = System.currentTimeMillis();
                if (total > 0 && now - lastStatusUpdate > 300) {
                    int pct = (int) (downloaded * 100 / total);
                    status.accept("Downloading... " + pct + "% (" + Formatter.bytes(downloaded) + " / " + Formatter.bytes(total) + ")");
                    lastStatusUpdate = now;
                }
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void makeExecutable(File f) {
        try {
            new ProcessBuilder("chmod", "+x", f.getAbsolutePath()).start().waitFor(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }
}