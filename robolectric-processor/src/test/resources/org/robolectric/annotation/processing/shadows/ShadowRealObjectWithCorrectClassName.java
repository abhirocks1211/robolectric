package org.robolectric.annotation.processing.shadows;

import com.example.objects.Dummy;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

@Implements(className="com.example.objects.Dummy")
public class ShadowRealObjectWithCorrectClassName {

  @RealObject Dummy someField;
}
