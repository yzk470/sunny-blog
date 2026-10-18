package com.sunnyblog.service;

import com.sunnyblog.common.PageResult;
import com.sunnyblog.dto.CommentVO;
import com.sunnyblog.dto.request.CommentCreateRequest;

public interface CommentService {

    PageResult<CommentVO> listApprovedByPostSlug(String slug, int page, int size);

    CommentVO create(String slug, CommentCreateRequest request);

    PageResult<CommentVO> adminList(Integer status, int page, int size);

    void moderate(Long id, int status);

    void delete(Long id);
}
