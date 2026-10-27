package com.match.choms.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Small date helpers. The web app stores dates as plain "yyyy-MM-dd" strings, so we do the same. */
public final class DateUtils {
    private DateUtils() { }

    private static SimpleDateFormat iso() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setLenient(false); // rejects 2026-02-30
        return f;
    }

    public static String todayISO() { return iso().format(new Date()); }

    public static String nowISO() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));   // same format as JavaScript's toISOString()
        return f.format(new Date());
    }

    /** True only for real calendar dates in yyyy-MM-dd format. */
    public static boolean isValidISODate(String s) {
        if (s == null || !s.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        try {
            iso().parse(s);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    public static boolean isFuture(String s) {
        return isValidISODate(s) && s.compareTo(todayISO()) > 0;
    }

    public static boolean isPast(String s) {
        return isValidISODate(s) && s.compareTo(todayISO()) < 0;
    }

    /** True for yyyy-MM-ddTHH:mm strings (what the web app stores for appointments). */
    public static boolean isValidDateTime(String s) {
        if (s == null || !s.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")) return false;
        int h = Integer.parseInt(s.substring(11, 13));
        int m = Integer.parseInt(s.substring(14, 16));
        return isValidISODate(s.substring(0, 10)) && h < 24 && m < 60;
    }

    public static boolean isFutureDateTime(String s) {
        if (!isValidDateTime(s)) return false;
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        return s.compareTo(f.format(new Date())) > 0;
    }

    /** The reminder goes out 24 hours before the appointment. */
    public static String reminderDue(String datetime) {
        if (!isValidDateTime(datetime)) return "";
        Calendar c = toCalendar(datetime.substring(0, 10));
        c.add(Calendar.DAY_OF_MONTH, -1);
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
                + datetime.substring(10);
    }

    /** yyyy-MM-ddTHH:mm -> dd/MM/yyyy HH:mm for display. */
    public static String displayDateTime(String dt) {
        if (!isValidDateTime(dt)) return "-";
        return display(dt.substring(0, 10)) + " " + dt.substring(11);
    }

    /** Age in whole years, or -1 if the date is invalid. */
    public static int age(String dob) {
        if (!isValidISODate(dob)) return -1;
        int y = Integer.parseInt(dob.substring(0, 4));
        int m = Integer.parseInt(dob.substring(5, 7));
        int d = Integer.parseInt(dob.substring(8, 10));
        Calendar now = Calendar.getInstance();
        int age = now.get(Calendar.YEAR) - y;
        int month = now.get(Calendar.MONTH) + 1;
        if (month < m || (month == m && now.get(Calendar.DAY_OF_MONTH) < d)) age--;
        return age;
    }

    /** yyyy-MM-dd -> dd/MM/yyyy for display. */
    public static String display(String iso) {
        if (iso == null || !isValidISODate(iso.length() >= 10 ? iso.substring(0, 10) : iso)) return "-";
        String s = iso.substring(0, 10);
        return s.substring(8, 10) + "/" + s.substring(5, 7) + "/" + s.substring(0, 4);
    }

    /** Parse yyyy-MM-dd into a Calendar (today if invalid) - used to open the date picker. */
    public static Calendar toCalendar(String iso) {
        Calendar c = Calendar.getInstance();
        if (isValidISODate(iso)) {
            c.set(Integer.parseInt(iso.substring(0, 4)), Integer.parseInt(iso.substring(5, 7)) - 1,
                    Integer.parseInt(iso.substring(8, 10)));
        }
        return c;
    }
}
