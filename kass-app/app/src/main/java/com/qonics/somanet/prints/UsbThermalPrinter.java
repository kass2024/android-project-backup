package com.qonics.somanet.prints;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * ESC/POS thermal printer on the phone USB port (OTG). Bluetooth printing is unchanged.
 */
public class UsbThermalPrinter {
    public interface Listener {
        void onResult(boolean printed, String message);
    }

    private static final String TAG = "UsbThermalPrinter";
    private static final String ACTION = "com.xandertech.smartsms.USB_PRINTER_PERMISSION";
    /** Font A on a 58mm slip. Every line is padded to this width. */
    private static final int COLS = 32;

    /** Common ESC/POS thermal vendors, decimal. */
    private static final int[] VENDORS = {
            1046, 1155, 1208, 1305, 1659, 4070, 5455, 10473, 26728, 49745
    };

    private final Context context;
    private final UsbManager usb;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private Slip pending;
    private boolean registered;

    public static class Slip {
        public String logoUrl = "";
        public String school = "";
        public String slogan = "";
        public String address = "";
        public String pobox = "";
        public String phone = "";
        public String email = "";
        public String website = "";
        public String student = "";
        public String destination = "";
        public String reason = "";
        public String leave = "";
        public String ret = "";
        public String givenBy = "";
        public String printedOn = "";
    }

