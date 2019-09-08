package mchehab.com.behavioranalysis;

import android.content.Context;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

public class TFModel {
    static {
        System.loadLibrary("tensorflow_inference");
    }

    private TensorFlowInferenceInterface inferenceInterface;
    private static final String MODEL_FILE = "file:///android_asset/tensorflow_lite_model.pb";
    private static final String INPUT_NODE = "dense_1_input";
    private static final String[] OUTPUT_NODES = {"dense_2/Softmax"};
    private static final String OUTPUT_NODE = "dense_2/Softmax";
    private static final long[] INPUT_SIZE = {1,9}; // 1 row 9 columns/features
    private static final int OUTPUT_SIZE = 10; //number of labels

    public TFModel(final Context context) {
        inferenceInterface = new TensorFlowInferenceInterface(context.getAssets(), MODEL_FILE);
    }

    public float[] predict(float[] data) {
        float[] result = new float[OUTPUT_SIZE];
        // feed run fetch
        inferenceInterface.feed(INPUT_NODE, data, INPUT_SIZE);
        inferenceInterface.run(OUTPUT_NODES);
        inferenceInterface.fetch(OUTPUT_NODE, result);

        return result;
    }
}