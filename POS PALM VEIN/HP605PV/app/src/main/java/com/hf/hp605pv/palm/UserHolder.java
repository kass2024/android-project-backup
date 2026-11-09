package com.hf.hp605pv.palm;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hf.hp605pv.R;

public class UserHolder extends RecyclerView.ViewHolder {
    public TextView name;
    public TextView feature;
    public UserHolder(@NonNull View itemView) {
        super(itemView);
        name = itemView.findViewById(R.id.name);
        feature = itemView.findViewById(R.id.feature);
    }
}
