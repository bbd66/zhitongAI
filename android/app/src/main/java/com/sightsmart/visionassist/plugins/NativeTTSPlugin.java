package com.sightsmart.visionassist.plugins;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import androidx.annotation.NonNull;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@CapacitorPlugin(name = "NativeTTS")
public class NativeTTSPlugin extends Plugin {

    private TextToSpeech tts;
    private boolean isInitialized = false;
    private boolean isInitializing = false;
    private String lastError = "";

    @Override
    public void load() {
        super.load();
        initializeTTS();
    }

    private void initializeTTS() {
        if (isInitializing || isInitialized) {
            return;
        }
        
        isInitializing = true;
        
        try {
            tts = new TextToSpeech(getContext(), status -> {
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        int result = tts.setLanguage(Locale.CHINA);
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e("NativeTTS", "中文语言包不可用，尝试使用系统默认语言");
                            result = tts.setLanguage(Locale.getDefault());
                        }
                        
                        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                            @Override
                            public void onStart(String utteranceId) {
                                Log.d("NativeTTS", "开始播报: " + utteranceId);
                            }

                            @Override
                            public void onDone(String utteranceId) {
                                Log.d("NativeTTS", "播报完成: " + utteranceId);
                            }

                            @Override
                            public void onError(String utteranceId) {
                                Log.e("NativeTTS", "播报错误: " + utteranceId);
                            }
                        });
                        
                        isInitialized = true;
                        lastError = "";
                        Log.d("NativeTTS", "TTS初始化成功");
                    } catch (Exception e) {
                        Log.e("NativeTTS", "TTS初始化异常", e);
                        lastError = e.getMessage();
                    }
                } else {
                    Log.e("NativeTTS", "TTS初始化失败，状态码: " + status);
                    lastError = "TTS引擎初始化失败，状态码: " + status;
                }
                isInitializing = false;
            });
        } catch (Exception e) {
            Log.e("NativeTTS", "创建TTS引擎失败", e);
            lastError = e.getMessage();
            isInitializing = false;
        }
    }

    @PluginMethod
    public void speak(PluginCall call) {
        String text = call.getString("text", "");
        String lang = call.getString("lang", "zh-CN");
        double rate = call.getDouble("rate", 1.0);
        double pitch = call.getDouble("pitch", 1.0);

        if (text.isEmpty()) {
            call.reject("文本内容不能为空");
            return;
        }

        // 如果正在初始化，等待完成
        if (isInitializing) {
            new Thread(() -> {
                int waitCount = 0;
                while (isInitializing && waitCount < 50) {
                    try {
                        Thread.sleep(100);
                        waitCount++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                if (!isInitialized) {
                    call.reject("TTS初始化失败: " + lastError);
                } else {
                    doSpeak(text, rate, pitch, call);
                }
            }).start();
            return;
        }

        if (!isInitialized) {
            call.reject("TTS尚未初始化: " + lastError);
            return;
        }

        doSpeak(text, rate, pitch, call);
    }
    
    private void doSpeak(String text, double rate, double pitch, PluginCall call) {
        try {
            if (tts == null) {
                call.reject("TTS引擎为空");
                return;
            }
            
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utterance_" + System.currentTimeMillis());

            tts.setSpeechRate((float) Math.max(0.1, Math.min(2.0, rate)));
            tts.setPitch((float) Math.max(0.1, Math.min(2.0, pitch)));

            int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
            
            if (result == TextToSpeech.SUCCESS) {
                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
            } else {
                call.reject("语音合成失败，错误码: " + result);
            }
        } catch (Exception e) {
            Log.e("NativeTTS", "speak error: " + e.getMessage(), e);
            call.reject("语音合成异常: " + e.getMessage());
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        if (tts != null) {
            tts.stop();
        }
        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void isSupported(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("value", isInitialized);
        ret.put("initializing", isInitializing);
        ret.put("error", lastError);
        call.resolve(ret);
    }
    
    @PluginMethod
    public void initialize(PluginCall call) {
        if (isInitialized) {
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("error", "");
            call.resolve(result);
            return;
        }
        
        if (isInitializing) {
            new Thread(() -> {
                int waitCount = 0;
                while (isInitializing && waitCount < 50) {
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
                result.put("error", isInitialized ? "" : lastError);
                call.resolve(result);
            }).start();
            return;
        }
        
        initializeTTS();
        
        new Thread(() -> {
            int waitCount = 0;
            while (isInitializing && waitCount < 50) {
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
            result.put("error", isInitialized ? "" : lastError);
            call.resolve(result);
        }).start();
    }

    @Override
    protected void handleOnDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.handleOnDestroy();
    }
}
