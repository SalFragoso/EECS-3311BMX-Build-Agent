# BMX Build Agent

An AI agent web application that helps riders plan, validate, price and visualize a custom BMX build. Riders describe what they want in plain language and the agent plans a complete, compatible build by searching the parts catalog, checking compatibility and comparing prices, then explains its choices.

EECS3311 Software Design course project, Fall 2026.

## Stage 1: Design

- **Report:** [docs/Stage1-Report.md](docs/Stage1-Report.md)
- **UML diagrams (UMLet sources and images):** [docs/diagrams/](docs/diagrams/)

| Diagram | File |
|---|---|
| Class diagram | `ClassDiagram.uxf` |
| Use-case diagram | `UseCaseDiagram.uxf` |
| Sequence diagrams SD01 to SD08 | `SD01_StyleRecommendation.uxf` ... `SD08_UpgradePath.uxf` |

## Planned Stack

- Java 21 and Spring Boot for the back end (REST API, CLI through `CommandLineRunner`)
- React for the web GUI
- Groq-hosted Llama model for the agent
- JUnit for testing

## Project Stages

1. Design (this stage)
2. Implementation
3. Testing
