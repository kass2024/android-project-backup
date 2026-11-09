package com.qonics.Adapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.qonics.model.BookRecord;
import com.qonics.somanet.BorrowActivity;
import com.qonics.somanet.MainActivity;
import com.qonics.somanet.R;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.pedant.SweetAlert.SweetAlertDialog;

public abstract class BookAdapter extends RecyclerView.Adapter<BookAdapter.MyViewHolder> {
    private List<BookRecord> requestList;
    private Context mContext;
    SharedPreferences preferences;
    RefreshCallback callback;

    public class MyViewHolder extends RecyclerView.ViewHolder {
        public TextView name,author,ac_year,from_date,to_date,book_nbr,status;
        public Button btnReturn;


        public MyViewHolder(View view) {
            super(view);

            name = view.findViewById(R.id.name);
            author = view.findViewById(R.id.book_author);
            ac_year = view.findViewById(R.id.academic_year);
            from_date = view.findViewById(R.id.dFrom);
            to_date = view.findViewById(R.id.dTo);
            book_nbr = view.findViewById(R.id.book_nbr);
            status = view.findViewById(R.id.status);
            btnReturn = view.findViewById(R.id.btnStatus);

        }
    }


    public BookAdapter(List<BookRecord> requestList, Context context,RefreshCallback callback) {
        this.requestList = requestList;
        this.callback = callback;
        mContext = context;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.library_data_item, parent, false);

        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        BookRecord book = requestList.get(position);
        holder.name.setText(book.getTitle());
        holder.ac_year.setText(Integer.toString(book.getAcademic_year()));
        holder.from_date.setText(book.getBorrow_date());
        holder.to_date.setText(book.getReturn_due_date());
        holder.book_nbr.setText(book.getTermStr(mContext));
        holder.author.setText(book. getAuthor());

        if (book.getStatus() == 1){
            holder.status.setText(book.getStatusStr(mContext));
            holder.status.setTextColor(book.getStatusColor());
            holder.btnReturn.setVisibility(View.GONE);
        }else if(book.getStatus() == 0) {
            holder.btnReturn.setOnClickListener(view -> {
                        new SweetAlertDialog(mContext, SweetAlertDialog.WARNING_TYPE)
                                .setTitleText(mContext.getString(R.string.are_u_sure))
                                .setContentText(mContext.getString(R.string.want_return)+"\n"+book.getTitle())
                                .setConfirmText(mContext.getString(R.string.submit))
                                .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                                    @Override
                                    public void onClick(SweetAlertDialog sDialog) {
                                        sDialog.dismiss();
                                        SweetAlertDialog sa = new SweetAlertDialog(mContext,SweetAlertDialog.PROGRESS_TYPE);
                                        sa.setTitle(mContext.getString(R.string.please_wait));
                                        Map map = new HashMap();
                                        map.put("record_id", book.getId());
                                        HttpRequest req = new HttpRequest(mContext);
                                        String server = MainActivity.SERVER;
                                        req.progress(sa).post(server + "/save_return_book", map, new Callback() {
                                            @Override
                                            public void HttpCallback(JSONObject object, int status_code, String status) {
                                                if (object == null) {
                                                    Toast.makeText(mContext,mContext.getString(R.string.lbl_error)+status,Toast.LENGTH_LONG).show();
                                                } else {
                                                    try {
                                                        if (object.has("error")) {
                                                            Toast.makeText(mContext,mContext.getString(R.string.oops)+object.getString("error"),Toast.LENGTH_LONG).show();
                                                        }else if (object.has("success")) {
                                                            Toast.makeText(mContext, mContext.getString(R.string.book_returned), Toast.LENGTH_SHORT).show();
                                                            callback.refreshList();
                                                        } else {
                                                            Toast.makeText(mContext,mContext.getString(R.string.oops)+object.getString("message"),Toast.LENGTH_LONG).show();
                                                        }
                                                    } catch (JSONException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }
                                        });

                                    }
                                })
                                .setCancelButton(mContext.getString(R.string.cancel), new SweetAlertDialog.OnSweetClickListener() {
                                    @Override
                                    public void onClick(SweetAlertDialog sDialog) {
                                        sDialog.dismissWithAnimation();
                                    }
                                })
                                .show();

            });
            holder.btnReturn.setVisibility(View.VISIBLE);
            holder.status.setVisibility(View.GONE);

        }


    }

    @Override
    public int getItemCount() {
        return requestList.size();
    }
    public interface RefreshCallback {
        // Declaration of the template function for the interface
        public void refreshList();
    }

}
