# @mongodb-js/search-index-schema

MongoDB Atlas Search Index JSON schemas.

This package provides JSON Schema definitions for MongoDB Atlas Search Indexes, including full-text search indexes and vector search indexes.

## Installation

```bash
npm install @mongodb-js/search-index-schema
```

## Package Structure

- **`schema/`** - source JSON schema files
- **`output/`** - bundled JSON schema files generated from source schemas with all `$ref` pointers resolved
- **`variants.json`** - maps feature-flag combinations to the schema variant they select
- **`index.js`** / **`index.d.ts`** - the `getSchema` selector that returns the right variant for a set of enabled flags

## Usage

Rather than importing a schema file by path, consumers pass the set of enabled feature flags and let
the package select the correct variant:

```js
const { getSchema } = require('@mongodb-js/search-index-schema');

// Base search index schema (no feature flags enabled)
const searchSchema = getSchema('search');

// Vector search index schema with auto-embedding public preview enabled
const vectorSchema = getSchema('vectorSearch', ['autoEmbeddingPublicPreview']);
```

`getSchema(indexType, enabledFlags)` evaluates the variants for the given index type in priority
order (declared in `variants.json`) and returns the first whose required flags are all present in
`enabledFlags`. The base variant has no required flags and is always the fallback. Unknown flags are
ignored. The returned object is the bundled schema from `output/` with all `$ref` pointers resolved.

## Schema Files

The package includes schemas for different search index types:

### Search Indexes

- `search/index.json` - Search index schema for use in JSON editors, which excludes metadata such as `name`, `database`, and `collectionName`

### Vector Search Indexes

- `vectorSearch/index.json` - Vector search index schema for use in JSON editors, which excludes metadata such as `name`, `database`, and `collectionName`

## License

Apache-2.0

## Links

- [MongoDB Atlas Search Documentation](https://www.mongodb.com/docs/atlas/atlas-search/)
- [GitHub Repository](https://github.com/mongodb/mongot)
- [Issues](https://github.com/mongodb/mongot/issues)
