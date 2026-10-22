# CodeGraph and MCP in the development workflow

Use colbymchenry/codegraph, not another similarly named project:
https://github.com/colbymchenry/codegraph

CodeGraph is optional local agent tooling. The application, Maven verification,
and Compose startup must work without it. It is not a Spring dependency or an
application container.

## Setup on the candidate's Mac

After the repository contains initial source code, from its root:

    npm install -g @colbymchenry/codegraph
    codegraph install --target=codex --location=local
    codegraph init
    codegraph status

Use the installed version's help/documentation before changing setup commands.
Record the chosen version. The installer can configure MCP for supported agents;
restart the agent after configuring it. Scope changes to this project and avoid
overwriting an existing user configuration. Other agent clients can use their
supported installer target or a documented local MCP configuration.

Codex discovers project skills in .agents/skills. Other agents can read the
SKILL.md files and the numbered prompts directly.

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

## Repository hygiene

Ignore the generated .codegraph directory and machine-specific settings.
Commit portable instructions, not absolute laptop paths or generated indexes.
Document real usage in docs/ai-usage.md. Avoid mandatory graph-based CI or a
benchmarking project inside the take-home scope.

The custom operational MCP server is a separate backlog item described in
docs/backlog.md. The actual development integration here is CodeGraph over MCP.
