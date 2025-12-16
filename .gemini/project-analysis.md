# Project Analysis: Knight2

**Date:** 2025-12-15

## Overview
Modular monolith using Java 17 and Spring Boot, following DDD principles.

## Structure
- **application**: Main Spring Boot application assembling the backend. Exposes REST APIs.
- **domain**: Contains business modules (Bounded Contexts): `profiles`, `users`, `clients`, etc.
- **kernel**: Shared cross-cutting concerns.
- **employee-portal**: Frontend application (Vaadin-based).
- **employee-gateway**: API Gateway using NGINX and Lua.

## Key Observations
- `application/KnightApplication.java` scans base packages to include domain modules.
- `domain` modules appear to be independent libraries aggregated by the root domain pom.
- Uses Maven for build management.
