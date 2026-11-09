# ReturnHub – Product Return Management System

A real-world Product Return Management application built using Java, Spring Boot, Spring Security, Maven, and a responsive web interface.

## Features
- Customer registration and login
- Staff login and dashboard
- Admin login and user management
- Customer and purchase management
- Product return request submission
- Return approval and rejection
- Return tracking
- Refund and sales statistics
- Audit logs
- REST API with HTTP Basic authentication

## Technologies
- Java
- Spring Boot
- Spring Security
- Spring Data JPA
- Maven
- Thymeleaf
- HTML, CSS, JavaScript
- H2 Database

## Run the Application

Make sure Java and Maven are installed.

```bash
mvn spring-boot:run
```

Open your browser:

http://localhost:8080

## Demo Accounts

| Role | Email | Password |
|---|---|---|
| Customer | aarav@mail.com | customer123 |
| Staff | staff@returnhub.com | staff123 |
| Admin | admin@returnhub.com | admin123 |

New customers can register at `/register`.

## API

Staff/Admin API endpoint:

```bash
curl -u admin@returnhub.com:admin123 http://localhost:8080/api/stats
```

## Reset Demo Data

Stop the application, delete the `data` folder, and restart the application.

## Project Structure

```text
ReturnHub-Web/
├── src/
│   └── main/
│       ├── java/
│       └── resources/
│           ├── data/
│           ├── static/
│           └── templates/
├── data/
├── pom.xml
├── Dockerfile
├── run.bat
├── run.sh
└── README.md
```

## GitHub

Repository: https://github.com/ashok010406/Product-Return-Management