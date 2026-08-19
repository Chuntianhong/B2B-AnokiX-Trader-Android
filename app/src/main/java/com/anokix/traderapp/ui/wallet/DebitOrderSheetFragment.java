package com.anokix.traderapp.ui.wallet;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.OrderFormat;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.DebitRequestsData;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Authorise IMB to debit the trader's own wallet — once off, or monthly between two
 * dates (POST api/common/debit-requests).
 *
 * The account being debited is resolved server-side from the caller's active wallet, so
 * nothing on this form identifies an account: a merchant can only ever debit themselves.
 * The account number shown in the intro is there to tell them which wallet it will hit,
 * not to choose one.
 *
 * The amount and reference bounds come from the {@code limits} block on the list call
 * rather than being hard-coded, so the form follows the server if those ever move.
 */
public class DebitOrderSheetFragment extends BottomSheetDialogFragment {

    public static final String TAG = "DebitOrderSheet";

    private static final String ARG_ACCOUNT = "account_number";
    private static final String ARG_MIN = "min_amount";
    private static final String ARG_MAX = "max_amount";
    private static final String ARG_MAX_REF = "max_reference";

    /** The API's date format for both debit dates. */
    private static final String API_DATE = "yyyy-MM-dd";

    /** Implemented by the host screen so the list can reload once one is created. */
    public interface Host {
        void onDebitOrderCreated();
    }

    private TextView segOnceOff, segMonthly, referenceCounter, footnote, firstDate, lastDate;
    private EditText amountInput, referenceInput;
    private View dateBlock;
    private MaterialButton authoriseBtn;

    private boolean onceOff = true;
    /** "yyyy-MM-dd", or null until picked. Only used by the monthly schedule. */
    private String firstDateValue, lastDateValue;

    private double minAmount = 1;
    private double maxAmount = 500000;
    private int maxReference = 20;

