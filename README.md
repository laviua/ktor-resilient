# KTOR Resilient 
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0) [![Maven Central](https://img.shields.io/maven-central/v/ua.com.lavi.ktor-resilient/ktor-resilient-client.svg?style=plastic)]()

This repository contains a light implementation of the Ktor framework wrapped by Resilience4j, a Kotlin library for managing failures in a microservice-based system.

Expected behaviour:

The Resilience4j Aspects order is following: Retry ( CircuitBreaker ( RateLimiter ( TimeLimiter ( Bulkhead ( Function ) ) ) ) )
## Requirements
- Java 17 or later

## Features
- Circuit breaker pattern to prevent cascading failures
- Retry pattern to automatically retry failed requests
- Bulkhead pattern to limit the number of concurrent requests
- KTOR Client Plugins
- For more information on Resilience4j, please see the official documentation: https://resilience4j.readme.io/
- For more information on Ktor, please see the official documentation: https://ktor.io/servers/index.html

## Plugin examples:
Can be found here: ```ktor-resilient-client/src/test/kotlin/PluginHttpTests.kt```

## Development
GPG key should be placed in ~/.gradle/gradle.properties and has the following format:
```
signing.gnupg.keyName=
signing.gnupg.passphrase=
```

Sonatype keys in the ~/.gradle/gradle.properties should have the following format:
```
sonatypeUsername=
sonatypePassword=
```

How to build and publish to sonatype?
Run the following command in the console:

```
./gradlew clean build publishToMavenLocal
or
./gradlew clean build publishToSonatype
```

### Contributing
To contribute to this project, simply fork the repository and submit a pull request with your changes.

### License
This project is licensed under the Apache 2.0 license. See the LICENSE file for details.
