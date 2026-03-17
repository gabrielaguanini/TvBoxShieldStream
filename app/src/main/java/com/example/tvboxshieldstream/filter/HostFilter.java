package com.example.tvboxshieldstream.filter;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

public class HostFilter {
    private final Set<String> blockedDomains = new HashSet<>();

    public HostFilter(Context context) {
        cargarHosts(context);
    }

    private void cargarHosts(Context context) {
        try {
            File file = new File(context.getFilesDir(), "hosts.txt");
            if (!file.exists()) return;
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split("\\s+");
                    String domain = (parts.length >= 2) ? parts[1].toLowerCase() : parts[0].toLowerCase();
                    blockedDomains.add(domain);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public boolean isBlocked(String domain) {

        if (domain == null) return false;

        String d = domain.toLowerCase();

        if (d.contains("googleapis.com")) return false;
        if (d.contains("gstatic.com")) return false;
        if (d.contains("dns.google")) return false;

        if (blockedDomains.contains(d)) return true;

        int index;

        while ((index = d.indexOf(".")) != -1) {

            d = d.substring(index + 1);

            if (blockedDomains.contains(d))
                return true;
        }

        return false;
    }
}
