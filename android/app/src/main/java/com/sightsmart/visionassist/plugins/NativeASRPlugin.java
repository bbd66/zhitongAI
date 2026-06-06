package com.sightsmart.visionassist.plugins;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.sightsmart.visionassist.AsrEngine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(name = "NativeASR")
public class NativeASRPlugin extends Plugin {

    private static final String TAG = "NativeASR";
    
    private AsrEngine asrEngine;
    private ExecutorService executorService;
    private AudioRecord audioRecord;
    private Thread recordThread;
    private boolean isRecording = false;
    private boolean isInitialized = false;
    private boolean isInitializing = false;
    
    // 累积录音数据（只在停止时识别一次）
    private java.util.ArrayList<Short> recordedAudio = new java.util.ArrayList<>();
    private final Object audioLock = new Object();

    @Override
    public void load() {
        super.load();
        try {
            executorService = Executors.newSingleThreadExecutor();
            asrEngine = new AsrEngine(getContext());
            
            // 应用启动时就开始初始化模型（后台线程）
            startInitialization();
        } catch (Exception e) {
            Log.e("NativeASR", "插件加载失败", e);
        }
    }
    
    private void startInitialization() {
        if (isInitialized || isInitializing) {
            return;
        }
        
        isInitializing = true;
        
        executorService.execute(() -> {
            try {
                boolean success = asrEngine.initialize();
                isInitialized = success;
                isInitializing = false;
                
                if (success) {
                    Log.d(TAG, "模型初始化成功");
                } else {
                    Log.e(TAG, "模型初始化失败: " + asrEngine.getLastError());
                }
            } catch (Exception e) {
                Log.e(TAG, "模型初始化异常", e);
                isInitializing = false;
            }
        });
    }

