package ru.nezabudka.speechaudit;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/** Keeps the offline benchmark visible so OEM background policies do not freeze it. */
public final class BenchmarkActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TextView text = new TextView(this);
        text.setText("Проверка GigaAM\n\nСравниваем скорость и точность на сохранённых записях.\n\nПриложение и ваши данные не меняются.");
        text.setTextSize(20); text.setGravity(Gravity.CENTER); text.setPadding(32, 32, 32, 32);
        setContentView(text);
        new Thread(() -> new GigaamBenchmark().runFromActivity(this, getIntent().getExtras()), "gigaam-audit").start();
    }
}
