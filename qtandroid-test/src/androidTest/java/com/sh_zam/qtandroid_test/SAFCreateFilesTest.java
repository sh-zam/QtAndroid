/*
 * SPDX-FileCopyrightText: 2021 Sharaf Zaman <sharafzaz121@gmail.com>
 *
 * SPDX-License-Identifier: MIT
 */

package com.sh_zam.qtandroid_test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.qtproject.qt5.android.SAFFileManager;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class SAFCreateFilesTest {
    Context ctx =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    Uri uri = ctx.getContentResolver().getPersistedUriPermissions().get(0).getUri();
    String uriStr = uri.toString();
    SAFFileManager manager = SAFFileManager.instance(ctx);


    @Test
    public void createFilesWithoutDuplicates() {
        ArrayList<String> documentUris = new ArrayList<>();
        ArrayList<Integer> fileDescriptors = new ArrayList<>();

        int numFiles = 20;
        for (int i = 0; i < numFiles; ++i) {
            String name = i + ".test";
            String path = "/" + name;
            documentUris.add(uriStr + path);
            fileDescriptors.add(manager.openFileDescriptor(uriStr + path, "rw"));
            assertTrue(fileDescriptors.get(fileDescriptors.size() - 1) > 0);
        }
        for (int i = 0; i < numFiles; ++i) {
            String name = i + ".test";
            String path = "/" + name;
            assertTrue(manager.exists(uriStr + path));
            assertTrue(manager.canWrite(uriStr + path));
            assertEquals(manager.getFileName(uriStr + path), name);
            assertFalse(manager.isDir(uriStr + path));

            assertTrue(manager.closeFileDescriptor(fileDescriptors.get(i)));
            assertTrue(manager.delete(documentUris.get(i)));

            assertFalse(manager.isDir(uriStr + path));
            assertFalse(manager.exists(uriStr + path));
        }
    }

    @Test
    public void filesWarmCache() {
        ArrayList<String> documentUris = new ArrayList<>();
        ArrayList<Integer> fileDescriptors = new ArrayList<>();

        int numFiles = 20;
        for (int i = 0; i < numFiles; ++i) {
            String name = i + ".test";
            String path = "/" + name;
            documentUris.add(uriStr + path);
            fileDescriptors.add(manager.openFileDescriptor(uriStr + path, "rw"));
            assertTrue(fileDescriptors.get(fileDescriptors.size() - 1) > 0);
        }

        for (int j = 0; j < 100; ++j) {
            long start_time = System.currentTimeMillis();
            for (int i = 0; i < numFiles; ++i) {
                String name = i + ".test";
                String path = "/" + name;
                assertTrue(manager.exists(uriStr + path));
                assertTrue(manager.canWrite(uriStr + path));
                assertTrue(manager.exists(uriStr + path));
                assertEquals(manager.getFileName(uriStr + path), name);
                assertFalse(manager.isDir(uriStr + path));
            }
            System.out.println("Iteration #" + j + ", time taken = " + (System.currentTimeMillis() - start_time) + "ms");
        }

        for (int i = 0; i < numFiles; ++i) {
            assertTrue(manager.closeFileDescriptor(fileDescriptors.get(i)));
            assertTrue(manager.delete(documentUris.get(i)));

            assertFalse(manager.exists(documentUris.get(i)));
        }
    }

    @Test
    public void nestedFileCreation() {
        nestedFileCreationFromRoot("//one/two/three/four /five/ six");
        nestedFileCreationFromRoot("//one/two/////three/four /////////////five/ six");

        // TODO(sh_zam): this fails expectedly, we should implement a failsafe in nearestTreeUri
        // nestedFileCreationFromRoot("/one%2Ftwo%2Fthree%2Ffour %2Ffive%2F six");

        assertFalse(manager.delete(uriStr + "/one/two/three/ four/five/six"));
        assertFalse(manager.delete(uriStr + "/one/two/three/four/five/ six"));
        assertTrue(manager.delete(uriStr + "/one/two/three/four /five/ six"));
        assertTrue(manager.delete(uriStr + "/one/two/three/four /five"));
        assertTrue(manager.delete(uriStr + "/one/two/three/four "));
        assertTrue(manager.delete(uriStr + "/one/two/three"));
        assertTrue(manager.delete(uriStr + "/one"));
    }

    public void nestedFileCreationFromRoot(String pathDir) {
        final int num = 20;
        ArrayList<Integer> fileDescriptors = new ArrayList<>();

        String contentUrl = uriStr + pathDir + "/";
        for (int i = 0; i < num; ++i) {
            String fileUrl = contentUrl + i + ".test";
            fileDescriptors.add(manager.openFileDescriptor(fileUrl, "rw"));
            assertTrue(fileDescriptors.get(fileDescriptors.size() - 1) > 0);
            assertTrue(manager.exists(fileUrl));
            assertFalse(manager.isDir(fileUrl));

            assertTrue(manager.closeFileDescriptor(fileDescriptors.get(fileDescriptors.size() - 1)));
            assert(manager.delete(fileUrl));
            assertFalse(manager.exists(fileUrl));
            assertFalse(manager.isDir(fileUrl));
        }
    }

    @Test
    public void renameFiles() {
        ArrayList<Integer> fileDescriptors = new ArrayList<>();

        int numFiles = 5;
        for (int i = 0; i < numFiles; ++i) {
            String name = i + ".test";
            String path = uriStr + "/" + name;

            fileDescriptors.add(manager.openFileDescriptor(path, "rw"));
            assertTrue(fileDescriptors.get(fileDescriptors.size() - 1) > 0);

            String newName = i + ".test-renamed";
            String newPath = uriStr + "/" + newName;

            rename(path, name, newPath, newName);
            rename(newPath, newName, path, name);
            rename(path, name, newPath, newName);

            assertTrue(manager.delete(newPath));

            assertFalse(manager.isDir(newPath));
            assertFalse(manager.exists(newPath));

            assertTrue(manager.closeFileDescriptor(fileDescriptors.get(i)));
        }
    }

    private void rename(String path, String name, String newPath, String newName) {
        assertTrue(manager.rename(path, newName));
        assertTrue(manager.exists(newPath));
        assertTrue(manager.canWrite(newPath));
        assertEquals(manager.getFileName(newPath), newName);
        assertFalse(manager.isDir(path));

        assertFalse(manager.exists(path));
        assertFalse(manager.canWrite(path));
        assertNotEquals(manager.getFileName(path), name);
        assertFalse(manager.isDir(path));
    }


    /* test the column size
    @Test
    public void sizeColumnTest() throws IOException {
        ArrayList<String> documentUris = new ArrayList<>();
        ArrayList<Integer> fileDescriptors = new ArrayList<>();

        int numFiles = 20;
        for (int i = 0; i < numFiles; ++i) {
            String name = i + ".test";
            String path = "/" + name;
            documentUris.add(uriStr + path);
            fileDescriptors.add(manager.openFileDescriptor(uriStr + path, "rw"));
            assertTrue(fileDescriptors.get(fileDescriptors.size() - 1) > 0);

            FileDescriptor fdObj = manager.fDescObj.getFileDescriptor();
            FileOutputStream output = new FileOutputStream(fdObj);
            output.write("AAAAAA".getBytes(StandardCharsets.UTF_8));
            output.close();

            assertEquals(manager.getSize(uriStr + path), 6);
            assertTrue(manager.delete(documentUris.get(i)));
        }
    }
     */
}
