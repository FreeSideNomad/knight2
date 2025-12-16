# Knight Platform

A Domain-Driven Design (DDD) Modular Monolith for Commercial Banking built with Spring Boot.

## Overview

Knight Platform is an enterprise-grade commercial banking platform that manages:
- **Clients** - Business and individual client management
- **Profiles** - Servicing and online profiles with client enrollments
- **Accounts** - Multi-currency account management across various banking systems
- **Services** - Service enrollment management (Receivables, Payments, etc.)
- **Users** - Employee user management with role-based access

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Employee Portal                          │
│                    (Vaadin Web Application)                     │
│                         Port: 8081                              │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Platform API                               │
│                   (Spring Boot REST API)                        │
│                         Port: 8080                              │
├─────────────────────────────────────────────────────────────────┤
│  Domain Modules:                                                │
│  ┌─────────┐ ┌──────────┐ ┌───────┐ ┌────────┐ ┌───────────┐   │
│  │ Clients │ │ Profiles │ │ Users │ │ Policy │ │ Approvals │   │
│  └─────────┘ └──────────┘ └───────┘ └────────┘ └───────────┘   │
├─────────────────────────────────────────────────────────────────┤
│                    Shared Kernel                                │
│         (Value Objects, Domain Primitives)                      │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      SQL Server                                 │
│                       Port: 1433                                │
└─────────────────────────────────────────────────────────────────┘
```

## Tech Stack

- **Java 17**
- **Spring Boot 3.5.8**
- **Vaadin 24** - Employee Portal UI
- **SQL Server 2022** - Primary database
- **Flyway** - Database migrations
- **MapStruct** - Object mapping
- **Docker & Docker Compose** - Containerization
- **Redis** - Session management
- **Kafka** - Event streaming (optional)

## Project Structure

```
knight2/
├── kernel/                 # Shared kernel - value objects, domain primitives
├── domain/                 # Domain modules
│   ├── clients/           # Client aggregate
│   ├── profiles/          # Profile aggregate (servicing/online)
│   ├── users/             # User aggregate
│   ├── policy/            # Policy domain
│   ├── approvals/         # Approval workflows
│   └── auth0-identity/    # Identity integration
├── application/           # REST API, persistence, infrastructure
├── employee-portal/       # Vaadin web application
├── employee-gateway/      # Nginx authentication gateway
├── docker/               # Docker configuration files
├── scripts/              # Utility scripts
└── docs/                 # Documentation and plans
```

## Prerequisites

- **Java 17** or later
- **Maven 3.8+** (or use included `./mvnw`)
- **Docker** and **Docker Compose**

## Quick Start

### Option 1: Docker Compose (Recommended)

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd knight2
   ```

2. **Create environment file**
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   ```

3. **Build and start all services**
   ```bash
   ./build.sh
   ```

   Or manually:
   ```bash
   ./mvnw clean package -DskipTests
   docker-compose up -d
   ```

4. **Access the application**
   - Employee Portal: http://localhost:8081
   - Platform API: http://localhost:8080
   - With Gateway (OAuth): http://localhost

### Option 2: Local Development

1. **Start SQL Server** (via Docker)
   ```bash
   docker-compose up -d sqlserver sql-init
   ```

2. **Build the project**
   ```bash
   ./mvnw clean install
   ```

3. **Run the Platform API**
   ```bash
   cd application
   ../mvnw spring-boot:run
   ```

4. **Run the Employee Portal** (in another terminal)
   ```bash
   cd employee-portal
   ../mvnw spring-boot:run
   ```

5. **Access the application**
   - Employee Portal: http://localhost:8081
   - Platform API: http://localhost:8080

## Generate Test Data

Generate sample clients and accounts for testing:

```bash
# Generate 10,000 clients with accounts
./scripts/generate-test-data.sh 10000

# Custom: 100 clients, 5 accounts per client
./scripts/generate-test-data.sh 100 5
```

## API Endpoints

### Clients
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/clients` | List all clients |
| GET | `/api/clients/{id}` | Get client by ID |
| POST | `/api/clients` | Create client |
| GET | `/api/clients/{id}/accounts` | Get client accounts |

### Profiles
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/profiles/search` | Search profiles |
| GET | `/api/profiles/{id}/detail` | Get profile details |
| POST | `/api/profiles` | Create profile |
| POST | `/api/profiles/{id}/clients` | Add secondary client |
| DELETE | `/api/profiles/{id}/clients/{clientId}` | Remove secondary client |

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_PASSWORD` | SQL Server SA password | `YourStrong@Passw0rd` |
| `ENTRA_TENANT_ID` | Azure AD tenant ID | - |
| `ENTRA_CLIENT_ID` | Azure AD client ID | - |
| `ENTRA_CLIENT_SECRET` | Azure AD client secret | - |

### Application Properties

Platform API (`application/src/main/resources/application.yml`):
- Database connection
- JPA/Hibernate settings
- Logging configuration

Employee Portal (`employee-portal/src/main/resources/application.yml`):
- API base URL
- JWT validation settings
- Vaadin configuration

## Development

### Running Tests
```bash
# Run all tests
./mvnw test

# Run tests for specific module
./mvnw test -pl application

# Run with coverage
./mvnw verify
```

### Code Structure Conventions

- **Domain modules** contain only business logic (no Spring dependencies)
- **Application module** contains REST controllers, JPA entities, and mappers
- **Aggregates** are the consistency boundaries
- **Value objects** are immutable and defined in kernel

### Building Docker Images
```bash
# Build all images
docker-compose build

# Build specific service
docker-compose build platform portal
```

## Documentation

- `docs/create-profile-plan.md` - Profile creation design
- `docs/receivable-service-plan.md` - Receivable service implementation plan
- `docs/profile-enhance-plan.md` - Profile enhancement specifications

## License

Proprietary - All rights reserved.
