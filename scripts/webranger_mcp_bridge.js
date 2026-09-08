#!/usr/bin/env node
/**
 * WebRanger Mobile MCP Bridge (Node.js)
 * Zero-dependency stdio bridge for Claude Code, Cursor, and Termux terminal agents.
 * Connects stdin/stdout JSON-RPC to the WebRanger Android background server.
 */

const http = require('http');
const readline = require('readline');

const HOST = process.env.WEBRANGER_HOST || '127.0.0.1';
const PORT = parseInt(process.env.WEBRANGER_PORT || '8765', 10);

function sendMcpRequest(payload) {
    return new Promise((resolve, reject) => {
        const data = Buffer.from(JSON.stringify(payload), 'utf8');
        const req = http.request({
            hostname: HOST,
            port: PORT,
            path: '/mcp',
            method: 'POST',
            headers: {
                'Content-Type': 'application/json; charset=utf-8',
                'Content-Length': data.length
            },
            timeout: 30000
        }, (res) => {
            let body = '';
            res.setEncoding('utf8');
            res.on('data', chunk => { body += chunk; });
            res.on('end', () => {
                try {
                    resolve(JSON.parse(body));
                } catch (err) {
                    resolve({
                        jsonrpc: '2.0',
                        id: payload.id || 1,
                        error: { code: -32700, message: 'Invalid JSON response from WebRanger' }
                    });
                }
            });
        });

        req.on('error', (err) => {
            resolve({
                jsonrpc: '2.0',
                id: payload.id || 1,
                error: { code: -32000, message: `Could not connect to WebRanger at http://${HOST}:${PORT}: ${err.message}` }
            });
        });

        req.on('timeout', () => {
            req.destroy();
            resolve({
                jsonrpc: '2.0',
                id: payload.id || 1,
                error: { code: -32000, message: 'WebRanger request timed out' }
            });
        });

        req.write(data);
        req.end();
    });
}

function startStdioLoop() {
    process.stderr.write(`[WebRanger Node Bridge] Connected to http://${HOST}:${PORT}\n`);

    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
        terminal: false
    });

    rl.on('line', async (line) => {
        const trimmed = line.trim();
        if (!trimmed) return;

        try {
            const req = JSON.parse(trimmed);
            const resp = await sendMcpRequest(req);
            process.stdout.write(JSON.stringify(resp) + '\n');
        } catch (e) {
            const err = { jsonrpc: '2.0', id: null, error: { code: -32700, message: 'Parse error' } };
            process.stdout.write(JSON.stringify(err) + '\n');
        }
    });
}

startStdioLoop();
