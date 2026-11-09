package com.qonics.somanet;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;

import org.json.JSONObject;

public class StockMenuActivity extends AppCompatActivity {
    private TextView txtStore;
    private TextView txtIn;
    private TextView txtOut;
    private TextView txtLow;
    private TextView txtCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (com.qonics.utils.MenuClearanceHelper.blockUnless(this, true)) return;
        setContentView(R.layout.activity_stock_menu);
        txtStore = findViewById(R.id.txt_store);
        txtIn = findViewById(R.id.txt_in);
        txtOut = findViewById(R.id.txt_out);
        txtLow = findViewById(R.id.txt_low);
        txtCount = findViewById(R.id.txt_count);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.card_entry).setOnClickListener(v -> open(v, StockEntryActivity.class));
        findViewById(R.id.card_issue).setOnClickListener(v -> open(v, StockIssueActivity.class));
        findViewById(R.id.card_summary).setOnClickListener(v -> open(v, StockSummaryActivity.class));
        UiMotion.rise(findViewById(R.id.hero), 0);
        UiMotion.rise(findViewById(R.id.stats), 80);
        UiMotion.rise(findViewById(R.id.card_entry), 140);
        UiMotion.rise(findViewById(R.id.card_issue), 200);
        UiMotion.rise(findViewById(R.id.card_summary), 260);
    }

    private void open(android.view.View card, Class<?> screen) {
        UiMotion.pop(card);
        card.postDelayed(() -> {
            if (!isFinishing()) startActivity(new Intent(this, screen));
        }, 140);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int staffId = StockFormat.staffId(this);
        if (staffId < 1) {
            Toast.makeText(this, R.string.stock_sign_in, Toast.LENGTH_LONG).show();
            return;
        }
        new HttpRequest(this).get(MainActivity.SERVER + "kass/stock/" + staffId, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (isFinishing()) return;
                String error = StockFormat.errorOf(object);
                if (object == null || error != null) {
                    txtStore.setText(error != null ? error : status);
                    return;
                }
                String store = object.optString("store", "");
                txtStore.setText(store.isEmpty() ? getString(R.string.stock_no_store) : store);
                txtIn.setText(StockFormat.qty(object.optDouble("receivedToday")));
                txtOut.setText(StockFormat.qty(object.optDouble("issuedToday")));
                int low = object.optInt("low");
                txtLow.setText(String.valueOf(low));
                txtLow.setTextColor(low > 0 ? 0xFFB42318 : 0xFF0F172A);
                txtCount.setText(getString(R.string.stock_item_count, object.optInt("count")));
            }
        });
    }
}
