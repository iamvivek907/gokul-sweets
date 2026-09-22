package com.gokulsweets.restaurant.category;

import com.gokulsweets.restaurant.category.dto.CategoryResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository
                .findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public CategoryResponse getCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Category not found: " + id)
                );

        return CategoryResponse.from(category);
    }
}