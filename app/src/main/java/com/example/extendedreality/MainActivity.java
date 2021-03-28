package com.example.extendedreality;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions;
import com.google.firebase.ml.modeldownloader.DownloadType;
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler;
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceImageLabelerOptions;
import com.google.firebase.ml.vision.objects.FirebaseVisionObject;
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetector;
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetectorOptions;
import com.google.mlkit.common.model.CustomRemoteModel;
import com.google.mlkit.common.model.LocalModel;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.linkfirebase.FirebaseModelSource;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
//import com.google.mlkit.vision.common.InputImage;
//import com.google.mlkit.vision.objects.DetectedObject;
//import com.google.mlkit.vision.objects.ObjectDetection;
//import com.google.mlkit.vision.objects.ObjectDetector;
//import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity  implements
        TextToSpeech.OnInitListener  {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final double MIN_OPENGL_VERSION = 3.0;
    ArFragment arFragment;
    private TextToSpeech tts;
    LocalModel localModel;
    CustomRemoteModel remoteModel;
    Interpreter interpreter;
    private final int REQ_CODE = 100;
    private  int[] imgIds;
    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!checkIsSupportedDeviceOrFinish(this)) {        return;    }
        setContentView(R.layout.activity_main);

         localModel =
                new LocalModel.Builder()
                        .setAssetFilePath("detect.tflite")
                        // or .setAbsoluteFilePath(absolute file path to model file)
                        // or .setUri(URI to model file)
                        .build();
        remoteModel =
                new CustomRemoteModel
                        .Builder(new FirebaseModelSource.Builder("ObjectTrained").build())
                        .build();
        CustomModelDownloadConditions conditions = new CustomModelDownloadConditions.Builder()
                .requireWifi()
                .build();
        FirebaseModelDownloader.getInstance()
                .getModel("ObjectTrained", DownloadType.LOCAL_MODEL, conditions)
                .addOnSuccessListener(new OnSuccessListener<CustomModel>() {
                    @Override
                    public void onSuccess(CustomModel model) {
                        // Download complete. Depending on your app, you could enable
                        // the ML feature, or switch from the local model to the remote
                        // model, etc.
                        File modelFile = model.getFile();
                        if (modelFile != null) {
                            interpreter = new Interpreter(modelFile);
                            Toast.makeText(getApplicationContext(),"Model Downloaded",Toast.LENGTH_LONG).show();
                        }
                    }
                });

        tts = new TextToSpeech(this, this);

        arFragment = (CustomArFragment) getSupportFragmentManager().findFragmentById(R.id.ar_fragment);
        arFragment.getPlaneDiscoveryController().hide();
        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQ_CODE: {
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    String voiceText= String.valueOf( result.get(0));
                    if(voiceText.toLowerCase().equals(currentPlace.toLowerCase())){

                        speakOut("Your Are Already in destination");
                    }
                   else if(voiceText.toLowerCase().equals("library")){
                       if(currentPlace.toLowerCase().equals("atm")) {
                           imgIds = new int[]{
                                   R.drawable.ic_straight,
                                   R.drawable.ic_right,
                                   R.drawable.ic_straight,
                                   R.drawable.ic_right
                           };
                           ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                           img.setAdapter(adapterView);
                           Timer timer = new Timer();
                           timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                           speakOut(" Go Straight and take Right then  go straight on right there will be your destination");

                           final Handler handler = new Handler();
                           handler.postDelayed(new Runnable() {
                               @Override
                               public void run() {
                                   //add your code here
                                   timer.cancel();
                                   alertDialog.cancel();
                               }
                           }, 5000);
                       }
                       else if(currentPlace.toLowerCase().equals("canteen")) {
                            imgIds = new int[]{
                                    R.drawable.ic_left,
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut("take left and Go Straight and take Right  there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                       else if(currentPlace.toLowerCase().equals("entrance")) {
                            imgIds = new int[]{
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right,
                                    R.drawable.ic_straight
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut(" Go Straight and take Right then  go straight  there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                       else if(currentPlace.toLowerCase().equals("s h block")) {
                            imgIds = new int[]{
                                    R.drawable.ic_left,
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut("Take left and Go Straight  on right there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                    }
                   else if(voiceText.toLowerCase().equals("canteen")){
                        if(currentPlace.toLowerCase().equals("atm")) {
                            imgIds = new int[]{
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right,
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut(" Go Straight and take Right then  go straight on right there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                        else if(currentPlace.toLowerCase().equals("library")) {
                            imgIds = new int[]{
                                    R.drawable.ic_left,
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut("take left and Go Straight and take Right  there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                        else if(currentPlace.toLowerCase().equals("entrance")) {
                            imgIds = new int[]{
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right,
                                    R.drawable.ic_straight
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut(" Go Straight and take Right then  go straight  there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                        else if(currentPlace.toLowerCase().equals("s h block")) {
                            imgIds = new int[]{
                                    R.drawable.ic_left,
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut("Take left and Go Straight  on right there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                    }
                    else if(voiceText.toLowerCase().equals("atm")){
                        if(currentPlace.toLowerCase().equals("library")) {
                            imgIds = new int[]{
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right,
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut(" Go Straight and take Right then  go straight on right there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                        else if(currentPlace.toLowerCase().equals("canteen")) {
                            imgIds = new int[]{
                                    R.drawable.ic_left,
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut("take left and Go Straight and take Right  there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                        else if(currentPlace.toLowerCase().equals("entrance")) {
                            imgIds = new int[]{
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right,
                                    R.drawable.ic_straight
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut(" Go Straight and take Right then  go straight  there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                        else if(currentPlace.toLowerCase().equals("s h block")) {
                            imgIds = new int[]{
                                    R.drawable.ic_left,
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut("Take left and Go Straight  on right there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                    }
                    else if(voiceText.toLowerCase().equals("entrance")){
                        if(currentPlace.toLowerCase().equals("atm")) {
                            imgIds = new int[]{
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right,
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut(" Go Straight and take Right then  go straight on right there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                        else if(currentPlace.toLowerCase().equals("canteen")) {
                            imgIds = new int[]{
                                    R.drawable.ic_left,
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut("take left and Go Straight and take Right  there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                        else if(currentPlace.toLowerCase().equals("library")) {
                            imgIds = new int[]{
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right,
                                    R.drawable.ic_straight
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut(" Go Straight and take Right then  go straight  there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                        else if(currentPlace.toLowerCase().equals("s h block")) {
                            imgIds = new int[]{
                                    R.drawable.ic_left,
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut("Take left and Go Straight  on right there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                    }
                    else if(voiceText.toLowerCase().equals("s h block")){
                        if(currentPlace.toLowerCase().equals("atm")) {
                            imgIds = new int[]{
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right,
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut(" Go Straight and take Right then  go straight on right there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                        else if(currentPlace.toLowerCase().equals("canteen")) {
                            imgIds = new int[]{
                                    R.drawable.ic_left,
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut("take left and Go Straight and take Right  there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                        else if(currentPlace.toLowerCase().equals("entrance")) {
                            imgIds = new int[]{
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right,
                                    R.drawable.ic_straight
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut(" Go Straight and take Right then  go straight  there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                        else if(currentPlace.toLowerCase().equals("library")) {
                            imgIds = new int[]{
                                    R.drawable.ic_left,
                                    R.drawable.ic_straight,
                                    R.drawable.ic_right
                            };
                            ImageAdapter adapterView = new ImageAdapter(this, imgIds);
                            img.setAdapter(adapterView);
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new SliderTimer(), 500, 1000);
                            speakOut("Take left and Go Straight  on right there will be your destination");

                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //add your code here
                                    timer.cancel();
                                    alertDialog.cancel();
                                }
                            }, 5000);
                        }
                    }


                }
                break;
            }
        }
    }
    Thread t = null;
    public  String detectImag="";
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void onUpdateFrame(FrameTime frameTime) {
        Frame frame = arFragment.getArSceneView().getArFrame();
        try {
              final Image image = frame.acquireCameraImage();
              ObjectDetection(image);
              image.close();
            } catch (NotYetAvailableException e) {
                e.printStackTrace();
            }


        Collection<AugmentedImage> augmentedImages = frame.getUpdatedTrackables(AugmentedImage.class);
        //Toast.makeText(this,"Update Started"+augmentedImages.size(),Toast.LENGTH_SHORT).show();
       // speakOut("Update Started");
        for (AugmentedImage augmentedImage : augmentedImages) {
            //Toast.makeText(this,"Checking with augmented Image",Toast.LENGTH_SHORT).show();
            //speakOut("Checking with augmented Image");
            if (augmentedImage.getTrackingState() == TrackingState.TRACKING) {
                if (augmentedImage.getName().equals("atm.jpg") && !isdetected ) {
                    Toast.makeText(this,"You are in ATM",Toast.LENGTH_LONG).show();
                    isdetected=true;
                    if(!detectImag.equals("atm")) {
                        detectImag = "atm";
                        speakOut("You are in ATM");
                    }
                    openDialog("ATM");
                }
                else  if (augmentedImage.getName().equals("canteen.jpg") && !isdetected ) {
                    Toast.makeText(this,"You are in Canteen",Toast.LENGTH_LONG).show();
                    isdetected=true;
                    if(!detectImag.equals("canteen")) {
                        detectImag="canteen";
                        speakOut("You are in Canteen");
                    }
                    openDialog("Canteen");
                }
                else if (augmentedImage.getName().equals("entrance.jpg") && !isdetected ) {
                    Toast.makeText(this,"You are in Entrance",Toast.LENGTH_LONG).show();
                    isdetected=true;
                    if(!detectImag.equals("entrance")) {
                        detectImag = "entrance";
                        speakOut("You are in Entrance");
                    }
                    openDialog("Entrance");
                }
                else if (augmentedImage.getName().equals("library.jpg") && !isdetected ) {
                    Toast.makeText(this,"You are in Library",Toast.LENGTH_LONG).show();
                    isdetected=true;
                    if(!detectImag.equals("library")) {
                        detectImag = "library";
                        speakOut("You are in Library");
                    }
                    openDialog("Library");
                }
                else if (augmentedImage.getName().equals("shblock.jpg") && !isdetected ) {
                    Toast.makeText(this,"You are in S H Block",Toast.LENGTH_LONG).show();
                    isdetected=true;
                    if(!detectImag.equals("s h block")) {
                        detectImag = "s h block";
                        speakOut("You are in S H Block");
                    }
                    openDialog("S H Block");
            }

            }
        }
    }
    boolean isdetected=false;
    public boolean setupAugmentedImagesDb(Config config, Session session) {
        AugmentedImageDatabase augmentedImageDatabase = new AugmentedImageDatabase(session);
        try(InputStream inputStream = getAssets().open("myimages.imgdb")){
            augmentedImageDatabase = AugmentedImageDatabase.deserialize(session, inputStream);
        } catch (IOException e) {
            Log.e(TAG, "IO exception loading augmented image database.", e);
            e.printStackTrace();
        }
        config.setAugmentedImageDatabase(augmentedImageDatabase);
        return true;
    }

    private Bitmap loadAugmentedImage(String url) {
        try (InputStream is = getAssets().open(url)) {
            return BitmapFactory.decodeStream(is);
        } catch (IOException e) {
            Toast.makeText(this,"IO Exception "+ e,Toast.LENGTH_LONG).show();
            Log.e("ImageLoad", "IO Exception", e);
        }

        return null;
    }
    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later");
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =  ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                                        .getDeviceConfigurationInfo()
                                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }

    return true;
    }
    private AlertDialog alertDialog = null;
    private  String currentPlace="";
    ViewPager img=null;
    public  void  openDialog(String str){
        currentPlace=str.toLowerCase();
        final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        // Set icon value.
        builder.setIcon(R.drawable.ic_launcher_background);
        // Set title value.
        builder.setTitle(str);
        builder.setCancelable(false);
        // Get custom login form view.
        final View loginFormView = getLayoutInflater().inflate(R.layout.transaction_options, null);
        // Set above view in alert dialog.
        builder.setView(loginFormView);
        img=(ViewPager)loginFormView.findViewById(R.id.viewPage);
        // Register button click listener.
        ImageButton registerButton = (ImageButton) loginFormView.findViewById(R.id.btnOptions);
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    // Close Alert Dialog.
                    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
                    intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Need to speak");
                    try {
                        startActivityForResult(intent, REQ_CODE);
                    } catch (ActivityNotFoundException a) {
                        Toast.makeText(getApplicationContext(),
                                "Sorry your device not supported",
                                Toast.LENGTH_SHORT).show();
                    }


                  //  alertDialog.cancel();
                }
                catch(Exception ex)
                {
                    isdetected=false;
                    Toast.makeText(getApplicationContext(),ex.getMessage(),Toast.LENGTH_SHORT).show();

                    ex.printStackTrace();
                }
            }
        });

        alertDialog = builder.create();
        alertDialog.show();
    }
    @Override
    public void onDestroy() {
        // Don't forget to shutdown tts!
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onInit(int status) {

        if (status == TextToSpeech.SUCCESS) {

            int result = tts.setLanguage(Locale.US);

            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "This Language is not supported");
            } else {

                speakOut("You are, WelCome");
            }

        } else {
            Log.e("TTS", "Initilization Failed!");
        }

    }

      private  void  ObjectDetection(Image image){
          byte[] nv21;
              // Get the three planes.
              ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
              ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
              ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

              int ySize = yBuffer.remaining();
              int uSize = uBuffer.remaining();
              int vSize = vBuffer.remaining();


              nv21 = new byte[ySize + uSize + vSize];

              //U and V are swapped
              yBuffer.get(nv21, 0, ySize);
              vBuffer.get(nv21, ySize, vSize);
              uBuffer.get(nv21, ySize + vSize, uSize);

              int width = image.getWidth();
              int height = image.getHeight();

              ByteArrayOutputStream out = new ByteArrayOutputStream();
              YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
              yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);
              byte[] byteArray = out.toByteArray();
              Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
          if(bitmap!=null){
              InputImage img = InputImage .fromBitmap(bitmap,0);

              ImageLabeler labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
              labeler.process(img)
                      .addOnSuccessListener(new OnSuccessListener<List<ImageLabel>>() {
                          @Override
                          public void onSuccess(List<ImageLabel> labels) {
                              // Task completed successfully
                              // ...
                              for (ImageLabel label : labels) {
                                  String text = label.getText();
                                  float confidence = label.getConfidence();
                                  int index = label.getIndex();
                                  Toast.makeText(getApplicationContext(),"Detected Image is "+text,Toast.LENGTH_LONG).show();
                              }
                          }
                      })
                      .addOnFailureListener(new OnFailureListener() {
                          @Override
                          public void onFailure(@NonNull Exception e) {
                              // Task failed with an exception
                              // ...
                              Toast.makeText(getApplicationContext(),e.getMessage(),Toast.LENGTH_LONG).show();
                          }
                      });

          }
      }
    private void speakOut(String data) {

        CharSequence text = data;

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null,"id1");
    }
    private class SliderTimer extends TimerTask {

        @Override
        public void run() {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    if (img.getCurrentItem() < imgIds.length - 1) {

                        img.setCurrentItem(img.getCurrentItem() + 1);
                    } else {
                        img.setCurrentItem(0);
                    }

                }
            });
        }
    }
}
