## Exercise the application

```
curl -X GET http://localhost:8080/pokemon
[{"id":1,"type":12,"name":"Bulbasaur"}, ...]

curl -X GET http://localhost:8080/type
[{"id":1,"name":"Normal"}, ...]

curl -H "Content-Type: application/json" --request POST --data '{"id":100, "type":1, "name":"Test"}' http://localhost:8080/pokemon
```

## Try health and metrics

```
curl -s -X GET http://localhost:8080/health
{"outcome":"UP",...
. . .

# Prometheus Format
curl -s -X GET http://localhost:8080/metrics
# TYPE base:gc_g1_young_generation_count gauge
. . .

# JSON Format
curl -H 'Accept: application/json' -X GET http://localhost:8080/metrics
{"base":...
. . .
```

## GraalVM Native Support

The generation of native binaries requires an installation of GraalVM 20.1.0+. For more
information about the steps necessary to use GraalVM with Helidon
see https://helidon.io/docs/v2/#/mp/guides/36_graalnative.

The H2 Database when configured to use the in-memory mode is currently _not compatible_
with GraalVM native.
In order to produce a native binary, you must run the H2 Database as a separate process
and use a network connection for access. The simplest way to do this is by starting a Docker
container as follows:

```
docker run -d -p 1521:1521 -p 81:81 -e H2_OPTIONS='-ifNotExists' --name=h2 oscarfonts/h2
```

The resulting container will listen to port 1521 for network connections.
Switch property `javax.sql.DataSource.test.dataSource.url` in `microprofile-config.properties`
to use a TCP connection:

```
javax.sql.DataSource.test.dataSource.url=jdbc:h2:tcp://localhost:1521/test
```

Next, uncomment the following dependency in your project's pom file:

```
<dependency>
    <groupId>io.helidon.integrations.db</groupId>
    <artifactId>h2</artifactId>
</dependency>
```

With all these changes, re-build your project and verify that all tests are passing.
Finally, you can build a native binary using Maven as follows:

```
mvn -Pnative-image install -DskipTests
```

The generation of the executable binary may take several minutes to complete
depending on your hardware and operating system --with Linux typically outperforming other
platforms. When completed, the executable file will be available
under the `target` directory and be named after the artifact ID you have chosen during the
project generation phase.
