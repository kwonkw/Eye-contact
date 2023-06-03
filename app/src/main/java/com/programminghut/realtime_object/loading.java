package com.programminghut.realtime_object;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.programminghut.realtime_object.MainActivity;
import com.programminghut.realtime_object.R;

public class loading extends Activity {

    // 로딩 화면이 표시될 시간 (밀리초 단위)
    private static final long LOADING_DELAY = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loading);

        // 일정 시간 후에 다음 액티비티로 이동
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // 메인 액티비티로 이동하는 인텐트 생성
                Intent intent = new Intent(loading.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        }, LOADING_DELAY);
    }
}
