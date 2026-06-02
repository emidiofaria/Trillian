package com.bmw.drivingcoach.data.api;

import kotlinx.coroutines.flow.SharedFlow;
import javax.inject.Inject;
import javax.inject.Singleton;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b6\u0018\u00002\u00020\u0001:\u0001\u0003B\u0007\b\u0004\u00a2\u0006\u0002\u0010\u0002\u0082\u0001\u0001\u0004\u00a8\u0006\u0005"}, d2 = {"Lcom/bmw/drivingcoach/data/api/AuthEvent;", "", "()V", "SessionExpired", "Lcom/bmw/drivingcoach/data/api/AuthEvent$SessionExpired;", "app_debug"})
public abstract class AuthEvent {
    
    private AuthEvent() {
        super();
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0003"}, d2 = {"Lcom/bmw/drivingcoach/data/api/AuthEvent$SessionExpired;", "Lcom/bmw/drivingcoach/data/api/AuthEvent;", "()V", "app_debug"})
    public static final class SessionExpired extends com.bmw.drivingcoach.data.api.AuthEvent {
        @org.jetbrains.annotations.NotNull()
        public static final com.bmw.drivingcoach.data.api.AuthEvent.SessionExpired INSTANCE = null;
        
        private SessionExpired() {
        }
    }
}