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
        holder.txtNombreApp.setText(item.nombre);
        holder.imgIconoApp.setImageDrawable(item.icono);

        holder.itemView.setFocusable(true);
        holder.itemView.setFocusableInTouchMode(true);

        holder.itemView.setOnClickListener(v ->
                v.getContext().startActivity(item.intent)
        );
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