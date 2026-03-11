package com.sodavision.pylonandroid;

import android.graphics.Bitmap;

import com.basler.pylon.GrabResult;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Command that converts a grabResult to a Java bitmap and saves it to storage.
 **/
public class SaveImageCommand implements ICommand{
    final String                  m_Path;
    final Bitmap.CompressFormat   m_Format;
    final GrabResult              m_GrabResult;
    final LogTarget               m_Log;
    private void Log( LogTarget.LogLevel l, String logText ) {
        if( m_Log != null) {
            m_Log.Log(l, logText);
        }
    }
    
    /** Constructor.
     *  @param path Image save path without extension.
     *  @param format Compression format. Only jpeg and png are supported.
     *  @param grabResult A valid grabResult to be converted and saved.
     *  @param log Logger implementation can be null.
     **/
    SaveImageCommand(String path, Bitmap.CompressFormat format, GrabResult grabResult, LogTarget log) {
        m_Path = path;
        m_Format = format;
        m_GrabResult = grabResult;
        m_Log = log;
    }
    @Override
    public void discard() {
        Log( LogTarget.LogLevel.Info,"Discard job: " + m_Path);
        m_GrabResult.release();
    }
    @Override
    public void execute() {
        try {
            // Convert to bitmap and save bitmap to file stream.
            MainActivity.saveImage(m_Path, m_Format, m_GrabResult.convertToBitmap(), new MainActivity());
        }
        catch(FileNotFoundException e) {
            Log( LogTarget.LogLevel.Error,"File " + m_Path + " not found");
        }
        catch( IOException e) {
            Log( LogTarget.LogLevel.Error,"File " + m_Path + " io error " + e.getMessage());
        }
        finally {
            // Release the resources. This will requeue the buffer.
            m_GrabResult.release();
        }
    }

    public static float[] readCsvToArray(String filePath) {
        List<Float> allValues = new ArrayList<>();
        String line;

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            while ((line = br.readLine()) != null) {
                // Split by comma
                String[] stringValues = line.split(",");

                for (String val : stringValues) {
                    try {
                        // Trim and parse each individual float
                        allValues.add(Float.parseFloat(val.trim()));
                    } catch (NumberFormatException e) {
                        // Handle non-numeric data (headers/empty cells) by adding 0.0
                        allValues.add(0.0f);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        // Convert the List<Float> to a single flattened primitive float[]
        float[] res = new float[allValues.size()];
        for (int i = 0; i < allValues.size(); i++) {
            res[i] = allValues.get(i);
        }

        return res;
    }

//    public static float[] readCsvToArray(String filePath) {
//        String line;
//        float[] floatValues;
//        List<float[]> list = new ArrayList<>();
//        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
//            line = br.readLine();
//            while (line != null){
//                String[] stringValues = line.split(",");
//                floatValues = new float[stringValues.length];
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
//                list.add(floatValues);
//                line = br.readLine();
//            }
//
//
//        } catch (IOException e) {
//            e.printStackTrace();
//            return null;
//        }
//
//
//        // Convert the List<float[]> to a 2D float array
//        float[] res = new float[list.size()];
//        for (int i = 0; i < list.size(); i++) res[i] = list.get(i);
//        return res;
//    }
}


