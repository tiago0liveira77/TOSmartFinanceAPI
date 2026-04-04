# SmartFinance — Backend (Spring Boot 3 + Java 21)

## CONTEXTO PARA O CLAUDE

Estás a desenvolver o backend do SmartFinance. É uma arquitetura de microserviços com 5 serviços Spring Boot. Cada serviço é um módulo Maven independente. Lê este ficheiro na íntegra antes de qualquer implementação.

---

## ESTRUTURA DE MÓDULOS MAVEN

```
backend/
├── pom.xml                          ← Parent POM (gestão de dependências)
├── shared-lib/                      ← Biblioteca partilhada entre serviços
│   ├── pom.xml
│   └── src/main/java/com/smartfinance/shared/
│       ├── dto/                     ← DTOs de comunicação inter-serviços
│       ├── exception/               ← Exceções globais partilhadas
│       ├── security/                ← JwtValidator partilhado
│       └── event/                   ← Eventos RabbitMQ (POJOs)
├── api-gateway/
├── auth-service/
├── finance-service/
├── ai-service/
└── notification-service/
```

---

## PARENT POM — DEPENDÊNCIAS GLOBAIS

```xml
<!-- backend/pom.xml -->
<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.2.5</spring-boot.version>
    <spring-cloud.version>2023.0.1</spring-cloud.version>
    <jjwt.version>0.12.5</jjwt.version>
    <mapstruct.version>1.5.5.Final</mapstruct.version>
    <testcontainers.version>1.19.7</testcontainers.version>
</properties>

<!-- Dependências que TODOS os serviços herdam -->
- spring-boot-starter-validation
- spring-boot-starter-actuator
- lombok
- mapstruct
- jjwt-api + jjwt-impl + jjwt-jackson
```

---

## SERVIÇO 1: auth-service (porta 8081)

### Responsabilidade
Gerir identidade: registo, login local, login OAuth2 Google, emissão de JWT, refresh de tokens, gestão de perfil.

### Dependências específicas
```xml
- spring-boot-starter-web
- spring-boot-starter-security
- spring-boot-starter-data-jpa
- spring-boot-starter-oauth2-client
- spring-boot-starter-data-redis
- postgresql driver
- flyway-core
```

### Estrutura de pacotes
```
auth-service/src/main/java/com/smartfinance/auth/
├── AuthServiceApplication.java
├── config/
│   ├── SecurityConfig.java          ← Configuração Spring Security
│   ├── JwtConfig.java               ← Propriedades JWT (@ConfigurationProperties)
│   └── RedisConfig.java             ← Configuração Redis para refresh tokens
├── controller/
│   ├── AuthController.java          ← /api/v1/auth/**
│   └── UserController.java          ← /api/v1/users/me
├── service/
│   ├── AuthService.java
│   ├── JwtService.java              ← Geração e validação de tokens
│   ├── RefreshTokenService.java     ← Gestão de refresh tokens no Redis
│   └── UserService.java
├── repository/
│   └── UserRepository.java
├── entity/
│   └── User.java
├── dto/
│   ├── request/
│   │   ├── RegisterRequest.java     ← record com @Valid
│   │   └── LoginRequest.java        ← record com @Valid
│   └── response/
│       ├── AuthResponse.java        ← record: accessToken, expiresIn, user
│       └── UserResponse.java        ← record: id, email, name, avatarUrl
├── exception/
│   ├── GlobalExceptionHandler.java  ← @RestControllerAdvice
│   ├── UserAlreadyExistsException.java
│   └── InvalidCredentialsException.java
└── security/
    ├── JwtAuthenticationFilter.java ← OncePerRequestFilter
    └── OAuth2SuccessHandler.java    ← Redirect após OAuth2 Google
```

### Entidade User
```java
@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;          // null se OAuth2

    @Column(nullable = false)
    private String name;

    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    private AuthProvider provider;    // LOCAL, GOOGLE

    @Column(nullable = false)
    private boolean emailVerified = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;  // soft delete
}
```

### Flyway Migration V1 — users table
```sql
-- V1__create_users_table.sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    avatar_url TEXT,
    provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_provider ON users(provider);
```

