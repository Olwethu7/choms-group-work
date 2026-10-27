package com.match.choms.ui;

import android.app.DatePickerDialog;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.match.choms.util.DateUtils;
import com.match.choms.util.Option;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Helpers shared by the form screens (drop-downs, date pickers, showing validation errors). */
final class FormUtils {
    private FormUtils() { }

    /** A drop-down that shows labels but returns the stored value (e.g. shows "Thandi", returns her UID). */
    static class Dropdown {
        private final AutoCompleteTextView view;
        private List<Option> options = new ArrayList<>();
        private String pendingValue;

        Dropdown(AutoCompleteTextView view) { this.view = view; }

        void setOptions(List<Option> newOptions) {
            options = newOptions;
            view.setAdapter(new ArrayAdapter<>(view.getContext(), android.R.layout.simple_list_item_1, options));
            if (pendingValue != null) select(pendingValue); // value chosen before the options arrived (edit screens)
        }

        /** Selected value ("" if nothing selected). */
        String value() {
            String text = view.getText().toString();
            for (Option o : options) if (o.label.equals(text)) return o.value;
            return "";
        }

        String label() { return view.getText().toString(); }

        void select(String value) {
            pendingValue = value;
            if (value == null) return;
            for (Option o : options) {
                if (o.value.equals(value)) {
                    view.setText(o.label, false); // false = do not filter the list
                    return;
                }
            }
        }
    }

    /** Turn ["female","male"] into options labelled "Female","Male". */
    static List<Option> options(List<String> values) {
        List<Option> out = new ArrayList<>();
        for (String v : values) {
            String label = v.isEmpty() ? v : Character.toUpperCase(v.charAt(0)) + v.substring(1);
            out.add(new Option(v, label));
        }
        return out;
    }

    /** Tapping the field opens a calendar and writes yyyy-MM-dd into it. */
    static void bindDatePicker(Context context, TextInputEditText field) {
        field.setFocusable(false);
        field.setClickable(true);
        field.setOnClickListener(v -> {
            Calendar c = DateUtils.toCalendar(String.valueOf(field.getText()));
            new DatePickerDialog(context, (picker, y, m, d) ->
                    field.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    /** Show an error under each field that has one, clear the others, and focus the first bad field. */
    static void showErrors(Map<String, String> errors, Map<String, TextInputLayout> fields) {
        TextInputLayout first = null;
        for (Map.Entry<String, TextInputLayout> e : fields.entrySet()) {
            String msg = errors == null ? null : errors.get(e.getKey());
            e.getValue().setError(msg);                 // null clears the error
            if (msg != null && first == null) first = e.getValue();
        }
        if (first != null) first.requestFocus();
    }

    static String text(TextInputEditText t) {
        return t.getText() == null ? "" : t.getText().toString();
    }
}
