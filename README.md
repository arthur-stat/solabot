# solabot

仅为重庆大学 PJSK 同好群「翼遥啤酒烧烤大排档」设计的独立 bot 服务。内容略。

# version

OpenJDK 17 / 21

Maven 3.9

Spring Boot 3.5

Spring WebFlux 3.5 (WebClient only)

Spring Cloud (in the planning)（计划首先用于组卡器微服务）

Kafka (in the planning)（计划首先用于组卡器微服务）

Docker 27.5

PostgreSQL 16

Redis 7

NapCat（OneBot v11）

# to do

1. 可观测性
2. 实现组卡器微服务
3. PJSK 资源管理器（自动检查并拉取最新 master data 等，统一管理版本资源）

# docker

```bash
# Start containers (and build if not exists) in detached mode before running the project
docker compose -f docker-compose.dev.yaml up -d

# Enter the bash interactive shell of the PostgreSQL container
docker exec -it dev-postgres bash
psql -U app -d appdb

# Enter the sh interactive shell of the Redis container
docker exec -it dev-redis sh
redis-cli

# Shutdown all containers
docker stop $(docker ps -q)
```

# maven

```bash
mvn clean package
mvn spring-boot:run  # java -jar target/bot-0.0.1-SNAPSHOT.jar
```

# thanks

Thanks to junior schoolmate [htrskmiku](https://github.com/htrskmiku) for the contribution!
