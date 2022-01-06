package com.sh_zam.qtandroid_test;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    public static final int DIRECTORY_REQUEST_CODE = 0;
    public static final int FILE_REQUEST_CODE = 1;
    public static final String[] KEYS = new String[] {"directory_tree", "file"};

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button mButton = findViewById(R.id.select_directory_button);
        Button selectFileButton = findViewById(R.id.select_file_button);

        mButton.setOnClickListener(view -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(i, DIRECTORY_REQUEST_CODE);
        });
        selectFileButton.setOnClickListener(view -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*");
            startActivityForResult(i, FILE_REQUEST_CODE);
        });
    }

    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode,
                                    @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == DIRECTORY_REQUEST_CODE || requestCode == FILE_REQUEST_CODE) {
                assert data != null;
                Uri uri = data.getData();
                if (uri == null) {
                    Log.wtf(TAG, "Uri returned is null!");
                    return;
                }
                getApplicationContext().getContentResolver()
                        .takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                writeConfig(requestCode, uri.toString());
            }
        } else {
            Log.wtf(TAG, "Result failed: " + resultCode);
        }
    }

    void writeConfig(int code, String uri) {
        assert (code >= 0 && code <= 1);
        SharedPreferences sharedPref = getSharedPreferences("config", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        try {
            sharedPref.getStringSet(KEYS[code], null);
        } catch (ClassCastException e) {
            editor.remove(KEYS[code]);
            editor.commit();
        }

        Set<String> originalSet = sharedPref.getStringSet(KEYS[code], null);
        HashSet<String> set;
        if (originalSet == null) {
            set = new HashSet<>();
        } else {
            set = new HashSet<>(originalSet);
        }
        set.add(uri);

        editor.putStringSet(KEYS[code], set);
        editor.apply();
    }

}