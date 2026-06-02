package com.bmw.drivingcoach.ui.session;

import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.bmw.drivingcoach.ui.session.tabs.ChartFragment;
import com.bmw.drivingcoach.ui.session.tabs.CoachFragment;
import com.bmw.drivingcoach.ui.session.tabs.LapsFragment;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0010\u0010\u0007\u001a\u00020\u00032\u0006\u0010\b\u001a\u00020\tH\u0016J\b\u0010\n\u001a\u00020\tH\u0016R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000b"}, d2 = {"Lcom/bmw/drivingcoach/ui/session/SessionPagerAdapter;", "Landroidx/viewpager2/adapter/FragmentStateAdapter;", "fragment", "Landroidx/fragment/app/Fragment;", "sessionId", "", "(Landroidx/fragment/app/Fragment;J)V", "createFragment", "position", "", "getItemCount", "app_release"})
public final class SessionPagerAdapter extends androidx.viewpager2.adapter.FragmentStateAdapter {
    private final long sessionId = 0L;
    
    public SessionPagerAdapter(@org.jetbrains.annotations.NotNull()
    androidx.fragment.app.Fragment fragment, long sessionId) {
        super(null);
    }
    
    @java.lang.Override()
    public int getItemCount() {
        return 0;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public androidx.fragment.app.Fragment createFragment(int position) {
        return null;
    }
}