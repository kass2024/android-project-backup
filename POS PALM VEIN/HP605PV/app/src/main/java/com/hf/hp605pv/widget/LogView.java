package com.hf.hp605pv.widget;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hf.hp605pv.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class LogView extends RecyclerView {

    boolean showTime = false;

    private static final int MAX_LOG_ITEM_COUNT = 50;

    public enum LOG_TYPE {INFO,ERROR,NOTIFICATION}

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd#HH:mm:ss");

    ArrayList<LogInfo> logList= new ArrayList<>();

    public static class LogInfo{
        public String info="";
        public LOG_TYPE logType = LOG_TYPE.INFO;
        public LogInfo(String info,LOG_TYPE logType){
            this.info = info;
            this.logType = logType;
        }
    }

    public void showBroadcast(String infoString,LOG_TYPE logType){
        logList.add(0,new LogInfo(infoString,logType));
        logList.remove(logList.size()-1);
        getAdapter().notifyDataSetChanged();
    }

    private void append(String infoString,LOG_TYPE logType){
        logList.add(0,new LogInfo((showTime?sdf.format(new Date())+": ":"")+infoString,logType));
        logList.remove(logList.size()-1);
        getAdapter().notifyDataSetChanged();
//        sendBroadcast(infoString,logType);
    }

    public void append(String infoString){
        append(infoString,LOG_TYPE.INFO);
    }

    public void error(String infoString){
        append(infoString,LOG_TYPE.ERROR);
    }

    public void notification(String infoString){
        append(infoString,LOG_TYPE.NOTIFICATION);
    }

    public LogView(@NonNull Context context) {
        super(context);
        init();
    }

    public LogView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LogView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init(){
        setBackgroundResource(R.drawable.selector_log_view);
        logList.clear();
        for(int i = 0;i<MAX_LOG_ITEM_COUNT;i++){
            logList.add(new LogInfo("",LOG_TYPE.INFO));
        }

        setLayoutManager(new LinearLayoutManager(getContext()));
        setAdapter(new Adapter<LogViewHolder>() {
            @NonNull
            @Override
            public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new LogViewHolder(LayoutInflater.from(getContext()).inflate(R.layout.item_log_view,parent,false));
            }

            @Override
            public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
                holder.info.setText(logList.get(position).info);
                switch (logList.get(position).logType){
                    case INFO:{
                        holder.info.setTextColor(Color.BLACK);
                        break;
                    }
                    case ERROR:{
                        holder.info.setTextColor(Color.RED);
                        break;
                    }
                    case NOTIFICATION:{
                        holder.info.setTextColor(Color.BLUE);
                        break;
                    }
                }
            }

            @Override
            public int getItemCount() {
                return MAX_LOG_ITEM_COUNT;
            }
        });
    }

    public void setShowTime(boolean showTime){
        this.showTime = showTime;
    }
}
