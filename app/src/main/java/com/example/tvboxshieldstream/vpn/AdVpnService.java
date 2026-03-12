package com.example.tvboxshieldstream.vpn;

import android.app.Notification;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.tvboxshieldstream.R;
import com.example.tvboxshieldstream.dns.DnsParser;
import com.example.tvboxshieldstream.dns.DnsResponseBuilder;
import com.example.tvboxshieldstream.filter.HostFilter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
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


// 1. Modificamos el método principal
private void configurarMotorDNS() {
    SharedPreferences prefs = getSharedPreferences("ConfigShield", MODE_PRIVATE);
    String dnsPrimario = prefs.getString("dns_primaria", "8.8.8.8");
    String dnsSecundario = prefs.getString("dns_secundaria", "8.8.4.4");

    // 1. LIMPIEZA: Cerramos cualquier interfaz previa para evitar "Bad address" por conflicto de IP
    if (vpnInterface != null) {
        try {
            vpnInterface.close();
            vpnInterface = null;
        } catch (Exception e) {
            Log.e("TVBoxShield", "Error al cerrar interfaz previa");
        }
    }

    try {
        VpnService.Builder builder = new VpnService.Builder();

        // 2. CONFIGURACIÓN ESTÁNDAR (Intento Global)
        builder.setSession("TvBoxShieldDNS")
                .addAddress("10.1.1.1", 32)
                .addDnsServer(dnsPrimario)
                .addDnsServer(dnsSecundario)
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)
                .setBlocking(false);

        // 3. TRUCO IPv4: Forzamos a que el sistema prefiera IPv4
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.allowFamily(OsConstants.AF_INET); // Solo IPv4
            // No agregamos AF_INET6 para forzar el tráfico hacia nuestro túnel v4
        }

        vpnInterface = builder.establish();

        if (vpnInterface != null) {
            Log.d("TVBoxShield", "¡TÚNEL MONTADO! Modo Global.");
            iniciarProcesadorDNS();
        }

    } catch (IllegalArgumentException e) {
        // 4. FALLBACK 1: Si el kernel es estricto (como el tuyo), entramos aquí
        if (e.getMessage() != null && e.getMessage().contains("Bad address")) {
            Log.w("TVBoxShield", "Kernel rechazó ruta global. Aplicando modo DNS66 Fallback...");
            reintentarConfiguracionMinima(dnsPrimario, dnsSecundario);
        } else {
            Log.e("TVBoxShield", "Error de configuración: " + e.getMessage());
        }
    } catch (Exception e) {
        Log.e("TVBoxShield", "Error crítico: " + e.getMessage());
    }
}

    // 2. El método de emergencia (DNS66 Fallback) corregido
    private void reintentarConfiguracionMinima(String d1, String d2) {
        try {
            VpnService.Builder builder = new VpnService.Builder();
            builder.setSession("TvBoxShieldDNS");

            // PASO CLAVE: Si el DNS es 127.0.0.1, no podemos usar 10.1.1.1
            // Usamos una IP neutra pero con máscara 8 para cubrir casi todo el rango
            builder.addAddress("10.0.0.1", 8);

            // DNS66: No agregues rutas si el kernel da Bad Address.
            // El addDnsServer ya le dice al sistema que mande el tráfico DNS al túnel.
            builder.addDnsServer(d1);
            if (d2 != null) builder.addDnsServer(d2);

            builder.setMtu(1500);
            builder.setBlocking(false);

            vpnInterface = builder.establish();

            if (vpnInterface != null) {
                Log.d("TVBoxShield", "¡VICTORIA! Túnel montado con máscara amplia.");
                iniciarProcesadorDNS();
            }
        } catch (Exception e) {
            // ULTIMATUM: Si esto falla, el problema es que el emulador no tiene el módulo TUN
            Log.e("TVBoxShield", "Fallo final: " + e.getMessage());
            intentarSinNada();
        }
    }

    private void intentarSinNada() {
        try {
            // La versión más "pelada" posible que acepta un Android viejo
            vpnInterface = new VpnService.Builder()
                    .addAddress("10.255.255.1", 30)
                    .addDnsServer("8.8.8.8")
                    .establish();
            if (vpnInterface != null) Log.d("TVBoxShield", "Montado con IP de emergencia 10.255");
        } catch (Exception e) {
            Log.e("TVBoxShield", "El kernel del emulador bloquea VpnService.");
        }
    }

    private void reintentarConIPNeutra(String d1, String d2) {
        try {
            vpnInterface = new VpnService.Builder()
                    .addAddress("172.19.0.1", 30) // Rango privado B, muy común en VPNs
                    .addDnsServer(d1)
                    .addRoute(d1, 32)
                    .setSession("TvBoxShieldDNS")
                    .establish();
            if (vpnInterface != null) Log.d("TVBoxShield", "Montado con IP Neutra 172.x");
        } catch (Exception e) {
            Log.e("TVBoxShield", "Dispositivo incompatible con VpnService: " + e.getMessage());
        }
    }

    // 2. El procesador de paquetes UDP (El "Homenaje" a DNS66)
    private byte[] construirPaqueteRespuesta(byte[] paqueteOriginal, byte[] payloadDnsFalso) {
        // 20 bytes (IP) + 8 bytes (UDP) + longitud del DNS falso
        int longitudTotal = 28 + payloadDnsFalso.length;
        byte[] respuesta = new byte[longitudTotal];

        // --- CABECERA IP (20 bytes) ---
        respuesta[0] = 0x45; // Versión 4, longitud 5
        respuesta[1] = 0x00;
        respuesta[2] = (byte) (longitudTotal >> 8);
        respuesta[3] = (byte) (longitudTotal & 0xFF);
        // Identificación (podemos copiar la original)
        respuesta[4] = paqueteOriginal[4];
        respuesta[5] = paqueteOriginal[5];
        respuesta[6] = 0x40; // Flags: Don't fragment
        respuesta[7] = 0x00;
        respuesta[8] = 0x40; // TTL: 64
        respuesta[9] = 17;   // Protocolo: UDP

        // INTERCAMBIO DE IPs: El destino original es ahora el origen
        System.arraycopy(paqueteOriginal, 16, respuesta, 12, 4); // Source IP
        System.arraycopy(paqueteOriginal, 12, respuesta, 16, 4); // Dest IP

        // --- CABECERA UDP (8 bytes) ---
        // Puerto Origen (era el destino: 53)
        respuesta[20] = paqueteOriginal[22];
        respuesta[21] = paqueteOriginal[23];
        // Puerto Destino (era el origen del dispositivo)
        respuesta[22] = paqueteOriginal[20];
        respuesta[23] = paqueteOriginal[21];

        int lenUdp = 8 + payloadDnsFalso.length;
        respuesta[24] = (byte) (lenUdp >> 8);
        respuesta[25] = (byte) (lenUdp & 0xFF);
        respuesta[26] = 0x00; // Checksum UDP (opcional en IPv4, se puede dejar en 0)
        respuesta[27] = 0x00;

        // --- PAYLOAD DNS ---
        System.arraycopy(payloadDnsFalso, 0, respuesta, 28, payloadDnsFalso.length);

        // Calcular Checksum IP (Solo para los primeros 20 bytes)
        calcularChecksumIP(respuesta);

        return respuesta;
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
        if (threadProcesador != null) {
            threadProcesador.interrupt();
        }

        threadProcesador = new Thread(() -> {
            Log.d("TVBoxShield", ">>> HILO DE PROCESAMIENTO INICIADO <<<");

            // Usamos el FileDescriptor del vpnInterface para leer y escribir paquetes IP
            try (FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
                 FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor())) {

                // Buffer de tamaño estándar para paquetes MTU 1500 + cabeceras
                byte[] buffer = new byte[16384];

                while (!Thread.currentThread().isInterrupted()) {
                    int length = in.read(buffer);

                    if (length > 0) {
                        // Log de depuración para confirmar que el tráfico entra
                        // Si no ves este log al navegar, la VPN no está capturando nada.
                        Log.v("TVBoxShield", "Paquete capturado: " + length + " bytes");

                        // DNS66 trabaja a nivel de bytes.
                        // Mandamos el paquete al procesador y le pasamos el 'out'
                        // para que pueda inyectar la respuesta de bloqueo si es necesario.
                        procesarPaqueteDNS(buffer, length, out);
                    }
                }
            } catch (IOException e) {
                Log.e("TVBoxShield", "Error de lectura/escritura en el túnel: " + e.getMessage());
            } catch (Exception e) {
                Log.e("TVBoxShield", "Error crítico en el hilo procesador: " + e.getMessage());
            } finally {
                Log.d("TVBoxShield", ">>> HILO DE PROCESAMIENTO FINALIZADO <<<");
            }
        }, "TVBoxShield-Worker");

        threadProcesador.setPriority(Thread.MAX_PRIORITY); // Prioridad alta para evitar lag en la TV Box
        threadProcesador.start();
    }

    private void procesarPaqueteDNS(byte[] datos, int len, FileOutputStream out) {
        try {
            // Validación mínima de cabeceras IP + UDP
            if (len < 28) return;

            // Protocolo UDP es el byte 9 con valor 17
            if (datos[9] == 17) {
                int puertoOrigen = ((datos[20] & 0xFF) << 8) | (datos[21] & 0xFF);
                int puertoDestino = ((datos[22] & 0xFF) << 8) | (datos[23] & 0xFF);

                // Si el destino es el puerto 53 (DNS)
                if (puertoDestino == 53) {
                    // El payload DNS comienza en el byte 28
                    byte[] dnsPayload = Arrays.copyOfRange(datos, 28, len);

                    // Extraemos el dominio (ej: "doubleclick.net")
                    String dominio = DnsParser.parseDomain(dnsPayload);

                    if (dominio != null) {
                        // Limpiamos el dominio para evitar errores por puntos finales
                        dominio = dominio.toLowerCase().trim();
                        if (dominio.endsWith(".")) {
                            dominio = dominio.substring(0, dominio.length() - 1);
                        }

                        // --- LÓGICA DE BLOQUEO ---
                        if (hostFilter != null && hostFilter.isBlocked(dominio)) {
                            Log.d("TVBoxShield", "🚫 BLOQUEADO: " + dominio);

                            // Generamos la respuesta falsa (NXDOMAIN)
                            byte[] fakeDnsBody = DnsResponseBuilder.buildBlockedResponse(dnsPayload);

                            // Construimos el paquete completo (IP + UDP + DNS)
                            byte[] respuestaCompleta = construirPaqueteRespuesta(datos, fakeDnsBody);

                            // Escribimos de vuelta al túnel
                            out.write(respuestaCompleta);
                            out.flush();
                            return; // Bloqueo completado
                        }
                    }

                    // --- LÓGICA DE REENVÍO (Dominios Permitidos) ---
                    // Si llegamos aquí, el dominio es seguro. Debemos mandarlo al DNS real.
                    enviarAlDnsReal(datos, len, out, puertoOrigen);
                }
            }
        } catch (Exception e) {
            Log.e("TVBoxShield", "Error en procesarPaqueteDNS: " + e.getMessage());
        }
    }

    private void enviarAlDnsReal(byte[] datos, int len, FileOutputStream out, int puertoOrigen) {
        // Abrimos un socket UDP para hablar con el DNS real (ej. 8.8.8.8)
        try (DatagramSocket socket = new DatagramSocket()) {
            // TRUCO CLAVE: Protegemos el socket para que su tráfico NO vuelva a entrar
            // a la VPN, evitando un bucle infinito.
            protect(socket);

            socket.setSoTimeout(2000); // 2 segundos de espera máxima

            // Extraemos solo la parte DNS del paquete IP (del byte 28 en adelante)
            byte[] dnsQuery = Arrays.copyOfRange(datos, 28, len);

            // Enviamos la consulta al DNS que el usuario configuró (o 8.8.8.8)
            InetAddress dnsServidor = InetAddress.getByName("8.8.8.8");
            DatagramPacket outPacket = new DatagramPacket(dnsQuery, dnsQuery.length, dnsServidor, 53);
            socket.send(outPacket);

            // Preparamos para recibir la respuesta
            byte[] respuestaBuffer = new byte[4096];
            DatagramPacket inPacket = new DatagramPacket(respuestaBuffer, respuestaBuffer.length);
            socket.receive(inPacket);

            // Una vez que tenemos la respuesta real de internet:
            // Debemos envolverla en un paquete IP/UDP para que Android la acepte
            byte[] dnsRespuesta = Arrays.copyOf(inPacket.getData(), inPacket.getLength());
            byte[] paqueteCompleto = construirPaqueteRespuesta(datos, dnsRespuesta);

            // Escribimos el paquete en el túnel
            out.write(paqueteCompleto);
            out.flush();

        } catch (SocketTimeoutException e) {
            Log.w("TVBoxShield", "Tiempo de espera agotado para el DNS real");
        } catch (Exception e) {
            Log.e("TVBoxShield", "Error al reenviar al DNS real: " + e.getMessage());
        }
    }

    private void calcularChecksumIP(byte[] buf) {
        int length = 20;
        int i = 0;
        long sum = 0;
        while (length > 1) {
            sum += (((buf[i] & 0xFF) << 8) | (buf[i + 1] & 0xFF));
            i += 2;
            length -= 2;
        }
        while (sum >> 16 != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        sum = ~sum;
        buf[10] = (byte) (sum >> 8);
        buf[11] = (byte) (sum & 0xFF);
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
