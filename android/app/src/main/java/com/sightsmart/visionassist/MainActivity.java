package com.sightsmart.visionassist;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;
import com.sightsmart.visionassist.plugins.NativeASRPlugin;
import com.sightsmart.visionassist.plugins.NativeTTSPlugin;
import com.sightsmart.visionassist.plugins.FallDetectionPlugin;
import com.sightsmart.visionassist.plugins.PermissionPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NativeASRPlugin.class);
        registerPlugin(NativeTTSPlugin.class);
        registerPlugin(FallDetectionPlugin.class);
        registerPlugin(PermissionPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
