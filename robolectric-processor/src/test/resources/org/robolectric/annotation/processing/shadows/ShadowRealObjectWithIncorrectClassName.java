package org.robolectric.annotation.processing.shadows;

import com.example.objects.UniqueDummy;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

@Implements(className="com.example.objects.Dummy")
public class ShadowRealObjectWithIncorrectClassName {

  @RealObject UniqueDummy someField;
}
