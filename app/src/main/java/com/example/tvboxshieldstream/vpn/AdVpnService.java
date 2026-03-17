package com.example.tvboxshieldstream.vpn;

import android.app.Notification;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.example.tvboxshieldstream.R;
import com.example.tvboxshieldstream.filter.HostFilter;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.io.FileInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdVpnService extends VpnService {

    private ParcelFileDescriptor vpnInterface = null;
    private Thread threadProcesador = null;
    private final ExecutorService dnsExecutor = Executors.newFixedThreadPool(2);
    private HostFilter hostFilter;

    public static boolean activo = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        android.util.Log.d("TVBoxShield", "¡SERVICIO DESPERTÓ!");
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

        new Thread(() -> {
            hostFilter = new HostFilter(this);
            configurarMotorDNS();
        }).start();

        return START_STICKY;
    }

    private void configurarMotorDNS() {

        try {

            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }

            Builder builder = new Builder();

            builder.setSession("TVBoxShield_DNS");
            builder.setMtu(1500);

            // IP del túnel
            builder.addAddress("10.1.10.1", 32);

            // SOLO rutas DNS
            builder.addRoute("8.8.8.8", 32);
            builder.addRoute("8.8.4.4", 32);
            builder.addRoute("1.1.1.1", 32);
            builder.addRoute("1.0.0.1", 32);
            builder.addRoute("94.140.14.14", 32);

            // DNS del sistema
            builder.addDnsServer("1.1.1.1");
            builder.addDnsServer("8.8.8.8");

            // IPv4 + IPv6
            builder.allowFamily(OsConstants.AF_INET);
            builder.allowFamily(OsConstants.AF_INET6);

            try {
                builder.addDisallowedApplication("com.example.tvboxshieldstream");
            } catch (Exception ignored) {}

            vpnInterface = builder.establish();

            if (vpnInterface != null) {

                Log.d("TVBoxShield", "VPN establecida");

                new Thread(this::tunLoop).start();
            }

        } catch (Exception e) {
            Log.e("TVBoxShield", "Error configurando VPN", e);
        }
    }
    private void iniciarProcesadorDNS() {
        new Thread(() -> {
            // Usamos el descriptor de archivo de la interfaz VPN
            FileInputStream in = new FileInputStream(this.vpnInterface.getFileDescriptor());
            ByteBuffer buffer = ByteBuffer.allocate(32767);

            try {
                while (!Thread.interrupted() && vpnInterface != null) {
                    int length = in.read(buffer.array());

                    if (length > 0) {
                        // AQUÍ LLEGAN LOS DATOS
                        Log.d("TVBoxShield", "📥 Paquete capturado! Tamaño: " + length + " bytes");

                    /* NOTA: Aquí recibes paquetes IP completos.
                       Para "ver" la URL (ej: api.strem.io), deberías parsear
                       la cabecera UDP y el protocolo DNS.
                    */

                        buffer.clear();
                    }
                    Thread.sleep(10); // Evitar consumo excesivo de CPU
                }
            } catch (Exception e) {
                Log.e("TVBoxShield", "Error en lectura: " + e.getMessage());
            }
        }).start();
    }
    private void tunLoop() {

        try (
                FileInputStream inVpn = new FileInputStream(vpnInterface.getFileDescriptor());
                FileOutputStream outVpn = new FileOutputStream(vpnInterface.getFileDescriptor())
        ) {

            byte[] buffer = new byte[32767];

            while (!Thread.currentThread().isInterrupted() && vpnInterface != null) {

                int length = inVpn.read(buffer);

                if (length <= 0) continue;

                // Solo IPv4
                if ((buffer[0] & 0xF0) != 0x40) continue;

                int protocol = buffer[9] & 0xFF;
                int ipHeaderLength = (buffer[0] & 0x0F) * 4;

                if (protocol == 17) {

                    int dstPort = ((buffer[ipHeaderLength + 2] & 0xFF) << 8)
                            | (buffer[ipHeaderLength + 3] & 0xFF);

                    if (dstPort == 53) {

                        byte[] dnsPacket = Arrays.copyOf(buffer, length);

                        dnsExecutor.execute(() ->
                                procesarPaqueteDNS(dnsPacket, length, outVpn)
                        );

                        continue;
                    }
                }

                // IMPORTANTE: reenviar tráfico no DNS
                synchronized (outVpn) {
                    outVpn.write(buffer, 0, length);
                    outVpn.flush();
                }
            }

        } catch (Exception e) {
            Log.e("TVBoxShield", "tunLoop error", e);
        }
    }
    private void procesarPaqueteDNS(byte[] paqueteCompleto, int length, FileOutputStream outVpn) {
        try {
            // 1. Validación de tamaño mínimo (IP 20 bytes + UDP 8 bytes)
            if (length < 28) return;

            int ipHeaderLength = (paqueteCompleto[0] & 0x0F) * 4;
            int dnsOffset = ipHeaderLength + 8;

            if (dnsOffset >= length) return;

            // Extraemos solo la parte DNS del paquete capturado
            byte[] dnsPayload = Arrays.copyOfRange(paqueteCompleto, dnsOffset, length);

            // 2. Parsear el dominio solicitado
            String dominio = com.example.tvboxshieldstream.dns.DnsParser.parseDomain(dnsPayload);

            if (dominio == null || dominio.isEmpty()) return;

            // 3. LÓGICA DE FILTRADO
            if (hostFilter.isBlocked(dominio)) {
                Log.d("TVBoxShield", "🚫 BLOQUEADO: " + dominio);

                // Extraemos el query limpio (Header + Pregunta) para construir una respuesta válida
                byte[] queryLimpio = com.example.tvboxshieldstream.dns.DnsParser.extraerQueryCompleto(dnsPayload);

                if (queryLimpio != null) {
                    // Creamos la respuesta DNS que apunta a 0.0.0.0
                    byte[] dnsBloqueado = com.example.tvboxshieldstream.dns.DnsResponseBuilder.buildBlockedResponse(queryLimpio);

                    // Construimos el paquete IP/UDP completo con las cabeceras invertidas
                    byte[] respuestaFinal = com.example.tvboxshieldstream.vpn.PaqueteConstructor.crearRespuestaIP(paqueteCompleto, dnsBloqueado);

                    if (respuestaFinal != null) {
                        synchronized (outVpn) {
                            outVpn.write(respuestaFinal);
                            outVpn.flush();
                        }
                    }
                }
            } else {
                // 4. LÓGICA DE PERMITIDO (Consulta Real)
                // Log opcional para monitorear el tráfico de Stremio
                Log.d("TVBoxShield", "⚡ PERMITIDO: " + dominio);

                // Consultamos al DNS real (Google/Cloudflare)
                byte[] respuestaReal = consultarDNSReal(paqueteCompleto, length);

                if (respuestaReal != null) {
                    synchronized (outVpn) {
                        outVpn.write(respuestaReal);
                        outVpn.flush();
                    }
                }
            }
        } catch (Exception e) {
            Log.e("TVBoxShield", "💥 Error crítico procesando paquete DNS", e);
        }
    }

    private byte[] consultarDNSReal(byte[] datosVpn, int length) {

        DatagramSocket socket = null;

        try {

            int ipHeaderLength = (datosVpn[0] & 0x0F) * 4;
            int dnsOffset = ipHeaderLength + 8;

            if (length <= dnsOffset) return null;

            byte[] dnsPregunta = Arrays.copyOfRange(datosVpn, dnsOffset, length);

            socket = new DatagramSocket();

            protect(socket);

            socket.setSoTimeout(2000);

            InetAddress dnsServer = InetAddress.getByName("1.1.1.1");

            DatagramPacket out =
                    new DatagramPacket(dnsPregunta, dnsPregunta.length, dnsServer, 53);

            socket.send(out);

            byte[] buffer = new byte[1024];

            DatagramPacket in =
                    new DatagramPacket(buffer, buffer.length);

            socket.receive(in);

            byte[] respuesta =
                    Arrays.copyOf(buffer, in.getLength());

            return PaqueteConstructor.crearRespuestaIP(datosVpn, respuesta);

        } catch (Exception e) {

            Log.e("TVBoxShield", "DNS real error", e);

            return null;

        } finally {

            if (socket != null) socket.close();
        }
    }

    private void crearCanalNotificacion() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel canal = new android.app.NotificationChannel(
                    "vpn_channel", "Protección", android.app.NotificationManager.IMPORTANCE_LOW);
            android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(canal);
        }
    }

    @Override
    public void onDestroy() {
        activo = false;
        if (threadProcesador != null) threadProcesador.interrupt();
        super.onDestroy();
    }
}