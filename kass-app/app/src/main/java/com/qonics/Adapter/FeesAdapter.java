package com.qonics.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.qonics.model.FeeCart;
import com.qonics.somanet.R;

import java.util.List;

public class FeesAdapter extends BaseAdapter {
    private List<FeeCart> mProductList;
    private LayoutInflater mInflater;
    private boolean mShowCheckbox;

    public FeesAdapter(List<FeeCart> list, LayoutInflater inflater, boolean showCheckbox) {
        mProductList = list;
        mInflater = inflater;
        mShowCheckbox = showCheckbox;
    }

    @Override
    public int getCount() {
        return mProductList.size();
    }

    @Override
    public Object getItem(int position) {
        return mProductList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ViewItem item;

        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.fee_items,
                    null);
            item = new ViewItem();



            item.feeTitle = (TextView) convertView.findViewById(R.id.IdfeeTitle);
            item.feeAmount= (TextView) convertView.findViewById(R.id.IdfeeAmount);

            item.removeImg = (ImageView) convertView.findViewById(R.id.CheckBoxSelected);

            convertView.setTag(item);
        } else {
            item = (ViewItem) convertView.getTag();
        }

        FeeCart curProduct = mProductList.get(position);

        item.feeTitle.setText(""+curProduct.getFeeTitle());
        item.feeAmount.setText(""+curProduct.getPaidAmount());


       

        return convertView;
    }




    private class ViewItem {
        TextView feeTitle,feeAmount;
        ImageView removeImg;
    }
}
