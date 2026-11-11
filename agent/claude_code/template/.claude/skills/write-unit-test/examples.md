# Unit test examples

## Plain unit test

```java
package com.comagents.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriceCalculatorTest {

    private final PriceCalculator calculator = new PriceCalculator();

    @Test
    void totalAppliesDiscount() {
        var total = calculator.total(100, 10);

        assertEquals(90, total);
    }

    @Test
    void totalRejectsNegativeDiscount() {
        assertThrows(IllegalArgumentException.class, () -> calculator.total(100, -1));
    }
}
```

## Micronaut HTTP test (only when the application context is needed)

```java
@MicronautTest
class AgentControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void returnsAgentInfo() {
        var info = client.toBlocking().retrieve("/code_review/info", AgentInfo.class);

        assertEquals("code_review", info.name());
    }
}
```
