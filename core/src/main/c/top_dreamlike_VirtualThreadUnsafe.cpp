#include "top_dreamlike_VirualThreadUnsafe.h"
#include <iostream>
/*
 * Class:     top_dreamlike_VirtualThreadUnsafe
 * Method:    getTrustedLookUp
 * Signature: ()Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_top_dreamlike_VirtualThreadUnsafe_getTrustedLookUp
        (JNIEnv *env, jclass jclazz) {
  auto lookupClass = env->FindClass("java/lang/invoke/MethodHandles$Lookup");
  auto fieldId = env->GetStaticFieldID(lookupClass, "IMPL_LOOKUP", "Ljava/lang/invoke/MethodHandles$Lookup;");
  auto TRUST_LOOKUP = env->GetStaticObjectField(lookupClass, fieldId);
  return TRUST_LOOKUP;

}


