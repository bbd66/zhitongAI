package com.sightsmart.visionassist.plugins;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.sightsmart.visionassist.falldetector.service.FallDetectionService;

@CapacitorPlugin(name = "FallDetection")
public class FallDetectionPlugin extends Plugin {
    
    private BroadcastReceiver fallReceiver;
    
    @Override
    public void load() {
        super.load();
        // 使用LocalBroadcastManager注册广播接收器（更可靠）
        fallReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(FallDetectionService.ACTION_RESULT)) {
                    boolean isFall = intent.getBooleanExtra(FallDetectionService.EXTRA_IS_FALL, false);
                    float score = intent.getFloatExtra(FallDetectionService.EXTRA_SCORE, 0);
                    
                    android.util.Log.d("FallDetectionPlugin", "收到摔倒检测广播: isFall=" + isFall + ", score=" + score);
                    
                    JSObject ret = new JSObject();
                    ret.put("isFall", isFall);
                    ret.put("score", score);
                    notifyListeners("fallDetected", ret);
                }
            }
        };
        
        IntentFilter filter = new IntentFilter(FallDetectionService.ACTION_RESULT);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(fallReceiver, filter);
    }
    
    @PluginMethod
    public void startDetection(PluginCall call) {
        android.util.Log.d("FallDetectionPlugin", "启动摔倒检测服务");
        Intent intent = new Intent(getContext(), FallDetectionService.class);
        getContext().startForegroundService(intent);
        JSObject result = new JSObject();
        result.put("success", true);
        call.resolve(result);
    }
    
    @PluginMethod
    public void stopDetection(PluginCall call) {
        android.util.Log.d("FallDetectionPlugin", "停止摔倒检测服务");
        Intent intent = new Intent(getContext(), FallDetectionService.class);
        getContext().stopService(intent);
        JSObject result = new JSObject();
        result.put("success", true);
        call.resolve(result);
    }
    
    @Override
    protected void handleOnDestroy() {
        if (fallReceiver != null) {
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(fallReceiver);
        }
        super.handleOnDestroy();
    }
}
