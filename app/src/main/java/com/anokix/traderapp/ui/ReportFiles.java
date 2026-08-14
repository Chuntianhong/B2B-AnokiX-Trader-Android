package com.anokix.traderapp.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.Http;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

/**
 * Saves a downloaded report to the app's own Downloads folder and offers to view or
 * share it. The folder lives under {@code getExternalFilesDir(DIRECTORY_DOWNLOADS)},
 * which needs no runtime storage permission, and is exported to other apps through the
 * existing {@code ${applicationId}.fileprovider} authority (see res/xml/file_paths.xml).
 */
final class ReportFiles {

    private ReportFiles() {}

    /**
     * Write the bytes to disk and show the "Report downloaded" dialog.
     *
     * @param fallbackName used when the server sent no Content-Disposition file name
     */
    static void saveAndOffer(Activity activity, Http.BinaryResult download, String fallbackName) {
        File file;
        try {
            file = write(activity, download, fallbackName);
        } catch (Exception e) {
            Toast.makeText(activity, R.string.report_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        final String mime = mimeFor(download, file);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.report_downloaded)
                .setMessage(activity.getString(R.string.report_downloaded_body, file.getName()))
                .setPositiveButton(R.string.view, (d, w) -> view(activity, file, mime))
                .setNeutralButton(R.string.share, (d, w) -> share(activity, file, mime))
                .setNegativeButton(R.string.cancel_btn, null)
                .show();
    }

    private static File write(Activity activity, Http.BinaryResult download, String fallbackName)
            throws Exception {
        File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            // No external storage mounted — fall back to internal, which file_paths.xml
            // also exposes to the FileProvider.
            dir = new File(activity.getFilesDir(), "downloads");
        }
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();

        File file = new File(dir, safeName(download.fileName, fallbackName));
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(download.bytes);
        }
        return file;
    }

    /** Strip any path separators a hostile Content-Disposition might carry. */
    private static String safeName(String serverName, String fallbackName) {
        String name = serverName == null || serverName.trim().isEmpty() ? fallbackName : serverName;
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.trim();
        return name.isEmpty() ? fallbackName : name;
    }

    /** Prefer the server's content type, unless it is the useless generic one. */
    private static String mimeFor(Http.BinaryResult download, File file) {
        String type = download.contentType;
        if (type != null) {
            int semi = type.indexOf(';');
            if (semi > 0) type = type.substring(0, semi);
            type = type.trim().toLowerCase(Locale.US);
            if (!type.isEmpty() && !type.equals("application/octet-stream")) {
                return type;
            }
        }
        String name = file.getName().toLowerCase(Locale.US);
        if (name.endsWith(".csv")) return "text/csv";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        return "*/*";
    }

    private static Uri uriFor(Activity activity, File file) {
        return FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".fileprovider", file);
    }

    private static void view(Activity activity, File file, String mime) {
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uriFor(activity, file), mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // Most phones have no CSV viewer installed — sharing still works.
            Toast.makeText(activity, R.string.report_no_viewer, Toast.LENGTH_LONG).show();
        }
    }

    private static void share(Activity activity, File file, String mime) {
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uriFor(activity, file))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(
                Intent.createChooser(intent, activity.getString(R.string.share)));
    }
}
