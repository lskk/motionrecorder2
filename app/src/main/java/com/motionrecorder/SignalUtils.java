package com.motionrecorder;

import android.util.Log;

import java.util.Arrays;

public class SignalUtils {

    private static final String TAG = SignalUtils.class.getName();

    /**
     * Upsample with linear interpolation. If newLength is shorter, it will truncate.
     * @param source
     * @param newLength
     * @return Upsampled or (possibly truncated) copy of source.
     */
    public static float[] upsample(float[] source, int newLength) {
        if (source.length == 0) {
            Log.w(TAG, String.format("Trying to upsample to %d samples but input length is 0", newLength));
            return Arrays.copyOf(source, newLength);
        } else if (newLength == source.length) {
            return source;
        } else if (newLength < source.length) {
            return Arrays.copyOf(source, newLength);
        } else {
            final float[] result = new float[newLength];
            for (int i = 0; i < newLength; i++) {
                float sourcePos = ((float) i) / (newLength - 1) * (source.length - 1);
                float frac1 = sourcePos - Math.abs(sourcePos);
                float frac2 = 1f - sourcePos;
                float v1 = source[(int) sourcePos];
                float v2 = ((int) sourcePos + 1) < source.length ? source[(int) sourcePos + 1] : source[source.length - 1];
                float interpolated = frac1 * v1 + frac2 * v2;
                result[i] = interpolated;
            }
            return result;
        }
    }
}
