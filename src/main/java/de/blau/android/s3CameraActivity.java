package de.blau.android;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.IOException;

import de.blau.android.contract.Paths;
import de.blau.android.util.DateFormatter;
import de.blau.android.util.FileUtil;
import de.blau.android.util.Snack;

public class s3CameraActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_s3_camera);
        Button takePhotoBtn = findViewById(R.id.s3takePhoto);
//        takePhotoBtn.setOnClickListener(view -> startActivity(new Intent("android.media.action.IMAGE_CAPTURE")));

        takePhotoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.amazonaws.demo.s3transferutility");
            if (launchIntent != null) {
                startActivity(launchIntent);//null pointer check in case package name was not found
            }


            }
        });

    }

}