package com.example.tvboxshieldstream.adapters;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tvboxshieldstream.R;
import com.example.tvboxshieldstream.models.AppItem;

import java.util.ArrayList;
import java.util.List;

public class SelectorAppsDialog extends Dialog {

    private List<AppItem> disponibles;
    private List<AppItem> seleccionadas;
    private OnAppsSeleccionadasListener listener;

    public interface OnAppsSeleccionadasListener {
        void onAppsSeleccionadas(List<AppItem> seleccionadas);
    }

    public SelectorAppsDialog(@NonNull Context context, List<AppItem> disponibles, List<AppItem> seleccionadas, OnAppsSeleccionadasListener listener) {
        super(context);
        this.disponibles = disponibles;
        this.seleccionadas = new ArrayList<>(seleccionadas);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_selector_apps);

        RecyclerView recycler = findViewById(R.id.recyclerSelectorApps);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(new SelectorAdapter());
    }

    private class SelectorAdapter extends RecyclerView.Adapter<SelectorAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_seleccion_app, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppItem app = disponibles.get(position);
            holder.txtNombre.setText(app.nombre);
            holder.imgIcono.setImageDrawable(app.icono);
            holder.checkBox.setChecked(seleccionadas.contains(app));

            holder.itemView.setOnClickListener(v -> {
                if (seleccionadas.contains(app)) {
                    seleccionadas.remove(app);
                    holder.checkBox.setChecked(false);
                } else {
                    seleccionadas.add(app);
                    holder.checkBox.setChecked(true);
                }
            });
        }

        @Override
        public int getItemCount() {
            return disponibles.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imgIcono;
            TextView txtNombre;
            CheckBox checkBox;

            ViewHolder(View itemView) {
                super(itemView);
                imgIcono = itemView.findViewById(R.id.imgIconoApp);
                txtNombre = itemView.findViewById(R.id.txtNombreApp);
                checkBox = itemView.findViewById(R.id.checkboxApp);
            }
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        listener.onAppsSeleccionadas(seleccionadas);
    }
}