package com.qonics.tflite;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Trace;
import android.util.Log;
import android.util.Pair;

import org.tensorflow.lite.Interpreter;
import com.qonics.env.Logger;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * Wrapper for frozen detection models trained using TensorFlow Object Detection API.
 */
public class TFLiteObjectDetectionAPIModel implements SimilarityClassifier {

  private static final Logger LOGGER = new Logger();

  private static final int OUTPUT_SIZE = 192;
  private static final int NUM_DETECTIONS = 1;
  private static final float IMAGE_MEAN = 128.0f;
  private static final float IMAGE_STD = 128.0f;
  private static final int DEFAULT_NUM_THREADS = 4;

  private boolean isModelQuantized;
  private int inputSize;

  private Vector<String> labels = new Vector<>();
  private int[] intValues;
  private float[][] embeedings;
  private ByteBuffer imgData;
  private Interpreter tfLite;

  // model + assets for reloading
  private String modelFilename;
  private AssetManager assetManager;

  private float[][][] outputLocations;
  private float[][] outputClasses;
  private float[][] outputScores;
  private float[] numDetections;

  private HashMap<String, Recognition> registered = new HashMap<>();
  public void register(String name, Recognition rec) {
    registered.put(name, rec);
  }

  private TFLiteObjectDetectionAPIModel() {}

  /** Memory-map the model file in Assets. */
  private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
          throws IOException {
    AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
    FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
    FileChannel fileChannel = inputStream.getChannel();
    long startOffset = fileDescriptor.getStartOffset();
    long declaredLength = fileDescriptor.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }

  /** Create and initialize classifier */
  public static SimilarityClassifier create(
          final AssetManager assetManager,
          final String modelFilename,
          final String labelFilename,
          final int inputSize,
          final boolean isQuantized)
          throws IOException {

    final TFLiteObjectDetectionAPIModel d = new TFLiteObjectDetectionAPIModel();

    // keep for reloads
    d.assetManager = assetManager;
    d.modelFilename = modelFilename;

    String actualFilename = labelFilename.split("file:///android_asset/")[1];
    InputStream labelsInput = assetManager.open(actualFilename);
    BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
    String line;
    while ((line = br.readLine()) != null) {
      LOGGER.w(line);
      d.labels.add(line);
    }
    br.close();

    d.inputSize = inputSize;

    try {
      Interpreter.Options options = new Interpreter.Options();
      options.setNumThreads(DEFAULT_NUM_THREADS);
      options.setUseNNAPI(false); // default off, can enable later
      d.tfLite = new Interpreter(loadModelFile(assetManager, modelFilename), options);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    d.isModelQuantized = isQuantized;

    int numBytesPerChannel = isQuantized ? 1 : 4;
    d.imgData = ByteBuffer.allocateDirect(1 * d.inputSize * d.inputSize * 3 * numBytesPerChannel);
    d.imgData.order(ByteOrder.nativeOrder());
    d.intValues = new int[d.inputSize * d.inputSize];

    d.outputLocations = new float[1][NUM_DETECTIONS][4];
    d.outputClasses = new float[1][NUM_DETECTIONS];
    d.outputScores = new float[1][NUM_DETECTIONS];
    d.numDetections = new float[1];

    return d;
  }

  /** Finds nearest embedding in dataset using L2 norm */
  private Pair<String, Float> findNearest(float[] emb) {
    Pair<String, Float> ret = null;
    for (Map.Entry<String, Recognition> entry : registered.entrySet()) {
      final String name = entry.getKey() + ":" + entry.getValue().getId();
      final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];

      float distance = 0;
      for (int i = 0; i < emb.length; i++) {
        float diff = emb[i] - knownEmb[i];
        distance += diff * diff;
      }
      distance = (float) Math.sqrt(distance);
      if (ret == null || distance < ret.second) {
        ret = new Pair<>(name, distance);
      }
    }
    return ret;
  }

  @Override
  public List<Recognition> recognizeImage(final Bitmap bitmap, boolean storeExtra) {
    Trace.beginSection("recognizeImage");

    Trace.beginSection("preprocessBitmap");
    bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

    imgData.rewind();
    for (int i = 0; i < inputSize; ++i) {
      for (int j = 0; j < inputSize; ++j) {
        int pixelValue = intValues[i * inputSize + j];
        if (isModelQuantized) {
          imgData.put((byte) ((pixelValue >> 16) & 0xFF));
          imgData.put((byte) ((pixelValue >> 8) & 0xFF));
          imgData.put((byte) (pixelValue & 0xFF));
        } else {
          imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
          imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
          imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
        }
      }
    }
    Trace.endSection();

    Object[] inputArray = {imgData};
    Log.e("FACE DATA", imgData.toString());

    Map<Integer, Object> outputMap = new HashMap<>();
    embeedings = new float[1][OUTPUT_SIZE];
    outputMap.put(0, embeedings);

    Trace.beginSection("run");
    tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
    Trace.endSection();

    float distance = Float.MAX_VALUE;
    String id = "";
    String label = "?";

    if (registered.size() > 0) {
      final Pair<String, Float> nearest = findNearest(embeedings[0]);
      if (nearest != null) {
        final String[] name = nearest.first.split(":");
        id = name[1];
        label = name[0];
        distance = nearest.second;
        LOGGER.i("nearest: " + name + " - distance: " + distance);
      }
    }

    final ArrayList<Recognition> recognitions = new ArrayList<>(1);
    Recognition rec = new Recognition(id, label, distance, new RectF(), bitmap);
    recognitions.add(rec);

    if (storeExtra) {
      rec.setExtra(embeedings);
    }

    Trace.endSection();
    return recognitions;
  }

  @Override
  public void enableStatLogging(final boolean logStats) {}

  @Override
  public String getStatString() {
    return "";
  }

  @Override
  public void close() {
    if (tfLite != null) {
      tfLite.close();
      tfLite = null;
    }
  }

  /** Recreate interpreter with new thread count */
  public void setNumThreads(int num_threads) {
    recreateInterpreter(num_threads, false);
  }

  /** Recreate interpreter with NNAPI enabled/disabled */
  @Override
  public void setUseNNAPI(boolean isChecked) {
    recreateInterpreter(DEFAULT_NUM_THREADS, isChecked);
  }

  /** Internal helper to rebuild interpreter */
  private void recreateInterpreter(int num_threads, boolean useNNAPI) {
    if (assetManager == null || modelFilename == null) {
      LOGGER.e("Cannot recreate interpreter: model or assets not set");
      return;
    }
    try {
      if (tfLite != null) {
        tfLite.close();
      }
      Interpreter.Options options = new Interpreter.Options();
      options.setNumThreads(num_threads);
      options.setUseNNAPI(useNNAPI);
      tfLite = new Interpreter(loadModelFile(assetManager, modelFilename), options);
      LOGGER.i("Interpreter recreated with threads=" + num_threads + ", NNAPI=" + useNNAPI);
    } catch (Exception e) {
      LOGGER.e("Error recreating interpreter: " + e.getMessage());
    }
  }
}
