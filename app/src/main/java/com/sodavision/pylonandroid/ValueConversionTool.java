package com.sodavision.pylonandroid;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ValueConversionTool {

    private final static double BOUNDARY_VALUE = 1000;

    public static double getBoundaryValue()
    {
        return BOUNDARY_VALUE;
    }

    /**
     * the implementation of these two function are completely opposite processes, so one of them will be explained,
     * the other one should be easy to understand.
     */

    /**
     * convert the SeekBar Value to exposure time or gain value
     * when maxvalue <= boundary value, SeekBar value range will be always from 0 to 1000
     * if boundary value < maxvalue <= 20000, SeekBar value will be directly convert to camera value
     * according to the ratio of camera value range to SeekBar value range
     * if 20000 < maxvalue <= 200000, the half of SeekBar value range (0 to 500) will be mapped to the camera value range
     * of 0 to 10000, the remaining half will be mapped to the camera value range of 10000 to maximum camera value
     * the rest of codes do the similar things
     **/
    public static double seekBarValueToCameraValueConversion(int value, double maxValue, double minValue, int maxRangeValue, int minRangeValue)
    {
        if (maxValue > BOUNDARY_VALUE)
        {
            if (maxValue <= 20000)
            {
                return (maxValue - minValue) / (float)(maxRangeValue - minRangeValue) * value + minValue;
            }
            else if (maxValue > 20000 && maxValue <= 200000)
            {
                if (value <= (float)BOUNDARY_VALUE / 2.0f)
                {
                    return 10000.0f / ((float)BOUNDARY_VALUE / 2.0f) * value + minValue;
                }
                else
                {
                    return (maxValue - 10000) / ((float)BOUNDARY_VALUE / 2.0f) * (value - (float)BOUNDARY_VALUE / 2.0f) + 10000;
                }
            }
            else if (maxValue > 200000 && maxValue <= 2000000)
            {
                if (value <= (float)BOUNDARY_VALUE / 2.0f)
                {
                    return 10000.0f / ((float)BOUNDARY_VALUE / 2.0f) * value + minValue;
                }
                else if (value > (float)BOUNDARY_VALUE / 2.0f && value <= 3 * (float)BOUNDARY_VALUE / 4.0f)
                {
                    return (100000 - 10000) / ((float)BOUNDARY_VALUE / 4.0f) * (value - (float)BOUNDARY_VALUE / 2.0f) + 10000;
                }
                else
                {
                    return (maxValue - 100000) / ((float)BOUNDARY_VALUE / 4.0f) * (value - 3 * (float)BOUNDARY_VALUE / 4.0f) + 100000;
                }
            }
            else
            {
                if (value <= (float)BOUNDARY_VALUE / 2.0f)
                {
                    return 10000.0f / ((float)BOUNDARY_VALUE / 2.0f) * value + minValue;
                }
                else if (value > (float)BOUNDARY_VALUE / 2.0f && value <= 3 * (float)BOUNDARY_VALUE / 4.0f)
                {
                    return (100000 - 10000) / ((float)BOUNDARY_VALUE / 4.0f) * (value - (float)BOUNDARY_VALUE / 2.0f) + 10000;
                }
                else if (value > 3 * (float)BOUNDARY_VALUE / 4.0f && value <= 7 * (float)BOUNDARY_VALUE / 8.0f)
                {
                    return (1000000 - 100000) / ((float)BOUNDARY_VALUE / 8.0f) * (value - 3 * (float)BOUNDARY_VALUE / 4.0f) + 100000;
                }
                else
                {
                    return (maxValue - 1000000) / ((float)BOUNDARY_VALUE / 8.0f) * (value - 7 * (float)BOUNDARY_VALUE / 8.0f) + 1000000;
                }
            }
        }
        else
        {
            return value;
        }
    }

    /**
     * convert exposure time or gain value to the SeekBar Value
     **/
    public static double cameraValueToSeekBarConversion(double value, double maxValue, double minValue, int maxRangeValue, int minRangeValue)
    {
        if (maxValue > BOUNDARY_VALUE)
        {
            if (maxValue <= 20000)
            {
                return (float)(maxRangeValue - minRangeValue) / (maxValue - minValue) * (value - minValue);
            }
            else if (maxValue > 20000 && maxValue <= 200000)
            {
                if (value <= 10000)
                {
                    return ((float)BOUNDARY_VALUE / 2.0f - minRangeValue) / (10000 - minValue) * (value - minValue);
                }
                else
                {
                    return ((float)BOUNDARY_VALUE / 2.0f - minRangeValue) / (maxValue - 10000) * (value - 10000) + (float)BOUNDARY_VALUE / 2.0f;
                }
            }
            else if (maxValue > 200000 && maxValue <= 2000000)
            {
                if (value <= 10000)
                {
                    return ((float)BOUNDARY_VALUE / 2.0f - minRangeValue) / (10000 - minValue) * (value - minValue);
                }
                else if (value > 10000 && value <= 100000)
                {
                    return ((float)BOUNDARY_VALUE / 4.0f - minRangeValue) / (100000 - 10000) * (value - 10000) + (float)BOUNDARY_VALUE / 2.0f;
                }
                else
                {
                    return ((float)BOUNDARY_VALUE / 4.0f - minRangeValue) / (maxValue - 100000) * (value - 100000) + 3 * (float)BOUNDARY_VALUE / 4.0f;
                }
            }
            else
            {
                if (value <= 10000)
                {
                    return ((float)BOUNDARY_VALUE / 2.0f - minRangeValue) / (10000 - minValue) * (value - minValue);
                }
                else if (value > 10000 && value <= 100000)
                {
                    return ((float)BOUNDARY_VALUE / 4.0f - minRangeValue) / (100000 - 10000) * (value - 10000) + (float)BOUNDARY_VALUE / 2.0f;
                }
                else if (value > 100000 && value <= 1000000)
                {
                    return ((float)BOUNDARY_VALUE / 8.0f - minRangeValue) / (1000000 - 100000) * (value - 100000) + 3 * (float)BOUNDARY_VALUE / 4.0f;
                }
                else
                {
                    return ((float)BOUNDARY_VALUE / 8.0f - minRangeValue) / (maxValue - 1000000) * (value - 1000000) + 7 * (float)BOUNDARY_VALUE / 8.0f;
                }
            }
        }
        else
        {
            return value;
        }
    }

//    public static float[][] readCsvToArray(String filePath) {    List<float[]> rows = new ArrayList<>();
//        String line;
//
//        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
//            while ((line = br.readLine()) != null) {
//                String[] stringValues = line.split(",");
//                float[] floatValues = new float[stringValues.length];
//
//                for (int i = 0; i < stringValues.length; i++) {
//                    try {
//                        // Trim to remove any accidental whitespace before parsing
//                        floatValues[i] = Float.parseFloat(stringValues[i].trim());
//                    } catch (NumberFormatException e) {
//                        // Handle non-numeric data (like headers) by setting to 0 or skipping
//                        floatValues[i] = 0.0f;
//                    }
//                }
//                rows.add(floatValues);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//            return null;
//        }
//
//        // Convert the List<float[]> to a 2D float array
//        return rows.toArray(new float[rows.size()][]);
//    }



}
