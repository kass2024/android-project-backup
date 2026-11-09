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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Student photos are stored on disk and in memory so a card tap never waits on the network.
 */
public class PhotoCache {
    private static final int TARGET = 480;
    private static final int LOGO_ID = -1;
    private final File dir;
    private final LruCache<Integer, Bitmap> mem;
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Object rosterLock = new Object();

    public PhotoCache(Context ctx) {
        dir = new File(ctx.getApplicationContext().getFilesDir(), "student_photos");
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
        if (!isRealPhoto(url)) {
            evict(studentId);
            view.setImageResource(R.drawable.bg_photo_placeholder);
            return;
        }
        Bitmap local = get(studentId);
        if (local != null && url.equals(readUrl(studentId))) {
            view.setImageBitmap(local);
            return;
        }
        view.setImageResource(R.drawable.bg_photo_placeholder);
        fetch(studentId, url, view);
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
        applyRoster(students);
    }

    /**
     * Keep disk/memory photos in lockstep with the server roster.
     * Deleted or placeholder photos are removed so a tap never shows a stale face.
     */
    public void applyRoster(JSONArray students) {
        final JSONArray copy = students == null ? new JSONArray() : students;
        io.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (rosterLock) {
                    Set<Integer> keep = new HashSet<Integer>();
                    keep.add(Integer.valueOf(LOGO_ID));
                    for (int i = 0; i < copy.length(); i++) {
                        JSONObject o = copy.optJSONObject(i);
                        if (o == null) {
                            continue;
                        }
                        int id = o.optInt("id", 0);
                        if (id <= 0) {
                            continue;
                        }
                        String url = o.optString("photo", "");
                        if (!isRealPhoto(url)) {
                            evict(id);
                            continue;
                        }
                        if (get(id) != null && url.equals(readUrl(id))) {
                            keep.add(Integer.valueOf(id));
                            continue;
                        }
                        Bitmap bmp = download(id, url);
                        if (bmp != null) {
                            keep.add(Integer.valueOf(id));
                        } else if (!url.equals(readUrl(id))) {
                            evict(id);
                        } else {
                            keep.add(Integer.valueOf(id));
                        }
                    }
                    prune(keep);
                }
            }
        });
    }

    public void evict(int studentId) {
        if (studentId == 0) {
            return;
        }
        mem.remove(studentId);
        File jpg = file(studentId);
        if (jpg.exists()) {
            jpg.delete();
        }
        File urlFile = new File(dir, studentId + ".url");
        if (urlFile.exists()) {
            urlFile.delete();
        }
    }

    private void prune(Set<Integer> keep) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            if (dot <= 0) {
                continue;
            }
            String stem = name.substring(0, dot);
            int id;
            try {
                id = Integer.parseInt(stem);
            } catch (Exception e) {
                continue;
            }
            if (!keep.contains(Integer.valueOf(id))) {
                evict(id);
            }
        }
    }

    static boolean isRealPhoto(String url) {
        if (url == null) {
            return false;
        }
        String u = url.trim().toLowerCase(Locale.US);
        if (u.length() < 8) {
            return false;
        }
        return !u.contains("fallback-avatar")
                && !u.contains("no_image")
                && !u.contains("placeholder")
                && !u.endsWith("/logo.jpeg")
                && !u.contains("/logo.jpeg?");
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
