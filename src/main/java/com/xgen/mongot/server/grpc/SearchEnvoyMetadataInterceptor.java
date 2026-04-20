package com.xgen.mongot.server.grpc;

import com.xgen.mongot.searchenvoy.grpc.SearchEnvoyMetadata;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.Optional;

public class SearchEnvoyMetadataInterceptor implements ServerInterceptor {

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

    Optional<SearchEnvoyMetadata> searchEnvoyMetadata =
        Optional.ofNullable(headers.get(GrpcMetadata.SEARCH_ENVOY_METADATA_KEY));

    int attemptCount = parseAttemptCount(headers);

    Context context =
        Context.current()
            .withValue(
                GrpcContext.SEARCH_ENVOY_METADATA_KEY,
                searchEnvoyMetadata.orElse(SearchEnvoyMetadata.getDefaultInstance()))
            .withValue(GrpcContext.ENVOY_ATTEMPT_COUNT_KEY, attemptCount);

    return Contexts.interceptCall(context, call, headers, next);
  }

  private static int parseAttemptCount(Metadata headers) {
    // Envoy sets `x-envoy-attempt-count` starting at 1 on the initial request and increments it
    // on each retry (see envoy's `include_request_attempt_count` route option). When the header
    // is missing (direct gRPC clients, or envoy's `include_request_attempt_count` not enabled)
    // or malformed, we fall back to 1 — i.e. "treat this as the first attempt". This is the
    // conservative default: it matches envoy's own first-attempt value, and downstream retry
    // logic in `ServerCallHandler#shouldRetryViaEnvoy` will still permit signalling a retry
    // (1 < MAX_ENVOY_RETRY_ATTEMPTS), so we don't silently disable retries for clients that
    // omit the header.
    String value = headers.get(GrpcMetadata.ENVOY_ATTEMPT_COUNT_KEY);
    if (value == null) {
      return 1;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return 1;
    }
  }
}
