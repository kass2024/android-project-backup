package com.qonics.utils;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.media.FaceDetector;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;

import com.qonics.somanet.MainActivity;
import com.qonics.somanet.R;
import com.squareup.picasso.Callback;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;

import java.io.InputStream;

/**
 * Student photos cropped to the US visa standard: 2×2 inches, square, white background.
 * Head from chin to crown is about 60% of the frame (allowed range 50–69%).
 */
public final class StudentPhotoHelper {
    /** 2 inches at 300 dpi. */
    public static final int ID_SIZE = 600;
    /** Chin-to-crown target as a fraction of the square (US visa allows 50–69%). */
    private static final float HEAD_FRACTION = 0.60f;
    /** Eyes sit about 62% up from the bottom, so 38% down from the top. */
    private static final float EYES_FROM_TOP = 0.38f;

    private StudentPhotoHelper() {
    }

    public static String urlFor(String filenameOrUrl) {
        if (TextUtils.isEmpty(filenameOrUrl)) {
            return null;
        }
        String photo = filenameOrUrl.trim();
        if (photo.startsWith("http://") || photo.startsWith("https://")) {
            return photo;
        }
        int slash = photo.lastIndexOf('/');
        if (slash >= 0 && slash < photo.length() - 1) {
            photo = photo.substring(slash + 1);
        }
        return MainActivity.SERVER + "../assets/images/profile/" + photo;
    }

    public static void invalidate(String filenameOrUrl) {
        String url = urlFor(filenameOrUrl);
        if (url != null) {
            try {
                Picasso.get().invalidate(url);
            } catch (Exception ignored) {
            }
        }
    }

    public static void load(ImageView imageView, String filenameOrUrl) {
        load(imageView, filenameOrUrl, false);
    }

    /** Show the photo already saved on this phone, then the server copy if it is not here yet. */
    public static void loadPreferCache(ImageView imageView, String filenameOrUrl) {
        if (imageView == null) {
            return;
        }
        String url = urlFor(filenameOrUrl);
        if (url == null || url.length() < 8) {
            imageView.setImageResource(R.drawable.placeholder_image);
            return;
        }
        imageView.setTag(url);
        try {
            Picasso.get()
                    .load(url)
                    .networkPolicy(NetworkPolicy.OFFLINE)
                    .placeholder(R.drawable.placeholder_image)
                    .into(imageView, new Callback() {
                        @Override
                        public void onSuccess() {
                        }

                        @Override
                        public void onError(Exception e) {
                            if (!url.equals(imageView.getTag())) {
                                return;
                            }
                            load(imageView, filenameOrUrl, false);
                        }
                    });
        } catch (Exception e) {
            load(imageView, filenameOrUrl, false);
        }
    }

