example using spring-boot-maven-plugin

check https://github.com/springdoc/springdoc-openapi-maven-plugin

```
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <version>${spring-boot-maven-plugin.version}</version>
    <configuration>
        <jvmArguments>-Dspring.application.admin.enabled=true</jvmArguments>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>start</goal>
                <goal>stop</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```
