# ReturnHub - Real-world Product Return Management (Java Spring Boot)
Run:  mvn spring-boot:run   -> http://localhost:8080 (browser opens automatically)

Accounts (passwords stored with BCrypt):
  Customer  aarav@mail.com / customer123   (all 24 dataset customers use customer123)
  Staff     staff@returnhub.com / staff123
  Admin     admin@returnhub.com / admin123
New customers register at /register.
Reset data: stop the app, delete the ./data folder, start again.
API (HTTP Basic, staff/admin): curl -u admin@returnhub.com:admin123 http://localhost:8080/api/stats
