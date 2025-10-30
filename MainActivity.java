package com.example.classwork3b;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.classwork3b.R;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/// Classwork 3b
///
/// This Android application captures photos with the device camera and uses
/// Google Cloud Vision API to classify what's in the image. The app displays
/// the captured photo and shows the top 3 classification labels with their
/// confidence scores in a TextView.

public class MainActivity extends AppCompatActivity {

    /**
     * I am not submitting this with my API key for security reasons
     *
     * To test my app:
     * 1. Put in your API key from the google cloud
     * 2. Replace "YOUR_API_KEY_HERE" on line 61 with your actual key
     * 3. Sync and run the project
     */
    private static final String API_KEY = "YOUR_API_KEY_HERE";

    private File current;
    private Bitmap currentBitmap;
    private ImageView imageView;
    private TextView resultsTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //get references to UI elements
        imageView = findViewById(R.id.imageView);
        resultsTextView = findViewById(R.id.resultsTextView);

        //request the camera permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, 100);
        }
    }

    //launches camera, same function as cw3
    public void captureImage(View view) {
        File imageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        current = new File(imageDir, "IMG_" + timeStamp + ".jpg");

        Uri imageUri = FileProvider.getUriForFile(this,
                getApplicationContext().getPackageName() + ".fileprovider", current);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(intent, 1234);
    }

    //called when the camera activity returns a result, same as cw3
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1234 && resultCode == RESULT_OK) {
            //check if photo file exists
            if (current != null && current.exists()) {
                //load photo into bitmap
                currentBitmap = BitmapFactory.decodeFile(current.getAbsolutePath());

                //display bitmap in main imageview if loaded successfully
                if (currentBitmap != null) {
                    imageView.setImageBitmap(currentBitmap);
                    resultsTextView.setText("Classifying image...\nPlease wait.");

                    //Classify the image using Google Cloud Vision
                    classifyWithVision(currentBitmap);
                }
            }
        }
    }

    //sends image to Google Cloud Vision API for classification
    private void classifyWithVision(final Bitmap bitmap) {
        //run in background thread (need on a background thread not main UI thread)
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //convert bitmap to byte array (similar to how we saved to database in cw3)
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
                    byte[] imageBytes = baos.toByteArray();

                    //set up Google Vision API (from web)
                    //HttpTransport: handles HTTP communication with Google servers
                    HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
                    //JsonFactory: converts between Java objects and JSON format
                    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
                    //MOST IMPORTANT visionRequestInitializer, this adds our API key to all requests
                    VisionRequestInitializer requestInitializer = new VisionRequestInitializer(API_KEY);

                    // Build the Vision API client with all the components
                    Vision vision = new Vision.Builder(httpTransport, jsonFactory, null)
                            .setVisionRequestInitializer(requestInitializer)
                            .build();

                    //prepare the image for Vision API
                    Image visionImage = new Image();
                    visionImage.encodeContent(imageBytes);

                    //create feature request for label detection
                    Feature feature = new Feature();
                    feature.setType("LABEL_DETECTION");
                    feature.setMaxResults(10);

                    List<Feature> features = new ArrayList<>();
                    features.add(feature);

                    //create the request
                    AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();
                    annotateImageRequest.setImage(visionImage);
                    annotateImageRequest.setFeatures(features);

                    List<AnnotateImageRequest> requests = new ArrayList<>();
                    requests.add(annotateImageRequest);

                    BatchAnnotateImagesRequest batchRequest = new BatchAnnotateImagesRequest();
                    batchRequest.setRequests(requests);

                    //execute the request and get response
                    Vision.Images.Annotate annotate = vision.images().annotate(batchRequest);
                    annotate.setDisableGZipContent(true);
                    BatchAnnotateImagesResponse response = annotate.execute();

                    //get the labels from response
                    List<EntityAnnotation> labels = response.getResponses().get(0).getLabelAnnotations();

                    //format the results for display
                    final String displayText = formatLabels(labels);

                    //update UI on main thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            resultsTextView.setText(displayText);
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    //show error message on main thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            resultsTextView.setText("Error: " + e.getMessage() +
                                    "\n\nMake sure:\n• API Key is added\n• Vision API is enabled\n• Internet is connected");
                        }
                    });
                }
            }
        }).start();
    }

    //format the top 3 labels for display and takes the list of labels from Google and creates text
    private String formatLabels(List<EntityAnnotation> labels) {
        if (labels == null || labels.isEmpty()) {
            return "No labels detected in the image.";
        }

        String result = "Top 3 Classification Labels:\n\n";

        //display top 3 labels
        //math.min ensures we don't try to access more labels than what exists
        int count = Math.min(3, labels.size());
        for (int i = 0; i < count; i++) {
            //get the label at position i
            EntityAnnotation label = labels.get(i);
            //get the label name (e.g., "Car", "Dog", "Sky")
            String labelName = label.getDescription();
            //get confidence score (0.0 to 1.0) and convert to percentage with the * 100
            float confidence = label.getScore() * 100;

            //build result string with label number, name, and confidence
            result += (i + 1) + ". " + labelName + "\n";
            result += "   Confidence: " + String.format("%.1f", confidence) + "%\n\n";
        }

        return result;
    }

    //same as from classwork 3
    @Override
    protected void onDestroy() {
        super.onDestroy();
        //clean up
        if (current != null && current.exists()) {
            current.delete();
        }
    }
}