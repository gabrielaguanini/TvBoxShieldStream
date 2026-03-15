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
import com.example.tvboxshieldstream.dns.DnsResolver;
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
    if (vpnInterface != null) {
        try { vpnInterface.close(); vpnInterface = null; } catch (Exception ignored) {}
    }

    try {
        VpnService.Builder builder = new VpnService.Builder();

        builder.setSession("TvBoxShield")
                .addAddress("172.19.0.1", 30)
                .setMtu(1280);

        // --- EL CAMBIO CLAVE AQUÍ ---
        // En lugar de 0.0.0.0 (todo el tráfico), capturamos solo las IPs de DNS comunes
        // Esto obliga a que las preguntas de DNS pasen por tu código
        builder.addRoute("8.8.8.8", 32);    // Google DNS
        builder.addRoute("8.8.4.4", 32);    // Google DNS
        builder.addRoute("1.1.1.1", 32);    // Cloudflare
        builder.addRoute("94.140.14.14", 32); // AdGuard

        // También nos agregamos a nosotros mismos como DNS del sistema
        builder.addDnsServer("8.8.8.8");

        vpnInterface = builder.establish();

        if (vpnInterface != null) {
            Log.d("TVBoxShield", "¡MODO DNS-ONLY ACTIVO!");
            iniciarProcesadorDNS();
        }

    } catch (Exception e) {
        Log.e("TVBoxShield", "Error: " + e.getMessage());
    }
}
    private void iniciarProcesadorDNS() {
        if (threadProcesador != null) {
            threadProcesador.interrupt();
        }

        threadProcesador = new Thread(() -> {
            Log.i("TVBoxShield", ">>> HILO DE PROCESAMIENTO INICIADO <<<");

            // Usamos el FileDescriptor del vpnInterface para leer y escribir paquetes IP
            try (FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
                 FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor())) {

                // Buffer de tamaño estándar para paquetes
                byte[] buffer = new byte[16384];

                while (!Thread.currentThread().isInterrupted()) {
                    int longitud = in.read(buffer);

                    if (longitud > 0) {
                        // Verificamos Protocolo IP: 17 es UDP
                        byte protocolo = buffer[9];

                        if (protocolo == 17) {
                            // Puerto destino en UDP está en los bytes 22 y 23
                            int puertoDestino = ((buffer[22] & 0xFF) << 8) | (buffer[23] & 0xFF);

                            if (puertoDestino == 53) {
                                String dominio = DnsParser.parseDomain(buffer);
                                Log.d("TVBoxShield", "Consulta detectada: " + dominio);

                                if (hostFilter.isBlocked(dominio)) {
                                    Log.w("TVBoxShield", "🚫 BLOQUEANDO: " + dominio);
                                    byte[] fakeResponse = DnsResponseBuilder.buildBlockedResponse(buffer);
                                    if (fakeResponse != null) {
                                        out.write(fakeResponse);
                                    }
                                    continue;
                                } else {
                                    Log.i("TVBoxShield", "🌍 PERMITIDO: " + dominio);

                                    byte[] preguntaDnsPura = extraerSoloDns(buffer, longitud);

                                    // El 'this' es para que DnsResolver pueda hacer socket.protect()
                                    byte[] respuestaDnsPura = DnsResolver.resolve(preguntaDnsPura, this);

                                    if (respuestaDnsPura != null) {
                                        // RECONSTRUCCIÓN CRÍTICA: Aquí usamos tu nueva clase
                                        // Esto soluciona el problema de Time Out al dar un Checksum e ID válido
                                        byte[] paqueteCompleto = PaqueteConstructor.crearRespuestaIP(buffer, respuestaDnsPura);

                                        if (paqueteCompleto != null) {
                                            out.write(paqueteCompleto);
                                            out.flush();
                                            Log.d("TVBoxShield", "✅ Respuesta inyectada para: " + dominio);
                                        }
                                    } else {
                                        Log.e("TVBoxShield", "❌ Sin respuesta del servidor para: " + dominio);
                                    }
                                }
                            }
                        }
                        // Si no es DNS (Puerto 53), el paquete se ignora.
                        // Con las rutas específicas de Google/Cloudflare, nada más debería entrar aquí.
                    }
                }
            } catch (IOException e) {
                Log.e("TVBoxShield", "Error de E/S en túnel: " + e.getMessage());
            } catch (Exception e) {
                Log.e("TVBoxShield", "Error crítico en hilo: " + e.getMessage());
            } finally {
                Log.i("TVBoxShield", ">>> HILO DE PROCESAMIENTO FINALIZADO <<<");
            }
        }, "TVBoxShield-Worker");

        threadProcesador.setPriority(Thread.MAX_PRIORITY);
        threadProcesador.start();
    }

    /**
     * Corta los headers IP (20 bytes) y UDP (8 bytes) para dejar solo el mensaje DNS
     */
    private byte[] extraerSoloDns(byte[] buffer, int longitud) {
        int inicioDns = 28;
        if (longitud <= inicioDns) return null;
        byte[] dnsPuro = new byte[longitud - inicioDns];
        System.arraycopy(buffer, inicioDns, dnsPuro, 0, longitud - inicioDns);
        return dnsPuro;
    }

    public class PaqueteConstructor {

        public static byte[] crearRespuestaIP(byte[] peticionOriginal, byte[] respuestaDnsPura) {
            int tamanoTotal = 20 + 8 + respuestaDnsPura.length;
            byte[] paquetePuro = new byte[tamanoTotal];

            // --- CABECERA IP (20 bytes) ---
            paquetePuro[0] = 0x45; // Versión 4, IHL 5
            paquetePuro[2] = (byte) (tamanoTotal >> 8);
            paquetePuro[3] = (byte) (tamanoTotal & 0xFF);

            // Copiamos el ID original para que el sistema lo reconozca
            paquetePuro[4] = peticionOriginal[4];
            paquetePuro[5] = peticionOriginal[5];

            paquetePuro[8] = 64;   // TTL
            paquetePuro[9] = 17;   // Protocolo UDP

            // Invertimos las IPs: El que era destino ahora es origen
            System.arraycopy(peticionOriginal, 16, paquetePuro, 12, 4); // Source IP
            System.arraycopy(peticionOriginal, 12, paquetePuro, 16, 4); // Dest IP

            // --- CÁLCULO DEL CHECKSUM IP (Obligatorio) ---
            int sum = 0;
            for (int i = 0; i < 20; i += 2) {
                if (i == 10) continue; // Saltamos los bytes del checksum mismo
                sum += ((paquetePuro[i] & 0xFF) << 8) | (paquetePuro[i + 1] & 0xFF);
            }
            while ((sum >> 16) > 0) sum = (sum & 0xFFFF) + (sum >> 16);
            sum = ~sum;
            paquetePuro[10] = (byte) (sum >> 8);
            paquetePuro[11] = (byte) (sum & 0xFF);

            // --- CABECERA UDP (8 bytes) ---
            // Invertimos puertos: El DNS (53) ahora es el origen
            paquetePuro[20] = peticionOriginal[22];
            paquetePuro[21] = peticionOriginal[23];
            // El puerto aleatorio del navegador ahora es el destino
            paquetePuro[22] = peticionOriginal[20];
            paquetePuro[23] = peticionOriginal[21];

            int tamanoUdp = 8 + respuestaDnsPura.length;
            paquetePuro[24] = (byte) (tamanoUdp >> 8);
            paquetePuro[25] = (byte) (tamanoUdp & 0xFF);

            // Checksum UDP en 0 (Opcional en IPv4, ayuda a la velocidad)
            paquetePuro[26] = 0;
            paquetePuro[27] = 0;

            // --- DATOS DNS ---
            System.arraycopy(respuestaDnsPura, 0, paquetePuro, 28, respuestaDnsPura.length);

            return paquetePuro;
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
