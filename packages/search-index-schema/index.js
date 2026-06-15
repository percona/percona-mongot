const variants = require('./variants.json');

// Bundled schemas (with all $ref pointers resolved) are imported via a build-time generated map
// (scripts/bundle.js writes output/schemas.generated.js with one static require per variant), so
// getSchema works in browser/bundler builds without runtime fs/path access, and the map stays in
// sync with the variant files automatically.
const bundledSchemas = require('./output/schemas.generated.js');

/**
 * Return the bundled JSON schema for an index type given the set of enabled feature flags.
 *
 * Variants for each index type are declared in priority order in variants.json. The first variant
 * whose required flags are all enabled is selected, falling back to the base variant (no required
 * flags, listed last) when no feature-flagged variant matches.
 *
 * @param {string} indexType "search" or "vectorSearch"
 * @param {string[]} [enabledFlags] feature flags that are enabled
 * @returns {object} the bundled JSON schema (an independent copy; safe to mutate)
 */
function getSchema(indexType, enabledFlags = []) {
  const candidates = Object.hasOwn(variants, indexType) ? variants[indexType] : undefined;
  if (!candidates) {
    throw new Error(
      `Unknown index type "${indexType}". Expected one of: ${Object.keys(variants).join(', ')}.`
    );
  }

  const enabled = new Set(enabledFlags);
  const variant = candidates.find((v) => v.flags.every((flag) => enabled.has(flag)));

  if (!variant) {
    // Unreachable in practice: every index type declares a base variant with no required flags.
    throw new Error(`No schema variant matched for index type "${indexType}".`);
  }

  const schema = bundledSchemas[variant.schema];
  if (!schema) {
    throw new Error(`No bundled schema found for "${variant.schema}".`);
  }
  // Return an independent copy (JSON round-trip is portable across Node/browsers and lossless for
  // these pure-JSON schemas) so callers can't mutate the shared, cached module object.
  return JSON.parse(JSON.stringify(schema));
}

module.exports = { getSchema };
