package eu.postgresql.android.conferencescanner;

import android.media.Image;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;

class QRAnalyzer implements ImageAnalysis.Analyzer, OnSuccessListener<List<Barcode>>  {
    public interface QRNotificationReceiver {
        void OnQRCodeFound(String qrstring);
    }

    private final BarcodeScanner scanner;
    private final QRNotificationReceiver notificationReceiver;

    public QRAnalyzer(QRNotificationReceiver notificationReceiver) {
        this.notificationReceiver = notificationReceiver;

        BarcodeScannerOptions options = (new BarcodeScannerOptions.Builder())
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();

        scanner = BarcodeScanning.getClient(options);
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        Image i =  image.getImage();
        if (i == null)
            return;

        InputImage visionImage = InputImage.fromMediaImage(i, image.getImageInfo().getRotationDegrees());

        Task<List<Barcode>> task = scanner.process(visionImage);
        task.addOnSuccessListener(this);
        task.addOnCompleteListener(task1 -> image.close());
    }

    @Override
    public void onSuccess(List<Barcode> barcodes) {
        for (int i = 0; i < barcodes.size(); i++) {
            notificationReceiver.OnQRCodeFound(barcodes.get(i).getRawValue());
        }
    }
}
