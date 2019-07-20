package com.motionrecorder;

import android.util.Log;

public class LogUtils {

    /**
     * https://stackoverflow.com/a/48284047/122441
     * @param tag
     * @param theMsg
     */
    public static void iLong(String tag, String theMsg)
    {
        final int MAX_INDEX = 4000;
        final int MIN_INDEX = 3000;

        // String to be logged is longer than the max...
        if (theMsg.length() > MAX_INDEX)
        {
            String theSubstring = theMsg.substring(0, MAX_INDEX);
            int    theIndex = MAX_INDEX;

            // Try to find a substring break at a line end.
            theIndex = theSubstring.lastIndexOf('\n');
            if (theIndex >= MIN_INDEX)
            {
                theSubstring = theSubstring.substring(0, theIndex);
            }
            else
            {
                theIndex = MAX_INDEX;
            }

            // Log the substring.
            Log.i(tag, theSubstring);

            // Recursively log the remainder.
            iLong(tag, theMsg.substring(theIndex));
        }

        // String to be logged is shorter than the max...
        else
        {
            Log.i(tag, theMsg);
        }
    }

}
