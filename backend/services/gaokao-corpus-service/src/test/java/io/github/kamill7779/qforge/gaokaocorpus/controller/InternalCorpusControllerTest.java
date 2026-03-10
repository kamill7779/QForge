package io.github.kamill7779.qforge.gaokaocorpus.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.kamill7779.qforge.common.contract.GaokaoIndexCallbackRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class InternalCorpusControllerTest {

    @Test
    void updatePaperIndexShouldBeTransactional() throws Exception {
        Method method = InternalCorpusController.class.getMethod(
                "updatePaperIndex",
                Long.class,
                GaokaoIndexCallbackRequest.class
        );

        assertNotNull(method.getAnnotation(Transactional.class));
    }
}
