package com.calmara.multimodal.mediapipe;

import lombok.Data;

@Data
public class FaceLandmarks {
    private float[][] landmarks;

    public float[] getLandmark(int index) {
        if (landmarks != null && index < landmarks.length) {
            return landmarks[index];
        }
        return null;
    }

    public int getLandmarkCount() {
        return landmarks != null ? landmarks.length : 0;
    }

    public static final int LEFT_BROW_INNER = 107;
    public static final int RIGHT_BROW_INNER = 336;
    public static final int LEFT_BROW_OUTER = 70;
    public static final int RIGHT_BROW_OUTER = 300;
    public static final int LEFT_EYE_TOP = 159;
    public static final int LEFT_EYE_BOTTOM = 145;
    public static final int RIGHT_EYE_TOP = 386;
    public static final int RIGHT_EYE_BOTTOM = 374;
    public static final int MOUTH_LEFT = 61;
    public static final int MOUTH_RIGHT = 291;
}
