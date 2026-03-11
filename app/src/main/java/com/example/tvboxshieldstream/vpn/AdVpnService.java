package com.example.tvboxshieldstream.vpn;

import android.app.Notification;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.tvboxshieldstream.R;
import com.example.tvboxshieldstream.dns.DnsParser;
import com.example.tvboxshieldstream.dns.DnsResponseBuilder;
import com.example.tvboxshieldstream.filter.HostFilter;

import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdVpnService extends VpnService {

    private ParcelFileDescriptor vpnInterface = null;
    private Thread threadProcesador = null;
    private final ExecutorService dnsExecutor = Executors.newFixedThreadPool(4);
    private HostFilter hostFilter;

    public static boolean activo = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (vpnInterface != null) return START_STICKY;

        activo = true;
        crearCanalNotificacion();

        Notification notification = new NotificationCompat.Builder(this, "vpn_channel")
                .setContentTitle("TvBoxShieldStream Activo")
                .setContentText("Protegiendo tu conexión...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);

        // CORRECCIÓN: Cargar la lista ANTES de iniciar el motor
        new Thread(() -> {
            hostFilter = new HostFilter(this);
            configurarMotorDNS();
        }).start();

        return START_STICKY;
    }

    // 1. Modificamos la configuración para que NO capture todo el tráfico
    private void configurarMotorDNS() {
        try {

            SharedPreferences prefs = getSharedPreferences("ConfigShield", MODE_PRIVATE);
            String dns1 = prefs.getString("dns_primaria", "8.8.8.8");
            String dns2 = prefs.getString("dns_secundaria", "8.8.4.4");

            VpnService.Builder builder = new VpnService.Builder();
            builder.setSession("TvBoxShieldDNS")
                    .addAddress("10.0.0.1", 24)
                    .addDnsServer("10.0.0.1")
                    .setMtu(1500);

            builder.addRoute(dns1, 32);
            builder.addRoute(dns2, 32);
            builder.addRoute("::", 0);

            vpnInterface = builder.establish();

            if (vpnInterface != null) {
                iniciarProcesadorDNS();
            }

        } catch (Exception e) {
            Log.e("TVBoxShield", "Error en motor: " + e.getMessage());
        }
    }

    // 2. El procesador de paquetes UDP (El "Homenaje" a DNS66)
    private void procesarPaqueteDNS(byte[] datos, int len, FileOutputStream out) {
        try {

            if (len < 20) return;
            if (datos[9] == 17) {

                if (len < 24) return;
                int puertoDestino = ((datos[22] & 0xFF) << 8) | (datos[23] & 0xFF);
                int puertoOrigen = ((datos[20] & 0xFF) << 8) | (datos[21] & 0xFF);
                if (puertoDestino == 53) {
                    byte[] dnsPayload = Arrays.copyOfRange(datos, 28, len);

                    if (dnsPayload.length < 12) return;

                    if ((dnsPayload[2] & 0x80) != 0) return;

                    String dominio = DnsParser.parseDomain(dnsPayload);
                    if (dominio == null) return;
                    if (hostFilter != null && hostFilter.isBlocked(dominio)) {

                        Log.d("TVBoxShield", "🚫 BLOQUEADO: " + dominio);

                        byte[] dnsQuery = Arrays.copyOfRange(datos, 28, len);
                        byte[] fakeDns = DnsResponseBuilder.buildBlockedResponse(dnsQuery);

                        enviarRespuestaBloqueada(fakeDns, datos, out, puertoOrigen);

                        return;
                    }

                    // Si es un dominio limpio, lo reenviamos al DNS real
                    enviarAlDnsReal(datos, len, out, puertoOrigen);
                }

            }
        } catch (Exception e) {
            Log.e("TVBoxShield", "Error procesando: " + e.getMessage());
        }
    }

    private void enviarRespuestaBloqueada(byte[] dnsResponse, byte[] originalPacket,
                                          FileOutputStream out, int puertoCliente) {

        try {

            int dnsLen = dnsResponse.length;
            int totalLen = 28 + dnsLen;

            byte[] packet = new byte[totalLen];

            packet[0] = 0x45;
            packet[2] = (byte) (totalLen >> 8);
            packet[3] = (byte) (totalLen & 0xFF);
            packet[8] = 64;
            packet[9] = 17;

            System.arraycopy(originalPacket, 16, packet, 12, 4);
            System.arraycopy(originalPacket, 12, packet, 16, 4);

            calcularChecksumIP(packet);

            packet[20] = 0;
            packet[21] = 53;

            packet[22] = (byte) (puertoCliente >> 8);
            packet[23] = (byte) (puertoCliente & 0xFF);

            int udpLen = 8 + dnsLen;

            packet[24] = (byte) (udpLen >> 8);
            packet[25] = (byte) (udpLen & 0xFF);

            packet[26] = 0;
            packet[27] = 0;

            System.arraycopy(dnsResponse, 0, packet, 28, dnsLen);

            synchronized (out) {
                out.write(packet);
            }

        } catch (Exception e) {
            Log.e("TVBoxShield", "Error bloqueando DNS: " + e.getMessage());
        }
    }

    private void iniciarProcesadorDNS() {
        if (threadProcesador != null) threadProcesador.interrupt();

        threadProcesador = new Thread(() -> {
            try (FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
                 FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor())) {

                byte[] buffer = new byte[32767];
                while (!Thread.currentThread().isInterrupted()) {
                    int length = in.read(buffer);
                    if (length > 0) {
                        // Aquí es donde sucede la magia de DNS66:
                        // 1. Analizamos el paquete para ver si es una consulta DNS (UDP Puerto 53)
                        // 2. Si es un anuncio, NO lo dejamos pasar y enviamos una respuesta falsa
                        byte[] packet = Arrays.copyOf(buffer, length);
                        procesarPaqueteDNS(packet, length, out);
                    }
                }
            } catch (Exception e) {
                Log.e("TVBoxShield", "Procesador detenido");
            }
        });
        threadProcesador.start();
    }



    private void enviarAlDnsReal(byte[] datosOriginales, int len, FileOutputStream out, int puertoCliente) {
        dnsExecutor.execute(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setReuseAddress(true);
                socket.setSoTimeout(5000);

                // 1. Extraer Payload DNS (el paquete original tiene 20 bytes IP + 8 bytes UDP)
                int dnsPayloadLen = len - 28;
                if (dnsPayloadLen <= 0) return;
                byte[] dnsQuery = new byte[dnsPayloadLen];
                System.arraycopy(datosOriginales, 28, dnsQuery, 0, dnsPayloadLen);

                // 2. Enviar consulta al DNS real (Google)
                SharedPreferences prefs = getSharedPreferences("ConfigShield", MODE_PRIVATE);
                String dnsElegida = prefs.getString("dns_primaria", "8.8.8.8");
                InetAddress server = InetAddress.getByName(dnsElegida);
                DatagramPacket queryPacket = new DatagramPacket(dnsQuery, dnsQuery.length, server, 53);
                socket.send(queryPacket);

                byte[] respuestaBuffer = new byte[4096];
                DatagramPacket responsePacket = new DatagramPacket(respuestaBuffer, respuestaBuffer.length);
                socket.receive(responsePacket);

                int dnsResLen = responsePacket.getLength();
                int totalResLen = 28 + dnsResLen;
                byte[] paqueteCompleto = new byte[totalResLen];

                // 4. RECONSTRUCCIÓN DE CABECERA IP (20 bytes)
                paqueteCompleto[0] = 0x45; // Versión 4, Longitud 20
                paqueteCompleto[2] = (byte) (totalResLen >> 8);
                paqueteCompleto[3] = (byte) (totalResLen & 0xFF);
                paqueteCompleto[8] = 64;   // TTL
                paqueteCompleto[9] = 17;   // Protocolo UDP

                if (len < 28) return;

                System.arraycopy(datosOriginales, 16, paqueteCompleto, 12, 4);
                System.arraycopy(datosOriginales, 12, paqueteCompleto, 16, 4);

                // CALCULAR CHECKSUM IP (Obligatorio para Android/Linux)
                calcularChecksumIP(paqueteCompleto);

                // 5. RECONSTRUCCIÓN DE CABECERA UDP (8 bytes)
                paqueteCompleto[20] = 0;
                paqueteCompleto[21] = 53; // Source Port 53
                paqueteCompleto[22] = (byte) (puertoCliente >> 8);
                paqueteCompleto[23] = (byte) (puertoCliente & 0xFF);
                int udpLen = 8 + dnsResLen;
                paqueteCompleto[24] = (byte) (udpLen >> 8);
                paqueteCompleto[25] = (byte) (udpLen & 0xFF);
                // Checksum UDP (se puede dejar en 0, la mayoría de los stacks lo aceptan)
                paqueteCompleto[26] = 0;
                paqueteCompleto[27] = 0;

                // 6. CARGA ÚTIL DNS
                System.arraycopy(respuestaBuffer, 0, paqueteCompleto, 28, dnsResLen);

                // 7. ENVIAR AL TÚNEL
                synchronized (out) {
                    out.write(paqueteCompleto, 0, totalResLen);
                }
                Log.d("TVBoxShield", "DNS Resuelta y entregada: " + dnsResLen + " bytes");

            } catch (Exception e) {
                Log.e("TVBoxShield", "Error en Forwarder: " + e.getMessage());
            }
        });
    }

    // Método auxiliar para que el kernel de Android no descarte el paquete
    private void calcularChecksumIP(byte[] buf) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            if (i == 5) continue; // Saltamos el campo del checksum
            sum += ((buf[i * 2] & 0xFF) << 8) | (buf[i * 2 + 1] & 0xFF);
        }
        while ((sum >> 16) > 0) sum = (sum & 0xFFFF) + (sum >> 16);
        sum = ~sum;
        buf[10] = (byte) (sum >> 8);
        buf[11] = (byte) (sum & 0xFF);
    }

    @Override
    public void onDestroy() {
        activo = false;
        // 1. Detener el bucle principal de lectura
        if (threadProcesador != null) {
            threadProcesador.interrupt();
        }

        // 2. Apagar el pool de hilos de DNS para liberar memoria
        if (dnsExecutor != null) {
            dnsExecutor.shutdownNow();
        }

        // 3. Cerrar la interfaz del túnel
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }
        } catch (Exception e) {
            Log.e("TVBoxShield", "Error al cerrar vpnInterface: " + e.getMessage());
        }

        Log.d("TVBoxShield", "Servicio Destruido - Recursos liberados");
        super.onDestroy();
    }


    private void crearCanalNotificacion() {
        // Solo es necesario para Android 8.0 (Oreo) en adelante
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            String nombre = "Protección de Red";
            String descripcion = "Mantiene el escudo activo en segundo plano";
            int importancia = android.app.NotificationManager.IMPORTANCE_LOW;

            android.app.NotificationChannel canal = new android.app.NotificationChannel("vpn_channel", nombre, importancia);
            canal.setDescription(descripcion);

            // Registramos el canal en el sistema
            android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(canal);
            }
        }
    }
}
