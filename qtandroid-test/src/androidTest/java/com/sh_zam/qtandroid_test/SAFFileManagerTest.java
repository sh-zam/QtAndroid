package com.sh_zam.qtandroid_test;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.qtproject.qt5.android.SAFFileManager;

import static org.junit.Assert.*;

import java.lang.reflect.Array;
import java.util.ArrayList;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class SAFFileManagerTest {
    final String TAG = "SAFFileManagerTest";
    Context ctx =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    Uri uri = Uri.parse(ctx.getSharedPreferences("config", MODE_PRIVATE)
            .getString(MainActivity.KEYS[MainActivity.DIRECTORY_REQUEST_CODE], null));
    String uriStr = uri.toString();
    SAFFileManager manager = SAFFileManager.instance(ctx);

    @Test
    public void useAppContext() {
        // Context of the app under test.
        assertEquals("com.sh_zam.qtandroid_test", ctx.getPackageName());
    }

    @Test
    public void isDir() {
        Log.d(TAG, uri.toString());

        assertNotNull(uri);
        assertTrue(manager.isDir(uriStr));
        assertFalse(manager.isDir(uriStr + System.currentTimeMillis()));
    }
}