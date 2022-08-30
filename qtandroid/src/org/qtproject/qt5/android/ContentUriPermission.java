/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.qtproject.qt5.android;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;

// Almost re-imp UriPermission, but allows creation (for testing at least...)
public class ContentUriPermission {
    private final Uri mUri;
    private final int mModeFlags;

    public ContentUriPermission(Uri uri, boolean readPermission, boolean writePermission) {
        mUri = uri;
        mModeFlags = readPermission ?
                (writePermission ?
                        (Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        : Intent.FLAG_GRANT_READ_URI_PERMISSION)
                : 0;
    }

    public Uri getUri() {
        return mUri;
    }

    public boolean isReadPermission() {
        return (mModeFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0;
    }

    public boolean isWritePermission() {
        return (mModeFlags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0;
    }

    @NonNull
    @Override
    public String toString() {
        return "ContentUriPermission {uri=" + mUri + ", modeFlags=" + mModeFlags + "}";
    }
}
