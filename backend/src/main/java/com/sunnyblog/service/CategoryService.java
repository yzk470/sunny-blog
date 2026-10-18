package com.sunnyblog.service;

import com.sunnyblog.dto.CategoryVO;
import com.sunnyblog.dto.request.CategoryRequest;

import java.util.List;

public interface CategoryService {

    List<CategoryVO> list();

    CategoryVO create(CategoryRequest request);

    CategoryVO update(Long id, CategoryRequest request);

    void delete(Long id);
}
