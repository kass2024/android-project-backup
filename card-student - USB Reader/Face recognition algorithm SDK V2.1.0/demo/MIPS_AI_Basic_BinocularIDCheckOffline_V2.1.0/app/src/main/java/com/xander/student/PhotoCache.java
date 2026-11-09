package com.xander.student;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.LruCache;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HttpsURLConnection;

/**
 * Tap path never waits on the network. Disk/RAM photos are shown immediately;
 * missing files download in the background for the next tap.
 */
public class PhotoCache {
    private static final int TARGET = 192;
    private static final int LOGO_ID = -1;
    private static PhotoCache instance;
    private final File dir;
    private final LruCache<Integer, Bitmap> mem;
    private final ExecutorService dlIo = Executors.newFixedThreadPool(6, low("xander-photo-dl"));
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final AtomicInteger prefetchLeft = new AtomicInteger(0);
    private volatile String baseUrl = "";

    public static synchronized PhotoCache get(Context ctx) {
        if (instance == null) {
            instance = new PhotoCache(ctx.getApplicationContext());
        }
        return instance;
    }

    public PhotoCache(Context ctx) {
        dir = new File(ctx.getApplicationContext().getFilesDir(), "student_photos");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 4);
        mem = new LruCache<Integer, Bitmap>(Math.max(24576, kb)) {
            @Override
            protected int sizeOf(Integer key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    public void setBaseUrl(String url) {
        if (url == null) {
            return;
        }
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        baseUrl = u;
    }

    /** Decode every photo already on disk into RAM so the first tap is a cache hit. */
    public void warmRam() {
        dlIo.execute(new Runnable() {
            @Override
            public void run() {
                File[] files = dir.listFiles();
                if (files == null) {
                    return;
                }
                for (int i = 0; i < files.length; i++) {
                    File f = files[i];
                    String name = f.getName();
                    if (!name.endsWith(".jpg") || name.endsWith(".tmp")) {
                        continue;
                    }
                    try {
                        int id = Integer.parseInt(name.substring(0, name.length() - 4));
                        if (peek(id) == null) {
                            decodeOnDisk(id);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    public void bind(final ImageView view, final int studentId, String url) {
        if (view == null) {
            return;
        }
        view.setTag(R.id.photo_bind_tag, Integer.valueOf(studentId));
        Bitmap local = peek(studentId);
        if (local == null) {
            local = decodeOnDisk(studentId);
        }
        if (local != null) {
            show(view, local);
            return;
        }
        view.setImageResource(R.drawable.bg_photo_placeholder);
        final String abs = absUrl(url);
        if (abs.length() >= 8 && !abs.contains("fallback-avatar")) {
            dlIo.execute(new Runnable() {
                @Override
                public void run() {
                    Bitmap net = download(studentId, abs, true);
                    if (net != null) {
                        apply(view, studentId, net);
                    }
                }
            });
        }
    }

    public void bindLogo(final ImageView view, final String url) {
        final String abs = absUrl(url);
        if (view == null) {
            if (abs.length() < 8) {
                return;
            }
            dlIo.execute(new Runnable() {
                @Override
                public void run() {
                    download(LOGO_ID, abs, true);
                }
            });
            return;
        }
        view.setTag(R.id.photo_bind_tag, Integer.valueOf(LOGO_ID));
        Bitmap local = peek(LOGO_ID);
        if (local == null) {
            local = decodeOnDisk(LOGO_ID);
        }
        if (local != null) {
            show(view, local);
            return;
        }
        view.setImageResource(R.drawable.wisdom_logo);
        if (abs.length() >= 8 && !abs.contains("fallback-avatar")) {
            dlIo.execute(new Runnable() {
                @Override
                public void run() {
                    final Bitmap bmp = download(LOGO_ID, abs, true);
                    if (bmp != null) {
                        apply(view, LOGO_ID, bmp);
                    }
                }
            });
        }
    }

    public void prefetch(JSONArray students) {
        if (students == null || prefetchLeft.get() > 0) {
            return;
        }
        int n = 0;
        for (int i = 0; i < students.length(); i++) {
            JSONObject o = students.optJSONObject(i);
            if (o == null) {
                continue;
            }
            int id = o.optInt("id", 0);
            String url = absUrl(o.optString("photo", ""));
            if (id <= 0 || url.length() < 8 || url.contains("fallback-avatar")) {
                continue;
            }
            if (onDisk(id) && samePhoto(url, readUrl(id))) {
                if (peek(id) == null) {
                    final int warmId = id;
                    dlIo.execute(new Runnable() {
                        @Override
                        public void run() {
                            decodeOnDisk(warmId);
                        }
                    });
                }
                continue;
            }
            n++;
            prefetchLeft.incrementAndGet();
            final int sid = id;
            final String abs = url;
            dlIo.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        download(sid, abs, true);
                    } finally {
                        prefetchLeft.decrementAndGet();
                    }
                }
            });
        }
        if (n == 0) {
            prefetchLeft.set(0);
        }
    }

    private Bitmap peek(int studentId) {
        if (studentId == 0) {
            return null;
        }
        Bitmap hit = mem.get(studentId);
        if (hit != null && !hit.isRecycled()) {
            return hit;
        }
        return null;
    }

    private void show(ImageView view, Bitmap bmp) {
        view.setImageBitmap(bmp);
        view.invalidate();
        view.postInvalidate();
    }

    private void apply(final ImageView view, final int studentId, final Bitmap bmp) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                Object tag = view.getTag(R.id.photo_bind_tag);
                if (tag instanceof Integer && ((Integer) tag).intValue() == studentId) {
                    show(view, bmp);
                }
            }
        });
    }

    private Bitmap decodeOnDisk(int studentId) {
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

    private boolean onDisk(int id) {
        File f = file(id);
        return f.exists() && f.length() >= 32;
    }

    private Bitmap download(int studentId, String url, boolean cacheMem) {
        HttpURLConnection c = null;
        InputStream in = null;
        try {
            URL u = new URL(url);
            c = (HttpURLConnection) u.openConnection();
            if (c instanceof HttpsURLConnection) {
                XanderHttps.apply((HttpsURLConnection) c, XanderHttps.usesSchoolIp(url));
            }
            c.setConnectTimeout(2500);
            c.setReadTimeout(4000);
            c.setUseCaches(true);
            c.setInstanceFollowRedirects(true);
            in = c.getInputStream();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            byte[] data = readAll(in);
            if (data == null || data.length < 32) {
                return null;
            }
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample(bounds.outWidth, bounds.outHeight, TARGET);
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap src = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            if (src == null) {
                return null;
            }
            Bitmap scaled = scale(src);
            if (scaled != src) {
                src.recycle();
            }
            File tmp = new File(dir, studentId + ".tmp");
            FileOutputStream fos = new FileOutputStream(tmp);
            scaled.compress(Bitmap.CompressFormat.JPEG, 72, fos);
            fos.close();
            File dest = file(studentId);
            if (dest.exists()) {
                dest.delete();
            }
            tmp.renameTo(dest);
            writeUrl(studentId, url);
            if (cacheMem) {
                mem.put(studentId, scaled);
                return scaled;
            }
            scaled.recycle();
            return null;
        } catch (Exception e) {
            KioskLog.e("Photo", "id=" + studentId + " " + e.getMessage());
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private String absUrl(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim();
        if (u.length() < 4) {
            return "";
        }
        if (u.startsWith("http://") || u.startsWith("https://")) {
            return u;
        }
        String base = baseUrl;
        if (base.length() == 0) {
            return u;
        }
        if (u.startsWith("/")) {
            return base + u;
        }
        return base + "/" + u;
    }

    private static boolean samePhoto(String a, String b) {
        return stripQuery(a).equalsIgnoreCase(stripQuery(b));
    }

    private static String stripQuery(String url) {
        if (url == null) {
            return "";
        }
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
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

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(8192);
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
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

    private static ThreadFactory low(final String name) {
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        r.run();
                    }
                }, name);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
        };
    }
}