### Endpoints Auth
```
POST /api/v1/auth/register
  Body: { email, password, name }
  Response: AuthResponse (201)

POST /api/v1/auth/login
  Body: { email, password }
  Response: AuthResponse (200)
  Cookie: sets HttpOnly refresh_token cookie

POST /api/v1/auth/refresh
  Cookie: refresh_token
  Response: AuthResponse (200)

POST /api/v1/auth/logout
  Header: Authorization Bearer
  Response: 204 (invalida refresh token no Redis)

GET /api/v1/auth/oauth2/google
  → Redirect para Google OAuth2

GET /api/v1/users/me
  Header: Authorization Bearer
  Response: UserResponse (200)

PUT /api/v1/users/me
  Header: Authorization Bearer
  Body: { name, avatarUrl }
  Response: UserResponse (200)
```

### JWT — Implementação detalhada
```java
// JwtService.java
// Access token: 15 minutos, contém: sub (userId), email, roles
// Refresh token: UUID aleatório armazenado no Redis com TTL 7 dias
// Chave: "refresh:{userId}:{tokenId}" → valor: userId
// Ao fazer refresh: valida token no Redis, invalida o atual, emite novo par

// JwtAuthenticationFilter.java
// 1. Extrai Bearer token do header Authorization
// 2. Valida assinatura e expiração com JJWT
// 3. Extrai userId do subject
// 4. Cria UsernamePasswordAuthenticationToken
// 5. Coloca no SecurityContextHolder
```

### SecurityConfig — pontos críticos
```java
// Paths públicos (permitAll):
// POST /api/v1/auth/register
// POST /api/v1/auth/login
// POST /api/v1/auth/refresh
// GET  /api/v1/auth/oauth2/**
// GET  /actuator/health

// CSRF: desativado (stateless JWT)
// Session: STATELESS
// CORS: apenas origins configurados em application.yml
```

### Redis — Refresh Tokens
```java
// Estrutura no Redis:
// Key:   "rt:{userId}:{jti}"   (jti = JWT ID único, UUID)
// Value: userId como String
// TTL:   7 dias

// Rotação de refresh tokens:
// A cada /refresh bem-sucedido, o token antigo é apagado
// e um novo par (access + refresh) é emitido
// Isto permite detetar roubo de tokens (token reuse detection)
```

---

## SERVIÇO 2: finance-service (porta 8082)

### Responsabilidade
Gestão de contas bancárias, transações, categorias, orçamentos, importação CSV e geração de relatórios.

### Dependências específicas
```xml
- spring-boot-starter-web
- spring-boot-starter-data-jpa
- spring-boot-starter-amqp       ← RabbitMQ (publicar eventos)
- spring-boot-starter-data-redis ← Cache de relatórios
- opencsv                        ← Parsing de CSV
- postgresql driver
- flyway-core
- shared-lib                     ← JWT validator partilhado
```

### Estrutura de pacotes
```
finance-service/src/main/java/com/smartfinance/finance/
├── FinanceServiceApplication.java
├── config/
│   ├── SecurityConfig.java          ← Valida JWT via shared-lib
│   ├── RabbitMQConfig.java          ← Exchanges, queues, bindings
│   └── RedisConfig.java
├── controller/
│   ├── AccountController.java       ← /api/v1/accounts
│   ├── TransactionController.java   ← /api/v1/transactions
│   ├── CategoryController.java      ← /api/v1/categories
│   ├── BudgetController.java        ← /api/v1/budgets
│   └── ReportController.java        ← /api/v1/reports
├── service/
│   ├── AccountService.java
│   ├── TransactionService.java
│   ├── CategoryService.java
│   ├── BudgetService.java
│   ├── ReportService.java
│   ├── CsvImportService.java
│   └── EventPublisherService.java   ← Publica eventos para RabbitMQ
├── repository/
│   ├── AccountRepository.java
│   ├── TransactionRepository.java
│   ├── CategoryRepository.java
│   └── BudgetRepository.java
├── entity/
│   ├── Account.java
│   ├── Transaction.java
│   ├── Category.java
│   └── Budget.java
└── dto/ ...
```

### Entidades e Migrations

#### Account
```sql
-- V1__create_accounts_table.sql
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,             -- referência ao auth-service
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,         -- CHECKING, SAVINGS, CREDIT_CARD, INVESTMENT, CASH
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    color VARCHAR(7),                  -- hex color para UI
    icon VARCHAR(50),                  -- nome do ícone
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_accounts_type ON accounts(type);
```

