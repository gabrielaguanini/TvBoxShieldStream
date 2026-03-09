package com.example.tvboxshieldstream.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tvboxshieldstream.R;
import com.example.tvboxshieldstream.models.AppItem;

import java.util.List;

public class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.AppViewHolder> {

    private final List<AppItem> apps;

    public AppsAdapter(List<AppItem> apps) {
        this.apps = apps;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.buttons_lanzar_apps, parent, false);
        return new AppViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppItem item = apps.get(position);

        // 1. Ponemos el nombre como siempre
        holder.txtNombreApp.setText(item.nombre);

        // 2. CARGA INTELIGENTE: Obtenemos el PackageManager del contexto de la vista
        android.content.pm.PackageManager pm = holder.itemView.getContext().getPackageManager();

        // 3. Usamos el metodo que creamos en AppItem para cargar el icono solo si es necesario
        // Esto evita cargar 50 iconos en la RAM al mismo tiempo
        holder.imgIconoApp.setImageDrawable(item.cargarIconoSiEsNecesario(pm));

        // Configuración de foco para D-Pad (Control remoto)
        holder.itemView.setFocusable(true);
        holder.itemView.setFocusableInTouchMode(true);

        holder.itemView.setOnClickListener(v -> {
            try {
                v.getContext().startActivity(item.intent);
            } catch (Exception e) {
                android.widget.Toast.makeText(v.getContext(),
                        "No se pudo abrir la aplicación", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView imgIconoApp;
        TextView txtNombreApp;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIconoApp = itemView.findViewById(R.id.imgIconoApp);
            txtNombreApp = itemView.findViewById(R.id.txtNombreApp);
        }
    }
}