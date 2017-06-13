package org.robolectric.annotation.processing.shadows;

import com.example.objects.ParameterizedDummy;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

@Implements(ParameterizedDummy.class)
public class ShadowRealObjectParameterizedMismatch<T,S extends Number> {

  @RealObject
  ParameterizedDummy<S,T> someField;
}
