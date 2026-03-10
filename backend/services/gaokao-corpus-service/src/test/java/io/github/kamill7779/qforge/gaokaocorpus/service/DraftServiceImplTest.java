package io.github.kamill7779.qforge.gaokaocorpus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.lang.reflect.Method;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

class DraftServiceImplTest {

    @Test
    void buildStemXmlShouldKeepCdataSafeWhenTextContainsCdataClosingMarker() throws Exception {
        DraftServiceImpl service = new DraftServiceImpl(
                null, null, null, null, null, null, null, null, null, null, null, null
        );

        Method method = DraftServiceImpl.class.getDeclaredMethod("buildStemXml", String.class);
        method.setAccessible(true);

        String raw = "已知集合A]]>B，求A∩B。";
        String xml = (String) method.invoke(service, raw);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        String parsedText = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)))
                .getDocumentElement()
                .getTextContent();

        assertEquals(raw, parsedText);
    }
}
