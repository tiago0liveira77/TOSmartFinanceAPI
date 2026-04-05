package com.smartfinance.finance.service;

import com.smartfinance.finance.dto.request.CreateCategoryRequest;
import com.smartfinance.finance.dto.response.CategoryResponse;
import com.smartfinance.finance.entity.Category;
import com.smartfinance.finance.exception.CategoryNotFoundException;
import com.smartfinance.finance.repository.CategoryRepository;
import com.smartfinance.shared.exception.SmartFinanceException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    private UUID getUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    public List<CategoryResponse> findAll() {
        UUID userId = getUserId();
        return categoryRepository.findAllForUser(userId)
                .stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        UUID userId = getUserId();
        Category category = new Category();
        category.setUserId(userId);
        category.setName(request.name());
        category.setType(request.type());
        category.setIcon(request.icon());
        category.setColor(request.color());
        category.setSystem(false);

        if (request.parentId() != null) {
            Category parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new CategoryNotFoundException(request.parentId()));
            category.setParent(parent);
        }

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, CreateCategoryRequest request) {
        UUID userId = getUserId();
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new CategoryNotFoundException(id));

        if (category.isSystem()) {
            throw new SmartFinanceException("Cannot modify system categories", HttpStatus.FORBIDDEN);
        }

        category.setName(request.name());
        if (request.icon() != null) category.setIcon(request.icon());
        if (request.color() != null) category.setColor(request.color());

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID id) {
        UUID userId = getUserId();
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new CategoryNotFoundException(id));

        if (category.isSystem()) {
            throw new SmartFinanceException("Cannot delete system categories", HttpStatus.FORBIDDEN);
        }

        categoryRepository.delete(category);
    }
}