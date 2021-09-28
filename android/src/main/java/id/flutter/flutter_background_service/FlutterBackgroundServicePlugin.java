package id.flutter.flutter_background_service;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.service.ServiceAware;
import io.flutter.embedding.engine.plugins.service.ServicePluginBinding;
import io.flutter.plugin.common.JSONMethodCodec;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * FlutterBackgroundServicePlugin
 */
public class FlutterBackgroundServicePlugin extends BroadcastReceiver implements FlutterPlugin, MethodCallHandler, ServiceAware {
    private static final String TAG = "BackgroundServicePlugin";
    private static final List<FlutterBackgroundServicePlugin> _instances = new ArrayList<>();

    public FlutterBackgroundServicePlugin() {
        _instances.add(this);
    }

    private MethodChannel channel;
    private Context context;
    private BackgroundService service;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        this.context = flutterPluginBinding.getApplicationContext();
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this.context);
        localBroadcastManager.registerReceiver(this, new IntentFilter("id.flutter/background_service"));

        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "id.flutter/background_service", JSONMethodCodec.INSTANCE);
        channel.setMethodCallHandler(this);

        IntentFilter intentFilter = new IntentFilter("YouWillNeverKillMe");
        context.registerReceiver(new RestartServiceReceiver(), intentFilter);
    }

    public static void registerWith(Registrar registrar) {
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(registrar.context());
        final FlutterBackgroundServicePlugin plugin = new FlutterBackgroundServicePlugin();
        localBroadcastManager.registerReceiver(plugin, new IntentFilter("id.flutter/background_service"));

        final MethodChannel channel = new MethodChannel(registrar.messenger(), "id.flutter/background_service", JSONMethodCodec.INSTANCE);
        channel.setMethodCallHandler(plugin);
        plugin.channel = channel;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        String method = call.method;
        JSONObject arg = (JSONObject) call.arguments;

        try {

            if ("BackgroundService.start".equals(method)) {
                long callbackHandle = arg.getLong("handle");
                boolean isForeground = arg.getBoolean("is_foreground_mode");
                boolean autoStartOnBoot = arg.getBoolean("auto_start_on_boot");

                BackgroundService.setCallbackDispatcher(context, callbackHandle, isForeground, autoStartOnBoot);
                BackgroundService.enqueue(context);

                Intent intent = new Intent(context, BackgroundService.class);
                if (isForeground) {
                    ContextCompat.startForegroundService(context, intent);
                } else {
                    context.startService(intent);
                }

                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("sendData")) {
                for (FlutterBackgroundServicePlugin plugin : _instances) {
                    if (plugin.service != null) {
                        plugin.service.receiveData((JSONObject) call.arguments);
                        break;
                    }
                }

                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("isServiceRunning")) {
                ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                    if (BackgroundService.class.getName().equals(service.service.getClassName())) {
                        result.success(true);
                        return;
                    }
                }
                result.success(false);
                return;
            }

            result.notImplemented();
        } catch (Exception e) {
            result.error("100", "Failed read arguments", null);
            context.sendBroadcast(new Intent("YouWillNeverKillMe"));
            System.out.println("ConGauBeo has been killed.");
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);

        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this.context);
        localBroadcastManager.unregisterReceiver(this);
        context.sendBroadcast(new Intent("YouWillNeverKillMe"));
        System.out.println("ConGauBeo has been killed.");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == null) return;

        if (intent.getAction().equalsIgnoreCase("id.flutter/background_service")) {
            String data = intent.getStringExtra("data");
            try {
                JSONObject jData = new JSONObject(data);
                if (channel != null) {
                    channel.invokeMethod("onReceiveData", jData);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onAttachedToService(@NonNull ServicePluginBinding binding) {
        Log.d(TAG, "onAttachedToService");

        this.service = (BackgroundService) binding.getService();
    }

    @Override
    public void onDetachedFromService() {
        this.service = null;
        Log.d(TAG, "onDetachedFromService");
    }
}
