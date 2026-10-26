# CodeGraph and MCP in the development workflow

Use colbymchenry/codegraph, not another similarly named project:
https://github.com/colbymchenry/codegraph

CodeGraph is optional local agent tooling. The application, Maven verification,
and Compose startup must work without it. It is not a Spring dependency or an
application container.

## Setup on the candidate's Mac

After the repository contains initial source code, from its root:

    npm install -g @colbymchenry/codegraph@1.6.2
    codegraph install --target=codex --location=local --yes
    codegraph init --yes
    codegraph status

Use the installed version's help/documentation before changing setup commands.
Record the chosen version. The installer can configure MCP for supported agents;
restart the agent after configuring it. Scope changes to this project and avoid
overwriting an existing user configuration. Other agent clients can use their
supported installer target or a documented local MCP configuration.

Codex discovers project skills in .agents/skills. Other agents can read the
SKILL.md files and the numbered prompts directly.

The local installer writes `.codex/config.toml` with the portable command
`codegraph` and arguments `["serve", "--mcp"]`. It also adds a marker-fenced
section to AGENTS.md; this repository keeps that section as a link to this guide.
It does not configure other projects. Codex loads project MCP configuration only
for trusted projects; restart the agent after installation. Air may require the
server to be enabled through its MCP settings if it does not load this project
configuration. The current session's exposed tools must be checked independently.

## Verified installation — 2026-10-23

Installed npm package `@colbymchenry/codegraph@1.6.2` using the existing Node.js
22.16.0 / npm 11.4.2. `codegraph --version` returned `1.6.2`. The explicit local
installer and `codegraph init --yes` both completed successfully.

`codegraph status` reported an up-to-date index: 49 files (35 Java), 672 nodes,
1,126 edges, and SQLite WAL mode. `codegraph query WebhookApi --json` found the
Java class, receive method, and `POST /webhooks/stripe` route. A separate local
stdio MCP client successfully initialized the server, listed `codegraph_explore`,
and called it with `WebhookApi.receive WorkStore.accept durable webhook receipt`.
The response included WebhookApi and WorkStore source and their caller link.
This proves the installed MCP server works; it does not claim that the already
running Air agent has dynamically loaded it. No global agent settings changed.

The generated index remains ignored by Git. The MCP configuration uses no
absolute machine paths or credentials. This tooling-only installation did not
change application source or rerun the Maven application suite.

## Use

The documented default MCP tool is codegraph_explore. Its CLI equivalent is:

    codegraph explore "How does the Stripe webhook request durable sync work?"
    codegraph explore "What is affected if the retry policy changes?"

These become meaningful once those source components exist. Check tool
availability/index status rather than pretending the graph is already initialized.
CodeGraph's MCP watcher is documented to update the index on source changes.
If a watcher is not active or the index is stale, use codegraph sync and verify.

Use graph results for call paths and impact questions; complement them with
config, contract, and source inspection. Java and Spring route extraction are
documented, but runtime proxy/reflection behavior and remote HTTP boundaries
still need explicit reasoning and executable checks.

After the clean-code refactor, the ingress names are StripeWebhookController and
AcceptStripeWebhook. CLI exploration of those owners, WorkStore,
ReconciliationStore/ReconciliationLifecycle, and DemoControls/ErpStore was used
for the refactor; see [the review](refactoring-review.md). Installation statistics
above describe the earlier snapshot, not the current expanded source tree.

## Repository hygiene

Ignore the generated .codegraph directory and machine-specific settings.
Commit portable instructions, not absolute laptop paths or generated indexes.
Document real usage in docs/ai-usage.md. Avoid mandatory graph-based CI or a
benchmarking project inside the take-home scope.

The custom operational MCP server is a separate backlog item described in
docs/backlog.md. The actual development integration here is CodeGraph over MCP.
