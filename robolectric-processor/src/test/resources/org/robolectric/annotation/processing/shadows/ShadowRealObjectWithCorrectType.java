package org.robolectric.annotation.processing.shadows;

import com.example.objects.Dummy;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

@Implements(Dummy.class)
public class ShadowRealObjectWithCorrectType {

  @RealObject Dummy someField;
}
