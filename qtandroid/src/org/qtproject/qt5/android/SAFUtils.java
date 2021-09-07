package org.qtproject.qt5.android;

import android.database.Cursor;

public class SAFUtils {

    public static String getColumnValStringOrNull(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index == -1) {
            return null;
        }
        return cursor.getString(index);
    }

    public static Integer getColumnValIntegerOrDefault(Cursor cursor, String column, int defaultVal) {
        int index = cursor.getColumnIndex(column);
        if (index == -1) {
            return defaultVal;
        }
        return cursor.getInt(index);
    }

}