#### Category
```sql
-- V2__create_categories_table.sql
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,                      -- NULL = categoria do sistema
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,         -- INCOME, EXPENSE
    icon VARCHAR(50),
    color VARCHAR(7),
    parent_id UUID REFERENCES categories(id),  -- subcategorias
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Categorias do sistema inseridas em migration separada
-- V3__insert_system_categories.sql
INSERT INTO categories (name, type, icon, is_system) VALUES
  ('Alimentação', 'EXPENSE', 'utensils', true),
  ('Transporte', 'EXPENSE', 'car', true),
  ('Habitação', 'EXPENSE', 'home', true),
  ('Saúde', 'EXPENSE', 'heart', true),
  ('Entretenimento', 'EXPENSE', 'tv', true),
  ('Educação', 'EXPENSE', 'book', true),
  ('Vestuário', 'EXPENSE', 'shirt', true),
  ('Tecnologia', 'EXPENSE', 'laptop', true),
  ('Viagens', 'EXPENSE', 'plane', true),
  ('Outros', 'EXPENSE', 'more-horizontal', true),
  ('Salário', 'INCOME', 'briefcase', true),
  ('Freelance', 'INCOME', 'code', true),
  ('Investimentos', 'INCOME', 'trending-up', true),
  ('Outros Rendimentos', 'INCOME', 'plus-circle', true);
```

#### Transaction
```sql
-- V4__create_transactions_table.sql
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(id),
    category_id UUID REFERENCES categories(id),
    amount DECIMAL(15,2) NOT NULL,     -- sempre positivo
    type VARCHAR(50) NOT NULL,         -- INCOME, EXPENSE, TRANSFER
    description VARCHAR(500),
    notes TEXT,
    date DATE NOT NULL,
    is_recurring BOOLEAN NOT NULL DEFAULT FALSE,
    recurrence_rule VARCHAR(50),       -- DAILY, WEEKLY, MONTHLY, YEARLY
    ai_categorized BOOLEAN NOT NULL DEFAULT FALSE,
    ai_confidence DECIMAL(3,2),        -- 0.00 a 1.00
    import_id UUID,                    -- referência ao CSV import
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP               -- soft delete obrigatório
);

CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_category_id ON transactions(category_id);
CREATE INDEX idx_transactions_date ON transactions(date DESC);
CREATE INDEX idx_transactions_type ON transactions(type);
```

#### Budget
```sql
-- V5__create_budgets_table.sql
CREATE TABLE budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    category_id UUID NOT NULL REFERENCES categories(id),
    amount DECIMAL(15,2) NOT NULL,
    period VARCHAR(20) NOT NULL,       -- MONTHLY, YEARLY
    start_date DATE NOT NULL,
    end_date DATE,
    alert_threshold INTEGER DEFAULT 80, -- % para alertar
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### Endpoints Finance

#### Accounts
```
GET    /api/v1/accounts              → Lista contas do utilizador autenticado
POST   /api/v1/accounts              → Cria conta
GET    /api/v1/accounts/{id}         → Detalhe de conta
PUT    /api/v1/accounts/{id}         → Atualiza conta
DELETE /api/v1/accounts/{id}         → Soft delete
GET    /api/v1/accounts/{id}/summary → Resumo: saldo, receitas, despesas do mês
```

#### Transactions
```
GET  /api/v1/transactions            → Lista paginada com filtros
  Query params:
    - page, size, sort (default: date,desc)
    - accountId (UUID)
    - categoryId (UUID)
    - type (INCOME|EXPENSE|TRANSFER)
    - startDate, endDate (ISO date)
    - minAmount, maxAmount
    - search (full-text em description)

POST /api/v1/transactions            → Cria transação
GET  /api/v1/transactions/{id}       → Detalhe
PUT  /api/v1/transactions/{id}       → Atualiza
DELETE /api/v1/transactions/{id}     → Soft delete

POST /api/v1/transactions/import     → Upload CSV (multipart/form-data)
  Response: { importId, total, imported, failed, errors[] }

GET  /api/v1/transactions/import/{importId} → Status do import
```

#### Categories
```
GET  /api/v1/categories              → Sistema + custom do utilizador
POST /api/v1/categories              → Cria categoria custom
PUT  /api/v1/categories/{id}         → Atualiza (apenas custom próprias)
DELETE /api/v1/categories/{id}       → Remove (apenas custom próprias)
```

#### Reports
```
GET /api/v1/reports/summary
  Query: year, month
  Response: { totalIncome, totalExpenses, balance, savingsRate, topCategories[] }

