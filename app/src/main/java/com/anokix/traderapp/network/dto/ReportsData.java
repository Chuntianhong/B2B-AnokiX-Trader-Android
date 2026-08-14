package com.anokix.traderapp.network.dto;

import java.util.List;
import java.util.Locale;

/**
 * Response for GET api/trader/reports — everything the Reports screen needs in one call:
 * the catalogue of reports the trader may generate ({@link #available}), the reports that
 * have already been generated ({@link #reports}) and the KPI {@link Summary}.
 *
 * The list is searched/filtered on the device — the endpoint takes no query params.
 */
public class ReportsData {

    /** Catalogue of report types the trader can generate. */
    public List<Available> available;
    /** Reports already generated, newest first. */
    public List<Report> reports;
    public Summary summary;

    /** One entry in the "Choose a report" dropdown of the Generate sheet. */
    public static class Available {
        /** Value sent as {@code report_key} when generating, e.g. "till_sales". */
        public String key;
        /** Display name, e.g. "Till Sales". */
        public String name;
        /** Server category, e.g. "sales" | "products" | "orders" | "inventory" | "rewards". */
        public String category;
        /** One-line explanation shown under the picker. */
        public String about;
    }

    /** One generated report file. */
    public static class Report {
        public int id;
        public String name;
        public String category;
        /** "yyyy-MM-dd". */
        public String period_from;
        /** "yyyy-MM-dd". */
        public String period_to;
        /** "CSV". */
        public String format;
        public long size_bytes;
        public int row_count;
        /** "ready" | "generating" | "failed". */
        public String status;
        /** Failure reason when {@link #status} is "failed". */
        public String error;
        public int downloads;
        /** "yyyy-MM-dd HH:mm:ss". */
        public String created_at;

        public boolean isReady() {
            return "ready".equalsIgnoreCase(status == null ? "" : status);
        }

        /** "190 B" / "1.2 KB" / "3.4 MB". */
        public String sizeLabel() {
            if (size_bytes <= 0) return "—";
            if (size_bytes < 1024) return size_bytes + " B";
            double kb = size_bytes / 1024d;
            if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
            return String.format(Locale.US, "%.1f MB", kb / 1024d);
        }

        /** File name suggested for the download, e.g. "till-sales-22.csv". */
        public String fileName() {
            String base = (name == null || name.isEmpty() ? "report" : name)
                    .toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
            String ext = (format == null || format.isEmpty() ? "csv" : format).toLowerCase(Locale.US);
            return base + "-" + id + "." + ext;
        }
    }

    /** KPI cards across the top of the screen. */
    public static class Summary {
        public int generated;
        public int downloads;
        public int this_month;
        public int failed;
    }
}
