# lng-test

## Сборка

```bash
mvn -q -DskipTests package
```

Готовый jar:

```text
target/lng-test.jar
```

## Запуск

```bash
java -Xmx1G -jar target/lng-test.jar "C:\full\path\to\lng-4.txt"
```

Файл результата создаётся в текущей директории:

```text
groups.txt
```