GET /api/v1/reports/by-category
  Query: year, month, type (INCOME|EXPENSE)
  Response: [{ category, amount, percentage, transactionCount }]

GET /api/v1/reports/monthly-trend
  Query: year, months (default: 12)
  Response: [{ month, income, expenses, balance }]

GET /api/v1/reports/budget-status
  Response: [{ budget, spent, percentage, remaining, status (OK|WARNING|EXCEEDED) }]
```

### CSV Import — Formato suportado
```
// Formato esperado (compatível com exportação de MB, BPI, CGD):
date,description,amount,type
2024-01-15,"Supermercado Continente",-45.30,EXPENSE
2024-01-16,"Salário Janeiro",1800.00,INCOME

// CsvImportService:
// 1. Valida headers obrigatórios
// 2. Parse linha a linha com OpenCSV
// 3. Para cada transação: cria sem categoria
// 4. Publica evento TRANSACTIONS_IMPORTED para RabbitMQ
// 5. ai-service consome e categoriza assincronamente
// 6. Retorna import summary imediatamente (async processing)
```

### RabbitMQ — Eventos publicados
```java
// Exchanges e routing keys:
// Exchange: "smartfinance.events" (topic)

// Eventos publicados pelo finance-service:
"transactions.imported"    → payload: { userId, importId, transactionIds[] }
"transaction.created"      → payload: { userId, transactionId, amount, description }
"budget.threshold.reached" → payload: { userId, budgetId, percentage }

// Eventos consumidos pelo finance-service:
"ai.categorization.completed" → payload: { transactionId, categoryId, confidence }
  → Atualiza category_id e ai_categorized=true na transação
```

---

## SERVIÇO 3: ai-service (porta 8083)

### Responsabilidade
Integração com OpenAI API para: categorização automática de transações, geração de insights mensais em linguagem natural, previsão de gastos e chat financeiro.

### Dependências específicas
```xml
- spring-boot-starter-web
- spring-boot-starter-amqp       ← Consumir eventos de transações
- spring-boot-starter-data-redis ← Cache de respostas AI
- openai-java (SDK oficial)      ← ou WebClient direto
- shared-lib
```

### Funcionalidades detalhadas

#### 1. Categorização automática
```java
// Consumer: ouve "transactions.imported"
// Para cada transactionId:
//   1. Chama finance-service para obter descrição
//   2. Chama OpenAI com prompt estruturado
//   3. Retorna categoryId + confidence
//   4. Publica "ai.categorization.completed"

// Prompt de categorização:
"""
És um assistente de categorização financeira. 
Dado a descrição de uma transação bancária, 
devolve APENAS um JSON com:
{
  "category": "nome da categoria",
  "confidence": 0.95,
  "reasoning": "breve explicação"
}

Categorias disponíveis: [lista dinâmica das categorias do utilizador]

Transação: "{description}"
Valor: {amount}€
Tipo: {type}
"""
```

#### 2. Insights mensais
```java
// GET /api/v1/ai/insights?year=2024&month=1
// 1. Agrega dados do mês via finance-service
// 2. Chama OpenAI com contexto financeiro
// 3. Devolve texto em português com 3-5 insights
// Cache Redis: 24h (insights não mudam intraday)

// Prompt de insights:
"""
Analisa os dados financeiros deste utilizador para {mês/ano}:

Receitas: {totalIncome}€
Despesas: {totalExpenses}€
Top categorias de despesa:
{categoriasComValores}

Compara com mês anterior:
{comparacaoMesAnterior}

Gera 3-5 insights úteis e acionáveis em português de Portugal.
Formato: lista de observações concretas, sem jargão.
Foca em: onde está a gastar mais, tendências, sugestões de poupança.
"""
```

#### 3. Previsão de gastos
```java
// GET /api/v1/ai/forecast?months=3
// Usa dados dos últimos 6 meses
// Chama OpenAI para análise de tendências
// Devolve previsão por categoria para próximos N meses

// Prompt de previsão:
"""
Com base nos seguintes dados históricos de gastos mensais:
{dadosHistoricos}

Prevê os gastos para os próximos {meses} meses por categoria.
Devolve APENAS JSON:
{
  "predictions": [
    {
      "month": "2024-02",
      "categories": [
        { "name": "Alimentação", "predicted": 350.00, "confidence": "HIGH" }
      ],
      "totalPredicted": 1200.00
    }
  ]
}
"""
```

#### 4. Chat financeiro
```java
// POST /api/v1/ai/chat
// Body: { message, conversationId (optional) }
// Mantém histórico de conversa no Redis (max 10 mensagens)
// System prompt: contexto financeiro do utilizador

