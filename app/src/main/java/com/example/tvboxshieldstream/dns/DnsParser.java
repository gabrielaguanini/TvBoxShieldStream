package com.example.tvboxshieldstream.dns;

public class DnsParser {
    public static String parseDomain(byte[] packet) {
        try {
            // El header DNS mide 12 bytes, el nombre empieza en el byte 12 (offset 28 si es paquete IP completo)
            // Pero si ya extrajiste el payload UDP, empezamos en 12.
            int pos = 28;
            StringBuilder domain = new StringBuilder();

            while (pos < packet.length) {
                int len = packet[pos++] & 0xFF;
                if (len == 0) break;

                if (domain.length() > 0) domain.append(".");

                for (int i = 0; i < len; i++) {
                    domain.append((char) packet[pos++]);
                }
            }
            return domain.toString();
        } catch (Exception e) {
            return null;
        }
    }
}