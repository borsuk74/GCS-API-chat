package com.cloudspeechapi.demo;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;


import com.github.bassaer.chatmessageview.model.Message;
import com.github.bassaer.chatmessageview.view.MessageView;

import java.util.ArrayList;

/*
 * Copyright (C) 2017 The Android Open Source Project
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
 *
 * Created by Sujit Panda on 8/26/2017.
 */


public class SpeechConversation extends AppCompatActivity implements VoiceView.OnRecordListener {

    private static String TAG = "SpeechConversation";

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1;

    private TextView  mSpeechRecogText;
    private VoiceView mStartStopBtn;

    //private CloudSpeechService mCloudSpeechService;
    private MyCloudService mCloudSpeechService;
    private VoiceRecorder mVoiceRecorder;
    private boolean mIsRecording = false;

    // Resource caches
    private int mColorHearing;
    private int mColorNotHearing;
    private TextView mStatus;

    private String mSavedText;
    private Handler mHandler;
    private  ArrayList<Message> mMessages;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.speechlayout);
        initViews();
        //populateChat();
        mMessages = new ArrayList<>();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Prepare Cloud Speech API
        //bindService(new Intent(this, CloudSpeechService.class), mServiceConnection,
        bindService(new Intent(this, MyCloudService.class), mServiceConnection,
                BIND_AUTO_CREATE);

        // Start listening to voices
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecorder();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.RECORD_AUDIO)) {
            showPermissionMessageDialog();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    @Override
    public void onStop() {

        // Stop listening to voice
        stopVoiceRecorder();

        // Stop Cloud Speech API
        if (mCloudSpeechService != null) {
            mCloudSpeechService.removeListener(mCloudSpeechServiceListener);
            unbindService(mServiceConnection);
            mCloudSpeechService = null;
        }

        super.onStop();
    }

    private void initViews() {

        mSavedText = "Hello";
        mStartStopBtn = (VoiceView) findViewById(R.id.recordButton);
        mStartStopBtn.setOnRecordListener(this);

        //mUserSpeechText = (TextView) findViewById(R.id.userSpeechText);
        mSpeechRecogText = (TextView) findViewById(R.id.speechRecogText);
        mStatus = (TextView) findViewById(R.id.status);

        final Resources resources = getResources();
        final Resources.Theme theme = getTheme();
        mColorHearing = ResourcesCompat.getColor(resources, R.color.status_hearing, theme);
        mColorNotHearing = ResourcesCompat.getColor(resources, R.color.status_not_hearing, theme);

        //mUserSpeechText.setText(mSavedText);
        mHandler = new Handler(Looper.getMainLooper());
    }

    private void populateChat()
    {
        //User id
        int myId = 0;
        //User icon
        Bitmap myIcon = BitmapFactory.decodeResource(getResources(), R.drawable.face_2);
        //User name
        String myName = "Aleks";

        int yourId = 1;
        Bitmap yourIcon = BitmapFactory.decodeResource(getResources(), R.drawable.face_1);
        String yourName = "Emily";

        final User me = new User(myId, myName, myIcon);
        final User you = new User(yourId, yourName, yourIcon);


        ArrayList<Message> messages = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            Message message1 = new Message.Builder()
                    .setUser(me)
                    .setText(me.getName() + " " + i)
                    .setRight(true)
                    .build();
            Message message2 = new Message.Builder()
                    .setUser(you)
                    .setText(you.getName() + " " + i)
                    .setRight(false)
                    .build();
            messages.add(message1);
            messages.add(message2);
        }

        MessageView messageView = (MessageView) findViewById(R.id.message_view);
        messageView.init(messages);

    }

    private void addMessageToChat(String msgText)
    {
        //User id
        int myId = 0;
        //User icon
        Bitmap myIcon = BitmapFactory.decodeResource(getResources(), R.drawable.face_2);
        //User name
        String myName = "Aleks";

        final User me = new User(myId, myName, myIcon);

        //ArrayList<Message> messages = new ArrayList<>();
        Message message = new Message.Builder()
                .setUser(me)
                .setText(msgText)
                .setRight(true)
                .build();

        mMessages.add(message);

        MessageView messageView = (MessageView) findViewById(R.id.message_view);
        messageView.removeAll();
        messageView.init(mMessages);
        messageView.setSelection(messageView.getCount()-1);
        //messageView.setMessage(message);

    }
    //The expected format of the text here is: "1:phrase1; 2:phrase2 ..."
    private void addMultipleMessagesToChat(String msgText)
    {
        String [] speakersStr = msgText.split(";");
        Bitmap iconLeft = BitmapFactory.decodeResource(getResources(), R.drawable.face_2);
        Bitmap iconRight = BitmapFactory.decodeResource(getResources(), R.drawable.face_1);
        for(String spekersData : speakersStr)
        {
            String [] tagPhrase = spekersData.split(":");
            int tag = Integer.parseInt(tagPhrase[0].trim());
            String phrase = tagPhrase[1].trim();

            //User name
            String speakerName = "Speaker "+ tagPhrase[0];
            Bitmap icon = tag % 2 ==0 ? iconLeft : iconRight;
            final User usr = new User(tag, speakerName, icon);
            boolean side = tag % 2 ==0 ? true : false;
            Message message = new Message.Builder()
                    .setUser(usr)
                    .setText(phrase)
                    .setRight(side)
                    .build();

            mMessages.add(message);

        }

        MessageView messageView = (MessageView) findViewById(R.id.message_view);
        messageView.removeAll();
        messageView.init(mMessages);
        messageView.setSelection(messageView.getCount()-1);
        mSpeechRecogText.setText("");
        //messageView.setMessage(message);

    }

    //Aleks changed
    private final MyCloudService.Listener mCloudSpeechServiceListener = new MyCloudService.Listener() {
        @Override
        public void onSpeechRecognized(final String text, final boolean isFinal) {
            if (isFinal) {
                mVoiceRecorder.dismiss();
            }
            /*
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (isFinal) {
                        Log.d(TAG, "Final Response : " + text);
                        if (mSavedText.equalsIgnoreCase(text)) {
                            mSpeechRecogText.setTextColor(Color.GREEN);
                            mSpeechRecogText.setText(text);
                            addMessageToChat(text);
                        }
                    } else {
                        Log.d(TAG, "Non Final Response : " + text);
                        mSpeechRecogText.setTextColor(Color.RED);
                        mSpeechRecogText.setText(text);
                        mSavedText = text;//Aleks added in attempt to get correct color in the case of final response
                    }
                }
            });
            */
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (isFinal) {
                        Log.d(TAG, "Final Response : " + text);

                            //mSpeechRecogText.setTextColor(Color.GREEN);
                            //mSpeechRecogText.setText(text);
                            //We add message
                            addMultipleMessagesToChat(text);

                    } else {
                        Log.d(TAG, "Non Final Response : " + text);
                        mSpeechRecogText.setTextColor(Color.RED);
                        mSpeechRecogText.setText(text);
                        mSavedText = text;//Aleks added in attempt to get correct color in the case of final response
                    }
                }
            });


        }
    };

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            //mCloudSpeechService = CloudSpeechService.from(binder);
            mCloudSpeechService = MyCloudService.from(binder);
            mCloudSpeechService.addListener(mCloudSpeechServiceListener);
            mStatus.setVisibility(View.VISIBLE);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mCloudSpeechService = null;
        }

    };

    private final VoiceRecorder.Callback mVoiceCallback = new VoiceRecorder.Callback() {

        @Override
        public void onVoiceStart() {
            showStatus(true);
            if (mCloudSpeechService != null) {
                mCloudSpeechService.startRecognizing(mVoiceRecorder.getSampleRate());
            }
        }

        @Override
        public void onVoice(final byte[] buffer, int size) {
            if (mCloudSpeechService != null) {
                mCloudSpeechService.recognize(buffer, size);
            }

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    int amplitude = (buffer[0] & 0xff) << 8 | buffer[1];
                    double amplitudeDb3 = 20 * Math.log10((double)Math.abs(amplitude) / 32768);
                    float radius2 = (float) Math.log10(Math.max(1, amplitudeDb3)) * dp2px(SpeechConversation.this, 20);
                    Log.d("SUJIT","radius2 : " + radius2);
                    mStartStopBtn.animateRadius(radius2 * 10);
                }
            });
        }

        @Override
        public void onVoiceEnd() {
            showStatus(false);
            if (mCloudSpeechService != null) {
                mCloudSpeechService.finishRecognizing();
            }
        }

    };

    @Override
    public void onRecordStart() {
        startStopRecording();
    }

    @Override
    public void onRecordFinish() {
        startStopRecording();
    }

    private void startStopRecording() {

        Log.d(TAG, "# startStopRecording # : " + mIsRecording);
        if (mIsRecording) {
            mStartStopBtn.changePlayButtonState(VoiceView.STATE_NORMAL);
            stopVoiceRecorder();
        } else {
            mStartStopBtn.changePlayButtonState(VoiceView.STATE_RECORDING);
            startVoiceRecorder();
        }
    }

    private void startVoiceRecorder() {
        Log.d(TAG, "# startVoiceRecorder #");
        mIsRecording = true;
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
        }
        mVoiceRecorder = new VoiceRecorder(mVoiceCallback);
        mVoiceRecorder.start();
    }

    private void stopVoiceRecorder() {

        Log.d(TAG, "# stopVoiceRecorder #");
        mIsRecording = false;
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
            mVoiceRecorder = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (permissions.length == 1 && grantResults.length == 1
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecorder();
            } else {
                showPermissionMessageDialog();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void showPermissionMessageDialog() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage("This app needs to record audio and recognize your speech")
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                }).setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                    }
                }).create();

        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    private void showStatus(final boolean hearingVoice) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatus.setTextColor(hearingVoice ? mColorHearing : mColorNotHearing);
            }
        });
    }

    public static int dp2px(Context context, int dp) {
        int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context
                .getResources().getDisplayMetrics());
        return px;
    }
}
