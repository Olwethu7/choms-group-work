package com.match.choms.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.match.choms.databinding.ItemRowBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One RecyclerView adapter used by the Beneficiary, Visit and Referral lists. */
class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {

    /** What one list row shows. */
    static class Row {
        String id, title, subtitle, detail, badge, searchText;
    }

    interface OnRowClick { void onClick(Row row); }

    private final List<Row> all = new ArrayList<>();
    private final List<Row> shown = new ArrayList<>();
    private final OnRowClick listener;

    RowAdapter(OnRowClick listener) { this.listener = listener; }

    void setRows(List<Row> rows, String query) {
        all.clear();
        all.addAll(rows);
        filter(query);
    }

    /** Case-insensitive search: every word typed must appear somewhere in the row. */
    void filter(String query) {
        shown.clear();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (Row r : all) {
            boolean match = true;
            for (String term : q.split("\\s+")) {
                if (!term.isEmpty() && !r.searchText.toLowerCase(Locale.ROOT).contains(term)) { match = false; break; }
            }
            if (match) shown.add(r);
        }
        notifyDataSetChanged();
    }

    int shownCount() { return shown.size(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Row r = shown.get(position);
        h.b.tvTitle.setText(r.title);
        h.b.tvSubtitle.setText(r.subtitle);
        h.b.tvDetail.setText(r.detail);
        h.b.tvDetail.setVisibility(r.detail == null || r.detail.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        h.b.tvBadge.setText(r.badge);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(48f);
        bg.setColor(colorFor(r.badge));
        h.b.tvBadge.setBackground(bg);
        h.b.getRoot().setOnClickListener(v -> listener.onClick(r));
    }

    @Override public int getItemCount() { return shown.size(); }

    /** Status -> badge colour (green = good, amber = needs attention, red = problem, grey = closed). */
    static int colorFor(String status) {
        if (status == null) return Color.parseColor("#6B7280");
        switch (status) {
            case "active": case "completed": case "attended": case "user": return Color.parseColor("#15803D");
            case "admin": return Color.parseColor("#6D28D9");
            case "scheduled": case "booked": return Color.parseColor("#1D4ED8");
            case "pending": case "urgent": return Color.parseColor("#B45309");
            case "emergency": case "missed": case "deactivated": return Color.parseColor("#B91C1C");
            case "coordinator": return Color.parseColor("#1D4ED8");
            case "chw": return Color.parseColor("#15803D");
            default: return Color.parseColor("#6B7280");
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemRowBinding b;
        VH(ItemRowBinding b) { super(b.getRoot()); this.b = b; }
    }
}
