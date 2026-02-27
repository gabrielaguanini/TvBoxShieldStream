package com.example.tvboxshieldstream.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tvboxshieldstream.R;
import com.example.tvboxshieldstream.models.AppItem;

import java.util.List;

public class SelectorAppsAdapter extends RecyclerView.Adapter<SelectorAppsAdapter.ViewHolder> {

    private final List<AppItem> appsDisponibles;
    private final List<AppItem> seleccionadas;

    public SelectorAppsAdapter(List<AppItem> appsDisponibles, List<AppItem> seleccionadas) {
        this.appsDisponibles = appsDisponibles;
        this.seleccionadas = seleccionadas;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_selector_app, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppItem app = appsDisponibles.get(position);

        holder.txtNombre.setText(app.nombre);
        holder.imgIcono.setImageDrawable(app.icono);

        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(seleccionadas.contains(app));

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!seleccionadas.contains(app)) {
                    seleccionadas.add(app);
                }
            } else {
                seleccionadas.remove(app);
            }
        });
    }

    @Override
    public int getItemCount() {
        return appsDisponibles.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgIcono;
        TextView txtNombre;
        CheckBox checkBox;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcono = itemView.findViewById(R.id.imgIcono);
            txtNombre = itemView.findViewById(R.id.txtNombre);
            checkBox = itemView.findViewById(R.id.checkSeleccion);
        }
    }
}