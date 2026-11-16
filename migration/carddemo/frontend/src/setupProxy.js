// Dev server only (`npm start`). package.json's "proxy" skips requests that
// accept text/html, so the statement iframe (/api/statements/{id}/html) and the
// Download PDF/HTML links would get index.html. Send every /api request to the
// backend instead.
const { createProxyMiddleware } = require('http-proxy-middleware');

module.exports = function setupProxy(app) {
  app.use(createProxyMiddleware('/api', { target: 'http://localhost:8080', changeOrigin: false }));
};
