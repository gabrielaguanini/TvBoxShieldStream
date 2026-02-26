package com.example.tvboxshieldstream.models;

import android.content.Intent;
import android.graphics.drawable.Drawable;

public class AppItem {
    public String nombre;
    public Drawable icono;
    public Intent intent;

    public AppItem(String nombre, Drawable icono, Intent intent) {
        this.nombre = nombre;
        this.icono = icono;
        this.intent = intent;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AppItem)) return false;
        AppItem other = (AppItem) obj;
        return nombre.equals(other.nombre);
    }

    @Override
    public int hashCode() {
        return nombre.hashCode();
    }
}