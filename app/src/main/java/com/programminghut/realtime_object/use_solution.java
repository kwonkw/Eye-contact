package com.programminghut.realtime_object;

import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class use_solution extends AppCompatActivity {

    private TextToSpeech textToSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.use_solution);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = textToSpeech.setLanguage(Locale.KOREAN);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "Language not supported");
                    } else {
                        String text = "EyE Contact는 시각 장애인을 위한 전방 장애물 알림 어플리케이션입니다.\n" +
                                "어플 실행 버튼을 누르시면 자동으로 화면 동영상이 재생이 되면서 전방에 놓여 있는 장애물들과 사라진 장애물들에 대해 음성으로 알려줍니다.\n" +
                                "어플 실행 버튼을 누르셨다면 휴대폰 화면을 가슴 쪽으로 놓은 뒤 보조장치에 휴대폰을 넣습니다.\n" +
                                "화장실을 가시거나 잠시 어플을 멈추고 싶은 경우, 홀드 버튼을 눌러 어플을 일시정지합니다.\n" +
                                "다시 어플을 사용하고 싶은 경우, 홀드 버튼을 눌러 화면을 켜시면 바로 사용하실 수 있습니다.";
                        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "testUtteranceId");
                    }
                } else {
                    Log.e("TTS", "Initialization failed");
                }
            }
        });

        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d("TTS", "onStart: " + utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d("TTS", "onDone: " + utteranceId);
            }

            @Override
            public void onError(String utteranceId) {
                Log.d("TTS", "onError: " + utteranceId);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
}
