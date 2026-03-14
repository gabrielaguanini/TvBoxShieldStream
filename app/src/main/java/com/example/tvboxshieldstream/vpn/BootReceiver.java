package com.example.tvboxshieldstream.vpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Verificamos que sea el evento de "Inicio de Sistema"
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d("TVBoxShield", "¡Sistema iniciado! Arrancando VPN automáticamente...");

            Intent serviceIntent = new Intent(context, AdVpnService.class);

            // En Android 8.0 (Oreo) o superior, los servicios en segundo plano
            // deben iniciarse como "Foreground"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}