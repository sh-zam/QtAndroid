/*
 * SPDX-FileCopyrightText: 2021 Sharaf Zaman <sharafzaz121@gmail.com>
 *
 * SPDX-License-Identifier: MIT
 */

package com.sh_zam.qtandroid_test;


import static android.content.Context.MODE_PRIVATE;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNull;
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
    Uri uri = Uri.parse(ctx.getSharedPreferences("config", MODE_PRIVATE)
            .getString(MainActivity.KEYS[MainActivity.DIRECTORY_REQUEST_CODE], null));
    String uriStr = uri.toString();
    SAFFileManager manager = SAFFileManager.instance(ctx);


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

    @Test
    public void renameDirectoriesParentToChild() {
        final int count = 15;

        String pathDir = "/";
        for (int i = 0; i < count; ++i) {
            pathDir = pathDir + i + "/";
        }

        final String finalPath = uriStr + pathDir;

        // manager.mCachedDocumentFiles.clear();
        assertTrue(manager.mkdir(finalPath, true));
        assertTrue(manager.isDir(finalPath));

        String curPath = uriStr + "/";
        String oldPath = curPath;
        String newPath = curPath;
        for (int i = 0; i < count; ++i) {
            oldPath = oldPath + i + "/";
            curPath = newPath + "/" + i;

            String newName = String.valueOf(i + 100);
            String curName = String.valueOf(i);
            newPath = newPath + "/" + newName;

            renameDirectory(curPath, newPath, newName);
            renameDirectory(newPath, curPath, curName);
            renameDirectory(curPath, newPath, newName);
        }
        // TODO(sh_zam): design of caching
        // assertEquals(16, manager.mCachedDocumentFiles.size());
        assertTrue(manager.delete(uriStr + "/100"));
    }

    @Test
    public void renameDirectoriesChildToParent() {
        final int count = 15;

        String pathDir = "";
        for (int i = 0; i < count; ++i) {
            pathDir = pathDir + "/" + i;
        }

        pathDir = uriStr + pathDir;
        // manager.mCachedDocumentFiles.clear();
        assertTrue(manager.mkdir(pathDir, true));
        assertTrue(manager.isDir(pathDir));

        for (int i = count - 1; i >= 0; --i) {
            String curPath = pathDir;
            String newName = String.valueOf(i + 100);
            String curName = String.valueOf(i);

            pathDir = pathDir.substring(0, pathDir.lastIndexOf("/"));
            String newPath = pathDir + "/" + newName;
            renameDirectory(curPath, newPath, newName);
            renameDirectory(newPath, curPath, curName);
            renameDirectory(curPath, newPath, newName);
        }
        // assertEquals(16, manager.mCachedDocumentFiles.size());
        assertTrue(manager.delete(uriStr + "/100"));
    }

    private void renameDirectory(String curPath, String newPath, String newName) {
        assertTrue(manager.exists(curPath));

        assertTrue(manager.rename(curPath, newName));
        assertTrue(manager.exists(newPath));
        assertTrue(manager.isDir(newPath));

        assertEquals(newName, manager.getFileName(newPath));
        assertNull(manager.getFileName(curPath));
        assertFalse(manager.exists(curPath));
    }
}
