# AnythingLLM MCP Wrapper Server

This is a local wrapper that proxies requests to the AnythingLLM server while preserving the original base URL configuration.

## Usage

1. Run the wrapper:

```bash
node anythingllm-mcp-wrapper-server.js
```

2. Set these environment variables if needed:

- `ANYTHINGLLM_BASE_URL` - base API URL for AnythingLLM (default: `http://localhost:3001/api/v1`)
- `ANYTHINGLLM_DOCS_URL` - docs URL for AnythingLLM (default: `http://localhost:3001/api/docs`)
- `ANYTHINGLLM_API_KEY` - API key forwarded to backend as `Authorization: Bearer <key>`
- `PORT` - local wrapper port (default: `4001`)

## Example

```bash
ANYTHINGLLM_BASE_URL=http://localhost:3001/api/v1 \
ANYTHINGLLM_API_KEY=XPBFZ20-J3G4HPN-P0TXQGD-BMMRHWX \
node anythingllm-mcp-wrapper-server.js
```

Then point your MCP configuration to the wrapper URL if needed.

## MCP integration

If you want the MCP entry to use this wrapper, configure it to call the wrapper server rather than the raw AnythingLLM package.

For example, if your wrapper listens on port `4001`:

- `ANYTHINGLLM_BASE_URL` stays as `http://localhost:3001/api/v1`
- `ANYTHINGLLM_API_KEY` stays unchanged
- Use the wrapper endpoint for any MCP HTTP proxy configuration

This keeps the imported AnythingLLM URL unchanged while still wrapping the full API surface.
