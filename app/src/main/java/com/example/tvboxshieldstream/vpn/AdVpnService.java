package com.example.tvboxshieldstream.vpn;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Set;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileInputStream;

public class AdVpnService extends VpnService {
    private ParcelFileDescriptor vpnInterface = null;
    private Thread threadVaciado = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        configurarYTunelar();
        return START_STICKY;
    }

    private void configurarYTunelar() {
        new Thread(() -> {
            try {
                VpnService.Builder builder = new VpnService.Builder();

                // 1. Identidad de Red
                builder.setSession("SystemFramework")
                        .addAddress("10.0.0.1", 24)
                        .setMtu(1400)
                        .setBlocking(true);

                SharedPreferences prefs = getSharedPreferences("ConfigShield", Context.MODE_PRIVATE);
                String dns1 = prefs.getString("dns_primaria", "94.140.14.14");

                // 2. EL TRUCO: Declaramos que el DNS es nuestra propia IP virtual
                // Esto obliga al sistema a enviarnos las consultas DNS a nosotros
                builder.addDnsServer(dns1);
                builder.addDnsServer("8.8.8.8"); // Engañamos al sistema con una IP conocida

                // 3. CAPTURA AGRESIVA (Rutas de escape comunes)
                builder.addRoute("0.0.0.0", 0); // TODO el tráfico IPv4 debe entrar aquí

                // 4. BLOQUEO RADICAL DE IPv6
                // Si doubleclick carga, es 99% seguro que es por IPv6.
                // Esta línea "mata" la pila IPv6 para que deba usar la nuestra (IPv4)
                builder.addRoute("::", 0);

                if (vpnInterface != null) {
                    vpnInterface.close();
                }

                vpnInterface = builder.establish();

                if (vpnInterface != null) {
                    iniciarBucleVaciado();
                    new Handler(Looper.getMainLooper()).post(() -> {
                        Toast.makeText(getApplicationContext(), "ESCUDO TOTAL: " + dns1, Toast.LENGTH_SHORT).show();
                    });
                }

            } catch (Exception e) {
                Log.e("TVBoxShield", "Fallo: " + e.getMessage());
            }
        }).start();
    }
    private void iniciarBucleVaciado() {
        if (threadVaciado != null) threadVaciado.interrupt();

        threadVaciado = new Thread(() -> {
            // Buffer de tamaño medio para equilibrar velocidad y RAM
            try (FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor())) {
                byte[] paquete = new byte[16384];
                while (!Thread.interrupted()) {
                    int longitud = in.read(paquete);
                    if (longitud <= 0) break;
                    // Los paquetes mueren aquí sin procesar carga pesada
                }
            } catch (Exception e) {

                Log.e("TVBoxShield", "Túnel en reposo");
                try {
                    if (vpnInterface != null) {
                        vpnInterface.close();
                        vpnInterface = null;
                    }
                } catch (Exception ex) { /* Ignorar */ }
            }
        });
        threadVaciado.setPriority(Thread.MIN_PRIORITY); // Prioridad baja para no trabar el video de MXL
        threadVaciado.start();
    }

    private Set<String> cargarDominiosBloqueados() {
        Set<String> dominios = new HashSet<>();
        File archivo = new File(getFilesDir(), "hosts.txt");
        if (!archivo.exists()) return dominios;

        try (BufferedReader lector = new BufferedReader(new FileReader(archivo), 8192)) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("#")) continue;
                String[] partes = linea.split("\\s+");
                if (partes.length >= 2) {
                    dominios.add(partes[1].toLowerCase());
                } else if (partes.length == 1) {
                    dominios.add(partes[0].toLowerCase());
                }
            }
        } catch (Exception e) {
            Log.e("TVBoxShield", "Fallo en lectura: " + e.getMessage());
        }
        return dominios;
    }

    @Override
    public void onDestroy() {
        if (threadVaciado != null) threadVaciado.interrupt();
        try {
            if (vpnInterface != null) vpnInterface.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }
}