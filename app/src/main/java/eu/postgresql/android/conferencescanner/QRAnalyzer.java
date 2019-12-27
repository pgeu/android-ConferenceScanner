package eu.postgresql.android.conferencescanner;

import android.media.Image;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;

import java.util.List;

public class QRAnalyzer implements ImageAnalysis.Analyzer, OnSuccessListener<List<FirebaseVisionBarcode>> {
    public interface QRNotificationReceiver {
        void OnQRCodeFound(String qrstring);
    }

    private FirebaseVisionBarcodeDetector detector;
    private QRNotificationReceiver notificationReceiver;

    public QRAnalyzer(QRNotificationReceiver notificationReceiver) {
        this.notificationReceiver = notificationReceiver;

        FirebaseVisionBarcodeDetectorOptions options = (new FirebaseVisionBarcodeDetectorOptions.Builder())
                .setBarcodeFormats(FirebaseVisionBarcode.FORMAT_QR_CODE)
                .build();
        detector = FirebaseVision.getInstance().getVisionBarcodeDetector(options);
    }

    @Override
    public void analyze(ImageProxy image, int rotationDegrees) {
        if (image == null)
            return;

        Image i =  image.getImage();
        if (i == null)
            return;

        FirebaseVisionImage visionImage = FirebaseVisionImage.fromMediaImage(i, convertRotation(rotationDegrees));

        Task<List<FirebaseVisionBarcode>> task = detector.detectInImage(visionImage);
        task.addOnSuccessListener(this);
    }

    private int convertRotation(int degrees) {
        switch (degrees) {
            case 0:
                return FirebaseVisionImageMetadata.ROTATION_0;
            case 90:
                return FirebaseVisionImageMetadata.ROTATION_90;
            case 180:
                return FirebaseVisionImageMetadata.ROTATION_180;
            case 270:
                return FirebaseVisionImageMetadata.ROTATION_270;
        }
        return FirebaseVisionImageMetadata.ROTATION_0;
    }

    @Override
    public void onSuccess(List<FirebaseVisionBarcode> barcodes) {
        for (int i = 0; i < barcodes.size(); i++) {
            notificationReceiver.OnQRCodeFound(barcodes.get(i).getRawValue());
        }
    }
}
