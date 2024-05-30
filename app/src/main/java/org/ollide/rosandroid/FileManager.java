package org.ollide.rosandroid;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class FileManager {
    private static final String TAG = FileManager.class.getSimpleName();
    public static File copyResource(Resources r, int rsrcId, File dstFile) throws IOException {
        InputStream is = r.openRawResource(rsrcId);
        FileOutputStream os = new FileOutputStream(dstFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
        }
        os.close();
        is.close();
        return dstFile;
    }

    public static void closeQuietly( Closeable resource ) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch( Exception ex ) {
            Log.e(TAG, "Exception during InputStream.close()" + ex.toString());
        }
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    public static void copyAssetFile(AssetManager assetManager, String assetFileRelPath, String outFilePath) {
        // https://stackoverflow.com/questions/67752689/android-copy-file-from-assets-raw-dir-to-internal-storage
        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(assetFileRelPath);
            File outFile = new File(outFilePath);
            out = new FileOutputStream(outFile);
            copyFile(in, out);
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
        } catch (IOException e) {
            Log.e("tag", "Failed to copy asset file: " + assetFileRelPath, e);
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    public static void copyAssetDir(AssetManager assetManager, String assetDirRelPath, String outDirPath) {
        // https://stackoverflow.com/questions/67752689/android-copy-file-from-assets-raw-dir-to-internal-storage
        String[] files = null;
        try {
            files = assetManager.list(assetDirRelPath);
        } catch (IOException e) {
            Log.e("tag", "Failed to get asset file list.", e);
        }
        if (files != null) {
            for (String filename : files) {
                String assetFileRelPath = assetDirRelPath + "/" + filename;
                String outFilePath = outDirPath + "/" + filename;
                copyAssetFile(assetManager, assetFileRelPath, outFilePath);
            }
        }
    }

    public static void writeToFile(String data, File outputfile) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(outputfile));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }
}
