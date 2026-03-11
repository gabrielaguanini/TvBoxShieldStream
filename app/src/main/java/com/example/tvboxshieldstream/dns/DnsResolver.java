package com.example.tvboxshieldstream.dns;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class DnsResolver {
    private static final String UPSTREAM_DNS = "94.140.14.14"; // AdGuard DNS

    public static byte[] resolve(byte[] request) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(1500); // No esperar más de 1.5s

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