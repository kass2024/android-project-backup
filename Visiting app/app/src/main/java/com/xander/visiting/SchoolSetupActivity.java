package com.xander.visiting;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.xander.visiting.R.id;
import com.xander.visiting.R.layout;
import com.xander.visiting.R.string;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

public class SchoolSetupActivity extends AppCompatActivity {
   private EditText inputAcronym;
   private EditText inputServer;
   private TextView setupError;
   private Button btnContinue;
   private SoftKeyboard softKeyboard;

   protected void attachBaseContext(Context newBase) {
      super.attachBaseContext(LocaleHelper.wrap(newBase));
   }

   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      if (Prefs.hasSchool(this) && !this.getIntent().getBooleanExtra("force_setup", false)) {
         this.startActivity(new Intent(this, GateActivity.class));
         this.finish();
      } else {
         this.setContentView(layout.activity_school_setup);
         WifiKeeper.keep(this);
         this.inputAcronym = (EditText)this.findViewById(id.inputAcronym);
         this.inputServer = (EditText)this.findViewById(id.inputServer);
         this.setupError = (TextView)this.findViewById(id.setupError);
         this.btnContinue = (Button)this.findViewById(id.btnContinue);
         this.softKeyboard = (SoftKeyboard)this.findViewById(id.softKeyboard);
         this.bindSoftFields();
         this.inputServer.setText(Prefs.serverUrl(this));
         if (!Prefs.acronym(this).isEmpty()) {
            this.inputAcronym.setText(Prefs.acronym(this));
         }

         TextView langSwitch = (TextView)this.findViewById(id.langSwitch);
         langSwitch.setText("rw".equals(Prefs.lang(this)) ? this.getString(string.english) : this.getString(string.kinyarwanda));
         langSwitch.setOnClickListener((v) -> {
            Prefs.setLang(this, Prefs.nextLang(this));
            this.recreate();
         });
         this.btnContinue.setOnClickListener((v) -> this.saveSchool());
         Prefs.setKioskUnlocked(this, false);
         Kiosk.bindExitLogo(this, this.findViewById(id.setupLogo));
         this.inputAcronym.requestFocus();
         this.softKeyboard.setTarget(this.inputAcronym);
      }
   }

   protected void onResume() {
      super.onResume();
      WifiKeeper.keep(this);
      if (this.inputAcronym != null && !this.inputAcronym.hasFocus() && (this.inputServer == null || !this.inputServer.hasFocus())) {
         this.inputAcronym.requestFocus();
      }

      EditText focused = this.inputServer != null && this.inputServer.hasFocus() ? this.inputServer : this.inputAcronym;
      if (this.softKeyboard != null && focused != null) {
         this.softKeyboard.setTarget(focused);
      }

   }

   protected void onUserLeaveHint() {
      super.onUserLeaveHint();
   }

   public boolean dispatchKeyEvent(KeyEvent event) {
      if (event != null && event.getKeyCode() == 4 && Prefs.kioskLocked(this)) {
         return true;
      } else {
         return SoftKeyboard.isPhysicalKeyboard(event) ? true : super.dispatchKeyEvent(event);
      }
   }

   private void bindSoftFields() {
      SoftKeyboard.prepareField(this.inputAcronym);
      SoftKeyboard.prepareField(this.inputServer);
      this.softKeyboard.setHost((current) -> {
         if (current == this.inputAcronym) {
            this.inputServer.requestFocus();
            this.softKeyboard.setTarget(this.inputServer);
         } else {
            this.saveSchool();
         }

      });
      View.OnFocusChangeListener focus = (v, hasFocus) -> {
         if (hasFocus) {
            this.softKeyboard.setTarget((EditText)v);
            SoftKeyboard.hideSystemIme(v);
         }

      };
      this.inputAcronym.setOnFocusChangeListener(focus);
      this.inputServer.setOnFocusChangeListener(focus);
      this.inputAcronym.setOnClickListener((v) -> {
         v.requestFocus();
         this.softKeyboard.setTarget(this.inputAcronym);
      });
      this.inputServer.setOnClickListener((v) -> {
         v.requestFocus();
         this.softKeyboard.setTarget(this.inputServer);
      });
   }

   private void saveSchool() {
      String acronym = this.inputAcronym.getText().toString().trim().toUpperCase();
      String server = this.inputServer.getText().toString().trim();
      if (acronym.isEmpty()) {
         this.showError(this.getString(string.school_acronym));
      } else {
         Prefs.setServerUrl(this, server);
         this.btnContinue.setEnabled(false);
         this.setupError.setVisibility(8);
         this.btnContinue.setText(string.connecting_wifi);
         WifiKeeper.keep(this);
         WifiKeeper.whenOnline(this, 12000L, () -> this.openSchool(acronym), () -> this.openSchool(acronym));
      }
   }

   private void openSchool(String acronym) {
      this.btnContinue.setText(string.checking);
      Map<String, String> fields = new HashMap();
      fields.put("acronym", acronym);
      ApiClient.post(this, "gate_open_school", fields, (json, error) -> {
         if (json == null && !Net.isOnline(this)) {
            WifiKeeper.keep(this);
            WifiKeeper.whenOnline(this, 8000L, () -> this.retryOpen(acronym), () -> this.failNetwork());
         } else {
            this.finishOpen(json);
         }
      });
   }

   private void retryOpen(String acronym) {
      Map<String, String> fields = new HashMap();
      fields.put("acronym", acronym);
      ApiClient.post(this, "gate_open_school", fields, (json, error) -> this.finishOpen(json));
   }

   private void finishOpen(JSONObject json) {
      this.btnContinue.setEnabled(true);
      this.btnContinue.setText(string.continue_btn);
      if (json == null) {
         this.failNetwork();
      } else if (json.optInt("success", 0) != 1) {
         this.showError(json.optString("message", this.getString(string.school_not_found)));
      } else {
         JSONObject school = json.optJSONObject("school");
         if (school == null) {
            this.showError(this.getString(string.school_not_found));
         } else {
            Prefs.saveSchool(this, school.optInt("id"), school.optString("name"), school.optString("acronym"), school.optString("logo"));
            (new LocalStore(this)).saveSnapshot(json);
            this.startActivity(new Intent(this, GateActivity.class));
            this.finish();
         }
      }
   }

   private void failNetwork() {
      this.btnContinue.setEnabled(true);
      this.btnContinue.setText(string.continue_btn);
      this.showError(this.getString(string.network_error));
   }

   private void showError(String msg) {
      this.setupError.setText(msg);
      this.setupError.setVisibility(0);
   }
}
