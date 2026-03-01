package io.github.kamill7779.qforge.question.service;

import io.github.kamill7779.qforge.question.dto.MainTagCategoryResponse;
import io.github.kamill7779.qforge.question.dto.TagCatalogResponse;
import io.github.kamill7779.qforge.question.dto.TagOptionResponse;
import io.github.kamill7779.qforge.question.entity.TagCategory;
import io.github.kamill7779.qforge.question.repository.TagCategoryRepository;
import io.github.kamill7779.qforge.question.repository.TagRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TagQueryServiceImpl implements TagQueryService {

    private final TagRepository tagRepository;
    private final TagCategoryRepository tagCategoryRepository;

    public TagQueryServiceImpl(TagRepository tagRepository, TagCategoryRepository tagCategoryRepository) {
        this.tagRepository = tagRepository;
        this.tagCategoryRepository = tagCategoryRepository;
    }

    @Override
    public TagCatalogResponse getMainTagCatalog() {
        List<TagCategory> allCategories = tagCategoryRepository.findEnabledCategories();
        List<TagCategory> mainCategories = tagCategoryRepository.findEnabledMainCategories();

        List<MainTagCategoryResponse> mainCategoryResponses = mainCategories.stream()
                .map(category -> new MainTagCategoryResponse(
                        category.getCategoryCode(),
                        category.getCategoryName(),
                        tagRepository.findSystemTagsByCategory(category.getCategoryCode())
                                .stream()
                                .map(tag -> new TagOptionResponse(tag.getTagCode(), tag.getTagName()))
                                .toList()
                ))
                .toList();

        TagCategory secondaryCategory = allCategories.stream()
                .filter(category -> "SECONDARY".equals(category.getCategoryKind()))
                .findFirst()
                .orElse(null);

        return new TagCatalogResponse(
                mainCategoryResponses,
                secondaryCategory == null ? "SECONDARY_CUSTOM" : secondaryCategory.getCategoryCode(),
                secondaryCategory == null ? "Secondary" : secondaryCategory.getCategoryName()
        );
    }
}
