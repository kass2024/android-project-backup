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
import com.qonics.model.OfflineCourseModel;
import com.qonics.somanet.R;

import java.util.ArrayList;
import java.util.List;

public class CountOfflineMarkAdapter extends RecyclerView.Adapter<CountOfflineMarkAdapter.MyViewHolder>
        implements Filterable {

    private Context context;
    private List<OfflineCourseModel> client;
    private List<OfflineCourseModel> clientListFiltered;
    private OfflineCourseAdapterListener listener;
    public static  boolean isFiltered = false;
    DatabaseHandler db_helper;

    public class MyViewHolder extends RecyclerView.ViewHolder {

        public TextView course,cat, total;
        public LinearLayout lnl_row_client,lnl_call;

        public MyViewHolder(View view) {
            super(view);
            db_helper = new DatabaseHandler(context);
            course = view.findViewById(R.id.txt_client_name);
            cat = view.findViewById(R.id.txt_client_phone);
            total = view.findViewById(R.id.txt_student_marks);
            lnl_row_client = view.findViewById(R.id.lnl_row_client);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // send selected contact in callback
                    listener.onOfflineCourseSelected(clientListFiltered.get(getAdapterPosition()));
                }
            });

        }
    }

    public CountOfflineMarkAdapter(Context context, List<OfflineCourseModel> client, OfflineCourseAdapterListener listener) {
        this.context = context;
        this.client = client;
        this.listener = listener;
        this.clientListFiltered = client;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_item_offline_course, parent, false);

        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, final int position) {
        final OfflineCourseModel marks = clientListFiltered.get(position);
        holder.course.setText(marks.getName());
        holder.cat.setText(marks.getCat());
        holder.total.setText( ""+marks.getMarks());

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
                    List<OfflineCourseModel> filteredList = new ArrayList<>();
                    for (OfflineCourseModel row : client) {

                        // name match condition. this might differ depending on your requirement
                        // here we are looking for name or phone number match
                        if (row.getName().toLowerCase().contains(charString.toLowerCase())) {
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
                clientListFiltered = (ArrayList<OfflineCourseModel>) filterResults.values;
                isFiltered = true;
                notifyDataSetChanged();
            }
        };
    }

    public interface OfflineCourseAdapterListener {
        void onOfflineCourseSelected(OfflineCourseModel mark);
    }
}