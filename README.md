# BMX Build Agent

An AI agent web application that helps riders plan, check, price and visualize a custom BMX build. A rider can describe the bike they want in plain language and the agent plans a complete build by searching the parts catalog, adding parts, checking compatibility and fixing anything that doesn't fit, then explains its choices. Riders can also build manually, compare store prices, optimize for a budget, ask BMX questions and get a riding style recommendation.

EECS3311 Software Design course project, Fall 2026.

- **Stage 1 design report:** [docs/Stage1-Report.md](docs/Stage1-Report.md)
- **Stage 2 implementation report:** `docs/stage2-report.docx`
- **UML diagrams (UMLet sources and images):** [docs/diagrams/](docs/diagrams/)

## Features

| ID | Feature | AI | GUI | CLI |
|---|---|---|---|---|
| F01 | Rider profile and style recommendation | Hybrid | My Profile | `recommend` |
| F02 | AI build planner | AI agent | AI Planner | `plan` |
| F03 | Parts catalog search and filter | Deterministic | Catalog | `search` |
| F04 | Interactive build configurator | Deterministic | Build | `new`, `use`, `add`, `remove`, `show` |
| F05 | Compatibility checker | Deterministic | Build | `check`, `fits` |
| F06 | Price comparison | Deterministic | Catalog, Build | `prices`, `buildprices` |
| F07 | Budget optimizer | Hybrid | Build | `optimize` |
| F08 | Live 2D build preview | Deterministic | Build | (GUI only) |
| F09 | BMX Q&A assistant | AI agent (retrieval) | Ask | `ask` |
| F10 | Saved builds and side-by-side comparison | Deterministic | My Builds | `builds`, `compare`, `rename`, `copy`, `delete` |
| F11 | Upgrade path advisor | Hybrid | Upgrade | `upgrade` |

## Stack

- **Back end:** Java 21, Spring Boot 4 (REST API, JPA/Hibernate), PostgreSQL
- **AI:** Groq-hosted Llama (`llama-3.3-70b-versatile` by default) through a custom tool-calling agent loop
- **GUI:** React 18 + Vite, built into the Spring Boot app and served at `http://localhost:8080`
- **CLI:** Spring Boot `CommandLineRunner`, enabled with the `cli` profile
- **Tests:** JUnit 5 and Mockito

## Prerequisites

