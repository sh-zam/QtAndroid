/*
 * SPDX-FileCopyrightText: 2021 Sharaf Zaman <sharafzaz121@gmail.com>
 *
 * SPDX-License-Identifier: MIT
 */

package com.sh_zam.qtandroid_test;

import static android.content.Context.MODE_PRIVATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.qtproject.qt5.android.SAFFileManager;

@RunWith(AndroidJUnit4.class)
public class SAFFilesAccessTest {
    Context ctx =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    Uri uri = Uri.parse(ctx.getSharedPreferences("config", MODE_PRIVATE)
            .getString(MainActivity.KEYS[MainActivity.FILE_REQUEST_CODE], null));
    String uriStr = uri.toString();
    SAFFileManager manager = SAFFileManager.instance(ctx);

    @Test
    public void init() {
        assertFalse("Initialization invalid, try changing order of " +
                "opening the files: " + uriStr, uriStr.contains("tree"));
    }

    @Test
    public void existence() {
        assertTrue(manager.exists(uriStr));
        assertFalse(manager.isDir(uriStr));
        assertEquals(manager.getFileName(uriStr), "download.png");
    }

    @Test
    public void writable() {
        assertFalse(manager.isDir(uriStr));
        assertTrue(manager.canWrite(uriStr));
    }

    // @Test
    // public void rename() {
    //     String oldName = "download.png";
    //     String newName = "renamed-1.png";
    //     String renamedUri = uriStr.replace(oldName, newName);
    //     assertTrue(manager.rename(uriStr, newName));
    //     assertEquals(manager.getFileName(renamedUri), newName);
    //     assertTrue(manager.rename(renamedUri, oldName));
    //     assertEquals(manager.getFileName(uriStr), oldName);
    // }
}
