package com.example.tvboxshieldstream.vpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class AdVpnService extends VpnService {
    private static final String TAG = "TVBoxShieldVPN";
    private ParcelFileDescriptor vpnInterface = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Escudo de Protección activado!");

        // Aquí es donde configuramos la "tubería"
        configurarTunel();

        return START_STICKY;
    }

    private void configurarTunel() {
        VpnService.Builder builder = new VpnService.Builder();

        try {
            // Establecemos los parámetros básicos del túnel
            builder.setSession("TVBoxShield")
                    .addAddress("10.0.0.1", 24) // Dirección interna ficticia
                    .addDnsServer("94.140.14.14") // <--- ADGUARD DNS (Seguridad real)
                    .addRoute("0.0.0.0", 0);     // Captura todo el tráfico IPv4

            // Aquí es donde DNS66 bloquea IPv6 (simplemente no dándole ruta)
            // Al no añadir rutas IPv6, las apps se ven obligadas a usar IPv4 (filtrado)

            vpnInterface = builder.establish();
            Log.d(TAG, "Túnel establecido con éxito.");

        } catch (Exception e) {
            Log.e(TAG, "Error al crear el escudo: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Cerramos la tubería al apagar el escudo
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al cerrar el escudo.");
        }
    }
}