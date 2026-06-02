package com.bmw.drivingcoach.ui.session.tabs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bmw.drivingcoach.R;
import com.bmw.drivingcoach.data.db.entity.LapEntity;
import com.bmw.drivingcoach.databinding.FragmentLapsBinding;
import com.bmw.drivingcoach.databinding.ItemLapCardBinding;
import com.bmw.drivingcoach.ui.session.SessionResultFragmentDirections;
import com.bmw.drivingcoach.ui.session.SessionResultViewModel;
import dagger.hilt.android.AndroidEntryPoint;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0003J\u0018\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u00022\u0006\u0010\u0007\u001a\u00020\u0002H\u0016J\u0018\u0010\b\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u00022\u0006\u0010\u0007\u001a\u00020\u0002H\u0016\u00a8\u0006\t"}, d2 = {"Lcom/bmw/drivingcoach/ui/session/tabs/LapDiffCallback;", "Landroidx/recyclerview/widget/DiffUtil$ItemCallback;", "Lcom/bmw/drivingcoach/data/db/entity/LapEntity;", "()V", "areContentsTheSame", "", "oldItem", "newItem", "areItemsTheSame", "app_release"})
final class LapDiffCallback extends androidx.recyclerview.widget.DiffUtil.ItemCallback<com.bmw.drivingcoach.data.db.entity.LapEntity> {
    
    public LapDiffCallback() {
        super();
    }
    
    @java.lang.Override()
    public boolean areItemsTheSame(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.entity.LapEntity oldItem, @org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.entity.LapEntity newItem) {
        return false;
    }
    
    @java.lang.Override()
    public boolean areContentsTheSame(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.entity.LapEntity oldItem, @org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.entity.LapEntity newItem) {
        return false;
    }
}