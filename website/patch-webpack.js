// This file patches webpack to fix Node.js 25 compatibility
// Must be loaded before any webpack code

const { defineProperty } = Object;
const webpack = require('webpack');

// Monkey patch the ProgressPlugin to skip validation
const OriginalProgressPlugin = webpack.ProgressPlugin;

// Create a wrapper that bypasses the constructor validation
function PatchedProgressPlugin(options) {
  // Call parent constructor with safe defaults
  if (typeof options === 'function') {
    return new OriginalProgressPlugin(options);
  }
  // Ignore invalid options, pass empty object
  return new OriginalProgressPlugin({});
}

// Copy static properties
Object.setPrototypeOf(PatchedProgressPlugin, OriginalProgressPlugin);
Object.setPrototypeOf(PatchedProgressPlugin.prototype, OriginalProgressPlugin.prototype);

// Replace the ProgressPlugin
webpack.ProgressPlugin = PatchedProgressPlugin;

// Also patch it on any cached webpack instances
if (require.cache) {
  for (const key of Object.keys(require.cache)) {
    if (key.includes('webpack') && require.cache[key]?.exports?.ProgressPlugin) {
      require.cache[key].exports.ProgressPlugin = PatchedProgressPlugin;
    }
  }
}

module.exports = webpack;
