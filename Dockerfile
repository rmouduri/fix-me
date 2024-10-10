FROM mysql:8.0

ENV MYSQL_ROOT_PASSWORD=root
ENV MYSQL_DATABASE=fixme
ENV MYSQL_USER=user
ENV MYSQL_PASSWORD=password

EXPOSE 3306

# docker build -t mysql-database .
# docker run --name mysql-container -p 3306:3306 -d mysql-database
# docker exec -it $(docker ps -qa --filter=ancestor=mysql-database) /bin/bash
# mysql -u user -p -P 3306 -h 127.0.0.1
#> password
#> USE fixme
#> SELECT * FROM transaction