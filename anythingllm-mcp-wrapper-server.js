const http = require('http');
const https = require('https');
const { URL } = require('url');

const TARGET_BASE = process.env.ANYTHINGLLM_BASE_URL || 'http://localhost:3001/api/v1';
const DOCS_BASE = process.env.ANYTHINGLLM_DOCS_URL || 'http://localhost:3001/api/docs';
const API_KEY = process.env.ANYTHINGLLM_API_KEY || '';
const PORT = Number(process.env.PORT || 4001);

function getClient(url) {
  return url.protocol === 'https:' ? https : http;
}

function proxyRequest(req, res, targetUrl) {
  const parsedTarget = new URL(targetUrl);
  const outgoingOptions = {
    protocol: parsedTarget.protocol,
    hostname: parsedTarget.hostname,
    port: parsedTarget.port,
    path: parsedTarget.pathname + parsedTarget.search,
    method: req.method,
    headers: {
      ...req.headers,
      host: parsedTarget.host,
    },
  };

  if (API_KEY) {
    outgoingOptions.headers['authorization'] = `Bearer ${API_KEY}`;
  }

  const proxy = getClient(parsedTarget).request(outgoingOptions, (proxyRes) => {
    res.writeHead(proxyRes.statusCode, proxyRes.headers);
    proxyRes.pipe(res, { end: true });
  });

  proxy.on('error', (err) => {
    console.error('Proxy request failed:', err);
    if (!res.headersSent) {
      res.writeHead(502, { 'Content-Type': 'text/plain' });
    }
    res.end('Bad Gateway: ' + err.message);
  });

  req.pipe(proxy, { end: true });
}

const server = http.createServer((req, res) => {
  const incomingUrl = new URL(req.url, `http://${req.headers.host}`);
  const path = incomingUrl.pathname;

  let targetUrl;
  if (path === '/api/docs') {
    targetUrl = DOCS_BASE;
    if (incomingUrl.search) targetUrl += incomingUrl.search;
  } else if (path.startsWith('/api/docs/')) {
    targetUrl = DOCS_BASE.replace(/\/$/, '') + path.substring('/api/docs'.length);
    if (incomingUrl.search) targetUrl += incomingUrl.search;
  } else if (path.startsWith('/docs')) {
    targetUrl = DOCS_BASE.replace(/\/$/, '') + path.substring('/docs'.length);
    if (incomingUrl.search) targetUrl += incomingUrl.search;
  } else {
    const apiPath = path.startsWith('/api/v1') ? path.substring('/api/v1'.length) : path;
    targetUrl = TARGET_BASE.replace(/\/$/, '') + apiPath;
    if (incomingUrl.search) targetUrl += incomingUrl.search;
  }

  console.log(`${req.method} ${req.url} -> ${targetUrl}`);
  proxyRequest(req, res, targetUrl);
});

server.listen(PORT, () => {
  console.log(`AnythingLLM MCP wrapper listening on http://localhost:${PORT}`);
  console.log(`Target base: ${TARGET_BASE}`);
  console.log(`Docs base: ${DOCS_BASE}`);
});