    public static void load(ImageView imageView, String filenameOrUrl, boolean forceRefresh) {
        if (imageView == null) {
            return;
        }
        String url = urlFor(filenameOrUrl);
        if (url == null || url.length() < 8) {
            imageView.setImageResource(R.drawable.placeholder_image);
            return;
        }
        try {
            if (forceRefresh) {
                Picasso.get().invalidate(url);
            }
            com.squareup.picasso.RequestCreator creator = Picasso.get()
                    .load(url)
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.placeholder_image);
            if (forceRefresh) {
                creator = creator
                        .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                        .networkPolicy(NetworkPolicy.NO_CACHE);
            }
            creator.into(imageView);
        } catch (Exception e) {
            Log.e("StudentPhotoHelper", "load failed: " + e.getMessage());
            imageView.setImageResource(R.drawable.placeholder_image);
        }
    }

    public static void updateLocalDb(Context context, int studentId, String filename) {
        if (context == null || studentId < 1 || TextUtils.isEmpty(filename)) {
            return;
        }
        try {
            SQLiteDatabase db = context.openOrCreateDatabase("somanet_attendance_app_db", Context.MODE_PRIVATE, null);
            db.execSQL("update tb_student set photo=? where id=?", new Object[]{filename, studentId});
            db.close();
        } catch (Exception e) {
            Log.e("StudentPhotoHelper", "db update failed: " + e.getMessage());
        }
    }

    /** Decode camera Uri, fix orientation, then smart-crop to student ID photo. */
    public static Bitmap decodeAndMakeIdPhoto(Context context, Uri uri) {
        if (context == null || uri == null) {
            return null;
        }
        try {
            Bitmap raw = decodeSampled(context, uri, 1600);
            if (raw == null) {
                return null;
            }
            Bitmap oriented = applyExifOrientation(context, uri, raw);
            Bitmap id = toStudentIdPhoto(oriented);
            if (oriented != null && oriented != raw && oriented != id && !oriented.isRecycled()) {
                oriented.recycle();
            }
            if (raw != null && raw != id && !raw.isRecycled()) {
                raw.recycle();
            }
            return id;
        } catch (Exception e) {
            Log.e("StudentPhotoHelper", "decodeAndMakeIdPhoto: " + e.getMessage());
            return null;
        }
    }

    /**
     * Square 2×2 visa photo. The head is scaled uniformly onto white — never stretched.
     */
    public static Bitmap toStudentIdPhoto(Bitmap source) {
        if (source == null) {
            return null;
        }
        RectF crop = visaSquare(source);
        if (crop == null) {
            crop = fallbackSquare(source);
        }
        Bitmap out = Bitmap.createBitmap(ID_SIZE, ID_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.WHITE);
        float side = Math.max(crop.width(), 1f);
        float scale = ID_SIZE / side;

        int srcLeft = Math.max(0, (int) Math.floor(crop.left));
        int srcTop = Math.max(0, (int) Math.floor(crop.top));
        int srcRight = Math.min(source.getWidth(), (int) Math.ceil(crop.right));
        int srcBottom = Math.min(source.getHeight(), (int) Math.ceil(crop.bottom));
        if (srcRight - srcLeft < 8 || srcBottom - srcTop < 8) {
            return out;
        }
        Rect src = new Rect(srcLeft, srcTop, srcRight, srcBottom);
        RectF dest = new RectF(
                (srcLeft - crop.left) * scale,
                (srcTop - crop.top) * scale,
                (srcRight - crop.left) * scale,
                (srcBottom - crop.top) * scale);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, src, dest, paint);
        return out;
    }

    /** Square around the face so chin-to-crown is ~60% of the frame and eyes sit in the visa band. */
    private static RectF visaSquare(Bitmap src) {
        try {
            int evenW = src.getWidth() & ~1;
            if (evenW < 32 || src.getHeight() < 32) {
                return null;
            }
            Bitmap working = src;
            if (src.getWidth() != evenW) {
                working = Bitmap.createBitmap(src, 0, 0, evenW, src.getHeight());
            }
            Bitmap rgb565 = working.getConfig() == Bitmap.Config.RGB_565
                    ? working
                    : working.copy(Bitmap.Config.RGB_565, true);

            FaceDetector detector = new FaceDetector(rgb565.getWidth(), rgb565.getHeight(), 4);
            FaceDetector.Face[] faces = new FaceDetector.Face[4];
            int found = detector.findFaces(rgb565, faces);
            if (rgb565 != working && rgb565 != src && !rgb565.isRecycled()) {
                rgb565.recycle();
            }
            if (working != src && !working.isRecycled()) {
                working.recycle();
            }
            if (found < 1) {
                return null;
            }

            FaceDetector.Face best = faces[0];
            for (int i = 1; i < found; i++) {
                if (faces[i] != null && faces[i].eyesDistance() > best.eyesDistance()) {
                    best = faces[i];
                }
            }
            PointF mid = new PointF();
            best.getMidPoint(mid);
            float eyes = Math.max(best.eyesDistance(), 1f);
            // Crown above the eyes and chin below them. Head height ≈ 2.6 × eye spacing.
            float headPx = eyes * 2.6f;
            float side = headPx / HEAD_FRACTION;
            float left = mid.x - side / 2f;
            float top = mid.y - side * EYES_FROM_TOP;
            return new RectF(left, top, left + side, top + side);
        } catch (Exception e) {
            Log.w("StudentPhotoHelper", "face crop skipped: " + e.getMessage());
            return null;
        }
    }

    /** Square crop biased to the upper body when no face is found. Stays inside the photo. */
    private static RectF fallbackSquare(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        float side = Math.min(w, h);
        float x = (w - side) / 2f;
        float y = h > w ? Math.max(0, (h - side) / 5f) : (h - side) / 2f;
        if (y + side > h) y = h - side;
        return new RectF(x, y, x + side, y + side);
    }

    private static Bitmap decodeSampled(Context context, Uri uri, int maxSide) throws Exception {
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream in1 = context.getContentResolver().openInputStream(uri);
        android.graphics.BitmapFactory.decodeStream(in1, null, bounds);
        if (in1 != null) {
            in1.close();
        }

        int sample = 1;
        int maxDim = Math.max(bounds.outWidth, bounds.outHeight);
        while (maxDim / sample > maxSide) {
            sample *= 2;
        }

        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inSampleSize = Math.max(1, sample);
        InputStream in2 = context.getContentResolver().openInputStream(uri);
        Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in2, null, opts);
        if (in2 != null) {
            in2.close();
        }
        return bmp;
    }

    private static Bitmap applyExifOrientation(Context context, Uri uri, Bitmap bitmap) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            return bitmap;
        }
        try {
            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) {
                return bitmap;
            }
            ExifInterface exif = new ExifInterface(in);
            in.close();
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int degrees = 0;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degrees = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degrees = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degrees = 270;
                    break;
                default:
                    return bitmap;
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            return rotated;
        } catch (Exception e) {
            return bitmap;
        }
    }
}
