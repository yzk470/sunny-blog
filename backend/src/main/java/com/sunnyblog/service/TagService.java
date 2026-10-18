package com.sunnyblog.service;

import com.sunnyblog.dto.TagVO;
import com.sunnyblog.dto.request.TagRequest;

import java.util.List;

public interface TagService {

    List<TagVO> list();

    TagVO create(TagRequest request);

    TagVO update(Long id, TagRequest request);

    void delete(Long id);
}
