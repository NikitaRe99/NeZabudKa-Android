package ru.nezabudka.speechaudit;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Signature-matched instrumentation: explicitly launched by adb for an audio audit.
 * Reads only the two known recording files; no microphone, DB, preferences or network. */
public final class AudioSnapshot extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context target = getTargetContext();
            File destination = new File(target.getExternalFilesDir(null), "speech-audit/" + System.currentTimeMillis());
            if (!destination.mkdirs()) throw new IllegalStateException("Cannot create audio snapshot directory");
            JSONObject report = new JSONObject();
            for (String name : new String[]{"last_raw.wav", "last_processed.wav"}) {
                File source = new File(target.getFilesDir(), "recordings/" + name);
                if (!source.isFile()) continue;
                MessageDigest hash = MessageDigest.getInstance("SHA-256");
                try (FileInputStream input = new FileInputStream(source);
                     FileOutputStream output = new FileOutputStream(new File(destination, name))) {
                    byte[] buffer = new byte[65536]; int n;
                    while ((n = input.read(buffer)) != -1) { output.write(buffer,0,n); hash.update(buffer,0,n); }
                }
                StringBuilder hex = new StringBuilder();
                for (byte b : hash.digest()) hex.append(String.format("%02x", b & 255));
                report.put(name, new JSONObject().put("sha256",hex.toString()).put("bytes",source.length()).put("modified",source.lastModified()));
            }
            try(FileOutputStream output = new FileOutputStream(new File(destination,"snapshot.json"))) {
                output.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            result.putString("snapshot", destination.getAbsolutePath()); finish(-1,result);
        } catch(Exception e) { result.putString("error",e.toString()); finish(1,result); }
    }
}
