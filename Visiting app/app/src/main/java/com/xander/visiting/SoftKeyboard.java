package com.xander.visiting;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.xander.visiting.R.drawable;
import com.xander.visiting.R.string;
import java.util.Locale;

public class SoftKeyboard extends LinearLayout {
   private static final String[] ROW_NUM = new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
   private static final String[] ROW_1 = new String[]{"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"};
   private static final String[] ROW_2 = new String[]{"a", "s", "d", "f", "g", "h", "j", "k", "l"};
   private static final String[] ROW_3 = new String[]{"z", "x", "c", "v", "b", "n", "m"};
   private static final String[] SYM_1 = new String[]{"!", "@", "#", "$", "%", "&", "*", "(", ")", "/"};
   private static final String[] SYM_2 = new String[]{"-", "_", "=", "+", ":", ";", "\"", "'", ",", "?"};
   private EditText target;
   private Host host;
   private boolean shiftOn = true;
   private boolean shiftLock;
   private boolean symbols;
   private boolean phone;
   private int keyH;
   private int gap;

   public SoftKeyboard(Context context) {
      super(context);
      this.init();
   }

   public SoftKeyboard(Context context, AttributeSet attrs) {
      super(context, attrs);
      this.init();
   }

   public SoftKeyboard(Context context, AttributeSet attrs, int defStyleAttr) {
      super(context, attrs, defStyleAttr);
      this.init();
   }

   public void setHost(Host host) {
      this.host = host;
   }

   public void setTarget(EditText field) {
      this.target = field;
      if (field != null) {
         hideSystemIme(field);
         this.applyModeFor(field);
         this.rebuild();
      }
   }

   public static void prepareField(EditText field) {
      if (field != null) {
         field.setShowSoftInputOnFocus(false);
         hideSystemIme(field);
      }
   }

