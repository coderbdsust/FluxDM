package com.fluxdm;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.regex.*;

public class DownloadTask {

    public enum Status   { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED }
    public enum FileType { VIDEO, AUDIO, ARCHIVE, IMAGE, DOCUMENT, EXECUTABLE, OTHER, YOUTUBE }

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(1);
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(8,
            r -> { Thread t = new Thread(r, "DL-Worker"); t.setDaemon(true); return t; });

    private final int      id;
    private final String   url;
    private final String   savePath;
    private final String   quality;
    private final FileType fileType;
    private final String   addedTime;

    private volatile String  fileName;
    private volatile String  savedFilePath = null;
    private volatile long    totalSize  = 0;
    private volatile long    downloaded = 0;
    private volatile double  progress   = 0;
    private volatile double  speed      = 0;
    private volatile Status  status     = Status.QUEUED;
    private volatile boolean paused     = false;
    private volatile boolean cancelled  = false;
    private volatile Process ytdlpProcess = null;

    private volatile long startTimeMillis  = 0;
    private volatile long endTimeMillis    = 0;
    private volatile long pausedAccumulated = 0;
    private volatile long pauseStartTime   = 0;

    private Consumer<DownloadTask> onUpdate;

    public DownloadTask(String url, String savePath, String quality) {
        this.id       = ID_COUNTER.getAndIncrement();
        this.url      = url;
        this.savePath = savePath;
        this.quality  = quality == null ? "" : quality;
        this.fileType = detectFileType(url);
        this.addedTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        this.fileName  = extractFileName(url);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String extractFileName(String url) {
        try {
            String path = new URL(url).getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            name = URLDecoder.decode(name.isEmpty() ? "download_" + id : name, "UTF-8");
            if (name.contains("?")) name = name.substring(0, name.indexOf('?'));
            if (!name.contains(".")) name += isYouTube(url) ? ".mp4" : ".bin";
            return name;
        } catch (Exception e) { return "download_" + id + ".bin"; }
    }

    private FileType detectFileType(String url) {
        if (isYouTube(url)) return FileType.YOUTUBE;
        String u = url.toLowerCase();
        if (u.matches(".*\\.(mp4|mkv|avi|mov|webm|flv|wmv).*")) return FileType.VIDEO;
        if (u.matches(".*\\.(mp3|wav|flac|aac|ogg|m4a).*"))     return FileType.AUDIO;
        if (u.matches(".*\\.(zip|rar|7z|tar|gz|bz2|iso).*"))    return FileType.ARCHIVE;
        if (u.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp).*"))     return FileType.IMAGE;
        if (u.matches(".*\\.(pdf|doc|docx|xls|txt).*"))          return FileType.DOCUMENT;
        if (u.matches(".*\\.(exe|msi|dmg|deb|apk).*"))           return FileType.EXECUTABLE;
        return FileType.OTHER;
    }

    public boolean isYouTube(String u) {
        return u != null && (u.contains("youtube.com/watch") || u.contains("youtu.be/")
                || u.contains("youtube.com/shorts/") || u.contains("youtube.com/live/"));
    }

    public void setOnUpdate(Consumer<DownloadTask> cb) { this.onUpdate = cb; }
    private void notifyUpdate() {
        if (onUpdate != null) SwingUtilities.invokeLater(() -> onUpdate.accept(this));
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    public void start() {
        startTimeMillis = System.currentTimeMillis();
        status = Status.DOWNLOADING; notifyUpdate();
        EXECUTOR.submit(() -> { if (isYouTube(url)) downloadYouTube(); else downloadHttp(); });
    }
    public void pause()  {
        paused = true; speed = 0;
        pauseStartTime = System.currentTimeMillis();
        status = Status.PAUSED; notifyUpdate();
    }
    public void resume() {
        if (status != Status.PAUSED) return;
        pausedAccumulated += System.currentTimeMillis() - pauseStartTime;
        paused = false; status = Status.DOWNLOADING; notifyUpdate();
        EXECUTOR.submit(() -> { if (isYouTube(url)) downloadYouTube(); else downloadHttp(); });
    }
    public void cancel() {
        cancelled = true; paused = false; speed = 0;
        endTimeMillis = System.currentTimeMillis();
        if (ytdlpProcess != null) ytdlpProcess.destroyForcibly();
        status = Status.CANCELLED; notifyUpdate();
    }

    // ─── HTTP Download ────────────────────────────────────────────────────────

    private void downloadHttp() {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = openConn(u);
            int code = conn.getResponseCode();
            int hops = 0;
            while ((code==301||code==302||code==303||code==307||code==308) && hops++<10) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null) break;
                u = new URL(loc); conn = openConn(u); code = conn.getResponseCode();
            }
            if (code != 200 && code != 206) { setFailed("HTTP " + code); return; }
            totalSize = conn.getContentLengthLong(); notifyUpdate();
            String cd = conn.getHeaderField("Content-Disposition");
            if (cd != null && cd.toLowerCase().contains("filename")) {
                String n = cd.replaceAll("(?i).*filename\\*?=(?:UTF-8'')?[\"']?([^\"';\r\n]+)[\"']?.*","$1").trim();
                if (!n.isBlank() && !n.equals(cd)) fileName = n;
            }
            File dir = new File(savePath); dir.mkdirs();
            File out = uniqueFile(dir, fileName);
            fileName = out.getName(); savedFilePath = out.getAbsolutePath();
            streamToFile(conn.getInputStream(), out, totalSize);
        } catch (Exception e) { if (!cancelled) setFailed(e.getMessage()); }
        finally { if (conn != null) conn.disconnect(); }
    }

    private HttpURLConnection openConn(URL u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120 Safari/537.36");
        c.setRequestProperty("Accept","*/*");
        c.setConnectTimeout(15000); c.setReadTimeout(60000); c.setInstanceFollowRedirects(true);
        return c;
    }

    private void streamToFile(InputStream in, File out, long total) {
        try (BufferedInputStream bis = new BufferedInputStream(in, 131072);
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[131072]; long bytes=0, lastBytes=0; long lastTime=System.nanoTime(); int n;
            while ((n=bis.read(buf))!=-1) {
                while (paused&&!cancelled) Thread.sleep(200);
                if (cancelled) { fos.close(); out.delete(); return; }
                fos.write(buf,0,n); bytes+=n; downloaded=bytes;
                long now=System.nanoTime(); double elapsed=(now-lastTime)/1e9;
                if (elapsed>=0.3) { speed=(bytes-lastBytes)/elapsed; progress=total>0?(double)bytes/total:0; lastTime=now; lastBytes=bytes; notifyUpdate(); }
            }
            fos.flush();
            if (!cancelled) { totalSize=bytes; finishDownload(bytes); }
        } catch (Exception e) { if (!cancelled) setFailed(e.getMessage()); }
    }

    // ─── YouTube via yt-dlp ───────────────────────────────────────────────────

    private void downloadYouTube() {
        String ytdlp = findBin("yt-dlp", "/usr/local/bin/yt-dlp", "/opt/homebrew/bin/yt-dlp",
                               System.getProperty("user.home")+"/.local/bin/yt-dlp", "/usr/bin/yt-dlp");
        if (ytdlp == null) { setFailed("yt-dlp not found. Install: brew install yt-dlp"); return; }

        String ffmpeg = findFfmpeg();
        boolean hasFfmpeg = ffmpeg != null;
        System.out.println("FluxDM: ffmpeg=" + ffmpeg + " hasFfmpeg=" + hasFfmpeg);

        File dir = new File(savePath); dir.mkdirs();
        // %(title)s.%(ext)s — yt-dlp will set the real extension
        String outTpl = new File(dir, "%(title)s.%(ext)s").getAbsolutePath();

        boolean isAudioOnly = quality.contains("Audio");

        try {
            // ── Step 1: get predicted filename quickly ─────────────────────
            Process nameProc = new ProcessBuilder(ytdlp,
                    "--get-filename", "-o", outTpl, "--no-playlist", url)
                .redirectErrorStream(true).start();
            String predictedPath = new BufferedReader(new InputStreamReader(nameProc.getInputStream()))
                .lines().map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("[") && !l.startsWith("WARNING"))
                .findFirst().orElse("").trim();
            nameProc.waitFor(20, TimeUnit.SECONDS);
            if (!predictedPath.isEmpty()) {
                // After remux/merge the ext will be .mp4, or .mp3 for audio
                String ext = isAudioOnly && hasFfmpeg ? ".mp3" : isAudioOnly ? ".m4a" : ".mp4";
                String displayPath = predictedPath.replaceAll("\\.[^.]+$", ext);
                savedFilePath = displayPath;
                fileName = new File(displayPath).getName();
                notifyUpdate();
            }

            // ── Step 2: build download command ─────────────────────────────
            //
            // THE KEY FIX:
            // YouTube DASH streams are always either video-only OR audio-only.
            // A pre-muxed stream with both video+audio tops out at 720p (format 22).
            //
            // WITH ffmpeg:    download best video + best audio separately, then merge to mp4.
            //                 This is the only way to get 1080p/4K with audio.
            //
            // WITHOUT ffmpeg: use format 22 (720p H.264+AAC, pre-muxed, always playable)
            //                 with 18 (360p) as fallback. These are the ONLY formats on
            //                 YouTube that have both video AND audio in one stream.
            //                 Do NOT use bestvideo or bestaudio — those are DASH (video-only
            //                 or audio-only).

            List<String> cmd = new ArrayList<>();
            cmd.add(ytdlp);
            cmd.add("-o"); cmd.add(outTpl);
            cmd.add("--newline");
            cmd.add("--no-playlist");
            cmd.add("--no-mtime");
            cmd.add("--no-part");

            if (hasFfmpeg && isAudioOnly) {
                // Audio Only (MP3) with ffmpeg: extract audio and convert to MP3
                cmd.add("-f"); cmd.add("bestaudio[acodec^=mp4a]/bestaudio");
                cmd.add("--extract-audio");
                cmd.add("--audio-format"); cmd.add("mp3");
                cmd.add("--audio-quality"); cmd.add("0");
                String ffmpegDir = new File(ffmpeg).getParent();
                if (ffmpegDir != null && !ffmpegDir.isEmpty()) {
                    cmd.add("--ffmpeg-location"); cmd.add(ffmpegDir);
                }
            } else if (hasFfmpeg) {
                // Download best video + best audio, merge into mp4
                cmd.add("-f");
                cmd.add(buildFmtFfmpeg(quality));
                cmd.add("--merge-output-format"); cmd.add("mp4");
                String ffmpegDir = new File(ffmpeg).getParent();
                if (ffmpegDir != null && !ffmpegDir.isEmpty()) {
                    cmd.add("--ffmpeg-location"); cmd.add(ffmpegDir);
                }
            } else {
                // NO ffmpeg path — MUST use pre-muxed formats only
                // Format 22 = 720p h264+aac mp4 (pre-muxed, always has audio+video)
                // Format 18 = 360p h264+aac mp4 (pre-muxed, universal fallback)
                // "Audio Only" user picks audio — use 140 (m4a, audio only)
                String audioOnly = quality.contains("Audio") ? "140/bestaudio[ext=m4a]/bestaudio" : null;
                cmd.add("-f");
                cmd.add(audioOnly != null ? audioOnly : "22/18");
            }

            cmd.add(url);

            // ── Step 3: run and parse output ───────────────────────────────
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.directory(dir);
            ytdlpProcess = pb.start();

            String trackedDest = null;
            StringBuilder allOut = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(ytdlpProcess.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (cancelled) break;
                    allOut.append(line).append("\n");
                    // "[download] Destination: /path/file.mp4"
                    if (line.contains("[download] Destination:")) {
                        String dest = line.substring(line.indexOf("Destination:") + 12).trim();
                        if (!dest.isEmpty()) { trackedDest = dest; savedFilePath = dest; fileName = new File(dest).getName(); notifyUpdate(); }
                    }
                    // "[ExtractAudio] Destination: /path/file.mp3"
                    if (line.contains("[ExtractAudio] Destination:")) {
                        String dest = line.substring(line.indexOf("Destination:") + 12).trim();
                        if (!dest.isEmpty()) { trackedDest = dest; savedFilePath = dest; fileName = new File(dest).getName(); notifyUpdate(); }
                    }
                    // "[Merger] Merging formats into \"file.mp4\""
                    if (line.contains("Merging formats into") || line.contains("Merger")) {
                        Matcher mm = Pattern.compile("\"([^\"]+\\.mp4)\"").matcher(line);
                        if (mm.find()) { trackedDest = mm.group(1); savedFilePath = trackedDest; fileName = new File(trackedDest).getName(); notifyUpdate(); }
                    }
                    parseYtDlp(line);
                }
            }

            // ── Step 4: finish ─────────────────────────────────────────────
            int exit = ytdlpProcess.waitFor();
            if (cancelled) return;

            if (exit == 0) {
                // yt-dlp sometimes leaves separate streams (.f137.mp4 + .f140.m4a)
                // even with --merge-output-format if ffmpeg wasn't invoked properly.
                // Detect this and merge manually. Skip for audio-only (already converted).
                if (hasFfmpeg && !isAudioOnly) {
                    mergeIfNeeded(dir, ffmpeg);
                }

                File actual = trackedDest != null ? new File(trackedDest) : null;
                if (actual == null || !actual.exists()) actual = findNewestVideo(dir);
                if (actual == null || !actual.exists()) actual = findNewest(dir);
                if (actual != null && actual.exists()) {
                    savedFilePath = actual.getAbsolutePath();
                    fileName      = actual.getName();
                    totalSize     = actual.length();
                    downloaded    = totalSize;
                }
                finishDownload(totalSize);
            } else {
                String err = allOut.toString().lines()
                    .filter(l -> l.contains("ERROR:")).reduce((a,b)->b)
                    .orElse("yt-dlp exited with code " + exit);
                setFailed(err.replaceAll(".*ERROR:\\s*(\\[.*?\\]\\s*)?","").trim());
            }
        } catch (Exception e) { if (!cancelled) setFailed(e.getMessage()); }
    }

    /**
     * Format selector when ffmpeg is available.
     * Requests separate best video + best audio streams — ffmpeg merges them.
     * Prefers H.264 video + AAC audio for maximum device compatibility.
     */
    private String buildFmtFfmpeg(String q) {
        String base = switch (q) {
            case "4K (2160p)"       -> "bestvideo[height<=2160][vcodec^=avc1]+bestaudio[acodec^=mp4a]";
            case "1080p"            -> "bestvideo[height<=1080][vcodec^=avc1]+bestaudio[acodec^=mp4a]";
            case "720p"             -> "bestvideo[height<=720][vcodec^=avc1]+bestaudio[acodec^=mp4a]";
            case "480p"             -> "bestvideo[height<=480][vcodec^=avc1]+bestaudio[acodec^=mp4a]";
            case "360p"             -> "bestvideo[height<=360][vcodec^=avc1]+bestaudio[acodec^=mp4a]";
            default                 -> "bestvideo[vcodec^=avc1]+bestaudio[acodec^=mp4a]";
        };
        // Fallback chain: preferred codec → any codec → single best stream
        String hCap = q.contains("2160") ? "2160" : q.contains("1080") ? "1080"
                    : q.contains("720")  ? "720"  : q.contains("480")  ? "480"
                    : q.contains("360")  ? "360"  : null;
        String fallback = hCap != null
            ? "bestvideo[height<=" + hCap + "]+bestaudio/best[height<=" + hCap + "]"
            : "bestvideo+bestaudio/best";
        return base + "/" + fallback;
    }

    // ─── yt-dlp progress parsing ──────────────────────────────────────────────

    // [download]  45.3% of   145.32MiB at    3.21MiB/s ETA 00:39
    private void parseYtDlp(String line) {
        if (!line.contains("[download]")) return;
        try {
            Matcher m = Pattern.compile("(\\d+\\.?\\d*)%").matcher(line);
            if (m.find()) progress = Double.parseDouble(m.group(1)) / 100.0;

            m = Pattern.compile("of\\s+~?([\\d.]+)(KiB|MiB|GiB|B)").matcher(line);
            if (m.find()) {
                double v = Double.parseDouble(m.group(1));
                totalSize = switch (m.group(2)) {
                    case "KiB" -> (long)(v*1024); case "MiB" -> (long)(v*1024*1024);
                    case "GiB" -> (long)(v*1024*1024*1024); default -> (long)v;
                };
                downloaded = (long)(totalSize * progress);
            }
            m = Pattern.compile("at\\s+([\\d.]+)(KiB|MiB|GiB|B)/s").matcher(line);
            if (m.find()) {
                double v = Double.parseDouble(m.group(1));
                speed = switch (m.group(2)) {
                    case "KiB" -> v*1024; case "MiB" -> v*1024*1024;
                    case "GiB" -> v*1024*1024*1024; default -> v;
                };
            }
        } catch (Exception ignored) {}
        notifyUpdate();
    }

    // ─── Binary finders ───────────────────────────────────────────────────────

    private String findBin(String... candidates) {
        for (String c : candidates) {
            try {
                // ffmpeg uses -version; yt-dlp uses --version
                String flag = c.contains("ffmpeg") ? "-version" : "--version";
                Process p = new ProcessBuilder(c, flag).redirectErrorStream(true).start();
                if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) return c;
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Find ffmpeg — checks Homebrew paths, PATH, and common macOS locations */
    private String findFfmpeg() {
        // Ask yt-dlp where it found ffmpeg — most reliable
        String[] ytdlpCandidates = {"yt-dlp","/usr/local/bin/yt-dlp","/opt/homebrew/bin/yt-dlp",
                System.getProperty("user.home")+"/.local/bin/yt-dlp"};
        // Direct ffmpeg paths
        String home = System.getProperty("user.home");
        String[] ffmpegPaths = {
            "/opt/homebrew/bin/ffmpeg",      // Homebrew Apple Silicon
            "/usr/local/bin/ffmpeg",          // Homebrew Intel
            "/opt/local/bin/ffmpeg",          // MacPorts
            home + "/bin/ffmpeg",
            home + "/.local/bin/ffmpeg",
            "/usr/bin/ffmpeg",
            "ffmpeg",                          // on PATH
        };
        for (String c : ffmpegPaths) {
            try {
                Process p = new ProcessBuilder(c, "-version").redirectErrorStream(true).start();
                if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) return c;
            } catch (Exception ignored) {}
        }
        // Last resort: ask 'which ffmpeg' / 'where ffmpeg'
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String whichCmd = os.contains("win") ? "where" : "which";
            Process p = new ProcessBuilder(whichCmd, "ffmpeg").redirectErrorStream(true).start();
            String result = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))
                .lines().findFirst().orElse("").trim();
            p.waitFor(5, TimeUnit.SECONDS);
            if (!result.isEmpty() && new File(result).exists()) return result;
        } catch (Exception ignored) {}
        return null;
    }

    // ─── File utilities ───────────────────────────────────────────────────────


    /**
     * If yt-dlp left separate video (.fXXX.mp4) and audio (.fXXX.m4a) streams,
     * merge them with ffmpeg into a single playable mp4 and delete the originals.
     *
     * Pattern: two files sharing the same base title but with .fNNN. in their names.
     */
    private void mergeIfNeeded(File dir, String ffmpegBin) {
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().matches(".*\\.f\\d+\\.(mp4|m4a|webm)$"));
        if (files == null || files.length < 2) return;

        // Group by base title (everything before .fNNN.)
        Map<String, List<File>> groups = new LinkedHashMap<>();
        for (File f : files) {
            String base = f.getName().replaceAll("\\.f\\d+\\.(mp4|m4a|webm)$", "");
            groups.computeIfAbsent(base, k -> new ArrayList<>()).add(f);
        }

        for (Map.Entry<String, List<File>> entry : groups.entrySet()) {
            List<File> parts = entry.getValue();
            if (parts.size() < 2) continue;

            // Find video and audio files
            File videoFile = null, audioFile = null;
            for (File f : parts) {
                String n = f.getName().toLowerCase();
                if (n.endsWith(".mp4") || n.endsWith(".webm")) videoFile = f;
                else if (n.endsWith(".m4a"))                   audioFile = f;
            }
            if (videoFile == null || audioFile == null) continue;

            File merged = new File(dir, entry.getKey() + ".mp4");
            // Avoid clobbering an existing merged file
            if (!merged.exists()) {
                System.out.println("FluxDM: merging " + videoFile.getName() + " + " + audioFile.getName());
                try {
                    // ffmpeg -i video -i audio -c copy -movflags +faststart output.mp4
                    Process p = new ProcessBuilder(
                        ffmpegBin,
                        "-y",
                        "-i", videoFile.getAbsolutePath(),
                        "-i", audioFile.getAbsolutePath(),
                        "-c", "copy",
                        "-movflags", "+faststart",
                        merged.getAbsolutePath()
                    ).redirectErrorStream(true).start();
                    // Drain output
                    p.getInputStream().transferTo(OutputStream.nullOutputStream());
                    int rc = p.waitFor();
                    if (rc == 0 && merged.exists() && merged.length() > 0) {
                        // Clean up the separate streams
                        videoFile.delete();
                        audioFile.delete();
                        // Update tracked path
                        savedFilePath = merged.getAbsolutePath();
                        fileName = merged.getName();
                        notifyUpdate();
                        System.out.println("FluxDM: merge OK -> " + merged.getName());
                    } else {
                        System.err.println("FluxDM: merge failed rc=" + rc);
                        if (merged.exists()) merged.delete();
                    }
                } catch (Exception e) {
                    System.err.println("FluxDM: merge exception: " + e.getMessage());
                }
            }
        }
    }

    private File findNewestVideo(File dir) {
        File[] files = dir.listFiles(f -> {
            if (!f.isFile()) return false;
            String n = f.getName().toLowerCase();
            return !n.endsWith(".part") && !n.endsWith(".ytdl") && !n.endsWith(".tmp") && !n.startsWith(".")
                && (n.endsWith(".mp4")||n.endsWith(".mkv")||n.endsWith(".webm")||n.endsWith(".m4a")||n.endsWith(".mp3")||n.endsWith(".mov"));
        });
        if (files == null || files.length == 0) return null;
        File best = files[0];
        for (File f : files) if (f.lastModified() > best.lastModified()) best = f;
        return best;
    }

    private File findNewest(File dir) {
        File[] files = dir.listFiles(f -> f.isFile() && !f.getName().endsWith(".part")
                && !f.getName().endsWith(".ytdl") && !f.getName().startsWith("."));
        if (files == null || files.length == 0) return null;
        File best = files[0];
        for (File f : files) if (f.lastModified() > best.lastModified()) best = f;
        return best;
    }

    private File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot>=0?name.substring(0,dot):name, ext=dot>=0?name.substring(dot):"";
        int i = 1;
        do { f = new File(dir, base+" ("+i+")"+ext); i++; } while (f.exists() && i<1000);
        return f;
    }

    // ─── Download finish / fail ───────────────────────────────────────────────

    private void finishDownload(long bytes) {
        downloaded = bytes; if (totalSize<=0) totalSize=bytes;
        endTimeMillis = System.currentTimeMillis();
        progress=1.0; speed=0; status=Status.COMPLETED; notifyUpdate();
    }
    private void setFailed(String reason) {
        endTimeMillis = System.currentTimeMillis();
        speed=0; status=Status.FAILED;
        System.err.println("FluxDM FAILED: " + reason);
        notifyUpdate();
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public int getId()               { return id; }
    public String getUrl()           { return url; }
    public String getFileName()      { return fileName; }
    public String getSavePath()      { return savePath; }
    public String getSavedFilePath() { return savedFilePath; }
    public String getQuality()       { return quality; }
    public FileType getFileType()    { return fileType; }
    public Status getStatus()        { return status; }
    public long getTotalSize()       { return totalSize; }
    public long getDownloaded()      { return downloaded; }
    public double getProgress()      { return progress; }
    public double getSpeed()         { return speed; }
    public String getAddedTime()     { return addedTime; }
    public boolean isYouTube()       { return isYouTube(url); }

    /** Returns elapsed active download time in milliseconds (excludes paused time). */
    public long getElapsedMillis() {
        if (startTimeMillis == 0) return 0;
        return switch (status) {
            case DOWNLOADING -> System.currentTimeMillis() - startTimeMillis - pausedAccumulated;
            case PAUSED      -> pauseStartTime - startTimeMillis - pausedAccumulated;
            case COMPLETED, FAILED, CANCELLED -> endTimeMillis - startTimeMillis - pausedAccumulated;
            default -> 0;
        };
    }
}
