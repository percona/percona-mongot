package com.xgen.mongot.config.provider.community;

import com.google.common.net.HostAndPort;
import com.xgen.mongot.util.Check;
import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentEncodable;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import com.xgen.mongot.util.bson.parser.Value;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.bson.BsonDocument;

public abstract sealed class MongoConnectionConfig implements DocumentEncodable
    permits ReplicaSetConfig, RouterConfig {

  private final List<HostAndPort> hostandPorts;

  private final Optional<ScramConfig> scram;

  private final Optional<X509Config> x509;

  public MongoConnectionConfig(
      List<HostAndPort> hostandPorts, Optional<X509Config> x509, Optional<ScramConfig> scram) {
    this.hostandPorts = hostandPorts;
    this.x509 = x509;
    this.scram = scram;
  }

  protected static class Fields {
    public static final Field.Required<List<String>> HOST_AND_PORT =
        Field.builder("hostAndPort")
            .singleValueOrListOf(Value.builder().stringValue().mustNotBeEmpty().required())
            .mustNotBeEmpty()
            .required();

    public static final Field.Optional<X509Config> X509 =
        Field.builder("x509")
            .classField(X509Config::fromBson)
            .disallowUnknownFields()
            .optional()
            .noDefault();

    public static final Field.Optional<ScramConfig> SCRAM =
        Field.builder("scramAuth")
            .classField(ScramConfig::fromBson)
            .disallowUnknownFields()
            .optional()
            .noDefault();
  }

  public List<HostAndPort> hostandPorts() {
    return this.hostandPorts;
  }

  public Optional<X509Config> x509() {
    return this.x509;
  }

  public Optional<ScramConfig> scram() {
    return this.scram;
  }

  @Override
  public BsonDocument toBson() {
    return BsonDocumentBuilder.builder()
        .field(Fields.HOST_AND_PORT, this.hostandPorts.stream().map(HostAndPort::toString).toList())
        .field(Fields.X509, this.x509)
        .field(Fields.SCRAM, this.scram)
        .build();
  }

  /**
   * Validates the configured authentication mechanism. x509 and scram are mutually exclusive; once
   * that check passes the call is dispatched to a path-specific validator.
   */
  public void validate(DocumentParser parser) throws BsonParseException {
    this.validateOnlyOneAuthType(parser);

    if (this.x509.isPresent()) {
      this.x509.get().validate(parser);
    } else if (this.scram.isPresent()) {
      this.scram.get().validate(parser);
    } else {
      throw Check.unreachableError();
    }
  }

  private void validateOnlyOneAuthType(DocumentParser parser) throws BsonParseException {
    long presentCount = Stream.of(this.scram, this.x509).filter(Optional::isPresent).count();
    if (presentCount != 1) {
      parser
          .getContext()
          .handleSemanticError("Exactly one authentication mechanism must be used (x509 or scram)");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MongoConnectionConfig that = (MongoConnectionConfig) o;
    return Objects.equals(this.hostandPorts, that.hostandPorts)
        && Objects.equals(this.x509, that.x509)
        && Objects.equals(this.scram, that.scram);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.hostandPorts, this.x509, this.scram);
  }
}
