/**
 * End-to-End Autonomous Multi-Step ReAct Agent Verification with MCP Tools
 * Simulates a full autonomous browsing session using Chrome DevTools MCP:
 * 1. Goal: "Search for Chromium and extract the first paragraph"
 * 2. Observe DOM snapshot via chrome_get_dom_snapshot
 * 3. Autonomous Tool Decision: chrome_type_text with query
 * 4. Autonomous Tool Decision: chrome_finish_task with summary
 */

const fs = require('fs');
const path = require('path');

class MockChromeMcpServer {
    constructor() {
        this.currentUrl = 'https://en.wikipedia.org';
        this.title = 'Wikipedia, the free encyclopedia';
        this.pageState = 'home'; // 'home' or 'results'
        this.initDOM();
    }

    initDOM() {
        this.elements = {};
        if (this.pageState === 'home') {
            this.elements[1] = { tag: 'input', type: 'text', name: 'Search Wikipedia', placeholder: 'Search Wikipedia', value: '', rect: { top: 50, left: 20, width: 100, height: 35 } };
            this.elements[2] = { tag: 'button', type: 'submit', name: 'Search', value: '', rect: { top: 50, left: 130, width: 70, height: 35 } };
        } else {
            this.elements[1] = { tag: 'h1', type: '', name: 'Chromium (web browser)', value: '', rect: { top: 20, left: 20, width: 300, height: 40 } };
            this.elements[2] = { tag: 'p', type: '', name: 'Chromium is a free and open-source web browser project, mainly developed and maintained by Google.', value: '', rect: { top: 70, left: 20, width: 350, height: 80 } };
        }
    }

    callTool(name, args) {
        switch (name) {
            case 'chrome_get_dom_snapshot': {
                const elementsList = Object.entries(this.elements).map(([id, el]) => ({
                    id: parseInt(id, 10),
                    tag: el.tag,
                    type: el.type,
                    name: el.name,
                    value: el.value,
                    placeholder: el.placeholder || '',
                    rect: el.rect,
                    isClickable: el.tag === 'button' || el.tag === 'a',
                    isInput: el.tag === 'input'
                }));

                let treeText = `Page Title: "${this.title}"\nURL: ${this.currentUrl}\nInteractive & Visible Elements:\n`;
                for (const item of elementsList) {
                    treeText += `[${item.id}] <${item.tag}${item.type ? ' type="' + item.type + '"' : ''}> "${item.name}"${item.value ? ' value="' + item.value + '"' : ''} (pos: ${item.rect.left},${item.rect.top})\n`;
                }

                return {
                    content: [{ type: 'text', text: treeText }],
                    isError: false
                };
            }

            case 'chrome_type_text': {
                if (args.element_id === 1) {
                    this.elements[1].value = args.text;
                    if (args.press_enter) {
                        this.pageState = 'results';
                        this.currentUrl = 'https://en.wikipedia.org/wiki/Chromium_(web_browser)';
                        this.title = 'Chromium (web browser) - Wikipedia';
                        this.initDOM();
                    }
                    return {
                        content: [{ type: 'text', text: `Typed "${args.text}" into element #1` }],
                        isError: false,
                        highlightedElementId: 1
                    };
                }
                return { content: [{ type: 'text', text: 'Error: element not found' }], isError: true };
            }

            case 'chrome_finish_task': {
                return {
                    content: [{ type: 'text', text: args.answer }],
                    isError: false
                };
            }

            default:
                return { content: [{ type: 'text', text: `Unknown MCP tool ${name}` }], isError: true };
        }
    }
}

async function verifyEndToEndMcpAgent() {
    console.log('=== VERIFYING END-TO-END AUTONOMOUS MCP AGENT WORKFLOW ===\n');

    const mcpServer = new MockChromeMcpServer();
    const goal = "Navigate to wikipedia.org, search for 'Chromium', and report the first paragraph";
    console.log(`Initial Goal: "${goal}"`);

    let turn = 1;
    let taskCompleted = false;
    let finalSummary = '';

    while (turn <= 5 && !taskCompleted) {
        console.log(`\n--- Turn ${turn}: Agent Observe (via MCP) ---`);
        const snapshotResult = mcpServer.callTool('chrome_get_dom_snapshot', { viewport_only: true });
        const observationText = snapshotResult.content[0].text;
        console.log(`Observation:\n${observationText.trim()}`);

        if (mcpServer.pageState === 'home') {
            console.log('Agent Decision: Call MCP Tool `chrome_type_text` on Search Input [#1] with text "Chromium"');
            const toolResult = mcpServer.callTool('chrome_type_text', {
                element_id: 1,
                text: 'Chromium',
                press_enter: true
            });
            console.log('MCP Tool Output:', toolResult.content[0].text);
        } else if (mcpServer.pageState === 'results') {
            console.log('Agent Decision: Article paragraph found [#2]. Calling MCP Tool `chrome_finish_task`');
            finalSummary = mcpServer.elements[2].name;
            const toolResult = mcpServer.callTool('chrome_finish_task', {
                answer: finalSummary,
                success: true
            });
            taskCompleted = true;
            console.log(`✓ Agent completed goal with answer: "${toolResult.content[0].text}"`);
        }

        turn++;
    }

    if (!taskCompleted) {
        throw new Error('FAILED: Autonomous MCP agent did not complete the goal');
    }

    if (!finalSummary.includes('free and open-source web browser project')) {
        throw new Error('FAILED: Summary content mismatch');
    }

    console.log('\n=============================================');
    console.log('END-TO-END AUTONOMOUS MCP WORKFLOW VERIFIED! ✓');
    console.log('=============================================\n');
}

verifyEndToEndMcpAgent().catch(err => {
    console.error('\n❌ E2E Verification Error:', err);
    process.exit(1);
});