// System prompt base:
"""
És o assistente financeiro pessoal do utilizador.
Tens acesso aos seguintes dados financeiros dele:
{resumoFinanceiro}

Responde sempre em português de Portugal.
Sê direto, útil e concreto. Usa valores reais quando relevante.
Nunca dês conselhos de investimento específicos.
"""
```

### Endpoints AI
```
POST /api/v1/ai/categorize          → Categoriza transação single
GET  /api/v1/ai/insights            → Insights do mês (query: year, month)
GET  /api/v1/ai/forecast            → Previsão de gastos (query: months)
POST /api/v1/ai/chat                → Chat financeiro
DELETE /api/v1/ai/chat/{id}         → Limpa histórico de conversa
```

---

## SERVIÇO 4: notification-service (porta 8084)

### Responsabilidade
Processar eventos do RabbitMQ e enviar notificações (email, in-app).

### Dependências específicas
```xml
- spring-boot-starter-web
- spring-boot-starter-amqp
- spring-boot-starter-mail
- spring-boot-starter-data-jpa
- spring-boot-starter-thymeleaf    ← Templates de email
- postgresql driver
- flyway-core
```

### Eventos consumidos
```
"transactions.imported"      → Email de confirmação com resumo do import
"budget.threshold.reached"   → Email + notificação in-app de alerta de orçamento
"transaction.created"        → Verificar se algum alerta configurado foi ativado
```

### Tabela de notificações (in-app)
```sql
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,        -- BUDGET_ALERT, IMPORT_COMPLETE, AI_INSIGHT
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE alert_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,        -- BUDGET_THRESHOLD, LARGE_TRANSACTION, MONTHLY_SUMMARY
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    config JSONB,                     -- configuração específica do alerta
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### Endpoints Notifications
```
GET  /api/v1/notifications          → Lista (não lidas por default)
PUT  /api/v1/notifications/{id}/read → Marca como lida
DELETE /api/v1/notifications/{id}   → Remove
GET  /api/v1/notifications/preferences → Preferências
PUT  /api/v1/notifications/preferences → Atualiza preferências
```

---

## SERVIÇO 5: api-gateway (porta 8080)

### Responsabilidade
Ponto único de entrada. Routing para microserviços, rate limiting, validação de JWT, CORS global.

### Dependências específicas
```xml
- spring-cloud-starter-gateway
- spring-boot-starter-data-redis  ← Rate limiting
- shared-lib
```

