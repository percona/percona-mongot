# Using Azure OpenAI as an Embedding Provider

`mongot`'s `OPENAI_COMPATIBLE` embedding provider (used for automatic embedding
generation with `autoEmbed` vector search indexes) can talk to Azure OpenAI Service in
addition to plain OpenAI and self-hosted engines (Ollama, vLLM, TEI, etc.). This page
covers only the Azure-specific configuration; it assumes you already have automatic
embedding generation configured in general — see the `embedding:` section reference in
`embedding-service-configs.yml` and your `mongot.conf`.

## Prerequisites

- An Azure OpenAI resource with at least one embedding model deployed (for example
  `text-embedding-3-small`, `text-embedding-3-large`, or `text-embedding-ada-002`).
- From the Azure portal, note down the resource endpoint, the deployment name, the
  `api-version` your deployment supports, and an API key.

## The two Azure deltas

Azure hosts the standard OpenAI embedding models but differs from plain OpenAI in two
ways, both handled entirely through configuration — no code changes are needed:

1. **The endpoint is a per-deployment URL** with a required `api-version` query
   parameter:
   ```
   https://{resource}.openai.azure.com/openai/deployments/{deployment}/embeddings?api-version=2024-02-01
   ```
2. **Authentication uses an `api-key: <key>` header** instead of `Authorization: Bearer <key>`.
   Set `credentials.authHeaderName: api-key` — the raw key is then sent in that
   header, with no `Bearer` prefix.

`authHeaderName` defaults to `Authorization` (the Bearer scheme) when omitted, which is
what plain OpenAI and most local engines expect.

## Catalog entry

Add an entry to the model catalog (`embedding-service-configs.yml`, or your own file
referenced via `embedding.modelConfigFile`). A commented copy of this same example ships
in the default catalog:

```yaml
configs:
  - modelName: text-embedding-3-small
    embeddingProvider: OPENAI_COMPATIBLE
    config:
      providerEndpoint: https://my-resource.openai.azure.com/openai/deployments/text-embedding-3-small/embeddings?api-version=2024-02-01
      modelConfig:
        batchSize: 96
        batchTokenLimit: 120000
        outputDimensions: 1536
        quantization: float
        forwardDimensions: true
      errorHandlingConfig:
        maxRetries: 10
        initialRetryWaitMs: 200
        maxRetryWaitMs: 10000
        jitter: 0.1
      credentials:
        apiKey: "<your-azure-openai-api-key>"
        authHeaderName: api-key
```

## `forwardDimensions` and `outputDimensions`

`outputDimensions` must match the model's native output dimension, and in turn the
vector search index's `numDimensions`. Set `forwardDimensions: true` only for Matryoshka
`text-embedding-3-*` models, which support shrinking their output to a smaller
dimension on request — the value forwarded is the index's resolved dimension, not
necessarily the catalog default. Leave `forwardDimensions` unset (or `false`) for local
engines and non-Matryoshka models, which reject the `dimensions` request field.

## modelName vs. Azure deployment name

> **Pending verification.** Whether Azure requires the wire `model` field (sent as the
> catalog's `modelName`) to match the deployment name in the URL path, or ignores it
> entirely, has not yet been confirmed against a real Azure deployment — see
> [PS4M-14](https://perconadev.atlassian.net/browse/PS4M-14). Until that's resolved,
> the safest choice is to give the catalog entry's `modelName` the same value as your
> Azure deployment name. This section will be updated with the confirmed behavior once
> PS4M-14 completes.

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `Authentication failed (HTTP 401): check the API key and authHeaderName (Authorization: Bearer vs Azure api-key)` | Wrong or missing API key, or `authHeaderName` not set to `api-key`. Azure rejects the default `Authorization: Bearer` scheme. |
| `Got invalid request, fail fast and give up retries.` (HTTP 400/422) | Usually a wrong `api-version`, a malformed deployment path, or a request field Azure doesn't accept. Check the redacted response body in the log line for Azure's specific error. |
| `Got client error from response (check the providerEndpoint path)` (other 4xx) | Wrong endpoint path — verify the resource name, deployment name, and path shape against the Azure portal. |
| Index stuck `PENDING`/`BUILDING` | The embedding server is unreachable or misconfigured; mongot retries per `errorHandlingConfig`. Fix the endpoint/credentials and the index resumes automatically. |

## Metrics

Embedding traffic is tagged with the provider name regardless of which
`OPENAI_COMPATIBLE` backend is in use:

```bash
curl -s localhost:9946/metrics | grep 'provider="OPENAI_COMPATIBLE"'
```

Useful series: `mongot_embeddingClient_inputTokenDistribution_*` (tagged by
`canonicalModel` and `workload`) and `mongot_embeddingClient_invalidRequestCounter`.
