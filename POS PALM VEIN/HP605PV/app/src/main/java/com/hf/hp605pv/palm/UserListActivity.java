package com.hf.hp605pv.palm;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hf.hp605pv.BaseActivity;
import com.hf.hp605pv.R;
import com.hf.hp605pv.databinding.ActivityUserListBinding;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.interfaces.OnConfirmListener;

import java.util.ArrayList;
import java.util.List;

public class UserListActivity extends BaseActivity {

    ActivityUserListBinding userListBinding;
    List<User> userList = new ArrayList<>();

    @Override
    public void onBaseCreate(Bundle bundle) {
        userListBinding = ActivityUserListBinding.inflate(getLayoutInflater());
        setContentView(userListBinding.getRoot());

        userListBinding.actionbar.backTitleTextStyle(getString(R.string.user_list), getString(R.string.clear), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new XPopup.Builder(UserListActivity.this).asConfirm(getString(R.string.clear), getString(R.string.clear)+" "+getString(R.string.user_list)+"?", new OnConfirmListener() {
                    @Override
                    public void onConfirm() {
                        getUserDao().deleteAll();
                        Toast.makeText(UserListActivity.this,getString(R.string.clear)+" "+getString(R.string.user_list)+" "+ getString(R.string.success),Toast.LENGTH_SHORT).show();
                        load();
                    }
                }).show();
            }
        });

        userListBinding.recycler.setLayoutManager(new LinearLayoutManager(this));
        userListBinding.recycler.setAdapter(new RecyclerView.Adapter<UserHolder>() {
            @NonNull
            @Override
            public UserHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new UserHolder(getLayoutInflater().inflate(R.layout.holder_user, parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull UserHolder holder, int position) {
                holder.name.setText(userList.get(position).name);
                holder.feature.setText("feature:"+userList.get(position).feature);
                holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        new XPopup.Builder(UserListActivity.this).asConfirm(getString(R.string.delete), getString(R.string.delete_user) +" "+ userList.get(position).name + "?", new OnConfirmListener() {
                            @Override
                            public void onConfirm() {
                                getUserDao().delete(userList.get(position));
                                Toast.makeText(UserListActivity.this,getString(R.string.delete_user)  +" "+ userList.get(position).name+" "+ getString(R.string.success),Toast.LENGTH_SHORT).show();
                                load();
                            }
                        }).show();
                        return true;
                    }
                });
            }

            @Override
            public int getItemCount() {
                return userList.size();
            }
        });
        load();
    }

    private void load() {
        userList.clear();
        userList.addAll(getUserDao().loadAll());
        userListBinding.recycler.getAdapter().notifyDataSetChanged();
    }
}