    @PluginMethod
    public void initialize(PluginCall call) {
        // 如果正在初始化，等待完成
        if (isInitializing) {
            // 等待初始化完成（最多等待10秒）
            new Thread(() -> {
                int waitCount = 0;
                while (isInitializing && waitCount < 100) {
                    try {
                        Thread.sleep(100);
                        waitCount++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                JSObject result = new JSObject();
                result.put("success", isInitialized);
                result.put("error", isInitialized ? "" : asrEngine.getLastError());
                call.resolve(result);
            }).start();
            return;
        }
        
        // 如果还没初始化，开始初始化
        if (!isInitialized) {
            executorService.execute(() -> {
                boolean success = asrEngine.initialize();
                isInitialized = success;
                
                JSObject result = new JSObject();
                result.put("success", success);
                result.put("error", success ? "" : asrEngine.getLastError());
                
                call.resolve(result);
            });
        } else {
            // 已经初始化过了
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("error", "");
            call.resolve(result);
        }
    }

    @PluginMethod
    public void isInitialized(PluginCall call) {
        JSObject result = new JSObject();
        result.put("value", isInitialized);
        result.put("initializing", isInitializing);
        call.resolve(result);
    }

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1001;
    private PluginCall pendingCall = null;

    @PluginMethod
    public void startListening(PluginCall call) {
        if (asrEngine == null) {
            call.reject("ASR引擎未创建");
            return;
        }

        if (!isInitialized) {
            call.reject("引擎未初始化");
            return;
        }

        if (isRecording) {
            call.reject("正在录音中");
            return;
        }

        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            pendingCall = call;
            ActivityCompat.requestPermissions(getActivity(), 
                    new String[]{Manifest.permission.RECORD_AUDIO}, 
                    REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }

        isRecording = true;
        
        executorService.execute(() -> {
            try {
                final int sampleRate = 16000;
                int tempBufferSize = AudioRecord.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                );
                
                if (tempBufferSize == AudioRecord.ERROR || tempBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "无法获取最小缓冲区大小");
                    isRecording = false;
                    call.reject("无法获取音频缓冲区大小");
                    return;
                }
                
                final int bufferSize = Math.max(tempBufferSize, 4096);

                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                );

                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord 初始化失败");
                    audioRecord.release();
                    audioRecord = null;
                    isRecording = false;
                    call.reject("音频录制器初始化失败");
                    return;
                }

                audioRecord.startRecording();

                recordThread = new Thread(() -> {
                    try {
                        short[] readBuffer = new short[bufferSize / 2];
                        
                        synchronized (audioLock) {
                            recordedAudio.clear();
                        }

                        while (isRecording) {
                            int read = audioRecord.read(readBuffer, 0, readBuffer.length);
                            if (read > 0 && isRecording) {
                                synchronized (audioLock) {
                                    for (int i = 0; i < read; i++) {
                                        recordedAudio.add(readBuffer[i]);
                                    }
                                }
                            } else if (read <= 0) {
                                Thread.sleep(10);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "录音异常", e);
                    } finally {
                        if (audioRecord != null) {
                            try { audioRecord.stop(); } catch (Exception ignored) {}
                            audioRecord.release();
                            audioRecord = null;
                        }
                    }
                });

                recordThread.start();

                JSObject result = new JSObject();
                result.put("success", true);
                call.resolve(result);

            } catch (Exception e) {
                Log.e(TAG, "启动录音失败", e);
                isRecording = false;
                call.reject(e.getMessage());
            }
        });
    }

    @PluginMethod
    public void stopListening(PluginCall call) {
        isRecording = false;
        
        if (recordThread != null) {
            try {
                recordThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            recordThread = null;
        }

        if (audioRecord != null) {
            try { 
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop(); 
                }
            } catch (Exception ignored) {}
            audioRecord.release();
            audioRecord = null;
        }

        // 在停止录音时进行一次完整识别
        String recognitionResult = "";
        synchronized (audioLock) {
            if (!recordedAudio.isEmpty()) {
                try {
                    short[] audioData = new short[recordedAudio.size()];
                    for (int i = 0; i < recordedAudio.size(); i++) {
                        audioData[i] = recordedAudio.get(i);
                    }
                    
                    if (asrEngine != null && asrEngine.isInitialized()) {
                        recognitionResult = asrEngine.recognize(audioData, 16000);
                        Log.d(TAG, "识别结果: " + recognitionResult);
                    } else {
                        Log.e(TAG, "ASR引擎未初始化，无法识别");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "识别失败", e);
                }
                recordedAudio.clear();
            } else {
                Log.d(TAG, "录音数据为空");
            }
        }

        JSObject result = new JSObject();
        result.put("success", true);
        result.put("text", recognitionResult);
        call.resolve(result);
    }

    @PluginMethod
    public void recognize(PluginCall call) {
        if (!isInitialized) {
            call.reject("引擎未初始化");
            return;
        }

        String base64Audio = call.getString("audio");
        int sampleRate = call.getInt("sampleRate", 16000);

        if (base64Audio == null || base64Audio.isEmpty()) {
            call.reject("音频数据为空");
            return;
        }

        executorService.execute(() -> {
            try {
                byte[] audioBytes = android.util.Base64.decode(base64Audio, android.util.Base64.DEFAULT);
                short[] pcmData = new short[audioBytes.length / 2];
                for (int i = 0; i < pcmData.length; i++) {
                    pcmData[i] = (short) ((audioBytes[i * 2] & 0xFF) | (audioBytes[i * 2 + 1] << 8));
                }

                String result = asrEngine.recognize(pcmData, sampleRate);

                JSObject resultObj = new JSObject();
                resultObj.put("text", result != null ? result : "");
                call.resolve(resultObj);

            } catch (Exception e) {
                Log.e(TAG, "识别异常", e);
                call.reject(e.getMessage());
            }
        });
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        stopListening(null);
        if (asrEngine != null) {
            asrEngine.release();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Override
    protected void handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.handleRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION && pendingCall != null) {
            PluginCall call = pendingCall;
            pendingCall = null;
            
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening(call);
            } else {
                call.reject("麦克风权限被拒绝");
            }
        }
    }
}
