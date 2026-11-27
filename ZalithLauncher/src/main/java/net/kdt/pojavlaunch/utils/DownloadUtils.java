package net.kdt.pojavlaunch.utils;

import androidx.annotation.Nullable;

import com.movtery.zalithlauncher.feature.log.Logging;
import com.movtery.zalithlauncher.utils.path.PathManager;
import com.movtery.zalithlauncher.utils.ZHTools;
import com.movtery.zalithlauncher.utils.path.UrlManager;

import net.kdt.pojavlaunch.Tools;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

@SuppressWarnings("IOStreamConstructor")
public class DownloadUtils {
    public static void download(String url, OutputStream os) throws IOException {
        download(new URL(url), os);
    }

    public static void download(URL url, OutputStream os) throws IOException {
        InputStream is = null;
        try {
            HttpURLConnection conn = UrlManager.createHttpConnection(url);
            conn.setDoInput(true);
            conn.connect();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server returned HTTP " + conn.getResponseCode()
                        + ": " + conn.getResponseMessage());
            }
            is = conn.getInputStream();
            IOUtils.copy(is, os);
        } catch (SocketTimeoutException e) {
            throw new IOException("Download timed out: " + url, e);
        } catch (IOException e) {
            throw new IOException("Unable to download from " + url, e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    Logging.e("DownloadUtils", Tools.printToString(e));
                }
            }
        }
    }

    public static String downloadString(String url) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        download(url, bos);
        bos.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    public static void downloadFile(String url, File out) throws IOException {
        FileUtils.ensureParentDirectory(out);
        try (FileOutputStream fileOutputStream = new FileOutputStream(out)) {
            download(url, fileOutputStream);
        }
    }

    public static void downloadFileMonitored(String urlInput, File outputFile, @Nullable byte[] buffer,
                                             Tools.DownloaderFeedback monitor) throws IOException {
        FileUtils.ensureParentDirectory(outputFile);

        // Use the shared connection factory: it applies the connect AND read timeouts
        // (a raw openConnection() has no connect timeout, so a dead route could hang
        // a pool thread indefinitely) plus the launcher's User-Agent.
        HttpURLConnection conn = UrlManager.createHttpConnection(new URL(urlInput));
        conn.setDoInput(true);
        try {
            conn.connect();
            int responseCode = conn.getResponseCode();
            // A mirror/CDN error page (429/5xx) must fail the attempt instead of being
            // written to disk as if it were the file.
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server returned HTTP " + responseCode + ": "
                        + conn.getResponseMessage() + " (" + urlInput + ")");
            }
            try (InputStream readStr = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(outputFile)) {
                int current;
                int overall = 0;
                int length = conn.getContentLength();

                if (buffer == null) buffer = new byte[65535];

                while ((current = readStr.read(buffer)) != -1) {
                    overall += current;
                    fos.write(buffer, 0, current);
                    monitor.updateProgress(overall, length);
                }
            } catch (SocketTimeoutException e) {
                throw new IOException("Download timed out: " + urlInput, e);
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Pause briefly between download attempts so a transient reset is not retried
     * into another immediate reset. Interrupting the worker (pool shutdown) aborts
     * the retry loop instead of fighting the interrupt.
     */
    private static void sleepBeforeRetry(int attempt) throws IOException {
        try {
            Thread.sleep(200L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    public static <T> T downloadStringCached(String url, String cacheName, boolean force, ParseCallback<T> parseCallback) throws IOException, ParseException{
        File cacheDestination = new File(PathManager.DIR_CACHE_STRING, cacheName);
        if (force && cacheDestination.exists()) org.apache.commons.io.FileUtils.deleteQuietly(cacheDestination);
        if (cacheDestination.isFile() && cacheDestination.canRead() &&
                ZHTools.getCurrentTimeMillis() < (cacheDestination.lastModified() + 86400000)) {
            try {
                String cachedString = Tools.read(new FileInputStream(cacheDestination));
                return parseCallback.process(cachedString);
            }catch(IOException e) {
                Logging.i("DownloadUtils", "Failed to read the cached file", e);
            }catch (ParseException e) {
                Logging.i("DownloadUtils", "Failed to parse the cached file", e);
            }
        }
        String urlContent = DownloadUtils.downloadString(url);
        // if we download the file and fail parsing it, we will yeet outta there
        // and not cache the unparseable sting. We will return this after trying to save the downloaded
        // string into cache
        T parseResult = parseCallback.process(urlContent);

        boolean tryWriteCache;
        if(cacheDestination.exists()) {
            tryWriteCache = cacheDestination.canWrite();
        } else {
            tryWriteCache = FileUtils.ensureParentDirectorySilently(cacheDestination);
        }

        if(tryWriteCache) try {
            Tools.write(cacheDestination.getAbsolutePath(), urlContent);
        }catch(IOException e) {
            Logging.i("DownloadUtils", "Failed to cache the string", e);
        }
        return parseResult;
    }

    private static <T> T downloadFile(Callable<T> downloadFunction) throws IOException{
        try {
            return downloadFunction.call();
        } catch (IOException e){
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean verifyFile(File file, String sha1) {
        return file.exists() && Tools.compareSHA1(file, sha1);
    }

    public static <T> T ensureSha1(File outputFile, @Nullable String sha1, Callable<T> downloadFunction) throws IOException {
        // Skip if needed
        if(sha1 == null) {
            // If the file exists and we don't know it's SHA1, don't try to redownload it.
            if(outputFile.exists()) return null;
            // The no-hash path used to be a single attempt: one dropped connection
            // aborted the whole game download. Retry like the hashed path, and never
            // keep a partial file — without a hash it would be trusted as complete
            // on every following run.
            IOException lastFailure = null;
            for (int attempt = 1; attempt <= 5; attempt++) {
                try {
                    return downloadFile(downloadFunction);
                } catch (IOException e) {
                    lastFailure = e;
                    org.apache.commons.io.FileUtils.deleteQuietly(outputFile);
                    if (attempt < 5) sleepBeforeRetry(attempt);
                }
            }
            throw lastFailure;
        }

        int attempts = 0;
        boolean fileOkay = verifyFile(outputFile, sha1);
        T result = null;
        while (attempts < 5 && !fileOkay){
            attempts++;
            try {
                downloadFile(downloadFunction);
            } catch (IOException e) {
                // Transient network failures (resets, timeouts, 5xx) are retried
                // the same way hash mismatches are, instead of aborting the entire
                // download on the first error.
                if (attempts >= 5) throw e;
                sleepBeforeRetry(attempts);
                continue;
            }
            fileOkay = verifyFile(outputFile, sha1);
        }
        if(!fileOkay) throw new SHA1VerificationException("SHA1 verifcation failed after 5 download attempts");
        return result;
    }

    /**
     * Get the content length for a given URL.
     * @param url the URL to get the length for
     * @return the length in bytes or -1 if not available
     * @throws IOException if an I/O error occurs.
     */
    public static long getContentLength(String url) throws IOException {
        HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
        urlConnection.setRequestMethod("HEAD");
        urlConnection.setDoInput(false);
        urlConnection.setDoOutput(false);
        urlConnection.connect();
        int responseCode = urlConnection.getResponseCode();
        if(responseCode >= 200 && responseCode <= 299) return urlConnection.getContentLength();
        return -1;
    }

    public interface ParseCallback<T> {
        T process(String input) throws ParseException;
    }
    public static class ParseException extends Exception {
        public ParseException(Exception e) {
            super(e);
        }
    }

    public static class SHA1VerificationException extends IOException {
        public SHA1VerificationException(String message) {
            super(message);
        }
    }
}

