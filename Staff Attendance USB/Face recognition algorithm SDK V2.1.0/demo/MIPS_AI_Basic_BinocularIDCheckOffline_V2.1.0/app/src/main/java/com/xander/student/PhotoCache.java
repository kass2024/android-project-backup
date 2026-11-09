package com.xander.student;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Staff photos are stored on disk and in memory so a card tap never waits on the network.
 */
public class PhotoCache {
    private static final int TARGET = 480;
    private static final int LOGO_ID = -1;
    private final Context app;
    private final File dir;
    private final LruCache<Integer, Bitmap> mem;
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final Handler ui = new Handler(Looper.getMainLooper());

    public PhotoCache(Context ctx) {
        app = ctx.getApplicationContext();
        dir = new File(app.getFilesDir(), "staff_photos");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        mem = new LruCache<Integer, Bitmap>(Math.max(4096, kb)) {
            @Override
            protected int sizeOf(Integer key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    public Bitmap get(int studentId) {
        if (studentId == 0) {
            return null;
        }
        Bitmap hit = mem.get(studentId);
        if (hit != null && !hit.isRecycled()) {
            return hit;
        }
        File f = file(studentId);
        if (!f.exists() || f.length() < 32) {
            return null;
        }
        Bitmap bmp = decodeFile(f.getAbsolutePath());
        if (bmp != null) {
            mem.put(studentId, bmp);
        }
        return bmp;
    }

    public void bind(final ImageView view, final int studentId, String url) {
        if (view == null) {
            return;
        }
        view.setTag(R.id.photo_bind_tag, Integer.valueOf(studentId));
        Bitmap local = get(studentId);
        String cachedUrl = studentId == 0 ? "" : readUrl(studentId);
        boolean urlOk = url == null || url.length() < 8 || cachedUrl.length() == 0 || url.equals(cachedUrl);
        if (local != null) {
            view.setImageBitmap(local);
            if (urlOk) {
                return;
            }
        } else {
            view.setImageResource(R.drawable.bg_photo_placeholder);
        }
        if (!NetWatch.online(app)) {
            return;
        }
        if (url != null && url.length() >= 8 && !url.contains("fallback-avatar")) {
            fetch(studentId, url, view);
        }
    }

    public void bindLogo(final ImageView view, final String url) {
        if (view != null) {
            bind(view, LOGO_ID, url);
            return;
        }
        if (url == null || url.length() < 8) {
            return;
        }
        io.execute(new Runnable() {
            @Override
            public void run() {
                download(LOGO_ID, url);
            }
        });
    }

    public void prefetch(JSONArray students) {
        if (students == null) {
            return;
        }
        final JSONArray copy = students;
        io.execute(new Runnable() {
            @Override
            public void run() {
                prefetchWait(copy);
            }
        });
    }

    /** Download every staff photo onto disk. Safe to call from a login/sync worker thread. */
    public int prefetchWait(JSONArray students) {
        if (students == null) {
            return 0;
        }
        int ready = 0;
        for (int i = 0; i < students.length(); i++) {
            JSONObject o = students.optJSONObject(i);
            if (o == null) {
                continue;
            }
            int id = o.optInt("id", 0);
            String url = o.optString("photo", "");
            if (id <= 0) {
                continue;
            }
            if (url.length() < 8 || url.contains("fallback-avatar")) {
                continue;
            }
            if (get(id) != null && url.equals(readUrl(id))) {
                ready++;
                continue;
            }
            if (download(id, url) != null) {
                ready++;
            }
        }
        return ready;
    }

    public int cachedCount() {
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < files.length; i++) {
            if (files[i].getName().endsWith(".jpg") && files[i].length() > 32) {
                n++;
            }
        }
        return n;
    }

    private void fetch(final int studentId, final String url, final ImageView view) {
        io.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bmp = download(studentId, url);
                if (bmp == null) {
                    return;
                }
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        Object tag = view.getTag(R.id.photo_bind_tag);
                        if (tag instanceof Integer && ((Integer) tag).intValue() == studentId) {
                            view.setImageBitmap(bmp);
                        }
                    }
                });
            }
        });
    }

    private Bitmap download(int studentId, String url) {
        try {
            InputStream in = XanderApi.openStream(url);
            Bitmap src = BitmapFactory.decodeStream(in);
            try {
                in.close();
            } catch (Exception ignored) {
            }
            if (src == null) {
                return null;
            }
            Bitmap scaled = scale(src);
            if (scaled != src) {
                src.recycle();
            }
            File tmp = new File(dir, studentId + ".tmp");
            FileOutputStream fos = new FileOutputStream(tmp);
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, fos);
            fos.close();
            File dest = file(studentId);
            if (dest.exists()) {
                dest.delete();
            }
            tmp.renameTo(dest);
            writeUrl(studentId, url);
            mem.put(studentId, scaled);
            return scaled;
        } catch (Exception e) {
            KioskLog.e("Photo", "id=" + studentId + " " + e.getMessage());
            return null;
        }
    }

    private File file(int id) {
        return new File(dir, id + ".jpg");
    }

    private String readUrl(int id) {
        File f = new File(dir, id + ".url");
        if (!f.exists()) {
            return "";
        }
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] b = new byte[(int) f.length()];
            int n = in.read(b);
            in.close();
            return n <= 0 ? "" : new String(b, 0, n, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private void writeUrl(int id, String url) {
        try {
            FileOutputStream fos = new FileOutputStream(new File(dir, id + ".url"));
            fos.write(url.getBytes("UTF-8"));
            fos.close();
        } catch (Exception ignored) {
        }
    }

    private static Bitmap decodeFile(String path) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample(bounds.outWidth, bounds.outHeight, TARGET);
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(path, opts);
    }

    private static Bitmap scale(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= TARGET && h <= TARGET) {
            return src;
        }
        float s = TARGET / (float) Math.max(w, h);
        int nw = Math.max(1, Math.round(w * s));
        int nh = Math.max(1, Math.round(h * s));
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }

    private static int sample(int w, int h, int target) {
        int s = 1;
        while (w / s > target * 2 || h / s > target * 2) {
            s *= 2;
        }
        return s;
    }
}
