/**
 * Custom Docusaurus plugin to fix Node.js 25+ compatibility
 * by completely disabling webpack ProgressPlugin
 */
module.exports = function disableProgressPlugin(context, options) {
  return {
    name: 'disable-progress-plugin',
    configureWebpack(config, isServer, utils) {
      // Return a function to modify the config directly
      return (config) => {
        // Remove ProgressPlugin from plugins
        if (config.plugins) {
          config.plugins = config.plugins.filter(
            (plugin) => !plugin || plugin.constructor.name !== 'ProgressPlugin'
          );
        }
        // Disable infrastructure logging progress
        config.infrastructureLogging = {
          ...config.infrastructureLogging,
          level: 'error',
        };
        return config;
      };
    },
  };
};
