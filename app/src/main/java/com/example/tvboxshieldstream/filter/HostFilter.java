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
                    // Guardamos el dominio (usualmente la segunda columna en un archivo hosts)
                    String domain = (parts.length >= 2) ? parts[1].toLowerCase() : parts[0].toLowerCase();
                    blockedDomains.add(domain);

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isBlocked(String domain) {
        if (domain == null) return false;
        String d = domain.toLowerCase();
        // Verifica el dominio exacto o si es un subdominio de algo bloqueado
        return blockedDomains.contains(d) || verificarSubdominio(d);
    }

    private boolean verificarSubdominio(String domain) {
        for (String blocked : blockedDomains) {
            if (domain.endsWith("." + blocked)) return true;
        }
        return false;
    }
}
