package com.anokix.traderapp.network.dto;

import java.util.List;
import java.util.Locale;

/**
 * Response for GET api/trader/staff — the people the trader has given till access to.
 * Each one signs in to the app with their own email and password.
 */
public class StaffData {

    public List<Member> staff;
    public int total;
    /**
     * What a staff login is allowed to reach, e.g. ["sell"]. Drives the "What staff
     * can do" explainer; the screen falls back to the default wording when empty.
     */
    public List<String> staff_capabilities;

    /** One staff member. */
    public static class Member {
        public int id;
        public int user_id;
        /** Full name as the server composes it. */
        public String name;
        public String first_name;
        public String last_name;
        public String email;
        public String phone;
        public String job_title;
        /** "active" | "disabled". */
        public String status;
        /** Server-side role wording, e.g. "Trader Stuff". */
        public String role_label;
        /** "yyyy-MM-dd HH:mm:ss". */
        public String created_at;
        public boolean can_sign_in;

        public boolean isActive() {
            return "active".equalsIgnoreCase(status == null ? "" : status);
        }

        /** Name to show, falling back to the two parts and then the email. */
        public String displayName() {
            if (name != null && !name.trim().isEmpty()) return name.trim();
            String composed = ((first_name == null ? "" : first_name) + " "
                    + (last_name == null ? "" : last_name)).trim();
            if (!composed.isEmpty()) return composed;
            return email == null ? "" : email;
        }

        /** Uppercase initials for the avatar tile, e.g. "TN". */
        public String initials() {
            String source = displayName();
            if (source.isEmpty()) return "?";
            String[] parts = source.trim().split("\\s+");
            String first = parts[0].substring(0, 1);
            String second = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
            return (first + second).toUpperCase(Locale.US);
        }
    }
}
