package com.example.tvboxshieldstream.models;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

public class AppItem {
    public String nombre;
    public String packageName;
    public Intent intent;
    // Mantenemos el nombre 'icono' para que el resto del código compile
    public Drawable icono;

    public AppItem(String nombre, String packageName, Intent intent) {
        this.nombre = nombre;
        this.packageName = packageName;
        this.intent = intent;
        // IMPORTANTE: No cargamos el icono aquí. Lo dejamos en null.
    }

    // Este metodo lo usaremos en los Adaptadores para cargar la imagen solo cuando se vea
    public Drawable cargarIconoSiEsNecesario(PackageManager pm) {
        if (this.icono == null) {
            try {
                this.icono = pm.getApplicationIcon(packageName);
            } catch (Exception e) {
                return null;
            }
        }
        return this.icono;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AppItem)) return false;
        AppItem other = (AppItem) obj;
        return packageName != null && packageName.equals(other.packageName);
    }

    @Override
    public int hashCode() {
        return packageName != null ? packageName.hashCode() : 0;
    }
}