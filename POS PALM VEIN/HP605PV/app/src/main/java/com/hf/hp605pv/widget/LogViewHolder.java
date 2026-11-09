package com.hf.hp605pv.widget;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hf.hp605pv.R;

public class LogViewHolder extends RecyclerView.ViewHolder {
    public TextView info;
    public LogViewHolder(@NonNull View itemView) {
        super(itemView);
        info = itemView.findViewById(R.id.info);
    }
}
