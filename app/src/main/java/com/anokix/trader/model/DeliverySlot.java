package com.anokix.trader.model;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A single selectable delivery window (one date + one time range), generated
 * locally from the trader's {@code preferred_delivery_days} string.
 *
 * Format of {@code preferred_delivery_days} (from the login response), e.g.
 * {@code "1:8.00-12,15-22/2:8.00-22,-/3:8.00-22,/4:8.00-22,/5:8.00-22"}:
 *   - entries are separated by {@code /}
 *   - each entry is {@code <dayOfWeek>:<range>,<range>} where dayOfWeek 1=Monday … 7=Sunday
 *   - each range is {@code start-end}; times use {@code .} as the minute separator
 *     ({@code 8.00} = 08:00) and a bare hour means {@code :00} ({@code 12} = 12:00)
 *   - an empty range or {@code -} is ignored
 *
 * Slots are generated from tomorrow for the next 4 weeks.
 */
public final class DeliverySlot {

    /** "yyyy-MM-dd" used for the API delivery_date. */
    public final String deliveryDate;
    /** "HH:mm" used for display. */
    public final String startDisplay;
    public final String endDisplay;
    /** "HH:mm:ss" used for the API. */
    public final String startApi;
    public final String endApi;
    /** e.g. "Tomorrow (Fri, 26 Jun)" or "Mon (29 Jun)". */
    public final String dateLabel;

    private DeliverySlot(String deliveryDate, String startDisplay, String endDisplay,
                         String startApi, String endApi, String dateLabel) {
        this.deliveryDate = deliveryDate;
        this.startDisplay = startDisplay;
        this.endDisplay = endDisplay;
        this.startApi = startApi;
        this.endApi = endApi;
        this.dateLabel = dateLabel;
    }

    /** Full label, e.g. "Mon (29 Jun) 08:00 - 12:00". */
    public String label() {
        return dateLabel + " " + startDisplay + " - " + endDisplay;
    }

    // ---- Generation ------------------------------------------------------

    private static final int WEEKS_AHEAD = 4;

    /**
     * Generate the selectable slots for the next 4 weeks (starting tomorrow)
     * from the {@code preferred_delivery_days} string. Returns an empty list
     * when the string is null/blank or unparseable.
     */
    public static List<DeliverySlot> generate(String preferredDeliveryDays) {
        List<DeliverySlot> slots = new ArrayList<>();
        Map<Integer, List<String[]>> byDay = parse(preferredDeliveryDays);
        if (byDay.isEmpty()) return slots;

        SimpleDateFormat apiDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat weekdayShort = new SimpleDateFormat("EEE", Locale.US);   // Mon
        SimpleDateFormat dayMonth = new SimpleDateFormat("dd MMM", Locale.US);    // 29 Jun

        Calendar today = Calendar.getInstance();
        clearTime(today);

        Calendar cursor = (Calendar) today.clone();
        cursor.add(Calendar.DAY_OF_MONTH, 1); // start from tomorrow

        for (int i = 0; i < WEEKS_AHEAD * 7; i++) {
            int iso = isoDayOfWeek(cursor);
            List<String[]> ranges = byDay.get(iso);
            if (ranges != null) {
                Date date = cursor.getTime();
                String relative = relativePrefix(today, cursor);
                String wd = weekdayShort.format(date);
                String dm = dayMonth.format(date);
                String dateLabel = relative != null
                        ? relative + " (" + wd + ", " + dm + ")"
                        : wd + " (" + dm + ")";
                String iso8601 = apiDate.format(date);
                for (String[] range : ranges) {
                    slots.add(new DeliverySlot(iso8601, range[0], range[1],
                            range[0] + ":00", range[1] + ":00", dateLabel));
                }
            }
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }
        return slots;
    }

    /** Maps ISO day (1=Mon..7=Sun) → list of [startDisplay, endDisplay] ranges. */
    private static Map<Integer, List<String[]>> parse(String raw) {
        Map<Integer, List<String[]>> map = new LinkedHashMap<>();
        if (raw == null || raw.trim().isEmpty()) return map;
        for (String entry : raw.split("/")) {
            if (entry.trim().isEmpty()) continue;
            int colon = entry.indexOf(':');
            if (colon <= 0) continue;
            int day;
            try {
                day = Integer.parseInt(entry.substring(0, colon).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (day < 1 || day > 7) continue;
            String rest = entry.substring(colon + 1);
            List<String[]> ranges = new ArrayList<>();
            for (String r : rest.split(",")) {
                String[] range = parseRange(r);
                if (range != null) ranges.add(range);
            }
            if (!ranges.isEmpty()) map.put(day, ranges);
        }
        return map;
    }

    /** Parse "8.00-12" → ["08:00", "12:00"]; returns null for empty/"-". */
    private static String[] parseRange(String range) {
        if (range == null) return null;
        String r = range.trim();
        if (r.isEmpty() || r.equals("-")) return null;
        int dash = r.indexOf('-');
        if (dash <= 0 || dash == r.length() - 1) return null;
        String start = normalizeTime(r.substring(0, dash));
        String end = normalizeTime(r.substring(dash + 1));
        if (start == null || end == null) return null;
        return new String[]{start, end};
    }

    /** "8.00" → "08:00", "12" → "12:00", "15.30" → "15:30". */
    private static String normalizeTime(String t) {
        if (t == null) return null;
        String s = t.trim().replace('.', ':');
        if (s.isEmpty()) return null;
        int hour;
        int minute = 0;
        try {
            int colon = s.indexOf(':');
            if (colon >= 0) {
                hour = Integer.parseInt(s.substring(0, colon).trim());
                String mm = s.substring(colon + 1).trim();
                minute = mm.isEmpty() ? 0 : Integer.parseInt(mm);
            } else {
                hour = Integer.parseInt(s);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }

    /** Calendar's day-of-week (1=Sun..7=Sat) → ISO (1=Mon..7=Sun). */
    private static int isoDayOfWeek(Calendar c) {
        int dow = c.get(Calendar.DAY_OF_WEEK); // 1=Sun..7=Sat
        return dow == Calendar.SUNDAY ? 7 : dow - 1;
    }

    private static String relativePrefix(Calendar today, Calendar date) {
        long days = daysBetween(today, date);
        if (days == 0) return "Today";
        if (days == 1) return "Tomorrow";
        return null;
    }

    private static long daysBetween(Calendar a, Calendar b) {
        long ms = b.getTimeInMillis() - a.getTimeInMillis();
        return Math.round(ms / (24.0 * 60 * 60 * 1000));
    }

    private static void clearTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}
