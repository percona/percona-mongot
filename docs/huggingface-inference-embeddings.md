# Using the Hugging Face Inference API as an Embedding Provider

`mongot`'s `HUGGINGFACE_INFERENCE` embedding provider generates embeddings for
`autoEmbed` vector search indexes through Hugging Face's hosted Inference API, so you
don't have to run a model server yourself. This page covers only the Hugging
Face-specific configuration. It assumes automatic embedding generation is already set
up; see the `embedding:` section of your `mongot.conf` and the model catalog
`embedding-service-configs.yml`.

## Which Hugging Face API this uses

Hugging Face replaced the old serverless "Inference API" (`api-inference.huggingface.co`,
now retired) with **Inference Providers**, a router that sends requests to Hugging Face's
own `hf-inference` service or to partner providers. mongot calls the `hf-inference`
feature-extraction pipeline:

```
POST https://router.huggingface.co/hf-inference/models/{modelId}/pipeline/feature-extraction
Authorization: Bearer hf_...
Content-Type: application/json

{"inputs": ["first text", "second text"], "truncate": true}
```

The response is a JSON array with one float vector per input, in request order.

That request and response format is also what Hugging Face
[Text Embeddings Inference](https://github.com/huggingface/text-embeddings-inference)
(TEI) serves on its native `/embed` route. So the same provider can point at a dedicated
Inference Endpoint or a self-hosted TEI server by setting `providerEndpoint` (see
[Custom endpoints](#custom-endpoints-dedicated-inference-endpoints-and-self-hosted-tei)).

## Prerequisites

1. **A Hugging Face account.**
2. **An access token.** Create a *fine-grained* token at
   <https://huggingface.co/settings/tokens> with the **"Make calls to Inference
   Providers"** permission. Store it like any other secret: the catalog file that holds
   it should be readable only by the `mongot` user.
3. **A model that `hf-inference` serves for feature extraction.** It must be a sentence
   embedding model with a pooling layer, so it returns one vector per input, not one
   per token. Browse the
   [available models](https://huggingface.co/models?inference_provider=hf-inference&pipeline_tag=feature-extraction)
   or run `hf models ls --warm --pipeline-tag feature-extraction`. `hf-inference` serves
   only a curated set of CPU models, mostly popular sentence-transformers models such as
   `BAAI/bge-small-en-v1.5` or `sentence-transformers/all-MiniLM-L6-v2`. It does not
   serve every embedding model on the Hub.
4. **The model's native output dimension.** Look for `hidden_size` in the model's
   `config.json`. For example, `BAAI/bge-small-en-v1.5` has 384 dimensions.

## Free tier and billing

Inference Providers is pay-as-you-go, with a monthly allowance of included credits.
When this page was written, the allowance was **$0.10/month for free accounts** (Hugging
Face says this is subject to change) and $2.00/month for PRO users and per seat for
Team/Enterprise organizations. `hf-inference` bills by compute time × hardware price.
CPU embedding requests for small models are cheap, but an initial sync of a large
collection can still use up a free allowance quickly. Check
<https://huggingface.co/docs/inference-providers/pricing> for current numbers, and track
your usage at <https://huggingface.co/settings/billing>.

When the credits run out, requests are rejected, typically with **HTTP 402 (Payment
Required)**. mongot treats any 4xx other than 400/408/422/429 as non-retryable and logs
`Payment required (HTTP 402)` for that status; add credits or upgrade the plan.

## Catalog entry

Add an entry to the model catalog (`embedding-service-configs.yml`, or your own file
referenced via `embedding.modelConfigFile`). A commented copy of this example ships in
the default catalog:

```yaml
configs:
  - modelName: bge-small-en-v1.5
    embeddingProvider: HUGGINGFACE_INFERENCE
    config:
      modelConfig:
        modelId: BAAI/bge-small-en-v1.5
        batchSize: 32
        batchTokenLimit: 120000
        outputDimensions: 384
        quantization: float
      errorHandlingConfig:
        maxRetries: 10
        initialRetryWaitMs: 500
        maxRetryWaitMs: 30000
        jitter: 0.1
      credentials:
        apiToken: "<your-hugging-face-access-token>"
```

Restart mongot after editing the catalog. A `HUGGINGFACE_INFERENCE` entry without a custom
`providerEndpoint` needs an `apiToken` for every workload (`query`, `collectionScan` and
`changeStream`). A workload's `credentials` override replaces the base `credentials` entirely,
so a workload uses its override's `apiToken` if it has a `credentials` block, otherwise the base
`credentials.apiToken`. Otherwise the entry is skipped at startup with a `Skipping Hugging Face
embedding model` warning; the other models still load.

### Fields

| Field | Required | Meaning |
| --- | --- | --- |
| `modelName` | yes | The name `autoEmbed` index definitions reference (and the `canonicalModel` metrics tag). mongot lowercases it. |
| `modelConfig.modelId` | no | Exact, **case-sensitive** Hub repo id, e.g. `BAAI/bge-small-en-v1.5`. Defaults to `modelName`. Set it whenever the repo id has an organization prefix or uppercase letters: the router does not resolve a lowercased id. |
| `modelConfig.outputDimensions` | yes | Must equal the model's native dimension. It sizes the index; a mismatch fails with `returned N-dimensional vectors but the index expects M`. |
| `modelConfig.batchSize` | no | Inputs per request (default 32). Lower it if you see HTTP 413. |
| `modelConfig.truncate` | no | Default `true`: inputs longer than the model's maximum sequence length (512 tokens for `bge-small-en-v1.5`) are truncated instead of failing the whole batch. |
| `modelConfig.normalize` | no | Sent only when set; otherwise the server's default applies (TEI normalizes by default). |
| `modelConfig.queryPrefix` / `documentPrefix` | no | Prepended to query-time and indexing-time inputs respectively, for asymmetric models (e.g. e5: `"query: "` / `"passage: "`). The separator is part of the value. |
| `modelConfig.quantization` | no | Only `float` is supported. |
| `credentials.apiToken` | yes for the hosted API | Hugging Face access token, sent as `Authorization: Bearer <token>`. |
| `providerEndpoint` | no | Replaces the router URL verbatim (see below). |

Per-workload overrides (`query`, `collectionScan`, `changeStream`) accept `modelConfig`
and `credentials` blocks just like the other providers. For example, you can use a
separate token for query traffic.

## Index definition

Reference the catalog `modelName` from an `autoEmbed` field; `numDimensions` must match
`outputDimensions`:

```javascript
db.movies.createSearchIndex("plot_hf", "vectorSearch", {
  fields: [
    { type: "autoEmbed", path: "plot", model: "bge-small-en-v1.5", modality: "text",
      numDimensions: 384, similarity: "cosine" }
  ]
});
```

Then query it with `$vectorSearch` using a text `query`, the same as with any other
auto-embedding provider.

## Custom endpoints (dedicated Inference Endpoints and self-hosted TEI)

Set `providerEndpoint` to send requests somewhere other than the shared router:

- **Dedicated Hugging Face Inference Endpoint** running TEI:
  `https://<your-endpoint>.endpoints.huggingface.cloud/embed`, with your token in
  `credentials.apiToken`.
- **Self-hosted TEI**, e.g.
  `docker run -p 8080:80 ghcr.io/huggingface/text-embeddings-inference:cpu-1.8 --model-id BAAI/bge-small-en-v1.5`,
  with `providerEndpoint: http://<host>:8080/embed`. A token is optional here: entries
  with a `providerEndpoint` load without one, and no `Authorization` header is sent.

The global `embedding.providerEndpoint` setting in `mongot.conf` applies to Voyage
models only and does not affect these entries.

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `Skipping Hugging Face embedding model '…': no credentials.apiToken configured` (startup) | The catalog entry has no token. Add `credentials.apiToken`. |
| `Authentication failed (HTTP 401/403)` | Missing, invalid, or under-privileged token. Use a fine-grained token with "Make calls to Inference Providers". |
| `Payment required (HTTP 402)` | Usually means the monthly Inference Providers credits are exhausted. |
| `Got client error (HTTP 404)` | `hf-inference` doesn't serve that model for feature extraction, or `modelId` has the wrong case or organization prefix. |
| `Got client error (HTTP 413)` | Request too large. Lower `batchSize`. |
| `Rate limit exceeded (HTTP 429)` or `HTTP 503` | Throttling, or a cold model still loading. These are retried with backoff per `errorHandlingConfig`. |
| `Model returned token-level embeddings` | The model has no pooling layer. Pick a sentence-embedding model. |
| `returned N-dimensional vectors but the index expects M` | `outputDimensions` (and the index's `numDimensions`) don't match the model. |

## Metrics

Embedding traffic is tagged `provider="HUGGINGFACE_INFERENCE"`:

```bash
curl -s localhost:9946/metrics | grep 'provider="HUGGINGFACE_INFERENCE"'
```

`mongot_embeddingClient_invalidRequestCounter` counts requests rejected for
non-retryable reasons (4xx). The Hugging Face API doesn't report token usage, so the
`inputTokenDistribution` series is not populated for this provider.
