package com.example.tvboxshieldstream.dns;

public class DnsParser {
    public static String parseDomain(byte[] packet) {
        try {
            // 20 (IP) + 8 (UDP) + 12 (DNS Header) = 40
            int pos = 40;
            StringBuilder domain = new StringBuilder();

            // Verificamos que el paquete sea lo suficientemente largo
            if (packet.length <= pos) return null;

            while (pos < packet.length) {
                int len = packet[pos++] & 0xFF;

                // Si el largo es 0, terminamos (fin del nombre)
                if (len == 0) break;

                // Si ya hay algo en el dominio, agregamos un punto
                if (domain.length() > 0) domain.append(".");

                // Leemos la cantidad de caracteres que indica 'len'
                for (int i = 0; i < len; i++) {
                    if (pos < packet.length) {
                        domain.append((char) packet[pos++]);
                    }
                }
            }
            return domain.toString();
        } catch (Exception e) {
            return "error.parsing";
        }
    }
}