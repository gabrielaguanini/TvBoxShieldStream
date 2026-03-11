package com.example.tvboxshieldstream.dns;

public class DnsResponseBuilder {
    public static byte[] buildBlockedResponse(byte[] request) {
        // Clonamos la petición para mantener el ID de transacción
        byte[] response = new byte[request.length + 16];
        System.arraycopy(request, 0, response, 0, request.length);

        // Flags: Respuesta estándar, sin error (pero nosotros daremos 0.0.0.0)
        response[2] = (byte) 0x81;
        response[3] = (byte) 0x80;

        // Answer Count: 1
        response[7] = 1;

        int pos = request.length;
        // Nombre (puntero al nombre en la pregunta)
        response[pos++] = (byte) 0xC0;
        response[pos++] = 0x0C;
        // Tipo A (IPv4)
        response[pos++] = 0x00; response[pos++] = 0x01;
        // Clase IN
        response[pos++] = 0x00; response[pos++] = 0x01;
        // TTL (60 segundos)
        response[pos++] = 0x00; response[pos++] = 0x00;
        response[pos++] = 0x00; response[pos++] = 0x3C;
        // Data length (4 bytes para IP)
        response[pos++] = 0x00; response[pos++] = 0x04;
        // IP: 0.0.0.0
        response[pos++] = 0; response[pos++] = 0;
        response[pos++] = 0; response[pos++] = 0;

        return response;
    }
}
