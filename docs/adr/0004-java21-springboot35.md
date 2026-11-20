# ADR 0004: Java 21 and Spring Boot 3.5

- Status: Accepted
- Date: 2026-10-15

## Context

The build must be stable and well documented for a portfolio project. The local
JDK is newer than the LTS line the framework best supports. Spring Cloud
release trains are version-locked to Spring Boot lines.

## Decision

Target Java 21 (LTS) via `maven.compiler.release=21`, with Spring Boot 3.5.x
and Spring Cloud 2025.0.x. Build and run services on a JDK 21 toolchain
(local toolchain or Docker images). The committed Maven wrapper is used so no
global Maven install is required.

## Consequences

- A long-term-supported, widely documented stack with a four-line Spring Cloud
  pairing (2025.0.x <-> Boot 3.5.x).
- Local builds compiling on a newer JDK still emit Java 21 bytecode.
- Services are run on Java 21 (containers) to avoid unsupported-runtime
  surprises; running on a newer JDK than the framework supports is avoided.
- Upgrading to a newer Boot major is deferred until after the core workflow is
  complete.
