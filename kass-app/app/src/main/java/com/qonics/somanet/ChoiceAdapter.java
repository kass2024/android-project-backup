package com.qonics.somanet;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Tappable name row used by stock entry and stock issue. */
public class ChoiceAdapter extends BaseAdapter {
    public static class Row {
        public int id;
        public String title;
        public String detail;
        public boolean alert;
        public double amount;

        public Row(int id, String title, String detail, boolean alert, double amount) {
            this.id = id;
            this.title = title;
            this.detail = detail;
            this.alert = alert;
            this.amount = amount;
        }
    }

    private final LayoutInflater inflater;
    private final List<Row> rows = new ArrayList<>();

    public ChoiceAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setRows(List<Row> next) {
        rows.clear();
        if (next != null) rows.addAll(next);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return rows.size(); }
    @Override public Row getItem(int position) { return rows.get(position); }
    @Override public long getItemId(int position) { return rows.get(position).id; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView != null ? convertView : inflater.inflate(R.layout.item_choice_row, parent, false);
        Row row = rows.get(position);
        TextView title = view.findViewById(R.id.txtChoiceTitle);
        TextView detail = view.findViewById(R.id.txtChoiceDetail);
        TextView mark = view.findViewById(R.id.txtChoiceMark);
        title.setText(row.title);
        detail.setText(row.detail);
        detail.setTextColor(row.alert ? Color.parseColor("#B42318") : Color.parseColor("#64748B"));
        String letter = row.title == null || row.title.trim().isEmpty() ? "?" : row.title.trim().substring(0, 1).toUpperCase();
        mark.setText(letter);
        mark.setBackgroundResource(row.alert ? R.drawable.bg_avatar_alert : R.drawable.bg_avatar);
        mark.setTextColor(row.alert ? Color.parseColor("#B42318") : Color.parseColor("#0369A1"));
        return view;
    }
}
