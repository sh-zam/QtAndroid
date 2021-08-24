/*
 * SPDX-FileCopyrightText: 2021 Sharaf Zaman <sharafzaz121@gmail.com>
 *
 * SPDX-License-Identifier: MIT
 */

package com.sh_zam.qtandroid_test;


import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.qtproject.qt5.android.SAFFileManager;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class SAFDirectoriesTest {
    Context ctx =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    Uri uri = ctx.getContentResolver().getPersistedUriPermissions().get(0).getUri();
    String uriStr = uri.toString();
    SAFFileManager manager = SAFFileManager.instance(ctx);


    ArrayList<String> documentUris = new ArrayList<>();

    @Test
    synchronized public void createDirectoriesOneByOne() {
        final int count = 15;
        ArrayList<String> dirUris = new ArrayList<>();

        String pathDir = "/";
        for (int i = 0; i < count; ++i) {
            pathDir = pathDir + i + "/";
            dirUris.add(uriStr + pathDir);
            final String finalPath = uriStr + pathDir;
            assertTrue(manager.mkdir(finalPath, true));
            assertTrue(manager.exists(finalPath));
            assertTrue(manager.canWrite(finalPath));
            assertTrue(manager.isDir(finalPath));
        }

        for (int i = count - 1; i >= 0; --i) {
            assertTrue(manager.delete(dirUris.get(i)));
            assertFalse(manager.exists(dirUris.get(i)));
            assertFalse(manager.isDir(dirUris.get(i)));
        }
    }

    @Test
    synchronized public void createDirectoriesInOneGo() {
        final int count = 15;

        String pathDir = "/";
        for (int i = 0; i < count; ++i) {
            pathDir = pathDir + i + "/";
        }

        final String finalPath = uriStr + pathDir;

        assertTrue(manager.mkdir(finalPath, true));
        assertTrue(manager.canWrite(finalPath));
        assertTrue(manager.exists(finalPath));
        assertTrue(manager.isDir(finalPath));

        assertTrue(manager.delete(uriStr + "/0"));
        assertFalse(manager.exists(uriStr + "/0"));
        assertFalse(manager.isDir(uriStr + "/0"));
    }
}
