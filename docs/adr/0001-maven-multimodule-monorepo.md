# ADR 0001: Maven multi-module monorepo

- Status: Accepted
- Date: 2026-10-15

## Context

The platform has several independently deployable services plus shared code.
Options considered: a polyrepo with one repository per service, and a single
monorepo built with one aggregator.

## Decision

Use a single Git repository with a Maven multi-module build. The aggregator POM
`com.dpp:dpp-parent` inherits from `spring-boot-starter-parent` and manages
Spring Cloud, springdoc and Testcontainers versions. Services are Maven modules
that produce executable jars. A Maven wrapper is committed so no global Maven
install is required.

## Consequences

- One clone, one build command, consistent dependency versions.
- Shared contracts change atomically with their consumers in a single commit.
- Independent deployability is preserved at the artifact level (each service is
  its own jar/image) even though sources live together.
- A polyrepo would give stronger physical isolation but far heavier local setup
  and coordination for a portfolio project.
