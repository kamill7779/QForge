package io.github.kamill7779.qforge.gaokaoanalysis.mq;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class GaokaoPaperIndexRequestedConsumerTest {

    @Test
    void estimateTokensShouldNotSeverelyUnderestimateChineseText() throws Exception {
        GaokaoPaperIndexRequestedConsumer consumer = new GaokaoPaperIndexRequestedConsumer(
                null, null, null, null
        );
        Method method = GaokaoPaperIndexRequestedConsumer.class.getDeclaredMethod("estimateTokens", String.class);
        method.setAccessible(true);

        int tokens = (int) method.invoke(consumer, "函数单调性");

        assertTrue(tokens >= 4, "expected chinese text token estimate to be >= 4 but was " + tokens);
    }
}
