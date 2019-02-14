package dzo.com.mymlproject;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity{
TextToSpeech textToSpeech;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int ttsLang = textToSpeech.setLanguage(Locale.US);

                    if (ttsLang == TextToSpeech.LANG_MISSING_DATA
                            || ttsLang == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "The Language is not supported!");
                    } else {
                        Log.i("TTS", "Language Supported.");
                    }
                    Log.i("TTS", "Initialization success.");
                    textToSpeech.setPitch(0.3f);
                    textToSpeech.setSpeechRate(0.8f);
                    textToSpeech.speak("Click Image to recognize text !",TextToSpeech.QUEUE_FLUSH,null);
                } else {
                    Toast.makeText(getApplicationContext(), "TTS Initialization failed!", Toast.LENGTH_SHORT).show();
                }
            }
        });


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater=this.getMenuInflater();
        inflater.inflate(R.menu.menu,menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.map:{
                startActivity(new Intent(this,MapsActivity.class));

                break;
            }
            case R.id.capture:{
                Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent,94);
                break;
            }
            case R.id.select:{
if(Build.VERSION.SDK_INT>Build.VERSION_CODES.M){
    if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},1);
    }else{
        selectImage();
    }
}else{
    selectImage();
}

                break;
            }
            case R.id.setting:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode==1&&grantResults[0]==PackageManager.PERMISSION_GRANTED){
            selectImage();
        }else{
            Toast.makeText(this, "Oops! Storage poermission denied !", Toast.LENGTH_SHORT).show();
        }
    }

    private void selectImage() {
        Intent intent=new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent,15);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ImageView imageView=findViewById(R.id.ivRecog);

        if(requestCode==94 && resultCode==RESULT_OK && data!=null){
            Bitmap bitmap=(Bitmap) Objects.requireNonNull(data.getExtras()).get("data");
            imageView.setImageBitmap(bitmap);
            if(bitmap!=null) {
                recogText(bitmap);
            }
        }
        if(requestCode==15&&resultCode==RESULT_OK&&data!=null){

            Uri selectedImage = data.getData();
            String[] filePathColumn = { MediaStore.Images.Media.DATA };

            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();

           Bitmap bitmap = BitmapFactory.decodeFile(picturePath);
            imageView.setImageBitmap(bitmap);
            if(bitmap!=null) {
                recogText(bitmap);
            }

        }
    }
private static  final SparseIntArray orientations=new SparseIntArray();
    static{
        orientations.append(Surface.ROTATION_0,90);
        orientations.append(Surface.ROTATION_90,0);
        orientations.append(Surface.ROTATION_180,270);
        orientations.append(Surface.ROTATION_270,180);
    }
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private int getRotationCompensation(String cameraId, Activity activity, Context context)
            throws CameraAccessException {
        // Get the device's current rotation relative to its "native" orientation.
        // Then, from the ORIENTATIONS table, look up the angle the image must be
        // rotated to compensate for the device's rotation.
        int deviceRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int rotationCompensation = orientations.get(deviceRotation);

        // On most devices, the sensor orientation is 90 degrees, but for some
        // devices it is 270 degrees. For devices with a sensor orientation of
        // 270, rotate the image an additional 180 ((270 + 270) % 360) degrees.
        CameraManager cameraManager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        int sensorOrientation = cameraManager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION);
        rotationCompensation = (rotationCompensation + sensorOrientation + 270) % 360;

        // Return the corresponding FirebaseVisionImageMetadata rotation value.
        int result;
        switch (rotationCompensation) {
            case 0:
                result = FirebaseVisionImageMetadata.ROTATION_0;
                break;
            case 90:
                result = FirebaseVisionImageMetadata.ROTATION_90;
                break;
            case 180:
                result = FirebaseVisionImageMetadata.ROTATION_180;
                break;
            case 270:
                result = FirebaseVisionImageMetadata.ROTATION_270;
                break;
            default:
                result = FirebaseVisionImageMetadata.ROTATION_0;
                Log.e("vsking", "Bad rotation value: " + rotationCompensation);
        }
        return result;
    }

    private void recogText(Bitmap bitmap) {
        FirebaseVisionImage fbImage=FirebaseVisionImage.fromBitmap(bitmap);
        FirebaseVisionTextRecognizer detecter= FirebaseVision.getInstance().getOnDeviceTextRecognizer();
        final Task<FirebaseVisionText> result=detecter.processImage(fbImage)
                .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                    @Override
                    public void onSuccess(FirebaseVisionText firebaseVisionText) {
                        Log.d("vsking:>",""+firebaseVisionText);

                        AlertDialog alertDialog=new AlertDialog.Builder(MainActivity.this).create();
                        alertDialog.setCancelable(true);
                        alertDialog.setTitle("vsking text learning");
                        alertDialog.setMessage(firebaseVisionText.getText());
                        alertDialog.show();
                        if(firebaseVisionText.getText().isEmpty()||firebaseVisionText.getText()==null){
                            textToSpeech.speak("Opps ! Unable to read text from this image, try next .", TextToSpeech.QUEUE_FLUSH, null);

                        }else {
                            textToSpeech.speak(firebaseVisionText.getText(), TextToSpeech.QUEUE_FLUSH, null);
                        }

                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d("vsking:>",""+e);
                        textToSpeech.speak("Unable torecognize text, try another image !",TextToSpeech.QUEUE_FLUSH,null);
                        Toast.makeText(MainActivity.this, "Unable torecognize text, try another image !", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        if(textToSpeech!=null){
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();

    }
}
