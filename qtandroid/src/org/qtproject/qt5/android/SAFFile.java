package org.qtproject.qt5.android;

import android.net.Uri;

import java.util.List;

public class SAFFile {
    private Uri baseUri;
    private List<String> segments;

    SAFFile(Uri baseUri, List<String> segments) {
        this.baseUri = baseUri;
        this.segments = segments;
    }

    List<String> getSegments() {
        return segments;
    }

    public Uri getBaseUri() {
        return baseUri;
    }
}
