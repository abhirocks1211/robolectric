package org.robolectric.annotation.processing.shadows;

import com.example.objects.Dummy;
import com.example.objects.UniqueDummy;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

@Implements(Dummy.class)
public class ShadowRealObjectWithWrongType {

  @RealObject
  UniqueDummy someField;
}
