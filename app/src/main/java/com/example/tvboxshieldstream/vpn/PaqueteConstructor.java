package com.example.tvboxshieldstream.vpn;

public class PaqueteConstructor {

    /**
     * Construye un paquete IP/UDP completo para envolver una respuesta DNS.
     * @param peticionOriginal El paquete que capturamos del navegador (para copiar sus IDs).
     * @param respuestaDnsPura Los bytes que nos devolvió el servidor DNS de AdGuard.
     * @return Un array de bytes listo para ser escrito en el FileOutputStream del VPN.
     */
    public static byte[] crearRespuestaIP(byte[] peticionOriginal, byte[] respuestaDnsPura) {
        // Un paquete IP tiene 20 bytes de cabecera + 8 de UDP + el contenido
        int tamanoTotal = 20 + 8 + respuestaDnsPura.length;
        byte[] paquete = new byte[tamanoTotal];

        // --- CABECERA IP (20 bytes) ---
        paquete[0] = 0x45; // Versión 4, IHL 5 (longitud estándar)
        paquete[1] = 0x00; // Diferentiated Services
        paquete[2] = (byte) (tamanoTotal >> 8); // Longitud total (Parte alta)
        paquete[3] = (byte) (tamanoTotal & 0xFF); // Longitud total (Parte baja)

        // !!! EL "DNI" DEL PAQUETE: Copiamos el Identification del original
        paquete[4] = peticionOriginal[4];
        paquete[5] = peticionOriginal[5];

        paquete[6] = 0x40; // Flags: Don't Fragment (evita que el paquete se rompa)
        paquete[7] = 0x00; // Fragment Offset
        paquete[8] = 64;   // TTL (Time to Live)
        paquete[9] = 17;   // Protocolo 17 = UDP

        // INVERSIÓN DE DIRECCIONES IP
        // El que era el destino de la pregunta (12-15) ahora es el origen de la respuesta
        System.arraycopy(peticionOriginal, 16, paquete, 12, 4); // Source IP
        System.arraycopy(peticionOriginal, 12, paquete, 16, 4); // Dest IP (Tu TV Box)

        // --- CÁLCULO DEL CHECKSUM IP ---
        // Android descarta el paquete si esto no es exacto
        int sum = 0;
        for (int i = 0; i < 20; i += 2) {
            if (i == 10) continue; // Saltamos el espacio del checksum
            sum += ((paquete[i] & 0xFF) << 8) | (paquete[i + 1] & 0xFF);
        }
        while ((sum >> 16) > 0) sum = (sum & 0xFFFF) + (sum >> 16);
        sum = ~sum;
        paquete[10] = (byte) (sum >> 8);
        paquete[11] = (byte) (sum & 0xFF);

        // --- CABECERA UDP (8 bytes) ---
        // INVERSIÓN DE PUERTOS
        System.arraycopy(peticionOriginal, 22, paquete, 20, 2); // Source Port (53)
        System.arraycopy(peticionOriginal, 20, paquete, 22, 2); // Dest Port (El de la App)

        int tamanoUdp = 8 + respuestaDnsPura.length;
        paquete[24] = (byte) (tamanoUdp >> 8);
        paquete[25] = (byte) (tamanoUdp & 0xFF);

        // El Checksum UDP es opcional en IPv4, lo dejamos en 0
        paquete[26] = 0x00;
        paquete[27] = 0x00;

        // --- CARGA ÚTIL (El mensaje DNS real) ---
        System.arraycopy(respuestaDnsPura, 0, paquete, 28, respuestaDnsPura.length);

        return paquete;
    }
}