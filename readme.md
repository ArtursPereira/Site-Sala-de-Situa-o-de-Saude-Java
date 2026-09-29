Sala de Situação de Saúde (NSS) - Backend
Backend da plataforma de Sala de Situação de Saúde (NSS), desenvolvido com Spring Boot e Java 21. Esta aplicação gerencia a persistência de dados, autenticação segura e disponibilização de APIs REST para o cliente web.

📌 Status do Projeto
O projeto encontra-se em estágio inicial de desenvolvimento, com o módulo de Autenticação e Gestão de Usuários já implementado (cadastro, login com emissão de token JWT, controle de acesso e consulta de usuários).

🛠️ Tecnologias Utilizadas
Linguagem: Java 21

Framework: Spring Boot 3.x

Spring Web

Spring Data JPA

Spring Security

Banco de Dados: PostgreSQL

Controle de Migrações: Flyway

Autenticação & Segurança:

JSON Web Tokens (JJWT 0.12.6)

Argon2 Password Encoder

Mapeamento de Objetos: MapStruct

Produtividade: Project Lombok

Gerenciador de Dependências: Apache Maven

Contêineres: Docker / Docker Compose

📂 Estrutura do Projeto
A organização de pacotes segue o padrão em camadas sob com.example.demo:

text
src/main/java/com/example/demo/
├── controller/     # Controladores REST (/auth, /NSS/users)
├── DTO/            # Objetos de transferência de dados (request, response e mappers)
├── entity/         # Entidades de banco de dados (ex.: User)
├── repository/     # Interfaces de acesso a dados (Spring Data JPA)
├── security/       # Configurações de segurança, filtros JWT e UserDetailsService
└── service/        # Regras de negócio da aplicação
🔑 Autenticação e Perfis (Roles)
A aplicação utiliza autenticação stateless via Bearer Token (JWT).

As senhas são armazenadas utilizando o algoritmo de hash Argon2.

A entidade User contempla os seguintes campos principais:

nome: Nome completo do usuário.

email: Endereço de e-mail (usado para login).

password: Hash da senha.

matricula: Identificador funcional do usuário.

cargo: Papel ou função desempenhada na instituição.

🚀 Endpoints da API
1. Autenticação (/auth)
   Método	Endpoint	Acesso	Descrição
   POST	/auth/register	Público	Registra um novo usuário no sistema
   POST	/auth/login	Público	Autentica as credenciais e retorna o JWT
   Exemplo de Cadastro (POST /auth/register):

json
{
"nome": "Nome do Profissional",
"email": "usuario@saude.gov.br",
"password": "senhaSegura123",
"matricula": "123456",
"cargo": "ANALISTA"
}
Exemplo de Login (POST /auth/login):

json
{
"email": "usuario@saude.gov.br",
"password": "senhaSegura123"
}
Resposta:

json
{
"token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
2. Gestão de Usuários (/NSS/users)
   Método	Endpoint	Acesso	Descrição
   GET	/NSS/users	Autenticado	Retorna a lista de todos os usuários
   GET	/NSS/users/{id}	Autenticado	Retorna os detalhes de um usuário específico
   Nota: Para requisições em endpoints protegidos, envie o header Authorization: Bearer <seu_token_jwt>.

⚙️ Pré-requisitos
Java JDK 21

Docker e Docker Compose (ou instância local do PostgreSQL)

Maven (opcional, o wrapper ./mvnw está incluso)

🏃 Como Executar
1. Executando com Docker Compose
   Suba a infraestrutura de banco de dados e dependências:

bash
docker compose up -d
2. Executando a Aplicação Localmente
   Com o banco de dados rodando:

bash
# No Linux/macOS
./mvnw clean spring-boot:run

# No Windows
mvnw.cmd clean spring-boot:run
A API estará disponível por padrão em http://localhost:8080.

🗺️ Roadmap de Integração
Definir e implementar as permissões baseadas em perfis/roles (ADMIN, OPERADOR, etc.).

Integração com o frontend (NSS Front-end).

Implementação das entidades e regras de negócio da Sala de Situação de Saúde (painéis, indicadores e relatórios epidemiológicos).

Integração com a esteira de implantação contínua (NSS Deployment).