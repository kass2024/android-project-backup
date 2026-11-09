package com.qonics.Adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.qonics.dbhelper.DatabaseHandler;
import com.qonics.model.OfflineMarksModel;
import com.qonics.somanet.R;

import java.util.ArrayList;
import java.util.List;

public class OfflineMarkAdapter extends RecyclerView.Adapter<OfflineMarkAdapter.MyViewHolder>
        implements Filterable {

    private Context context;
    private List<OfflineMarksModel> client;
    private List<OfflineMarksModel> clientListFiltered;
    public static  boolean isFiltered = false;
    DatabaseHandler db_helper;

    public class MyViewHolder extends RecyclerView.ViewHolder {

        public TextView name,phone, mark;
        public LinearLayout lnl_row_client,lnl_call;

        public MyViewHolder(View view) {
            super(view);
            db_helper = new DatabaseHandler(context);
            name = view.findViewById(R.id.txt_client_name);
            phone = view.findViewById(R.id.txt_client_phone);
            mark = view.findViewById(R.id.txt_student_marks);
            lnl_row_client = view.findViewById(R.id.lnl_row_client);

        }
    }

    public OfflineMarkAdapter(Context context, List<OfflineMarksModel> client) {
        this.context = context;
        this.client = client;
        this.clientListFiltered = client;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_item_offline_mark, parent, false);

        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, final int position) {
        final OfflineMarksModel marks = clientListFiltered.get(position);
        holder.name.setText(marks.getStudentName());
        holder.phone.setText(marks.getRegno());
        holder.mark.setText( ""+marks.getMarks());

    }

    @Override
    public int getItemCount() {
        return clientListFiltered.size();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                String charString = charSequence.toString();
                if (charString.isEmpty()) {
                    clientListFiltered = client;
                } else {
                    List<OfflineMarksModel> filteredList = new ArrayList<>();
                    for (OfflineMarksModel row : client) {

                        // name match condition. this might differ depending on your requirement
                        // here we are looking for name or phone number match
                        if (row.getExamDate().toLowerCase().contains(charString.toLowerCase())) {
                            filteredList.add(row);
                        }
                    }
                    clientListFiltered = filteredList;
                }

                FilterResults filterResults = new FilterResults();
                filterResults.values = clientListFiltered;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                clientListFiltered = (ArrayList<OfflineMarksModel>) filterResults.values;
                isFiltered = true;
                notifyDataSetChanged();
            }
        };
    }

    public interface OfflineMarkAdapterListener {
        void onClientSelected(OfflineMarksModel mark);
    }
}