package com.xgen.mongot.util.bson;

import java.util.Optional;
import org.bson.codecs.Codec;
import org.bson.codecs.pojo.PropertyCodecProvider;
import org.bson.codecs.pojo.PropertyCodecRegistry;
import org.bson.codecs.pojo.TypeWithTypeParameters;
import org.jetbrains.annotations.Nullable;

/** This code from http://mongodb.github.io/mongo-java-driver/3.9/bson/pojos/ */
public class OptionalPropertyCodecProvider implements PropertyCodecProvider {

  @Override
  @Nullable
  @SuppressWarnings({"rawtypes", "unchecked"})
  public <T> Codec<T> get(TypeWithTypeParameters<T> type, PropertyCodecRegistry registry) {
    // Check the main type and number of generic parameters
    if (Optional.class.isAssignableFrom(type.getType()) && type.getTypeParameters().size() == 1) {
      // Get the codec for the concrete type of the Optional, as its declared in the POJO.
      Codec<?> valueCodec = registry.get(type.getTypeParameters().get(0));
      return new OptionalCodec(type.getType(), valueCodec);
    } else {
      return null;
    }
  }
}
