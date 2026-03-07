package io.github.kamill7779.qforge.question.validation;

import io.github.kamill7779.qforge.question.exception.BusinessValidationException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * XML 题干校验器。
 * <p>
 * 在写入 stem_text 前执行校验：
 * 1. Well-formed 检查
 * 2. 白名单标签检查
 * 3. 结构约束检查
 */
@Component
public class StemXmlValidator {

    private static final Set<String> ALLOWED_TAGS = Set.of(
            "stem", "p", "image", "choices", "choice",
            "blanks", "blank", "answer-area",
            "table", "thead", "tbody", "tr", "th", "td"
    );

    /**
     * 校验 XML 题干内容。如果为 null 或空串则跳过校验。
     *
     * @param xml 题干 XML 字符串
     */
    public void validate(String xml) {
        if (xml == null || xml.isBlank()) {
            return;
        }

        Document doc = parseXml(xml);
        Element root = doc.getDocumentElement();

        if (!"stem".equals(root.getTagName())) {
            throw new BusinessValidationException(
                    "INVALID_ROOT_ELEMENT",
                    "Root element must be <stem>",
                    Map.of("actual", root.getTagName())
            );
        }

        walkAndValidateTags(root);
        validateChoices(root);
        validateBlankIds(root);
        validateChoiceKeys(root);
    }

    private Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entities for security
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new BusinessValidationException(
                    "INVALID_XML",
                    "XML is not well-formed: " + e.getMessage(),
                    Map.of("error", e.getMessage())
            );
        }
    }

    private void walkAndValidateTags(Element element) {
        if (!ALLOWED_TAGS.contains(element.getTagName())) {
            throw new BusinessValidationException(
                    "DISALLOWED_TAG",
                    "Tag <" + element.getTagName() + "> is not allowed",
                    Map.of("tag", element.getTagName())
            );
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                walkAndValidateTags((Element) child);
            }
        }
    }

    private void validateChoices(Element root) {
        NodeList choicesList = root.getElementsByTagName("choices");
        for (int i = 0; i < choicesList.getLength(); i++) {
            Element choices = (Element) choicesList.item(i);
            int choiceCount = 0;
            NodeList children = choices.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE && "choice".equals(child.getNodeName())) {
                    choiceCount++;
                }
            }
            if (choiceCount < 2) {
                throw new BusinessValidationException(
                        "INSUFFICIENT_CHOICES",
                        "<choices> must contain at least 2 <choice> elements",
                        Map.of("count", choiceCount)
                );
            }
        }
    }

    private void validateChoiceKeys(Element root) {
        NodeList choicesList = root.getElementsByTagName("choices");
        for (int i = 0; i < choicesList.getLength(); i++) {
            Element choices = (Element) choicesList.item(i);
            Set<String> keys = new HashSet<>();
            NodeList children = choices.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE && "choice".equals(child.getNodeName())) {
                    String key = ((Element) child).getAttribute("key");
                    if (key != null && !key.isEmpty() && !keys.add(key)) {
                        throw new BusinessValidationException(
                                "DUPLICATE_CHOICE_KEY",
                                "Duplicate choice key: " + key,
                                Map.of("key", key)
                        );
                    }
                }
            }
        }
    }

    private void validateBlankIds(Element root) {
        Set<String> blankIds = new HashSet<>();
        NodeList blanks = root.getElementsByTagName("blank");
        for (int i = 0; i < blanks.getLength(); i++) {
            Element blank = (Element) blanks.item(i);
            String id = blank.getAttribute("id");
            if (id != null && !id.isEmpty() && !blankIds.add(id)) {
                throw new BusinessValidationException(
                        "DUPLICATE_BLANK_ID",
                        "Duplicate blank id: " + id,
                        Map.of("id", id)
                );
            }
        }
    }
}