   public static void hideSystemIme(View view) {
      if (view != null) {
         InputMethodManager imm = (InputMethodManager)view.getContext().getSystemService("input_method");
         if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
         }

      }
   }

   public static boolean isPhysicalKeyboard(KeyEvent event) {
      if (event == null) {
         return false;
      } else if (event.getKeyCode() == 4) {
         return false;
      } else if (event.getDeviceId() == -1) {
         return false;
      } else {
         InputDevice device = event.getDevice();
         if (device == null) {
            return event.getDeviceId() > 0;
         } else if (device.isVirtual()) {
            return false;
         } else {
            int sources = device.getSources();
            return (sources & 257) == 257 || (sources & 513) != 0;
         }
      }
   }

   private void init() {
      this.setOrientation(1);
      this.setBackgroundColor(-16048310);
      int pad = this.dp(8);
      this.setPadding(pad, this.dp(10), pad, this.dp(10));
      this.keyH = this.dp(68);
      this.gap = this.dp(5);
      this.rebuild();
   }

   private void applyModeFor(EditText field) {
      int type = field.getInputType();
      int cls = type & 15;
      int var = type & 4080;
      this.phone = cls == 3 || cls == 2;
      this.symbols = false;
      this.shiftOn = true;
      this.shiftLock = true;
   }

   private void rebuild() {
      this.removeAllViews();
      if (this.phone && !this.symbols) {
         this.buildPhone();
      } else if (this.symbols) {
         this.addRow(SYM_1, 1.0F, false);
         this.addRow(SYM_2, 1.0F, false);
         this.addSymbolBottom();
      } else {
         this.addRow(ROW_NUM, 1.0F, false);
         this.addRow(ROW_1, 1.0F, true);
         this.addLetterMiddle();
         this.addLetterBottom();
         this.addActionRow();
      }
   }

   private void buildPhone() {
      this.addRow(new String[]{"1", "2", "3"}, 1.2F, false);
      this.addRow(new String[]{"4", "5", "6"}, 1.2F, false);
      this.addRow(new String[]{"7", "8", "9"}, 1.2F, false);
      LinearLayout row = this.newRow();
      row.addView(this.actionKey("ABC", 1.0F, this::showLetters));
      row.addView(this.charKey("+", 1.2F, false));
      row.addView(this.charKey("0", 1.2F, false));
      row.addView(this.charKey("#", 1.2F, false));
      row.addView(this.actionKey("⌫", 1.0F, this::backspace));
      row.addView(this.nextKey());
      this.addView(row);
   }

   private void addLetterMiddle() {
      LinearLayout row = this.newRow();
      row.addView(this.spacer(0.35F));

      for(String key : ROW_2) {
         row.addView(this.charKey(key, 1.0F, true));
      }

      row.addView(this.spacer(0.35F));
      this.addView(row);
   }

   private void addLetterBottom() {
      LinearLayout row = this.newRow();
      row.addView(this.spacer(1.15F));

      for(String key : ROW_3) {
         row.addView(this.charKey(key, 1.0F, true));
      }

      row.addView(this.actionKey("⌫", 1.25F, this::backspace));
      this.addView(row);
   }

   private void addActionRow() {
      LinearLayout row = this.newRow();
      row.addView(this.actionKey("123", 1.2F, this::showSymbols));
      row.addView(this.charKey("-", 0.8F, false));
      row.addView(this.charKey(",", 0.7F, false));
      row.addView(this.spaceKey());
      row.addView(this.charKey(".", 0.7F, false));
      row.addView(this.charKey("'", 0.7F, false));
      row.addView(this.nextKey());
      this.addView(row);
   }

   private void addSymbolBottom() {
      LinearLayout row = this.newRow();
      row.addView(this.actionKey("ABC", 1.2F, this::showLetters));
      row.addView(this.spaceKey());
      row.addView(this.actionKey("⌫", 1.1F, this::backspace));
      row.addView(this.nextKey());
      this.addView(row);
   }

   private void addRow(String[] keys, float weight, boolean letters) {
      LinearLayout row = this.newRow();

      for(String key : keys) {
         row.addView(this.charKey(key, weight, letters));
      }

      this.addView(row);
   }

   private LinearLayout newRow() {
      LinearLayout row = new LinearLayout(this.getContext());
      row.setOrientation(0);
      row.setGravity(17);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, this.keyH);
      lp.topMargin = this.gap;
      row.setLayoutParams(lp);
      return row;
   }

   private TextView charKey(String value, float weight, boolean letter) {
      String shown = letter ? value.toUpperCase(Locale.US) : value;
      TextView key = this.keyView(shown, weight, drawable.bg_key, -15788246, 36);
      key.setOnClickListener((v) -> this.type(value, letter));
      return key;
   }

   private TextView actionKey(String label, float weight, Runnable action) {
      TextView key = this.keyView(label, weight, drawable.bg_key_action, -1, 26);
      key.setOnClickListener((v) -> action.run());
      return key;
   }

   private TextView nextKey() {
      TextView key = this.keyView(this.getResources().getString(string.key_next), 1.4F, drawable.bg_key_gold, -16048310, 24);
      key.setOnClickListener((v) -> this.goNext());
      return key;
   }

   private TextView spaceKey() {
      TextView key = this.keyView(this.getResources().getString(string.key_space), 3.4F, drawable.bg_key, -15788246, 24);
      key.setOnClickListener((v) -> this.type(" ", false));
      return key;
   }

   private View spacer(float weight) {
      View view = new View(this.getContext());
      view.setLayoutParams(new LinearLayout.LayoutParams(0, -1, weight));
      return view;
   }

   private TextView keyView(String label, float weight, int bg, int color, int sizeSp) {
      TextView key = new TextView(this.getContext());
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, weight);
      lp.leftMargin = this.gap;
      lp.rightMargin = this.gap;
      key.setLayoutParams(lp);
      key.setText(label);
      key.setGravity(17);
      key.setTextColor(color);
      key.setTextSize(2, (float)sizeSp);
      key.setTypeface(Typeface.DEFAULT_BOLD);
      key.setIncludeFontPadding(false);
      key.setBackgroundResource(bg);
      key.setFocusable(false);
      key.setClickable(true);
      key.setAllCaps(false);
      return key;
   }

   private void type(String raw, boolean letter) {
      EditText field = this.target;
      if (field != null && field.isEnabled()) {
         String text = raw;
         if (letter && raw.length() == 1 && Character.isLetter(raw.charAt(0))) {
            text = raw.toUpperCase(Locale.US);
         }

         this.insert(field, text);
      }
   }

   private void insert(EditText field, String text) {
      int start = Math.max(0, field.getSelectionStart());
      int end = field.getSelectionEnd();
      if (end < start) {
         int tmp = start;
         start = end;
         end = tmp;
      }

      Editable editable = field.getText();
      if (editable == null) {
         field.setText(text);
         field.setSelection(text.length());
      } else {
         editable.replace(start, Math.max(start, end), text);
      }
   }

   private void backspace() {
      EditText field = this.target;
      if (field != null && field.isEnabled()) {
         int start = Math.max(0, field.getSelectionStart());
         int end = field.getSelectionEnd();
         Editable editable = field.getText();
         if (editable != null && editable.length() != 0) {
            if (end != start) {
               editable.delete(Math.min(start, end), Math.max(start, end));
            } else {
               if (start > 0) {
                  editable.delete(start - 1, start);
               }

            }
         }
      }
   }

   private void toggleShift() {
      if (this.shiftLock) {
         this.shiftLock = false;
         this.shiftOn = false;
      } else if (this.shiftOn) {
         this.shiftLock = true;
      } else {
         this.shiftOn = true;
      }

      this.refreshLetters();
   }

   private void showSymbols() {
      this.symbols = true;
      this.phone = false;
      this.rebuild();
   }

   private void showLetters() {
      this.symbols = false;
      this.phone = false;
      this.rebuild();
   }

   private void goNext() {
      EditText field = this.target;
      if (field != null) {
         field.onEditorAction(5);
      }

      if (this.host != null) {
         this.host.onNext(field);
      }

   }

   private void refreshLetters() {
      this.rebuild();
   }

   private String shiftLabel() {
      return this.shiftLock ? "⇪" : "⇧";
   }

   private boolean shouldCapWords(EditText field) {
      int type = field.getInputType();
      return (type & 8192) != 0;
   }

   private boolean shouldCapSentences(EditText field) {
      int type = field.getInputType();
      return (type & 16384) != 0 || (type & 8192) != 0;
   }

   private int dp(int value) {
      return Math.round((float)value * this.getResources().getDisplayMetrics().density);
   }

   public interface Host {
      void onNext(EditText var1);
   }
}
