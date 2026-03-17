package com.example.tvboxshieldstream.dns;

import java.util.Arrays;

public class DnsParser {

    public static String parseDomain(byte[] dnsPayload) {
        try {
            if (dnsPayload == null || dnsPayload.length < 12) return null;

            StringBuilder domain = new StringBuilder();
            int pos = 12; // Saltamos el Header ID, Flags, etc.

            // Leemos las etiquetas del nombre de dominio
            return leerNombre(dnsPayload, pos, domain);
        } catch (Exception e) {
            return null;
        }
    }

    // Método recursivo simple para manejar nombres y evitar errores de desbordamiento
    private static String leerNombre(byte[] data, int pos, StringBuilder sb) {
        while (pos < data.length) {
            int len = data[pos++] & 0xFF;

            if (len == 0) break; // Fin del nombre

            // Manejo de Compresión DNS (Punteros 0xC0)
            if ((len & 0xC0) == 0xC0) {
                // El puntero indica que el resto del nombre está en otra parte del paquete
                // Para el filtrado de anuncios inicial, normalmente basta con lo que leímos
                break;
            }

            if (sb.length() > 0) sb.append(".");

            for (int i = 0; i < len && pos < data.length; i++) {
                sb.append((char) data[pos++]);
            }
        }
        return sb.toString();
    }

    /**
     * Esta función es VITAL para tu AdVpnService.
     * Extrae solo la sección de la "Pregunta" (Query) para poder
     * reconstruir la respuesta de bloqueo.
     */
    public static byte[] extraerQueryCompleto(byte[] dnsPayload) {
        if (dnsPayload == null || dnsPayload.length < 12) return null;

        int pos = 12;
        while (pos < dnsPayload.length) {
            int len = dnsPayload[pos++] & 0xFF;
            if (len == 0) break;
            if ((len & 0xC0) == 0xC0) {
                pos++; // Saltar el segundo byte del puntero
                break;
            }
            pos += len;
        }
        // Después del nombre vienen 4 bytes: Type (2) y Class (2)
        int finQuery = pos + 4;
        if (finQuery > dnsPayload.length) return null;

        return Arrays.copyOfRange(dnsPayload, 0, finQuery);
    }
}