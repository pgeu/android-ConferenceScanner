package eu.postgresql.android.conferencescanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;


public class AddConferenceActivity extends AppCompatActivity
    implements QRAnalyzer.QRNotificationReceiver {

    private static final int PERMSSION_REQUEST_CAMERA = 17;

    private PreviewView viewfinder;
    private Button scanbutton;
    private Button addurlbutton;
    private EditText addurl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_add_conference);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        viewfinder = findViewById(R.id.view_finder);
        scanbutton = findViewById(R.id.scanbutton);
        addurlbutton = findViewById(R.id.addurlbutton);
        addurl = findViewById(R.id.addurl);

        viewfinder.setVisibility(View.INVISIBLE);
        scanbutton.setOnClickListener(view -> {
                if (ContextCompat.checkSelfPermission(AddConferenceActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(AddConferenceActivity.this,
                            new String[]{Manifest.permission.CAMERA},
                            PERMSSION_REQUEST_CAMERA);
                    return;
                }

                StartCamera();
        });

        addurlbutton.setOnClickListener(view -> {
                ReturnWithUrl(addurl.getText().toString());
        });
        addurl.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    addurlbutton.setEnabled(MainActivity.GetConferenceUrlMatcher(addurl.getText().toString()).matches());
                }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            StopCamera();
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMSSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                /* Permission granted for camera, so start up the scanning */
                StartCamera();
            }
        }
    }

    private void StopCamera() {
        try {
            ProcessCameraProvider.getInstance(this).get().unbindAll();
        } catch (ExecutionException e) {
            Log.e("conferencescanner", "Unable to unbind");
        } catch (InterruptedException e) {
            Log.e("conferencescanner", "Interrupted in unbind");
        }
    }

    private void StartCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(new Runnable() {
                @Override
                public void run() {
                    ProcessCameraProvider cameraProvider = null;
                    try {
                        cameraProvider = cameraProviderFuture.get();
                    } catch (ExecutionException e) {
                        return;
                    } catch (InterruptedException e) {
                        return;
                    }

                    scanbutton.setEnabled(false);

                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(viewfinder.getSurfaceProvider());

                    viewfinder.setVisibility(View.VISIBLE);

                    ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                    analysis.setAnalyzer(ContextCompat.getMainExecutor(AddConferenceActivity.this), new QRAnalyzer(AddConferenceActivity.this));

                    CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                    cameraProvider.unbindAll();
                    cameraProvider.bindToLifecycle(AddConferenceActivity.this, cameraSelector, preview, analysis);
                }
            },
            ContextCompat.getMainExecutor(this)
            );
    }

    @Override
    public void OnQRCodeFound(String qrstring) {
        ReturnWithUrl(qrstring);
    }

    private void ReturnWithUrl(String url) {
        Intent i = new Intent();
        i.putExtra("url", url);
        setResult(RESULT_OK, i);

        finish();
    }
}
