package com.example.tvboxshieldstream.vpn;

public class PaqueteConstructor {
    public static byte[] crearRespuestaIP(byte[] peticionOriginal, byte[] respuestaDnsPura) {
        int ipHeaderLenOriginal = (peticionOriginal[0] & 0x0F) * 4;
        int tamanoTotal = 20 + 8 + respuestaDnsPura.length;
        byte[] paquete = new byte[tamanoTotal];

        // --- HEADER IP ---
        paquete[0] = 0x45;
        paquete[2] = (byte) (tamanoTotal >> 8);
        paquete[3] = (byte) (tamanoTotal & 0xFF);
        paquete[4] = peticionOriginal[4]; // Copiamos ID
        paquete[5] = peticionOriginal[5];
        paquete[6] = 0x40; // Don't Fragment
        paquete[8] = 64;   // TTL
        paquete[9] = 17;   // UDP

        // IPs Invertidas
        System.arraycopy(peticionOriginal, 16, paquete, 12, 4); // Dest -> Src
        System.arraycopy(peticionOriginal, 12, paquete, 16, 4); // Src -> Dest

        calcularChecksumIP(paquete);

        // --- HEADER UDP ---
        // Puertos invertidos (SrcPort está en bytes 0-1 del UDP, DestPort en 2-3)
        paquete[20] = peticionOriginal[ipHeaderLenOriginal + 2];
        paquete[21] = peticionOriginal[ipHeaderLenOriginal + 3];
        paquete[22] = peticionOriginal[ipHeaderLenOriginal + 0];
        paquete[23] = peticionOriginal[ipHeaderLenOriginal + 1];

        int tamanoUdp = 8 + respuestaDnsPura.length;
        paquete[24] = (byte) (tamanoUdp >> 8);
        paquete[25] = (byte) (tamanoUdp & 0xFF);

        // --- PAYLOAD DNS ---
        System.arraycopy(respuestaDnsPura, 0, paquete, 28, respuestaDnsPura.length);

        calcularChecksumUDP(paquete, respuestaDnsPura.length);

        return paquete;
    }

    private static void calcularChecksumIP(byte[] buf) {
        int sum = 0;
        for (int i = 0; i < 20; i += 2) {
            if (i == 10) continue;
            sum += ((buf[i] & 0xFF) << 8) | (buf[i + 1] & 0xFF);
        }
        while ((sum >> 16) > 0) sum = (sum & 0xFFFF) + (sum >> 16);
        sum = (~sum) & 0xFFFF;
        buf[10] = (byte) (sum >> 8);
        buf[11] = (byte) (sum & 0xFF);
    }

    private static void calcularChecksumUDP(byte[] buf, int dnsLen) {
        long sum = 0;
        // Pseudo-header IP
        for (int i = 12; i < 20; i += 2) sum += ((buf[i] & 0xFF) << 8) | (buf[i + 1] & 0xFF);
        sum += 17; // Protocolo
        sum += (8 + dnsLen); // Longitud UDP

        for (int i = 20; i < buf.length; i += 2) {
            if (i == 26) continue;
            if (i + 1 < buf.length) sum += ((buf[i] & 0xFF) << 8) | (buf[i + 1] & 0xFF);
            else sum += (buf[i] & 0xFF) << 8;
        }
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        sum = (~sum) & 0xFFFF;
        if (sum == 0) sum = 0xFFFF;
        buf[26] = (byte) (sum >> 8);
        buf[27] = (byte) (sum & 0xFF);
    }
}