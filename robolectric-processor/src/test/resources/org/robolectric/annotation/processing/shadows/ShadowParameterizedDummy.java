package org.robolectric.annotation.processing.shadows;

import com.example.objects.ParameterizedDummy;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

@Implements(ParameterizedDummy.class)
public class ShadowParameterizedDummy<T, S extends Number> {
  @RealObject
  ParameterizedDummy<T,S> real;
}
