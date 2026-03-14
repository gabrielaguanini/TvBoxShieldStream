package com.example.tvboxshieldstream.dns;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import android.net.VpnService; // Importante

public class DnsResolver {
    private static final String UPSTREAM_DNS = "94.140.14.14"; // AdGuard DNS

    // Le pasamos el VpnService como parámetro para poder usar 'protect'
    public static byte[] resolve(byte[] request, VpnService vpnService) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();

            // --- EL PASO CRÍTICO ---
            // Esto evita que la consulta DNS se meta dentro de tu propia VPN
            if (vpnService != null) {
                vpnService.protect(socket);
            }

            socket.setSoTimeout(1500);

            InetAddress server = InetAddress.getByName(UPSTREAM_DNS);
            DatagramPacket outPacket = new DatagramPacket(request, request.length, server, 53);
            socket.send(outPacket);

            byte[] buffer = new byte[1024];
            DatagramPacket inPacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(inPacket);

            byte[] result = new byte[inPacket.getLength()];
            System.arraycopy(buffer, 0, result, 0, result.length);
            return result;
        } catch (Exception e) {
            return null;
        } finally {
            if (socket != null) socket.close();
        }
    }
}