    public static DebitOrderSheetFragment newInstance(@Nullable String accountNumber,
                                                      DebitRequestsData.Limits limits) {
        Bundle args = new Bundle();
        args.putString(ARG_ACCOUNT, accountNumber);
        if (limits != null) {
            args.putDouble(ARG_MIN, limits.min());
            args.putDouble(ARG_MAX, limits.max());
            args.putInt(ARG_MAX_REF, limits.maxReference());
        }
        DebitOrderSheetFragment f = new DebitOrderSheetFragment();
        f.setArguments(args);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_wallet_debit_order, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        Bundle args = getArguments();
        String account = args == null ? null : args.getString(ARG_ACCOUNT);
        if (args != null) {
            minAmount = args.getDouble(ARG_MIN, minAmount);
            maxAmount = args.getDouble(ARG_MAX, maxAmount);
            maxReference = args.getInt(ARG_MAX_REF, maxReference);
        }

        segOnceOff = v.findViewById(R.id.segOnceOff);
        segMonthly = v.findViewById(R.id.segMonthly);
        amountInput = v.findViewById(R.id.inputAmount);
        referenceInput = v.findViewById(R.id.inputReference);
        referenceCounter = v.findViewById(R.id.referenceCounter);
        dateBlock = v.findViewById(R.id.dateBlock);
        firstDate = v.findViewById(R.id.inputFirstDate);
        lastDate = v.findViewById(R.id.inputLastDate);
        footnote = v.findViewById(R.id.debitFootnote);
        authoriseBtn = v.findViewById(R.id.btnAuthorise);

        TextView intro = v.findViewById(R.id.debitIntro);
        intro.setText(account == null || account.isEmpty()
                ? getString(R.string.debit_intro_no_account)
                : getString(R.string.debit_intro, account));

        v.findViewById(R.id.sheetClose).setOnClickListener(x -> dismiss());

        // The reference is printed on the debit itself and the bank truncates it, so the
        // limit is enforced as you type rather than rejected after the fact.
        referenceInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxReference)});
        referenceInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updateCounter(); }
        });
        updateCounter();

        segOnceOff.setOnClickListener(x -> setSchedule(true));
        segMonthly.setOnClickListener(x -> setSchedule(false));
        setSchedule(true);

        firstDate.setOnClickListener(x -> pickDate(true));
        lastDate.setOnClickListener(x -> pickDate(false));

        authoriseBtn.setOnClickListener(x -> submit());
    }

    // ---- Form ------------------------------------------------------------

    private void updateCounter() {
        referenceCounter.setText(getString(R.string.debit_reference_counter,
                referenceInput.getText().length(), maxReference));
    }

    private void setSchedule(boolean once) {
        onceOff = once;
        paintSegment(segOnceOff, once);
        paintSegment(segMonthly, !once);
        dateBlock.setVisibility(once ? View.GONE : View.VISIBLE);
        footnote.setText(once ? R.string.debit_footnote_once_off : R.string.debit_footnote_monthly);
    }

    /**
     * Selected reads as a filled purple pill with white text, exactly as the portal draws
     * it (wallet04/wallet05). It must never be a white pill carrying white text — that is
     * invisible, and it is what this drew before.
     */
    private void paintSegment(TextView tab, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.bg_segment_tab_selected : 0);
        tab.setTextColor(ContextCompat.getColor(requireContext(),
                selected ? R.color.white : R.color.text_secondary));
    }

    /**
     * @param first true for the first debit date, false for the last. The last date's
     *              calendar starts at the first date, so an out-of-order pair cannot be
     *              chosen in the first place.
     */
    private void pickDate(boolean first) {
        long floor = todayUtc();
        if (!first && firstDateValue != null) {
            Long picked = parseUtc(firstDateValue);
            if (picked != null) floor = picked;
        }

        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.from(floor))
                .setStart(floor)
                .setOpenAt(floor)
                .build();

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(first ? R.string.debit_first_date : R.string.debit_last_date)
                .setCalendarConstraints(constraints)
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            String value = formatUtc(selection);
            if (first) {
                firstDateValue = value;
                firstDate.setText(displayDate(selection));
                // Moving the start past the end would leave an impossible pair on screen.
                if (lastDateValue != null && lastDateValue.compareTo(value) < 0) {
                    lastDateValue = null;
                    lastDate.setText("");
                }
            } else {
                lastDateValue = value;
                lastDate.setText(displayDate(selection));
            }
        });
        picker.show(getChildFragmentManager(), first ? "firstDebitDate" : "lastDebitDate");
    }

    // ---- Submit ----------------------------------------------------------

    private void submit() {
        String rawAmount = amountInput.getText().toString().trim();
        if (rawAmount.isEmpty()) {
            amountInput.setError(getString(R.string.debit_err_amount));
            amountInput.requestFocus();
            return;
        }
        double amount = parseAmount(rawAmount);
        if (amount < minAmount) {
            amountInput.setError(getString(R.string.debit_err_min, OrderFormat.money(minAmount, "R")));
            amountInput.requestFocus();
            return;
        }
        if (amount > maxAmount) {
            amountInput.setError(getString(R.string.debit_err_max, OrderFormat.money(maxAmount, "R")));
            amountInput.requestFocus();
            return;
        }

        String reference = referenceInput.getText().toString().trim();
        if (reference.isEmpty()) {
            referenceInput.setError(getString(R.string.debit_err_reference));
            referenceInput.requestFocus();
            return;
        }

        if (!onceOff) {
            if (firstDateValue == null) {
                toast(getString(R.string.debit_err_first_date));
                return;
            }
            if (lastDateValue == null) {
                toast(getString(R.string.debit_err_last_date));
                return;
            }
            if (lastDateValue.compareTo(firstDateValue) < 0) {
                toast(getString(R.string.debit_err_date_order));
                return;
            }
        }

        authoriseBtn.setEnabled(false);
        authoriseBtn.setText(R.string.please_wait);

        ApiClient.get(requireContext()).createDebitRequest(
                String.format(Locale.US, "%.2f", amount), reference, onceOff,
                firstDateValue, lastDateValue,
                new ApiCallback<DebitRequestsData.DebitRequest>() {
                    @Override
                    public void onSuccess(DebitRequestsData.DebitRequest created) {
                        onSuccess(created, null);
                    }

                    /** The server's own wording ("Transfer request created") is worth showing. */
                    @Override
                    public void onSuccess(DebitRequestsData.DebitRequest created, String message) {
                        if (!isAdded()) return;
                        toast(message == null || message.isEmpty()
                                ? getString(R.string.debit_created) : message);
                        notifyHost();
                        dismiss();
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        authoriseBtn.setEnabled(true);
                        authoriseBtn.setText(R.string.debit_authorise);
                        toast(message == null ? getString(R.string.debit_failed) : message);
                    }
                });
    }

    private void notifyHost() {
        Host host = null;
        if (getParentFragment() instanceof Host) {
            host = (Host) getParentFragment();
        } else if (getActivity() instanceof Host) {
            host = (Host) getActivity();
        }
        if (host != null) host.onDebitOrderCreated();
    }

    // ---- Dates -----------------------------------------------------------

    /**
     * MaterialDatePicker works in UTC milliseconds, so every conversion here is pinned to
     * UTC. Reading them back in the phone's own zone would shift the date by a day either
     * side of midnight — and a debit order raised for the wrong day is a real problem.
     */
    private static SimpleDateFormat utcFormat(String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f;
    }

    private static String formatUtc(long millis) {
        return utcFormat(API_DATE).format(new Date(millis));
    }

    private static String displayDate(long millis) {
        return utcFormat("dd MMM yyyy").format(new Date(millis));
    }

    @Nullable
    private static Long parseUtc(String value) {
        try {
            Date d = utcFormat(API_DATE).parse(value);
            return d == null ? null : d.getTime();
        } catch (Exception e) {
            return null;
        }
    }

    private static long todayUtc() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private double parseAmount(String raw) {
        if (raw == null) return 0;
        String cleaned = raw.trim().replace(",", "").replace("R", "").trim();
        if (cleaned.isEmpty()) return 0;
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void toast(String message) {
        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }
}
