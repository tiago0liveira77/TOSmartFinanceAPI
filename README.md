# TOSmartFinanceAPI

# SmartFinance — Backend

> API de microserviços para gestão de finanças pessoais com inteligência artificial.  
> Construída com **Java 21 + Spring Boot 3.2 + Spring Cloud Gateway**.

---

## Índice

- [Visão Geral](#visão-geral)
- [Arquitetura](#arquitetura)
- [Microserviços](#microserviços)
- [Stack Tecnológica](#stack-tecnológica)
- [Pré-requisitos](#pré-requisitos)
- [Início Rápido](#início-rápido)
- [Variáveis de Ambiente](#variáveis-de-ambiente)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [API Reference](#api-reference)
- [Base de Dados](#base-de-dados)
- [Mensageria](#mensageria)
- [Testes](#testes)
- [CI/CD](#cicd)
- [Deploy (Azure)](#deploy-azure)

---

## Visão Geral

O backend do SmartFinance expõe uma API REST que alimenta a aplicação web de finanças pessoais. Gere autenticação, contas bancárias, transações, orçamentos, relatórios e integração com IA para categorização automática e geração de insights financeiros em linguagem natural.

```
Frontend React  ──►  API Gateway (:8080)
                          │
         ┌────────────────┼────────────────┐
         ▼                ▼                ▼
   auth-service    finance-service    ai-service
      (:8081)          (:8082)          (:8083)
         │                │                │
    PostgreSQL       PostgreSQL       OpenAI API
       Redis            Redis
                         │
                     RabbitMQ ──► notification-service
                                       (:8084)
```

---

## Arquitetura

O sistema segue uma **arquitetura de microserviços** com as seguintes características:

- **API Gateway** como ponto único de entrada com rate limiting e validação de JWT
- **Comunicação síncrona** via REST entre gateway e serviços
- **Comunicação assíncrona** via RabbitMQ para eventos (importação CSV, alertas)
- **Base de dados por serviço** — cada serviço tem o seu próprio schema PostgreSQL
- **Cache distribuído** com Redis para tokens, sessões e respostas de IA
- **Soft delete** em todos os dados financeiros (nunca apaga registos reais)

---

## Microserviços

| Serviço | Porta | Responsabilidade |
|---|---|---|
| `api-gateway` | 8080 | Routing, rate limiting, CORS, validação JWT |
| `auth-service` | 8081 | Registo, login, JWT, OAuth2 Google, perfil |
| `finance-service` | 8082 | Contas, transações, categorias, orçamentos, relatórios |
| `ai-service` | 8083 | Categorização automática, insights, previsões, chat |
| `notification-service` | 8084 | Emails, notificações in-app, alertas de orçamento |
| `shared-lib` | — | DTOs, exceções e JWT validator partilhados |

---

## Stack Tecnológica

| Categoria | Tecnologia |
|---|---|
| Linguagem | Java 21 (LTS) |
| Framework | Spring Boot 3.2.5 |
| Segurança | Spring Security 6 + JJWT 0.12 |
| Gateway | Spring Cloud Gateway 2023.0 |
| Persistência | Spring Data JPA + Hibernate + PostgreSQL 16 |
| Cache | Redis 7 |
| Mensageria | RabbitMQ 3.13 |
| Migrations | Flyway |
| Mapeamento | MapStruct |
| Documentação | OpenAPI 3 / Swagger UI |
| Testes | JUnit 5 + Mockito + Testcontainers |
| Build | Maven 3.9 (multi-módulo) |
| Containers | Docker + Docker Compose |
| CI/CD | GitHub Actions |
| Cloud | Azure App Service + Azure Database for PostgreSQL |

---

## Pré-requisitos

- **Java 21** — [Download Temurin](https://adoptium.net/)
- **Maven 3.9+** — `mvn -v`
- **Docker Desktop** — [Download](https://www.docker.com/products/docker-desktop/)
- **Docker Compose v2** — incluído no Docker Desktop

Verificar instalações:
```bash
java -version   # openjdk 21...
mvn -version    # Apache Maven 3.9...
docker -v       # Docker version 25...
```

---

## Início Rápido

### 1. Clonar o repositório
```bash
git clone https://github.com/teu-user/smartfinance.git
cd smartfinance/backend
```

### 2. Configurar variáveis de ambiente
```bash
cp .env.example .env
# Editar .env com os teus valores (ver secção abaixo)
```

### 3. Subir infraestrutura
```bash
docker compose up -d postgres redis rabbitmq
```

Aguardar os serviços ficarem saudáveis:
```bash
docker compose ps
# postgres: healthy
# redis:    healthy
# rabbitmq: healthy
```

### 4. Compilar o projeto
```bash
./mvnw clean install -DskipTests
```

### 5. Subir todos os serviços
```bash
docker compose up -d
```

### 6. Verificar que está tudo a funcionar
```bash
# Health checks
curl http://localhost:8080/actuator/health   # api-gateway
curl http://localhost:8081/actuator/health   # auth-service
curl http://localhost:8082/actuator/health   # finance-service

# Swagger UI (apenas dev)
open http://localhost:8081/swagger-ui.html   # auth-service API docs
open http://localhost:8082/swagger-ui.html   # finance-service API docs
```

### 7. Criar utilizador de teste
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Password123!","name":"Test User"}'
```

---

## Variáveis de Ambiente

Criar um ficheiro `.env` na raiz do `/backend`:

```env
# JWT
JWT_SECRET=your-256-bit-secret-key-change-this-in-production
JWT_ACCESS_EXPIRATION_MS=900000       # 15 minutos
JWT_REFRESH_EXPIRATION_DAYS=7

# Google OAuth2
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret

# OpenAI
OPENAI_API_KEY=sk-...

# Email (SMTP)
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=your-email@gmail.com
SMTP_PASS=your-app-password

# Frontend URL (CORS)
FRONTEND_URL=http://localhost:5173

# Redis
REDIS_PASSWORD=                       # vazio em dev local

# RabbitMQ
RABBITMQ_DEFAULT_USER=smartfinance
RABBITMQ_DEFAULT_PASS=smartfinance
```

> **Nunca** commitar o ficheiro `.env`. Está incluído no `.gitignore`.

---

## Estrutura do Projeto

```
backend/
├── pom.xml                          # Parent POM — gestão de dependências global
├── docker-compose.yml               # Orquestração local completa
├── docker-compose.test.yml          # Ambiente isolado para testes de integração
├── .env.example                     # Template de variáveis de ambiente
│
├── shared-lib/                      # Biblioteca partilhada entre serviços
│   └── src/main/java/com/smartfinance/shared/
│       ├── dto/                     # DTOs de comunicação inter-serviços
│       ├── exception/               # GlobalExceptionHandler + exceções base
│       ├── security/                # JwtValidator reutilizável
│       └── event/                   # POJOs de eventos RabbitMQ
│
├── api-gateway/                     # Spring Cloud Gateway
│   └── src/main/resources/
│       └── application.yml          # Routing, rate limiting, CORS
│
├── auth-service/                    # Autenticação e identidade
│   └── src/main/java/com/smartfinance/auth/
│       ├── config/                  # SecurityConfig, JwtConfig, RedisConfig
│       ├── controller/              # AuthController, UserController
│       ├── service/                 # AuthService, JwtService, RefreshTokenService
│       ├── repository/              # UserRepository
│       ├── entity/                  # User
│       ├── dto/                     # request/ e response/ records
│       ├── exception/               # Exceções específicas + GlobalExceptionHandler
│       └── security/                # JwtAuthenticationFilter, OAuth2SuccessHandler
│
├── finance-service/                 # Núcleo financeiro
│   └── src/main/java/com/smartfinance/finance/
│       ├── controller/              # Account, Transaction, Category, Budget, Report
│       ├── service/                 # Lógica de negócio por domínio
│       ├── repository/              # Spring Data JPA repositories
│       ├── entity/                  # Account, Transaction, Category, Budget
│       └── dto/                     # DTOs de request e response
│
├── ai-service/                      # Integração OpenAI
│   └── src/main/java/com/smartfinance/ai/
│       ├── consumer/                # RabbitMQ consumers
│       ├── service/                 # CategorizationService, InsightService, ChatService
│       └── config/                  # OpenAI WebClient, Redis, RabbitMQ config
│
└── notification-service/            # Notificações e alertas
    └── src/main/java/com/smartfinance/notification/
        ├── consumer/                # Consumers de eventos RabbitMQ
        ├── service/                 # EmailService, NotificationService
        └── templates/               # Templates Thymeleaf para emails
```

---

## API Reference

A documentação interativa está disponível via Swagger UI em cada serviço (apenas em dev):

| Serviço | Swagger UI |
|---|---|
| auth-service | http://localhost:8081/swagger-ui.html |
| finance-service | http://localhost:8082/swagger-ui.html |
| ai-service | http://localhost:8083/swagger-ui.html |
| notification-service | http://localhost:8084/swagger-ui.html |

### Autenticação

Todos os endpoints protegidos requerem o header:
```
Authorization: Bearer <access_token>
```

O access token tem validade de **15 minutos**. O refresh token é enviado automaticamente via `HttpOnly cookie` e renovado em `/api/v1/auth/refresh`.

### Endpoints principais

#### Auth (`/api/v1/auth`)
```
POST /register          Registo com email e password
POST /login             Login — devolve access token + seta cookie refresh
POST /refresh           Renova access token via cookie
POST /logout            Invalida refresh token
GET  /oauth2/google     Inicia fluxo OAuth2 Google
```

#### Contas (`/api/v1/accounts`)
```
GET    /                Lista contas do utilizador autenticado
POST   /                Cria conta (CHECKING, SAVINGS, CREDIT_CARD, INVESTMENT, CASH)
GET    /{id}            Detalhe de conta
PUT    /{id}            Atualiza conta
DELETE /{id}            Soft delete
GET    /{id}/summary    Resumo do mês: saldo, receitas, despesas
```

#### Transações (`/api/v1/transactions`)
```
GET    /                Lista paginada — filtros: conta, categoria, tipo, datas, valor, texto
POST   /                Cria transação
GET    /{id}            Detalhe
PUT    /{id}            Atualiza
DELETE /{id}            Soft delete
POST   /import          Importa CSV (multipart/form-data)
GET    /import/{id}     Status de importação
```

#### Relatórios (`/api/v1/reports`)
```
GET /summary            Resumo mensal: receitas, despesas, saldo, taxa de poupança
GET /by-category        Despesas/receitas por categoria com percentagens
GET /monthly-trend      Tendência dos últimos N meses
GET /budget-status      Estado atual de todos os orçamentos
```

#### IA (`/api/v1/ai`)
```
POST /categorize        Categoriza uma transação manualmente
GET  /insights          Insights do mês em linguagem natural (?year=&month=)
GET  /forecast          Previsão de gastos (?months=3)
POST /chat              Mensagem para o chat financeiro
DELETE /chat/{id}       Limpa histórico de conversa
```

---

## Base de Dados

Cada serviço tem a sua própria base de dados PostgreSQL, criadas automaticamente pelo `init-db.sql` no arranque do Docker Compose:

| Base de dados | Serviço |
|---|---|
| `smartfinance_auth` | auth-service |
| `smartfinance_finance` | finance-service |
| `smartfinance_notifications` | notification-service |

### Migrations

As migrations são geridas pelo **Flyway** e executadas automaticamente no arranque de cada serviço. A nomenclatura segue o padrão `V{versão}__{descrição}.sql`.

```bash
# Ver estado das migrations (exemplo para finance-service)
./mvnw flyway:info -pl finance-service

# Reparar migration com checksum errado (só dev)
./mvnw flyway:repair -pl finance-service
```

### Acesso direto à DB (dev)
```bash
# Conectar ao PostgreSQL local
docker exec -it smartfinance-postgres-1 psql -U postgres -d smartfinance_finance

# Ou via psql local
psql -h localhost -p 5432 -U postgres -d smartfinance_finance
```

### RabbitMQ Management UI
```
URL:  http://localhost:15672
User: smartfinance
Pass: smartfinance (ou o valor em .env)
```

---

## Mensageria

O sistema usa RabbitMQ com uma **topic exchange** (`smartfinance.events`).

| Routing Key | Publicado por | Consumido por | Descrição |
|---|---|---|---|
| `transactions.imported` | finance-service | ai-service, notification-service | CSV importado com sucesso |
| `transaction.created` | finance-service | notification-service | Nova transação criada |
| `budget.threshold.reached` | finance-service | notification-service | Orçamento atingiu threshold |
| `ai.categorization.completed` | ai-service | finance-service | Categorização AI concluída |

---

## Testes

### Correr todos os testes
```bash
./mvnw test
```

### Correr testes de um serviço específico
```bash
./mvnw test -pl auth-service
./mvnw test -pl finance-service
```

### Correr testes de integração (requer Docker)
```bash
./mvnw verify -pl finance-service -Pintegration-tests
```

### Ver relatório de cobertura
```bash
./mvnw test jacoco:report -pl auth-service
open auth-service/target/site/jacoco/index.html
```

### Estrutura de testes
```
src/test/java/
├── unit/                   # JUnit 5 + Mockito — sem infraestrutura
│   ├── AuthServiceTest.java
│   ├── JwtServiceTest.java
│   └── TransactionServiceTest.java
└── integration/            # Testcontainers — PostgreSQL + Redis reais
    ├── AuthControllerIT.java
    └── TransactionControllerIT.java
```

> **Target de cobertura:** mínimo 80% em todos os `*Service.java`.

---

## CI/CD

O pipeline de CI/CD usa **GitHub Actions** e está definido em `.github/workflows/backend-ci.yml`.

### Pipeline por branch

| Branch | Ação |
|---|---|
| `feature/*` | Testes unitários |
| `develop` | Testes unitários + integração + build Docker |
| `main` | Tudo acima + push para Azure Container Registry + deploy staging |

### Secrets necessários no GitHub

```
JWT_SECRET
OPENAI_API_KEY
GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
AZURE_CREDENTIALS
ACR_LOGIN_SERVER
ACR_USERNAME
ACR_PASSWORD
```

---

## Deploy (Azure)

### Infraestrutura necessária
- **Azure Container Registry** — imagens Docker
- **Azure App Service** (x5) — um por microserviço
- **Azure Database for PostgreSQL Flexible Server**
- **Azure Cache for Redis**
- **Azure Service Bus** (alternativa ao RabbitMQ em produção)

### Deploy manual (primeira vez)
```bash
# Login Azure
az login
az acr login --name smartfinanceacr

# Build e push das imagens
docker compose build
docker tag smartfinance/auth-service smartfinanceacr.azurecr.io/auth-service:latest
docker push smartfinanceacr.azurecr.io/auth-service:latest

# Deploy para App Service
az webapp config container set \
  --name smartfinance-auth \
  --resource-group smartfinance-rg \
  --docker-custom-image-name smartfinanceacr.azurecr.io/auth-service:latest
```

A partir do primeiro deploy, o GitHub Actions trata de tudo automaticamente em cada push para `main`.

---

## Contribuição

1. Cria um branch a partir de `develop`: `git checkout -b feature/nome-da-feature`
2. Implementa seguindo as regras em `CLAUDE.md`
3. Garante cobertura de testes ≥ 80%
4. Cria Pull Request para `develop`
5. CI tem de passar antes do merge

---

## Licença

MIT © SmartFinance