    private final BroadcastReceiver permission = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (intent == null || !ACTION.equals(intent.getAction())) return;
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            Slip slip = pending;
            pending = null;
            if (!granted || device == null || slip == null) {
                tell(false, "USB printer permission was not allowed");
                return;
            }
            printAsync(device, slip);
        }
    };

    public UsbThermalPrinter(Context context) {
        this.context = context.getApplicationContext();
        this.usb = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void start(Context host) {
        if (registered || usb == null) return;
        IntentFilter filter = new IntentFilter(ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            host.registerReceiver(permission, filter, Context.RECEIVER_EXPORTED);
        } else {
            host.registerReceiver(permission, filter);
        }
        registered = true;
    }

    public void stop(Context host) {
        if (!registered) return;
        try {
            host.unregisterReceiver(permission);
        } catch (Exception ignored) {
        }
        registered = false;
    }

    /** @return the attached printer, or null when nothing suitable is on USB */
    public UsbDevice find() {
        if (usb == null) return null;
        HashMap<String, UsbDevice> list = usb.getDeviceList();
        if (list == null || list.isEmpty()) return null;
        UsbDevice best = null;
        int bestScore = 0;
        int outsiders = 0;
        for (UsbDevice device : list.values()) {
            if (device == null || device.getDeviceClass() == UsbConstants.USB_CLASS_HUB) continue;
            outsiders++;
            int score = score(device);
            if (score > bestScore) {
                bestScore = score;
                best = device;
            }
        }
        if (bestScore >= 40) return best;
        if (outsiders == 1 && best != null && bulkOut(best) != null) return best;
        return null;
    }

    /**
     * Print on USB when a thermal printer is attached.
     * @return false when no USB printer is present, so Bluetooth can run instead
     */
    public boolean tryPrint(Context host, Slip slip) {
        UsbDevice device = find();
        if (device == null || usb == null || slip == null) return false;
        if (!usb.hasPermission(device)) {
            pending = slip;
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent grant = PendingIntent.getBroadcast(
                    context, 0, new Intent(ACTION).setPackage(context.getPackageName()), flags);
            usb.requestPermission(device, grant);
            tell(false, "Allow the USB printer. The slip prints after you tap OK.");
            return true;
        }
        printAsync(device, slip);
        return true;
    }

    private void printAsync(UsbDevice device, Slip slip) {
        new Thread(() -> {
            boolean ok = write(device, slip);
            main.post(() -> tell(ok, ok
                    ? "Permission saved and printed."
                    : "Permission saved. The printer did not print."));
        }).start();
    }

    private void tell(boolean printed, String message) {
        if (listener != null) listener.onResult(printed, message);
    }

    private boolean write(UsbDevice device, Slip slip) {
        UsbInterface iface = printerInterface(device);
        UsbEndpoint out = iface == null ? null : bulkOut(iface);
        if (iface == null || out == null || usb == null) return false;
        UsbDeviceConnection conn = usb.openDevice(device);
        if (conn == null) return false;
        try {
            if (!conn.claimInterface(iface, true)) return false;
            byte[] data = payload(slip);
            int offset = 0;
            while (offset < data.length) {
                int len = Math.min(512, data.length - offset);
                byte[] chunk = new byte[len];
                System.arraycopy(data, offset, chunk, 0, len);
                int sent = conn.bulkTransfer(out, chunk, len, 4000);
                if (sent <= 0) {
                    Log.w(TAG, "bulkTransfer failed at " + offset);
                    return false;
                }
                offset += sent;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "print failed: " + e.getMessage());
            return false;
        } finally {
            try {
                conn.releaseInterface(iface);
            } catch (Exception ignored) {
            }
            conn.close();
        }
    }

    private byte[] payload(Slip slip) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // Center every line so the left and right margins stay equal.
            out.write(new byte[]{0x1B, 0x40, 0x1B, 0x61, 0x01, 0x1B, 0x20, 0x00, 0x1B, 0x4D, 0x00, 0x1B, 0x33, 40});
            byte[] logo = logoRaster(slip.logoUrl);
            if (logo != null) {
                out.write(logo);
                out.write(0x0A);
            }
            heading(out, slip.school);
            paragraph(out, "REPUBLIC OF RWANDA", false);
            paragraph(out, "MINISTRY OF EDUCATION", false);
            paragraph(out, slip.slogan, false);
            paragraph(out, slip.address, false);
            if (slip.pobox != null && slip.pobox.trim().length() > 0) {
                paragraph(out, "P.O. Box " + slip.pobox.trim(), false);
            }
            if (slip.phone != null && slip.phone.trim().length() > 0) {
                paragraph(out, "Tel: " + slip.phone.trim(), false);
            }
            paragraph(out, slip.email, false);
            paragraph(out, slip.website, false);
            blank(out);
            paragraph(out, "STUDENT PERMISSION", true);
            paragraph(out, "------------------------------", false);
            blank(out);
            field(out, "Student name", slip.student, false);
            field(out, "Destination", slip.destination, false);
            field(out, "Reason", slip.reason, true);
            field(out, "Leave time", slip.leave, false);
            field(out, "Return time", slip.ret, false);
            field(out, "Permission given by", slip.givenBy, false);
            field(out, "Printed on", slip.printedOn, false);
            out.write(new byte[]{0x0A, 0x0A, 0x0A, 0x1B, 0x64, 0x04, 0x1D, 0x56, 0x00});
        } catch (Exception e) {
            Log.w(TAG, "payload: " + e.getMessage());
        }
        return out.toByteArray();
    }

    private void heading(ByteArrayOutputStream out, String text) throws Exception {
        out.write(new byte[]{0x1B, 0x45, 0x01, 0x1D, 0x21, 0x11, 0x1B, 0x33, 60});
        for (String row : layout(text, 16)) writeRow(out, row);
        out.write(new byte[]{0x1D, 0x21, 0x00, 0x1B, 0x45, 0x00, 0x1B, 0x33, 40});
    }

    private void paragraph(ByteArrayOutputStream out, String text, boolean bold) throws Exception {
        if (text == null || text.trim().length() == 0) return;
        if (bold) out.write(new byte[]{0x1B, 0x45, 0x01});
        for (String row : layout(text, COLS)) writeRow(out, row);
        if (bold) out.write(new byte[]{0x1B, 0x45, 0x00});
    }

    private void field(ByteArrayOutputStream out, String label, String value, boolean emphasize) throws Exception {
        paragraph(out, label + ":", emphasize);
        if (emphasize) {
            out.write(new byte[]{0x1B, 0x45, 0x01, 0x1B, 0x2D, 0x01, 0x1D, 0x21, 0x01, 0x1B, 0x33, 56});
        }
        String shown = value == null ? "" : value.trim();
        if (shown.length() == 0) shown = "-";
        for (String row : layout(shown, COLS)) writeRow(out, row);
        if (emphasize) {
            out.write(new byte[]{0x1D, 0x21, 0x00, 0x1B, 0x2D, 0x00, 0x1B, 0x45, 0x00, 0x1B, 0x33, 40});
        }
        blank(out);
    }

    private void blank(ByteArrayOutputStream out) throws Exception {
        out.write(0x0A);
    }

    private void writeRow(ByteArrayOutputStream out, String row) throws Exception {
        String shown = row == null ? "" : row.trim();
        byte[] bytes;
        try {
            bytes = shown.getBytes("GBK");
        } catch (Exception e) {
            bytes = shown.getBytes();
        }
        out.write(new byte[]{0x1B, 0x61, 0x01});
        out.write(bytes);
        out.write(0x0A);
    }

    /** Wrap long lines. The printer centers each line with equal side margins. */
    private List<String> layout(String text, int width) {
        ArrayList<String> lines = new ArrayList<>();
        if (text == null || width < 8) return lines;
        String clean = text.replace('\r', ' ').replace('\n', ' ').trim();
        if (clean.length() == 0) return lines;
        lines.addAll(wrap(clean, width));
        return lines;
    }

    private ArrayList<String> wrap(String text, int width) {
        ArrayList<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder cur = new StringBuilder();
        for (String word : words) {
            if (word.length() == 0) continue;
            if (word.length() > width) {
                if (cur.length() > 0) {
                    lines.add(cur.toString());
                    cur = new StringBuilder();
                }
                for (int i = 0; i < word.length(); i += width) {
                    lines.add(word.substring(i, Math.min(word.length(), i + width)));
                }
                continue;
            }
            if (cur.length() == 0) {
                cur.append(word);
            } else if (cur.length() + 1 + word.length() <= width) {
                cur.append(' ').append(word);
            } else {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    private byte[] logoRaster(String url) {
        if (url == null || url.length() < 8) return null;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            Bitmap raw = BitmapFactory.decodeStream(conn.getInputStream());
            if (raw == null) return null;
            int target = 200;
            int h = Math.max(1, raw.getHeight() * target / Math.max(1, raw.getWidth()));
            if (h > 180) {
                target = target * 180 / h;
                h = 180;
            }
            Bitmap scaled = Bitmap.createScaledBitmap(raw, Math.max(8, target), Math.max(8, h), true);
            int widthBytes = 48;
            int width = widthBytes * 8;
            int left = Math.max(0, (width - scaled.getWidth()) / 2);
            ByteArrayOutputStream raster = new ByteArrayOutputStream();
            raster.write(new byte[]{
                    0x1D, 0x76, 0x30, 0x00,
                    (byte) (widthBytes & 0xFF), (byte) ((widthBytes >> 8) & 0xFF),
                    (byte) (scaled.getHeight() & 0xFF), (byte) ((scaled.getHeight() >> 8) & 0xFF)
            });
            for (int y = 0; y < scaled.getHeight(); y++) {
                for (int xb = 0; xb < widthBytes; xb++) {
                    int bits = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int x = xb * 8 + bit - left;
                        boolean black = false;
                        if (x >= 0 && x < scaled.getWidth()) {
                            int c = scaled.getPixel(x, y);
                            int lum = ((c >> 16) & 0xFF) * 3 + ((c >> 8) & 0xFF) * 6 + (c & 0xFF);
                            int alpha = (c >>> 24) & 0xFF;
                            black = alpha > 40 && lum < 1800;
                        }
                        if (black) bits |= 0x80 >> bit;
                    }
                    raster.write(bits);
                }
            }
            raster.write(0x0A);
            if (scaled != raw) scaled.recycle();
            raw.recycle();
            return raster.toByteArray();
        } catch (Exception e) {
            Log.w(TAG, "logo: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private int score(UsbDevice device) {
        if (device.getDeviceClass() == UsbConstants.USB_CLASS_PRINTER) return 100;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            if (device.getInterface(i).getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) return 100;
        }
        for (int vendor : VENDORS) {
            if (device.getVendorId() == vendor && bulkOut(device) != null) return 80;
        }
        int cls = device.getDeviceClass();
        if ((cls == 255 || cls == 0) && bulkOut(device) != null) return 40;
        return 0;
    }

    private UsbInterface printerInterface(UsbDevice device) {
        UsbInterface fallback = null;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (bulkOut(iface) == null) continue;
            if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) return iface;
            if (fallback == null) fallback = iface;
        }
        return fallback;
    }

    private UsbEndpoint bulkOut(UsbDevice device) {
        UsbInterface iface = printerInterface(device);
        return iface == null ? null : bulkOut(iface);
    }

    private UsbEndpoint bulkOut(UsbInterface iface) {
        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint ep = iface.getEndpoint(i);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                    && ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                return ep;
            }
        }
        return null;
    }
}
