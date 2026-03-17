package com.example.tvboxshieldstream.dns;

import java.util.Arrays;

public class DnsResponseBuilder {
    public static byte[] buildBlockedResponse(byte[] query) {
        if (query == null || query.length < 12) return query;

        // Estructura de la respuesta: Puntero al nombre + Tipo A + Clase IN + TTL + IP 0.0.0.0
        byte[] answerSection = {
                (byte)0xC0, (byte)0x0C, // Nombre comprimido (puntero)
                (byte)0x00, (byte)0x01, // Tipo A
                (byte)0x00, (byte)0x01, // Clase IN
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x3C, // TTL 60s
                (byte)0x00, (byte)0x04, // Longitud IP
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00  // IP: 0.0.0.0
        };

        byte[] response = new byte[query.length + answerSection.length];
        System.arraycopy(query, 0, response, 0, query.length);
        System.arraycopy(answerSection, 0, response, query.length, answerSection.length);

        // Flags: QR=1 (Respuesta), AA=1, RCODE=0 (Sin error)
        response[2] = (byte) 0x81;
        response[3] = (byte) 0x80;

        // Answer Count = 1 (Indispensable para que la app lea la IP)
        response[6] = 0;
        response[7] = 1;

        return response;
    }
}