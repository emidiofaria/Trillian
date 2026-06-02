package com.bmw.drivingcoach.data.telemetry;

import com.google.gson.Gson;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00008\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\b\n\u0002\b\u0004\n\u0002\u0010 \n\u0002\b\u0006\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u000bH\u0002J\b\u0010\r\u001a\u00020\tH\u0007J\u0010\u0010\u000e\u001a\u00020\u000b2\u0006\u0010\u000f\u001a\u00020\u0010H\u0002J\b\u0010\u0011\u001a\u00020\tH\u0007J\b\u0010\u0012\u001a\u00020\tH\u0007J\b\u0010\u0013\u001a\u00020\tH\u0007J\u0016\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u000b0\u00152\u0006\u0010\u0016\u001a\u00020\u0006H\u0002J\b\u0010\u0017\u001a\u00020\tH\u0007J\b\u0010\u0018\u001a\u00020\tH\u0007J\b\u0010\u0019\u001a\u00020\tH\u0007J\b\u0010\u001a\u001a\u00020\tH\u0007R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001b"}, d2 = {"Lcom/bmw/drivingcoach/data/telemetry/TelemetryFileWriterTest;", "", "()V", "gson", "Lcom/google/gson/Gson;", "tempDir", "Ljava/io/File;", "testFile", "assertSampleEquals", "", "expected", "Lcom/bmw/drivingcoach/data/telemetry/TelemetrySample;", "actual", "benchmark 18000 samples writes in under 100ms", "createTestSample", "index", "", "empty file returns empty list", "malformed line does not crash readAll", "non-existent file returns empty list", "readSamplesFromFile", "", "file", "sample values are preserved through serialization", "setUp", "tearDown", "write and read round-trip with 100 samples", "app_debugUnitTest"})
public final class TelemetryFileWriterTest {
    private java.io.File tempDir;
    private java.io.File testFile;
    @org.jetbrains.annotations.NotNull()
    private final com.google.gson.Gson gson = null;
    
    public TelemetryFileWriterTest() {
        super();
    }
    
    @org.junit.Before()
    public final void setUp() {
    }
    
    @org.junit.After()
    public final void tearDown() {
    }
    
    private final java.util.List<com.bmw.drivingcoach.data.telemetry.TelemetrySample> readSamplesFromFile(java.io.File file) {
        return null;
    }
    
    private final com.bmw.drivingcoach.data.telemetry.TelemetrySample createTestSample(int index) {
        return null;
    }
    
    private final void assertSampleEquals(com.bmw.drivingcoach.data.telemetry.TelemetrySample expected, com.bmw.drivingcoach.data.telemetry.TelemetrySample actual) {
    }
}