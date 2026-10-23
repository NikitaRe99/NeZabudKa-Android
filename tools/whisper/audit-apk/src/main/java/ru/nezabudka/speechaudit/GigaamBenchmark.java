package ru.nezabudka.speechaudit;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Self-targeted, offline audit. Reads only supplied files in its own directory.
 * No microphone, permissions, main application access, commands or network. */
public final class GigaamBenchmark extends Instrumentation {
    private Bundle arguments;
    private Activity activityContext;
    public void runFromActivity(Activity context, Bundle args) {
        activityContext = context;
        arguments = args == null ? new Bundle() : args;
        onStart();
    }
    private void complete(int code, Bundle result) {
        if (activityContext == null) finish(code, result);
        else android.util.Log.i("GigaamAudit", "Finished " + code + ": " + result);
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args; start(); }
    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
    private static long peakRssKiB() throws Exception {
        for (String line : read(new File("/proc/self/status")).split("\n")) {
            if (line.startsWith("VmHWM:")) return Long.parseLong(line.replaceAll("[^0-9]", ""));
        }
        return -1;
    }
    private static void write(File file, JSONObject value) throws Exception {
        Files.write(file.toPath(), value.toString(2).getBytes(StandardCharsets.UTF_8));
    }
    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = input.read(buffer)) >= 0) digest.update(buffer, 0, n);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format("%02x", b & 255));
        return hex.toString();
    }
    private static float[] audio(File file) throws Exception {
        ByteBuffer bytes = ByteBuffer.wrap(Files.readAllBytes(file.toPath())).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.getInt() != 0x46464952) throw new IllegalArgumentException("Expected RIFF");
        bytes.getInt();
        if (bytes.getInt() != 0x45564157) throw new IllegalArgumentException("Expected WAVE");
        boolean format = false;
        while (bytes.remaining() >= 8) {
            int id = bytes.getInt(), size = bytes.getInt(), start = bytes.position();
            if (size < 0 || size > bytes.remaining()) throw new IllegalArgumentException("Invalid WAV");
            if (id == 0x20746d66) {
                if (size < 16) throw new IllegalArgumentException("Invalid WAV format");
                int encoding = bytes.getShort(), channels = bytes.getShort(), rate = bytes.getInt();
                bytes.getInt(); bytes.getShort(); int bits = bytes.getShort();
                format = encoding == 1 && channels == 1 && rate == 16000 && bits == 16;
            } else if (id == 0x61746164) {
                if (!format || (size & 1) != 0) throw new IllegalArgumentException("Expected PCM16 mono 16kHz");
                float[] audio = new float[size / 2];
                for (int i = 0; i < audio.length; i++) audio[i] = bytes.getShort() / 32768f;
                return audio;
            }
            bytes.position(start + size + (size & 1));
        }
        throw new IllegalArgumentException("No WAV data");
    }
    private static JSONObject infer(OrtEnvironment env, OrtSession session, float[] audio,
                                    JSONArray vocabulary, ScheduledExecutorService scheduler) throws Exception {
        try (OnnxTensor wave = OnnxTensor.createTensor(env, FloatBuffer.wrap(audio), new long[]{1, audio.length});
             OnnxTensor length = OnnxTensor.createTensor(env, LongBuffer.wrap(new long[]{audio.length}), new long[]{1});
             OrtSession.RunOptions run = new OrtSession.RunOptions()) {
            Map<String, OnnxTensor> inputs = new HashMap<>(); inputs.put("audio", wave); inputs.put("length", length);
            ScheduledFuture<?> timeout = scheduler.schedule(() -> {
                try { run.setTerminate(true); } catch (Exception ignored) { }
            }, 30, TimeUnit.SECONDS);
            try {
                long started = SystemClock.elapsedRealtimeNanos();
                long cpuStarted = android.os.Process.getElapsedCpuTime();
                try (OrtSession.Result result = session.run(inputs, run)) {
                    double ms = (SystemClock.elapsedRealtimeNanos() - started) / 1e6;
                    long[] labels = ((long[][]) result.get(0).getValue())[0];
                    Object outputLength = result.get(1).getValue();
                    int frames = outputLength instanceof long[] ? (int) ((long[]) outputLength)[0] : ((int[]) outputLength)[0];
                    StringBuilder text = new StringBuilder(); long previous = -1;
                    for (int i = 0; i < Math.min(frames, labels.length); i++) {
                        long token = labels[i];
                        if (token != vocabulary.length() && token != previous) text.append(vocabulary.getString((int) token));
                        previous = token;
                    }
                    return new JSONObject().put("text", text.toString()).put("ms", ms)
                        .put("cpuMs", android.os.Process.getElapsedCpuTime() - cpuStarted);
                }
            } finally { timeout.cancel(false); }
        }
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        JSONObject report = new JSONObject(); File destination = null;
        Activity activity = null;
        try {
            Context context = activityContext == null ? getTargetContext() : activityContext;
            File directory = new File(context.getExternalFilesDir(null), "gigaam-test");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot create test directory");
            if ("prepare".equals(arguments.getString("mode"))) {
                result.putString("directory", directory.getAbsolutePath()); complete(-1, result); return;
            }
            activity = activityContext;
            String name = arguments.getString("model", "v3_ctc-waveform-int8.onnx");
            if (!name.matches("(?:v[123]_ctc-waveform-(?:int8|fp32)|gigaam-v[123]-ctc-int8|gigaam-v3-ctc-lite-(?:minmax|pointwise)-int8)\\.onnx")) throw new IllegalArgumentException("Unknown model");
            int threads = Integer.parseInt(arguments.getString("threads", "4"));
            if (threads < 1 || threads > Runtime.getRuntime().availableProcessors()) throw new IllegalArgumentException("Thread count");
            destination = new File(directory, name + ".threads" + threads + ".json");
            JSONObject metadata = new JSONObject(read(new File(directory, "metadata.json")));
            JSONArray vocabulary = metadata.getJSONArray("vocabulary");
            JSONArray corpus = new JSONArray(read(new File(directory, "corpus.json")));
            File model = new File(directory, name);
            String hash = sha256(model);
            if (!hash.equals(metadata.getJSONObject("hashes").getString(name))) throw new IllegalArgumentException("Model hash mismatch");
            JSONArray cases = new JSONArray();
            report.put("device", android.os.Build.MODEL).put("hardware", android.os.Build.HARDWARE)
                .put("model", name).put("sha256", hash).put("modelBytes", model.length())
                .put("threads", threads).put("cases", cases);
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                options.setIntraOpNumThreads(threads); options.setInterOpNumThreads(1);
                options.addConfigEntry("session.intra_op.allow_spinning", "0");
                options.addConfigEntry("session.inter_op.allow_spinning", "0");
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                long started = SystemClock.elapsedRealtimeNanos();
                try (OrtSession session = env.createSession(model.getAbsolutePath(), options)) {
                    report.put("loadMs", (SystemClock.elapsedRealtimeNanos() - started) / 1e6).put("pssAfterLoadKiB", Debug.getPss());
                    infer(env, session, audio(new File(directory, corpus.getJSONObject(0).getString("file"))), vocabulary, scheduler);
                    for (int i = 0; i < corpus.length(); i++) {
                        JSONObject entry = corpus.getJSONObject(i);
                        File file = new File(directory, entry.getString("file"));
                        if (!sha256(file).equals(entry.getString("sha256"))) throw new IllegalArgumentException("Audio hash mismatch");
                        float[] samples = audio(file); JSONArray runs = new JSONArray();
                        for (int r = 0; r < 2; r++) runs.put(infer(env, session, samples, vocabulary, scheduler));
                        cases.put(new JSONObject().put("id", entry.getString("id")).put("runs", runs));
                        write(destination, report);
                        Bundle progress = new Bundle(); progress.putString("case", entry.getString("id"));
                        progress.putString("text", runs.getJSONObject(0).getString("text"));
                        if (activityContext == null) sendStatus(0, progress);
                    }
                    report.put("pssAfterInferenceKiB", Debug.getPss());
                    report.put("peakRssKiB", peakRssKiB());
                }
            }
            report.put("status", "complete");
            write(destination, report);
            result.putString("report", destination.getAbsolutePath()); complete(-1, result);
        } catch (Exception e) {
            try {
                report.put("status", "failed").put("error", e.toString());
                if (destination != null) write(destination, report);
            } catch (Exception ignored) { }
            result.putString("error", e.toString()); complete(1, result);
        } finally {
            scheduler.shutdownNow();
            if (activity != null) {
                Activity completedActivity = activity;
                completedActivity.runOnUiThread(completedActivity::finish);
            }
        }
    }
}
