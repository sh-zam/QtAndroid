package com.sh_zam.qtandroid_test;

import android.content.Context;
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
    Uri uri = ctx.getContentResolver().getPersistedUriPermissions().get(0).getUri();
    String uriStr = ctx.getContentResolver().getPersistedUriPermissions().get(0).getUri().toString();
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