### Routing config (application.yml)
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: http://auth-service:8081
          predicates:
            - Path=/api/v1/auth/**, /api/v1/users/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 20
                redis-rate-limiter.burstCapacity: 40

        - id: finance-service
          uri: http://finance-service:8082
          predicates:
            - Path=/api/v1/accounts/**, /api/v1/transactions/**, /api/v1/categories/**, /api/v1/reports/**, /api/v1/budgets/**
          filters:
            - JwtAuthenticationFilter  ← Custom filter que valida JWT

        - id: ai-service
          uri: http://ai-service:8083
          predicates:
            - Path=/api/v1/ai/**
          filters:
            - JwtAuthenticationFilter

        - id: notification-service
          uri: http://notification-service:8084
          predicates:
            - Path=/api/v1/notifications/**
          filters:
            - JwtAuthenticationFilter

      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "http://localhost:5173"
              - "https://smartfinance.yourdomain.com"
            allowedMethods: [GET, POST, PUT, DELETE, OPTIONS]
            allowedHeaders: ["*"]
            allowCredentials: true
```

---

## DOCKER COMPOSE LOCAL

```yaml
# docker-compose.yml
version: '3.9'

services:
  # Infraestrutura
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_PASSWORD: smartfinance
    volumes:
      - ./docker/init-db.sql:/docker-entrypoint-initdb.d/init.sql
    ports: ["5432:5432"]

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    ports:
      - "5672:5672"
      - "15672:15672"   ← Management UI

  # Microserviços
  api-gateway:
    build: ./api-gateway
    ports: ["8080:8080"]
    depends_on: [auth-service, finance-service, ai-service, notification-service]

  auth-service:
    build: ./auth-service
    ports: ["8081:8081"]
    depends_on: [postgres, redis]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/smartfinance_auth
      JWT_SECRET: ${JWT_SECRET}
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET}

  finance-service:
    build: ./finance-service
    ports: ["8082:8082"]
    depends_on: [postgres, rabbitmq, redis]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/smartfinance_finance

  ai-service:
    build: ./ai-service
    ports: ["8083:8083"]
    depends_on: [rabbitmq, redis]
    environment:
      OPENAI_API_KEY: ${OPENAI_API_KEY}

  notification-service:
    build: ./notification-service
    ports: ["8084:8084"]
    depends_on: [postgres, rabbitmq]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/smartfinance_notifications
      SMTP_HOST: ${SMTP_HOST}
      SMTP_USER: ${SMTP_USER}
      SMTP_PASS: ${SMTP_PASS}
```

### init-db.sql (criar bases de dados separadas)
```sql
CREATE DATABASE smartfinance_auth;
CREATE DATABASE smartfinance_finance;
CREATE DATABASE smartfinance_notifications;
```

---

## TESTES — ESTRATÉGIA COMPLETA

### Pirâmide de testes
```
         [E2E - Playwright]          ← poucos, críticos
        [Integração - Spring]        ← por endpoint
      [Unitários - JUnit/Mockito]    ← por serviço/usecase
```

### Testes unitários (auth-service exemplo)
```java
// AuthServiceTest.java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AuthService authService;

    @Test
    void register_WithNewEmail_ShouldCreateUser() { ... }

    @Test
    void register_WithExistingEmail_ShouldThrowException() { ... }

    @Test
    void login_WithValidCredentials_ShouldReturnTokens() { ... }

    @Test
    void login_WithWrongPassword_ShouldThrowException() { ... }
}
```

### Testes de integração com Testcontainers
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class TransactionControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    // Testa endpoints reais contra DB real
    // Usa @BeforeEach para limpar dados
    // Verifica HTTP status codes, response bodies, DB state
}
```

---

## CI/CD — GITHUB ACTIONS

```yaml
# .github/workflows/backend-ci.yml
name: Backend CI

on:
  push:
    branches: [main, develop]
    paths: ['backend/**']
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env: { POSTGRES_PASSWORD: test }
      redis:
        image: redis:7

    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - name: Run tests
        run: ./mvnw test -pl auth-service,finance-service,ai-service
      - name: Upload coverage
        uses: codecov/codecov-action@v3

  build-and-push:
    needs: test
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - name: Build Docker images
        run: docker compose build
      - name: Push to Azure Container Registry
        run: |
          docker tag smartfinance/auth-service $ACR_LOGIN_SERVER/auth-service:$GITHUB_SHA
          docker push $ACR_LOGIN_SERVER/auth-service:$GITHUB_SHA

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to Azure App Service
        uses: azure/webapps-deploy@v2
```

---

## ORDEM DE IMPLEMENTAÇÃO RECOMENDADA

### Sprint 1 (Semana 1)
1. Setup Parent POM e shared-lib
2. Docker Compose com infraestrutura (postgres, redis, rabbitmq)
3. auth-service: entidade User, migrations, registo e login com JWT

### Sprint 2 (Semana 2)
4. auth-service: refresh tokens, logout, OAuth2 Google
5. api-gateway: routing básico + JwtAuthenticationFilter
6. Testes unitários auth-service (target: 80%+)

### Sprint 3 (Semana 3)
7. finance-service: migrations, Account CRUD
8. finance-service: Category system + custom categories
9. finance-service: Transaction CRUD com paginação e filtros

### Sprint 4 (Semana 4)
10. finance-service: CSV import com async processing
11. finance-service: Reports endpoints
12. RabbitMQ: publicar eventos transactions.imported

### Sprint 5 (Semana 5)
13. ai-service: setup, consumer RabbitMQ, categorização auto
14. ai-service: insights mensais com cache Redis
15. Testes de integração finance-service

### Sprint 6 (Semana 6)
16. ai-service: previsão de gastos + chat
17. notification-service: emails + in-app notifications
18. Budget alertas end-to-end

### Sprint 7-8 (Semanas 7-8)
19. CI/CD GitHub Actions completo
20. Deploy Azure (staging environment)
21. Performance tuning: query optimization, caching strategy
22. Security hardening: rate limiting, input sanitization
