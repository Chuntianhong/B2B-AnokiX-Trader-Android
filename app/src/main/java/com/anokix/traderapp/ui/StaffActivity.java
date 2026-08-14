package com.anokix.traderapp.ui;

import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.StaffData;
import com.anokix.traderapp.network.dto.StaffMemberData;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Staff — GET api/trader/staff, the people the trader has given till access to.
 *
 * Each person signs in to the app with their own email and password. From here the
 * trader can add someone (POST api/trader/staff), reset their password
 * (POST api/trader/staff/password) and switch their sign-in on or off
 * (POST api/trader/staff/update with status active|disabled).
 */
public class StaffActivity extends AppCompatActivity {

    /** The API rejects anything shorter, and the form says so up front. */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final List<StaffData.Member> staff = new ArrayList<>();

    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout staffList;
    private View loading;
    private View emptyView;
    private TextView staffCount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        staffList = findViewById(R.id.staffList);
        staffCount = findViewById(R.id.staffCount);
        loading = findViewById(R.id.loading);
        emptyView = findViewById(R.id.emptyView);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.purple_primary);
        swipeRefresh.setOnRefreshListener(() -> load(false));

        findViewById(R.id.btnAddStaff).setOnClickListener(v -> showAddSheet());

        load(true);
    }

    // ---- Data ------------------------------------------------------------

    private void load(boolean showSpinner) {
        if (showSpinner) loading.setVisibility(View.VISIBLE);
        ApiClient.get(this).getStaff(new ApiCallback<StaffData>() {
            @Override
            public void onSuccess(StaffData data) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                staff.clear();
                if (data != null && data.staff != null) staff.addAll(data.staff);
                render();
            }

            @Override
            public void onError(String message) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                Toast.makeText(StaffActivity.this, message, Toast.LENGTH_SHORT).show();
                render();
            }
        });
    }

    // ---- List ------------------------------------------------------------

    private void render() {
        staffList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (StaffData.Member member : staff) {
            View row = inflater.inflate(R.layout.item_staff, staffList, false);
            bindRow(row, member);
            staffList.addView(row);
        }

        boolean empty = staff.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        staffCount.setText(empty
                ? getString(R.string.staff_sign_in_hint)
                : getResources().getQuantityString(R.plurals.staff_people, staff.size(), staff.size()));
    }

    private void bindRow(View row, StaffData.Member member) {
        boolean active = member.isActive();

        ((TextView) row.findViewById(R.id.staffInitials)).setText(member.initials());
        ((TextView) row.findViewById(R.id.staffName)).setText(member.displayName());
        ((TextView) row.findViewById(R.id.staffEmail)).setText(safe(member.email));

        String jobTitle = member.job_title == null || member.job_title.trim().isEmpty()
                ? getString(R.string.staff_no_job_title) : member.job_title;
        ((TextView) row.findViewById(R.id.staffMeta))
                .setText(jobTitle + " · " + getString(R.string.staff_added_on, formatDate(member.created_at)));

        TextView access = row.findViewById(R.id.staffAccess);
        access.setText(member.can_sign_in ? R.string.staff_can_sign_in : R.string.staff_disabled);
        access.setBackgroundResource(member.can_sign_in
                ? R.drawable.bg_badge_outline_success : R.drawable.bg_badge_outline_muted);
        access.setTextColor(ContextCompat.getColor(this,
                member.can_sign_in ? R.color.success : R.color.text_secondary));

        // A disabled member is dimmed the way the portal greys the row out.
        row.findViewById(R.id.staffBody).setAlpha(active ? 1f : 0.55f);

        row.findViewById(R.id.btnPassword).setOnClickListener(v -> showPasswordSheet(member));

        MaterialButton toggle = row.findViewById(R.id.btnToggleAccess);
        toggle.setText(active ? R.string.staff_disable : R.string.staff_enable);
        toggle.setTextColor(ContextCompat.getColor(this,
                active ? R.color.danger : R.color.success));
        toggle.setOnClickListener(v -> confirmToggle(member));
    }

    // ---- Enable / disable ------------------------------------------------

    private void confirmToggle(StaffData.Member member) {
        boolean disabling = member.isActive();
        new AlertDialog.Builder(this)
                .setTitle(getString(disabling
                                ? R.string.staff_disable_title : R.string.staff_enable_title,
                        member.displayName()))
                .setMessage(disabling ? R.string.staff_disable_body : R.string.staff_enable_body)
                .setPositiveButton(disabling ? R.string.staff_disable : R.string.staff_enable,
                        (d, w) -> updateStatus(member, disabling ? "disabled" : "active"))
                .setNegativeButton(R.string.cancel_btn, null)
                .show();
    }

    private void updateStatus(StaffData.Member member, String status) {
        ApiClient.get(this).updateStaffStatus(member.id, status,
                new ApiCallback<StaffMemberData>() {
                    @Override
                    public void onSuccess(StaffMemberData data) {
                        onSuccess(data, null);
                    }

                    @Override
                    public void onSuccess(StaffMemberData data, String message) {
                        toast(message, R.string.staff_updated);
                        replace(data);
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(StaffActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Swap the server's updated member into the list in place, then repaint. */
    private void replace(StaffMemberData data) {
        if (data == null || data.staff == null) {
            load(false);
            return;
        }
        for (int i = 0; i < staff.size(); i++) {
            if (staff.get(i).id == data.staff.id) {
                staff.set(i, data.staff);
                render();
                return;
            }
        }
        load(false);
    }

    // ---- Add a staff member ----------------------------------------------

    private void showAddSheet() {
        View sheet = getLayoutInflater().inflate(R.layout.sheet_add_staff, null);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(sheet);

        EditText firstName = sheet.findViewById(R.id.inputFirstName);
        EditText lastName = sheet.findViewById(R.id.inputLastName);
        EditText email = sheet.findViewById(R.id.inputEmail);
        EditText password = sheet.findViewById(R.id.inputPassword);
        EditText jobTitle = sheet.findViewById(R.id.inputJobTitle);
        EditText phone = sheet.findViewById(R.id.inputPhone);
        MaterialButton submit = sheet.findViewById(R.id.btnSubmit);

        bindPasswordToggle(password, sheet.findViewById(R.id.togglePassword));

        sheet.findViewById(R.id.sheetClose).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        submit.setOnClickListener(v -> {
            String first = text(firstName);
            String last = text(lastName);
            String mail = text(email);
            String pass = password.getText().toString();

            if (first.isEmpty()) {
                fail(firstName, R.string.staff_first_name_required);
                return;
            }
            if (last.isEmpty()) {
                fail(lastName, R.string.staff_last_name_required);
                return;
            }
            if (mail.isEmpty()) {
                fail(email, R.string.staff_email_required);
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches()) {
                fail(email, R.string.staff_email_invalid);
                return;
            }
            if (pass.length() < MIN_PASSWORD_LENGTH) {
                fail(password, R.string.staff_password_required);
                return;
            }

            CharSequence original = submit.getText();
            submit.setEnabled(false);
            submit.setText(R.string.staff_saving);

            ApiClient.get(this).addStaff(first, last, mail, pass, text(phone), text(jobTitle),
                    new ApiCallback<StaffMemberData>() {
                        @Override
                        public void onSuccess(StaffMemberData data) {
                            onSuccess(data, null);
                        }

                        @Override
                        public void onSuccess(StaffMemberData data, String message) {
                            dialog.dismiss();
                            toast(message, R.string.staff_added);
                            load(false);
                        }

                        @Override
                        public void onError(String message) {
                            submit.setEnabled(true);
                            submit.setText(original);
                            Toast.makeText(StaffActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        expand(dialog, sheet);
    }

    // ---- Set password ----------------------------------------------------

    private void showPasswordSheet(StaffData.Member member) {
        View sheet = getLayoutInflater().inflate(R.layout.sheet_staff_password, null);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(sheet);

        ((TextView) sheet.findViewById(R.id.sheetTitle))
                .setText(getString(R.string.staff_password_title, member.displayName()));

        EditText password = sheet.findViewById(R.id.inputPassword);
        MaterialButton submit = sheet.findViewById(R.id.btnSubmit);

        bindPasswordToggle(password, sheet.findViewById(R.id.togglePassword));

        sheet.findViewById(R.id.sheetClose).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        submit.setOnClickListener(v -> {
            String pass = password.getText().toString();
            if (pass.length() < MIN_PASSWORD_LENGTH) {
                fail(password, R.string.staff_password_required);
                return;
            }

            CharSequence original = submit.getText();
            submit.setEnabled(false);
            submit.setText(R.string.staff_saving);

            ApiClient.get(this).setStaffPassword(member.id, pass,
                    new ApiCallback<StaffMemberData>() {
                        @Override
                        public void onSuccess(StaffMemberData data) {
                            onSuccess(data, null);
                        }

                        @Override
                        public void onSuccess(StaffMemberData data, String message) {
                            dialog.dismiss();
                            toast(message, R.string.staff_password_updated);
                            replace(data);
                        }

                        @Override
                        public void onError(String message) {
                            submit.setEnabled(true);
                            submit.setText(original);
                            Toast.makeText(StaffActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        expand(dialog, sheet);
    }

    // ---- Helpers ---------------------------------------------------------

    /** Eye button that flips the field between masked and plain text, keeping the caret. */
    private void bindPasswordToggle(EditText field, ImageView toggle) {
        final boolean[] visible = {false};
        toggle.setOnClickListener(v -> {
            visible[0] = !visible[0];
            int selection = field.getSelectionEnd();
            field.setInputType(InputType.TYPE_CLASS_TEXT | (visible[0]
                    ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            field.setSelection(Math.min(selection, field.getText().length()));
            toggle.setImageResource(visible[0]
                    ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
        });
    }

    private void expand(BottomSheetDialog dialog, View sheet) {
        dialog.setOnShowListener(d -> {
            View parent = (View) sheet.getParent();
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(parent);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        });
        dialog.show();
    }

    private void fail(EditText field, int messageRes) {
        field.setError(getString(messageRes));
        field.requestFocus();
    }

    /** Show the server's wording when it sent any, else the local fallback. */
    private void toast(String serverMessage, int fallbackRes) {
        Toast.makeText(this,
                serverMessage == null || serverMessage.isEmpty()
                        ? getString(fallbackRes) : serverMessage,
                Toast.LENGTH_SHORT).show();
    }

    private static String text(EditText field) {
        return field.getText().toString().trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** "2026-08-12 05:16:38" → "12 Aug 2026" (falls back to the raw value). */
    private static String formatDate(String raw) {
        if (raw == null || raw.isEmpty()) return "—";
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(raw);
            if (d != null) return new SimpleDateFormat("dd MMM yyyy", Locale.US).format(d);
        } catch (Exception ignored) {
            // fall through to the raw value
        }
        return raw;
    }
}
