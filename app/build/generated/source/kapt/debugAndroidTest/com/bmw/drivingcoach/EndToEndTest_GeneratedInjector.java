package com.bmw.drivingcoach;

import dagger.hilt.InstallIn;
import dagger.hilt.codegen.OriginatingElement;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.internal.GeneratedEntryPoint;
import javax.annotation.processing.Generated;

@OriginatingElement(
    topLevelClass = EndToEndTest.class
)
@GeneratedEntryPoint
@InstallIn(SingletonComponent.class)
@Generated("dagger.hilt.processor.internal.root.TestInjectorGenerator")
public interface EndToEndTest_GeneratedInjector {
  void injectTest(EndToEndTest endToEndTest);
}
