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

Программа печатает в stdout:
- `groups_gt_1` — число групп, содержащих больше одного элемента (это нужно отправить как ответ)
- `time_ms` — время выполнения в миллисекундах (это тоже нужно отправить)