- **JDK 21 or newer** (the Maven wrapper is included, so Maven itself isn't needed)
- **PostgreSQL** running locally on port 5432
- **A Groq API key** (free at <https://console.groq.com>, under API Keys). Without one the app still runs and the AI features fall back to their non-AI behaviour.
- **Node 18+** only if you want to change the GUI. The built GUI is already committed.

## Setup

1. **Create the database** (in psql, pgAdmin or IntelliJ's Database tool):
   ```sql
   CREATE DATABASE bmx_builder;
   ```

2. **Create your local config.** Copy the example file and fill in your own values:
   ```bash
   cd backend
   cp .env.example .env          # Windows PowerShell: Copy-Item .env.example .env
   ```
   Then edit `backend/.env`:
   ```
   DB_USERNAME=postgres
   DB_PASSWORD=your_local_postgres_password
   GROQ_API_KEY=your_groq_api_key
   ```
   `.env` is gitignored, so real credentials are never committed. Only `.env.example`, which holds placeholders, is in the repository.

Tables are created automatically on first start (`spring.jpa.hibernate.ddl-auto=update`) and the sample parts catalog is loaded by `SampleDataLoader`.

## Running

All commands run from the `backend` folder.

### GUI

```bash
./mvnw spring-boot:run            # Windows: .\mvnw.cmd spring-boot:run
```

Then open **http://localhost:8080**.

### CLI

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=cli
# Windows PowerShell:
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=cli"
```

The CLI starts an interactive prompt (no web server). Type `help` for every command. An example session:

```
bmx> recommend --height 175 --spots street --goals grind ledges and rails
bmx> plan street bike under 2000, no brakes
bmx> show
bmx> check
bmx> ask could I use a spanish bottom bracket with this frame?
bmx> buildprices
bmx> copy
bmx> optimize 1500 --lock FRAME
bmx> optimize 1500 --lock FRAME --apply
bmx> compare 1 2
bmx> upgrade 400 --style STREET
bmx> upgrade 400 --style STREET --apply-stage 1
bmx> upgrade 300 --describe Fit Begin frame, stock complete with plastic pedals
bmx> search odyssey --category FORK --max 200
bmx> prices 4
bmx> quit
```

In IntelliJ you can also make a Maven run configuration with the command `spring-boot:run -Dspring-boot.run.profiles=cli` and the working directory set to `backend`.

### Tests

```bash
./mvnw test                       # Windows: .\mvnw.cmd test
```

The unit tests need no database or API key: repositories are mocked and the LLM is replaced by a scripted fake (`ScriptedLLMClient`). `BackendApplicationTests` starts the full app, so it needs PostgreSQL running.

### Changing the GUI

```bash
cd frontend
npm install
npm run dev      # GUI on http://localhost:5173, calls forwarded to the backend on :8080
npm run build    # rebuilds the GUI into backend/src/main/resources/static
```

## REST API

| Method | Path | Feature |
|---|---|---|
| GET | `/api/parts?keyword=&category=&minPrice=&maxPrice=&style=` | F03 |
| GET | `/api/parts/{id}`, `/api/parts/{id}/prices?refresh=` | F03, F06 |
| POST / GET | `/api/builds?name=`, `/api/builds` | F04, F10 |
| GET / PATCH / DELETE | `/api/builds/{id}` (`PATCH ?name=` renames) | F04, F10 |
| PUT / DELETE | `/api/builds/{id}/parts/{SLOT}?partId=` | F04 |
| GET | `/api/builds/{id}/compatibility`, `/api/builds/{id}/compatible-parts?slot=` | F05 |
| GET | `/api/builds/{id}/prices` | F06 |
| POST | `/api/builds/{id}/optimize?target=&locked=&style=`, `/api/builds/{id}/apply-swaps?swaps=FORK:20,...` | F07 |
| POST | `/api/builds/{id}/duplicate`; GET `/api/builds/compare?ids=1,2` | F10 |
| POST | `/api/agent/plan?request=&name=` | F02 |
| POST | `/api/agent/ask?question=&conversationId=&buildId=` | F09 |
| POST | `/api/agent/recommend-style?heightCm=&experience=&spots=&goals=&budget=&wantsBrakes=` | F01 |
| POST | `/api/agent/upgrade?buildId=&description=&budget=&style=` | F11 |

Errors come back as JSON, e.g. `{"error": "Build 9 not found"}`, with status 400, 404 or 503.

## How the agent works

`AgentOrchestrator` runs a tool-calling loop. It sends the conversation and the tools allowed for the task to the LLM, runs any tools the model asks for through `ToolRegistry`, feeds the results back, and repeats until the model answers or a step limit is reached. The LLM only decides; every action goes through deterministic code (catalog search, `BikeBuild.setPart`, `CompatibilityChecker`, knowledge retrieval), and finished builds are re-checked without the LLM. `ConversationMemory` keeps recent turns so follow-up questions work. Every AI feature has a non-AI fallback if Groq can't be reached.

## Design patterns

| Pattern | Where | Classes |
|---|---|---|
| Composite | Bike made of assemblies and parts; totals by recursion | `BikeComponent`, `Part` (leaf), `Assembly` (composite), `BikeBuild` |
| Observer | Compatibility re-checked whenever a build changes | `BikeBuild` (subject), `BuildChangeListener`, `CompatibilityMonitor` |
| Facade | One entry point for the GUI controllers and the CLI | `BuildService` |
| Adapter | Groq's API adapted to the agent's LLM interface | `LLMClient` (target), `GroqClient` (adapter) |
| Command | Each agent tool is a command the LLM can invoke | `AgentTool`, `ToolRegistry` (invoker), the tools |
| Strategy | Part ranking per riding style, used by the budget optimizer and upgrade advisor | `PartRankingStrategy`, `StreetRankingStrategy` and others, `PartRecommender` |
| Decorator | Caching added around any price source | `PriceSource`, `CachingPriceSource` |

## Sample data

The parts catalog and store prices are **sample data** for demonstration. Brand names are used to make the catalog realistic, but models, prices and specs are illustrative, not real listings. The three stores are labelled "Sample" and their prices are derived from each part's MSRP. A real store API would be one more `PriceSource`.

## Project structure

```
backend/
  src/main/java/com/salvador/bmxbuilder/
    domain/         Part, BikeBuild, Assembly, Money, compatibility rules
    data/           repositories and the sample data loader
    service/        BuildService (facade) and result types
    agent/          orchestrator, memory, prompts, tools, LLM client, knowledge base
    pricing/        price sources, caching decorator, PriceService
    recommend/      ranking strategies, budget optimizer, style recommender, upgrade advisor
    web/            REST controllers and error handling
    cli/            BmxCli
  src/main/resources/static/   built GUI
frontend/           React source for the GUI
docs/               reports and UML diagrams
```
