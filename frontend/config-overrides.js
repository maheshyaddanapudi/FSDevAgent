module.exports = function override(config, env) {
  // Add webpack dev server configuration
  config.devServer = {
    ...config.devServer,
    allowedHosts: 'all',
    host: '0.0.0.0',
    port: 3000
  };
  
  return config;
};
