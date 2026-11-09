package com.xander.visiting;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.view.ViewOutlineProvider;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build.VERSION;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.xander.visiting.R.drawable;
import com.xander.visiting.R.id;
import com.xander.visiting.R.layout;
import com.xander.visiting.R.string;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class GateActivity extends AppCompatActivity {
   private static final int MODE_SCAN = 0;
   private static final int MODE_FORM = 1;
   private static final int MODE_ASSIGN = 2;
   private static final int MODE_RESULT = 3;
   private final Handler handler = new Handler(Looper.getMainLooper());
   private final SimpleDateFormat clockFmt;
   private LocalStore store;
   private RfidWedge wedge;
   private AnimatorSet blink;
   private ConnectivityManager.NetworkCallback netCb;
   private int mode;
   private boolean busy;
   private boolean syncing;
   private String draftNames;
   private String draftId;
   private String draftPhone;
   private String draftReason;
   private String draftMaterials;
   private ImageView schoolLogo;
   private TextView schoolTitle;
   private TextView schoolAcronym;
   private TextView syncLabel;
   private TextView clockLabel;
   private ImageView scanIcon;
   private View leaveLight;
   private ImageView resultPhoto;
   private TextView scanTitle;
   private TextView scanHint;
   private TextView scanStatus;
   private Button btnNewVisitor;
   private LinearLayout scanPane;
   private LinearLayout formPane;
   private LinearLayout listPane;
   private LinearLayout insideList;
   private TextView statInside;
   private EditText rfidWedge;
   private EditText inputNames;
   private EditText inputIdNumber;
   private EditText inputPhone;
   private EditText inputMaterials;
   private Spinner inputReason;
   private ArrayAdapter<String> reasonAdapter;
   private String selectedReason;
   private TextView formError;
   private Button btnSave;
   private View readyDot;
   private LinearLayout staffModal;
   private LinearLayout outReasonModal;
   private TextView outReasonName;
   private TextView outReasonPost;
   private TextView outReasonTitle;
   private ImageView outReasonPhoto;
   private Button outReasonSave;
   private EditText outReasonInput;
   private TextView outReasonError;
   private String pendingOutUid;
   private JSONObject pendingOutPerson;
   private boolean confirmingReturn;
   private ImageView modalPhoto;
   private TextView modalName;
   private TextView modalPost;
   private TextView modalShift;
   private TextView modalStatus;
   private TextView modalHint;
   private SoftKeyboard softKeyboard;
   private EditText[] formFields;
   private LinearLayout successModal;
   private TextView successTitle;
   private TextView successName;
   private TextView successHint;
   private VoiceAnnouncer voice;
   private String lastUid;
   private long lastUidAt;
   private int staffScanGen;
   private final Runnable resetScan;
   private final Runnable clockTicker;
   private final Runnable syncTicker;

   public GateActivity() {
      this.clockFmt = new SimpleDateFormat("hh:mm:ss a", Locale.US);
      this.mode = 0;
      this.draftNames = "";
      this.draftId = "";
      this.draftPhone = "";
      this.draftReason = "";
      this.draftMaterials = "";
      this.selectedReason = "";
      this.lastUid = "";
      this.resetScan = this::showScan;
      this.clockTicker = new Runnable() {
         public void run() {
            GateActivity.this.clockLabel.setText(GateActivity.this.clockFmt.format(new Date()));
            GateActivity.this.handler.postDelayed(this, 1000L);
         }
      };
      this.syncTicker = new Runnable() {
         public void run() {
            if (GateActivity.this.store.pendingCount() > 0) {
               GateActivity.this.flushQueue();
            } else {
               GateActivity.this.pullSnapshot();
            }

            GateActivity.this.handler.postDelayed(this, 25000L);
         }
      };
   }

   protected void attachBaseContext(Context newBase) {
      super.attachBaseContext(LocaleHelper.wrap(newBase));
   }

   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      if (!Prefs.hasSchool(this)) {
         this.startActivity(new Intent(this, SchoolSetupActivity.class));
         this.finish();
      } else {
         this.getWindow().addFlags(128);
         this.setContentView(layout.activity_gate);
         this.store = new LocalStore(this);
         this.schoolLogo = (ImageView)this.findViewById(id.schoolLogo);
         this.schoolTitle = (TextView)this.findViewById(id.schoolTitle);
         this.schoolAcronym = (TextView)this.findViewById(id.schoolAcronym);
         this.syncLabel = (TextView)this.findViewById(id.syncLabel);
         this.clockLabel = (TextView)this.findViewById(id.clockLabel);
         this.scanIcon = (ImageView)this.findViewById(id.scanIcon);
         this.leaveLight = this.findViewById(id.leaveLight);
         this.resultPhoto = (ImageView)this.findViewById(id.resultPhoto);
         this.scanTitle = (TextView)this.findViewById(id.scanTitle);
         this.scanHint = (TextView)this.findViewById(id.scanHint);
         this.scanStatus = (TextView)this.findViewById(id.scanStatus);
         this.btnNewVisitor = (Button)this.findViewById(id.btnNewVisitor);
         this.scanPane = (LinearLayout)this.findViewById(id.scanPane);
         this.formPane = (LinearLayout)this.findViewById(id.formPane);
         this.listPane = (LinearLayout)this.findViewById(id.listPane);
         this.insideList = (LinearLayout)this.findViewById(id.insideList);
         this.statInside = (TextView)this.findViewById(id.statInside);
         this.rfidWedge = (EditText)this.findViewById(id.rfidWedge);
         this.inputNames = (EditText)this.findViewById(id.inputNames);
         this.inputIdNumber = (EditText)this.findViewById(id.inputIdNumber);
         this.inputPhone = (EditText)this.findViewById(id.inputPhone);
         this.inputMaterials = (EditText)this.findViewById(id.inputMaterials);
         this.inputReason = (Spinner)this.findViewById(id.inputReason);
         this.bindReasonChoices();
         this.formError = (TextView)this.findViewById(id.formError);
         this.btnSave = (Button)this.findViewById(id.btnSave);
         this.readyDot = this.findViewById(id.readyDot);
         this.staffModal = (LinearLayout)this.findViewById(id.staffModal);
         this.outReasonModal = (LinearLayout)this.findViewById(id.outReasonModal);
         this.outReasonName = (TextView)this.findViewById(id.outReasonName);
         this.outReasonPost = (TextView)this.findViewById(id.outReasonPost);
         this.outReasonTitle = (TextView)this.findViewById(id.outReasonTitle);
         this.outReasonPhoto = (ImageView)this.findViewById(id.outReasonPhoto);
         this.outReasonInput = (EditText)this.findViewById(id.outReasonInput);
         this.outReasonError = (TextView)this.findViewById(id.outReasonError);
         this.outReasonSave = (Button)this.findViewById(id.outReasonSave);
         this.roundPhoto(this.resultPhoto);
         this.roundPhoto(this.outReasonPhoto);
         this.findViewById(id.outReasonCancel).setOnClickListener((v) -> this.hideOutReason());
         this.outReasonSave.setOnClickListener((v) -> this.submitOutReason());
         SoftKeyboard.prepareField(this.outReasonInput);
         this.outReasonInput.setOnClickListener((v) -> {
            this.outReasonInput.requestFocus();
            this.softKeyboard.setTarget(this.outReasonInput);
            this.showSoftKeyboard();
            SoftKeyboard.hideSystemIme(v);
         });
         this.modalPhoto = (ImageView)this.findViewById(id.modalPhoto);
         this.roundPhoto(this.modalPhoto);
         this.modalName = (TextView)this.findViewById(id.modalName);
         this.modalPost = (TextView)this.findViewById(id.modalPost);
         this.modalShift = (TextView)this.findViewById(id.modalShift);
         this.modalStatus = (TextView)this.findViewById(id.modalStatus);
         this.modalHint = (TextView)this.findViewById(id.modalHint);
         this.softKeyboard = (SoftKeyboard)this.findViewById(id.softKeyboard);
         this.successModal = (LinearLayout)this.findViewById(id.successModal);
         this.successTitle = (TextView)this.findViewById(id.successTitle);
         this.successName = (TextView)this.findViewById(id.successName);
         this.successHint = (TextView)this.findViewById(id.successHint);
         this.staffModal.setOnClickListener((v) -> {
            this.handler.removeCallbacks(this.resetScan);
            this.showScan();
         });
         this.successModal.setOnClickListener((v) -> this.hideSuccessModal());
         this.formFields = new EditText[]{this.inputNames, this.inputIdNumber, this.inputPhone, this.inputMaterials};
         this.bindSoftFields();
         this.voice = new VoiceAnnouncer();
         this.voice.start(this);
         Prefs.setKioskUnlocked(this, false);
         this.schoolTitle.setText(Prefs.schoolName(this));
         this.schoolAcronym.setText(Prefs.acronym(this));
         ImageLoader.load(this.schoolLogo, Prefs.logoUrl(this), drawable.ic_launcher_legacy);
         Kiosk.bindExitLogo(this, this.schoolLogo);
         Button btnLang = (Button)this.findViewById(id.btnLang);
         btnLang.setText("rw".equals(Prefs.lang(this)) ? this.getString(string.english) : this.getString(string.kinyarwanda));
         btnLang.setOnClickListener((v) -> {
            Prefs.setLang(this, Prefs.nextLang(this));
            this.recreate();
         });
         this.btnNewVisitor.setOnClickListener((v) -> this.showForm());
         this.findViewById(id.btnCancel).setOnClickListener((v) -> this.showScan());
         this.btnSave.setOnClickListener((v) -> this.continueToAssign());
         this.wedge = new RfidWedge(this::onCardScanned);
         this.rfidWedge.setOnEditorActionListener((v, actionId, event) -> {
            String uid = RfidWedge.clean(this.rfidWedge.getText().toString());
            this.rfidWedge.setText("");
            if (uid.length() >= 4) {
               this.onCardScanned(uid);
            }

            return true;
         });
         ObjectAnimator fade = ObjectAnimator.ofFloat(this.scanIcon, View.ALPHA, new float[]{1.0F, 0.35F, 1.0F});
         ObjectAnimator growX = ObjectAnimator.ofFloat(this.scanIcon, View.SCALE_X, new float[]{1.0F, 1.06F, 1.0F});
         ObjectAnimator growY = ObjectAnimator.ofFloat(this.scanIcon, View.SCALE_Y, new float[]{1.0F, 1.06F, 1.0F});
         ObjectAnimator pulse = ObjectAnimator.ofFloat(this.readyDot, View.ALPHA, new float[]{1.0F, 0.25F, 1.0F});
         fade.setRepeatCount(-1);
         growX.setRepeatCount(-1);
         growY.setRepeatCount(-1);
         pulse.setRepeatCount(-1);
         this.blink = new AnimatorSet();
         this.blink.playTogether(new Animator[]{fade, growX, growY, pulse});
         this.blink.setDuration(1100L);
         this.showScan();
         this.paintCachedBoard();
         this.refreshSyncLabel();
         this.pullSnapshot();
         this.handler.post(this.clockTicker);
         this.handler.postDelayed(this.syncTicker, 25000L);
         this.listenNetwork();
      }
   }

   protected void onResume() {
      super.onResume();
      WifiKeeper.keep(this);
      if (this.outReasonOpen()) {
         this.softKeyboard.setTarget(this.outReasonInput);
         this.showSoftKeyboard();
      } else {
         this.focusWedge();
      }
      this.pullSnapshot();
      if (this.mode == 0 && !this.outReasonOpen()) {
         this.startBlink();
      }

   }

   protected void onUserLeaveHint() {
      super.onUserLeaveHint();
   }

   protected void onDestroy() {
      this.handler.removeCallbacksAndMessages((Object)null);
      if (this.blink != null) {
         this.blink.cancel();
      }

      if (this.netCb != null && VERSION.SDK_INT >= 21) {
         ConnectivityManager cm = (ConnectivityManager)this.getSystemService("connectivity");

         try {
            cm.unregisterNetworkCallback(this.netCb);
         } catch (Exception var3) {
         }
      }

      if (this.voice != null) {
         this.voice.shutdown();
      }

      super.onDestroy();
   }

   public boolean dispatchKeyEvent(KeyEvent event) {
      if (event != null && event.getKeyCode() == 4) {
         return Prefs.kioskLocked(this) ? true : super.dispatchKeyEvent(event);
      } else if (this.mode == 1 || this.outReasonOpen()) {
         return SoftKeyboard.isPhysicalKeyboard(event) ? true : super.dispatchKeyEvent(event);
      } else {
         if (this.wedge != null) {
            this.wedge.onKey(event);
         }

         return true;
      }
   }

   private void listenNetwork() {
      ConnectivityManager cm = (ConnectivityManager)this.getSystemService("connectivity");
      if (cm != null && VERSION.SDK_INT >= 21) {
         this.netCb = new ConnectivityManager.NetworkCallback() {
            public void onAvailable(Network network) {
               GateActivity.this.handler.post(() -> GateActivity.this.flushQueue());
            }
         };
         cm.registerNetworkCallback((new NetworkRequest.Builder()).build(), this.netCb);
      }
   }

   private void startBlink() {
      if (this.blink != null && !this.blink.isRunning()) {
         this.blink.start();
      }

   }

   private void stopBlink() {
      if (this.blink != null) {
         this.blink.cancel();
      }

      this.scanIcon.setAlpha(1.0F);
      this.scanIcon.setScaleX(1.0F);
      this.scanIcon.setScaleY(1.0F);
      if (this.readyDot != null) {
         this.readyDot.setAlpha(1.0F);
      }

   }

   private void bindReasonChoices() {
      if (this.inputReason != null) {
         String[] reasons = new String[]{this.getString(string.reason_choose), this.getString(string.reason_director), this.getString(string.reason_headteacher), this.getString(string.reason_finance), this.getString(string.reason_general), this.getString(string.reason_other)};
         this.reasonAdapter = new ArrayAdapter(this, layout.spinner_reason, reasons);
         this.reasonAdapter.setDropDownViewResource(layout.spinner_reason_dropdown);
         this.inputReason.setAdapter(this.reasonAdapter);
         this.inputReason.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
               if (position <= 0) {
                  GateActivity.this.selectedReason = "";
               } else {
                  GateActivity.this.selectedReason = parent.getItemAtPosition(position).toString();
                  if (GateActivity.this.formError != null) {
                     GateActivity.this.formError.setVisibility(8);
                  }

               }
            }

            public void onNothingSelected(AdapterView<?> parent) {
               GateActivity.this.selectedReason = "";
            }
         });
         this.resetReasonChoice();
      }
   }

   private void resetReasonChoice() {
      this.selectedReason = "";
      if (this.inputReason != null) {
         this.inputReason.setSelection(0);
      }

   }

   private void bindSoftFields() {
      this.softKeyboard.setHost(this::focusNextFormField);

      for(EditText field : this.formFields) {
         SoftKeyboard.prepareField(field);
         field.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && this.mode == 1) {
               this.softKeyboard.setTarget((EditText)v);
               this.showSoftKeyboard();
               SoftKeyboard.hideSystemIme(v);
            }

         });
         field.setOnClickListener((v) -> {
            v.requestFocus();
            this.softKeyboard.setTarget((EditText)v);
            this.showSoftKeyboard();
            SoftKeyboard.hideSystemIme(v);
         });
      }

   }

   private void focusNextFormField(EditText current) {
      if (this.formFields != null) {
         int index = 0;

         for(int i = 0; i < this.formFields.length; ++i) {
            if (this.formFields[i] == current) {
               index = i + 1;
               break;
            }
         }

         if (index >= this.formFields.length) {
            this.continueToAssign();
         } else {
            this.formFields[index].requestFocus();
            this.softKeyboard.setTarget(this.formFields[index]);
         }
      }
   }

   private void showSoftKeyboard() {
      if (this.softKeyboard != null) {
         this.softKeyboard.setVisibility(0);
      }

   }

   private void hideSoftKeyboard() {
      if (this.softKeyboard != null) {
         this.softKeyboard.setVisibility(8);
      }

      SoftKeyboard.hideSystemIme(this.scanPane);
   }

   private void focusWedge() {
      if (this.mode == 1) {
         this.inputNames.requestFocus();
         this.softKeyboard.setTarget(this.inputNames);
         this.showSoftKeyboard();
         SoftKeyboard.hideSystemIme(this.inputNames);
      } else {
         this.hideSoftKeyboard();
         this.clearFormFocus();
         if (this.scanPane != null) {
            this.scanPane.setFocusable(true);
            this.scanPane.setFocusableInTouchMode(true);
            this.scanPane.requestFocus();
         }

         this.rfidWedge.clearFocus();
         if (this.wedge != null) {
            this.wedge.reset();
         }

      }
   }

   private void clearFormFocus() {
      if (this.formFields != null) {
         for(EditText field : this.formFields) {
            field.clearFocus();
            field.setFocusable(false);
            field.setFocusableInTouchMode(false);
         }

         View current = this.getCurrentFocus();
         if (current != null) {
            current.clearFocus();
         }

      }
   }

   private void showScan() {
      this.mode = 0;
      this.busy = false;
      this.hideSoftKeyboard();
      this.hideStaffModal();
      if (this.outReasonModal != null) {
         this.outReasonModal.setVisibility(8);
      }
      this.hideSuccessModal();
      this.draftNames = this.draftId = this.draftPhone = this.draftReason = this.draftMaterials = "";
      this.formPane.setVisibility(8);
      this.listPane.setVisibility(0);
      this.btnNewVisitor.setVisibility(0);
      this.btnSave.setVisibility(0);
      this.setFormEnabled(true);
      this.resultPhoto.setVisibility(8);
      this.scanIcon.setVisibility(0);
      if (this.leaveLight != null) {
         this.leaveLight.setVisibility(8);
      }
      this.scanPane.setBackgroundResource(drawable.bg_scan_zone);
      this.scanTitle.setTextColor(-3562969);
      this.scanTitle.setTextSize(42.0F);
      this.scanHint.setTextColor(-3418655);
      this.scanStatus.setTextColor(-3562969);
      this.scanTitle.setText(this.getString(string.scan_title));
      this.scanHint.setText("");
      this.scanHint.setVisibility(8);
      this.scanStatus.setText(this.getString(string.scan_ready));
      this.startBlink();
      this.focusWedge();
      this.paintCachedBoard();
   }

   private void showForm() {
      this.mode = 1;
      this.stopBlink();
      this.formError.setVisibility(8);
      this.inputNames.setText("");
      this.inputIdNumber.setText("");
      this.inputPhone.setText("");
      this.inputMaterials.setText("");
      this.resetReasonChoice();
      this.setFormEnabled(true);
      this.btnSave.setText(string.continue_assign);
      this.formPane.setVisibility(0);
      this.listPane.setVisibility(8);
      this.btnNewVisitor.setVisibility(8);
      this.scanTitle.setTextSize(28.0F);
      this.scanTitle.setText(this.getString(string.step1_title));
      this.scanHint.setText(this.getString(string.step1_hint));
      this.scanHint.setVisibility(0);
      this.scanStatus.setText("1 / 2");
      this.inputNames.requestFocus();
      this.softKeyboard.setTarget(this.inputNames);
      this.showSoftKeyboard();
      SoftKeyboard.hideSystemIme(this.inputNames);
   }

   private void continueToAssign() {
      this.draftNames = this.inputNames.getText().toString().trim();
      this.draftId = this.inputIdNumber.getText().toString().trim();
      this.draftPhone = this.inputPhone.getText().toString().trim();
      this.draftReason = this.selectedReason == null ? "" : this.selectedReason.trim();
      this.draftMaterials = this.inputMaterials.getText().toString().trim();
      if (this.draftNames.length() < 2) {
         this.showFormError(this.getString(string.required_name));
      } else if (this.draftPhone.isEmpty()) {
         this.showFormError(this.getString(string.required_phone));
      } else if (this.draftReason.isEmpty()) {
         this.showFormError(this.getString(string.required_reason));
      } else {
         this.formError.setVisibility(8);
         this.hideSoftKeyboard();
         this.mode = 2;
         this.setFormEnabled(false);
         this.btnSave.setVisibility(8);
         this.scanTitle.setTextSize(28.0F);
         this.scanTitle.setText(this.getString(string.step2_title));
         String hint = this.draftNames;
         if (!this.draftPhone.isEmpty()) {
            hint = hint + "\n" + this.getString(string.phone_number) + ": " + this.draftPhone;
         }

         if (!this.draftId.isEmpty()) {
            hint = hint + "\n" + this.getString(string.id_number) + ": " + this.draftId;
         }

         hint = hint + "\n" + this.getString(string.step2_hint);
         this.scanHint.setText(hint);
         this.scanHint.setVisibility(0);
         this.scanStatus.setText(this.getString(string.waiting_card));
         this.startBlink();
         this.focusWedge();
      }
   }

   private void onCardScanned(String uid) {
      if (this.mode != 1) {
         uid = RfidWedge.clean(uid);
         if (uid.length() >= 4) {
            long now = System.currentTimeMillis();
            if (!uid.equals(this.lastUid) || now - this.lastUidAt >= 400L) {
               this.lastUid = uid;
               this.lastUidAt = now;
               this.handler.removeCallbacks(this.resetScan);
               this.hideSuccessModal();
               this.hideStaffModal();
               if (this.mode == 2) {
                  this.assignVisitor(uid);
               } else {
                  this.resolveCard(uid);
               }
            }
         }
      }
   }

   private void resolveCard(String uid) {
      JSONObject inside = this.store.findInside(uid);
      if (inside != null) {
         this.checkout(uid, inside);
      } else {
         JSONObject owned = this.store.findCard(uid);
         if (owned != null) {
            this.applyOwnedCard(uid, owned);
         } else if (Net.isOnline(this)) {
            this.scanStatus.setText(this.getString(string.saving));
            ApiClient.post(this, "gate_lookup_card", this.fields(uid), (json, error) -> {
               if (json == null) {
                  this.showResult(false, this.getString(string.card_free_register_first), "", uid, (JSONObject)null);
               } else {
                  String kind = json.optString("kind");
                  if ("visitor_out".equals(kind)) {
                     JSONObject visit = json.optJSONObject("visit");
                     if (visit != null) {
                        this.checkout(uid, visit);
                        return;
                     }
                  }

                  if ("staff".equals(kind)) {
                     JSONObject owner = json.optJSONObject("owner");
                     if (owner == null) {
                        owner = new JSONObject();
                     }

                     try {
                        owner.put("kind", "staff");
                        owner.put("card", owner.optString("card", uid));
                     } catch (Exception var7) {
                     }

                     this.store.rememberCard(owner);
                     JSONObject cached = this.store.findCard(uid);
                     this.staffScan(uid, cached != null ? cached : owner);
                  } else if ("student".equals(kind)) {
                     this.showResult(false, this.getString(string.student_card), json.optString("message"), uid, (JSONObject)null);
                  } else if ("parent".equals(kind)) {
                     this.showResult(false, this.getString(string.parent_card_warning), json.optString("message"), uid, (JSONObject)null);
                  } else {
                     this.showResult(false, this.getString(string.card_free_register_first), "", uid, (JSONObject)null);
                  }
               }
            });
         } else {
            this.showResult(false, this.getString(string.offline_unknown), "", uid, (JSONObject)null);
         }
      }
   }

   private void applyOwnedCard(String uid, JSONObject owned) {
      String kind = owned.optString("kind");
      if ("staff".equals(kind)) {
         this.staffScan(uid, owned);
      } else if ("student".equals(kind)) {
         this.showResult(false, this.getString(string.student_card), owned.optString("name"), uid, (JSONObject)null);
      } else if ("parent".equals(kind)) {
         this.showResult(false, this.getString(string.parent_card_warning), owned.optString("name"), uid, (JSONObject)null);
      } else {
         this.showResult(false, this.getString(string.card_free_register_first), "", uid, (JSONObject)null);
      }
   }

   private void staffScan(String uid, JSONObject owner) {
      if (this.outReasonModal != null) {
         this.outReasonModal.setVisibility(8);
      }
      this.pendingOutUid = null;
      this.hideSoftKeyboard();
      if (!Net.isOnline(this)) {
         this.showLeaveLight(false, owner, uid, this.getString(string.leave_must_online));
         return;
      }
      if (this.leaveLight != null) {
         this.leaveLight.setVisibility(8);
      }
      this.scanIcon.setVisibility(0);
      this.scanStatus.setText(this.getString(string.leave_checking));
      int gen = ++this.staffScanGen;
      Map<String, String> req = this.fields(uid);
      req.put("time", String.valueOf(System.currentTimeMillis() / 1000L));
      ApiClient.post(this, "gate_staff_leave", req, (json, error) -> {
         if (gen != this.staffScanGen) {
            return;
         }
         if (json == null) {
            this.showLeaveLight(false, owner, uid, this.getString(string.leave_must_online));
            return;
         }
         String kind = json.optString("kind");
         if (!"staff".equals(kind) && json.optInt("success", 0) != 1 && json.optInt("granted", 0) != 1) {
            String message = json.optString("message");
            if (message == null || message.isEmpty()) {
               message = error != null ? error : this.getString(string.network_error);
            }
            this.showResult(false, message, owner != null ? owner.optString("name") : "", uid, owner);
            return;
         }
         JSONObject person = json.optJSONObject("person");
         if (person == null) {
            person = json.optJSONObject("staff");
         }
         if (person == null) {
            person = owner;
         }
         if (json.optInt("need_return", 0) == 1 || "return".equals(json.optString("action"))) {
            this.confirmReturnTap(uid, person);
            return;
         }
         boolean granted = json.optBoolean("granted") || json.optInt("granted", 0) == 1;
         String message = json.optString("message");
         if (this.isSecurity(person) && granted && (message == null || message.isEmpty())) {
            message = this.getString(string.leave_security_voice);
         }
         this.showLeaveLight(granted, person, uid, message);
      });
   }

   private void roundPhoto(ImageView photo) {
      if (photo == null) {
         return;
      }
      photo.setClipToOutline(true);
      photo.setOutlineProvider(new ViewOutlineProvider() {
         public void getOutline(View view, Outline outline) {
            int w = view.getWidth();
            int h = view.getHeight();
            if (w < 1 || h < 1) {
               outline.setOval(0, 0, 1, 1);
               return;
            }
            outline.setOval(0, 0, w, h);
         }
      });
   }

   private void confirmReturnTap(String uid, JSONObject person) {
      if (!Net.isOnline(this)) {
         this.showLeaveLight(false, person, uid, this.getString(string.leave_must_online));
         return;
      }
      Map<String, String> req = this.fields(uid);
      req.put("time", String.valueOf(System.currentTimeMillis() / 1000L));
      ApiClient.post(this, "gate_staff_return", req, (json, error) -> {
         if (json == null || json.optInt("granted", 0) != 1) {
            String message = json == null ? this.getString(string.leave_must_online) : json.optString("message");
            this.showLeaveLight(false, person, uid, message == null || message.isEmpty() ? this.getString(string.network_error) : message);
            return;
         }
         JSONObject back = json.optJSONObject("person");
         if (back == null) {
            back = person;
         }
         String message = json.optString("message");
         this.showLeaveLight(true, back, uid, message == null || message.isEmpty() ? this.getString(string.leave_return_voice) : message);
      });
   }

   private void showReturnPrompt(String uid, JSONObject person, String message) {
      this.confirmingReturn = true;
      this.pendingOutUid = uid;
      this.pendingOutPerson = person;
      this.mode = 3;
      this.busy = false;
      this.stopBlink();
      this.hideStaffModal();
      this.hideSuccessModal();
      this.scanIcon.setVisibility(8);
      String name = person != null ? person.optString("name") : "";
      String post = person != null ? person.optString("post") : "";
      String photo = person != null ? person.optString("photo") : "";
      this.outReasonName.setText(name);
      this.outReasonPost.setText(post);
      if (this.outReasonTitle != null) {
         this.outReasonTitle.setText(this.getString(string.leave_return_title));
      }
      if (this.outReasonSave != null) {
         this.outReasonSave.setText(this.getString(string.leave_return_send));
      }
      this.outReasonInput.setText("");
      this.outReasonInput.setHint(this.getString(string.leave_return_hint));
      this.outReasonError.setVisibility(8);
      if (message != null && message.length() > 0) {
         this.outReasonError.setText(message);
         this.outReasonError.setVisibility(0);
      }
      ImageLoader.load(this.outReasonPhoto, Prefs.photoUrl(this, photo), drawable.ic_person);
      this.outReasonModal.setVisibility(0);
      this.outReasonInput.requestFocus();
      this.softKeyboard.setTarget(this.outReasonInput);
      this.showSoftKeyboard();
      if (this.voice != null) {
         this.voice.speak(this, this.getString(string.leave_return_required), Prefs.lang(this));
      }
   }

   private void showLeaveLight(boolean approved, JSONObject person, String uid, String spoken) {
      this.mode = 3;
      this.busy = false;
      this.stopBlink();
      this.hideStaffModal();
      this.hideSuccessModal();
      if (this.outReasonModal != null) {
         this.outReasonModal.setVisibility(8);
      }
      this.hideSoftKeyboard();
      this.formPane.setVisibility(8);
      this.listPane.setVisibility(0);
      this.btnNewVisitor.setVisibility(0);
      String name = person != null ? person.optString("name") : "";
      if (name == null || name.isEmpty()) {
         name = uid == null ? "" : uid;
      }
      String post = person != null ? person.optString("post") : "";
      this.scanIcon.setVisibility(8);
      if (this.leaveLight != null) {
         this.leaveLight.setBackgroundResource(approved ? drawable.bg_light_green : drawable.bg_light_red);
         this.leaveLight.setVisibility(0);
      }
      String photo = person != null ? person.optString("photo") : "";
      this.resultPhoto.setVisibility(0);
      ImageLoader.load(this.resultPhoto, Prefs.photoUrl(this, photo), drawable.ic_person);
      this.scanPane.setBackgroundResource(drawable.bg_scan_zone);
      int titleColor = Color.parseColor(approved ? "#4ADE80" : "#FCA5A5");
      this.scanTitle.setTextSize(36.0F);
      this.scanTitle.setTextColor(titleColor);
      this.scanTitle.setText(this.getString(approved ? string.leave_approved_title : string.leave_denied_title));
      if (!approved && spoken != null && spoken.equals(this.getString(string.leave_must_online))) {
         this.scanTitle.setText(this.getString(string.leave_offline_title));
      }
      if (approved && spoken != null && spoken.startsWith("You are back")) {
         this.scanTitle.setText(this.getString(string.leave_return_welcome));
      }
      this.scanHint.setTextColor(-1);
      this.scanHint.setText(post != null && post.length() > 0 ? name + "\n" + post : name);
      this.scanHint.setVisibility(0);
      this.scanStatus.setTextColor(titleColor);
      String voiceText = this.leaveSpeech(spoken, approved);
      this.scanStatus.setText(voiceText);
      this.focusWedge();
      if (this.voice != null) {
         this.voice.speak(this, voiceText, Prefs.lang(this));
      }
      this.handler.removeCallbacks(this.resetScan);
      this.handler.postDelayed(this.resetScan, 5000L);
   }

   private String leaveSpeech(String spoken, boolean approved) {
      if (spoken != null && (spoken.equals(this.getString(string.leave_must_online)) || spoken.equals(this.getString(string.network_error)))) {
         return spoken;
      }
      if (!"rw".equals(Prefs.lang(this))) {
         if (spoken != null && spoken.length() > 0) {
            return spoken;
         }
         return this.getString(approved ? string.leave_approved_voice : string.leave_denied_voice);
      }
      String text = spoken == null ? "" : spoken;
      if (text.startsWith("You are back")) {
         return this.getString(string.leave_return_voice);
      }
      if (text.startsWith("You are already")) {
         return this.getString(string.leave_already_voice);
      }
      if (text.startsWith("This permission is finished")) {
         return this.getString(string.leave_finished_voice);
      }
      if (text.startsWith("Short permission")) {
         return this.getString(string.leave_short_voice);
      }
      if (text.startsWith("Security")) {
         return this.getString(string.leave_security_voice);
      }
      if (text.startsWith("Leave approved")) {
         return this.getString(string.leave_approved_voice);
      }
      if (text.startsWith("Leave is not approved")) {
         return this.getString(string.leave_denied_voice);
      }
      return this.getString(approved ? string.leave_approved_voice : string.leave_denied_voice);
   }

   private boolean isSecurity(JSONObject person) {
      if (person == null) {
         return false;
      }
      String post = person.optString("post").toLowerCase();
      return post.contains("security");
   }

   private void showOutReason(String uid, JSONObject person) {
      this.staffScan(uid, person);
   }

   private boolean outReasonOpen() {
      return this.outReasonModal != null && this.outReasonModal.getVisibility() == 0;
   }

   private void hideOutReason() {
      if (this.outReasonModal != null) {
         this.outReasonModal.setVisibility(8);
      }
      this.confirmingReturn = false;
      this.pendingOutUid = null;
      this.pendingOutPerson = null;
      this.focusWedge();
      this.startBlink();
   }

   private void submitOutReason() {
      if (!this.confirmingReturn) {
         JSONObject person = this.pendingOutPerson;
         String uid = this.pendingOutUid;
         this.hideOutReason();
         if (uid != null) {
            this.staffScan(uid, person);
         }
         return;
      }
      String typed = this.outReasonInput.getText() == null ? "" : this.outReasonInput.getText().toString().trim();
      if (!typed.equalsIgnoreCase("RETURN")) {
         this.outReasonError.setText(this.getString(string.leave_return_required));
         this.outReasonError.setVisibility(0);
         return;
      }
      if (!Net.isOnline(this)) {
         this.outReasonError.setText(this.getString(string.leave_must_online));
         this.outReasonError.setVisibility(0);
         return;
      }
      JSONObject person = this.pendingOutPerson;
      String uid = this.pendingOutUid;
      this.outReasonError.setVisibility(8);
      this.outReasonSave.setEnabled(false);
      Map<String, String> req = this.fields(uid);
      req.put("text", typed);
      req.put("time", String.valueOf(System.currentTimeMillis() / 1000L));
      ApiClient.post(this, "gate_staff_return", req, (json, error) -> {
         this.outReasonSave.setEnabled(true);
         if (json == null) {
            this.outReasonError.setText(this.getString(string.leave_must_online));
            this.outReasonError.setVisibility(0);
            return;
         }
         if (json.optInt("need_return", 0) == 1 || json.optInt("granted", 0) != 1) {
            String message = json.optString("message");
            this.outReasonError.setText(message == null || message.isEmpty() ? this.getString(string.leave_return_required) : message);
            this.outReasonError.setVisibility(0);
            return;
         }
         JSONObject back = json.optJSONObject("person");
         if (back == null) {
            back = person;
         }
         this.confirmingReturn = false;
         this.hideOutReason();
         String message = json.optString("message");
         this.showLeaveLight(true, back, uid, message == null || message.isEmpty() ? this.getString(string.leave_return_voice) : message);
      });
   }

   private void rememberOuting(String uid, String reason) {
      JSONObject payload = new JSONObject();
      try {
         payload.put("card", uid);
         payload.put("reason", reason);
      } catch (Exception ignored) {
      }
      this.store.enqueue("staff_out", payload, System.currentTimeMillis() / 1000L);
   }

   private void queueOuting(String uid, String reason, JSONObject person) {
      this.rememberOuting(uid, reason);
      this.showOutingSuccess(person, reason, false);
      this.flushQueue();
   }

   private void showOutingSuccess(JSONObject person, String reason, boolean notified) {
      this.hideOutReason();
      this.hideSoftKeyboard();
      String name = person != null ? person.optString("name") : "";
      if (name == null) {
         name = "";
      }
      String hint = this.getString(notified ? string.out_reason_success : string.out_reason_saved_pending, reason == null ? "" : reason);
      this.mode = 3;
      this.busy = false;
      this.stopBlink();
      this.hideStaffModal();
      if (this.successTitle != null) {
         this.successTitle.setText(this.getString(string.out_reason_done));
      }
      if (this.successName != null) {
         this.successName.setText(name);
      }
      if (this.successHint != null) {
         this.successHint.setText(hint);
      }
      if (this.successModal != null) {
         this.successModal.setVisibility(0);
      }
      this.focusWedge();
      if (this.voice != null) {
         this.voice.speak(this, this.getString(string.out_reason_voice), Prefs.lang(this));
      }
      this.handler.removeCallbacks(this.resetScan);
      this.handler.postDelayed(this.resetScan, 4500L);
   }

   private void showStaffModal(JSONObject person, String status, String message, JSONObject shift, String card, boolean already) {
      this.mode = 0;
      this.stopBlink();
      this.hideStaffModal();
      String name = person != null ? person.optString("name") : "";
      String post = person != null ? person.optString("post") : "";
      String photo = person != null ? person.optString("photo") : "";
      if (name.isEmpty()) {
         name = card;
      }

      this.modalName.setText(name);
      this.modalPost.setText(post);
      if (shift != null && shift.optString("hours").length() > 0) {
         this.modalShift.setText(shift.optString("title") + "  ·  " + shift.optString("hours"));
      } else {
         this.modalShift.setText("");
      }

      boolean out = !already && "OUT".equalsIgnoreCase(status);
      if (already) {
         this.modalStatus.setText(this.getString(string.staff_already_in));
         this.modalStatus.setBackgroundResource(drawable.bg_status_in);
         this.modalStatus.setTextColor(-15309516);
      } else {
         this.modalStatus.setText(out ? this.getString(string.staff_out) : this.getString(string.staff_in));
         this.modalStatus.setBackgroundResource(out ? drawable.bg_status_out : drawable.bg_status_in);
         this.modalStatus.setTextColor(out ? -4645860 : -15309516);
      }

      this.modalHint.setText(message != null && !message.isEmpty() ? message : card);
      ImageLoader.load(this.modalPhoto, Prefs.photoUrl(this, photo), drawable.ic_person);
      this.staffModal.setVisibility(0);
      this.paintCachedBoard();
      this.refreshSyncLabel();
      this.startBlink();
      this.focusWedge();
      this.handler.removeCallbacks(this.resetScan);
      this.handler.postDelayed(this.resetScan, already ? 1200L : 1500L);
   }

   private void hideStaffModal() {
      if (this.staffModal != null) {
         this.staffModal.setVisibility(8);
      }

   }

   private void checkout(String uid, JSONObject visit) {
      long time = System.currentTimeMillis() / 1000L;
      JSONObject payload = new JSONObject();

      try {
         payload.put("card", uid);
      } catch (Exception var7) {
      }

      this.store.enqueue("checkout", payload, time);
      this.store.forgetInside(uid);
      this.paintCachedBoard();
      this.showGateSuccess(false, visit, uid);
      this.flushQueue();
   }

   private void assignVisitor(String uid) {
      JSONObject inside = this.store.findInside(uid);
      if (inside != null) {
         this.showFormError(this.getString(string.card_already_used));
      } else {
         JSONObject owned = this.store.findCard(uid);
         if (owned != null && !"visitor_free".equals(owned.optString("kind"))) {
            this.showFormError("staff".equals(owned.optString("kind")) ? this.getString(string.staff_card_not_visitor) : this.getString(string.parent_card_warning));
         } else {
            long time = System.currentTimeMillis() / 1000L;
            JSONObject payload = new JSONObject();

            try {
               payload.put("card", uid);
               payload.put("names", this.draftNames);
               payload.put("id_number", this.draftId);
               payload.put("phone", this.draftPhone);
               payload.put("reason", this.draftReason);
               payload.put("materials", this.draftMaterials);
            } catch (Exception var10) {
            }

            JSONObject visit = new JSONObject();

            try {
               visit.put("names", this.draftNames);
               visit.put("id_number", this.draftId);
               visit.put("phone", this.draftPhone);
               visit.put("reason", this.draftReason);
               visit.put("materials", this.draftMaterials);
               visit.put("card", uid);
               visit.put("time_in_label", this.clockFmt.format(new Date()));
            } catch (Exception var9) {
            }

            JSONObject already = this.store.findInsideByVisitor(visit);
            if (already != null) {
               this.store.forgetInside(already.optString("card"));
            }

            this.store.enqueue("checkin", payload, time);
            this.store.rememberInside(visit);
            this.store.dedupeInside();
            this.paintCachedBoard();
            this.showGateSuccess(true, visit, uid);
            this.flushQueue();
         }
      }
   }

   private void showGateSuccess(boolean entering, JSONObject visit, String card) {
      this.mode = 3;
      this.busy = false;
      this.stopBlink();
      this.hideStaffModal();
      this.formPane.setVisibility(8);
      this.listPane.setVisibility(0);
      this.btnNewVisitor.setVisibility(0);
      this.btnSave.setVisibility(0);
      this.setFormEnabled(true);
      this.scanPane.setBackgroundColor(-2294553);
      String name = visit != null ? visit.optString("names") : this.draftNames;
      if (name == null || name.isEmpty()) {
         name = this.draftNames;
      }

      String title = this.getString(entering ? string.card_assigned_success : string.card_left_success);
      String ready = this.getString(entering ? string.card_assigned_ready : string.card_left_ready);
      String spoken = this.getString(entering ? string.card_assigned_voice : string.card_left_voice);
      this.scanTitle.setTextSize(34.0F);
      this.scanTitle.setTextColor(-15309516);
      this.scanTitle.setText(title);
      this.scanHint.setText(name);
      this.scanHint.setVisibility(0);
      this.scanStatus.setText(card == null ? "" : card);
      this.resultPhoto.setVisibility(8);
      this.scanIcon.setVisibility(0);
      if (this.successTitle != null) {
         this.successTitle.setText(title);
      }

      if (this.successName != null) {
         this.successName.setText(name);
      }

      if (this.successHint != null) {
         this.successHint.setText(ready);
      }

      if (this.successModal != null) {
         this.successModal.setVisibility(0);
      }

      if (entering) {
         this.draftNames = this.draftId = this.draftPhone = this.draftReason = this.draftMaterials = "";
      }

      this.paintCachedBoard();
      this.refreshSyncLabel();
      this.focusWedge();
      if (this.voice != null) {
         this.voice.speak(this, spoken, Prefs.lang(this));
      }

      this.handler.removeCallbacks(this.resetScan);
      this.handler.postDelayed(this.resetScan, 3500L);
   }

   private void hideSuccessModal() {
      if (this.successModal != null) {
         this.successModal.setVisibility(8);
      }

   }

   private void showResult(boolean ok, String title, String hint, String card, JSONObject extra) {
      this.mode = 3;
      this.hideSuccessModal();
      this.stopBlink();
      if (this.leaveLight != null) {
         this.leaveLight.setVisibility(8);
      }
      this.formPane.setVisibility(8);
      this.listPane.setVisibility(0);
      this.btnNewVisitor.setVisibility(0);
      this.scanPane.setBackgroundColor(ok ? -2294553 : -72990);
      this.scanTitle.setTextSize(28.0F);
      this.scanTitle.setText(title);
      this.scanHint.setText(hint != null && !hint.isEmpty() ? hint : title);
      this.scanHint.setVisibility(0);
      this.scanStatus.setText(card == null ? "" : card);
      String photo = extra != null ? extra.optString("photo") : "";
      if (photo != null && !photo.isEmpty()) {
         this.scanIcon.setVisibility(8);
         this.resultPhoto.setVisibility(0);
         ImageLoader.load(this.resultPhoto, Prefs.photoUrl(this, photo), drawable.ic_person);
      } else {
         this.resultPhoto.setVisibility(8);
         this.scanIcon.setVisibility(0);
      }

      this.paintCachedBoard();
      this.refreshSyncLabel();
      this.focusWedge();
      this.handler.removeCallbacks(this.resetScan);
      this.handler.postDelayed(this.resetScan, 6000L);
   }

   private void showFormError(String msg) {
      this.formError.setText(msg);
      this.formError.setVisibility(0);
      this.formPane.setVisibility(0);
      this.listPane.setVisibility(8);
   }

   private void setFormEnabled(boolean enabled) {
      EditText[] fields = new EditText[]{this.inputNames, this.inputIdNumber, this.inputPhone, this.inputMaterials};

      for(EditText field : fields) {
         if (field != null) {
            field.setEnabled(enabled);
            field.setFocusable(enabled);
            field.setFocusableInTouchMode(enabled);
            if (!enabled) {
               field.clearFocus();
            }
         }
      }

   }

   private Map<String, String> fields(String uid) {
      Map<String, String> fields = new HashMap();
      fields.put("school_id", String.valueOf(Prefs.schoolId(this)));
      fields.put("card", uid);
      return fields;
   }

   private boolean jsonHasFail(JSONObject json) {
      return json.optInt("success", 0) != 1 && !json.optBoolean("success", false);
   }

   private String visitText(JSONObject visit) {
      if (visit == null) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder(visit.optString("names"));
         if (visit.optString("id_number").length() > 0) {
            sb.append("\n").append(this.getString(string.id_number)).append(": ").append(visit.optString("id_number"));
         }

         if (visit.optString("reason").length() > 0) {
            sb.append("\n").append(visit.optString("reason"));
         }

         if (visit.optString("materials").length() > 0) {
            sb.append("\n").append(this.getString(string.materials)).append(": ").append(visit.optString("materials"));
         }

         return sb.toString();
      }
   }

   private void pullSnapshot() {
      if (!Net.isOnline(this)) {
         this.refreshSyncLabel();
      } else {
         Map<String, String> fields = new HashMap();
         fields.put("school_id", String.valueOf(Prefs.schoolId(this)));
         fields.put("acronym", Prefs.acronym(this));
         ApiClient.post(this, "gate_bootstrap", fields, (json, error) -> {
            if (json != null && json.optInt("success", 0) == 1) {
               this.applySnapshot(json);
               JSONObject school = json.optJSONObject("school");
               if (school != null) {
                  Prefs.saveSchool(this, school.optInt("id"), school.optString("name"), school.optString("acronym"), school.optString("logo"));
                  this.schoolTitle.setText(Prefs.schoolName(this));
                  this.schoolAcronym.setText(Prefs.acronym(this));
                  ImageLoader.load(this.schoolLogo, Prefs.logoUrl(this), drawable.ic_launcher_legacy);
               }
            }

            this.flushQueue();
         });
      }
   }

   private void flushQueue() {
      this.refreshSyncLabel();
      if (!this.syncing && Net.isOnline(this)) {
         JSONArray pending = this.store.pendingEvents();
         if (pending.length() != 0) {
            this.syncing = true;
            Map<String, String> fields = new HashMap();
            fields.put("school_id", String.valueOf(Prefs.schoolId(this)));
            fields.put("events", pending.toString());
            ApiClient.post(this, "gate_sync", fields, (json, error) -> {
               this.syncing = false;
               if (json != null && json.optInt("success", 0) == 1) {
                  this.applySnapshot(json);
                  this.store.clearQueue();
               }

               this.refreshSyncLabel();
            });
         }
      }
   }

   private void applySnapshot(JSONObject json) {
      if (json != null) {
         this.store.saveSnapshot(json);
         this.paintCachedBoard();
         this.refreshSyncLabel();
      }
   }

   private void paintCachedBoard() {
      if (this.statInside != null) {
         JSONArray rows = this.store.insideList();
         int inside = rows.length();
         this.statInside.setText(String.valueOf(inside));
         if (this.insideList != null) {
            this.insideList.removeAllViews();
            if (rows.length() == 0) {
               TextView empty = new TextView(this);
               empty.setText(this.getString(string.no_one_inside));
               empty.setTextSize(18.0F);
               empty.setTextColor(-10193781);
               empty.setPadding(8, 18, 8, 8);
               this.insideList.addView(empty);
            } else {
               for(int i = 0; i < rows.length(); ++i) {
                  JSONObject row = rows.optJSONObject(i);
                  if (row != null) {
                     TextView item = new TextView(this);
                     String line = row.optString("names");
                     if (row.optString("id_number").length() > 0) {
                        line = line + "  ·  " + row.optString("id_number");
                     }

                     line = line + "\n" + row.optString("reason") + "  ·  " + row.optString("time_in_label") + "  ·  " + row.optString("card");
                     item.setText(line);
                     item.setTextSize(17.0F);
                     item.setTypeface(Typeface.SANS_SERIF);
                     item.setPadding(10, 10, 10, 10);
                     item.setBackgroundColor(i % 2 == 0 ? -460036 : -1);
                     this.insideList.addView(item);
                  }
               }

            }
         }
      }
   }

   private void refreshSyncLabel() {
      int n = this.store.pendingCount();
      if (!Net.isOnline(this)) {
         this.syncLabel.setText(n > 0 ? this.getString(string.offline_pending, new Object[]{n}) : this.getString(string.offline));
         this.syncLabel.setTextColor(-278748);
      } else if (n > 0) {
         this.syncLabel.setText(this.getString(string.syncing_pending, new Object[]{n}));
         this.syncLabel.setTextColor(-278748);
      } else {
         this.syncLabel.setText(this.getString(string.synced));
         this.syncLabel.setTextColor(-7934036);
      }
   }
}
