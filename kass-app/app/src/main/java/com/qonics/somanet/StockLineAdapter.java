package com.qonics.somanet;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import java.util.ArrayList;
import java.util.List;

/** Item rows for stock entry (radio) and stock issue (stepper). */
public class StockLineAdapter extends BaseAdapter {
    public interface Listener {
        void onPick(ChoiceAdapter.Row row);
        void onStep(ChoiceAdapter.Row row, int delta);
    }

    private static final int[] INK = {
            0xFF2563EB, 0xFF059669, 0xFFEA580C, 0xFF7C3AED, 0xFFDB2777
    };
    private static final int[] WASH = {
            0xFFDBEAFE, 0xFFD1FAE5, 0xFFFFEDD5, 0xFFEDE9FE, 0xFFFCE7F3
    };

    private final LayoutInflater inflater;
    private final boolean stepper;
    private final List<ChoiceAdapter.Row> rows = new ArrayList<>();
    private Listener listener;
    private int selectedId;
    private double selectedQty;

    public StockLineAdapter(Context context, boolean stepper) {
        inflater = LayoutInflater.from(context);
        this.stepper = stepper;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setRows(List<ChoiceAdapter.Row> next) {
        rows.clear();
        if (next != null) rows.addAll(next);
        notifyDataSetChanged();
    }

    public void setSelected(int id, double qty) {
        selectedId = id;
        selectedQty = qty;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return rows.size(); }
    @Override public ChoiceAdapter.Row getItem(int position) { return rows.get(position); }
    @Override public long getItemId(int position) { return rows.get(position).id; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int layout = stepper ? R.layout.item_stock_issue_row : R.layout.item_stock_entry_row;
        View view = convertView != null ? convertView : inflater.inflate(layout, parent, false);
        ChoiceAdapter.Row row = rows.get(position);
        boolean selected = row.id == selectedId && selectedId > 0;
        TextView title = view.findViewById(R.id.txtChoiceTitle);
        TextView detail = view.findViewById(R.id.txtChoiceDetail);
        TextView mark = view.findViewById(R.id.txtChoiceMark);
        title.setText(row.title);
        detail.setText(row.detail);
        detail.setTextColor(row.alert ? Color.parseColor("#B42318") : Color.parseColor("#64748B"));
        paintMark(mark, row, position);
        if (view instanceof CardView) {
            ((CardView) view).setCardBackgroundColor(selected ? 0xFFEAF3FF : Color.WHITE);
        }
        View radio = view.findViewById(R.id.mark_radio);
        if (radio != null) {
            radio.setBackgroundResource(selected ? R.drawable.bg_radio_on : R.drawable.bg_radio_off);
        }
        TextView stepQty = view.findViewById(R.id.txt_step_qty);
        if (stepQty != null) {
            stepQty.setText(selected ? StockFormat.qty(selectedQty) : "0");
        }
        TextView minus = view.findViewById(R.id.btn_minus);
        TextView plus = view.findViewById(R.id.btn_plus);
        if (minus != null) {
            minus.setOnClickListener(v -> {
                if (listener != null) listener.onStep(row, -1);
            });
        }
        if (plus != null) {
            plus.setOnClickListener(v -> {
                if (listener != null) listener.onStep(row, 1);
            });
        }
        return view;
    }

    private void paintMark(TextView mark, ChoiceAdapter.Row row, int position) {
        String letter = row.title == null || row.title.trim().isEmpty()
                ? "?"
                : row.title.trim().substring(0, 1).toUpperCase();
        mark.setText(letter);
        int ink = row.alert ? 0xFFB42318 : INK[position % INK.length];
        int wash = row.alert ? 0xFFFEE4E2 : WASH[position % WASH.length];
        GradientDrawable disk = new GradientDrawable();
        disk.setShape(GradientDrawable.OVAL);
        disk.setColor(wash);
        mark.setBackground(disk);
        mark.setTextColor(ink);
    }
}
