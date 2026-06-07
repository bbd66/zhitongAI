package com.sightsmart.visionassist.plugins;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "Permission")
public class PermissionPlugin extends Plugin {
    
    public static final int REQUEST_PERMISSIONS = 1001;
    private PluginCall pendingCall;
    
    @PluginMethod
    public void requestNotificationPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 需要运行时申请通知权限
            int result = ContextCompat.checkSelfPermission(
                getContext(), 
                Manifest.permission.POST_NOTIFICATIONS
            );
            
            if (result == PackageManager.PERMISSION_GRANTED) {
                JSObject ret = new JSObject();
                ret.put("granted", true);
                call.resolve(ret);
            } else {
                pendingCall = call;
                ActivityCompat.requestPermissions(
                    getActivity(),
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_PERMISSIONS
                );
            }
        } else {
            // Android 13 以下不需要申请通知权限
            JSObject ret = new JSObject();
            ret.put("granted", true);
            call.resolve(ret);
        }
    }
    
    @PluginMethod
    public void checkNotificationPermission(PluginCall call) {
        boolean granted = true;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted = ContextCompat.checkSelfPermission(
                getContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED;
        }
        
        JSObject ret = new JSObject();
        ret.put("granted", granted);
        call.resolve(ret);
    }
    
    @Override
    protected void handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.handleRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_PERMISSIONS && pendingCall != null) {
            PluginCall call = pendingCall;
            pendingCall = null;
            
            boolean granted = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                granted = true;
            }
            
            JSObject ret = new JSObject();
            ret.put("granted", granted);
            call.resolve(ret);
        }
    }
}
